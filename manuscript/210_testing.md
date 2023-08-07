
## Testen von Kotlin Coroutines

Das Testen von suspendierenden Funktionen unterscheidet sich in den meisten Fällen nicht vom Testen normaler Funktionen. Werfen Sie einen Blick auf die untenstehende `fetchUserData` aus `FetchUserUseCase`. Dank einiger Fake-Objekte[^210_1] (oder Mocks[^210_2]) und einfachen Assert-Anweisungen kann überprüft werden, ob sie die Daten wie erwartet anzeigt.

{crop-start: 7, crop-end: 52}
```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

//sampleStart
class FetchUserUseCase(
    private val repo: UserDataRepository,
) {

    suspend fun fetchUserData(): User = coroutineScope {
        val name = async { repo.getName() }
        val friends = async { repo.getFriends() }
        val profile = async { repo.getProfile() }
        User(
            name = name.await(),
            friends = friends.await(),
            profile = profile.await()
        )
    }
}

class FetchUserDataTest {

    @Test
    fun `should construct user`() = runBlocking {
        // given
        val repo = FakeUserDataRepository()
        val useCase = FetchUserUseCase(repo)

        // when
        val result = useCase.fetchUserData()

        // then
        val expectedUser = User(
            name = "Ben",
            friends = listOf(Friend("some-friend-id-1")),
            profile = Profile("Example description")
        )
        assertEquals(expectedUser, result)
    }

    class FakeUserDataRepository : UserDataRepository {
        override suspend fun getName(): String = "Ben"

        override suspend fun getFriends(): List<Friend> =
            listOf(Friend("some-friend-id-1"))

        override suspend fun getProfile(): Profile =
            Profile("Example description")
    }
}
//sampleEnd

interface UserDataRepository {
    suspend fun getName(): String
    suspend fun getFriends(): List<Friend>
    suspend fun getProfile(): Profile
}

data class User(
    val name: String,
    val friends: List<Friend>,
    val profile: Profile
)

data class Friend(val id: String)
data class Profile(val description: String)
```

> Meine Methode zum Testen von Logik sollte nicht als Referenz verwendet werden. Es gibt viele widersprüchliche Vorstellungen davon, wie Tests aussehen sollten. Ich habe hier Fakes anstelle von Mocks verwendet, um keine externe Bibliothek einzuführen (ich persönlich bevorzuge sie auch). Ich habe auch versucht, alle Tests minimalistisch zu halten, um sie leichter lesbar zu machen.

Ähnlich verhält es sich in vielen anderen Fällen, wenn wir daran interessiert sind, was die "suspending function" tut, brauchen wir praktisch nichts anderes als `runBlocking` und klassische Tools für Assertions. So sehen Unit Tests in vielen Projekten aus. Hier sind einige Unit Tests aus dem Backend der Kt. Academy:

```kotlin
class UserTests : KtAcademyFacadeTest() {

    @Test
    fun `should modify user details`() = runBlocking {
        // given
        thereIsUser(aUserToken, aUserId)

        // when
        facade.updateUserSelf(
            aUserToken,
            PatchUserSelfRequest(
                bio = aUserBio,
                bioPl = aUserBioPl,
                publicKey = aUserPublicKey,
                customImageUrl = aCustomImageUrl
            )
        )

        // then
        with(findUser(aUserId)) {
            assertEquals(aUserBio, bio)
            assertEquals(aUserBioPl, bioPl)
            assertEquals(aUserPublicKey, publicKey)
            assertEquals(aCustomImageUrl, customImageUrl)
        }
    }

    //...
}
```

IntegrationTests können auf die gleiche Weise implementiert werden. Wir verwenden einfach `runBlocking`, und es gibt fast keinen Unterschied zwischen dem Testen, wie suspending und blocking Funktionen sich verhalten.

### Testen von Zeitabhängigkeiten

Der Unterschied kommt ins Spiel, wenn wir beginnen wollen, Zeitabhängigkeiten zu testen. Denken Sie zum Beispiel an die folgenden Funktionen:

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


Beide Funktionen erzeugen das gleiche Ergebnis; der Unterschied besteht darin, dass die erste dies nacheinander tut, während die zweite dies gleichzeitig tut. Der Unterschied besteht darin, dass wenn das Abrufen des Profils und der Freunde jeweils 1 Sekunde dauert, würde die erste Funktion ungefähr 2 Sekunden benötigen, während die zweite nur 1 Sekunde benötigen würde. Wie würden Sie das testen?

Beachten Sie, dass der Unterschied nur auftritt, wenn die Ausführung von `getProfile` und `getFriends` tatsächlich Zeit in Anspruch nimmt. Wenn sie unmittelbar ausgeführt werden, sind beide Methoden zur Erzeugung des Benutzers nicht voneinander zu unterscheiden. Daher könnten wir uns selbst helfen, indem wir die Ausführung von simulierten Funktionen mit `delay` verzögern, um ein Szenario mit verzögerter Datenladung nachzuahmen:


```kotlin
class FakeDelayedUserDataRepository : UserDataRepository {

    override suspend fun getProfile(): Profile {
        delay(1000)
        return Profile("Example description")
    }

    override suspend fun getFriends(): List<Friend> {
        delay(1000)
        return listOf(Friend("some-friend-id-1"))
    }
}
```


Jetzt wird der Unterschied in den Unit-Tests sichtbar: Der Aufruf von `produceCurrentUserSeq` wird etwa 1 Sekunde dauern, und der Aufruf von `produceCurrentUserSym` wird etwa 2 Sekunden dauern. Das Problem ist, dass wir nicht möchten, dass ein einzelner Unit-Test so viel Zeit in Anspruch nimmt. In unseren Projekten haben wir typischerweise Tausende von Unit-Tests, und wir möchten, dass alle so schnell wie möglich ausgeführt werden. Wie kann man beides unter einen Hut bekommen? Dafür müssen wir in simulierter Zeit arbeiten. Hier kommt die `kotlinx-coroutines-test` Bibliothek mit ihrem `StandardTestDispatcher` zur Rettung.

