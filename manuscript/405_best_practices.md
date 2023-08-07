## Beste Vorgehensweisen

Ich werde dieses Buch mit meinen bescheidenen besten Vorgehensweisen beenden. Sie sind alle bereits im Buch besprochen worden, dies könnte daher als eine kurze Zusammenfassung betrachtet werden, aber ich hoffe, es wird Ihnen helfen, sie sich zu merken und sie in Ihrer täglichen Praxis anzuwenden.

### Verwenden Sie `async` nicht mit einem sofortigen `await`

Es macht keinen Sinn, eine asynchrone Aufgabe mit `async` zu definieren, wenn wir auf ihr Abschluss warten wollen, ohne während dieser Zeit irgendwelche Operationen durchzuführen.

```kotlin
// Don't
suspend fun getUser(): User = coroutineScope {
    val user = async { repo.getUser() }.await()
    user.toUser()
}

// Do
suspend fun getUser(): User {
    val user = repo.getUser()
    return user.toUser()
}
```

Es gibt Fälle, in denen diese Umwandlung nicht so einfach ist. Wenn Sie einen Geltungsbereich benötigen, verwenden Sie anstelle von `async { ... }.await()`, `coroutineScope`. Wenn Sie einen Kontext setzen müssen, verwenden Sie `withContext`.

Wenn Sie einige asynchrone Aufgaben starten, muss nicht jede Aufgabe außer der letzten `async` verwenden. In diesem Fall empfehle ich, `async` bei allen Aufgaben einzusetzen, um die Lesbarkeit zu verbessern.

```kotlin
fun showNews() {
    viewModelScope.launch {
        val config = async { getConfigFromApi() }
        val news = async { getNewsFromApi(config.await()) }
        val user = async { getUserFromApi() } // async not
        // necessary here, but useful for readability
        view.showNews(user.await(), news.await())
    }
}
```


### Verwenden Sie `coroutineScope` anstelle von `withContext(EmptyCoroutineContext)`

Der einzige Unterschied zwischen `withContext` und `coroutineScope` besteht darin, dass `withContext` den Kontext überschreiben kann. Daher sollte man anstelle von `withContext(EmptyCoroutineContext)` `coroutineScope` verwenden.

### Verwenden Sie awaitAll

Die Funktion `awaitAll` sollte gegenüber `map { it.await() }` bevorzugt werden, da sie aufhört zu warten, wenn die erste asynchrone Aufgabe eine Ausnahme wirft. Im Gegensatz dazu wartet `map { it.await() }` diese Coroutinen nacheinander ab, bis sie auf eine stößt, die fehlschlägt.

### Suspendierende Funktionen sollten sicher von jedem Thread aus aufgerufen werden können

Wenn Sie eine suspendierende Funktion aufrufen, sollten Sie sich keine Sorgen machen, dass sie den von Ihnen derzeit verwendeten Thread blockieren könnte. Dies ist besonders wichtig auf Android, wo wir oft `Dispatchers.Main` verwenden. Es ist jedoch auch auf dem Backend wichtig, wo Sie möglicherweise einen Dispatcher verwenden, der auf einen einzelnen Thread zur Synchronisation begrenzt ist.

Jede suspendierende Funktion, die blockierende Aufrufe tätigen muss, sollte `Dispatchers.IO` oder einen benutzerdefinierten Dispatcher verwenden, der darauf ausgelegt ist, Blockierungen auszuführen. Jeder Dispatcher, der CPU-intensiv sein könnte, sollte `Dispatchers.Default` oder einen Dispatcher mit begrenzter Parallelität verwenden. Diese Dispatcher sollten mit `withContext` eingestellt werden, damit Funktionsaufrufe diese Dispatcher nicht selbst einstellen müssen.


```kotlin
class DiscSaveRepository(
    private val discReader: DiscReader
) : SaveRepository {
    
    override suspend fun loadSave(name: String): SaveData =
        withContext(Dispatchers.IO) {
            discReader.read("save/$name")
        }
}
```

