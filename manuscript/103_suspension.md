
## Wie funktioniert die Unterbrechung?

Unterbrechbare Funktionen sind das Markenzeichen von Kotlin-Koroutinen. Die Fähigkeit, die Ausführung zu unterbrechen, ist das grundlegendste Merkmal, auf dem alle anderen Konzepte von Kotlin-Koroutinen aufbauen. Deshalb ist unser Ziel in diesem Kapitel, ein solides Verständnis dafür aufzubauen, wie es funktioniert.

Eine Koroutine zu unterbrechen bedeutet, sie mitten in der Ausführung anzuhalten. Es ähnelt dem Anhalten eines Videospiels: Sie speichern an einem Checkpoint, schalten das Spiel ab und sowohl Sie als auch Ihr Computer können sich auf andere Aufgaben konzentrieren. Wenn Sie später weitermachen möchten, schalten Sie das Spiel wieder ein, setzen es vom gespeicherten Checkpoint aus fort und können genau dort weiterspielen, wo Sie zuvor aufgehört hatten. Dies ist eine Analogie zu Koroutinen. Wenn sie unterbrochen sind, geben sie eine `Continuation` zurück. Es ist wie ein Speichern in einem Spiel: wir können es verwenden, um an dem Punkt fortzufahren, an dem wir gestoppt haben.

Beachten Sie, dass dies sehr unterschiedlich von einem Thread ist, der nicht gespeichert, nur blockiert werden kann. Eine Koroutine ist viel leistungsfähiger. Wenn sie unterbrochen ist, verbraucht sie keine Ressourcen. Eine Koroutine kann auf einem anderen Thread fortgesetzt werden und (zumindest theoretisch) kann eine Continuation serialisiert, deserialisiert und dann fortgesetzt werden.

### Resume

Lassen Sie uns das nun in Aktion sehen. Dafür brauchen wir eine Koroutine. Wir starten Koroutinen mit Koroutinen-Buildern (wie `runBlocking` oder `launch`), die wir später einführen werden. Es gibt allerdings auch einen einfacheren Weg: Wir können eine unterbrechbare `main` Funktion verwenden.

Unterbrechbare Funktionen sind Funktionen, die eine Koroutine unterbrechen können. Das bedeutet, dass sie von einer Koroutine (oder einer anderen unterbrechbaren Funktion) aufgerufen werden müssen. Letztendlich müssen sie etwas zum Unterbrechen haben. Die Funktion `main` ist der Startpunkt, daher wird Kotlin sie in einer Koroutine starten, wenn wir sie ausführen.

{crop-start: 3, crop-end: 9}
```kotlin
import kotlin.*

//sampleStart
suspend fun main() {
    println("Before")

    println("After")
}
// Before
// After
//sampleEnd
```

Dies ist ein einfaches Programm, das "Before" und "After" ausgibt. Was passiert, wenn wir zwischen diesen beiden Ausgaben pausieren? Dafür können wir die `suspendCoroutine` Funktion verwenden, die uns die Standard Kotlin Bibliothek[^103_1] zur Verfügung stellt.

{crop-start: 3, crop-end: 10}
```kotlin
import kotlin.coroutines.*

//sampleStart
suspend fun main() {
    println("Before")

    suspendCoroutine<Unit> { }

    println("After")
}
// Before
//sampleEnd
```

Wenn Sie den oben genannten Code aufrufen, werden Sie "After" nicht sehen, und der Code hört nicht auf zu laufen (da unsere `main` Funktion nie abgeschlossen wurde). Die Koroutine wird nach "Before" ausgesetzt. Unser Programm wurde gestoppt und nie wieder aufgenommen. Also, wie können wir fortsetzen? Wo ist diese zuvor erwähnte `Continuation`?

Betrachten Sie noch einmal den Aufruf von `suspendCoroutine` und beachten Sie, dass er mit einem Lambda-Ausdruck (`{ }`) endet. Die Funktion, die als Argument übergeben wird, wird **bevor** die Aussetzung stattfindet aufgerufen. Diese Funktion bekommt eine Kontinuation als Argument übergeben.

{crop-start: 3, crop-end: 13}
```kotlin
import kotlin.coroutines.*

//sampleStart
suspend fun main() {
    println("Before")

    suspendCoroutine<Unit> { continuation ->
        println("Before too")
    }

    println("After")
}
// Before
// Before too
//sampleEnd
```