> Dieses Kapitel stellt die kotlinx-coroutines-test Funktionen und Klassen vor, die in Version 1.6 eingeführt wurden. Wenn Sie eine ältere Version dieser Bibliothek verwenden, sollte es in den meisten Fällen ausreichen, `runBlockingTest` anstelle von `runTest`, `TestCoroutineDispatcher` anstelle von `StandardTestDispatcher`, und `TestCoroutineScope` anstelle von `TestScope` zu verwenden. Auch `advanceTimeBy` in älteren Versionen ist wie `advanceTimeBy` und `runCurrent` in Versionen neuer als 1.6. Die detaillierten Unterschiede sind im Migrationsleitfaden unter [https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-test/MIGRATION.md](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-test/MIGRATION.md) beschrieben.

### `TestCoroutineScheduler` und `StandardTestDispatcher`

Wenn wir `delay` aufrufen, wird unsere Coroutine angehalten und nach einer festgelegten Zeit fortgesetzt. Dieses Verhalten kann dank `TestCoroutineScheduler` aus `kotlinx-coroutines-test` geändert werden, das `delay` in virtueller Zeit arbeiten lässt, die vollständig simuliert ist und nicht von der realen Zeit abhängt.


```kotlin
fun main() {
    val scheduler = TestCoroutineScheduler()

    println(scheduler.currentTime) // 0
    scheduler.advanceTimeBy(1_000)
    println(scheduler.currentTime) // 1000
    scheduler.advanceTimeBy(1_000)
    println(scheduler.currentTime) // 2000
}
```

> `TestCoroutineScheduler` sowie `StandardTestDispatcher`, `TestScope` und `runTest` sind immer noch experimentell.

Um `TestCoroutineScheduler` bei Koroutinen zu verwenden, sollten wir einen Dispatcher verwenden, der ihn unterstützt. Die Standardoption ist `StandardTestDispatcher`. Im Gegensatz zu den meisten Dispatchern wird er nicht nur dazu benutzt, zu bestimmen auf welchem Thread eine Koroutine laufen soll. Koroutinen, die mit einem solchen Dispatcher gestartet werden, werden nicht ausgeführt, bis wir die virtuelle Zeit weiterleiten. Die typischste Art, dies zu tun, ist die Verwendung von `advanceUntilIdle`, welches die virtuelle Zeit weiterleitet und alle Operationen ausführt, die zu diesem Zeitpunkt aufgerufen werden würden, wenn es sich um die reale Zeit handeln würde.

```kotlin
fun main() {
    val scheduler = TestCoroutineScheduler()
    val testDispatcher = StandardTestDispatcher(scheduler)

    CoroutineScope(testDispatcher).launch {
        println("Some work 1")
        delay(1000)
        println("Some work 2")
        delay(1000)
        println("Coroutine done")
    }

    println("[${scheduler.currentTime}] Before")
    scheduler.advanceUntilIdle()
    println("[${scheduler.currentTime}] After")
}
// [0] Before
// Some work 1
// Some work 2
// Coroutine done
// [2000] After
```

`StandardTestDispatcher` erstellt standardmäßig einen `TestCoroutineScheduler`, daher müssen wir dies nicht ausdrücklich tun. Wir können darauf mit der Eigenschaft `scheduler` zugreifen.

```kotlin
fun main() {
    val dispatcher = StandardTestDispatcher()

    CoroutineScope(dispatcher).launch {
        println("Some work 1")
        delay(1000)
        println("Some work 2")
        delay(1000)
        println("Coroutine done")
    }

    println("[${dispatcher.scheduler.currentTime}] Before")
    dispatcher.scheduler.advanceUntilIdle()
    println("[${dispatcher.scheduler.currentTime}] After")
}
// [0] Before
// Some work 1
// Some work 2
// Coroutine done
// [2000] After
```


Es ist wichtig zu beachten, dass `StandardTestDispatcher` die Zeit nicht selbstständig voranschreitet. Wir müssen dafür sorgen, dass die Zeit voranschreitet, ansonsten wird unsere coroutine nie wieder aufgenommen.


```kotlin
fun main() {
    val testDispatcher = StandardTestDispatcher()

    runBlocking(testDispatcher) {
        delay(1)
        println("Coroutine done")
    }
}
// (code runs forever)
```


Eine andere Möglichkeit, die Zeit voranzuschreiten, ist `advanceTimeBy` mit einer konkreten Anzahl von Millisekunden zu verwenden. Diese Funktion führt alle Operationen aus, die in der Zwischenzeit stattgefunden haben. Das bedeutet, wenn wir um 2 Millisekunden vorschieben, wird alles, was weniger als diese Zeit verzögert wurde, fortgesetzt. Um Operationen fortzusetzen, die genau zur zweiten Millisekunde wieder aufgenommen werden sollten, müssen wir zusätzlich die Funktion `runCurrent` aufrufen.


```kotlin
fun main() {
    val testDispatcher = StandardTestDispatcher()

    CoroutineScope(testDispatcher).launch {
        delay(1)
        println("Done1")
    }
    CoroutineScope(testDispatcher).launch {
        delay(2)
        println("Done2")
    }
    testDispatcher.scheduler.advanceTimeBy(2) // Done
    testDispatcher.scheduler.runCurrent() // Done2
}
```

Hier ist ein größeres Beispiel für die Verwendung von `advanceTimeBy` zusammen mit `runCurrent`.

```kotlin
fun main() {
    val testDispatcher = StandardTestDispatcher()

    CoroutineScope(testDispatcher).launch {
        delay(2)
        print("Done")
    }

    CoroutineScope(testDispatcher).launch {
        delay(4)
        print("Done2")
    }

    CoroutineScope(testDispatcher).launch {
        delay(6)
        print("Done3")
    }

    for (i in 1..5) {
        print(".")
        testDispatcher.scheduler.advanceTimeBy(1)
        testDispatcher.scheduler.runCurrent()
    }
}
// ..Done..Done2.
```

