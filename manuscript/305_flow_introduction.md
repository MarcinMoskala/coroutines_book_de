## Einführung in Flow

Ein Flow repräsentiert einen Datenstrom von Werten, die asynchron verarbeitet werden. Die `Flow`-Schnittstelle ermöglicht nur das Sammeln der durchfließenden Elemente, was bedeutet, dass jedes Element behandelt wird, wenn es das Ende des Flow erreicht (`collect` für `Flow` ist wie `forEach` für Kollektionen).

```kotlin
interface Flow<out T> {
   suspend fun collect(collector: FlowCollector<T>)
}
```


Wie Sie sehen können, ist `collect` die einzige Memberfunktion in `Flow`. Alle anderen sind als Erweiterungen definiert. Dies ähnelt `Iterable` oder `Sequence`, die beide nur `iterator` als eine Memberfunktion haben.


```kotlin
interface Iterable<out T> {
   operator fun iterator(): Iterator<T>
}

interface Sequence<out T> {
   operator fun iterator(): Iterator<T>
}
```


### Vergleich von Flow mit anderen Weisen der Repräsentation von Werten

Das Konzept von Flow sollte denen, die [RxJava](https://github.com/ReactiveX/RxJava) oder [Reactor](https://projectreactor.io/) benutzen, gut bekannt sein. Andere könnten jedoch eine bessere Erklärung benötigen. Stellen Sie sich vor, Sie brauchen eine Funktion, die mehr als einen einzelnen Wert zurückgeben muss. Wenn alle diese Werte gleichzeitig zurückgegeben werden, verwenden wir eine Sammlung wie `List` oder `Set`.


```kotlin
fun allUsers(): List<User> =
   api.getAllUsers().map { it.toUser() }
```


Das Wesentliche hier ist, dass `List` und `Set` eine vollständig berechnete Sammlung darstellen. Da der Prozess der Berechnung dieser Werte Zeit in Anspruch nimmt, müssen wir auf alle Werte warten, bevor wir sie erhalten können.


```kotlin
fun getList(): List<Int> = List(3) {
   Thread.sleep(1000)
   "User$it"
}

fun main() {
   val list = getList()
   println("Function started")
   list.forEach { println(it) }
}
// (3 sec)
// Function started
// User0
// User1
// User2
```

Wenn die Elemente einzeln berechnet werden, ziehen wir es vor, die nächsten Elemente sofort zu erhalten, sobald sie auftauchen. Eine Art und Weise, dies zu tun, ist durch die Verwendung von `Sequence`, welches wir bereits im Kapitel *Sequence builder* gelernt haben.

```kotlin
fun getSequence(): Sequence<String> = sequence {
   repeat(3) {
       Thread.sleep(1000)
       yield("User$it")
   }
}

fun main() {
   val list = getSequence()
   println("Function started")
   list.forEach { println(it) }
}
// Function started
// (1 sec)
// User0
// (1 sec)
// User1
// (1 sec)
// User2
```

Sequenzen sind ideal, um einen Fluss von Werten darzustellen, die bei Bedarf berechnet werden, wenn diese Berechnung CPU-intensiv sein kann (wie das Berechnen komplexer Ergebnisse) oder blockiert (wie das Lesen von Dateien). Es ist jedoch wichtig zu wissen, dass Sequenz Endoperationen (wie `forEach`) nicht suspendierend sind, so dass jede Suspension innerhalb eines Sequenzbauers bedeutet, dass der Thread, der auf den Wert wartet, blockiert wird. Aus diesem Grund können Sie in einem `sequence` Builder keine suspendierende Funktion verwenden, außer diejenigen, die auf dem `SequenceScope` Aufrufer aufgerufen werden (`yield` und `yieldAll`).

```kotlin
fun getSequence(): Sequence<String> = sequence {
   repeat(3) {
       delay(1000) // Compilation error
       yield("User$it")
   }
}
```


Dieser Mechanismus wurde eingeführt, damit Sequenzen nicht missbraucht werden. Selbst wenn das obige Beispiel kompiliert werden könnte, wäre es trotzdem nicht korrekt, da die Terminaloperation (wie `forEach`) den Thread blockieren würde, anstatt die pausierende Coroutine, was zu einer unerwartete Blockierung des Threads führen könnte. Bedenken Sie, dass jemand eine Sequenz verwenden möchte, um auf paginierte Weise eine Liste aller Benutzer von einem HTTP-Endpunkt abzurufen, bis eine leere Seite empfangen wird. Das Problem ist, dass jede Nutzung einer solchen Sequenz den Thread blockieren würde, weil die `iterator` Funktion in `Sequence` nicht unterbrochen ist.


```kotlin
// Don't do that, we should use Flow instead of Sequence
fun allUsersSequence(
   api: UserApi
): Sequence<User> = sequence {
       var page = 0
       do {
           val users = api.takePage(page++) // suspending,
           // so compilation error
           yieldAll(users)
       } while (!users.isNullOrEmpty())
   }
```


Sequenzen sollten nicht für diesen Zweck verwendet werden. Sequenzen sind ideal für Datenquellen, deren Größe groß (oder unendlich) sein kann und deren Elemente aufwändig sein könnten, daher möchten wir sie bei Bedarf, "lazy", berechnen oder lesen.


```kotlin
val fibonacci: Sequence<BigInteger> = sequence {
    var first = 0.toBigInteger()
    var second = 1.toBigInteger()
    while (true) {
        yield(first)
        val temp = first
        first += second
        second = temp
    }
}

fun countCharactersInFile(path: String): Int =
    File(path).useLines { lines ->
        lines.sumBy { it.length }
    }
```


Ich hoffe, du hast schon eine Ahnung davon, dass das Blockieren von Threads sehr gefährlich sein kann und zu unerwarteten Situationen führen kann. Um dies ganz klar zu stellen, schau dir das untenstehende Beispiel an. Wir verwenden `Sequence`, daher ist dessen `forEach` eine blockierende Operation. Daher wird eine Coroutine, die auf dem gleichen Thread mit `launch` gestartet wird, warten und somit die Ausführung einer anderen Coroutine blockieren.

{crop-start: 2}
```kotlin
import kotlinx.coroutines.*

fun getSequence(): Sequence<String> = sequence {
    repeat(3) {
        Thread.sleep(1000)
        // the same result as if there were delay(1000) here
        yield("User$it")
    }
}

suspend fun main() {
    withContext(newSingleThreadContext("main")) {
        launch {
            repeat(3) {
                delay(100)
                println("Processing on coroutine")
            }
        }

        val list = getSequence()
        list.forEach { println(it) }
    }
}
// (1 sec)
// User0
// (1 sec)
// User1
// (1 sec)
// User2
// Processing on coroutine
// (0.1 sec)
// Processing on coroutine
// (0.1 sec)
// Processing on coroutine
```


Dies ist ein Fall, in dem wir `Flow` anstelle von `Sequence` verwenden sollten. Ein solcher Ansatz unterstützt Coroutinen in vollem Umfang in seinen Operationen. Sein Builder und Operationen sind Funktionen, die pausieren können, und er unterstützt strukturierte Nebenläufigkeit sowie korrekte Fehlerbehandlung. Wir werden all dies in den nächsten Kapiteln erklären, aber zunächst schauen wir mal, wie es in diesem Fall hilft.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

fun getFlow(): Flow<String> = flow {
    repeat(3) {
        delay(1000)
        emit("User$it")
    }
}

suspend fun main() {
    withContext(newSingleThreadContext("main")) {
        launch {
            repeat(3) {
                delay(100)
                println("Processing on coroutine")
            }
        }

        val list = getFlow()
        list.collect { println(it) }
    }
}
// (0.1 sec)
// Processing on coroutine
// (0.1 sec)
// Processing on coroutine
// (0.1 sec)
// Processing on coroutine
// (1 - 3 * 0.1 = 0.7 sec)
// User0
// (1 sec)
// User1
// (1 sec)
// User2
```


Flow sollte für Datenströme verwendet werden, die Coroutines nutzen müssen. Beispielsweise kann es dazu genutzt werden, einen Datenstrom von Benutzern zu erzeugen, die seitenweise von einer API abgerufen werden. Beachten Sie, dass der Aufrufer dieser Funktion mit den kommenden Seiten umgehen kann, wie sie ankommen, und entscheiden kann, wie viele Seiten abgerufen werden sollen. Rufen wir beispielsweise `allUsersFlow(api).first()` auf, holen wir uns nur die erste Seite; rufen wir `allUsersFlow(api).toList()` auf, holen wir uns alle Seiten; rufen wir `allUsersFlow(api).find { it.id == id }` auf, holen wir uns die Seiten, bis wir die Seite finden, die wir suchen.


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


### Die Eigenschaften von Flow

Die Abschlussoperationen von Flow (wie `collect`) setzen eine Coroutine in den Wartezustand, anstatt einen Thread zu blockieren. Sie unterstützen auch andere Coroutine-Funktionen, wie das Beachten des Coroutine-Kontexts und das Behandeln von Ausnahmen. Die Flow-Verarbeitung kann storniert werden, und strukturierte Nebenläufigkeit wird standardmäßig unterstützt. Der `flow`-Builder setzt nicht aus und benötigt keinen Geltungsbereich. Es ist die Abschlussoperation, die aussetzt und eine Verbindung zu ihrer Eltern-Coroutine herstellt (ähnlich der Funktion `coroutineScope`).

Das folgende Beispiel zeigt, wie der `CoroutineName`-Kontext von `collect` an den Lambda-Ausdruck im `flow`-Builder weitergegeben wird. Es zeigt auch, dass die Stornierung durch `launch` zur richtigen Stornierung der Flow-Verarbeitung führt.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// Notice, that this function is not suspending
// and does not need CoroutineScope
fun usersFlow(): Flow<String> = flow {
    repeat(3) {
        delay(1000)
        val ctx = currentCoroutineContext()
        val name = ctx[CoroutineName]?.name
        emit("User$it in $name")
    }
}

suspend fun main() {
    val users = usersFlow()

    withContext(CoroutineName("Name")) {
        val job = launch {
            // collect is suspending
            users.collect { println(it) }
        }

        launch {
            delay(2100)
            println("I got enough")
            job.cancel()
        }
    }
}
// (1 sec)
// User0 in Name
// (1 sec)
// User1 in Name
// (0.1 sec)
// I got enough
```


### Flow-Nomenklatur

Jeder Flow besteht aus einigen Elementen: 
* Ein Flow muss irgendwo beginnen. Oft beginnt es mit einem Flow-Builder, einer Umwandlung von einem anderen Objekt oder einer Hilfsfunktion. Die wichtigste Option wird im nächsten Kapitel, *Flow-Erstellung*, erläutert.
* Die letzte Operation am Flow wird als **terminale Operation** bezeichnet, die sehr wichtig ist, da sie oft die einzige ist, die pausierend oder einen Geltungsbereich erfordert. Die typische terminale Operation ist `collect`, entweder mit oder ohne Lambda-Ausdruck. Es gibt jedoch auch andere terminale Operationen. Einige von ihnen werden im Kapitel *Flow-Verarbeitung* erklärt.
* Zwischen der Startoperation und der terminalen Operation, könnten wir **intermediäre Operationen** haben, jede davon modifiziert den Flow auf irgendeine Weise. Wir werden über verschiedene intermediäre Operationen in den Kapiteln *Flow-Lebenszyklus* und *Flow-Verarbeitung* lernen.

{width: 100%}
![](flow_nomenclature.png)

### Praktische Anwendungsfälle

Die Praxis zeigt, dass wir häufiger einen Flow anstelle eines Channel benötigen. Wenn Sie eine Datenübertragung anfordern, möchten Sie es typischerweise auf Anfrage tun. Wenn Sie etwas beobachten müssen, wie Änderungen in Ihrer Datenbank oder Ereignisse von UI-Widgets oder Sensoren, möchten Sie wahrscheinlich, dass diese Ereignisse von jedem Beobachter empfangen werden. Sie müssen auch aufhören zu beobachten, wenn niemand beobachtet. Aus all diesen Gründen wird die Verwendung eines Flow gegenüber der Verwendung eines Channel bevorzugt (obwohl wir in einigen Fällen eine Kombination von beiden verwenden werden).

Die typischsten Anwendungen von Flow beinhalten:
- Empfang oder Versand von Nachrichten, die durch Server-Sent Events übertragen werden, wie WebSockets, RSocket, Benachrichtigungen usw.;
- Beobachtung von Benutzeraktionen, wie Textänderungen oder Klicks;
- Empfang von Updates von Sensoren oder anderen Informationen über ein Gerät, wie seinen Standort oder seine Ausrichtung;
- Beobachtung von Änderungen in Datenbanken.

So können wir Änderungen in einer SQL-Datenbank mit der Room Library beobachten:


```kotlin
@Dao
interface MyDao {
   @Query("SELECT * FROM somedata_table")
   fun getData(): Flow<List<SomeData>>
}
```


Schauen wir uns einige Beispiele an, wie wir einen Flow verwenden könnten, um eine Strömung von Antworten von einer API zu bearbeiten. Ich werde mit dem starten, an dem ich kürzlich gearbeitet habe. Betrachten Sie eine Handels-Workstation, wie Bloomberg oder Scanz, die immer den aktuellen Stand des Marktes anzeigt. Da sich der Markt ständig ändert, aktualisieren diese Programme viele Male pro Sekunde. Dies ist ein perfekter Anwendungsfall für einen Flow, sowohl auf dem Backend als auch auf dem Client.

{width: 100%}
![](scanz.png)

Ein alltäglicheres Beispiel könnte ein Chat sein, oder ein Client der Echtzeit-Vorschläge für eine Suche bereitstellt. Zum Beispiel, wenn wir auf [SkyScanner](https://www.skyscanner.pl/) nach dem besten Flug suchen, kommen einige Angebote schnell an, aber dann kommen im Laufe der Zeit mehr dazu; daher sehen Sie immer bessere Ergebnisse. Dies ist auch ein großartiger Fall für einen Flow.

{width: 100%}
![Auf SkyScanner können wir immer bessere Flug-Suchergebnisse sehen, wenn Fluggesellschaften auf die Angebotsanfrage reagieren.](skyscanner.png)

Zusätzlich zu diesen Situationen ist ein Flow auch ein nützliches Werkzeug für verschiedene Fälle von paralleler Verarbeitung. Stellen Sie sich zum Beispiel vor, dass Sie eine Liste von Verkäufern haben, für jeden von ihnen müssen Sie ihre Angebote abrufen. Wir haben bereits gelernt, dass wir dies mit `async` in der Verarbeitung von Sammlungen tun können:


```kotlin
suspend fun getOffers(
   sellers: List<Seller>
): List<Offer> = coroutineScope {
   sellers
       .map { seller ->
           async { api.requestOffers(seller.id) }
       }
       .flatMap { it.await() }
}
```


Der obige Ansatz ist in vielen Fällen richtig, hat aber einen Nachteil: Wenn die Liste der Verkäufer groß ist, wäre es weder für uns noch für den Server, an den wir uns wenden, gut, so viele Anfragen auf einmal zu senden. Sicher, dies kann im Repository mit einer Drossel begrenzt werden, aber wir möchten es vielleicht auch auf der Anwendungsseite steuern, für die wir Flow verwenden könnten. In diesem Fall, um die Anzahl der gleichzeitigen Aufrufe auf 20 zu begrenzen, können wir `flatMapMerge` (eine der Flow-Verarbeitungsfunktionen, die wir im Kapitel *Flow-Verarbeitung* erklären werden) mit einem Gleichzeitigkeitsmodifikator verwenden, der auf 20 eingestellt ist.


```kotlin
suspend fun getOffers(
   sellers: List<Seller>
): List<Offer> = sellers
   .asFlow()
   .flatMapMerge(concurrency = 20) { seller ->
       suspend { api.requestOffers(seller.id) }.asFlow()
   }
   .toList()
```

Die Bedienung von Flow anstelle auf einer Sammlung gibt uns viel mehr Kontrolle über das Nebenläufigkeitsverhalten, Kontexte, Ausnahmen und vieles mehr. Wir werden diese Funktionen in den nächsten Kapiteln entdecken. Dies ist, wo (nach meiner Erfahrung) Flow am meisten nützlich ist. Ich hoffe, Sie werden das deutlich sehen, sobald wir alle seine verschiedenen Funktionen abgedeckt haben.

Schließlich, da sie einen reaktiven Programmierstil bevorzugen, nutzen einige Teams gerne Flow anstelle von suspendierenden Funktionen. Ein solcher Stil wurde auf Android beliebt, wo RxJava populär war, aber jetzt wird Flow oft als bessere Alternative behandelt. In solchen Teams wird Flow oft verwendet, wenn nur ein einziger Wert von Funktionen zurückgegeben wird. Ich bevorzuge in solchen Fällen einfach suspendierende Funktionen, aber beide Vorgehensweisen sind akzeptabel.

Wie Sie sehen können, gibt es ziemlich viele Anwendungsfälle für Flows. In einigen Projekten werden sie oft verwendet, in anderen nur gelegentlich, aber ich hoffe, Sie können sehen, dass sie nützlich sind und es lohnt sich, sie zu lernen.

### Zusammenfassung

In diesem Kapitel haben wir das Flow-Konzept eingeführt. Es repräsentiert einen Strom von asynchron berechnete Werte, die Coroutinen unterstützt (anders als Sequenzen). Es gibt ziemlich viele Anwendungsfälle, in denen Flow von Nutzen ist. Wir werden sie in den nächsten Kapiteln erkunden, während wir mehr über die Flow-Fähigkeiten lernen.


