## Coroutine Scope funktionen

Stellen Sie sich vor, Sie brauchen in einer suspending function nebenläufig Daten von zwei (oder mehr) Endpunkten zu bekommen. Bevor wir untersuchen, wie dies korrekt gemacht wird, werfen wir einen Blick auf einige **suboptimale** Vorgehensweisen.

### Ansätze, die vor der Einführung der Coroutine Scope funktion verwendet wurden

Der erste Ansatz besteht darin, suspending functions von einer suspending function aufzurufen. Das Problem mit dieser Lösung ist, dass sie nicht nebenläufig ist (also, wenn das Abrufen der Daten von einem Endpunkt 1 Sekunde dauert, wird die Funktion 2 Sekunden statt 1 Sekunde benötigen).

```kotlin
// Data loaded sequentially, not simultaneously
suspend fun getUserProfile(): UserProfileData {
    val user = getUserData() // (1 sec)
    val notifications = getNotifications() // (1 sec)

    return UserProfileData(
        user = user,
        notifications = notifications,
    )
}
```

Um zwei suspendierende Aufrufe gleichzeitig auszuführen, ist die einfachste Möglichkeit, sie mit `async` zu verpacken. Allerdings erfordert `async` einen Geltungsbereich, und die Verwendung von `GlobalScope` ist keine gute Idee.

```kotlin
// DON'T DO THAT
suspend fun getUserProfile(): UserProfileData {
    val user = GlobalScope.async { getUserData() }
    val notifications = GlobalScope.async {
        getNotifications()
    }

    return UserProfileData(
        user = user.await(), // (1 sec)
        notifications = notifications.await(),
    )
}
```

`GlobalScope` ist lediglich ein Geltungsbereich mit `EmptyCoroutineContext`.

```kotlin
public object GlobalScope : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = EmptyCoroutineContext
}
```


Wenn wir `async` auf einem `GlobalScope` aufrufen, haben wir keine Verbindung zur Eltern-Coroutine. Dies bedeutet, dass die `async` Coroutine:
* nicht storniert werden kann (wenn die Eltern-Coroutine storniert wird, laufen Funktionen innerhalb von async weiter, was Ressourcen verschwendet, bis sie fertig sind);
* erbt keinen Scope von irgendeiner Eltern-Coroutine (sie wird immer auf dem Standard-Dispatcher ausgeführt und respektiert keinen Kontext von der Eltern-Coroutine).

Die wichtigsten Konsequenzen sind:
* mögliche Speicherlecks und überflüssige CPU-Nutzung;
* die Werkzeuge für das Unit-Testing von Coroutines funktionieren hier nicht, daher ist das Testen dieser Funktion sehr schwierig.

Das ist keine gute Lösung. Schauen wir uns eine andere an, in der wir einen Scope als Argument übergeben:


```kotlin
// DON'T DO THAT
suspend fun getUserProfile(
    scope: CoroutineScope
): UserProfileData {
    val user = scope.async { getUserData() }
    val notifications = scope.async { getNotifications() }

    return UserProfileData(
        user = user.await(), // (1 sec)
        notifications = notifications.await(),
    )
}

// or

// DON'T DO THAT
suspend fun CoroutineScope.getUserProfile(): UserProfileData {
    val user = async { getUserData() }
    val notifications = async { getNotifications() }

    return UserProfileData(
        user = user.await(), // (1 sec)
        notifications = notifications.await(),
    )
}
```

Diese Lösung ist etwas besser, da Stornierung und ordnungsgemäßes Unit-Testing nun möglich sind. Das Problem ist, dass diese Lösung erfordert, dass dieser Scope von Funktion zu Funktion weitergegeben wird. Des Weiteren können diese Funktionen unerwünschte Nebenwirkungen im Scope verursachen; beispielsweise, wenn es eine Ausnahme in einem `async` gibt, wird der gesamte Scope heruntergefahren (vorausgesetzt, es wird `Job`, nicht `SupervisorJob` verwendet). Außerdem könnte eine Funktion, die Zugang zum Scope hat, diesen Zugang leicht missbrauchen und zum Beispiel diesen Scope mit der `cancel`-Methode abbrechen. Deshalb ist dieser Ansatz schwierig und potenziell gefährlich.