> Wie funktioniert das im Hintergrund? Wenn `delay` aufgerufen wird, prüft es, ob der Dispatcher (Klasse mit dem `ContinuationInterceptor` Schlüssel) das `Delay` Interface (`StandardTestDispatcher` tut das) implementiert. Für solche Dispatcher ruft es deren `scheduleResumeAfterDelay` Funktion statt der von `DefaultDelay`, welche in Echtzeit wartet, auf.

Um zu sehen, dass die virtuelle Zeit wirklich unabhängig von der Echtzeit ist, siehst du im folgenden Beispiel. Das Hinzufügen von `Thread.sleep` hat keinen Einfluss auf die Coroutine mit `StandardTestDispatcher`. Beachte auch, dass der Aufruf von `advanceUntilIdle` nur wenige Millisekunden dauert, also wartet er nicht auf Echtzeit. Er beschleunigt sofort die virtuelle Zeit und führt Coroutine-Operationen aus.

```kotlin
fun main() {
    val dispatcher = StandardTestDispatcher()

    CoroutineScope(dispatcher).launch {
        delay(1000)
        println("Coroutine done")
    }

    Thread.sleep(Random.nextLong(2000)) // Does not matter
    // how much time we wait here, it will not influence
    // the result

    val time = measureTimeMillis {
       println("[${dispatcher.scheduler.currentTime}] Before")
       dispatcher.scheduler.advanceUntilIdle()
       println("[${dispatcher.scheduler.currentTime}] After")
    }
    println("Took $time ms")
}
// [0] Before
// Coroutine done
// [1000] After
// Took 15 ms (or other small number)
```


In den vorherigen Beispielen haben wir `StandardTestDispatcher` verwendet und es in einen Scope eingewickelt. Stattdessen könnten wir `TestScope` einsetzen, welches das Gleiche erreicht (und dabei alle Exceptions mit `CoroutineExceptionHandler` sammelt). Das Besondere daran ist, dass wir in diesem Scope auch Funktionen wie `advanceUntilIdle`, `advanceTimeBy` oder die Eigenschaft `currentTime` nutzen können, die alle an den Scheduler weitergeleitet werden, der in diesem Scope genutzt wird. Das ist sehr nützlich.


```kotlin
fun main() {
    val scope = TestScope()

    scope.launch {
        delay(1000)
        println("First done")
        delay(1000)
        println("Coroutine done")
    }

    println("[${scope.currentTime}] Before") // [0] Before
    scope.advanceTimeBy(1000)
    scope.runCurrent() // First done
    println("[${scope.currentTime}] Middle") // [1000] Middle
    scope.advanceUntilIdle() // Coroutine done
    println("[${scope.currentTime}] After") // [2000] After
}
```


Wir werden später sehen, dass `StandardTestDispatcher` oft direkt auf Android verwendet wird, um ViewModels, Presenters, Fragments usw. zu testen. Wir könnten es auch verwenden, um die Funktionen `produceCurrentUserSeq` und `produceCurrentUserSym` zu testen, indem wir sie in einer Coroutine starten, die Zeit vorrücken, bis sie im Leerlauf ist und überprüfen, wie viel simulierte Zeit sie in Anspruch genommen haben. Dies wäre jedoch ziemlich kompliziert; stattdessen sollten wir `runTest` verwenden, das für solche Zwecke konzipiert ist.

### runTest

`runTest` ist die am häufigsten verwendete Funktion aus `kotlinx-coroutines-test`. Es startet eine Coroutine mit `TestScope` und rückt sie sofort bis zum Leerlauf vor. Innerhalb dieser Coroutine ist der Bereich vom Typ `TestScope`, daher können wir `currentTime` jederzeit überprüfen. So können wir nachvollziehen, wie die Zeit in unseren Coroutines verläuft, während unsere Tests nur Millisekunden in Anspruch nehmen.


```kotlin
class TestTest {

    @Test
    fun test1() = runTest {
        assertEquals(0, currentTime)
        delay(1000)
        assertEquals(1000, currentTime)
    }

    @Test
    fun test2() = runTest {
        assertEquals(0, currentTime)
        coroutineScope {
            launch { delay(1000) }
            launch { delay(1500) }
            launch { delay(2000) }
        }
        assertEquals(2000, currentTime)
    }
}
```

Kehren wir zu unseren Funktionen zurück, wo wir Benutzerdaten sequenziell und parallel geladen haben. Mit `runTest` ist es einfach, sie zu testen. Angenommen, unser simuliertes Repository benötigt 1 Sekunde für jeden Funktionsaufruf, sollte die sequenzielle Verarbeitung 2 Sekunden dauern, und die parallele Verarbeitung sollte nur 1 Sekunde dauern. Dank unserer Verwendung von virtueller Zeit, erfolgen unsere Tests sofort, und die Werte von `currentTime` sind präzise.

```kotlin
@Test
fun `Should produce user sequentially`() = runTest {
    // given
    val userDataRepository = FakeDelayedUserDataRepository()
    val useCase = ProduceUserUseCase(userDataRepository)

    // when
    useCase.produceCurrentUserSeq()

    // then
    assertEquals(2000, currentTime)
}

@Test
fun `Should produce user simultaneously`() = runTest {
    // given
    val userDataRepository = FakeDelayedUserDataRepository()
    val useCase = ProduceUserUseCase(userDataRepository)

    // when
    useCase.produceCurrentUserSym()

    // then
    assertEquals(1000, currentTime)
}
```


Da es sich um einen wichtigen Anwendungsfall handelt, schauen wir uns ein vollständiges Beispiel für das Testen einer sequenziellen Funktion mit allen erforderlichen Klassen und Schnittstellen an:


