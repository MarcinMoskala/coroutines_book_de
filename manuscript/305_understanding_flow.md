
## Einblick in `Flow`

Kotlin Coroutines Flow ist ein weitaus einfacheres Konzept, als die meisten Entwickler annehmen. Es handelt sich lediglich um eine Definition der auszuführenden Operationen. Es ähnelt einem suspendierenden Lambda-Ausdruck, enthält jedoch einige zusätzliche Elemente. In diesem Kapitel werde ich Ihnen Schritt für Schritt zeigen, wie Sie die `Flow`-Schnittstelle und den `flow`-Builder durch die Umwandlung eines Lambda-Ausdrucks implementieren können. Dies sollte Ihnen ein tiefgehendes Verständnis dafür vermitteln, wie Flow funktioniert. Dieses Kapitel richtet sich an neugierige Personen, die die Werkzeuge, die sie verwenden, wirklich verstehen möchten. Falls das nicht auf Sie zutrifft, können Sie dieses Kapitel gerne überspringen. Sollten Sie sich jedoch entscheiden, weiterzulesen, hoffe ich, dass es Ihnen Freude bereitet.

### Einblick in `Flow`

Wir beginnen unsere Erklärung mit einem einfachen Lambda-Ausdruck. Jeder Lambda-Ausdruck kann einmal definiert und dann mehrmals aufgerufen werden.

```kotlin
fun main() {
    val f: () -> Unit = {
        print("A")
        print("B")
        print("C")
    }
    f() // ABC
    f() // ABC
}
```

Um es etwas würziger zu machen, machen wir unseren Lambda-Ausdruck `suspend` und fügen wir eine Verzögerung in sie ein. Beachten Sie, dass jeder Aufruf eines solchen Lambda-Ausdrucks sequenziell ist, Sie sollten also keinen weiteren Aufruf tätigen, bis der vorherige abgeschlossen ist.

```kotlin
suspend fun main() {
    val f: suspend () -> Unit = {
        print("A")
        delay(1000)
        print("B")
        delay(1000)
        print("C")
    }
    f()
    f()
}
// A
// (1 sec)
// B
// (1 sec)
// C
// A
// (1 sec)
// B
// (1 sec)
// C
```



Ein Lambda-Ausdruck könnte einen Parameter enthalten, der eine Funktion repräsentieren kann. Wir werden diesen Parameter `emit` nennen. Wenn Sie also den Lambda-Ausdruck `f` rufen, müssen Sie einen anderen Lambda-Ausdruck bestimmen, der als `emit` verwendet wird.



```kotlin
suspend fun main() {
    val f: suspend ((String) -> Unit) -> Unit = { emit ->
        emit("A")
        emit("B")
        emit("C")
    }
    f { print(it) } // ABC
    f { print(it) } // ABC
}
```


Fakt ist, dass `emit` auch eine Suspending Funktion sein sollte. Unser Funktionstyp wird schon recht komplex, deshalb vereinfachen wir ihn durch die Definition eines `FlowCollector` Funktionsinterface mit einer abstrakten Methode namens `emit`. Wir werden dieses Interface anstelle des Funktionstyps nutzen. Das Interessante ist, dass Funktionsinterfaces mit Lambda-Ausdrücken definiert werden können, deshalb brauchen wir den Aufruf von `f` nicht zu ändern.


```kotlin
import kotlin.*

fun interface FlowCollector {
    suspend fun emit(value: String)
}

suspend fun main() {
    val f: suspend (FlowCollector) -> Unit = {
        it.emit("A")
        it.emit("B")
        it.emit("C")
    }
    f { print(it) } // ABC
    f { print(it) } // ABC
}
```



