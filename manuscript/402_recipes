
## Rezepte

In diesem Kapitel werden wir eine Sammlung von praktischen Kotlin Coroutine-Rezepten untersuchen, die Ihren Entwicklungsprozess optimieren können. Diese Rezepte wurden in mehreren Projekten getestet und verfeinert, daher können Sie darauf vertrauen, dass sie eine wertvolle Ergänzung zu Ihrem Werkzeugkasten sind.

Diese Rezepte, zusammen mit ihren begleitenden Unit-Tests, finden Sie im folgenden GitHub-Repository:

[https://github.com/MarcinMoskala/kotlin-coroutines-recipes](https://github.com/MarcinMoskala/kotlin-coroutines-recipes)

Um die Dinge noch bequemer zu machen, wurden die Rezepte auf Maven veröffentlicht, sodass Sie sie leicht in Ihren Projekten verwenden können.

### Rezept 1: Asynchrone Abbildung

Eine asynchrone Abbildung ist etwas, das wir bereits als ein Muster diskutiert haben, aber ich habe bemerkt, dass es so repetitiv ist, dass es sich lohnt, es in eine Funktion zu extrahieren.

```kotlin
suspend fun <T, R> List<T>.mapAsync(
   transformation: suspend (T) -> R
): List<R> = coroutineScope {
   this@mapAsync.map { async { transformation(it) } }
       .awaitAll()
}

// Practical example use
suspend fun getBestStudent(
   semester: String,
   repo: StudentsRepository
): Student =
   repo.getStudentIds(semester)
       .mapAsync { repo.getStudent(it) }
       .maxBy { it.result }

// Practical example use
suspend fun getCourses(user: User): List<UserCourse> =
    courseRepository.getAllCourses()
        .mapAsync { composeUserCourse(user, it) }
        .filterNot { courseShouldBeHidden(user, it) }
        .sortedBy { it.state.ordinal }
```


Dank der `mapAsync` Funktion können wir `map`, `awaitAll` und `coroutineScope` abstrahieren. Dies macht die Implementierung von asynchroner Zuordnung einfacher und prägnanter. Um Rate-Limiting zu implementieren und die Anzahl der gleichzeitigen Anfragen zu kontrollieren, können wir eine Semaphore nutzen.


```kotlin
suspend fun <T, R> List<T>.mapAsync(
   concurrencyLimit: Int = Int.MAX_VALUE,
   transformation: suspend (T) -> R
): List<R> = coroutineScope {
   val semaphore = Semaphore(concurrencyLimit)
   this@mapAsync.map {
       async {
           semaphore.withPermit {
               transformation(it)
           }
       }
   }.awaitAll()
}
```


Indem wir einen optionalen `concurrencyLimit`-Parameter zur `mapAsync`-Funktion hinzufügen, können wir die Anzahl der parallelen Anfragen einfach verwalten, damit unsere Anwendung reaktionsschnell und effizient bleibt.

### Rezept 2: Aussetzende Träge Initialisierung

In Kotlin Coroutines ist Ihnen vielleicht aufgefallen, dass aussetzende Funktionen manchmal in nicht-aussetzenden Lambda-Ausdrücken verwendet werden können, wie bei einer `map`. Dies funktioniert, weil aussetzende Funktionen in nicht-aussetzenden Lambda-Ausdrücken aufgerufen werden können, wenn diese Ausdrücke inline sind, und `map` ist eine inline Funktion. Obwohl diese Einschränkung nachvollziehbar ist, könnte sie uns davon abhalten, bestimmte Funktionen zu verwenden, an die wir uns gewöhnt haben.


```kotlin
// Works because map is an inline function,
// so we can call suspend await in its lambda,
// even though this lambda itself is not suspending.
suspend fun getOffers(
    categories: List<Category>
): List<Offer> = coroutineScope {
    categories
        .map { async { api.requestOffers(it) } }
        .map { it.await() } // Prefer awaitAll
        .flatten()
}
```


Für mich ist das `lazy` Delegat das wichtigste Beispiel, das nicht mit suspending functions verwendet werden kann.


```kotlin
suspend fun makeConnection(): Connection = TODO()

val connection by lazy { makeConnection() } // COMPILER ERROR
```


Um dies zu ermöglichen, müssen wir unsere eigene `suspendLazy` implementieren. Wenn jedoch eine Wertberechnung unterbrochen werden kann, bräuchten wir einen `suspend` Getter, und dafür bräuchten wir ein `suspend` Attribut. Dies wird in Kotlin nicht unterstützt, daher werden wir stattdessen eine Funktion erstellen, die eine Getter-Funktion erzeugt.


```kotlin
fun <T> suspendLazy(
   initializer: suspend () -> T
): suspend () -> T {
   TODO()
}
```

Zunächst verwenden wir `Mutex`, um zu verhindern, dass nicht mehr als eine Coroutine gleichzeitig denselben Wert berechnet[^402_1]. Beachte, dass `Mutex` nicht durch einen Dispatcher ersetzt werden kann, der auf einen einzigen Thread beschränkt ist, weil wir nicht wollen, dass mehr als ein Prozess einen Wert berechnet, selbst wenn der vorherige ausgesetzt ist. Als nächstes setzen wir eine Variable für den berechneten Wert. Wir verwenden `NOT_SET` als Zeichen dafür, dass der Wert noch nicht initialisiert ist. Jetzt sollte unser Prozess, der den Wert erzeugt und ihn mit einem Mutex sichert, überprüfen, ob der Wert bereits berechnet wurde: Wenn dies nicht der Fall ist, berechne ihn mit der Initialisierungsfunktion und gib dann den Wert zurück.

```kotlin
private val NOT_SET = Any()

fun <T> suspendLazy(
    initializer: suspend () -> T
): suspend () -> T {
    val mutex = Mutex()
    var holder: Any? = NOT_SET
    
    return {
        if (holder !== NOT_SET) holder as T
        else mutex.withLock {
            if (holder === NOT_SET) holder = initializer()
            holder as T
        }
    }
}
```


Haben Sie bemerkt, dass diese Implementierung ein Speicherleck hat? Sobald `initializer` verwendet wurde, müssen wir seine Referenz nicht behalten, daher können wir diesen Lambda-Ausdruck (und alle Werte, die er erfasst hat) freigeben, indem wir `initializer` auf `null`[^402_2] setzen. Wenn wir dies tun, können wir unsere Bedingung ändern und den trägen Wert initialisieren, wenn der `initializer` immer noch nicht `null` ist. Dies ist die Implementierung von "suspending lazy", die ich verwende:

{crop-start: 3}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

fun <T> suspendLazy(
    initializer: suspend () -> T
): suspend () -> T {
    var initializer: (suspend () -> T)? = initializer
    val mutex = Mutex()
    var holder: Any? = Any()
    
    return {
        if (initializer == null) holder as T
        else mutex.withLock {
            initializer?.let {
                holder = it()
                initializer = null
            }
            holder as T
        }
    }
}

// Example
suspend fun makeConnection(): String {
    println("Creating connection")
    delay(1000)
    return "Connection"
}

val getConnection = suspendLazy { makeConnection() }

suspend fun main() {
    println(getConnection())
    println(getConnection())
    println(getConnection())
}
// Creating connection
// (1 sec)
// (1 sec)
// Connection
// Connection
// Connection
```

```kotlin
// Practical example use
val userData: suspend () -> UserData = suspendLazy {
    service.fetchUserData()
}

suspend fun getUserData(): UserData = userData()
```


### Rezept 3: Wiederverwendung von Verbindungen

Ich habe Ihnen gezeigt, wie `SharedFlow` einen einzelnen Flow wiederverwenden kann, sodass seine Werte an mehrere Flows ausgegeben werden. Dies ist eine sehr wichtige Optimierung, insbesondere wenn dieser anfängliche Flow eine persistente HTTP-Verbindung (wie WebSocket oder RSocket) benötigt oder eine Datenbank beobachten muss. Lassen Sie uns einen Moment auf die persistente HTTP-Verbindung konzentrieren. Die Wartung ist mit erheblichen Kosten verbunden, daher möchten wir nicht unnötig zwei Verbindungen aufrechterhalten, um dieselben Daten zu empfangen. Deshalb werden wir später in diesem Buch lernen, wie man einen Flow in einen Shared Flow verwandelt, um eine einzige Verbindung wiederverwenden zu können.


```kotlin
class LocationService(
    locationDao: LocationDao,
    scope: CoroutineScope
) {
    private val locations = locationDao.observeLocations()
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
        )
    
    fun observeLocations(): Flow<List<Location>> = locations
}
```


Dieses Entwurfsmuster ist nützlich für Kommunikationsverbindungen, die nicht parametrisiert sind, aber was ist mit denjenigen, die mit spezifischen Parametern gestartet wurden? Beispielsweise, wenn Sie eine Messenger-Anwendung entwickeln und bestimmte Diskussionsverläufe beobachten möchten. Für solche Fälle finde ich die folgende `ConnectionPool`-Klasse sehr nützlich. Wenn `getConnection` zum ersten Mal aufgerufen wird, erstellt es einen State-Flow, der eine Verbindung basierend auf dem in seinem Builder angegebenen Flow herstellt. Beachten Sie, dass `ConnectionPool` State-Flows mit `WhileSubscribed` verwendet, sodass sie nur Kommunikationsverbindungen aktiv halten, solange sie benötigt werden.


```kotlin
class ConnectionPool<K, V>(
    private val scope: CoroutineScope,
    private val builder: (K) -> Flow<V>,
) {
    private val connections = mutableMapOf<K, Flow<V>>()
    
    fun getConnection(key: K): Flow<V> = synchronized(this) {
        connections.getOrPut(key) {
            builder(key).shareIn(
                scope,
                started = SharingStarted.WhileSubscribed(),
            )
        }
    }
}

