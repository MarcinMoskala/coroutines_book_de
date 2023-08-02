
## Aufbau eines Flow

Jeder Flow muss irgendwo beginnen. Es gibt viele Wege, dies zu tun, je nachdem, was wir benötigen. In diesem Kapitel konzentrieren wir uns auf die wichtigsten Optionen.

### Flow aus unverarbeiteten Werten

Der einfachste Weg, einen Flow zu erstellen, ist die `flowOf` Funktion, bei der wir einfach definieren, welche Werte dieser Flow haben sollte (ähnlich der `listOf` Funktion für eine Liste).

{crop-start: 2}
```kotlin
import kotlinx.coroutines.flow.*

suspend fun main() {
    flowOf(1, 2, 3, 4, 5)
        .collect { print(it) } // 12345
}
```

Zuweilen benötigen wir auch einen Flow ohne Werte. Dafür haben wir die Funktion `emptyFlow()` (ähnlich wie die Funktion `emptyList` für eine Liste).
{crop-start: 2}

```kotlin
import kotlinx.coroutines.flow.*

suspend fun main() {
    emptyFlow<Int>()
        .collect { print(it) } // (nothing)
}
```


### Umsetzer

Wir können auch jedes `Iterable`, `Iterator` oder `Sequence` mit der Funktion `asFlow` in einen `Flow` konvertieren.

{crop-start: 2}
```kotlin
import kotlinx.coroutines.flow.*

suspend fun main() {
    listOf(1, 2, 3, 4, 5)
        // or setOf(1, 2, 3, 4, 5)
        // or sequenceOf(1, 2, 3, 4, 5)
        .asFlow()
        .collect { print(it) } // 12345
}
```


Diese Funktionen erzeugen einen Fluss von Elementen, die sofort verfügbar sind. Sie sind nützlich, um einen Fluss von Elementen zu starten, den wir dann mit den Flussverarbeitungsfunktionen verarbeiten können.

### Umwandlung einer Funktion in einen Fluss

Fluss wird häufig verwendet, um einen einzelnen, zeitverzögerten Wert darzustellen (wie ein `Single` in RxJava). Daher macht es Sinn, eine suspendierende Funktion in einen Fluss umzuwandeln. Das Ergebnis dieser Funktion wird der einzige Wert in diesem Fluss sein. Dafür gibt es die Erweiterungsfunktion `asFlow`, die mit Funktionstypen arbeitet (sowohl `suspend () -> T` als auch `() -> T`). Hier wird sie verwendet, um einen suspendierenden Lambda-Ausdruck in `Flow` umzuwandeln.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

suspend fun main() {
    val function = suspend {
        // this is suspending lambda expression
        delay(1000)
        "UserName"
    }

    function.asFlow()
        .collect { println(it) }
}
// (1 sec)
// UserName
```

Um eine reguläre Funktion umzuwandeln, müssen wir sie zuerst referenzieren. Dies tun wir mit `::` in Kotlin.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

suspend fun getUserName(): String {
    delay(1000)
    return "UserName"
}

suspend fun main() {
    ::getUserName
        .asFlow()
        .collect { println(it) }
}
// (1 sec)
// UserName
```


### Flow und Reactive Streams