```kotlin
class FetchUserUseCase(
    private val repo: UserDataRepository,
) {

    suspend fun fetchUserData(): User = coroutineScope {
        val name = async { repo.getName() }
        val friends = async { repo.getFriends() }
        val profile = async { repo.getProfile() }
        User(
            name = name.await(),
            friends = friends.await(),
            profile = profile.await()
        )
    }
}

class FetchUserDataTest {

    @Test
    fun `should load data concurrently`() = runTest {
        // given
        val userRepo = FakeUserDataRepository()
        val useCase = FetchUserUseCase(userRepo)

        // when
        useCase.fetchUserData()

        // then
        assertEquals(1000, currentTime)
    }

    @Test
    fun `should construct user`() = runTest {
        // given
        val userRepo = FakeUserDataRepository()
        val useCase = FetchUserUseCase(userRepo)

        // when
        val result = useCase.fetchUserData()

        // then
        val expectedUser = User(
            name = "Ben",
            friends = listOf(Friend("some-friend-id-1")),
            profile = Profile("Example description")
        )
        assertEquals(expectedUser, result)
    }

    class FakeUserDataRepository : UserDataRepository {
        override suspend fun getName(): String {
            delay(1000)
            return "Ben"
        }

        override suspend fun getFriends(): List<Friend> {
            delay(1000)
            return listOf(Friend("some-friend-id-1"))
        }

        override suspend fun getProfile(): Profile {
            delay(1000)
            return Profile("Example description")
        }
    }
}

interface UserDataRepository {
    suspend fun getName(): String
    suspend fun getFriends(): List<Friend>
    suspend fun getProfile(): Profile
}

data class User(
    val name: String,
    val friends: List<Friend>,
    val profile: Profile
)

data class Friend(val id: String)
data class Profile(val description: String)
```

{width: 100%}
![`runTest` enthält `TestScope`, das `StandardTestDispatcher` enthält, das `TestCoroutineScheduler` enthält.](scheduler_onion.png)

### Hintergrund Geltungsbereich

Die `runTest` Funktion erstellt einen Geltungsbereich; wie alle solche Funktionen, wartet sie auf die Beendigung ihrer Unterprozesse. Das bedeutet, wenn Sie einen Prozess starten, der nie endet, wird Ihr Test nie enden.

```kotlin
@Test
fun `should increment counter`() = runTest {
    var i = 0
    launch {
        while (true) {
            delay(1000)
            i++
        }
    }
    
    delay(1001)
    assertEquals(1, i)
    delay(1000)
    assertEquals(2, i)
    
    // Test would pass if we added
    // coroutineContext.job.cancelChildren()
}
```


Für solche Situationen bietet `runTest` den `backgroundScope` an. Dies ist ein Geltungsbereich, der ebenfalls auf virtueller Zeit arbeitet, jedoch wird `runTest` nicht auf seine Fertigstellung warten. Aus diesem Grund wird der untenstehende Test problemlos bestanden. Wir nutzen den `backgroundScope`, um alle Prozesse zu starten, für die unser Test nicht warten soll.


```kotlin
@Test
fun `should increment counter`() = runTest {
    var i = 0
    backgroundScope.launch {
        while (true) {
            delay(1000)
            i++
        }
    }
    
    
    delay(1001)
    assertEquals(1, i)
    delay(1000)
    assertEquals(2, i)
}
```


### Testen der Abbruchbedingungen und Übertragung des Kontextes

Wenn Sie prüfen möchten, ob eine bestimmte Funktion die strukturierte parallele Ausführung berücksichtigt, ist der einfachste Weg, den Kontext von einer suspendierenden Funktion zu erfassen und dann zu überprüfen, ob dieser den erwarteten Wert enthält oder ob seine Aufgabe den entsprechenden Status hat. Als Beispiel nehmen wir die `mapAsync` Funktion, die ich im Kapitel *Rezepte* näher erläutere.


```kotlin
suspend fun <T, R> Iterable<T>.mapAsync(
    transformation: suspend (T) -> R
): List<R> = coroutineScope {
    this@mapAsync.map { async { transformation(it) } }
        .awaitAll()
}
```


Diese Funktion sollte Elemente asynchron mapeen, während ihre Reihenfolge beibehalten wird. Dieses Verhalten kann durch den folgenden Test überprüft werden:


```kotlin
@Test
fun `should map async and keep elements order`() = runTest {
    val transforms = listOf(
        suspend { delay(3000); "A" },
        suspend { delay(2000); "B" },
        suspend { delay(4000); "C" },
        suspend { delay(1000); "D" },
    )
    
    val res = transforms.mapAsync { it() }
    assertEquals(listOf("A", "B", "C", "D"), res)
    assertEquals(4000, currentTime)
}
```


Aber das ist noch nicht alles. Wir erwarten, dass eine richtig implementierte "suspending function" die Strukturierte Nebenläufigkeit respektiert.
Die einfachste Möglichkeit, dies zu überprüfen, besteht darin, einen Kontext wie `CoroutineName` für die übergeordnete Coroutine anzugeben, und dann zu überprüfen, ob dieser in der `transformation` Funktion immer noch derselbe ist.
Um den Kontext einer "suspending function" zu erfassen, können wir die Funktion `currentCoroutineContext` oder die Eigenschaft `coroutineContext` verwenden. In Lambda-Ausdrücken, die in "coroutine builders" oder "scope functions" verschachtelt sind, sollten wir die Funktion `currentCoroutineContext` verwenden, da die Eigenschaft `coroutineContext` aus `CoroutineScope` Priorität über die Eigenschaft hat, die den aktuellen Coroutine-Kontext bereitstellt.


```kotlin
@Test
fun `should support context propagation`() = runTest {
    var ctx: CoroutineContext? = null
    
    val name1 = CoroutineName("Name 1")
    withContext(name1) {
        listOf("A").mapAsync {
            ctx = currentCoroutineContext()
            it
        }
        assertEquals(name1, ctx?.get(CoroutineName))
    }
    
    val name2 = CoroutineName("Some name 2")
    withContext(name2) {
        listOf(1, 2, 3).mapAsync {
            ctx = currentCoroutineContext()
            it
        }
        assertEquals(name2, ctx?.get(CoroutineName))
    }
}
```

Der einfachste Weg, um eine Abbruch zu testen, besteht darin, die innere Funktion zu erfassen und ihren Abbruch nach dem Abbruch der äußeren Coroutine zu bestätigen.