Dass eine Funktion eine andere Funktion an Ort und Stelle aufruft, ist nichts Ungewöhnliches. Dies ähnelt `let`, `apply` oder `useLines`. Die Funktion `suspendCoroutine` ist auf die gleiche Weise gestaltet, was es ermöglicht, die Kontinuation kurz vor der Aussetzung zu nutzen. Nach dem Aufruf von `suspendCoroutine` wäre es zu spät. Daher wird der Lambda-Ausdruck, der als Parameter an die Funktion `suspendCoroutine` übergeben wird, kurz vor der Aussetzung aufgerufen. Dieses Lambda wird dazu genutzt, diese Kontinuation irgendwo zu speichern oder zu planen, ob sie fortgesetzt werden soll.

Wir könnten es zur sofortigen Fortsetzung verwenden:

{crop-start: 3, crop-end: 13}
```kotlin
import kotlin.coroutines.*

//sampleStart
suspend fun main() {
    println("Before")

    suspendCoroutine<Unit> { continuation ->
        continuation.resume(Unit)
    }

    println("After")
}
// Before
// After
//sampleEnd
```

Beachten Sie, dass "After" im obigen Beispiel ausgegeben wird, weil wir `resume` in `suspendCoroutine`[^103_2] aufrufen.

> Seit Kotlin 1.3 hat sich die Definition von `Continuation` geändert. Statt `resume` und `resumeWithException` gibt es eine `resumeWith` Funktion, die `Result` erwartet. Die Funktionen `resume` und `resumeWithException`, die wir verwenden, sind Erweiterungsmethoden aus der Standardbibliothek, die `resumeWith` nutzen.

```kotlin
inline fun <T> Continuation<T>.resume(value: T): Unit =
    resumeWith(Result.success(value))

inline fun <T> Continuation<T>.resumeWithException(
    exception: Throwable
): Unit = resumeWith(Result.failure(exception))
```

Wir könnten auch einen anderen Thread starten, der für eine bestimmte Dauer schlafen wird und nach dieser Zeit wiederaufnehmen wird.

{crop-start: 4, crop-end: 22}
```kotlin
import kotlin.concurrent.thread
import kotlin.coroutines.*

//sampleStart
suspend fun main() {
    println("Before")

    suspendCoroutine<Unit> { continuation ->
        thread {
            println("Suspended")
            Thread.sleep(1000)
            continuation.resume(Unit)
            println("Resumed")
        }
    }

    println("After")
}
// Before
// Suspended
// (1 second delay)
// After
// Resumed
//sampleEnd
```

Dies ist eine wichtige Beobachtung. Beachten Sie, dass wir eine Funktion erstellen können, die unsere Kontinuität nach einem festgelegten Zeitraum wieder aufnimmt. In einem solchen Fall wird die Kontinuität durch die Lambda-Funktion erfasst, wie im unten gezeigten Codeausschnitt dargestellt.

{crop-start: 4, crop-end: 23}
```kotlin
import kotlin.concurrent.thread
import kotlin.coroutines.*

//sampleStart
fun continueAfterSecond(continuation: Continuation<Unit>) {
    thread {
        Thread.sleep(1000)
        continuation.resume(Unit)
    }
}

suspend fun main() {
    println("Before")

    suspendCoroutine<Unit> { continuation ->
        continueAfterSecond(continuation)
    }

    println("After")
}
// Before
// (1 sec)
// After
//sampleEnd
```


Ein solcher Mechanismus funktioniert zwar, aber er erzeugt unnötigerweise Threads, nur um sie nach gerade mal einer Sekunde Inaktivität wieder zu beenden. Threads sind kostspielig, also warum sollten wir sie verschwenden? Ein besserer Ansatz wäre, einen "Zeitgeber" einzurichten. In der JVM können wir dafür den `ScheduledExecutorService` verwenden. Wir können ihn so konfigurieren, dass er `continuation.resume(Unit)` nach einem festgelegten Zeitraum aufruft.