{crop-start: 3, crop-end: 34}
```kotlin
import kotlinx.coroutines.*

//sampleStart
data class Details(val name: String, val followers: Int)
data class Tweet(val text: String)

fun getFollowersNumber(): Int =
    throw Error("Service exception")

suspend fun getUserName(): String {
    delay(500)
    return "marcinmoskala"
}

suspend fun getTweets(): List<Tweet> {
    return listOf(Tweet("Hello, world"))
}

suspend fun CoroutineScope.getUserDetails(): Details {
    val userName = async { getUserName() }
    val followersNumber = async { getFollowersNumber() }
    return Details(userName.await(), followersNumber.await())
}

fun main() = runBlocking {
    val details = try {
        getUserDetails()
    } catch (e: Error) {
        null
    }
    val tweets = async { getTweets() }
    println("User: $details")
    println("Tweets: ${tweets.await()}")
}
// Only Exception...
//sampleEnd
```

Im obigen Code möchten wir zumindest Tweets sehen, auch wenn wir Probleme beim Abrufen von Benutzerdetails haben. Leider führt eine Ausnahme bei `getFollowersNumber` zu einem Bruch in `async`, was den gesamten Geltungsbereich zerstört und das Programm beendet. Stattdessen würden wir eine Funktion bevorzugen, die einfach eine Ausnahme auslöst, wenn sie auftritt. Es ist Zeit, unseren Helden vorzustellen: `coroutineScope`.

### coroutineScope

`coroutineScope` ist eine suspendierende Funktion, die einen Geltungsbereich startet. Sie gibt den von der Argumentfunktion erzeugten Wert zurück.

```kotlin
suspend fun <R> coroutineScope(
    block: suspend CoroutineScope.() -> R
): R
```


Im Gegensatz zu `async` oder `launch` wird der Körper von `coroutineScope` direkt ausgeführt. Es erzeugt formal eine neue Coroutine, aber es setzt die vorherige aus, bis die neue abgeschlossen ist, daher startet es keinen gleichzeitigen Prozess. Schauen Sie sich das untenstehende Beispiel an, in dem beide `delay` Aufrufe `runBlocking` aussetzen.

{crop-start: 5, crop-end: 22}
```kotlin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

//sampleStart
fun main() = runBlocking {
    val a = coroutineScope {
        delay(1000)
        10
    }
    println("a is calculated")
    val b = coroutineScope {
        delay(1000)
        20
    }
    println(a) // 10
    println(b) // 20
}
// (1 sec)
// a is calculated
// (1 sec)
// 10
// 20
//sampleEnd
```

Der bereitgestellte Scope erbt seinen `coroutineContext` vom äußeren Scope, überschreibt jedoch den `Job` des Kontexts. Daher respektiert der erzeugte Scope seine übergeordneten Verantwortlichkeiten:
* erbt einen Kontext von seinem Elternscope;
* wartet auf all seine Unteraufgaben, bevor er sich selbst abschließen kann;
* bricht all seine Unteraufgaben ab, wenn der Elternscope abgebrochen wird.

Im untenstehenden Beispiel können Sie beobachten, dass "After" am Ende ausgegeben wird, weil `coroutineScope` nicht abgeschlossen wird, bis alle seine Unteraufgaben abgeschlossen sind. Zudem wird `CoroutineName` korrekt vom Elternscope zum Kindscope übermittelt.