// Practical example use
private val scope = CoroutineScope(SupervisorJob())
private val messageConnections =
    ConnectionPool(scope) { threadId: String ->
        api.observeMessageThread(threadId)
    }

fun observeMessageThread(threadId: String) =
    messageConnections.getConnection(threadId)
```


Beachten Sie, dass die Methode `getConnection` einen regulären Synchronisationsblock verwendet. Dies liegt daran, dass es sich um eine nicht aussetzende Funktion handelt, wie alle Funktionen, die `Flow` zurückgeben sollten. Diese Synchronisation sichert den Zugriff auf die Variable `connections`. Die Funktion `getConnection` sollte sehr schnell ausgeführt werden, da sie nur einen Flow definiert. Eine Verbindung wird erstellt, wenn sie mindestens von einem einzelnen Flow benötigt wird. Beachten Sie, dass dank der Tatsache, dass wir `WhileSubscribed` verwenden, eine Verbindung nur aufrechterhalten wird, wenn sie mindestens von einer einzelnen Coroutine verwendet wird.

`WhileSubscribed` kann parametrisiert werden. Diese Parameterwerte könnten über einen Konstruktor in `ConnectionPool` eingeführt werden, wie im untenstehenden Beispiel.


```kotlin
class ConnectionPool<K, V>(
    private val scope: CoroutineScope,
    private val replay: Int = 0,
    private val stopTimeout: Duration,
    private val replayExpiration: Duration,
    private val builder: (K) -> Flow<V>,
) {
    private val connections = mutableMapOf<K, Flow<V>>()
    
    fun getConnection(key: K): Flow<V> = synchronized(this) {
        connections.getOrPut(key) {
            builder(key).shareIn(
                scope,
                started = SharingStarted.WhileSubscribed(
                    stopTimeoutMillis =
                    stopTimeout.inWholeMilliseconds,
                    replayExpirationMillis =
                    replayExpiration.inWholeMilliseconds,
                ),
                replay = replay,
            )
        }
    }
}
```


### Rezept 4: Koroutine-Rennen

Wie ich im *Select*-Kapitel erwähnte, um einige suspendierende Prozesse zu starten und das Ergebnis des zuerst abschließenden zu erwarten, können wir die `raceOf`-Funktion aus der Splitties-Bibliothek verwenden. Allerdings bin ich kein Fan davon, eine Bibliothek nur für die Verwendung einer Funktion zu nutzen, die in wenigen Zeilen implementiert werden kann. Daher ist dies meine Implementierung von `raceOf`:


```kotlin
suspend fun <T> raceOf(
    racer: suspend CoroutineScope.() -> T,
    vararg racers: suspend CoroutineScope.() -> T
): T = coroutineScope {
    select {
        (listOf(racer) + racers).forEach { racer ->
            async { racer() }.onAwait {
                coroutineContext.job.cancelChildren()
                it
            }
        }
    }
}

