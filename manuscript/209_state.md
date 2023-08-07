## Das Problem mit dem geteilten Zustand

Bevor wir beginnen, schauen Sie sich die `UserDownloader` Klasse unten an. Sie ermöglicht es uns, einen Benutzer nach ID abzurufen oder alle zuvor heruntergeladenen Benutzer abzurufen. Was ist das Problem mit dieser Implementierung?


```kotlin
class UserDownloader(
    private val api: NetworkService
) {
    private val users = mutableListOf<User>()

    fun downloaded(): List<User> = users.toList()

    suspend fun fetchUser(id: Int) {
        val newUser = api.fetchUser(id)
        users.add(newUser)
    }
}
```


> Beachten Sie den Gebrauch der defensiven Kopie `toList`. Dies wird gemacht, um einen Konflikt zwischen dem Abrufen des von `downloaded` zurückgegebenen Objekts und dem Hinzufügen eines Elements zur mutable Liste zu vermeiden. Wir könnten Benutzer auch mit der schreibgeschützten Liste (`List<User>`) und der read-write Eigenschaft (`var`) repräsentieren. Dann bräuchten wir keine defensive Kopie zu machen, und `downloaded` müsste überhaupt nicht geschützt werden, aber wir würden die Leistung beim Hinzufügen von Elementen zur Sammlung verringern. Ich persönlich bevorzuge den zweiten Ansatz, aber ich habe mich entschieden, denjenigen zu zeigen, der eine mutable Sammlung verwendet, da ich ihn häufiger in realen Projekten sehe.

Die obige Implementierung ist nicht für die gleichzeitige Nutzung vorbereitet. Jeder Aufruf von `fetchUser` ändert `users`. Dies ist in Ordnung, solange diese Funktion nicht gleichzeitig in mehreren Threads ausgeführt wird. Da sie in mehreren Threads gleichzeitig ausgeführt werden kann, sagen wir, dass `users` ein shared Zustand ist, und daher muss er geschützt werden. Dies liegt daran, dass gleichzeitige Änderungen zu Konflikten führen können. Dieses Problem wird unten dargestellt:

{crop-start: 21}
```kotlin
import kotlinx.coroutines.*

class UserDownloader(
    private val api: NetworkService
) {
    private val users = mutableListOf<User>()

    fun downloaded(): List<User> = users.toList()

    suspend fun fetchUser(id: Int) {
        val newUser = api.fetchUser(id)
        users += newUser
    }
}

class User(val name: String)

interface NetworkService {
    suspend fun fetchUser(id: Int): User
}

class FakeNetworkService : NetworkService {
    override suspend fun fetchUser(id: Int): User {
        delay(2)
        return User("User$id")
    }
}

suspend fun main() {
    val downloader = UserDownloader(FakeNetworkService())
    coroutineScope {
        repeat(1_000_000) {
            launch {
                downloader.fetchUser(it)
            }
        }
    }
    print(downloader.downloaded().size) // ~998242
}
```


Da mehrere Threads mit derselben Instanz interagieren, druckt der obige Code eine Nummer kleiner als 1.000.000 (wie zum Beispiel 998.242), oder es könnte eine Ausnahme auslösen.


```
Exception in thread "main"
java.lang.ArrayIndexOutOfBoundsException: 22
 at java.util.ArrayList.add(ArrayList.java:463)
 ...
```


Dies ist ein typisches Problem bei Modifikationen von geteilten Zuständen. Um es klarer zu sehen, werde ich ein einfacheres Beispiel präsentieren: mehrere Threads, die einen Integer erhöhen. Ich verwende `massiveRun`, um eine Operation 1.000 Mal auf 1.000 Coroutinen mit `Dispatchers.Default` auszuführen. Nach diesen Operationen sollte die Zahl 1.000.000 (1.000 * 1.000) sein. Ohne irgendeine Synchronisation wird das tatsächliche Ergebnis jedoch kleiner sein wegen Konflikten.

{crop-start: 6, crop-end: 22}
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

//sampleStart
var counter = 0

fun main() = runBlocking {
    massiveRun {
        counter++
    }
    println(counter) // ~567231
}

