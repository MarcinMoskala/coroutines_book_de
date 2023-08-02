
## Coroutine-Builder

Suspendierende Funktionen müssen Kontinuationen aneinander weiterreichen. Sie haben keine Schwierigkeiten, normale Funktionen aufzurufen, aber normale Funktionen können keine suspendierenden Funktionen aufrufen.

{width: 100%}
![](builders_suspend_normal.png)

Jede suspendierende Funktion muss von einer anderen suspendierenden Funktion aufgerufen werden, die von einer weiteren suspendierenden Funktion aufgerufen wird, und so fort. All das muss irgendwo starten. Dieser Anfang ist der **Coroutine-Builder**, eine Brücke von der normalen zur suspendierenden Welt[^201_1].

Wir werden uns die drei wesentlichen Coroutine-Builder ansehen, die von der kotlinx.coroutines-Bibliothek bereitgestellt werden:
* `launch`
* `runBlocking`
* `async`

Jeder dieser Builder hat seine spezifischen Anwendungsfälle. Lass uns sie näher betrachten.

### `launch` Builder

Die Funktionsweise von `launch` ist konzeptionell ähnlich dem Starten eines neuen Threads (mit der `thread` Funktion). Wir starten einfach eine Coroutine und sie läuft unabhängig, ähnlich wie ein Feuerwerk, das in die Luft geschossen wird. Wir verwenden `launch`, um einen Prozess zu starten.

{crop-start: 5, crop-end: 25}
```kotlin
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//sampleStart
fun main() {
    GlobalScope.launch {
        delay(1000L)
        println("World!")
    }
    GlobalScope.launch {
        delay(1000L)
        println("World!")
    }
    GlobalScope.launch {
        delay(1000L)
        println("World!")
    }
    println("Hello,")
    Thread.sleep(2000L)
}
// Hello,
// (1 sec)
// World!
// World!
// World!
//sampleEnd
```


Die `launch` Funktion ist eine Erweiterungsfunktion auf der `CoroutineScope` Schnittstelle. Dies ist Teil eines wichtigen Mechanismus namens *strukturierte Parallelität*, dessen Zweck es ist, eine Beziehung zwischen der übergeordneten Coroutine und einer untergeordneten Coroutine zu erstellen. Später in diesem Kapitel werden wir mehr über strukturierte Parallelität lernen, aber vorerst werden wir dieses Thema zunächst nicht weiter vertiefen, indem wir `launch` (und später `async`) auf das `GlobalScope` Objekt aufrufen. Dies ist jedoch keine Standardpraxis, da wir `GlobalScope` in realen Projekten nur selten verwenden sollten.