// Example use
suspend fun a(): String {
    delay(1000)
    return "A"
}

suspend fun b(): String {
    delay(2000)
    return "B"
}

suspend fun c(): String {
    delay(3000)
    return "C"
}

suspend fun main(): Unit = coroutineScope {
    println(raceOf({ c() }))
    // (3 sec)
    // C
    println(raceOf({ b() }, { a() }))
    // (1 sec)
    // A
    println(raceOf({ b() }, { c() }))
    // (2 sec)
    // B
    println(raceOf({ b() }, { a() }, { c() }))
    // (1 sec)
    // A
}
```

```kotlin
// Practical example use
suspend fun fetchUserData(): UserData = raceOf(
    { service1.fetchUserData() },
    { service2.fetchUserData() }
)
```

### Rezept 5: Wiederholung eines aussetzenden Prozesses

Da wir in der realen Welt leben, müssen wir der Tatsache ins Auge sehen, dass unerwartete Fehler auftreten können. Wenn Sie einige Daten von einem Dienst anfordern, könnte dieser vorübergehend nicht verfügbar sein, Ihre Netzwerkverbindung könnte unterbrochen sein oder etwas anderes könnte passieren. Eine der Möglichkeiten, wie wir solche Situationen handhaben, besteht darin, ein automatisches Wiederholen zu implementieren, wenn ein Prozess fehlschlägt, es erneut zu versuchen.

Wir haben bereits gelernt, dass wir einen Flow mit den Methoden `retry` oder `retryWhen` wiederholen.

```kotlin
fun makeConnection(config: ConnectionConfig) = api
    .startConnection(config)
    .retryWhen { e, attempt ->
        val times = 2.0.pow(attempt.toDouble()).toInt()
        delay(maxOf(10_000L, 100L * times))
        log.error(e) { "Error for $config" }
        e is ApiException && e.code !in 400..499
    }