Wenn du in deiner App Reactive Streams verwendest (wie [Reactor](https://projectreactor.io/), [RxJava 2.x.](https://github.com/ReactiveX/RxJava) oder [RxJava 3.x](https://github.com/ReactiveX/RxJava)), musst du keine großen Änderungen an deinem Code vornehmen. Alle Objekte wie `Flux`, `Flowable` oder `Observable` implementieren das Interface `Publisher`, das mit der Funktion `asFlow` aus der Bibliothek `kotlinx-coroutines-reactive` in `Flow` umgewandelt werden kann.


```kotlin
suspend fun main() = coroutineScope {
    Flux.range(1, 5).asFlow()
        .collect { print(it) } // 12345
    Flowable.range(1, 5).asFlow()
        .collect { print(it) } // 12345
    Observable.range(1, 5).asFlow()
        .collect { print(it) } // 12345
}
```


Um die Konvertierung andersherum durchzuführen, benötigen Sie spezifischere Bibliotheken. Mit `kotlinx-coroutines-reactor` können Sie `Flow` in `Flux` konvertieren. Mit `kotlinx-coroutines-rx3` (oder `kotlinx-coroutines-rx2`) können Sie `Flow` in `Flowable` oder `Observable` konvertieren.


```kotlin
suspend fun main(): Unit = coroutineScope {
    val flow = flowOf(1, 2, 3, 4, 5)

    flow.asFlux()
        .doOnNext { print(it) } // 12345
        .subscribe()

    flow.asFlowable()
        .subscribe { print(it) } // 12345

    flow.asObservable()
        .subscribe { print(it) } // 12345
}
```


### Flow-Builder

Die gängigste Methode, einen Flow zu bauen, ist der `flow`-Builder, den wir bereits in vorherigen Kapiteln genutzt haben. Er verhält sich ähnlich wie der `sequence`-Builder zum Bauen einer Sequenz oder der `produce`-Builder zum Bauen eines Kanals. Wir starten die Builder-Funktion mit dem `flow` Funktionsaufruf und emittieren innerhalb des Lambda-Ausdrucks die nächsten Werte mit der `emit` Funktion. Wir können auch `emitAll` nutzen, um alle Werte von `Channel` oder `Flow` zu emittieren (`emitAll(flow)` ist die Kurzform für `flow.collect { emit(it) }`).

{crop-start: 3}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

fun makeFlow(): Flow<Int> = flow {
    repeat(3) { num ->
        delay(1000)
        emit(num)
    }
}

suspend fun main() {
    makeFlow()
        .collect { println(it) }
}
// (1 sec)
// 0
// (1 sec)
// 1
// (1 sec)
// 2
```


Dieser Builder wurde bereits in vorherigen Kapiteln genutzt und wird auch in den kommenden häufig zum Einsatz kommen, deswegen werden wir noch viele Beispiele für seine Verwendung sehen. Für den Moment werde ich nur ein Beispiel aus dem Kapitel *Sequence Builder* erneut besuchen. Hier wird der `flow` Builder genutzt, um einen Nutzer-Stream zu generieren, der seitenweise von unserer Netzwerk-API abgerufen werden muss.


```kotlin
fun allUsersFlow(
    api: UserApi
): Flow<User> = flow {
    var page = 0
    do {
        val users = api.takePage(page++) // suspending
        emitAll(users)
    } while (!users.isNullOrEmpty())
}
```


### Verstehen des Flow Builders

Der Flow Builder ist der einfachste Weg, einen Flow zu erstellen. Alle anderen Optionen basieren darauf.


```kotlin
public fun <T> flowOf(vararg elements: T): Flow<T> = flow {
    for (element in elements) {
        emit(element)
    }
}
```


Wenn wir verstehen, wie dieser Builder arbeitet, werden wir auch verstehen, wie flow funktioniert. Der `flow` Builder ist unter der Haube recht simpel: er erzeugt ein Objekt, das das Interface `Flow` nutzt und die `block` Funktion innerhalb der `collect` Methode aufruft[^306_1].


```kotlin
fun <T> flow(
    block: suspend FlowCollector<T>.() -> Unit
): Flow<T> = object : Flow<T>() {
    override suspend fun collect(collector: FlowCollector<T>){
        collector.block()
    }
}

interface Flow<out T> {
    suspend fun collect(collector: FlowCollector<T>)
}

fun interface FlowCollector<in T> {
    suspend fun emit(value: T)
}
```

Das wissend, analysieren wir, wie der folgende Code funktioniert:

{crop-start: 3}
```kotlin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    flow { // 1
        emit("A")
        emit("B")
        emit("C")
    }.collect { value -> // 2
        println(value)
    }
}
// A
// B
// C
```


Wenn wir einen `flow` Builder aufrufen, erstellen wir lediglich ein Objekt. Allerdings bedeutet `collect` aufrufen, die `block` Funktion auf der `collector` Schnittstelle aufzurufen. Die `block` Funktion in diesem Beispiel ist der bei 1 definierte Lambda-Ausdruck. Sein Empfänger ist der `collector`, der bei 2 mit einem Lambda-Ausdruck definiert ist. Wenn wir eine Funktionschnittstelle (wie `FlowCollector`) mit einem Lambda-Ausdruck definieren, wird der Körper dieses Lambda-Ausdrucks als Implementierung der einzigen von dieser Schnittstelle erwarteten Funktion verwendet, das ist in diesem Fall `emit`. Also ist der Körper der `emit` Funktion `println(value)`. Wenn wir also `collect` aufrufen, beginnen wir mit der Ausführung des bei 1 definierten Lambda-Ausdrucks, und wenn er `emit` aufruft, ruft er den bei 2 definierten Lambda-Ausdruck auf. So funktioniert flow. Alles andere basiert darauf.

### channelFlow

`Flow` ist ein kalter Datenstrom, es produziert also Werte auf Anfrage, wenn sie benötigt werden. Wenn du an den oben dargestellten `allUsersFlow` denkst, wird die nächste Seite von Benutzern angefordert, wenn der Empfänger danach fragt. Dies ist in einigen Situationen erwünscht. Stell dir zum Beispiel vor, wir suchen einen bestimmten Benutzer. Wenn er auf der ersten Seite ist, müssen wir keine weiteren Seiten anfordern. Um dies in der Praxis zu sehen, erzeugen wir in dem unten gezeigten Beispiel die nächsten Elemente mit dem `flow` Builder. Beachte, dass die nächste Seite träge angefordert wird, wenn sie benötigt wird.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

data class User(val name: String)

interface UserApi {
    suspend fun takePage(pageNumber: Int): List<User>
}

class FakeUserApi : UserApi {
    private val users = List(20) { User("User$it") }
    private val pageSize: Int = 3

    override suspend fun takePage(
        pageNumber: Int
    ): List<User> {
        delay(1000) // suspending
        return users
            .drop(pageSize * pageNumber)
            .take(pageSize)
    }
}

fun allUsersFlow(api: UserApi): Flow<User> = flow {
    var page = 0
    do {
        println("Fetching page $page")
        val users = api.takePage(page++) // suspending
        emitAll(users.asFlow())
    } while (!users.isNullOrEmpty())
}

suspend fun main() {
    val api = FakeUserApi()
    val users = allUsersFlow(api)
    val user = users
        .first {
            println("Checking $it")
            delay(1000) // suspending
            it.name == "User3"
        }
    println(user)
}
// Fetching page 0
// (1 sec)
// Checking User(name=User0)
// (1 sec)
// Checking User(name=User1)
// (1 sec)
// Checking User(name=User2)
// (1 sec)
// Fetching page 1
// (1 sec)
// Checking User(name=User3)
// (1 sec)
// User(name=User3)
```


Auf der anderen Seite könnten wir Fälle haben, in denen wir Seiten im Voraus abrufen möchten, während wir noch mit der Verarbeitung der Elemente beschäftigt sind. In dem hier vorgestellten Fall könnte dies mehr Netzwerkanfragen verursachen, könnte aber auch ein schnelleres Ergebnis liefern. Um dies zu erreichen, bräuchten wir unabhängige Produktion und Konsumption. Solche Unabhängigkeit ist typisch für Live-Datenströme, wie zum Beispiel Channels. Daher benötigen wir eine Mischung aus Channel und Flow. Ja, das wird unterstützt: wir müssen nur die Funktion `channelFlow` verwenden, die wie Flow ist, weil sie das `Flow`-Interface umsetzt. Dieser Builder ist eine reguläre Funktion und wird mit einer Terminaloperation (wie `collect`) gestartet. Er ist auch wie ein Channel, denn sobald er gestartet ist, produziert er die Werte in einer separaten Coroutine, ohne auf den Empfänger zu warten. Daher erfolgen das Abrufen der nächsten Seiten und das Überprüfen der Benutzer parallel.

{crop-start: 23}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

data class User(val name: String)

interface UserApi {
    suspend fun takePage(pageNumber: Int): List<User>?
}

class FakeUserApi : UserApi {
    private val users = List(20) { User("User$it") }
    private val pageSize: Int = 3

    override suspend fun takePage(
        pageNumber: Int
    ): List<User>? {
        delay(1000)
        return users
            .drop(pageSize * pageNumber)
            .take(pageSize)
    }
}

fun allUsersFlow(api: UserApi): Flow<User> = channelFlow {
    var page = 0
    do {
        println("Fetching page $page")
        val users = api.takePage(page++) // suspending
        users?.forEach { send(it) }
    } while (!users.isNullOrEmpty())
}

suspend fun main() {
    val api = FakeUserApi()
    val users = allUsersFlow(api)
    val user = users
        .first {
            println("Checking $it")
            delay(1000)
            it.name == "User3"
        }
    println(user)
}
// Fetching page 0
// (1 sec)
// Checking User(name=User0)
// Fetching page 1
// (1 sec)
// Checking User(name=User1)
// Fetching page 2
// (1 sec)
// Checking User(name=User2)
// Fetching page 3
// (1 sec)
// Checking User(name=User3)
// Fetching page 4
// (1 sec)
// User(name=User3)
```


In `channelFlow` arbeiten wir mit `ProducerScope<T>`. `ProducerScope` ist die gleiche Art wie der von dem `produce` Ersteller genutzt wird. Es implementiert `CoroutineScope`, so dass wir es benutzen können, um neue Coroutinen mit Erstellern zu starten. Um Elemente zu erzeugen, benutzen wir `send` anstatt `emit`. Wir können auch auf den Kanal zugreifen oder ihn direkt mit den Funktionen von `SendChannel` manipulieren.


```kotlin
interface ProducerScope<in E> :
    CoroutineScope, SendChannel<E> {

    val channel: SendChannel<E>
}
```


Ein typisches Anwendungsbeispiel für `channelFlow` ist, wenn wir Werte unabhängig berechnen müssen. Um dies zu unterstützen, erstellt `channelFlow` einen Coroutine-Kontext, damit wir Coroutine-Bauer wie `launch` direkt starten können. Der unten stehende Code würde für `flow` nicht funktionieren, da er den für Coroutine-Bauer benötigten Kontext nicht erstellt.


```kotlin
fun <T> Flow<T>.merge(other: Flow<T>): Flow<T> =
    channelFlow {
        launch {
            collect { send(it) }
        }
        other.collect { send(it) }
    }

fun <T> contextualFlow(): Flow<T> = channelFlow {
    launch(Dispatchers.IO) {
        send(computeIoValue())
    }
    launch(Dispatchers.Default) {
        send(computeCpuValue())
    }
}
```


Genauso wie alle anderen Koroutinen, wird `channelFlow` nicht beendet, bis alle seine untergeordneten Prozesse in einem Endzustand sind.

### callbackFlow

Angenommen, Sie benötigen einen Ereignisfluss, auf den Sie reagieren müssen, wie Benutzerklicks oder andere Arten von Aktionen. Der Prozess des Zuhörens sollte unabhängig vom Prozess der Bearbeitung dieser Ereignisse sein, daher wäre `channelFlow` ein guter Kandidat. Es gibt jedoch eine bessere Option: `callbackFlow`.

Lange Zeit gab es keinen Unterschied zwischen `channelFlow` und `callbackFlow`. In der Version 1.3.4 wurden kleine Änderungen vorgenommen, um es weniger fehleranfällig bei der Verwendung von Callbacks zu machen. Der größte Unterschied besteht jedoch darin, wie diese Funktionen von den Benutzern verstanden werden: `callbackFlow` dient zur Einbindung von Callbacks.

Im Rahmen von `callbackFlow` arbeiten wir auch mit `ProducerScope<T>`. Hier sind einige Funktionen, die beim Einbinden von Callbacks hilfreich sein könnten:

- `awaitClose { ... }` - eine Funktion, die wartet, bis der Kanal geschlossen ist. Sobald er geschlossen ist, ruft sie ihr Argument auf. `awaitClose` ist sehr wichtig für `callbackFlow`. Schauen Sie sich das untenstehende Beispiel an. Ohne `awaitClose` wird die Coroutine unmittelbar nach der Registrierung eines Callbacks beendet. Dies ist für eine Coroutine natürlich: Ihr Körper ist beendet und sie hat keine untergeordneten Prozesse zu warten, also endet sie. Wir verwenden `awaitClose` (auch mit einem leeren Körper), um dies zu verhindern, und wir lauschen auf Elemente, bis der Kanal auf andere Weise geschlossen wird.
- `trySendBlocking(value)` - ähnlich wie `send`, aber es blockiert anstatt in den Wartezustand zu gehen, so dass es in nicht-anhaltenden Funktionen verwendet werden kann.
- `close()` - beendet diesen Kanal.
- `cancel(throwable)` - beendet diesen Kanal und sendet eine Ausnahme zum Fluss.

Hier ist ein typisches Beispiel dafür, wie `callbackFlow` verwendet wird:


```kotlin
fun flowFrom(api: CallbackBasedApi): Flow<T> = callbackFlow {
    val callback = object : Callback {
        override fun onNextValue(value: T) {
            trySendBlocking(value)
        }
        override fun onApiError(cause: Throwable) {
            cancel(CancellationException("API Error", cause))
        }
        override fun onCompleted() = channel.close()
    }
    api.register(callback)
    awaitClose { api.unregister(callback) }
}
```


### Zusammenfassung

In diesem Kapitel haben wir verschiedene Möglichkeiten betrachtet, wie Flows erstellt werden können. Es gibt viele Funktionen zum Starten eines Flows, von einfachen wie `flowOf` oder `emptyFlow`, über die Konvertierungsfunktion `asFlow`, bis hin zu Flow-Builder. Die einfachste Flow-Builder-Funktion ist die `flow` Funktion, bei der Sie die `emit` Funktion nutzen können, um die nächsten Werte zu produzieren. Es gibt auch die `channelFlow` und `callbackFlow` Builder, die einen Flow erstellen, der einige der Eigenschaften eines Channel aufweist. Jede dieser Funktionen hat spezifische Anwendungsfälle, und es ist hilfreich, sie zu kennen, um das volle Potenzial von Flow auszuschöpfen.

[^306_1]: Der untenstehende Code ist vereinfacht. In echtem Code würde ein zusätzlicher Mechanismus zum Freigeben eines Kontinuations-Interceptor vorhanden sein.

