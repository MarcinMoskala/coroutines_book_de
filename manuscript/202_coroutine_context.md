## Coroutine-Kontext

Wenn Sie sich die Definitionen der Coroutine builders ansehen, werden Sie feststellen, dass ihr erster Parameter vom Typ `CoroutineContext` ist.

```kotlin
public fun CoroutineScope.launch(
   context: CoroutineContext = EmptyCoroutineContext,
   start: CoroutineStart = CoroutineStart.DEFAULT,
   block: suspend CoroutineScope.() -> Unit
): Job {
   ...
}
```


Der Empfänger und der des letzten Arguments sind beide vom Typ `CoroutineScope`[^202_1]. Dieser `CoroutineScope` scheint ein wichtiges Konzept zu sein, also schauen wir uns seine Definition an:


```kotlin
public interface CoroutineScope {
    public val coroutineContext: CoroutineContext
}
```

Es scheint lediglich ein Wrapper um `CoroutineContext` zu sein. Sie möchten vielleicht darüber nachdenken, wie `Continuation` definiert wurde.

```kotlin
public interface Continuation<in T> {
    public val context: CoroutineContext
    public fun resumeWith(result: Result<T>)
}
```


`Continuation` enthält ebenfalls `CoroutineContext`. Dieser Typ wird von den wichtigsten Kotlin Coroutinen Elemente verwendet. Das muss ein wirklich wichtiges Konzept sein, also was ist es?

### `CoroutineContext` Interface

`CoroutineContext` ist ein Interface, das ein Element oder eine Sammlung von Elementen repräsentiert. Es ist konzeptuell ähnlich wie eine Map oder eine Set-Sammlung: es handelt sich um ein indiziertes Set von `Element`-Instanzen wie `Job`, `CoroutineName`, `CoroutineDispatcher`, usw. Das Ungewöhnliche ist, dass jedes `Element` auch ein `CoroutineContext` ist. Also ist jedes Element in einer Sammlung selbst eine Sammlung.

Dieses Konzept ist ziemlich intuitiv. Stellen Sie sich eine Tasse vor. Sie ist ein einzelnes Element, aber sie ist auch eine Sammlung, die ein einzelnes Element enthält. Wenn Sie eine weitere Tasse hinzufügen, haben Sie eine Sammlung mit zwei Elementen.

Um eine bequeme Kontextspezifikation und -modifikation zu ermöglichen, ist jedes `CoroutineContext`-Element selbst ein `CoroutineContext`, wie im folgenden Beispiel (das Hinzufügen von Kontexten und das Festlegen eines Coroutine-Builders-Kontext wird später erklärt). Es ist viel einfacher, Kontexte zu spezifizieren oder hinzuzufügen, als ein explizites Set zu erstellen.


```kotlin
launch(CoroutineName("Name1")) { ... }
launch(CoroutineName("Name2") + Job()) { ... }
```


Jedes Element in dieser Menge hat einen eindeutigen `Key`, der zur Identifizierung verwendet wird. Diese Schlüssel werden per Referenz verglichen.

Zum Beispiel: `CoroutineName` oder `Job` sind Implementierungen von `CoroutineContext.Element`, welches das `CoroutineContext` Interface umsetzt.

{crop-start: 5, crop-end: 13}
```kotlin
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

//sampleStart
fun main() {
    val name: CoroutineName = CoroutineName("A name")
    val element: CoroutineContext.Element = name
    val context: CoroutineContext = element

    val job: Job = Job()
    val jobElement: CoroutineContext.Element = job
    val jobContext: CoroutineContext = jobElement
}
//sampleEnd
```


Es ist dasselbe mit `SupervisorJob`, `CoroutineExceptionHandler` und den Dispatchern vom `Dispatchers` Objekt. Das sind die wichtigsten Coroutine-Kontexte. Sie werden in den nächsten Kapiteln erklärt.

### Elemente im CoroutineContext finden