Eine andere Sache, die Ihnen vielleicht aufgefallen ist, ist, dass wir am Ende der `main` Funktion `Thread.sleep` aufrufen müssen. Ohne dies würde diese Funktion sofort nach dem Start der Coroutines enden, so dass sie keine Chance hätten, ihre Arbeit zu erledigen. Dies liegt daran, dass `delay` den Thread nicht blockiert: es pausiert eine Coroutine. Vielleicht erinnern Sie sich aus dem Kapitel [*Wie funktioniert die Aussetzung?*](https://kt.academy/article/cc-suspension), dass `delay` nur einen Timer setzt, um nach einer bestimmten Zeit fortzusetzen, und eine Coroutine bis dahin aussetzt. Wenn der Thread nicht blockiert ist, ist nichts beschäftigt, also steht nichts im Weg, das Programm zu beenden (später werden wir sehen, dass `Thread.sleep` bei Verwendung von strukturierter Parallelität nicht benötigt wird).

In gewisser Weise ähnelt die Arbeitsweise von `launch` einem Daemon-Thread[^201_2], ist aber viel günstiger. Diese Metapher ist anfangs nützlich, wird aber später problematisch. Ein blockierter Thread ist immer kostspielig, während das Aufrechterhalten einer ausgesetzten Coroutine fast kostenlos ist (wie im Kapitel *Coroutines unter der Haube* erklärt). Beide starten einige unabhängige Prozesse und benötigen etwas, das verhindert, dass das Programm endet, bevor sie fertig sind (im folgenden Beispiel ist dies `Thread.sleep(2000L)`).

{crop-start: 3, crop-end: 18}
```kotlin
import kotlin.concurrent.thread

//sampleStart
fun main() {
    thread(isDaemon = true) {
        Thread.sleep(1000L)
        println("World!")
    }
    thread(isDaemon = true) {
        Thread.sleep(1000L)
        println("World!")
    }
    thread(isDaemon = true) {
        Thread.sleep(1000L)
        println("World!")
    }
    println("Hello,")
    Thread.sleep(2000L)
}
//sampleEnd
```


### `runBlocking` Ersteller

Die allgemeine Regel ist, dass Koroutinen niemals Threads blockieren, sondern sie nur suspendieren sollten. Andererseits gibt es Fälle, in denen das Blockieren notwendig ist. So wie in der Hauptfunktion, müssen wir den Thread blockieren, sonst endet unser Programm zu früh. Für solche Fälle könnten wir `runBlocking` verwenden.

`runBlocking` ist ein sehr ungewöhnlicher Ersteller. Es blockiert den Thread, auf dem es gestartet wurde, wann immer seine Koroutine suspendiert wird[^201_3] (ähnlich dem Suspendieren von main). Das bedeutet, dass `delay(1000L)` innerhalb von `runBlocking` wird sich wie `Thread.sleep(1000L)` verhalten[^201_7].

{crop-start: 4, crop-end: 25}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

//sampleStart
fun main() {
    runBlocking {
        delay(1000L)
        println("World!")
    }
    runBlocking {
        delay(1000L)
        println("World!")
    }
    runBlocking {
        delay(1000L)
        println("World!")
    }
    println("Hello,")
}
// (1 sec)
// World!
// (1 sec)
// World!
// (1 sec)
// World!
// Hello,
//sampleEnd
```

{crop-start: 3, crop-end: 18}
```kotlin
import kotlin.*

//sampleStart
fun main() {
    Thread.sleep(1000L)
    println("World!")
    Thread.sleep(1000L)
    println("World!")
    Thread.sleep(1000L)
    println("World!")
    println("Hello,")
}
// (1 sec)
// World!
// (1 sec)
// World!
// (1 sec)
// World!
// Hello,
//sampleEnd
```


Es gibt tatsächlich einige spezifische Anwendungsfälle, in denen `runBlocking` verwendet wird. Einer davon ist die Main-Funktion, bei der wir den Thread blockieren müssen, da das Programm sonst beendet wird. Ein weiterer häufiger Anwendungsfall sind Unit-Tests, bei denen wir aus demselben Grund den Thread blockieren müssen.


```kotlin
fun main() = runBlocking {
    // ...
}

class MyTests {

    @Test
    fun `a test`() = runBlocking {

    }
}
```


Wir könnten `runBlocking` in unserem Beispiel verwenden, um `Thread.sleep(2000)` durch `delay(2000)` zu ersetzen. Später werden wir sehen, dass es noch nützlicher ist, sobald wir strukturierte Nebenläufigkeit einführen.

{crop-start: 3, crop-end: 23}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun main() = runBlocking {
    GlobalScope.launch {
        delay(1000L)
        println("World!")
    }
    GlobalScope.launch {
        delay(1000L)
        println("World!")
    }
    GlobalScope.launch {
        delay(1000L)
        println("World!")
    }
    println("Hello,")
    delay(2000L) // still needed
}
// Hello,
// (1 sec)
// World!
// World!
// World!
//sampleEnd
```


`runBlocking` war früher ein bedeutender Builder, aber in der modernen Programmierung wird es eher selten benutzt. Bei Unit Tests verwenden wir stattdessen oft seinen Nachfolger `runTest`, der Coroutinen in virtuelle Zeit arbeiten lässt (eine sehr nützliche Eigenschaft für das Testen, die wir im Kapitel *Testing coroutines* beschreiben werden). Wir gestalten die Hauptfunktion oft als suspendierende Funktion.

{crop-start: 3, crop-end: 23}
```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun main() {
    GlobalScope.launch {
        delay(1000L)
        println("World!")
    }
    GlobalScope.launch {
        delay(1000L)
        println("World!")
    }
    GlobalScope.launch {
        delay(1000L)
        println("World!")
    }
    println("Hello,")
    delay(2000L)
}
// Hello,
// (1 sec)
// World!
// World!
// World!
//sampleEnd
```


Das Suspendieren von `main` ist praktisch, aber wir werden vorerst weiterhin `runBlocking`[^201_4] nutzen.

### `async` Erzeuger

Der `async` Coroutine-Erzeuger ähnelt `launch`, ist jedoch darauf ausgelegt, einen Wert zu produzieren. Dieser Wert muss von der Lambda-Funktion[^201_5] zurückgegeben werden. Die `async` Funktion gibt ein Objekt vom Typ `Deferred<T>` zurück, wobei `T` der Typ des produzierten Wertes ist. `Deferred` hat eine suspendierende Methode `await`, die diesen Wert zurückgibt, sobald er bereit ist. Im folgenden Beispiel ist `42` der produzierte Wert mit dem Typ `Int`, daher wird `Deferred<Int>` zurückgegeben und `await` gibt `42` vom Typ `Int` zurück.

{crop-start: 3, crop-end: 13}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun main() = runBlocking {
    val resultDeferred: Deferred<Int> = GlobalScope.async {
        delay(1000L)
        42
    }
    // do other stuff...
    val result: Int = resultDeferred.await() // (1 sec)
    println(result) // 42
    // or just
    println(resultDeferred.await()) // 42
}
//sampleEnd
```


Genau wie der `launch` Builder startet `async` eine Koroutine sofort, wenn sie aufgerufen wird. Es bietet also eine Möglichkeit, mehrere Vorgänge gleichzeitig zu starten und dann auf alle ihre Ergebnisse zu warten. Das zurückgegebene `Deferred` speichert einen Wert in sich selbst, sobald er produziert wird, sodass er sofort von `await` zurückgegeben wird. Wenn wir jedoch `await` aufrufen, bevor der Wert produziert wird, werden wir angehalten, bis der Wert bereit ist.

{crop-start: 3, crop-end: 24}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun main() = runBlocking {
    val res1 = GlobalScope.async {
        delay(1000L)
        "Text 1"
    }
    val res2 = GlobalScope.async {
        delay(3000L)
        "Text 2"
    }
    val res3 = GlobalScope.async {
        delay(2000L)
        "Text 3"
    }
    println(res1.await())
    println(res2.await())
    println(res3.await())
}
// (1 sec)
// Text 1
// (2 sec)
// Text 2
// Text 3
//sampleEnd
```


Die Funktionsweise des `async`-Builders ähnelt sehr der von `launch`, bietet jedoch zusätzliche Unterstützung für die Rückgabe eines Wertes. Wenn alle `launch`-Funktionen durch `async` ersetzt würden, würde der Code immer noch einwandfrei funktionieren. Aber das solltest du nicht tun! `async` dient dazu, einen Wert zu erzeugen, daher sollten wir `launch` verwenden, wenn wir keinen Wert benötigen.

{crop-start: 3, crop-end: 15}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun main() = runBlocking {
    // Don't do that!
    // this is misleading to use async as launch
    GlobalScope.async {
        delay(1000L)
        println("World!")
    }
    println("Hello,")
    delay(2000L)
}
// Hello,
// (1 sec)
// World!
//sampleEnd
```

Der `async` Builder wird oft verwendet, um zwei Prozesse zu parallelisieren, wie beispielsweise das Abrufen von Daten aus zwei verschiedenen Quellen, um sie zusammenzuführen.

```kotlin
scope.launch {
    val news = async {
        newsRepo.getNews()
            .sortedByDescending { it.date }
    }
    val newsSummary = newsRepo.getNewsSummary()
    // we could wrap it with async as well,
    // but it would be redundant
    view.showNews(
        newsSummary,
        news.await()
    )
}
```


### Strukturierte Nebenläufigkeit

Wenn eine Coroutine auf `GlobalScope` gestartet wird, wartet das Programm nicht darauf. Wie bereits erwähnt, sperren Coroutinen keine Threads und nichts verhindert, dass das Programm endet. Deshalb muss im folgenden Beispiel ein zusätzliches `delay` am Ende von `runBlocking` aufgerufen werden, wenn wir "World!" ausgegeben sehen wollen.

{crop-start: 3, crop-end: 15}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun main() = runBlocking {
    GlobalScope.launch {
        delay(1000L)
        println("World!")
    }
    GlobalScope.launch {
        delay(2000L)
        println("World!")
    }
    println("Hello,")
    //    delay(3000L)
}
// Hello,
//sampleEnd
```

Warum brauchen wir an erster Stelle diesen `GlobalScope`? Es ist, weil `launch` und `async` sind Erweiterungsfunktionen auf `CoroutineScope`. Wenn Sie jedoch die Definitionen von diesen und von `runBlocking` betrachten, werden Sie feststellen, dass der `block` Parameter ein Funktionstyp ist, dessen Empfängertyp ebenfalls `CoroutineScope` ist.

```kotlin
fun <T> runBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T

fun CoroutineScope.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job

fun <T> CoroutineScope.async(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T
): Deferred<T>
```


Das bedeutet, dass wir den `GlobalScope` entfernen können; stattdessen kann `launch` auf dem von `runBlocking` bereitgestellten Empfangsobjekt aufgerufen werden, also mit `this.launch` oder einfach `launch`. Dadurch wird `launch` zum Kind von `runBlocking`. Wie Eltern vielleicht wissen, liegt eine elterliche Verantwortung darin, zu warten, bis alle ihre Kinder abgeschlossen sind, daher wird `runBlocking` unterbrochen, bis alle seine Kinder abgeschlossen sind.

{crop-start: 3, crop-end: 18}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun main() = runBlocking {
    this.launch { // same as just launch
        delay(1000L)
        println("World!")
    }
    launch { // same as this.launch
        delay(2000L)
        println("World!")
    }
    println("Hello,")
}
// Hello,
// (1 sec)
// World!
// (1 sec)
// World!
//sampleEnd
```

Ein Elternteil bietet einen Gültigkeitsbereich für seine Kinder an, und sie werden innerhalb dieses Gültigkeitsbereichs aufgerufen. Dies bildet eine Beziehung, die als *strukturierte Nebenläufigkeit* bezeichnet wird. Hier sind die wichtigsten Effekte der Eltern-Kind-Beziehung:
* Kinder erben den Kontext von ihren Eltern (aber sie können ihn auch überschreiben, wie im Kapitel *Kontext der Coroutine* erklärt wird);
* ein Elternteil wird pausiert, bis alle Kinder abgeschlossen sind (dies wird im Kapitel *Job und Warten auf Kinder* erklärt);
* wenn die Eltern-Coroutine abgebrochen wird, werden auch ihre Kinder-Coroutines abgebrochen (dies wird im Kapitel *Abbruch* erklärt);
* wenn ein Kind einen Fehler auslöst, zerstört es auch das Elternteil (dies wird im Kapitel *Ausnahmehandhabung* erklärt).

Beachten Sie, dass `runBlocking`, im Gegensatz zu anderen Coroutine-Erstellern, keine Erweiterungsfunktion auf `CoroutineScope` ist. Das bedeutet, dass es kein Kind sein kann: es kann nur als Root-Coroutine verwendet werden (der Elternteil aller Kinder in einer Hierarchie). Das bedeutet, dass `runBlocking` in anderen Fällen als anderen Coroutines verwendet wird. Wie wir bereits erwähnt haben, unterscheidet sich dies sehr von anderen Erstellern.

### Das größere Bild

Unterbrechungsfunktionen müssen von anderen Unterbrechungsfunktionen aufgerufen werden. Dies muss alles mit einem Coroutine-Ersteller beginnen. Außer für `runBlocking`, müssen Ersteller auf `CoroutineScope` gestartet werden. In unseren einfachen Beispielen wird der Gültigkeitsbereich von `runBlocking` bereitgestellt, aber in größeren Anwendungen wird er entweder von uns erstellt (wir werden erklären, wie dies im Kapitel *Erstellen des Coroutine-Gültigkeitsbereichs* gemacht wird) oder er wird von dem Framework bereitgestellt, das wir verwenden, zum Beispiel Ktor auf einem Server oder Android KTX auf Android. Sobald der erste Ersteller auf einem Gültigkeitsbereich gestartet ist, können andere Ersteller auf dem Gültigkeitsbereich des ersten Erstellers gestartet werden, und so weiter. Dies ist im Wesentlichen, wie unsere Anwendungen strukturiert sind.

Hier sind einige Beispiele dafür, wie Coroutines in realen Projekten verwendet werden. Die ersten beiden Beispiele sind sowohl für Server-Anwendungen als auch für Android typisch. `MainPresenter` stellt einen Fall dar, der typisch für Android ist. `UserController` stellt einen Fall dar, der typisch für Server-Anwendungen ist.

```kotlin
class NetworkUserRepository(
    private val api: UserApi,
) : UserRepository {
    suspend fun getUser(): User = api.getUser().toDomainUser()
}

class NetworkNewsRepository(
    private val api: NewsApi,
    private val settings: SettingsRepository,
) : NewsRepository {

    suspend fun getNews(): List<News> = api.getNews()
        .map { it.toDomainNews() }

    suspend fun getNewsSummary(): List<News> {
        val type = settings.getNewsSummaryType()
        return api.getNewsSummary(type)
    }
}

class MainPresenter(
    private val view: MainView,
    private val userRepo: UserRepository,
    private val newsRepo: NewsRepository
) : BasePresenter {

    fun onCreate() {
        scope.launch {
            val user = userRepo.getUser()
            view.showUserData(user)
        }
        scope.launch {
            val news = async {
                newsRepo.getNews()
                    .sortedByDescending { it.date }
            }
            val newsSummary = async {
                newsRepo.getNewsSummary()
            }
            view.showNews(newsSummary.await(), news.await())
        }
    }
}

@Controller
class UserController(
    private val tokenService: TokenService,
    private val userService: UserService,
) {
    @GetMapping("/me")
    suspend fun findUser(
        @PathVariable userId: String,
        @RequestHeader("Authorization") authorization: String
    ): UserJson {
        val userId = tokenService.readUserId(authorization)
        val user = userService.findUserById(userId)
        return user.toJson()
    }
}
```


Es gibt allerdings ein Problem: Wie sieht es aus mit suspendierenden Funktionen? Wir können dort anhalten, aber wir haben keinen Kontext. Es ist keine gute Lösung, den Kontext als Argument zu übergeben (wie wir im Kapitel *Scoping-Funktionen* sehen werden). Stattdessen sollten wir die Funktion `coroutineScope` verwenden, eine suspendierende Funktion, die einen Kontext für Builder erstellt.

### Verwendung von `coroutineScope`

Stellen Sie sich vor, Sie müssen in einer Repository-Funktion zwei Ressourcen asynchron laden, zum Beispiel Benutzerdaten und eine Liste von Artikeln. In diesem Fall möchten Sie nur die Artikel zurückgeben, die der Benutzer sehen sollte. Um `async` aufzurufen, benötigen wir einen Kontext, aber wir wollen diesen nicht an eine Funktion übergeben[^201_6]. Zur Erstellung eines Kontexts aus einer suspendierenden Funktion benutzen wir die Funktion `coroutineScope`.


```kotlin
suspend fun getArticlesForUser(
   userToken: String?,
): List<ArticleJson> = coroutineScope {
   val articles = async { articleRepository.getArticles() }
   val user = userService.getUser(userToken)
   articles.await()
       .filter { canSeeOnList(user, it) }
       .map { toArticleJson(it) }
}
```


`coroutineScope` ist nur eine aussetzende Funktion, die einen Bereich für ihren Lambda-Ausdruck erstellt. Die Funktion gibt das zurück, was vom Lambda-Ausdruck zurückgegeben wird (wie `let`, `run`, `use` oder `runBlocking`). So gibt sie im obigen Beispiel `List<ArticleJson>` zurück, weil dies das ist, was vom Lambda-Ausdruck zurückgegeben wird.

`coroutineScope` ist eine Standardfunktion, die wir verwenden, wenn wir einen Bereich innerhalb einer aussetzenden Funktion benötigen. Sie ist sehr wichtig. Die Art und Weise, wie sie entworfen wurde, ist perfekt für diesen Anwendungsfall, aber um sie zu analysieren, müssen wir zunächst ein wenig über Kontext, Stornieren und Ausnahmebehandlung lernen. Deshalb wird die Funktion später in einem speziellen Kapitel (*Coroutine scope functions*) ausführlich erklärt.

Wir können auch anfangen, die aussetzende Hauptfunktion zusammen mit `coroutineScope` zu verwenden, was eine moderne Alternative zur Verwendung der Hauptfunktion mit `runBlocking` ist.

{crop-start: 3, crop-end: 12}
```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
  launch {
      delay(1000L)
      println("World!")
  }
  println("Hello,")
}
// Hello,
// (1 sec)
// World!
//sampleEnd
```


{width: 100%}
![Ein Diagramm zeigt, wie verschiedene Elemente der kotlinx.coroutines-Bibliothek verwendet werden. Wir beginnen in der Regel mit einem Anwendungsbereich oder `runBlocking`. In diesen können wir andere Ersteller oder suspendierende Funktionen aufrufen. Wir können Ersteller nicht auf suspendierenden Funktionen ausführen, daher verwenden wir Coroutine-Anwendungsbereich-Funktionen (wie `coroutineScope`).](coroutine_what_needs_what.png)

### Zusammenfassung

Dieses Wissen reicht für die meisten Anwendungen von Kotlin-Coroutinen aus. In den meisten Fällen haben wir nur suspendierende Funktionen, die andere suspendierende oder normale Funktionen aufrufen. Wenn wir nebenläufige Verarbeitung einführen müssen, wickeln wir eine Funktion mit `coroutineScope` ein und verwenden Ersteller in ihrem Anwendungsbereich. Alles muss mit einigen Erstellern beginnen, die in einem bestimmten Anwendungsbereich aufgerufen werden. In späteren Teilen werden wir lernen, wie man einen solchen Anwendungsbereich erstellt, aber für die meisten Projekte muss er einmal definiert und nur selten berührt werden.

Obwohl wir das Wesentliche gelernt haben, gibt es noch viel zu lernen. In den nächsten Kapiteln werden wir tiefer in Coroutinen eintauchen. Wir werden lernen, verschiedene Kontexte zu verwenden, wie wir Stornierungen handhaben, Fehlerbehandlung, wie wir Coroutinen testen, usw. Es gibt noch viele großartige Funktionen zu entdecken.

[^201_1]: Es kann auch von einer suspendierenden Hauptfunktion gestartet werden, aber obwohl wir es in vielen Beispielen verwenden, ist es nicht hilfreich, wenn wir Android- oder Backend-Anwendungen schreiben. Es ist auch gut zu wissen, dass eine suspendierende Hauptfunktion auch nur von einem Ersteller gestartet wird, aber wir sehen es nicht, weil Kotlin das für uns macht.
[^201_2]: Ein Daemon-Thread ist ein Thread mit niedriger Priorität, der im Hintergrund ausgeführt wird. Hier vergleiche ich `launch` mit einem Daemon-Thread, weil beide das Programm nicht vom Beenden abhalten.
[^201_3]: Um genauer zu sein, es startet eine neue Coroutine und blockiert den aktuellen Thread unterbrechbar, bis diese abgeschlossen ist.
[^201_4]: Der Grund dafür ist, dass `runBlocking` einen Anwendungsbereich erstellt, während eine suspendierende Hauptfunktion dies nicht tut, es sei denn, wir verwenden die Funktion `coroutineScope`, die wir später einführen werden.
[^201_5]: Um genau mit der Wortwahl zu sein: durch das Argument eines Funktionstyps, das an letzter Stelle platziert ist.
[^201_6]: Im Kapitel *Coroutine scope functions* werden wir im Detail erklären, warum.
[^201_7]: Mit einem Dispatcher können wir `runBlocking` auf einem anderen Thread ausführen lassen. Aber dennoch wird der Thread, auf dem dieser Ersteller gestartet wurde, blockiert, bis die Coroutine fertig ist.