Das Aufrufen von `emit` auf `it` ist nicht praktisch; stattdessen machen wir `FlowCollector` zu einem Zielobjekt. Dadurch gibt es in unserem Lambda-Ausdruck ein Zielobjekt (`this` Begriff) vom Typ `FlowCollector`. Dies bedeutet, dass wir `this.emit` oder einfach nur `emit` aufrufen können. `f` wird immer noch auf die gleiche Weise aufgerufen.



```kotlin
fun interface FlowCollector {
    suspend fun emit(value: String)
}

suspend fun main() {
    val f: suspend FlowCollector.() -> Unit = {
        emit("A")
        emit("B")
        emit("C")
    }
    f { print(it) } // ABC
    f { print(it) } // ABC
}
```


Anstatt Lambda-Ausdrücke zu verwenden, bevorzugen wir ein Objekt, das eine Schnittstelle umsetzt. Wir werden diese Schnittstelle `Flow` nennen und unsere Definition mit einem Objektausdruck umgeben.


```kotlin
import kotlin.*

fun interface FlowCollector {
    suspend fun emit(value: String)
}

interface Flow {
    suspend fun collect(collector: FlowCollector)
}

suspend fun main() {
    val builder: suspend FlowCollector.() -> Unit = {
        emit("A")
        emit("B")
        emit("C")
    }
    val flow: Flow = object : Flow {
        override suspend fun collect(
            collector: FlowCollector
        ) {
            collector.builder()
        }
    }
    flow.collect { print(it) } // ABC
    flow.collect { print(it) } // ABC
}
```

Schließlich definieren wir die `flow` Builder-Funktion, um unsere Flow-Erstellung zu vereinfachen.

```kotlin
import kotlin.*

fun interface FlowCollector {
    suspend fun emit(value: String)
}

interface Flow {
    suspend fun collect(collector: FlowCollector)
}

fun flow(
    builder: suspend FlowCollector.() -> Unit
) = object : Flow {
    override suspend fun collect(collector: FlowCollector) {
        collector.builder()
    }
}

suspend fun main() {
    val f: Flow = flow {
        emit("A")
        emit("B")
        emit("C")
    }
    f.collect { print(it) } // ABC
    f.collect { print(it) } // ABC
}
```


Die letzte Änderung, die wir benötigen, ist `String` durch einen generischen Typparameter zu ersetzen, um das Ausgeben und Sammeln von beliebigen Werten zu ermöglichen.


```kotlin
import kotlin.*

fun interface FlowCollector<T> {
    suspend fun emit(value: T)
}

interface Flow<T> {
    suspend fun collect(collector: FlowCollector<T>)
}

fun <T> flow(
    builder: suspend FlowCollector<T>.() -> Unit
) = object : Flow<T> {
    override suspend fun collect(
        collector: FlowCollector<T>
    ) {
        collector.builder()
    }
}

suspend fun main() {
    val f: Flow<String> = flow {
        emit("A")
        emit("B")
        emit("C")
    }
    f.collect { print(it) } // ABC
    f.collect { print(it) } // ABC
}
```



Das ist es! Dies entspricht fast genau der Implementierung von `Flow`, `FlowCollector` und `flow`. Wenn Sie `collect` aufrufen, setzen Sie den Lambda-Ausdruck aus dem `flow`-Builder-Aufruf in Gang. Wenn dieser Ausdruck `emit` aufruft, ruft er den Lambda-Ausdruck auf, der festgelegt wurde, als `collect` aufgerufen wurde. So funktioniert das System.

Der vorgestellte Builder ist die einfachste Methode, um einen Ablauf zu erstellen. Später werden wir andere Builder kennenlernen, aber sie nutzen im Grunde nur `flow` im Hintergrund.



```kotlin
public fun <T> Iterator<T>.asFlow(): Flow<T> = flow {
    forEach { value ->
        emit(value)
    }
}

public fun <T> Sequence<T>.asFlow(): Flow<T> = flow {
    forEach { value ->
        emit(value)
    }
}

public fun <T> flowOf(vararg elements: T): Flow<T> = flow {
    for (element in elements) {
        emit(element)
    }
}
```