Da `CoroutineContext` wie eine Sammlung ist, können wir ein Element mit einem konkreten Schlüssel mithilfe von `get` finden. Eine andere Möglichkeit besteht darin, eckige Klammern zu verwenden, da in Kotlin die `get` Methode ein Operator ist und mithilfe von eckigen Klammern anstelle eines expliziten Funktionsaufrufs aufgerufen werden kann. Genauso wie bei `Map`: Wenn ein Element im Kontext ist, wird es zurückgegeben. Ist es nicht vorhanden, wird stattdessen `null` zurückgegeben.

{crop-start: 5, crop-end: 12}
```kotlin
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

//sampleStart
fun main() {
    val ctx: CoroutineContext = CoroutineName("A name")

    val coroutineName: CoroutineName? = ctx[CoroutineName]
    // or ctx.get(CoroutineName)
    println(coroutineName?.name) // A name
    val job: Job? = ctx[Job] // or ctx.get(Job)
    println(job) // null
}
//sampleEnd
```


> `CoroutineContext` ist Teil der eingebauten Unterstützung für Kotlin-Coroutinen und wird daher aus `kotlin.coroutines` importiert, während Kontexte wie `Job` oder `CoroutineName` Teil der kotlinx.coroutines-Bibliothek sind und daher aus `kotlinx.coroutines` importiert werden müssen.

Um einen `CoroutineName` zu finden, verwenden wir einfach `CoroutineName`. Dies ist weder ein Typ noch eine Klasse, sondern ein Companion-Objekt. In Kotlin wirkt ein Klassenname, der für sich allein steht, als Referenz auf sein Companion-Objekt, somit ist `ctx[CoroutineName]` einfach eine Abkürzung für `ctx[CoroutineName.Key]`.


```kotlin
data class CoroutineName(
    val name: String
) : AbstractCoroutineContextElement(CoroutineName) {

    override fun toString(): String = "CoroutineName($name)"

    companion object Key : CoroutineContext.Key<CoroutineName>
}
```


Es ist üblich in der kotlinx.coroutines Bibliothek, Companion Objects als Schlüssel für Elemente mit dem gleichen Namen zu verwenden. Dies macht es leichter sich zu merken[^202_2]. Ein Schlüssel könnte auf eine Klasse (wie `CoroutineName`) oder auf ein Interface (wie `Job`) verweisen, das von vielen Klassen implementiert wird (wie `Job` und `SupervisorJob`).


```kotlin
interface Job : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<Job>

    // ...
}
```


### Hinzufügen von Kontexten

Was `CoroutineContext` wirklich nützlich macht, ist die Fähigkeit, zwei davon zusammenzufügen.

Wenn zwei Elemente mit unterschiedlichen Schlüsseln hinzugefügt werden, reagiert der resultierende Kontext auf beide Schlüssel.

{crop-start: 5, crop-end: 17}
```kotlin
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

//sampleStart
fun main() {
    val ctx1: CoroutineContext = CoroutineName("Name1")
    println(ctx1[CoroutineName]?.name) // Name1
    println(ctx1[Job]?.isActive) // null

    val ctx2: CoroutineContext = Job()
    println(ctx2[CoroutineName]?.name) // null
    println(ctx2[Job]?.isActive) // true, because "Active"
    // is the default state of a job created this way

    val ctx3 = ctx1 + ctx2
    println(ctx3[CoroutineName]?.name) // Name1
    println(ctx3[Job]?.isActive) // true
}
//sampleEnd
```

Wenn ein weiteres Element mit demselben Schlüssel hinzugefügt wird, genau wie in einer Map, ersetzt das neue Element das vorherige.

{crop-start: 4, crop-end: 13}
```kotlin
import kotlinx.coroutines.CoroutineName
import kotlin.coroutines.CoroutineContext

//sampleStart
fun main() {
    val ctx1: CoroutineContext = CoroutineName("Name1")
    println(ctx1[CoroutineName]?.name) // Name1

    val ctx2: CoroutineContext = CoroutineName("Name2")
    println(ctx2[CoroutineName]?.name) // Name2

    val ctx3 = ctx1 + ctx2
    println(ctx3[CoroutineName]?.name) // Name2
}
//sampleEnd
```