{crop-start: 3, crop-end: 26}
```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun longTask() = coroutineScope {
    launch {
        delay(1000)
        val name = coroutineContext[CoroutineName]?.name
        println("[$name] Finished task 1")
    }
    launch {
        delay(2000)
        val name = coroutineContext[CoroutineName]?.name
        println("[$name] Finished task 2")
    }
}

fun main() = runBlocking(CoroutineName("Parent")) {
    println("Before")
    longTask()
    println("After")
}
// Before
// (1 sec)
// [Parent] Finished task 1
// (1 sec)
// [Parent] Finished task 2
// After
//sampleEnd
```


Im nächsten Ausschnitt können Sie beobachten, wie der Abbruch funktioniert. Ein abgebrochenes Elternteil führt zum Abbruch von unfertigen Kindern.

{crop-start: 3, crop-end: 23}
```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun longTask() = coroutineScope {
    launch {
        delay(1000)
        val name = coroutineContext[CoroutineName]?.name
        println("[$name] Finished task 1")
    }
    launch {
        delay(2000)
        val name = coroutineContext[CoroutineName]?.name
        println("[$name] Finished task 2")
    }
}

fun main(): Unit = runBlocking {
    val job = launch(CoroutineName("Parent")) {
        longTask()
    }
    delay(1500)
    job.cancel()
}
// [Parent] Finished task 1
//sampleEnd
```


Wenn es eine Ausnahme in `coroutineScope` oder einem seiner Kinder gibt, beendet es im Gegensatz zu Coroutine-Erstellern, alle anderen Kinder und wirft sie noch einmal. Deshalb würde der Einsatz von `coroutineScope` unser vorheriges Problem mit dem "Twitter-Beispiel" lösen. Um zu zeigen, dass die gleiche Ausnahme noch einmal geworfen wird, habe ich einen allgemeinen `Error` in eine spezifische `ApiException` umgewandelt.

{crop-start: 3, crop-end: 39}
```kotlin
import kotlinx.coroutines.*

//sampleStart
data class Details(val name: String, val followers: Int)
data class Tweet(val text: String)
class ApiException(
    val code: Int,
    message: String
) : Throwable(message)

fun getFollowersNumber(): Int =
    throw ApiException(500, "Service unavailable")

suspend fun getUserName(): String {
    delay(500)
    return "marcinmoskala"
}

suspend fun getTweets(): List<Tweet> {
    return listOf(Tweet("Hello, world"))
}

suspend fun getUserDetails(): Details = coroutineScope {
    val userName = async { getUserName() }
    val followersNumber = async { getFollowersNumber() }
    Details(userName.await(), followersNumber.await())
}

fun main() = runBlocking<Unit> {
    val details = try {
        getUserDetails()
    } catch (e: ApiException) {
        null
    }
    val tweets = async { getTweets() }
    println("User: $details")
    println("Tweets: ${tweets.await()}")
}
// User: null
// Tweets: [Tweet(text=Hello, world)]
//sampleEnd
```

Das macht `coroutineScope` zu einem idealen Kandidaten für die meisten Situationen, in denen wir lediglich einige gleichzeitige Aufrufe in einer unterbrechbaren Funktion initiieren müssen.

```kotlin
suspend fun getUserProfile(): UserProfileData =
    coroutineScope {
        val user = async { getUserData() }
        val notifications = async { getNotifications() }

        UserProfileData(
            user = user.await(), // (1 sec)
            notifications = notifications.await(),
        )
    }
```


Wie wir bereits erwähnt haben, wird `coroutineScope` heutzutage oft verwendet, um einen suspendierenden Hauptteil einzuschließen. Sie können es als die moderne Ersetzung für die `runBlocking` Funktion betrachten:

{crop-start: 3, crop-end: 12}
```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    launch {
        delay(1000)
        println("World")
    }
    println("Hello, ")
}
// Hello
// (1 sec)
// World
//sampleEnd
```

Die Funktion `coroutineScope` erstellt einen Bereich aus einem Unterbrechungs-Kontext. Sie übernimmt den Bereich von der übergeordneten Funktion und unterstützt strukturierte Parallelität.