```kotlin
@Test
fun `should support cancellation`() = runTest {
    var job: Job? = null
    
    val parentJob = launch {
        listOf("A").mapAsync {
            job = currentCoroutineContext().job
            delay(Long.MAX_VALUE)
        }
    }
    
    
    delay(1000)
    parentJob.cancel()
    assertEquals(true, job?.isCancelled)
}
```

Ich denke, solche Tests sind nicht in den meisten Anwendungen erforderlich, aber ich finde sie in Programmbibliotheken nützlich. Es ist nicht so offensichtlich, dass strukturierte Parallelität respektiert wird. Beide oben genannten Tests würden scheitern, wenn `async` in einem äußeren Bereich gestartet würde.

```kotlin
// Incorrect implementation, that would make above tests fail
suspend fun <T, R> Iterable<T>.mapAsync(
    transformation: suspend (T) -> R
): List<R> =
    this@mapAsync
        .map { GlobalScope.async { transformation(it) } }
        .awaitAll()
```

### `UnconfinedTestDispatcher`

Neben dem `StandardTestDispatcher` haben wir auch `UnconfinedTestDispatcher`. Der größte Unterschied besteht darin, dass der `StandardTestDispatcher` keine Operationen auslöst, bis wir seinen Planer verwenden. Der `UnconfinedTestDispatcher` führt unmittelbar alle Operationen vor der ersten Verzögerung auf laufenden Koroutinen aus, weshalb der untenstehende Code "C" anzeigt.

```kotlin
fun main() {
    CoroutineScope(StandardTestDispatcher()).launch {
        print("A")
        delay(1)
        print("B")
    }
    CoroutineScope(UnconfinedTestDispatcher()).launch {
        print("C")
        delay(1)
        print("D")
    }
}
// C
```


Die Funktion `runTest` wurde in Version 1.6 von `kotlinx-coroutines-test` implementiert. Zuvor haben wir `runBlockingTest` benutzt, welches stark `runTest` mit `UnconfinedTestDispatcher` ähnelt. Wenn du also direkt von `runBlockingTest` zu `runTest` migrieren möchtest, könnten unsere Tests dann so aussehen:


```kotlin
@Test
fun testName() = runTest(UnconfinedTestDispatcher()) {
    //...
}
```


### Nutzung von Mocks

Die Anwendung von `delay` in Fakes ist leicht, aber nicht sehr deutlich. Viele Entwickler bevorzugen es, `delay` in der Testfunktion auszuführen. Eine Möglichkeit dies umzusetzen, ist die Nutzung von Mocks[^210_3]:


```kotlin
@Test
fun `should load data concurrently`() = runTest {
    // given
    val userRepo = mockk<UserDataRepository>()
    coEvery { userRepo.getName() } coAnswers {
        delay(600)
        aName
    }
    coEvery { userRepo.getFriends() } coAnswers {
        delay(700)
        someFriends
    }
    coEvery { userRepo.getProfile() } coAnswers {
        delay(800)
        aProfile
    }
    val useCase = FetchUserUseCase(userRepo)

    // when
    useCase.fetchUserData()

    // then
    assertEquals(800, currentTime)
}
```