Funktionen, die `Flow` zurückgeben, sollten einen Dispatcher mit `flowOn` spezifizieren, welcher den Kontext für alle **vorherigen** Schritte ändert, daher wird es typischerweise als letzter Schritt in einer Funktion verwendet.

Ob `Dispatchers.Main.immediate` explizit in suspendierenden Funktionen verwendet werden sollte, die Android-Ansichten aktualisieren, ist ein umstrittenes Thema. Ihre Entscheidung sollte von der Richtlinie Ihres Projekts abhängen. Wir müssen es in Schichten nicht verwenden, in denen `Dispatchers.Main` als Standard-Dispatcher angesehen wird, wie in der Darstellungsschicht in vielen Android-Projekten.

Wenn Sie diese spezifischen Klassen testen möchten, denken Sie daran, dass Sie einen Dispatcher bereitstellen müssen, damit er für Unit-Tests überschrieben werden kann.

```kotlin
class DiscSaveRepository(
    private val discReader: DiscReader,
    private val dispatcher: CoroutineContext = Dispatchers.IO
) : SaveRepository {
    
    override suspend fun loadSave(name: String): SaveData =
        withContext(dispatcher) {
            discReader.read("save/$name")
        }
}
```


### Verwenden Sie `Dispatchers.Main.immediate` anstatt `Dispatchers.Main`

`Dispatchers.Main.immediate` ist eine optimierte Version von `Dispatchers.Main`, die das Neuverteilen von Coroutinen vermeidet, wenn es nicht notwendig ist. Wir verwenden es generell bevorzugt.


```kotlin
suspend fun showUser(user: User) =
    withContext(Dispatchers.Main.immediate) {
        userNameElement.text = user.name
        // ...
    }
```


### Denken Sie daran, `yield` in ressourcenintensiven Funktionen zu verwenden

Es ist eine gute Praxis, `yield` in Funktionen zu verwenden, die zwischen Blöcken von CPU-intensiven oder zeitintensiven Operationen unterbrochen werden. Diese Funktion unterbricht und nimmt die Coroutine sofort wieder auf und unterstützt somit den Abbruch. Das Aufrufen von `yield` ermöglicht auch eine Umverteilung, dank der ein Prozess andere Prozesse nicht verhungern lässt.


```kotlin
suspend fun cpuIntensiveOperations() =
    withContext(Dispatchers.Default) {
        cpuIntensiveOperation1()
        yield()
        cpuIntensiveOperation2()
        yield()
        cpuIntensiveOperation3()
    }
```

Innerhalb der Coroutine-Ersteller können Sie auch `ensureActive` verwenden.

### Verstehen Sie, dass suspendierende Funktionen auf den Abschluss ihrer Unterprozesse warten

Eine Eltern-Coroutine kann nicht vor ihren Unterprozessen abgeschlossen werden, und Coroutine-Umfang-Funktionen wie `coroutineScope` oder `withContext`, setzen ihre Eltern-Coroutine in den Wartezustand, bis ihre Unterprozesse abgeschlossen sind. Daher warten sie auf alle Coroutinen, die sie gestartet haben.

{crop-start: 3, crop-end: 24}
```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun longTask() = coroutineScope {
    launch {
        delay(1000)
        println("Done 1")
    }
    launch {
        delay(2000)
        println("Done 2")
    }
}

suspend fun main() {
    println("Before")
    longTask()
    println("After")
}
// Before
// (1 sec)
// Done 1
// (1 sec)
// Done 2
// After
//sampleEnd
```


Beachten Sie, dass es keinen Sinn macht, eine Coroutine-Scope-Funktion `launch` am Ende zu haben, da sich nichts ändern würde, wenn wir es entfernen. 


```kotlin
suspend fun updateUser() = coroutineScope {
    // ...
    
    // Don't
    launch { sendEvent(UserSunchronized) }
}
```

Wir erwarten, dass suspendierte Funktionen auf die Vollendung der von ihnen gestarteten Coroutinen warten. Sie können diese Erwartung durch einen externen Bereich umgehen, aber wir sollten dies vermeiden, wenn es nicht unbedingt notwendig ist.

