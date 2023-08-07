## Coroutinen unter der Haube

Es gibt eine bestimmte Art von Person, die nicht akzeptieren kann, dass ein Auto einfach nur gefahren wird. Sie müssen unter die Haube schauen, um zu verstehen, wie es funktioniert. Ich bin eine dieser Personen, also musste ich einfach herausfinden, wie Coroutinen arbeiten. Wenn Sie auch so sind, werden Sie dieses Kapitel genießen. Wenn nicht, können Sie es einfach überspringen.

Dieses Kapitel führt keine neuen Werkzeuge ein, die Sie verwenden könnten. Es ist rein erläuternd. Es versucht zu erklären, wie Coroutinen auf einem zufriedenstellenden Niveau arbeiten. Die Schlüssellektionen sind:
- Suspendierende Funktionen sind wie Zustandsmaschinen, mit einem möglichen Zustand am Anfang der Funktion und nach jedem Aufruf einer suspendierenden Funktion.
- Sowohl die den Zustand identifizierende Zahl als auch die lokalen Daten werden im Kontinuationsobjekt aufbewahrt.
- Die Fortsetzung einer Funktion dekoriert eine Fortsetzung ihrer Aufruffunktion; als Ergebnis repräsentieren all diese Fortsetzungen einen Aufrufstapel, der verwendet wird, wenn wir eine Funktion fortsetzen oder eine fortgesetzte Funktion beenden.

Wenn Sie daran interessiert sind, einige internes Wissen (natürlich vereinfacht) zu lernen, lesen Sie weiter.

### Continuation-passing style