Um es zu verdeutlichen, es gibt praktisch keinen Unterschied zwischen den untenstehenden Funktionen, außer dass die erste `getProfile` und `getFriends` nacheinander aufruft, während die zweite sie gleichzeitig aufruft.

```kotlin
suspend fun produceCurrentUserSeq(): User {
    val profile = repo.getProfile()
    val friends = repo.getFriends()
    return User(profile, friends)
}

suspend fun produceCurrentUserSym(): User = coroutineScope {
    val profile = async { repo.getProfile() }
    val friends = async { repo.getFriends() }
    User(profile.await(), friends.await())
}
```

`coroutineScope` ist eine nützliche Funktion, aber nicht die einzige ihrer Art.

### Coroutine-Scope-Funktionen

Es gibt mehr Funktionen, die einen Bereich erstellen und sich ähnlich wie `coroutineScope` verhalten. `supervisorScope` ist wie `coroutineScope`, verwendet aber `SupervisorJob` anstelle von `Job`. `withContext` ist eine `coroutineScope`-Funktion, die den Coroutine-Kontext verändern kann. `withTimeout` ist eine `coroutineScope`-Funktion mit einem Timeout. Jede dieser Funktionen wird in den folgenden Teilen dieses Kapitels besser erklärt. Ich möchte, dass Sie jetzt wissen, dass es solche Funktionen gibt, denn wenn es eine Gruppe von ähnlichen Funktionen gibt, macht es Sinn, dass sie einen Namen haben sollte. Wie sollten wir also diese Gruppe nennen? Einige Leute nennen sie "Bereichsfunktionen", aber ich finde das verwirrend, da ich nicht sicher bin, was mit "Bereich" gemeint ist. Ich vermute, dass die Person, die diesen Begriff zuerst verwendet hat, ihn einfach von "Bereichsfunktionen" (Funktionen wie `let`, `with` oder `apply`) unterscheiden wollte. Das ist jedoch nicht wirklich hilfreich, da diese beiden Begriffe oft verwechselt werden. Deshalb habe ich mich für den Begriff "Coroutine-Scope-Funktionen" entschieden. Er ist länger, sollte aber weniger Missverständnisse verursachen, und ich finde ihn korrekter. Denken Sie mal darüber nach: Coroutine-Scope-Funktionen sind diejenigen, die verwendet werden, um einen Coroutine-Bereich in suspendierenden Funktionen zu erstellen.

Andererseits werden Coroutine-Scope-Funktionen oft mit Coroutine-Buildern verwechselt, aber das ist falsch, da sie konzeptuell und praktisch sehr unterschiedlich sind. Um dies zu verdeutlichen, zeigt die untenstehende Tabelle den Vergleich zwischen ihnen.

| Coroutine Builder (außer `runBlocking`)               | Coroutine-Scope-Funktionen                                        |
|--------------------------------------------------------|-------------------------------------------------------------------|
| `launch`, `async`, `produce`                           | `coroutineScope`, `supervisorScope`, `withContext`, `withTimeout` |
| Sind Erweiterungsfunktionen auf `CoroutineScope`.      | Sind suspendierende Funktionen.                                   |
| Nehmen Coroutine-Kontext vom `CoroutineScope`-Empfänger. | Nehmen Coroutine-Kontext von der Fortsetzungsfunktion auf. |
| Exceptions werden durch `Job` an den Elternteil weitergegeben. | Exceptions werden genauso geworfen, wie in regulären Funktionen. |
| Startet eine asynchrone Coroutine.                       | Startet eine Coroutine, die direkt ausgeführt wird.               |