```kotlin
suspend fun updateUser() = coroutineScope {
    // ...
    
    eventsScope.launch { sendEvent(UserSunchronized) }
}
```

### Verstehen Sie, dass `Job` nicht geerbt wird: Es wird als Übergeordnete verwendet

Ein großes Missverständnis, das zu Fehlern in mit Kotlin Coroutines arbeitenden Projekten führt, beruht auf der Tatsache, dass der `Job` Kontext der einzige ist, der nicht vererbt wird. Stattdessen wird ein `Job` von einem Übergeordneten oder Argument als Übergeordnete einer Coroutine verwendet.

Werfen wir einen Blick auf einige Beispiele. Ein `SupervisorJob` als Argument in einem Coroutine-Builder hinzuzufügen, ist nutzlos, da es keine Veränderungen bewirkt.

```kotlin
// Don't
fun main() = runBlocking(SupervisorJob()) {
    launch {
        delay(1000)
        throw Error()
    }
    launch {
        delay(2000)
        println("Done")
    }
    launch {
        delay(3000)
        println("Done")
    }
}
// (1 sec)
// Error...
```


`Job` ist der einzige Kontext, der nicht vererbt wird. Jede Coroutine benötigt ihren eigenen Job, und wenn man einen Job an eine Coroutine übergibt, wird der übergebene Job zum **Elternteil** dieses Coroutine-Jobs. Also, im obigen Ausschnitt ist `SupervisorJob` der Elternteil von `runBlocking`. Wenn ein Unterprozess eine Exception auslöst, propagiert diese Exception zur `runBlocking` Coroutine, unterbricht die `Job` Coroutine, bricht ihre Unterprozesse ab und löst eine Exception aus. Die Tatsache, dass `SupervisorJob` ein Elternteil ist, hat keine praktische Bedeutung.

![](runBlockingSupervisorJob.png)

Ich sehe einen ähnlichen Fehler noch häufiger, wenn `withContext` zusammen mit `SupervisorJob` verwendet wird.


```kotlin
// Don't
suspend fun sendNotifications(
    notifications: List<Notification>
) = withContext(SupervisorJob()) {
    for (notification in notifications) {
        launch {
            client.send(notification)
        }
    }
}
```


![](withContextSupervisorJob.png)

Wenn `SupervisorJob` so verwendet wird, ist es zwecklos. Wenn ich das sehe, nehme ich normalerweise an, dass es darum geht, Ausnahmen bei untergeordneten Elementen zu unterdrücken. Der richtige Weg, um dies zu tun, ist es, `supervisorScope` zu verwenden, das Ausnahmen in seinen direkten Kindern ignoriert.


```kotlin
// Do
suspend fun sendNotifications(
    notifications: List<Notification>
) = supervisorScope {
    for (notification in notifications) {
        launch {
            client.send(notification)
        }
    }
}
```


Die Verwendung von `withContext(Job())` ist zwecklos und sollte auch als ein Fehler angesehen werden.

### Verletzen Sie nicht die Strukturierte Nebenläufigkeit

Die Fehler, die wir oben dargestellt haben, sind nicht nur zwecklos, sondern auch schädlich. Immer wenn Sie einen expliziten `Job` als Kontext für eine Coroutine festlegen, brechen Sie die Beziehung zur übergeordneten Coroutine. Betrachten Sie das untenstehende Beispiel. Das Problem bei der Verwendung von `Job` als Argument für die Coroutine besteht darin, dass es als Übergeordnete dieser Coroutine festgelegt wird. Daher ist `withContext` kein Kind der Coroutine, die diese Funktion aufgerufen hat. Wenn diese Coroutine abgebrochen wird, wird unsere Coroutine nicht abgebrochen, daher werden die Prozesse in ihr fortgesetzt und verschwenden dadurch unsere Ressourcen. Die Verwendung eines externen Jobs oder Bereichs bricht die Strukturierte Nebenläufigkeit, verhindert eine ordnungsgemäße Stornierung und führt infolgedessen zu Speicherlecks.