```


Es gibt keine solche Funktion für das Neu starten regulärer pausierender Prozesse, aber die einfachste Lösung könnte nur eine Schleife sein, die den Prozess wiederholt, bis er erfolgreich ist.


```kotlin
inline fun <T> retry(operation: () -> T): T {
    while (true) {
        try {
            return operation()
        } catch (e: Throwable) {
            // no-op
        }
    }
}

// Usage
suspend fun requestData(): String {
    if (Random.nextInt(0, 10) == 0) {
        return "ABC"
    } else {
        error("Error")
    }
}

suspend fun main(): Unit = coroutineScope {
    println(retry { requestData() })
}
// (1 sec)
// ABC
```

```kotlin
// Practical example use
suspend fun checkConnection(): Boolean = retryWhen(
    predicate = { _, retries -> retries < 3 },
    operation = { api.connected() }
)
```

Das Problem ist, dass es so etwas wie eine standardisierte Antwort nicht gibt. Wenn wir einen solchen Mechanismus implementieren, möchten wir oft Folgendes einbeziehen:

* die Bedingungen, unter denen der Prozess wiederholt werden sollte, meist basierend auf der Anzahl der Wiederholungen und dem Ausnahmetyp,
* zunehmende Verzögerung zwischen den Wiederholungen,
* Ausnahme- und Informationsprotokollierung.

Ich kenne zwei gute Ansätze zur Implementierung von `retry`. Der erste beinhaltet die Definition einer universellen Funktion wie `retryWhen`, die auf der Anwenderseite leicht angepasst werden kann. Der folgende Codeausschnitt zeigt meine Implementierung einer solchen Funktion und beinhaltet zwei wichtige Merkmale:

* es wiederholt niemals Abbruchausnahmen, um den Abbruchmechanismus nicht zu beeinträchtigen,
* es fügt vorherige Ausnahmen als unterdrückte Ausnahmen hinzu, so dass sie angezeigt werden, wenn die endgültige Ausnahme aus der Funktion herausgeworfen wird.

```kotlin
inline fun <T> retryWhen(
    predicate: (Throwable, retries: Int) -> Boolean,
    operation: () -> T
): T {
    var retries = 0
    var fromDownstream: Throwable? = null
    while (true) {
        try {
            return operation()
        } catch (e: Throwable) {
            if (fromDownstream != null) {
                e.addSuppressed(fromDownstream)
            }
            fromDownstream = e
            if (e is CancellationException ||
                !predicate(e, retries++)
            ) {
                throw e
            }
        }
    }
}