{crop-start: 4, crop-end: 22}
```kotlin
import java.util.concurrent.*
import kotlin.coroutines.*

//sampleStart
private val executor =
    Executors.newSingleThreadScheduledExecutor {
        Thread(it, "scheduler").apply { isDaemon = true }
    }

suspend fun main() {
    println("Before")

    suspendCoroutine<Unit> { continuation ->
        executor.schedule({
            continuation.resume(Unit)
        }, 1000, TimeUnit.MILLISECONDS)
    }

    println("After")
}
// Before
// (1 second delay)
// After
//sampleEnd
```

Das Unterbrechen für eine festgelegte Zeitspanne scheint eine nützliche Funktion zu sein. Lasst uns das in eine Funktion extrahieren. Wir werden sie `delay` nennen.

{crop-start: 4, crop-end: 25}
```kotlin
import java.util.concurrent.*
import kotlin.coroutines.*

//sampleStart
private val executor =
    Executors.newSingleThreadScheduledExecutor {
        Thread(it, "scheduler").apply { isDaemon = true }
    }

suspend fun delay(timeMillis: Long): Unit =
    suspendCoroutine { cont ->
        executor.schedule({
            cont.resume(Unit)
        }, timeMillis, TimeUnit.MILLISECONDS)
    }

suspend fun main() {
    println("Before")

    delay(1000)

    println("After")
}
// Before
// (1 second delay)
// After
//sampleEnd
```

Das Ausführungsprogramm verwendet weiterhin einen Prozessfaden, aber es handelt sich um einen Prozessfaden für alle Koroutinen, die die `delay` Funktion verwenden. Dies ist deutlich besser als jedes Mal einen Prozessfaden zu sperren, wenn wir einige Zeit warten müssen.

Genau so wurde die `delay` Funktion aus der Kotlin Koroutinen-Bibliothek früher implementiert. Die aktuelle Implementierung ist komplizierter, hauptsächlich um Tests zu unterstützen, aber die grundlegende Idee bleibt die gleiche.

### Mit einem Wert fortfahren

Eine Sache, die Sie vielleicht interessiert, ist, warum wir `Unit` an die `resume` Funktion übergeben haben. Sie fragen sich vielleicht auch, warum wir `Unit` als Typparameter für die `suspendCoroutine` verwendet haben. Dass diese beiden gleich sind, ist kein Zufall. `Unit` wird auch von der `resume` Funktion zurückgegeben und ist der generische Typ des `Continuation` Parameters.

```kotlin
val ret: Unit =
    suspendCoroutine<Unit> { cont: Continuation<Unit> ->
        cont.resume(Unit)
    }
```

Wenn wir `suspendCoroutine` aufrufen, können wir angeben, welcher Typ in seiner Fortsetzung zurückgegeben wird. Derselbe Typ muss verwendet werden, wenn wir `resume` aufrufen.

{crop-start: 3, crop-end: 18}
```kotlin
import kotlin.coroutines.*

//sampleStart
suspend fun main() {
    val i: Int = suspendCoroutine<Int> { cont ->
        cont.resume(42)
    }
    println(i) // 42

    val str: String = suspendCoroutine<String> { cont ->
        cont.resume("Some text")
    }
    println(str) // Some text

    val b: Boolean = suspendCoroutine<Boolean> { cont ->
        cont.resume(true)
    }
    println(b) // true
}
//sampleEnd
```

Dies passt nicht gut zum Vergleich mit einem Spiel. Mir ist kein Spiel bekannt, in dem man beim Fortsetzen eines gespeicherten Spiels etwas hinzufügen kann[^103_3] (es sei denn, man hat geschummelt und gegoogelt, wie man die nächste Herausforderung löst). Bei Koroutinen ergibt das jedoch vollkommen Sinn. Oft werden wir angehalten, weil wir auf Daten warten, wie eine Netzwerk-Antwort einer API. Dies ist ein häufiges Szenario. Dein Thread führt Business-Logik aus, bis er an einen Punkt kommt, wo er Daten benötigt. Also fordert er deine Netzwerkbibliothek auf, diese zu liefern. Ohne Koroutinen würde dieser Thread dann warten müssen. Das wäre eine enorme Verschwendung, denn Threads kosten viel Ressourcen, besonders wenn es sich um einen wichtigen Thread handelt, wie den Haupt-Thread auf Android. Mit Koroutinen wird er einfach angehalten und gibt der Bibliothek eine Fortführung mit der Anweisung "Sobald du diese Daten hast, sende sie einfach an die `resume` Funktion". Dann kann der Thread andere Aufgaben übernehmen. Sobald die Daten vorliegen, wird der Thread dazu verwendet, ab dem Punkt fortzusetzen, an dem die Koroutine angehalten wurde.