Es gibt einige Möglichkeiten, wie suspendierende Funktionen hätten implementiert werden können, aber das Kotlin-Team hat sich für eine Option namens [**Continuation-passing style**](https://en.wikipedia.org/wiki/Continuation-passing_style) entschieden. Das bedeutet, dass Kontinuationen (im vorherigen Kapitel erklärt) von Funktion zu Funktion als Argumente weitergegeben werden. Nach Konvention nimmt eine Kontinuation die letzte Parameterposition ein.

```kotlin
suspend fun getUser(): User?
suspend fun setUser(user: User)
suspend fun checkAvailability(flight: Flight): Boolean

// under the hood is
fun getUser(continuation: Continuation<*>): Any?
fun setUser(user: User, continuation: Continuation<*>): Any
fun checkAvailability(
  flight: Flight,
  continuation: Continuation<*>
): Any
```


Vielleicht haben Sie auch bemerkt, dass der Ergebnistyp, der eigentlich deklariert wurde, sich unterscheidet. Er hat sich zu `Any` oder `Any?` geändert. Warum ist das so? Der Grund dafür ist, dass eine "suspending function" möglicherweise suspendiert wird und daher keinen deklarierten Typ zurückgeben könnte. In einem solchen Fall gibt sie eine spezielle `COROUTINE_SUSPENDED` Markierung zurück, die wir später in der Praxis sehen werden. Beachten Sie vorerst, dass `getUser` entweder `User?` oder `COROUTINE_SUSPENDED` (welches vom Typ `Any` ist) zurückgeben könnte, muss ihr Ergebnistyp der nächstgelegene Obertyp von `User?` und `Any` sein, also ist es `Any?`. Vielleicht führt Kotlin eines Tages Union-Typen ein, in diesem Fall hätten wir stattdessen `User? | COROUTINE_SUSPENDED`.

### Eine sehr einfache Funktion

Um das Thema weiter zu vertiefen, starten wir mit einer einfachen Funktion, die etwas vor und nach einer Verzögerung ausgibt.


```kotlin
suspend fun myFunction() {
  println("Before")
  delay(1000) // suspending
  println("After")
}
```

Sie können bereits ableiten, wie die Funktionssignatur von `myFunction` im Detail aussehen wird:

```kotlin
fun myFunction(continuation: Continuation<*>): Any
```


Als nächstes muss diese Funktion ihre eigene Fortsetzung haben, um ihren Zustand zu behalten. Nennen wir es `MyFunctionContinuation` (die tatsächliche Fortsetzung ist ein Objektausdruck und hat keinen Namen, aber so lässt es sich leichter erklären). Am Anfang seines Rumpfes, wird `myFunction` die `continuation` (den Parameter) in ihre eigene Fortsetzung (`MyFunctionContinuation`) einbeziehen.


```kotlin
val continuation = MyFunctionContinuation(continuation)
```


Dies sollte nur erfolgen, wenn die Prozessfortführung noch nicht abgeschlossen ist. Wenn sie es ist, ist dies Teil des Wiederherstellungsprozesses, und wir sollten die Prozessfortführung unverändert lassen[^104_1] (das mag jetzt verwirrend sein, aber Sie werden später besser verstehen, warum).


```kotlin
val continuation =
  if (continuation is MyFunctionContinuation) continuation
  else MyFunctionContinuation(continuation)
```

Diese Bedingung lässt sich vereinfachen zu:

```kotlin
val continuation = continuation as? MyFunctionContinuation
  ?: MyFunctionContinuation(continuation)
```


Schließlich, lassen Sie uns über den Inhalt unserer Funktion sprechen.


```kotlin
suspend fun myFunction() {
  println("Before")
  delay(1000) // suspending
  println("After")
}
```


Die Funktion könnte von zwei Stellen aus gestartet werden: entweder vom Anfang (im Falle eines ersten Aufrufs) oder vom Punkt nach der Unterbrechung (im Falle der Wiederaufnahme nach einer Unterbrechung). Um den aktuellen Zustand zu identifizieren, verwenden wir ein Feld namens `label`. Am Anfang ist es `0`, daher wird die Funktion vom Anfang starten. Es wird jedoch vor jeder Unterbrechung auf den nächsten Zustand gesetzt, so dass wir genau nach der Unterbrechung beginnen, wenn wir fortsetzen.


```kotlin
// A simplified picture of how myFunction looks under the hood
fun myFunction(continuation: Continuation<Unit>): Any {
    val continuation = continuation as? MyFunctionContinuation
        ?: MyFunctionContinuation(continuation)

    if (continuation.label == 0) {
        println("Before")
        continuation.label = 1
        if (delay(1000, continuation) == COROUTINE_SUSPENDED){
            return COROUTINE_SUSPENDED
        }
    }
    if (continuation.label == 1) {
        println("After")
        return Unit
    }
    error("Impossible")
}
```

Das letzte wichtige Element wird ebenfalls in dem oben gezeigten Schnipsel präsentiert. Wenn `delay` ausgesetzt wird, gibt es `COROUTINE_SUSPENDED` zurück, dann gibt `myFunction` `COROUTINE_SUSPENDED` zurück; das Gleiche machen die Funktion, die diese Funktion aufgerufen hat, und die Funktion, die jene Funktion aufgerufen hat, und alle anderen Funktionen bis nach ganz oben auf dem Aufrufstapel[^104_4]. So endet eine Aussetzung all diese Funktionen und lässt den Thread für andere ausführbare Elemente (einschließlich Coroutinen) zur Verfügung.

Bevor wir weitergehen, analysieren wir den obigen Code. Was würde passieren, wenn dieser `delay` Aufruf `COROUTINE_SUSPENDED` nicht zurückgeben würde? Was wäre, wenn es stattdessen einfach `Unit` zurückgeben würde (wir wissen, dass es das nicht tut, aber stellen wir uns das mal vor)? Beachten Sie, dass, wenn die Verzögerung einfach `Unit` zurückgeben würde, wir einfach zum nächsten Zustand übergehen würden, und die Funktion würde sich genauso wie jede andere verhalten.

Jetzt sprechen wir über die Continuation, die als anonyme Klasse implementiert ist. Vereinfacht sieht es so aus:


```kotlin
cont = object : ContinuationImpl(continuation) {
    var result: Any? = null
    var label = 0

    override fun invokeSuspend(`$result`: Any?): Any? {
        this.result = `$result`;
        return myFunction(this);
    }
};
```


Um die Lesbarkeit unserer Funktion zu verbessern, habe ich mich entschieden, sie als eine Klasse namens `MyFunctionContinuation` darzustellen. Ich habe auch beschlossen, die Vererbung zu verbergen, indem ich den Körper von `ContinuationImpl` integriere. Die resultierende Klasse ist einfacher gestaltet: Ich habe viele Optimierungen und Funktionen ausgelassen, um nur das Wesentliche zu behalten.

> In der JVM werden Typparameter während der Kompilierung gelöscht; daher werden zum Beispiel sowohl `Continuation<Unit>` als auch `Continuation<String>` einfach zu `Continuation`. Da alles, was wir hier darstellen, eine Kotlin-Darstellung des JVM-Bytecodes ist, sollten Sie sich keine Sorgen um diese Typparameter machen.

Der untenstehende Code stellt eine vollständige Vereinfachung dar, wie unsere Funktion unter der Haube aussieht:

{crop-start: 8, crop-end: 46}
```kotlin
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume

//sampleStart
fun myFunction(continuation: Continuation<Unit>): Any {
    val continuation = continuation as? MyFunctionContinuation
        ?: MyFunctionContinuation(continuation)

    if (continuation.label == 0) {
        println("Before")
        continuation.label = 1
        if (delay(1000, continuation) == COROUTINE_SUSPENDED){
            return COROUTINE_SUSPENDED
        }
    }
    if (continuation.label == 1) {
        println("After")
        return Unit
    }
    error("Impossible")
}

class MyFunctionContinuation(
    val completion: Continuation<Unit>
) : Continuation<Unit> {
    override val context: CoroutineContext
        get() = completion.context

    var label = 0
    var result: Result<Any>? = null

    override fun resumeWith(result: Result<Unit>) {
        this.result = result
        val res = try {
            val r = myFunction(this)
            if (r == COROUTINE_SUSPENDED) return
            Result.success(r as Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
        completion.resumeWith(res)
    }
}
//sampleEnd

private val executor = Executors
    .newSingleThreadScheduledExecutor {
        Thread(it, "scheduler").apply { isDaemon = true }
    }

fun delay(timeMillis: Long, continuation: Continuation<Unit>): Any {
    executor.schedule({
        continuation.resume(Unit)
    }, timeMillis, TimeUnit.MILLISECONDS)
    return COROUTINE_SUSPENDED
}

fun main() {
    val EMPTY_CONTINUATION = object : Continuation<Unit> {
        override val context: CoroutineContext =
            EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            // This is root coroutine, we don't need anything in this example
        }
    }
    myFunction(EMPTY_CONTINUATION)
    Thread.sleep(2000)
    // Needed to don't let the main finish immediately.
}

val COROUTINE_SUSPENDED = Any()
```


Wenn Sie selbst analysieren möchten, was suspendierende Funktionen hinter den Kulissen sind, öffnen Sie die Funktion in IntelliJ IDEA, verwenden Sie Tools > Kotlin > Show Kotlin bytecode und klicken Sie auf den Button "Decompile". Als Ergebnis sehen Sie diesen in Java dekompilierten Code (also mehr oder weniger, wie dieser Code aussehen würde, wenn er in Java geschrieben wäre).

{width: 100%}
![Wie man den aus der Datei generierten Bytecode anzeigt.](104_show_kotlin_bytecode.png)

{width: 100%}
![Der aus der Datei generierte Bytecode. Beachten Sie den Button "Decompile", mit dem wir diesen Bytecode in Java dekompilieren können.](104_decompile.png)

{width: 100%}
![Bytecode der suspendierenden Kotlin-Funktion in Java dekompiliert.](104_decompiled_code.png)

### Eine Funktion mit einem Zustand

Wenn eine Funktion einen Zustand hat (wie lokale Variablen oder Parameter), der nach der Aussetzung wieder aufgenommen werden muss, muss dieser Zustand in der Kontinuation dieser Funktion gespeichert werden. Betrachten wir die folgende Funktion:


```kotlin
suspend fun myFunction() {
  println("Before")
  var counter = 0
  delay(1000) // suspending
  counter++
  println("Counter: $counter")
  println("After")
}
```


Hier wird `counter` in zwei Status benötigt (für ein Label gleichgestellt mit 0 und 1), daher muss es in der Fortsetzung beibehalten werden. Es wird direkt vor der Unterbrechung gespeichert. Das Wiederherstellen solcher Eigenschaften geschieht am Anfang der Funktion. Also, so sieht die (vereinfachte) Funktion unter der Haube aus:

{crop-start: 5, crop-end: 50}
```kotlin
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.*

//sampleStart
fun myFunction(continuation: Continuation<Unit>): Any {
    val continuation = continuation as? MyFunctionContinuation
        ?: MyFunctionContinuation(continuation)

    var counter = continuation.counter

    if (continuation.label == 0) {
        println("Before")
        counter = 0
        continuation.counter = counter
        continuation.label = 1
        if (delay(1000, continuation) == COROUTINE_SUSPENDED){
            return COROUTINE_SUSPENDED
        }
    }
    if (continuation.label == 1) {
        counter = (counter as Int) + 1
        println("Counter: $counter")
        println("After")
        return Unit
    }
    error("Impossible")
}

class MyFunctionContinuation(
    val completion: Continuation<Unit>
) : Continuation<Unit> {
    override val context: CoroutineContext
        get() = completion.context

    var result: Result<Unit>? = null
    var label = 0
    var counter = 0

    override fun resumeWith(result: Result<Unit>) {
        this.result = result
        val res = try {
            val r = myFunction(this)
            if (r == COROUTINE_SUSPENDED) return
            Result.success(r as Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
        completion.resumeWith(res)
    }
}
//sampleEnd

private val executor = Executors.newSingleThreadScheduledExecutor {
    Thread(it, "scheduler").apply { isDaemon = true }
}

fun delay(timeMillis: Long, continuation: Continuation<Unit>): Any {
    executor.schedule({ continuation.resume(Unit) }, timeMillis, TimeUnit.MILLISECONDS)
    return COROUTINE_SUSPENDED
}

fun main() {
    val EMPTY_CONTINUATION = object : Continuation<Unit> {
        override val context: CoroutineContext = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            // This is root coroutine, we don't need anything in this example
        }
    }
    myFunction(EMPTY_CONTINUATION)
    Thread.sleep(2000)
    // Needed to prevent main() from finishing immediately.
}

private val COROUTINE_SUSPENDED = Any()
```


### Eine Funktion, die mit einem Wert fortgesetzt wird

Die Situation ist etwas anders, wenn wir tatsächlich Daten nach der Unterbrechung erwarten. Analysieren wir die Funktion unten:


```kotlin
suspend fun printUser(token: String) {
  println("Before")
  val userId = getUserId(token) // suspending
  println("Got userId: $userId")
  val userName = getUserName(userId, token) // suspending
  println(User(userId, userName))
  println("After")
}
```


Hier gibt es zwei suspendierende Funktionen: `getUserId` und `getUserName`. Wir haben auch einen Parameter `token` hinzugefügt, und unsere suspendierende Funktion gibt auch einige Werte zurück. All dies muss in der Kontinuität gespeichert werden:
- `token`, weil es in den Zuständen 0 und 1 benötigt wird,
- `userId`, weil es in den Zuständen 1 und 2 benötigt wird,
- `result` vom Typ `Result`, das repräsentiert, wie diese Funktion fortgesetzt wurde.

Wenn die Funktion mit einem Wert fortgesetzt wurde, wird das Ergebnis `Result.Success(value)` sein. In einem solchen Fall können wir diesen Wert erhalten und verwenden. Wurde sie mit einer Exception fortgesetzt, wird das Ergebnis `Result.Failure(exception)` sein. In einem solchen Fall wird diese Exception geworfen.

{crop-start: 5, crop-end: 70}
```kotlin
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.*

//sampleStart
fun printUser(
    token: String,
    continuation: Continuation<*>
): Any {
    val continuation = continuation as? PrintUserContinuation
        ?: PrintUserContinuation(
            continuation as Continuation<Unit>,
            token
        )

    var result: Result<Any>? = continuation.result
    var userId: String? = continuation.userId
    val userName: String

    if (continuation.label == 0) {
        println("Before")
        continuation.label = 1
        val res = getUserId(token, continuation)
        if (res == COROUTINE_SUSPENDED) {
            return COROUTINE_SUSPENDED
        }
        result = Result.success(res)
    }
    if (continuation.label == 1) {
        userId = result!!.getOrThrow() as String
        println("Got userId: $userId")
        continuation.label = 2
        continuation.userId = userId
        val res = getUserName(userId, continuation)
        if (res == COROUTINE_SUSPENDED) {
            return COROUTINE_SUSPENDED
        }
        result = Result.success(res)
    }
    if (continuation.label == 2) {
        userName = result!!.getOrThrow() as String
        println(User(userId as String, userName))
        println("After")
        return Unit
    }
    error("Impossible")
}

class PrintUserContinuation(
    val completion: Continuation<Unit>,
    val token: String
) : Continuation<String> {
    override val context: CoroutineContext
        get() = completion.context

    var label = 0
    var result: Result<Any>? = null
    var userId: String? = null

    override fun resumeWith(result: Result<String>) {
        this.result = result
        val res = try {
            val r = printUser(token, this)
            if (r == COROUTINE_SUSPENDED) return
            Result.success(r as Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
        completion.resumeWith(res)
    }
}
//sampleEnd

fun main() {
    toStart()
}

private val executor = Executors.newSingleThreadScheduledExecutor {
    Thread(it, "scheduler").apply { isDaemon = true }
}

data class User(val id: String, val name: String)
object ApiException : Throwable("Fake API exception")

fun getUserId(token: String, continuation: Continuation<String>): Any {
    executor.schedule({ continuation.resume("SomeId") }, 1000, TimeUnit.MILLISECONDS)
    return COROUTINE_SUSPENDED
}

fun getUserName(userId: String, continuation: Continuation<String>): Any {
    executor.schedule({
        continuation.resume("SomeName")
        //        continuation.resumeWithException(ApiException)
    }, 1000, TimeUnit.MILLISECONDS)
    return COROUTINE_SUSPENDED
}

fun toStart() {
    val EMPTY_CONTINUATION = object : Continuation<Unit> {
        override val context: CoroutineContext = EmptyCoroutineContext

        override fun resumeWith(result: kotlin.Result<Unit>) {
            if (result.isFailure) {
                result.exceptionOrNull()?.printStackTrace()
            }
        }
    }
    printUser("SomeToken", EMPTY_CONTINUATION)
    Thread.sleep(3000)
    // Needed to prevent the function from finishing immediately.
}

private fun Result<*>.throwOnFailure() {
    if (isFailure) throw exceptionOrNull()!!
}

private val COROUTINE_SUSPENDED = Any()
```


### Der Aufrufstapel

Wenn Funktion `a` Funktion `b` aufruft, muss die virtuelle Maschine den Zustand von `a` irgendwo speichern, sowie die Adresse, an die die Ausführung zurückkehren soll, sobald `b` beendet ist. All dies wird in einer Struktur gespeichert, die als **Aufrufstapel**[^104_2] bezeichnet wird. Das Problem ist, dass wir beim Aussetzen einen Thread freigeben; als Ergebnis leeren wir unseren Aufrufstapel. Daher ist der Aufrufstapel nicht nützlich, wenn wir wiederaufnehmen. Stattdessen dienen die **Fortsetzungen** als Aufrufstapel. Jede Fortsetzung behält den Zustand, in dem wir ausgesetzt haben (als `label`), sowie die Felder, die die lokalen Variablen und Parameter der Funktion darstellen, und die Referenz zur Fortsetzung der Funktion, die diese Funktion aufgerufen hat. Eine Fortsetzung verweist auf eine andere, die auf eine andere verweist, usw. Als Ergebnis ist unsere Fortsetzung wie eine riesige Zwiebel: sie behält alles, was normalerweise auf dem Aufrufstapel aufbewahrt wird. Schauen Sie sich das folgende Beispiel an:


```kotlin
suspend fun a() {
    val user = readUser()
    b()
    b()
    b()
    println(user)
}

suspend fun b() {
    for (i in 1..10) {
        c(i)
    }
}

suspend fun c(i: Int) {
    delay(i * 100L)
    println("Tick")
}
```

Ein weiteres Beispiel könnte wie folgt dargestellt werden:

```
CContinuation(
 i = 4,
 label = 1,
 completion = BContinuation(
     i = 4,
     label = 1,
     completion = AContinuation(
         label = 2,
         user = User@1234,
         completion = ...
     )
 )
)
```


> Wenn Sie sich die obige Darstellung ansehen, wie oft wurde "Tick" bereits gedruckt (nehmen Sie an, `readUser` ist keine suspendierende Funktion)[^104_3]?

Wenn eine Kontinuation wieder aufgenommen wird, ruft jede Kontinuation zunächst ihre Funktion auf; sobald dies geschehen ist, nimmt diese Kontinuation die Ausführung der Kontinuation auf, die die Funktion aufgerufen hat. Diese Kontinuation ruft dann ihre Funktion auf und der Prozess wiederholt sich, bis der Stapel vollständig abgearbeitet ist.


```kotlin
override fun resumeWith(result: Result<String>) {
    this.result = result
    val res = try {
        val r = printUser(token, this)
        if (r == COROUTINE_SUSPENDED) return
        Result.success(r as Unit)
    } catch (e: Throwable) {
        Result.failure(e)
    }
    completion.resumeWith(res)
}
```

Denken Sie zum Beispiel an eine Situation, in der die Funktion `a` die Funktion `b` aufruft, welche wiederum die Funktion `c` aufruft, die dann ausgesetzt wird. Bei der Wiederaufnahme nimmt die `c` Fortsetzung zuerst die Funktion `c` wieder auf. Sobald diese Funktion abgeschlossen ist, setzt die `c` Fortsetzung die `b` Fortsetzung fort, die die Funktion `b` aufruft. Ist diese abgeschlossen, setzt die `b` Fortsetzung die `a` Fortsetzung fort, die wiederum die Funktion `a` aufruft.

Der gesamte Prozess kann mit der folgenden Skizze visualisiert werden:

{width: 100%}
![](104_call_stack.png)

Es verhält sich ähnlich mit Ausnahmen: Eine nicht eingefangene Ausnahme wird in `resumeWith` eingefangen und dann mit `Result.failure(e)` verpackt, und danach wird die Funktion, die unsere Funktion aufgerufen hat, mit diesem Ergebnis fortgesetzt.

Ich hoffe, dass dies Ihnen ein Bild davon vermittelt, was passiert, wenn wir aussetzen. Der Zustand muss in einer Kontinuation gespeichert werden, und der Aussetzungsmechanismus muss unterstützt werden. Wenn wir fortsetzen, müssen wir den Zustand aus der Kontinuation wiederherstellen und entweder das Ergebnis verwenden oder eine Ausnahme werfen.

{width: 100%}
![](suspension_operations.png)

### Der tatsächliche Code

Der tatsächliche Code, zu dem Kontinuationen und aussetzende Funktionen kompiliert werden, ist komplizierter, da er Optimierungen und einige zusätzliche Mechanismen enthält, wie:
* Erstellung einer besseren Ausnahme-Stack-Trace;
* Hinzufügen des Abfangens der Coroutine-Aussetzung (wir werden später über dieses Feature sprechen);
* Optimierungen auf verschiedenen Ebenen, wie das Entfernen unbenutzter Variablen oder die Tail-Call-Optimierung.

Hier ist ein Ausschnitt aus der `BaseContinuationImpl` der Kotlin-Version "1.5.30", der die tatsächliche Umsetzung von `resumeWith` zeigt (einige Methoden und Kommentare wurden ausgelassen):

```kotlin
internal abstract class BaseContinuationImpl(
    val completion: Continuation<Any?>?
) : Continuation<Any?>, CoroutineStackFrame, Serializable {
    // This implementation is final. This fact is used to
    // unroll resumeWith recursion.
    final override fun resumeWith(result: Result<Any?>) {
        // This loop unrolls recursion in
        // current.resumeWith(param) to make saner and
        // shorter stack traces on resume
        var current = this
        var param = result
        while (true) {
            // Invoke "resume" debug probe on every resumed
            // continuation, so that a debugging library
            // infrastructure can precisely track what part
            // of suspended call stack was already resumed
            probeCoroutineResumed(current)
            with(current) {
                val completion = completion!! // fail fast
                // when trying to resume continuation
                // without completion
                val outcome: Result<Any?> =
                    try {
                        val outcome = invokeSuspend(param)
                        if (outcome === COROUTINE_SUSPENDED)
                            return
                        Result.success(outcome)
                    } catch (exception: Throwable) {
                        Result.failure(exception)
                    }
                releaseIntercepted()
                // this state machine instance is terminating
                if (completion is BaseContinuationImpl) {
                    // unrolling recursion via loop
                    current = completion
                    param = outcome
                } else {
                    // top-level completion reached --
                    // invoke and return
                    completion.resumeWith(outcome)
                    return
                }
            }
        }
    }

    // ...
}
```


Wie Sie sehen können, verwendet es eine Schleife anstelle von Rekursion. Diese Änderung ermöglicht es dem eigentlichen Code, einige Optimierungen und Vereinfachungen vorzunehmen.

### Die Performance von suspendierenden Funktionen

Welche Kosten entstehen durch die Verwendung von suspendierenden Funktionen anstelle von regulären? Wenn man hinter die Kulissen schaut, könnten viele Leute den Eindruck haben, dass die Kosten erheblich sind, aber das ist nicht wahr. Das Bilden einer Funktion in Zustände ist so kostengünstig wie Zahlenvergleiche und das Springen von Ausführungen kostet fast nichts. Das Speichern eines Zustands in einer Kontinuation ist auch kostengünstig. Wir kopieren keine lokalen Variablen: wir lassen neue Variablen auf dieselben Speicherstellen verweisen. Die einzige Operation, die etwas kostet, ist das Erstellen einer Kontinuationsklasse, aber das ist immer noch kein großes Problem. Wenn Sie sich nicht um die Performance von RxJava oder Callbacks sorgen, sollten Sie sich definitiv keine Sorgen um die Performance von suspendierenden Funktionen machen.

### Zusammenfassung

Was tatsächlich dahinter steckt, ist komplizierter als ich es beschrieben habe, aber ich hoffe, dass Sie einen Einblick in die Interna von Coroutinen erhalten haben. Die wichtigsten Erkenntnisse sind:
- Suspendierende Funktionen gleichen Zustandsmaschinen, mit einem möglichen Zustand zu Beginn der Funktion und nach jedem Aufruf einer suspendierenden Funktion.
- Sowohl das Label, das den Zustand kennzeichnet, als auch die lokalen Daten werden im Kontinuationsobjekt gespeichert.
- Die Kontinuation einer Funktion erweitert die Kontinuation ihrer aufrufenden Funktion; als Ergebnis repräsentieren all diese Kontinuationen einen Aufrufstapel, der verwendet wird, wenn wir fortsetzen oder eine fortgesetzte Funktion abschließen.

[^104_1]: Der tatsächliche Mechanismus hier ist etwas komplizierter, da das erste Bit des Labels auch geändert wird und diese Änderung von der suspendierenden Funktion überprüft wird. Dieser Mechanismus ist für suspendierende Funktionen erforderlich, um Wiederholungen zu unterstützen. Dies wurde aus Gründen der Einfachheit übersprungen.
[^104_2]: Der Aufrufstapel hat begrenzten Platz. Wenn dieser vollständig genutzt wurde, tritt ein `StackOverflowError` auf. Erinnert Sie das an eine beliebte Website, die wir nutzen, um technische Fragen zu stellen oder zu beantworten?
[^104_3]: Die Antwort lautet 13. Da das Label auf `AContinuation` 2 ist, hat bereits ein Aufruf der `b` Funktion abgeschlossen (das bedeutet 10 Ticks). Da `i` gleich `4` ist, wurden in dieser `b` Funktion bereits drei Ticks ausgegeben.
[^104_4]: Konkreter gesagt, wird `COROUTINE_SUSPENDED` weitergegeben, bis es entweder die Builder-Funktion oder die 'resume'-Funktion erreicht.