### Wie die `Flow` Verarbeitung funktioniert

`Flow` kann als etwas komplizierter angesehen werden als Lambda-Ausdrücke mit einem Empfänger zu unterbrechen. Jedoch liegt die Stärke in all den Funktionen, die für ihre Erstellung, Verarbeitung und Beobachtung definiert sind. Die meisten von ihnen sind in Wirklichkeit sehr einfach. Wir werden in den nächsten Kapiteln mehr über sie lernen, aber ich möchte, dass du verstehst, dass die meisten von ihnen sehr einfach sind und leicht mit `flow`, `collect` und `emit` konstruiert werden können.

Betrachten Sie die `map` Funktion, die jedes Element eines Flows transformiert. Sie erstellt einen neuen Flow, deshalb nutzt sie den `flow` Ersteller. Wenn der Flow gestartet wird, muss der umfassende Flow ebenfalls gestartet werden; deshalb nutzt sie innerhalb des Erstellers die `collect` Methode. Wenn ein Element empfangen wird, transformiert `map` dieses Element und sendet es dann zum neuen Flow.



```kotlin
fun <T, R> Flow<T>.map(
    transformation: suspend (T) -> R
): Flow<R> = flow {
    collect {
        emit(transformation(it))
    }
}

suspend fun main() {
    flowOf("A", "B", "C")
        .map {
            delay(1000)
            it.lowercase()
        }
        .collect { println(it) }
}
// (1 sec)
// a
// (1 sec)
// b
// (1 sec)
// c
```

Das Verhalten der meisten Methoden, die wir in den nächsten Kapiteln kennenlernen werden, ist genauso einfach. Es ist wichtig, dies zu verstehen, denn es hilft uns nicht nur besser zu verstehen, wie unser Code funktioniert, sondern lehrt uns auch, ähnliche Funktionen zu schreiben.

```kotlin
fun <T> Flow<T>.filter(
    predicate: suspend (T) -> Boolean
): Flow<T> = flow {
    collect {
        if (predicate(it)) {
            emit(it)
        }
    }
}

fun <T> Flow<T>.onEach(
    action: suspend (T) -> Unit
): Flow<T> = flow {
    collect {
        action(it)
        emit(it)
    }
}

// simplified implementation
fun <T> Flow<T>.onStart(
    action: suspend () -> Unit
): Flow<T> = flow {
    action()
    collect {
        emit(it)
    }
}
```



### Flow ist gleichzeitig

Beachten Sie, dass Flow grundsätzlich gleichzeitig ist, genau wie pausierende Funktionen: Der `collect` Aufruf wird pausiert, bis der Flow abgeschlossen ist. Das bedeutet auch, dass ein Flow keine neuen Coroutinen startet. Seine einzelnen Schritte können es tun, genau wie pausierende Funktionen Coroutinen starten können, aber dies ist nicht das Standardverhalten für pausierende Funktionen. Die meisten Verarbeitungsschritte von Flow werden gleichzeitig ausgeführt, weshalb ein `delay` innerhalb von `onEach` eine Verzögerung **zwischen** jedem Element einführt, und nicht vor allen Elementen, wie man es erwarten könnte.



```kotlin
suspend fun main() {
    flowOf("A", "B", "C")
        .onEach { delay(1000) }
        .collect { println(it) }
}
// (1 sec)
// A
// (1 sec)
// B
// (1 sec)
// C
```


### Datenfluss und gemeinsam genutzte Zustände

Wenn Sie komplexere Algorithmen zur Datenflussverarbeitung implementieren, sollten Sie wissen, wann Sie den Zugriff auf Variablen synchronisieren müssen. Lassen Sie uns die wichtigsten Anwendungsfälle analysieren. Wenn Sie benutzerdefinierte Funktionen zur Datenflussverarbeitung implementieren, können Sie veränderbare Zustände innerhalb des Datenflusses definieren, ohne einen Mechanismus zur Synchronisation, da ein Schritt im Datenfluss von Natur aus synchronisiert ist.