### Leerer CoroutineContext

Da `CoroutineContext` wie eine Sammlung ist, haben wir auch einen leeren Kontext. Ein solcher Kontext liefert an sich keine Elemente; wenn wir ihn zu einem anderen Kontext hinzufügen, verhält er sich genau wie dieser andere Kontext.

{crop-start: 6, crop-end: 13}
```kotlin
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

//sampleStart
fun main() {
    val empty: CoroutineContext = EmptyCoroutineContext
    println(empty[CoroutineName]) // null
    println(empty[Job]) // null

    val ctxName = empty + CoroutineName("Name1") + empty
    println(ctxName[CoroutineName]) // CoroutineName(Name1)
}
//sampleEnd
```


### Entfernen von Elementen

Elemente können auch durch ihren Schlüssel aus einem Kontext entfernt werden, indem die Funktion `minusKey` verwendet wird.

> Der Operator `minus` ist nicht für `CoroutineContext` überladen worden. Ich denke, dies liegt daran, dass seine Bedeutung nicht klar genug wäre, wie in Effective Kotlin *Punkt 12: Die Bedeutung eines Operators sollte konsistent mit seinem Funktionsnamen sein* erklärt wird.

{crop-start: 4, crop-end: 17}
```kotlin
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job

//sampleStart
fun main() {
    val ctx = CoroutineName("Name1") + Job()
    println(ctx[CoroutineName]?.name) // Name1
    println(ctx[Job]?.isActive) // true

    val ctx2 = ctx.minusKey(CoroutineName)
    println(ctx2[CoroutineName]?.name) // null
    println(ctx2[Job]?.isActive) // true

    val ctx3 = (ctx + CoroutineName("Name2"))
        .minusKey(CoroutineName)
    println(ctx3[CoroutineName]?.name) // null
    println(ctx3[Job]?.isActive) // true
}
//sampleEnd
```


### Faltung des Kontexts

Wenn wir für jedes Element in einem Kontext etwas tun müssen, können wir die `fold` Methode nutzen, die ähnlich zur `fold` Methode in anderen Sammlungen ist. Sie erfordert:
* einen anfänglichen Akkumulatorwert;
* eine Operation, die den nächsten Zustand des Akkumulators erzeugt, basierend auf dem aktuellen Zustand und dem Element, auf das sie gerade angewendet wird.

{crop-start: 5, crop-end: 17}
```kotlin
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

//sampleStart
fun main() {
    val ctx = CoroutineName("Name1") + Job()

    ctx.fold("") { acc, element -> "$acc$element " }
        .also(::println)
    // CoroutineName(Name1) JobImpl{Active}@dbab622e

    val empty = emptyList<CoroutineContext>()
    ctx.fold(empty) { acc, element -> acc + element }
        .joinToString()
        .also(::println)
    // CoroutineName(Name1), JobImpl{Active}@dbab622e
}
//sampleEnd
```


### Coroutine-Kontext und Ersteller

`CoroutineContext` ist also nur eine Möglichkeit Daten zu speichern und zu übergeben. Standardmäßig übergibt das übergeordnete Element seinen Kontext an das untergeordnete Element, was einer der Effekte der Beziehung zwischen übergeordneten und untergeordneten Elementen ist. Man sagt, dass das untergeordnete Element den Kontext von seinem übergeordneten Element erbt.

{crop-start: 3, crop-end: 21}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun CoroutineScope.log(msg: String) {
    val name = coroutineContext[CoroutineName]?.name
    println("[$name] $msg")
}

fun main() = runBlocking(CoroutineName("main")) {
    log("Started") // [main] Started
    val v1 = async {
        delay(500)
        log("Running async") // [main] Running async
        42
    }
    launch {
        delay(1000)
        log("Running launch") // [main] Running launch
    }
    log("The answer is ${v1.await()}")
    // [main] The answer is 42
}
//sampleEnd
```

Für jedes Kind könnte ein spezifischer Kontext im Argument definiert sein. Dieser Kontext überschreibt den der Eltern.

{crop-start: 8, crop-end: 21}
```kotlin
import kotlinx.coroutines.*