Um dies in der Praxis zu sehen, schauen wir uns an, wie wir möglicherweise warten, bis wir einige Daten erhalten. Im folgenden Beispiel verwenden wir eine `requestUser` Callback-Funktion, die extern implementiert ist.

{crop-start: 13, crop-end: 26}
```kotlin
import kotlin.concurrent.thread
import kotlin.coroutines.*

data class User(val name: String)

fun requestUser(callback: (User) -> Unit) {
    thread {
        Thread.sleep(1000)
        callback.invoke(User("Test"))
    }
}

//sampleStart
suspend fun main() {
    println("Before")
    val user = suspendCoroutine<User> { cont ->
        requestUser { user ->
            cont.resume(user)
        }
    }
    println(user)
    println("After")
}
// Before
// (1 second delay)
// User(name=Test)
// After
//sampleEnd
```


{pagebreak}

`suspendCoroutine` direkt aufzurufen ist nicht praktisch. Wir würden stattdessen lieber eine Unterbrechungsfunktion haben. Wir können es selbst extrahieren.

{crop-start: 13, crop-end: 26}
```kotlin
import kotlin.concurrent.thread
import kotlin.coroutines.*

data class User(val name: String)

fun requestUser(callback: (User) -> Unit) {
    thread {
        Thread.sleep(1000)
        callback.invoke(User("Test"))
    }
}

//sampleStart
suspend fun requestUser(): User {
    return suspendCoroutine<User> { cont ->
        requestUser { user ->
            cont.resume(user)
        }
    }
}

suspend fun main() {
    println("Before")
    val user = requestUser()
    println(user)
    println("After")
}
//sampleEnd
```


Aktuell werden Unterbrechbare Funktionen bereits von vielen beliebten Bibliotheken, wie zum Beispiel Retrofit und Room unterstützt. Das ist der Grund, warum wir selten Callback-Funktionen in Unterbrechbaren Funktionen verwenden müssen. Sollten Sie jedoch ein solches Bedürfnis haben, empfehle ich die Verwendung von `suspendCancellableCoroutine` (anstatt `suspendCoroutine`), was im Kapitel *Abbruch* erklärt wird.


```kotlin
suspend fun requestUser(): User {
    return suspendCancellableCoroutine<User> { cont ->
        requestUser { user ->
            cont.resume(user)
        }
    }
}
```

Sie fragen sich vielleicht, was passiert, wenn uns die API statt Daten ein Problem liefert. Was ist, wenn der Dienst nicht funktioniert oder mit einem Fehler antwortet? In einem solchen Fall können wir keine Daten zurückgeben; stattdessen sollten wir eine Ausnahme von dem Ort auslösen, an dem die Coroutine unterbrochen wurde. Hier müssen wir mit einer Ausnahme fortsetzen.

### Fortsetzung mit einer Ausnahme

Jede Funktion, die wir aufrufen, könnte einen Wert zurückgeben oder eine Ausnahme auslösen. Das Gleiche gilt für `suspendCoroutine`. Wenn `resume` aufgerufen wird, gibt es Daten zurück, die als Argument übergeben wurden. Wenn `resumeWithException` aufgerufen wird, wird die Ausnahme, die als Argument übergeben wurde, aus dem Unterbrechungspunkt geworfen.

{crop-start: 3, crop-end: 14}
```kotlin
import kotlin.coroutines.*

//sampleStart
class MyException : Throwable("Just an exception")

suspend fun main() {
    try {
        suspendCoroutine<Unit> { cont ->
            cont.resumeWithException(MyException())
        }
    } catch (e: MyException) {
        println("Caught!")
    }
}
// Caught!
//sampleEnd
```

Dieser Mechanismus wird für verschiedene Arten von Problemen verwendet. Zum Beispiel, um Netzwerkausnahmen zu signalisieren.