suspend fun massiveRun(action: suspend () -> Unit) =
    withContext(Dispatchers.Default) {
        repeat(1000) {
            launch {
                repeat(1000) { action() }
            }
        }
    }
//sampleEnd
```


Um zu verstehen, warum das Ergebnis nicht 1.000.000 ist, stellen Sie sich ein Szenario vor, in dem zwei Threads versuchen, denselben Wert gleichzeitig zu erhöhen. Nehmen wir an, dass der Anfangswert 0 ist. Der erste Thread liest den aktuellen Wert 0 und dann wechselt der Prozessor zum zweiten Thread. Der zweite Thread liest auch den Wert 0, erhöht ihn auf 1 und speichert ihn in der Variable. Wir wechseln zum ersten Thread, der fertig ist: er hat den Wert 0, erhöht ihn auf 1 und speichert ihn. Als Ergebnis ist der Wert der Variable 1, obwohl er 2 sein sollte. Auf diese Weise gehen einige Operationen verloren.

### Blockierende Synchronisation

Das zuvor beschriebene Problem kann mit klassischen Werkzeugen gelöst werden, die wir aus Java kennen, wie dem `synchronized`-Block oder synchronisierten Kollektionen.

{crop-start: 6, crop-end: 16}
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

//sampleStart
var counter = 0

fun main() = runBlocking {
    val lock = Any()
    massiveRun {
        synchronized(lock) { // We are blocking threads!
            counter++
        }
    }
    println("Counter = $counter") // 1000000
}
//sampleEnd

suspend fun massiveRun(action: suspend () -> Unit) =
    withContext(Dispatchers.Default) {
        repeat(1000) {
            launch {
                repeat(1000) { action() }
            }
        }
    }
```

Diese Lösung funktioniert, aber es gibt einige Probleme. Das größte Problem ist, dass Sie innerhalb eines `synchronized` Blocks keine suspendierenden Funktionen verwenden können. Das zweite Problem ist, dass dieser Block Threads blockiert, wenn eine Coroutine auf ihren Einsatz wartet. Ich hoffe, dass Sie nach dem Kapitel über die Dispatcher verstanden haben, dass wir Threads nicht blockieren wollen. Was passiert, wenn es sich um den Hauptthread handelt? Was, wenn wir nur über eine begrenzte Anzahl von Threads verfügen? Warum sollten wir diese Ressourcen verschwenden? Wir sollten stattdessen spezifische Coroutine-Tools verwenden. Diese blockieren nicht, sondern suspendieren oder vermeiden Konflikte. Lassen wir also diese Lösung beiseite und schauen uns einige andere an.

### Atomics

Es gibt eine weitere Java-Lösung, die uns in einigen einfachen Fällen helfen kann. Java verfügt über eine Reihe von atomaren Werten. Alle ihre Operationen sind schnell und garantiert "thread-sicher". Ihre Operationen werden auf niedriger Ebene ohne Sperren durchgeführt, was diese Lösung effizient und für uns geeignet macht. Es gibt verschiedene atomare Werte, die wir verwenden können. Für unseren Fall können wir `AtomicInteger` verwenden.

{width: 100%}
![](state_atomic.png)

{crop-start: 7, crop-end: 14}
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

//sampleStart
private var counter = AtomicInteger()

fun main() = runBlocking {
    massiveRun {
        counter.incrementAndGet()
    }
    println(counter.get()) // 1000000
}
//sampleEnd

suspend fun massiveRun(action: suspend () -> Unit) =
    withContext(Dispatchers.Default) {
        repeat(1000) {
            launch {
                repeat(1000) { action() }
            }
        }
    }
```

Es funktioniert hier perfekt, aber der Nutzen von atomaren Werten ist generell sehr begrenzt, daher müssen wir vorsichtig sein: Nur zu wissen, dass eine einzige Operation atomar sein wird, hilft uns nicht, wenn wir ein Bündel von Operationen haben.

{crop-start: 7, crop-end: 14}
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

//sampleStart
private var counter = AtomicInteger()

fun main() = runBlocking {
    massiveRun {
        counter.set(counter.get() + 1)
    }
    println(counter.get()) // ~430467
}
//sampleEnd

suspend fun massiveRun(action: suspend () -> Unit) =
    withContext(Dispatchers.Default) {
        repeat(1000) {
            launch {
                repeat(1000) { action() }
            }
        }
    }
```