fun CoroutineScope.log(msg: String) {
    val name = coroutineContext[CoroutineName]?.name
    println("[$name] $msg")
}

//sampleStart
fun main() = runBlocking(CoroutineName("main")) {
    log("Started") // [main] Started
    val v1 = async(CoroutineName("c1")) {
        delay(500)
        log("Running async") // [c1] Running async
        42
    }
    launch(CoroutineName("c2")) {
        delay(1000)
        log("Running launch") // [c2] Running launch
    }
    log("The answer is ${v1.await()}")
    // [main] The answer is 42
}
//sampleEnd
```

Eine vereinfachte Formel zur Berechnung eines Coroutine-Kontexts lautet:

```kotlin
defaultContext + parentContext + childContext
```

Da neue Elemente immer alte mit dem gleichem Schlüssel ersetzen, überschreibt der Unterkontext stets Elemente mit dem gleichen Schlüssel aus dem übergeordneten Kontext. Die Standardwerte werden nur für Schlüssel verwendet, die ansonsten nirgendwo spezifiziert sind. Derzeit legen die Standardeinstellungen `Dispatchers.Default` fest, wenn kein `ContinuationInterceptor` gesetzt ist, und sie setzen nur `CoroutineId`, wenn die Anwendung im Debug-Modus ist.

Es gibt einen speziellen Kontext namens `Job`, der veränderbar ist und zur Kommunikation zwischen einem Coroutine-Unterkontext und seinem übergeordneten Kontext verwendet wird. Die nächsten Kapitel widmen sich den Auswirkungen dieser Kommunikation.

### Zugriff auf den Kontext in einer unterbrechenden Funktion

`CoroutineScope` hat eine Eigenschaft `coroutineContext`, die verwendet werden kann, um auf den Kontext zuzugreifen. Aber was, wenn wir uns in einer regulären unterbrechenden Funktion befinden? Wie Sie sich vielleicht aus dem Kapitel *Coroutinen unter der Haube* erinnern, wird der Kontext durch Fortsetzungen referenziert, die an jede unterbrechende Funktion weitergegeben werden. Es ist also möglich, auf den Kontext des Übergeordneten in einer unterbrechenden Funktion zuzugreifen. Dazu verwenden wir die Eigenschaft `coroutineContext`, die in jedem unterbrechenden Bereich verfügbar ist.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

suspend fun printName() {
    println(coroutineContext[CoroutineName]?.name)
}

suspend fun main() = withContext(CoroutineName("Outer")) {
    printName() // Outer
    launch(CoroutineName("Inner")) {
        printName() // Inner
    }
    delay(10)
    printName() // Outer
}
```


### Erstellen unseres eigenen Kontexts

Es ist nicht üblich, aber wir können unseren eigenen Coroutine-Kontext ziemlich einfach erstellen. Am einfachsten ist es, eine Klasse zu erstellen, die das `CoroutineContext.Element`-Interface implementiert. Eine solche Klasse benötigt eine Eigenschaft `key` vom Typ `CoroutineContext.Key<*>`. Dieser Schlüssel wird als der Schlüssel verwendet, der diesen Kontext identifiziert. Üblicherweise wird das Companion Object dieser Klasse als Schlüssel verwendet. So sieht eine einfache Implementierung eines Coroutine-Kontexts aus:


```kotlin
class MyCustomContext : CoroutineContext.Element {

    override val key: CoroutineContext.Key<*> = Key

    companion object Key :
        CoroutineContext.Key<MyCustomContext>
}
```

Ein solcher Kontext wird sich sehr ähnlich wie `CoroutineName` verhalten: Er leitet sich von der Eltern- zur Kind-Koroutine weiter, aber jede Kind-Koroutine kann es mit einem anderen Kontext mit demselben Schlüssel überschreiben. Um dies in der Praxis zu sehen, finden Sie unten ein Beispiel für einen Kontext, der dazu dient, aufeinanderfolgende Zahlen auszugeben.