```kotlin
// Don't
suspend fun getPosts() = withContext(Job()) {
    val user = async { userService.currentUser() }
    val posts = async { postsService.getAll() }
    posts.await()
        .filterCanSee(user.await())
}
```


### Verwenden Sie SupervisorJob beim Erstellen von CoroutineScope

Wenn wir einen Geltungsbereich erstellen, können wir davon ausgehen, dass wir nicht möchten, dass eine Ausnahme in einer Coroutine, die mit diesem Geltungsbereich gestartet wurde, alle anderen Coroutinen abbricht. Dafür müssen wir `SupervisorJob` anstelle von `Job` verwenden, welches standardmäßig verwendet wird.


```kotlin
// Don't
val scope = CoroutineScope(Job())

// Do
val scope = CoroutineScope(SupervisorJob())
```

### Überlegen Sie, die Unteraufgaben des Bereichs abzubrechen

Sobald ein Bereich abgebrochen wurde, kann er nicht mehr verwendet werden. Wenn Sie alle Aufgaben, die in einem Bereich gestartet wurden, abbrechen möchten, aber den Bereich aktiv halten möchten, brechen Sie seine Unteraufgaben ab. Es kostet nichts, einen Bereich aktiv zu halten.

```kotlin
fun onCleared() {
    // Consider doing
    scope.coroutineContext.cancelChildren()
    
    // Instead of
    scope.cancel()
}
```


Auf Android sollten Sie anstelle des Definierens und Abbrechens benutzerdefinierter Geltungsbereiche den `viewModelScope`, `lifecycleScope` und lebenszyklusabhängige Coroutinen-Geltungsbereiche aus den ktx-Bibliotheken verwenden, da diese automatisch abgebrochen werden.

### Bevor Sie einen Geltungsbereich verwenden, überlegen Sie unter welchen Bedingungen er abgebrochen wird

Eine meiner bevorzugten Heuristiken für die Verwendung von Kotlin Coroutines auf Android ist "die Auswahl des Geltungsbereichs, den Sie verwenden sollten, ist die Entscheidung, wann Sie diese Koroutine abbrechen möchten". Jedes ViewModel bietet seinen eigenen `viewModelScope`, der abgebrochen wird, wenn dieses ViewModel finalisiert wird. Jeder Lifecycle-Eigentümer hat seinen eigenen `lifecycleScope`, der abgebrochen wird, wenn dieser Lebenszyklus abgeschlossen ist. Wir verwenden diese Geltungsbereiche anstelle eines gemeinsamen globalen Geltungsbereichs, weil wir unsere Coroutinen abbrechen möchten, wenn sie nicht benötigt werden. Das Starten einer Koroutine in einem anderen Geltungsbereich bedeutet, dass sie unter anderen Bedingungen abgebrochen wird. Auf `GlobalScope` gestartete Coroutinen werden nie abgebrochen.


```kotlin
class MainViewModel : ViewModel() {
    val scope = CoroutineScope(SupervisorJob())
    
    fun onCreate() {
        viewModelScope.launch {
            // Will be cancelled with MainViewModel
            launch { task1() }
            // Will never be cancelled
            GlobalScope.launch { task2() }
            // Will be cancelled when we cancel scope
            scope.launch { task2() }
        }
    }
}
```


### GlobalScope sollte nicht verwendet werden

Es ist allzu leicht, `GlobalScope` zu verwenden, deshalb mag es verlockend erscheinen. Es wird jedoch davon abgeraten und stattdessen empfohlen, mindestens einen sehr einfachen Scope mit lediglich `SupervisorJob` als Kontext zu erstellen.


```kotlin
val scope = CoroutineScope(SupervisorJob())

fun example() {
    // Don't
    GlobalScope.launch { task() }
    
    // Do
    scope.launch { task() }
}
```


`GlobalScope` bedeutet keine Verbindung, keine Abbruchmöglichkeit und ist schwierig für Tests zu überschreiben. Selbst wenn `GlobalScope` jetzt alles ist, was Sie brauchen, könnte es in der Zukunft hilfreich sein, eine sinnvolle Reichweite zu definieren.