Denken Sie jetzt an `runBlocking`. Ihnen könnte auffallen, dass es mehr Gemeinsamkeiten mit Coroutine-Scope-Funktionen als mit Buildern hat. `runBlocking` führt auch seinen Inhalt direkt aus und gibt sein Ergebnis zurück. Der größte Unterschied besteht darin, dass `runBlocking` eine blockierende Funktion ist, während Coroutine-Scope-Funktionen suspendierte Funktionen sind. Deshalb muss `runBlocking` an der Spitze der Coroutine-Hierarchie stehen, während Coroutine-Scope-Funktionen in der Mitte stehen müssen.

### withContext

Die Funktion `withContext` ähnelt `coroutineScope`, ermöglicht jedoch zusätzlich einige Änderungen im Bereich. Der Kontext, der dieser Funktion als Argument übergeben wird, überschreibt den Kontext des übergeordneten Bereichs (genauso wie bei den Coroutine-Buildern). Das bedeutet, dass `withContext(EmptyCoroutineContext)` und `coroutineScope()` genau gleich funktionieren.

{crop-start: 3, crop-end: 28}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun CoroutineScope.log(text: String) {
    val name = this.coroutineContext[CoroutineName]?.name
    println("[$name] $text")
}

fun main() = runBlocking(CoroutineName("Parent")) {
    log("Before")

    withContext(CoroutineName("Child 1")) {
        delay(1000)
        log("Hello 1")
    }

    withContext(CoroutineName("Child 2")) {
        delay(1000)
        log("Hello 2")
    }

    log("After")
}
// [Parent] Before
// (1 sec)
// [Child 1] Hello 1
// (1 sec)
// [Child 2] Hello 2
// [Parent] After
//sampleEnd
```


Die Funktion `withContext` wird oft genutzt, um einen anderen Coroutine-Geltungsbereich für einen Teil unseres Codes festzulegen. Normalerweise solltest du sie zusammen mit Dispatchers verwenden, wie wir im nächsten Kapitel sehen werden.


```kotlin
launch(Dispatchers.Main) {
    view.showProgressBar()
    withContext(Dispatchers.IO) {
        fileRepository.saveData(data)
    }
    view.hideProgressBar()
}
```


> Sie könnten feststellen, dass die Art und Weise, wie `coroutineScope { /*...*/ }` funktioniert, sehr ähnlich zu async mit sofortigem `await`: `async { /*...*/ }.await()` ist. Auch `withContext(context) { /*...*/ }` ist in gewisser Weise ähnlich zu `async(context) { /*...*/ }.await()`. Der größte Unterschied besteht darin, dass `async` einen Geltungsbereich braucht, während `coroutineScope` und `withContext` den Geltungsbereich aus der Suspension übernehmen. In beiden Fällen ist es besser, `coroutineScope` und `withContext` zu verwenden und `async` mit sofortigem `await` zu vermeiden.

### supervisorScope

Die Funktion `supervisorScope` verhält sich auch sehr ähnlich wie `coroutineScope`: sie erstellt einen `CoroutineScope`, der vom äußeren Geltungsbereich erbt und ruft den angegebenen suspend-Block darin auf. Der Unterschied besteht darin, dass sie die `Aufgabe` des Kontexts mit `SupervisorJob` überschreibt, sodass sie nicht abgebrochen wird, wenn ein Unterprozess eine Ausnahme auslöst.

{crop-start: 3, crop-end: 25}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun main() = runBlocking {
    println("Before")

    supervisorScope {
        launch {
            delay(1000)
            throw Error()
        }

        launch {
            delay(2000)
            println("Done")
        }
    }

    println("After")
}
// Before
// (1 sec)
// Exception...
// (1 sec)
// Done
// After
//sampleEnd
```

`supervisorScope` wird vor allem in Funktionen eingesetzt, die zahlreiche eigenständige Tasks initiieren.

```kotlin
suspend fun notifyAnalytics(actions: List<UserAction>) =
    supervisorScope {
        actions.forEach { action ->
            launch {
                notifyAnalytics(action)
            }
        }
    }
```