{crop-start: 5}
```kotlin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class CounterContext(
    private val name: String
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key
    private var nextNumber = 0

    fun printNext() {
        println("$name: $nextNumber")
        nextNumber++
    }

    companion object Key :CoroutineContext.Key<CounterContext>
}

suspend fun printNext() {
    coroutineContext[CounterContext]?.printNext()
}

suspend fun main(): Unit =
    withContext(CounterContext("Outer")) {
        printNext() // Outer: 0
        launch {
            printNext() // Outer: 1
            launch {
                printNext() // Outer: 2
            }
            launch(CounterContext("Inner")) {
                printNext() // Inner: 0
                printNext() // Inner: 1
                launch {
                    printNext() // Inner: 2
                }
            }
        }
        printNext() // Outer: 3
    }
```


Ich habe gesehen, wie kundenspezifische Kontexte als eine Art von Dependency Injection verwendet werden - um leicht verschiedene Werte in der Produktion als bei Tests einzuspritzen. Ich denke jedoch nicht, dass dies zum Standardverfahren werden wird.

{crop-start: 6}
```kotlin
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.test.assertEquals

data class User(val id: String, val name: String)

abstract class UuidProviderContext :
    CoroutineContext.Element {

    abstract fun nextUuid(): String

    override val key: CoroutineContext.Key<*> = Key

    companion object Key :
        CoroutineContext.Key<UuidProviderContext>
}

class RealUuidProviderContext : UuidProviderContext() {
    override fun nextUuid(): String =
        UUID.randomUUID().toString()
}

class FakeUuidProviderContext(
    private val fakeUuid: String
) : UuidProviderContext() {
    override fun nextUuid(): String = fakeUuid
}

suspend fun nextUuid(): String =
    checkNotNull(coroutineContext[UuidProviderContext]) {
        "UuidProviderContext not present"
    }
        .nextUuid()

// function under test
suspend fun makeUser(name: String) = User(
    id = nextUuid(),
    name = name
)

suspend fun main(): Unit {
    // production case
    withContext(RealUuidProviderContext()) {
        println(makeUser("Michał"))
        // e.g. User(id=d260482a-..., name=Michał)
    }

    // test case
    withContext(FakeUuidProviderContext("FAKE_UUID")) {
        val user = makeUser("Michał")
        println(user) // User(id=FAKE_UUID, name=Michał)
        assertEquals(User("FAKE_UUID", "Michał"), user)
    }
}
```

### Zusammenfassung

`CoroutineContext` ist konzeptionell ähnlich wie eine Map oder eine Set-Sammlung. Es handelt sich um ein indiziertes Set von `Element`-Instanzen, wobei jedes `Element` auch ein `CoroutineContext` ist. Jedes Element darin hat einen eindeutigen `Key`, der zur Identifizierung verwendet wird. Auf diese Weise ist `CoroutineContext` einfach eine universelle Methode, um Objekte zu gruppieren und an Coroutinen zu übergeben. Diese Objekte werden von den Coroutinen aufbewahrt und können bestimmen, wie diese Coroutinen ausgeführt werden sollten (was ihr Zustand ist, in welchem Thread sie laufen, etc). In den folgenden Kapiteln werden wir uns mit den wichtigsten Kontexten von Coroutinen in der Kotlin-Bibliothek beschäftigen.

[^202_1]: Klären wir die Nomenklatur. `launch` ist eine Erweiterungsfunktion auf `CoroutineScope`, daher ist `CoroutineScope` ihr Empfängertyp. Der Empfänger der Erweiterungsfunktion ist das Objekt, auf das wir mit `this` verweisen.
[^202_2]: Das Begleiter-Objekt unten trägt den Namen `Key`. Wir können Begleiter-Objekte benennen, aber das ändert wenig daran, wie sie verwendet werden. Der Standardname für ein Begleiter-Objekt ist `Companion`, daher nutzen wir diesen Namen, wenn wir dieses Objekt über Reflexion referenzieren müssen oder wenn wir eine Erweiterungsfunktion darauf definieren. Hier verwenden wir stattdessen `Key`.