> Im obigen Beispiel wurde die [MockK](https://mockk.io/) Bibliothek verwendet.

### Testen von Funktionen, die den Dispatcher wechseln

Im **Dispatchers** Kapitel wurden typische Fälle vorgestellt, in denen konkrete Dispatcher festgelegt werden. Zum Beispiel verwenden wir `Dispatcher.IO` (oder einen benutzerdefinierten Dispatcher) für blockierende Anrufe, oder `Dispatchers.Default` für CPU-intensive Anrufe. Solche Funktionen müssen selten gleichzeitig ausgeführt werden, daher ist es normalerweise ausreichend, sie mit `runBlocking` zu testen. Dieser Fall ist einfach und kaum zu unterscheiden vom Testen blockierender Funktionen. Betrachten Sie zum Beispiel die folgende Funktion:

```kotlin
suspend fun readSave(name: String): GameState =
    withContext(Dispatchers.IO) {
        reader.readCsvBlocking(name, GameState::class.java)
    }

suspend fun calculateModel() =
    withContext(Dispatchers.Default) {
        model.fit(
            dataset = newTrain,
            epochs = 10,
            batchSize = 100,
            verbose = false
        )
    }
```

Wir könnten das Verhalten solcher Funktionen mit Tests überprüfen, die mit `runBlocking` versehen sind, aber wie wäre es, wenn wir kontrollieren, ob diese Funktionen den Dispatcher tatsächlich ändern? Dies kann ebenfalls erreicht werden, wenn wir die Funktionen, die wir aufrufen, simulieren und dabei den Namen des verwendeten Threads erfassen.

```kotlin
@Test
fun `should change dispatcher`() = runBlocking {
        // given
        val csvReader = mockk<CsvReader>()
        val startThreadName = "MyName"
        var usedThreadName: String? = null
        every {
            csvReader.readCsvBlocking(
                aFileName,
                GameState::class.java
            )
        } coAnswers {
            usedThreadName = Thread.currentThread().name
            aGameState
        }
        val saveReader = SaveReader(csvReader)

        // when
        withContext(newSingleThreadContext(startThreadName)) {
            saveReader.readSave(aFileName)
        }

        // then
        assertNotNull(usedThreadName)
        val expectedPrefix = "DefaultDispatcher-worker-"
        assert(usedThreadName!!.startsWith(expectedPrefix))
    }
```

> In der obigen Funktion konnte ich nicht Fakes benutzen, weil `CsvReader` eine Klasse ist, daher habe ich Mocks eingesetzt.

> Bitte beachten Sie, dass `Dispatchers.Default` und `Dispatchers.IO` denselben Thread-Pool teilen.

In seltenen Fällen wollen wir jedoch Zeitabhängigkeiten in Funktionen testen, die den Dispatcher ändern. Dies ist ein kniffliger Fall, weil der neue Dispatcher unseren `StandardTestDispatcher` ersetzt, sodass wir aufhören, in virtueller Zeit zu arbeiten. Um das deutlich zu machen, sollten wir die Funktion `fetchUserData` mit `withContext(Dispatchers.IO)` umgeben.

```kotlin
suspend fun fetchUserData() = withContext(Dispatchers.IO) {
    val name = async { userRepo.getName() }
    val friends = async { userRepo.getFriends() }
    val profile = async { userRepo.getProfile() }
    User(
        name = name.await(),
        friends = friends.await(),
        profile = profile.await()
    )
}
```

Nun werden all unsere zuvor implementierten Tests sich in Echtzeit aufhalten, und `currentTime` wird weiterhin `0` zurückgeben. Der einfachste Weg, dies zu verhindern, besteht darin, den Dispatcher über einen Konstruktor zu injizieren und ihn in den Unit-Tests zu ersetzen.

```kotlin
class FetchUserUseCase(
    private val userRepo: UserDataRepository,
    private val ioDispatcher: CoroutineDispatcher =
        Dispatchers.IO
) {

    suspend fun fetchUserData() = withContext(ioDispatcher) {
        val name = async { userRepo.getName() }
        val friends = async { userRepo.getFriends() }
        val profile = async { userRepo.getProfile() }
        User(
            name = name.await(),
            friends = friends.await(),
            profile = profile.await()
        )
    }
}
```


Nun könnten wir in Unit-Tests anstelle von `Dispatchers.IO` den `StandardTestDispatcher` aus `runTest` verwenden. Wir können ihn mit dem Schlüssel `ContinuationInterceptor` aus dem `coroutineContext` holen.


```kotlin
val testDispatcher = this
    .coroutineContext[ContinuationInterceptor]
    as CoroutineDispatcher

val useCase = FetchUserUseCase(
    userRepo = userRepo,
    ioDispatcher = testDispatcher,
)
```

Eine weitere Möglichkeit besteht darin, `ioDispatcher` als `CoroutineContext` zu casten und in Unit-Tests durch `EmptyCoroutineContext` zu ersetzen. Das letztendliche Verhalten bleibt gleich: Die Funktion wird den Dispatcher nie verändern.

```kotlin
val useCase = FetchUserUseCase(
    userRepo = userRepo,
    ioDispatcher = EmptyCoroutineContext,
)
```


### Testen, was während der Funktionsausführung passiert

Denken Sie an eine Funktion, die während ihrer Ausführung zuerst einen Fortschrittsbalken anzeigt und diesen später ausblendet. 


```kotlin
suspend fun sendUserData() {
    val userData = database.getUserData()
    progressBarVisible.value = true
    userRepository.sendUserData(userData)
    progressBarVisible.value = false
}
```


Wenn wir nur das Endergebnis überprüfen, können wir nicht bestätigen, dass der Fortschrittsbalken seinen Zustand während der Funktionsdurchführung geändert hat. Der hilfreiche Trick in solchen Fällen ist, diese Funktion in einer neuen Coroutine zu starten und die virtuelle Zeit von außerhalb zu steuern. Beachten Sie, dass `runTest` eine Coroutine mit dem `StandardTestDispatcher` Dispatcher erstellt und seine Zeit voranschreitet, bis sie untätig ist (unter Verwendung der Funktion `advanceUntilIdle`). Das bedeutet, dass die Zeit der untergeordneten Prozesse beginnen wird, sobald das Hauptprogramm auf sie wartet, also sobald dieser abgeschlossen ist. Vorher können wir die virtuelle Zeit selbstständig vorrücken.


```kotlin
@Test
fun `should show progress bar when sending data`() = runTest {
    // given
    val database = FakeDatabase()
    val vm = UserViewModel(database)

    // when
    launch {
        vm.sendUserData()
    }

    // then
    assertEquals(false, vm.progressBarVisible.value)

    // when
    advanceTimeBy(1000)

    // then
    assertEquals(false, vm.progressBarVisible.value)

    // when
    runCurrent()

    // then
    assertEquals(true, vm.progressBarVisible.value)

    // when
    advanceUntilIdle()

    // then
    assertEquals(false, vm.progressBarVisible.value)
}
```


> Beachten Sie, dass wir dank `runCurrent` genau überprüfen können, wann sich ein Wert ändert.

Ein ähnlicher Effekt könnte erzielt werden, wenn wir `delay` verwenden würden. Das ist, als hätte man zwei unabhängige Prozesse: einer führt Aufgaben aus, während der andere prüft, ob der erste korrekt arbeitet.


```kotlin
@Test
fun `should show progress bar when sending data`() =
    runTest {
        val database = FakeDatabase()
        val vm = UserViewModel(database)
        launch {
            vm.showUserData()
        }

        // then
        assertEquals(false, vm.progressBarVisible.value)
        delay(1000)
        assertEquals(true, vm.progressBarVisible.value)
        delay(1000)
        assertEquals(false, vm.progressBarVisible.value)
    }
```


> Die Verwendung von expliziten Funktionen wie `advanceTimeBy` gilt als lesbarer, anstatt `delay` zu nutzen.

### Testen von Funktionen, die neue Coroutinen initiieren

Coroutinen müssen irgendwo beginnen. Im Backend werden sie häufig durch das Framework, welches wir nutzen (zum Beispiel Spring oder Ktor), gestartet. Es kann jedoch auch notwendig sein, dass wir selbst einen Scope erstellen und Coroutinen darauf ausführen.


```kotlin
@Scheduled(fixedRate = 5000)
fun sendNotifications() {
    notificationsScope.launch {
        val notifications = notificationsRepository
            .notificationsToSend()
        for (notification in notifications) {
            launch {
                notificationsService.send(notification)
                notificationsRepository
                    .markAsSent(notification.id)
            }
        }
    }
}
```


Wie können wir `sendNotifications` testen, wenn die Benachrichtigungen tatsächlich nebeneinander gesendet werden? Wiederum müssen wir in Unit-Tests `StandardTestDispatcher` als Teil unseres Geltungsbereichs verwenden. Wir sollten Verzögerungen beim Aufrufen von `send` und `markAsSent` einfügen.


```kotlin
@Test
fun testSendNotifications() {
    // given
    val notifications = List(100) { Notification(it) }
    val repo = FakeNotificationsRepository(
        delayMillis = 200,
        notifications = notifications,
    )
    val service = FakeNotificationsService(
        delayMillis = 300,
    )
    val testScope = TestScope()
    val sender = NotificationsSender(
        notificationsRepository = repo,
        notificationsService = service,
        notificationsScope = testScope
    )

    // when
    sender.sendNotifications()
    testScope.advanceUntilIdle()

    // then all notifications are sent and marked
    assertEquals(
        notifications.toSet(),
        service.notificationsSent.toSet()
    )
    assertEquals(
        notifications.map { it.id }.toSet(),
        repo.notificationsMarkedAsSent.toSet()
    )

    // and notifications are sent concurrently
    assertEquals(700, testScope.currentTime)
}
```

> Beachten Sie, dass `runBlocking` im oben genannten Code nicht benötigt wird. Sowohl `sendNotifications` als auch `advanceUntilIdle` sind reguläre Funktionen.

### Ersetzen des Haupt-Dispatchers

In Unit-Tests gibt es keine Hauptfunktion. Das bedeutet, wenn wir es zu benutzen versuchen, scheitern unsere Tests mit der Ausnahme "Modul mit dem Haupt-Dispatcher fehlt". Andererseits wäre es anspruchsvoll, den Haupt-Thread jedes Mal zu injizieren, daher bietet die "kotlinx-coroutines-test" Bibliothek stattdessen die `setMain` Erweiterungsfunktion auf `Dispatchers` an.

Wir definieren oft main in einer Setup-Funktion (Funktion mit `@Before` oder `@BeforeEach`) in einer Basisklasse, die von allen Unit-Tests erweitert wird. Als Ergebnis sind wir immer sicher, dass wir unsere Koroutinen auf `Dispatchers.Main` ausführen können. Wir sollten auch den Haupt-Dispatcher mit `Dispatchers.resetMain()` auf den ursprünglichen Zustand zurücksetzen.

### Testen von Android-Funktionen, die Koroutinen starten

Auf Android starten wir typischerweise Koroutinen in ViewModels, Presentern, Fragmenten oder Aktivitäten. Dies sind sehr wichtige Klassen, und wir sollten sie testen. Denken Sie an die unten gezeigte `MainViewModel` Implementierung:

```kotlin
class MainViewModel(
    private val userRepo: UserRepository,
    private val newsRepo: NewsRepository,
) : BaseViewModel() {

    private val _userName: MutableLiveData<String> =
        MutableLiveData()
    val userName: LiveData<String> = _userName

    private val _news: MutableLiveData<List<News>> =
        MutableLiveData()
    val news: LiveData<List<News>> = _news

    private val _progressVisible: MutableLiveData<Boolean> =
        MutableLiveData()
    val progressVisible: LiveData<Boolean> =
        _progressVisible

    fun onCreate() {
        viewModelScope.launch {
            val user = userRepo.getUser()
            _userName.value = user.name
        }
        viewModelScope.launch {
            _progressVisible.value = true
            val news = newsRepo.getNews()
                .sortedByDescending { it.date }
            _news.value = news
            _progressVisible.value = false
        }
    }
}
```


Anstatt `viewModelScope` könnte es unseren eigenen Gültigkeitsbereich geben und anstelle von ViewModel könnte es Presenter, Activity oder eine andere Klasse sein. Das ist für unser Beispiel egal. Wie in jeder Klasse, die Coroutinen startet, sollten wir `StandardTestDispatcher` als Teil des Gültigkeitsbereichs verwenden. Früher mussten wir einen anderen Gültigkeitsbereich durch Dependency Injection injizieren, aber jetzt gibt es einen einfacheren Weg: Auf Android verwenden wir `Dispatchers.Main` als den Standardmäßigen Dispatcher und wir können ihn dank der Funktion `Dispatchers.setMain` durch `StandardTestDispatcher` ersetzen:


```kotlin
private lateinit var testDispatcher

@Before
fun setUp() {
    testDispatcher = StandardTestDispatcher()
    Dispatchers.setMain(testDispatcher)
}

@After
fun tearDown() {
    Dispatchers.resetMain()
}
```


Nachdem der Haupt-Dispatcher auf diese Weise eingestellt wurde, werden die `onCreate` Coroutinen auf dem `testDispatcher` laufen, so dass wir ihre Zeit steuern können. Wir können die Funktion `advanceTimeBy` verwenden, um vorzutäuschen, dass eine bestimmte Zeit vergangen ist. Wir können auch `advanceUntilIdle` verwenden, um alle Coroutinen auszuführen, bis sie abgeschlossen sind.


```kotlin
class MainViewModelTests {
    private lateinit var scheduler: TestCoroutineScheduler
    private lateinit var viewModel: MainViewModel

    @BeforeEach
    fun setUp() {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        viewModel = MainViewModel(
            userRepo = FakeUserRepository(aName),
            newsRepo = FakeNewsRepository(someNews)
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        viewModel.onCleared()
    }

    @Test
    fun `should show user name and sorted news`() {
        // when
        viewModel.onCreate()
        scheduler.advanceUntilIdle()

        // then
        assertEquals(aName, viewModel.userName.value)
        val someNewsSorted =
            listOf(News(date1), News(date2), News(date3))
        assertEquals(someNewsSorted, viewModel.news.value)
    }

    @Test
    fun `should show progress bar when loading news`() {
        // given
        assertEquals(null, viewModel.progressVisible.value)

        // when
        viewModel.onCreate()

        // then
        assertEquals(false, viewModel.progressVisible.value)

        // when
        scheduler.runCurrent()

        // then
        assertEquals(true, viewModel.progressVisible.value)

        // when
        scheduler.advanceTimeBy(200)

        // then
        assertEquals(true, viewModel.progressVisible.value)

        // when
        scheduler.runCurrent()

        // then
        assertEquals(false, viewModel.progressVisible.value)
    }

    @Test
    fun `user and news are called concurrently`() {
        // when
        viewModel.onCreate()
        scheduler.advanceUntilIdle()

        // then
        assertEquals(300, testDispatcher.currentTime)
    }

    class FakeUserRepository(
        private val name: String
    ) : UserRepository {
        override suspend fun getUser(): UserData {
            delay(300)
            return UserData(name)
        }
    }

    class FakeNewsRepository(
        private val news: List<News>
    ) : NewsRepository {
        override suspend fun getNews(): List<News> {
            delay(200)
            return news
        }
    }
}
```


### Einstellen eines Test-Dispatchers mit einem Regelwerk

JUnit 4 ermöglicht es uns, Regelwerke zu verwenden. Diese sind Klassen, die Logik enthalten, die bei bestimmten Testklassen-Lebenszyklusereignissen aufgerufen werden sollten. Ein Regelwerk kann beispielsweise definieren, was vor und nach allen Tests zu tun ist, daher kann es in unserem Fall verwendet werden, um unseren Test-Dispatcher einzustellen und ihn später aufzuräumen. Hier ist eine gute Implementierung eines solchen Regelwerks:


```kotlin
class MainCoroutineRule : TestWatcher() {
    lateinit var scheduler: TestCoroutineScheduler
        private set
    lateinit var dispatcher: TestDispatcher
        private set

    override fun starting(description: Description) {
        scheduler = TestCoroutineScheduler()
        dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```


Diese Regel muss `TestWatcher` erweitern, der Testlebenszyklusmethoden wie `starting` und `finished` bereitstellt, die wir überschreiben. Sie kombiniert `TestCoroutineScheduler` und `TestDispatcher`. Vor jedem Test in einer Klasse, die diese Regel verwendet, wird `TestDispatcher` als Hauptdispatcher festgelegt. Nach jedem Test wird der Hauptdispatcher zurückgesetzt. Wir können über die `scheduler` Eigenschaft dieser Regel auf den Scheduler zugreifen.


```kotlin
class MainViewModelTests {
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    // ...

    @Test
    fun `should show user name and sorted news`() {
        // when
        viewModel.onCreate()
        mainCoroutineRule.scheduler.advanceUntilIdle()

        // then
        assertEquals(aName, viewModel.userName.value)
        val someNewsSorted =
            listOf(News(date1), News(date2), News(date3))
        assertEquals(someNewsSorted, viewModel.news.value)
    }

    @Test
    fun `should show progress bar when loading news`() {
        // given
        assertEquals(null, viewModel.progressVisible.value)

        // when
        viewModel.onCreate()

        // then
        assertEquals(true, viewModel.progressVisible.value)

        // when
        mainCoroutineRule.scheduler.advanceTimeBy(200)

        // then
        assertEquals(false, viewModel.progressVisible.value)
    }

    @Test
    fun `user and news are called concurrently`() {
        // when
        viewModel.onCreate()

        mainCoroutineRule.scheduler.advanceUntilIdle()

        // then
        assertEquals(300, mainCoroutineRule.currentTime)
    }
}
```

> Wenn Sie `advanceUntilIdle`, `advanceTimeBy`, `runCurrent` und `currentTime` direkt auf `MainCoroutineRule` aufrufen möchten, können Sie diese als Erweiterungsfunktionen und -eigenschaften definieren.

Diese Art des Testens von Kotlin-Koroutinen ist auf Android weit verbreitet. Es wird sogar in den Codelabs-Materialien von Google erklärt ([Advanced Android in Kotlin 05.3: Testing Coroutines and Jetpack integrations](https://developer.android.com/codelabs/advanced-android-kotlin-training-testing-survey#3)) (derzeit, für ältere `kotlinx-coroutines-test` API).

Es ist ähnlich wie bei JUnit 5, wo wir eine Erweiterung definieren können:

```kotlin
@ExperimentalCoroutinesApi
class MainCoroutineExtension :
    BeforeEachCallback, AfterEachCallback {

    lateinit var scheduler: TestCoroutineScheduler
        private set
    lateinit var dispatcher: TestDispatcher
        private set

    override fun beforeEach(context: ExtensionContext?) {
        scheduler = TestCoroutineScheduler()
        dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
    }

    override fun afterEach(context: ExtensionContext?) {
        Dispatchers.resetMain()
    }
}
```


Das Benutzen von `MainCoroutineExtension` ist fast identisch mit dem Benutzen der `MainCoroutineRule` Regel. Der Unterschied liegt darin, dass wir anstelle der `@get:Rule` Annotation `@JvmField` und `@RegisterExtension` nutzen müssen.


```kotlin
@JvmField
@RegisterExtension
var mainCoroutineExtension = MainCoroutineExtension()
```


### Zusammenfassung

In diesem Kapitel haben wir die wichtigsten Anwendungsfälle für das Testen von Kotlin Coroutinen besprochen. Es gibt einige Tricks, die wir kennen müssen, aber am Ende können unsere Tests sehr elegant sein und alles kann recht einfach getestet werden. Ich hoffe, Sie fühlen sich inspiriert, gute Tests in Ihren Anwendungen mit Hilfe von Kotlin Coroutinen zu schreiben.

[^210_1]: Ein Fake ist eine Klasse, die eine Schnittstelle implementiert, aber feste Daten und keine Logik enthält. Sie sind nützlich, um ein konkretes Verhalten zum Testen nachzuahmen.
[^210_2]: Mocks sind universelle simulierte Objekte, die das Verhalten von echten Objekten auf kontrollierte Weise nachahmen. Wir erstellen sie in der Regel mit Bibliotheken, wie MockK, die das Mocken von suspendierenden Funktionen unterstützen. In den untenstehenden Beispielen habe ich mich dafür entschieden, Fakes zu verwenden, um eine externe Bibliothek zu vermeiden.
[^210_3]: Nicht jeder mag Mocking. Einerseits haben Mocking-Bibliotheken viele mächtige Funktionen. Andererseits denken Sie an folgende Situation: Sie haben Tausende von Tests, und Sie ändern eine Schnittstelle eines Repositories, das von allen genutzt wird. Wenn Sie Fakes verwenden, reicht es normalerweise aus, nur einige wenige Klassen zu aktualisieren. Dies ist ein großes Problem, weshalb ich es in der Regel vorziehe, Fakes zu verwenden.