Wenn Sie `async` verwenden, reicht es nicht aus, die Fehlerweiterleitung an den Aufrufer zu unterdrücken. Wenn wir `await` aufrufen und die `async` Coroutine mit einem Fehler endet, dann wirft `await` es erneut aus. Deshalb sollten wir, wenn wir Fehler wirklich ignorieren wollen, Aufrufe von `await` auch mit einer try-catch-Anweisung umgeben.

```kotlin
class ArticlesRepositoryComposite(
    private val articleRepositories: List<ArticleRepository>,
) : ArticleRepository {
    override suspend fun fetchArticles(): List<Article> =
        supervisorScope {
            articleRepositories
                .map { async { it.fetchArticles() } }
                .mapNotNull {
                    try {
                        it.await()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        null
                    }
                }
                .flatten()
                .sortedByDescending { it.publishedAt }
        }
}
```


In meinen Workshops werde ich oft gefragt, ob wir `withContext(SupervisorJob())` anstelle von `supervisorScope` verwenden können. Nein, das können wir nicht. Wenn wir `withContext(SupervisorJob())` verwenden, dann verwendet `withContext` immer noch einen regulären `Job`, und der `SupervisorJob()` wird dessen übergeordnete Instanz. Als Ergebnis, wenn ein Unterprozess eine Ausnahme wirft, werden auch die anderen Unterprozesse abgebrochen. `withContext` wirft auch eine Ausnahme, daher ist sein `SupervisorJob()` praktisch nutzlos. Deshalb finde ich `withContext(SupervisorJob())` sinnlos und irreführend, und ich halte es für eine schlechte Vorgehensweise.

{crop-start: 3, crop-end: 22}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun main() = runBlocking {
    println("Before")

    withContext(SupervisorJob()) {
        launch {
            delay(1000)
            throw Error()
        }

        launch {
            delay(2000)
            println("Done")
        }
    }

    println("After")
}
// Before
// (1 sec)
// Exception...
//sampleEnd
```


### withTimeout

Eine weitere Funktion, die sich sehr ähnlich wie `coroutineScope` verhält, ist `withTimeout`. Sie erstellt ebenfalls einen Gültigkeitsbereich und gibt einen Wert zurück. Tatsächlich verhält sich `withTimeout` mit einer sehr großen Timeout-Zeit genau wie `coroutineScope`. Der Unterschied besteht darin, dass `withTimeout` zusätzlich ein Zeitlimit für die Ausführung seines Körpers festlegt. Wenn es zu lange dauert, wird diese Ausführung abgebrochen und eine `TimeoutCancellationException` ausgelöst (einen Subtyp von `CancellationException`).

{crop-start: 2}
```kotlin
import kotlinx.coroutines.*

suspend fun test(): Int = withTimeout(1500) {
    delay(1000)
    println("Still thinking")
    delay(1000)
    println("Done!")
    42
}

suspend fun main(): Unit = coroutineScope {
    try {
        test()
    } catch (e: TimeoutCancellationException) {
        println("Cancelled")
    }
    delay(1000) // Extra timeout does not help,
    // `test` body was cancelled
}
// (1 sec)
// Still thinking
// (0.5 sec)
// Cancelled
```

Die Funktion `withTimeout` ist besonders nützlich für das Testen. Sie kann verwendet werden, um zu testen, ob eine Funktion mehr oder weniger als eine bestimmte Zeit benötigt. Wenn sie innerhalb von `runTest` verwendet wird, funktioniert sie in virtueller Zeit. Wir verwenden sie auch innerhalb von `runBlocking`, um lediglich die Ausführungszeit einer Funktion zu begrenzen (dies entspricht dann der Einstellung von `timeout` bei `@Test`).

{crop-start: 5}
```kotlin
// will not start, because runTest requires kotlinx-coroutines-test, but you can copy it to your project
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Test