```kotlin
// GlobalScope definition
public object GlobalScope : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = EmptyCoroutineContext
}
```


### Vermeiden Sie die Verwendung des `Job` Builders, außer zum Aufbau eines Geltungsbereichs

Wenn Sie einen Job mit der `Job` Funktion erstellen, wird er unabhängig vom Zustand seiner Unterprozesse im aktiven Zustand erstellt. Selbst wenn einige Unterprozesse abgeschlossen sind, bedeutet dies nicht, dass ihre Elternprozesse ebenfalls abgeschlossen sind.

{crop-start: 3, crop-end: 17}
```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val job = Job()
    launch(job) {
        delay(1000)
        println("Text 1")
    }
    launch(job) {
        delay(2000)
        println("Text 2")
    }
    job.join() // Here we will await forever
    println("Will not be printed")
}
// (1 sec)
// Text 1
// (1 sec)
// Text 2
// (runs forever)
//sampleEnd
```

Es ist möglich, dass ein solcher `Job` abgeschlossen wird, aber nur, wenn seine `complete` Methode zuerst aufgerufen und sein Zustand dann von "Aktiv" zu "Abschluss" geändert wird, wo er wartet, bis seine Kinder fertig sind. Sie können jedoch keine neuen Coroutinen auf abschließenden oder abgeschlossenen Jobs starten. Ein praktischerer Ansatz besteht darin, eine Referenz auf einen Job zu verwenden, um auf seine Kinder zu warten (`job.children.forEach { it.join() }`). In den meisten Fällen ist die einfachste Lösung, auf den Job zu warten, der von einem Coroutinen-Erzeuger erstellt wurde. Die häufigsten Fälle beinhalten das Speichern des aktiven Jobs in einer Variablen oder das Sammeln der Jobs von allen gestarteten Coroutinen.

```kotlin
class SomeService {
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob())
    
    // Every time we start a new task,
    // we cancel the previous one.
    fun startTask() {
        cancelTask()
        job = scope.launch {
            // ...
        }
    }
    
    fun cancelTask() {
        job?.cancel()
    }
}
```

```kotlin
class SomeService {
    private var jobs: List<Job> = emptyList()
    private val scope = CoroutineScope(SupervisorJob())
    
    fun startTask() {
        jobs += scope.launch {
            // ...
        }
    }
    
    fun cancelTask() {
        jobs.forEach { it.cancel() }
    }
}
```


Meine allgemeine Empfehlung ist, den `Job` Builder zu vermeiden, außer beim Erstellen eines Scopes.

### Funktionen, die `Flow` zurückgeben, sollten nicht suspendierende sein

Ein Flow stellt einen bestimmten Prozess dar, der mit der `collect` Funktion gestartet wird. Funktionen, die `Flow` definieren, legen solche Prozesse fest und ihre Ausführung wird aufgeschoben, bis diese Prozesse gestartet werden. Das unterscheidet sich stark von suspendierenden Funktionen, die dazu dienen, Prozesse selbst auszuführen. Es ist kontraintuitiv und problematisch, diese beiden Konzepte zu mischen.

Nehmen Sie zum Beispiel an, Sie benötigen eine Funktion, die Services zum Beobachten abruft und dann beobachtet. Dies wäre eine fehlerhafte Implementierung:


```kotlin
// Don’t use suspending functions returning Flow
suspend fun observeNewsServices(): Flow<News> {
    val newsServices = fetchNewsServices()
    return newsServices
        .asFlow()
        .flatMapMerge { it.observe() }
}

suspend fun main() {
    val flow = observeNewsServices() // Fetching services
    // ...
    flow.collect { println(it) } // Start observing
}
```


Es ist unkontraintuitiv, dass ein Teil des Prozesses ausgeführt wird, wenn `observeNewsServices` aufgerufen wird und ein Teil ausgeführt wird, wenn wir anfangen zu sammeln. Außerdem, wenn wir später sammeln, verwenden wir immer noch Nachrichten, die in der Vergangenheit abgerufen wurden. Dies ist problematisch und unkontraintuitiv. Wir erwarten, dass Funktionen, die `Flow` zurückgeben, den gesamten Prozess in diesen Flow verpacken.