Um unseren `UserDownloader` zu sichern, könnten wir den `AtomicReference` verwenden, der die schreibgeschützte Benutzerliste einhüllt. Wir können die atomare Funktion `getAndUpdate` verwenden, um ihren Wert ohne Konflikte zu aktualisieren.

{crop-start: 6, crop-end: 17}
```kotlin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

//sampleStart
class UserDownloader(
    private val api: NetworkService
) {
    private val users = AtomicReference(listOf<User>())

    fun downloaded(): List<User> = users.get()

    suspend fun fetchUser(id: Int) {
        val newUser = api.fetchUser(id)
        users.getAndUpdate { it + newUser }
    }
}
//sampleEnd

class User(val name: String)

interface NetworkService {
    suspend fun fetchUser(id: Int): User
}

class FakeNetworkService : NetworkService {
    override suspend fun fetchUser(id: Int): User {
        delay(2)
        return User("User$id")
    }
}

suspend fun main() {
    val downloader = UserDownloader(FakeNetworkService())
    coroutineScope {
        repeat(1_000_000) {
            launch {
                downloader.fetchUser(it)
            }
        }
    }
    print(downloader.downloaded().size) // 1000000
}
```


Wir verwenden oft Atomics, um ein einzelnes Primitiv oder eine einzelne Referenz zu sichern, aber für kompliziertere Fälle benötigen wir immer noch bessere Werkzeuge.

### Ein auf einen einzigen Thread beschränkter Dispatcher