class Test {
    @Test
    fun testTime2() = runTest {
        withTimeout(1000) {
            // something that should take less than 1000
            delay(900) // virtual time
        }
    }

    @Test(expected = TimeoutCancellationException::class)
    fun testTime1() = runTest {
        withTimeout(1000) {
            // something that should take more than 1000
            delay(1100) // virtual time
        }
    }

    @Test
    fun testTime3() = runBlocking {
        withTimeout(1000) {
            // normal test, that should not take too long
            delay(900) // really waiting 900 ms
        }
    }
}
```


Achten Sie darauf, dass `withTimeout` eine `TimeoutCancellationException` auslöst, die ein Subtyp von `CancellationException` ist (die gleiche Ausnahme, die ausgelöst wird, wenn eine Coroutine abgebrochen wird). Also, wenn diese Ausnahme in einem Coroutine-Ersteller ausgelöst wird, wird nur dieser abgebrochen und es wirkt sich nicht auf seine Eltern aus (wie im vorherigen Kapitel erklärt).

{crop-start: 2}
```kotlin
import kotlinx.coroutines.*

suspend fun main(): Unit = coroutineScope {
    launch { // 1
        launch { // 2, cancelled by its parent
            delay(2000)
            println("Will not be printed")
        }
        withTimeout(1000) { // we cancel launch
            delay(1500)
        }
    }
    launch { // 3
        delay(2000)
        println("Done")
    }
}
// (2 sec)
// Done
```


Im obigen Beispiel dauert `delay(1500)` länger als `withTimeout(1000)` erwartet, daher wirft es `TimeoutCancellationException`. Der Ausnahmefehler wird durch `launch` von 1 aufgefangen, und es wird abgebrochen und seine Unterprozesse ebenfalls, also `launch` von 2. `launch` gestartet bei 3 ist ebenfalls nicht betroffen.

Eine weniger aggressive Variante von `withTimeout` ist `withTimeoutOrNull`, die keine Ausnahme wirft. Wenn das Timeout überschritten wird, wird einfach der Prozess abgebrochen und `null` zurückgegeben. Ich finde `withTimeoutOrNull` nützlich, um Funktionen zu umschließen, bei denen zu lange Wartezeiten signalisieren, dass etwas schief gelaufen ist. Beispielsweise bei Netzwerkoperationen: Wenn wir über 5 Sekunden auf eine Antwort warten, ist es unwahrscheinlich, dass wir sie jemals erhalten (einige Bibliotheken könnten unendlich lange warten).

{crop-start: 4}
```kotlin
import kotlinx.coroutines.*

class User()

suspend fun fetchUser(): User {
    // Runs forever
    while (true) {
        yield()
    }
}

suspend fun getUserOrNull(): User? =
    withTimeoutOrNull(5000) {
        fetchUser()
    }

suspend fun main(): Unit = coroutineScope {
    val user = getUserOrNull()
    println("User: $user")
}
// (5 sec)
// User: null
```

### Verknüpfung von Coroutine-Scope-Funktionen

Wenn Sie Funktionen von zwei Coroutine-Scope-Funktionen nutzen müssen, müssen Sie eine in der anderen verwenden. Beispielsweise, um sowohl ein Timeout als auch einen Dispatcher zu definieren, können Sie `withTimeoutOrNull` innerhalb von `withContext` verwenden.

```kotlin
suspend fun calculateAnswerOrNull(): User? =
    withContext(Dispatchers.Default) {
        withTimeoutOrNull(1000) {
            calculateAnswer()
        }
    }