Um die oben genannte Funktion zu verbessern, beinhaltet die gebräuchlichste Intervention das Verpacken von suspend-Aufrufen in einen Flow.


```kotlin
fun observeNewsServices(): Flow<News> {
    return flow { emitAll(fetchNewsServices().asFlow()) }
        .flatMapMerge { it.observe() }
}

suspend fun main() {
    val flow = observeNewsServices()
    // ...
    flow.collect { println(it) }
    // Fetching services
    // Start observing
}
```

Eine Alternative besteht, wie üblich, darin, eine suspendierende Funktion zu erstellen, die auf die Fertigstellung ihres Prozesses wartet.

```kotlin
suspend fun fetchNewsFromServices(): List<News> {
    return fetchNewsServices()
        .mapAsync { it.observe() }
        .flatten()
}

suspend fun main() {
    val news = fetchNewsFromServices()
    // Fetching services
    // Start observing
    // ...
}
```

### Bevorzugen Sie eine Suspendierungsfunktion anstelle von Flow, wenn Sie nur einen Wert erwarten

Ich werde diese Sammlung mit dem umstrittensten Vorschlag abschließen. Betrachten Sie die untenstehende Funktion. Welche Werte erwarten Sie, dass sie ausgibt?

```kotlin
interface UserRepository {
    fun getUser(): Flow<User>
}
```


Ich würde erwarten, dass es einen Wert ausgibt, wann immer es geändert wird, nicht nur den aktuellen Zustand. Das liegt daran, dass der `Flow` Typ eine Quelle von Daten darstellt. Um einen einzelnen aufgeschobenen Wert darzustellen, verwenden wir suspendierte Funktionen.


```kotlin
interface UserRepository {
    suspend fun getUser(): User
}
```


Entgegen dieser Annahme verwenden viele Anwendungen, insbesondere Android-Anwendungen, Flow anstelle von suspendierenden Funktionen, wann immer es möglich ist. Ich verstehe die Gründe dafür: Einige Teams, die zuvor RxJava eingesetzt haben, wollen ihre Gewohnheiten nicht ändern. Wie mein Freund sagte: "Ich bin ein RxJava-Experte, aber ein Anfänger in Kotlin Coroutines. Ich mag es nicht, ein Anfänger zu sein, aber Flow ist wie RxJava, also bin ich vielleicht ein mittelständischer Flow-Entwickler".

Bei Android haben Entwickler einen weiteren Grund. Es ist beliebt geworden, veränderliche Zustände mit StateFlow darzustellen, und Flow kann leicht in StateFlow umgewandelt werden, indem die Funktion `stateIn` verwendet wird. Daher ist das Arbeiten mit Flow bequem.


```kotlin
class LocationsViewModel(
    locationService: LocationService
) : ViewModel() {
    
    private val location = locationService.observeLocations()
        .map { it.toLocationsDisplay() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = LocationsDisplay.Loading,
        )
    
    // ...
}
```


Wenn Sie einem Team beitreten, das überall Flow nutzt, ist es am besten, den Gepflogenheiten Ihres Teams zu folgen. Jedes Team hat seinen eigenen Stil und seine eigenen Praktiken. Wenn Sie jedoch entscheiden können - vielleicht weil Sie ein Greenfield-Projekt planen, oder vielleicht weil Sie gerade mit Coroutines begonnen haben - schlage ich vor, dass Sie Flow nicht dort einsetzen, wo Sie nur einen einzigen Wert erwarten. Dies wird Ihren Code einfacher, verständlicher und effizienter machen.

Bevor wir dieses Kapitel abschließen, möchte ich, dass Sie sich diesen Satz merken: Manchmal müssen Best Practices missachtet werden; sie sind Richtlinien für Standardsituationen, nicht Regeln für alle Situationen.