// Usage
suspend fun requestWithRetry() = retryWhen(
    predicate = { e, retries ->
        val times = 2.0.pow(attempt.toDouble()).toInt()
        delay(maxOf(10_000L, 100L * times))
        log.error(e) { "Retried" }
        retries < 10 && e is IllegalStateException
    }
) {
    requestData()
}
```


Der zweite Ansatz besteht darin, eine `retry` Funktion zu implementieren, die spezifisch für diese Anwendung vordefiniert ist, um genau festzulegen, wie wir in dieser Anwendung wiederholen möchten. Hier ist ein Beispiel dafür, wie eine solche Funktion aussehen könnte:


```kotlin
inline suspend fun <T> retry(
    operation: () -> T
): T {
    var retries = 0
    while (true) {
        try {
            return operation()
        } catch (e: Exception) {
            val times = 2.0.pow(attempt.toDouble()).toInt()
            delay(maxOf(10_000L, 100L * times))
            if (e is CancellationException || retries >= 10){
                throw e
            }
            retries++
            log.error(e) { "Retrying" }
        }
    }
}

// Usage
suspend fun requestWithRetry() = retry {
    requestData()
}
```


Es gibt einen populären Algorithmus für Wiederholung, der [exponential backoff](https://en.wikipedia.org/wiki/Exponential_backoff) genannt wird, bei dem nach jedem fehlgeschlagenen Versuch die Verzögerung wächst. Er basiert auf der Idee, dass je länger wir warten, desto unwahrscheinlicher wird ein erneuter Fehler. Sie können dessen Umsetzung in meinem Code-Snippets-Repository finden. Hier ist, wie man es verwenden kann:


```kotlin
suspend fun fetchUser(): User = retryBackoff(
    minDelay = 1.seconds,
    maxDelay = 10.seconds, // optional
    maxAttempts = 10, // optional
    backoffFactor = 1.5, // optional
    jitterFactor = 0.5, // optional
    beforeRetry = { cause, _, -> // optional
        println("Retrying after $cause")
    },
    retriesExhausted = { cause -> // optional
        println("Retries exhausted after $cause")
    },
) {
    api.fetchUser()
}

fun observeUserUpdates(): Flow<User> = api
    .observeUserUpdates()
    .retryBackoff(
        minDelay = 1.seconds,
        maxDelay = 1.minutes, // optional
        maxAttempts = 30, // optional
        backoffFactor = 2.0, // optional
        jitterFactor = 0.1, // optional
        beforeRetry = { cause, _, _ -> // optional
            println("Retrying after $cause")
        },
        retriesExhausted = { cause -> // optional
            println("Retries exhausted after $cause")
        },
    )
```

### Zusammenfassung

In diesem Abschnitt habe ich einige Rezepte präsentiert, die ich in meinen Projekten anwende. Ich hoffe, dass sie Ihnen helfen werden, nicht nur die vorgestellten Probleme zu lösen, sondern auch Ihre eigenen einzigartigen Rezepte umzusetzen.

[^402_1]: Details zum `Mutex` finden Sie im Kapitel *Das Problem mit geteilten Zuständen*.
[^402_2]: Dieses Problem wird detailliert in *Effective Kotlin*, *Artikel 50: Veraltete Objektreferenzen eliminieren* beschrieben.