```kotlin
fun <T, K> Flow<T>.distinctBy(
    keySelector: (T) -> K
) = flow {
    val sentKeys = mutableSetOf<K>()
    collect { value ->
        val key = keySelector(value)
        if (key !in sentKeys) {
            sentKeys.add(key)
            emit(value)
        }
    }
}
```


Hier ist ein Beispiel, das innerhalb eines Prozessschritts verwendet wird und konsistente Ergebnisse erzeugt; die Zählvariable wird immer auf 1000 erhöht.


```kotlin
fun Flow<*>.counter() = flow<Int> {
    var counter = 0
    collect {
        counter++
        // to make it busy for a while
        List(100) { Random.nextLong() }.shuffled().sorted()
        emit(counter)
    }
}

suspend fun main(): Unit = coroutineScope {
    val f1 = List(1000) { "$it" }.asFlow()
    val f2 = List(1000) { "$it" }.asFlow()
        .counter()
    
    
    
    
    launch { println(f1.counter().last()) } // 1000
    launch { println(f1.counter().last()) } // 1000
    launch { println(f2.last()) } // 1000
    launch { println(f2.last()) } // 1000
}
```


Es ist ein häufiger Fehler, eine Variable von außerhalb eines Schritts in einem Datenstrom in eine Funktion zu extrahieren. Eine solche Variable wird zwischen allen Coroutines geteilt, die Daten aus dem gleichen Datenstrom sammeln. **Sie erfordert Synchronisation und ist spezifisch für den Datenstrom, nicht für das Sammeln von Flussdaten**. Daher gibt `f2.last()` 2000 zurück, nicht 1000, weil es das Ergebnis des Zählens von Elementen aus zwei parallelen Ausführungen von Datenströmen ist.


```kotlin
fun Flow<*>.counter(): Flow<Int> {
    var counter = 0
    return this.map {
        counter++
        // to make it busy for a while
        List(100) { Random.nextLong() }.shuffled().sorted()
        counter
    }
}

suspend fun main(): Unit = coroutineScope {
    val f1 = List(1_000) { "$it" }.asFlow()
    val f2 = List(1_000) { "$it" }.asFlow()
        .counter()
    
    launch { println(f1.counter().last()) } // 1000
    launch { println(f1.counter().last()) } // 1000
    launch { println(f2.last()) } // less than 2000
    launch { println(f2.last()) } // less than 2000
}
```

Schließlich, genauso wie suspendierende Funktionen eine Synchronisierung benötigen wenn sie dieselben Variablen wie der Flow verwenden, benötigt eine Variable in einem Flow eine Synchronisation, wenn sie global definiert ist, im Kontext einer Klasse, oder außerhalb einer Funktion.

```kotlin
var counter = 0

fun Flow<*>.counter(): Flow<Int> = this.map {
    counter++
    // to make it busy for a while
    List(100) { Random.nextLong() }.shuffled().sorted()
    counter
}

suspend fun main(): Unit = coroutineScope {
    val f1 = List(1_000) { "$it" }.asFlow()
    val f2 = List(1_000) { "$it" }.asFlow()
        .counter()
    
    launch { println(f1.counter().last()) } // less than 4000
    launch { println(f1.counter().last()) } // less than 4000
    launch { println(f2.last()) } // less than 4000
    launch { println(f2.last()) } // less than 4000
}
```

### Schlussfolgerung

`Flow` kann als ein bisschen komplizierter als ein suspendierender Lambda-Ausdruck mit einem Empfängerobjekt betrachtet werden, und seine Verarbeitungsfunktionen erweitern ihn lediglich um neue Operationen. Es gibt keine Magie: Die Definition von `Flow` und den meisten seiner Methoden ist klar und direkt.