```


### Zusätzliche Operationen

Stellen Sie sich einen Fall vor, in dem Sie während einer Verarbeitung eine zusätzliche Operation ausführen müssen. Zum Beispiel, nachdem Sie ein Benutzerprofil angezeigt haben, möchten Sie eine Anfrage zu Analysezwecken senden. Das machen die Leute oft mit einem regulären `launch` im gleichen Kontext:


```kotlin
class ShowUserDataUseCase(
    private val repo: UserDataRepository,
    private val view: UserDataView,
) {

    suspend fun showUserData() = coroutineScope {
        val name = async { repo.getName() }
        val friends = async { repo.getFriends() }
        val profile = async { repo.getProfile() }
        val user = User(
            name = name.await(),
            friends = friends.await(),
            profile = profile.await()
        )
        view.show(user)
        launch { repo.notifyProfileShown() }
    }
}
```


Es gibt jedoch einige Probleme mit diesem Ansatz. Erstens, dieses `launch` bewirkt hier nichts, weil `coroutineScope` sowieso auf seine Fertigstellung warten muss. Wenn Sie als Entwickler also einen Fortschrittsbalken beim Aktualisieren der Ansicht anzeigen, muss der Benutzer abwarten, bis dieses `notifyProfileShown` ebenfalls abgeschlossen ist. Das ergibt wenig Sinn.


```kotlin
fun onCreate() {
    viewModelScope.launch {
        _progressBar.value = true
        showUserData()
        _progressBar.value = false
    }
}
```


Das zweite Problem ist die Stornierung. Coroutines sind standardmäßig so konzipiert, dass sie andere Operationen abbrechen, wenn ein Fehler auftritt. Das ist ideal für wesentliche Operationen. Wenn `getProfile` einen Fehler aufweist, sollten wir `getName` und `getFriends` abbrechen, da ihre Antwort sowieso nutzlos wäre. Es macht jedoch wenig Sinn, einen Prozess nur deshalb abzubrechen, weil ein Analytics-Aufruf fehlgeschlagen ist.

Was sollten wir also tun? Wenn es eine zusätzliche (nicht wesentliche) Operation gibt, die den Hauptprozess nicht beeinträchtigen sollte, ist es besser, diese in einem separaten Scope zu starten. Das Anlegen Ihres eigenen Scopes ist einfach. In diesem Beispiel legen wir ein `analyticsScope` an.


```kotlin
val analyticsScope = CoroutineScope(SupervisorJob())
```


Für Unit-Tests und die Steuerung dieses Geltungsbereichs ist es besser, diesen über einen Konstruktor einzufügen:


```kotlin
class ShowUserDataUseCase(
    private val repo: UserDataRepository,
    private val view: UserDataView,
    private val analyticsScope: CoroutineScope,
) {

    suspend fun showUserData() = coroutineScope {
        val name = async { repo.getName() }
        val friends = async { repo.getFriends() }
        val profile = async { repo.getProfile() }
        val user = User(
            name = name.await(),
            friends = friends.await(),
            profile = profile.await()
        )
        view.show(user)
        analyticsScope.launch { repo.notifyProfileShown() }
    }
}
```


Es ist gängig, Operationen auf einem injizierten Scope-Objekt zu starten. Das Übergeben eines Scopes signalisiert klar, dass eine solche Klasse unabhängige Aufrufe initiieren kann. Das bedeutet, dass pausierende Funktionen möglicherweise nicht auf das Beenden aller von ihnen gestarteten Operationen warten. Wenn kein Scope übergeben wird, können wir davon ausgehen, dass pausierende Funktionen nicht abschließen, bis alle ihre Operationen beendet sind.

### Zusammenfassung

Coroutine Scope-Funktionen sind sehr nützlich, insbesondere da sie in jeder pausierenden Funktion verwendet werden können. Meistens werden sie eingesetzt, um den gesamten Funktionskörper einzuschließen. Obwohl sie oft dazu genutzt werden, um eine Reihe von Aufrufen mit einem Scope einzuschließen (insbesondere `withContext`), hoffe ich, dass Sie ihre Nützlichkeit erkennen können. Sie sind ein essentieller Bestandteil des Kotlin Coroutines Ökosystems. Sie werden im Verlauf des Buches sehen, wie wir sie einsetzen.