Wir haben einen Dispatcher mit auf einen einzigen Thread beschränktem Parallelismus im Kapitel [*Dispatchers*](https://kt.academy/article/cc-dispatchers) gesehen. Dies ist die einfachste Lösung für die meisten Probleme mit gemeinsamen Zuständen.

{crop-start: 4, crop-end: 16}
```kotlin
import kotlinx.coroutines.*
import java.util.concurrent.Executors

//sampleStart
val dispatcher = Dispatchers.IO
    .limitedParallelism(1)

var counter = 0

fun main() = runBlocking {
    massiveRun {
        withContext(dispatcher) {
            counter++
        }
    }
    println(counter) // 1000000
}
//sampleEnd

suspend fun massiveRun(action: suspend () -> Unit) =
    withContext(Dispatchers.Default) {
        repeat(1000) {
            launch {
                repeat(1000) { action() }
            }
        }
    }
```


In der Praxis kann dieser Ansatz auf zwei Arten angewendet werden. Der erste Ansatz ist als *Grobgranulare Thread-Einschränkung* bekannt. Dies ist eine einfache Methode, bei der wir die gesamte Funktion einfach mit `withContext` umhüllen, mit einem Dispatcher, der auf einen einzigen Thread beschränkt ist. Diese Lösung ist unkompliziert und verhindert Konflikte, aber das Problem ist, dass wir die Multithreading-Fähigkeiten der gesamten Funktion verlieren. Schauen wir uns das untenstehende Beispiel an. `api.fetchUser(id)` könnte gleichzeitig auf vielen Threads gestartet werden, aber seine Ausführung findet auf einem Dispatcher statt, der auf einen einzigen Thread beschränkt ist. Daraus könnte die Ausführung dieser Funktion verlangsamt werden, wenn wir Funktionen aufrufen, die blockierende oder rechenintensive sind.

{crop-start: 4, crop-end: 20}
```kotlin
import kotlinx.coroutines.*
import java.util.concurrent.Executors

//sampleStart
class UserDownloader(
    private val api: NetworkService
) {
    private val users = mutableListOf<User>()
    private val dispatcher = Dispatchers.IO
        .limitedParallelism(1)

    suspend fun downloaded(): List<User> =
        withContext(dispatcher) {
            users.toList()
        }

    suspend fun fetchUser(id: Int) = withContext(dispatcher) {
        val newUser = api.fetchUser(id)
        users += newUser
    }
}
//sampleEnd

class User(val name: String)

interface NetworkService {
    suspend fun fetchUser(id: Int): User
}

class FakeNetworkService : NetworkService {
    override suspend fun fetchUser(id: Int): User {
        delay(2)
        return User("User$id")
    }
}

suspend fun main() {
    val downloader = UserDownloader(FakeNetworkService())
    coroutineScope {
        repeat(1_000_000) {
            launch {
                downloader.fetchUser(it)
            }
        }
    }
    print(downloader.downloaded().size) // ~1000000
}
```


Der zweite Ansatz ist bekannt als *Feinkörnige Thread-Beschränkung*. In diesem Ansatz wird nur diejenigen Anweisungen berücksichtigt, die den Zustand ändern. In unserem Beispiel sind dies alle Zeilen, in denen `users` verwendet wird. Dieser Ansatz ist fordernder, bringt uns jedoch eine bessere Performance, wenn die Funktionen, die nicht in unseren kritischen Abschnitt einbezogen sind (wie `fetchUser` in unserem Beispiel), blockierend oder CPU-intensiv sind. Wenn sie nur einfache Pausing Funktionen sind, ist eine Performanceverbesserung wahrscheinlich nicht zu bemerken.

{crop-start: 4, crop-end: 22}
```kotlin
import kotlinx.coroutines.*
import java.util.concurrent.Executors

//sampleStart
class UserDownloader(
    private val api: NetworkService
) {
    private val users = mutableListOf<User>()
    private val dispatcher = Dispatchers.IO
        .limitedParallelism(1)

    suspend fun downloaded(): List<User> =
        withContext(dispatcher) {
            users.toList()
        }

    suspend fun fetchUser(id: Int) {
        val newUser = api.fetchUser(id)
        withContext(dispatcher) {
            users += newUser
        }
    }
}
//sampleEnd

class User(val name: String)

interface NetworkService {
    suspend fun fetchUser(id: Int): User
}

class FakeNetworkService : NetworkService {
    override suspend fun fetchUser(id: Int): User {
        delay(2)
        return User("User$id")
    }
}

suspend fun main() {
    val downloader = UserDownloader(FakeNetworkService())
    coroutineScope {
        repeat(1_000_000) {
            launch {
                downloader.fetchUser(it)
            }
        }
    }
    print(downloader.downloaded().size) // ~1000000
}
```


In den meisten Fällen ist die Verwendung eines Dispatchers mit einem einzigen Thread nicht nur einfach, sondern dank der Tatsache, dass Standard-Dispatcher denselben Thread-Pool teilen, auch effizient.

### Mutex

Der letzte beliebte Ansatz ist die Verwendung eines `Mutex`. Sie können es sich wie einen Raum mit einem einzigen Schlüssel vorstellen (oder vielleicht eine Toilette in einer Cafeteria). Seine wichtigste Funktion ist `lock`. Wenn die erste Coroutine es aufruft, nimmt sie sozusagen den Schlüssel und durchläuft `lock` ohne Unterbrechung. Wenn eine andere Coroutine dann `lock` aufruft, wird sie angehalten, bis die erste Coroutine `unlock` aufruft (wie eine Person, die auf einen Schlüssel zur Toilette wartet[^209_1]). Wenn eine weitere Coroutine die `lock` Funktion erreicht, wird sie angehalten und in eine Warteschlange eingereiht, gleich hinter der zweiten Coroutine. Wenn die erste Coroutine schließlich die `unlock` Funktion aufruft, gibt sie den Schlüssel zurück, so dass die zweite Coroutine (die erste in der Warteschlange) kann nun fortgesetzt werden und kann schließlich die `lock`-Funktion passieren. So ist immer nur eine Coroutine zwischen `lock` und `unlock`.

{crop-start: 6, crop-end: 31}
```kotlin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

//sampleStart
suspend fun main() = coroutineScope {
    repeat(5) {
        launch {
            delayAndPrint()
        }
    }
}

val mutex = Mutex()

suspend fun delayAndPrint() {
    mutex.lock()
    delay(1000)
    println("Done")
    mutex.unlock()
}
// (1 sec)
// Done
// (1 sec)
// Done
// (1 sec)
// Done
// (1 sec)
// Done
// (1 sec)
// Done
//sampleEnd
```


Direktes Verwenden von `lock` und `unlock` ist riskant, da jeder Ausnahmefehler (oder vorzeitige Rückgabe) dazwischen dazu führen würde, dass das Lock nie zurückgegeben wird (`unlock` wird nie aufgerufen), und als Ergebnis wären keine anderen Coroutinen in der Lage, das Lock zu passieren. Dies ist ein schwerwiegendes Problem, das als Deadlock bekannt ist (stellen Sie sich eine Toilette vor, die nicht benutzt werden kann, weil jemand in Eile war und vergessen hat, das Lock zurückzugeben). Daher können wir stattdessen die Funktion `withLock` verwenden, die mit `lock` beginnt, aber `unlock` im `finally`-Block aufruft. Damit wird das Lock erfolgreich freigegeben, falls innerhalb des Blocks eine Ausnahme geworfen wird. Bei der Verwendung ähnelt es einem synchronisierten Block.

{crop-start: 5, crop-end: 16}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

//sampleStart
val mutex = Mutex()

var counter = 0

fun main() = runBlocking {
    massiveRun {
        mutex.withLock {
            counter++
        }
    }
    println(counter) // 1000000
}
//sampleEnd

suspend fun massiveRun(action: suspend () -> Unit) =
    withContext(Dispatchers.Default) {
        repeat(1000) {
            launch {
                repeat(1000) { action() }
            }
        }
    }
```


Der entscheidende Vorteil von Mutex gegenüber einem synchronisierten Block besteht darin, dass wir eine Coroutine in den Ruhezustand versetzen, anstatt einen Thread zu blockieren. Dies ist ein sicherer und weniger ressourcenintensiver Ansatz. Im Vergleich zur Verwendung eines Dispatchers, der auf einen einzigen Thread begrenzte Parallelität aufweist, ist Mutex weniger Ressourcenverbrauch und könnte in einigen Fällen eine bessere Leistung bieten. Andererseits ist seine korrekte Nutzung auch schwieriger. Es birgt eine wichtige Gefahr: Eine Coroutine kann nicht zweimal an der Sperre vorbeikommen (vielleicht bleibt der Schlüssel stecken, sodass es unmöglich wäre, eine weitere Tür, die denselben Schlüssel benötigt, zu überwinden). Die Ausführung des folgenden Codes führt zu einem Zustand des Programms, der als Deadlock bezeichnet wird - es bleibt für immer blockiert.

{crop-start: 4, crop-end: 14}
```kotlin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

//sampleStart
suspend fun main() {
    val mutex = Mutex()
    println("Started")
    mutex.withLock {
        mutex.withLock {
            println("Will never be printed")
        }
    }
}
// Started
// (runs forever)
//sampleEnd
```


Das zweite Problem mit Mutex ist, dass es nicht entsperrt wird, wenn eine Coroutine unterbrochen wird. Schauen Sie sich den untenstehenden Code an. Es dauert mehr als 5 Sekunden, weil der Mutex während des `delay` noch gesperrt ist.

{crop-start: 5}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.system.measureTimeMillis

class MessagesRepository {
    private val messages = mutableListOf<String>()
    private val mutex = Mutex()

    suspend fun add(message: String) = mutex.withLock {
        delay(1000) // we simulate network call
        messages.add(message)
    }
}

suspend fun main() {
    val repo = MessagesRepository()

    val timeMillis = measureTimeMillis {
        coroutineScope {
            repeat(5) {
                launch {
                    repo.add("Message$it")
                }
            }
        }
    }
    println(timeMillis) // ~5120
}
```

Wenn wir einen Dispatcher nutzen, der auf einen einzigen Thread beschränkt ist, haben wir dieses Problem nicht. Wenn eine `delay` Funktion oder eine Netzwerkanfrage eine Koroutine pausiert, kann der Thread von anderen Coroutinen genutzt werden.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

class MessagesRepository {
    private val messages = mutableListOf<String>()
    private val dispatcher = Dispatchers.IO
        .limitedParallelism(1)

    suspend fun add(message: String) =
        withContext(dispatcher) {
            delay(1000) // we simulate network call
            messages.add(message)
        }
}

suspend fun main() {
    val repo = MessagesRepository()

    val timeMillis = measureTimeMillis {
        coroutineScope {
            repeat(5) {
                launch {
                    repo.add("Message$it")
                }
            }
        }
    }
    println(timeMillis) // 1058
}
```


Aus diesem Grund meiden wir es, Mutex zu verwenden, um ganze Funktionen zu umschließen (Grobschrittige Ansatz). Wenn wir es überhaupt einsetzen, müssen wir dies mit äußerster Sorgfalt tun, um eine doppelte Blockierung und das Aufrufen von pausierenden Funktionen zu umgehen. 


```kotlin
class MongoUserRepository(
    //...
) : UserRepository {
    private val mutex = Mutex()

    override suspend fun updateUser(
        userId: String,
        userUpdate: UserUpdate
    ): Unit = mutex.withLock {
        // Yes, update should happen on db,
        // not via multiple functions,
        // this is just an example.
        val currentUser = getUser(userId) // Deadlock!
        deleteUser(userId) // Deadlock!
        addUser(currentUser.updated(userUpdate)) // Deadlock!
    }

    override suspend fun getUser(
        userId: String
    ): User = mutex.withLock {
        // ...
    }

    override suspend fun deleteUser(
        userId: String
    ): Unit = mutex.withLock {
        // ...
    }

    override suspend fun addUser(
        user: User
    ): User = mutex.withLock {
        // ...
    }
}
```


Feingranulare Thread-Isolierung (nur den Ort umschließen, an dem wir den gemeinsamen Zustand ändern) würde helfen, aber im obigen Beispiel würde ich lieber einen Dispatcher verwenden, der auf einen einzigen Thread beschränkt ist.

### Semaphore

Wenn wir `Mutex` erwähnt haben, sollten wir auch `Semaphore` erwähnen, das ähnlich funktioniert, aber mehr als eine Freigabe haben kann. Bei `Mutex` sprechen wir von einem einzigen Lock, daher hat es Funktionen wie `lock`, `unlock` und `withLock`. Bei `Semaphore` sprechen wir von Freigaben, daher hat es Funktionen wie `acquire`, `release` und `withPermit`. 

{crop-start: 3}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

suspend fun main() = coroutineScope {
    val semaphore = Semaphore(2)

    repeat(5) {
        launch {
            semaphore.withPermit {
                delay(1000)
                print(it)
            }
        }
    }
}
// 01
// (1 sec)
// 23
// (1 sec)
// 4
```

Ein Semaphore mit mehr als einer Genehmigung hilft uns nicht bei dem Problem des geteilten Zustands, kann aber verwendet werden, um die Anzahl der gleichzeitigen Anforderungen zu begrenzen, also um eine *Begrenzung der Anforderungsrate* zu implementieren.

```kotlin
class LimitedNetworkUserRepository(
    private val api: UserApi
) {
    // We limit to 10 concurrent requests
    private val semaphore = Semaphore(10)

    suspend fun requestUser(userId: String) = 
        semaphore.withPermit {
            api.requestUser(userId)
        }
}
```


### Zusammenfassung

Es gibt viele Wege, wie man Coroutinen koordinieren kann, um Konflikte zu vermeiden, wenn ein gemeinsamer Zustand verändert wird. Die praktischste Lösung ist, den gemeinsamen Zustand in einem Dispatcher zu verändern, der auf einen einzigen Thread beschränkt ist. Dies kann eine *feinkörnige Thread-Beschränkung* sein, die nur spezifische Stellen umfasst, an denen eine Synchronisation nötig ist; alternativ kann es eine *grobkörnige Thread-Beschränkung* sein, die den gesamten Vorgang einbezieht. Der zweite Ansatz ist einfacher, könnte aber langsamer sein. Wir könnten auch atomare Werte oder einen Mutex nutzen.

[^209_1]: Um keinen falschen Eindruck von meinem Heimatland zu vermitteln, muss ich sagen, dass wir den Schlüssel für eine Toilette hauptsächlich außerhalb von Polen benötigen. Zum Beispiel verfügt in Polen praktisch jede Tankstelle über eine Toilette, die für alle offen ist, kein Schlüssel erforderlich (und sie sind allgemein sauber und ordentlich). In vielen anderen europäischen Ländern hingegen sind die Toiletten besser vor Personen geschützt, die sie eventuell ohne Kauf nutzen möchten.