```kotlin
suspend fun requestUser(): User {
    return suspendCancellableCoroutine<User> { cont ->
        requestUser { resp ->
            if (resp.isSuccessful) {
                cont.resume(resp.data)
            } else {
                val e = ApiException(
                    resp.code,
                    resp.message
                )
                cont.resumeWithException(e)
            }
        }
    }
}

suspend fun requestNews(): News {
    return suspendCancellableCoroutine<News> { cont ->
        requestNews(
            onSuccess = { news -> cont.resume(news) },
            onError = { e -> cont.resumeWithException(e) }
        )
    }
}
```

### Eine Koroutine unterbrechen, nicht eine Funktion

Etwas, das hier hervorgehoben werden muss, ist, dass wir eine Koroutine aussetzen, nicht eine Funktion. Suspendierbare Funktionen sind keine Koroutinen, sondern nur Funktionen, die eine Koroutine aussetzen können[^103_4]. Stellen Sie sich vor, wir speichern eine Funktion in einer Variablen und versuchen, sie nach dem Funktionsaufruf fortzusetzen.

{crop-start: 3, crop-end: 20}
```kotlin
import kotlin.coroutines.*

//sampleStart
// Do not do this
var continuation: Continuation<Unit>? = null

suspend fun suspendAndSetContinuation() {
    suspendCoroutine<Unit> { cont ->
        continuation = cont
    }
}

suspend fun main() {
    println("Before")

    suspendAndSetContinuation()
    continuation?.resume(Unit)

    println("After")
}
// Before
//sampleEnd
```


Das macht keinen Sinn. Es ist vergleichbar mit dem Anhalten eines Spiels und der Planung, es zu einem späteren Zeitpunkt im Spiel wieder aufzunehmen. `resume` wird nie aufgerufen. Sie werden nur "Before" sehen und Ihr Programm wird nie beenden, es sei denn wir führen `resume` auf einem anderen Thread oder einer anderen Coroutine aus. Um dies zu veranschaulichen, können wir eine andere Coroutine festlegen, die nach einer Sekunde `resume` ausgeführt wird.

{crop-start: 4, crop-end: 26}
```kotlin
import kotlinx.coroutines.*
import kotlin.coroutines.*

//sampleStart
// Do not do this, potential memory leak
var continuation: Continuation<Unit>? = null

suspend fun suspendAndSetContinuation() {
   suspendCoroutine<Unit> { cont ->
       continuation = cont
   }
}

suspend fun main() = coroutineScope {
   println("Before")

   launch {
       delay(1000)
       continuation?.resume(Unit)
   }

   suspendAndSetContinuation()
   println("After")
}
// Before
// (1 second delay)
// After
//sampleEnd
```


### Zusammenfassung

Ich hoffe, jetzt haben Sie ein klares Bild davon, wie die Unterbrechung aus der Benutzersicht funktioniert. Es ist wichtig und wir werden es im Laufe des Buches sehen. Es ist zudem praktisch, da Sie nun Callback-Funktionen nehmen und sie in unterbrechende Funktionen umwandeln können. Wenn Sie wie ich sind und genau wissen wollen, wie Dinge funktionieren, fragen Sie sich wahrscheinlich immer noch, wie es umgesetzt wird. Wenn Sie neugierig auf dieses Thema sind, wird es im nächsten Kapitel behandelt. Falls Sie glauben, dass Sie es nicht wissen müssen, überspringen Sie es einfach. Es ist nicht sonderlich praktikabel, es enthüllt nur die Magie von Kotlin Coroutines.

[^103_1]: Es ruft direkt `suspendCoroutineUninterceptedOrReturn` auf, eine grundlegende Funktion, das bedeutet eine Funktion mit eingebauter Implementierung.
[^103_2]: Diese Aussage ist wahr, aber ich muss sie klären. Sie könnten denken, dass wir hier suspendieren und sofort fortfahren. Das ist eine gute Intuition, aber die Wahrheit ist, dass es eine Optimierung gibt, die eine Unterbrechung verhindert, wenn das Fortfahren unmittelbar erfolgt.
[^103_3]: Während einer Workshop-Diskussion stellte sich heraus, dass es ein solches Spiel gibt: In *Don't Starve Together* können Sie, wenn Sie fortfahren, die Spieler wechseln. Ich habe es selbst nicht gespielt, aber das klingt nach einer passenden Metapher für das Fortfahren mit einem Wert.
[^103_4]: Eine unterbrechende `main` Funktion ist ein Spezialfall. Der Kotlin Compiler startet sie in einer Coroutine.

