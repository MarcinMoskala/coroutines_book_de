## Abbruch

Eine sehr wichtige Funktionalität von Kotlin Coroutines ist der *Abbruch*. Er ist so wichtig, dass einige Klassen und Bibliotheken Unterbrechungsfunktionen hauptsächlich zur Unterstützung des Abbruchs verwenden[^204_1]. Es gibt einen guten Grund dafür: Ein guter Abbruchmechanismus ist Gold wert[^204_2]. Einen Thread einfach abzubrechen, ist eine schreckliche Lösung, da es eine Möglichkeit geben sollte, Verbindungen zu schließen und Ressourcen freizugeben. Es ist auch nicht praktisch, Entwickler ständig dazu zu zwingen, zu überprüfen, ob ein bestimmter Zustand noch aktiv ist. Das Problem des Abbruchs wartete sehr lange auf eine gute Lösung, aber was Kotlin Coroutines bietet, ist überraschend einfach: sie sind benutzerfreundlich und sicher. Dies ist der beste Abbruchmechanismus, den ich in meiner Karriere gesehen habe. Schauen wir uns das mal an.

### Grundlegender Abbruch

Die `Job`-Schnittstelle hat eine `cancel`-Methode, die ihren Abbruch ermöglicht. Ihr Aufruf löst die folgenden Effekte aus:
* Eine solche Coroutine beendet den Job am ersten Unterbrechungspunkt (`delay` im Beispiel unten).
* Wenn ein Job Unteraufgaben hat, werden diese auch abgebrochen (aber der übergeordnete Job ist nicht betroffen).
* Sobald ein Job abgebrochen ist, kann er nicht mehr als übergeordneter Job für neue Coroutinen verwendet werden. Er befindet sich zuerst im Zustand "Cancelling" und dann im Zustand "Cancelled".

{crop-start: 5, crop-end: 23}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val job = launch {
        repeat(1_000) { i ->
            delay(200)
            println("Printing $i")
        }
    }

    delay(1100)
    job.cancel()
    job.join()
    println("Cancelled successfully")
}
// Printing 0
// Printing 1
// Printing 2
// Printing 3
// Printing 4
// Cancelled successfully
//sampleEnd
```


Wir könnten mit einer anderen Ausnahme abbrechen (indem wir eine Ausnahme als Argument an die `cancel` Funktion übergeben), um den Grund anzugeben. Dieser Grund muss ein Subtyp von `CancellationException` sein, da nur eine Ausnahme dieses Typs verwendet werden kann, um eine Coroutine abzubrechen.

Nach `cancel`, fügen wir oft auch `join` hinzu, um auf das Beenden des Abbruchs zu warten, bevor wir fortfahren können. Ohne dies hätten wir einige Wettlaufsituationen. Im folgenden Beispiel ist zu sehen, dass, wenn wir `join` nicht verwenden, 'Drucken 4' nach 'Erfolgreich abgebrochen' ausgegeben wird.

{crop-start: 5, crop-end: 23}
```kotlin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//sampleStart
suspend fun main() = coroutineScope {
    val job = launch {
        repeat(1_000) { i ->
            delay(100)
            Thread.sleep(100) // We simulate long operation
            println("Printing $i")
        }
    }

    delay(1000)
    job.cancel()
    println("Cancelled successfully")
}
// Printing 0
// Printing 1
// Printing 2
// Printing 3
// Cancelled successfully
// Printing 4
//sampleEnd
```


Das Hinzufügen von `job.join()` würde dies ändern, weil es unterbricht, bis eine Coroutine ihren Abbruch abgeschlossen hat.

{crop-start: 5, crop-end: 24}
```kotlin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//sampleStart
suspend fun main() = coroutineScope {
    val job = launch {
        repeat(1_000) { i ->
            delay(100)
            Thread.sleep(100) // We simulate long operation
            println("Printing $i")
        }
    }

    delay(1000)
    job.cancel()
    job.join()
    println("Cancelled successfully")
}
// Printing 0
// Printing 1
// Printing 2
// Printing 3
// Printing 4
// Cancelled successfully
//sampleEnd
```


Um die Aufrufe von `cancel` und `join` zu erleichtern, bietet die kotlinx.coroutines-Bibliothek eine nützliche Erweiterungsfunktion mit einem selbsterklärenden Namen, `cancelAndJoin`.


```kotlin
// The most explicit function name I've ever seen
public suspend fun Job.cancelAndJoin() {
  cancel()
  return join()
}
```


Ein mit der `Job()` Factory-Funktion erstellter Job kann auf die gleiche Weise abgebrochen werden. Dies wird oft verwendet, um viele coroutines auf einmal zu stornieren.

{crop-start: 3, crop-end: 20}
```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val job = Job()
    launch(job) {
        repeat(1_000) { i ->
            delay(200)
            println("Printing $i")
        }
    }
    delay(1100)
    job.cancelAndJoin()
    println("Cancelled successfully")
}
// Printing 0
// Printing 1
// Printing 2
// Printing 3
// Printing 4
// Cancelled successfully
//sampleEnd
```

Dies ist eine entscheidende Funktion. Auf vielen Plattformen wird es oft notwendig, eine Gruppe von parallelen Aufgaben abzubrechen. Beispielsweise auf Android, werden alle gestarteten Coroutinen in einer Ansicht abgebrochen, wenn diese Ansicht vom Benutzer verlassen wird.

```kotlin
class ProfileViewModel : ViewModel() {
    private val scope =
        CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun onCreate() {
        scope.launch { loadUserData() }
    }

    override fun onCleared() {
        scope.coroutineContext.cancelChildren()
    }

    // ...
}
```


### Wie funktioniert der Abbruch?

Wenn eine Aufgabe abgebrochen wird, ändert sie ihren Zustand zu "Abbrechen". Dann wird an der ersten Unterbrechungsstelle eine `CancellationException` ausgelöst. Diese Ausnahme kann mit einem try-catch abgefangen werden, es wird jedoch empfohlen, sie weiterzuleiten.

{crop-start: 3, crop-end: 27}
```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val job = Job()
    launch(job) {
        try {
            repeat(1_000) { i ->
                delay(200)
                println("Printing $i")
            }
        } catch (e: CancellationException) {
            println(e)
            throw e
        }
    }
    delay(1100)
    job.cancelAndJoin()
    println("Cancelled successfully")
    delay(1000)
}
// Printing 0
// Printing 1
// Printing 2
// Printing 3
// Printing 4
// JobCancellationException...
// Cancelled successfully
//sampleEnd
```


Behalten Sie im Kopf, dass eine abgebrochene Koroutine nicht einfach gestoppt wird: Sie wird intern mit einer Exception abgebrochen. Daher können wir alles im `finally`-Block ohne Probleme aufräumen. Beispielsweise können wir einen `finally`-Block verwenden, um eine Datei oder eine Datenbankverbindung zu schließen. Da die meisten Mechanismen, die Ressourcen schließen, auf den `finally`-Block angewiesen sind (beispielsweise, wenn wir eine Datei mit `useLines` lesen), müssen wir uns einfach keine Sorgen darum machen.

{crop-start: 4, crop-end: 20}
```kotlin
import kotlinx.coroutines.*
import kotlin.random.Random

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val job = Job()
    launch(job) {
        try {
            delay(Random.nextLong(2000))
            println("Done")
        } finally {
            print("Will always be printed")
        }
    }
    delay(1000)
    job.cancelAndJoin()
}
// Will always be printed
// (or)
// Done
// Will always be printed
//sampleEnd
```

### Noch ein Anruf

Da wir eine `CancellationException` abfangen und noch weitere Operationen durchführen können, bevor die Coroutine endgültig beendet ist, fragen Sie sich vielleicht, wo das Limit liegt. Die Coroutine kann laufen, so lange sie benötigt, um alle Ressourcen freizugeben. Allerdings ist das Suspendieren nicht mehr erlaubt. Der `Job` befindet sich bereits im "Cancelling" Zustand, in dem weder das Suspendieren noch das Starten einer weiteren Coroutine möglich ist. Wenn wir versuchen, eine andere Coroutine zu starten, wird diese einfach ignoriert. Wenn wir versuchen zu suspendieren, wird eine `CancellationException` ausgelöst.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.*
import kotlin.random.Random

suspend fun main(): Unit = coroutineScope {
    val job = Job()
    launch(job) {
        try {
            delay(2000)
            println("Job is done")
        } finally {
            println("Finally")
            launch { // will be ignored
                println("Will not be printed")
            }
            delay(1000) // here exception is thrown
            println("Will not be printed")
        }
    }
    delay(1000)
    job.cancelAndJoin()
    println("Cancel done")
}
// (1 sec)
// Finally
// Cancel done
```


Manchmal müssen wir wirklich einen suspendierenden Aufruf verwenden, wenn eine Coroutine bereits storniert wurde. Beispielsweise müssen wir möglicherweise Änderungen in einer Datenbank rückgängig machen. In diesem Fall ist es bevorzugt, diesen Aufruf mit der Funktion `withContext(NonCancellable)` zu umschließen. Wir werden später im Detail erklären, wie `withContext` funktioniert. Jetzt müssen wir nur wissen, dass es den Kontext eines Codeblocks ändert. Innerhalb `withContext`, haben wir das `NonCancellable` Objekt verwendet, welches ein `Job` ist, der nicht storniert werden kann. So ist der Job innerhalb des Blocks im aktiven Zustand, und wir können die suspendierenden Funktionen aufrufen, die wir möchten.

{crop-start: 4, crop-end: 24}
```kotlin
import kotlinx.coroutines.*
import kotlin.random.Random

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val job = Job()
    launch(job) {
        try {
            delay(200)
            println("Coroutine finished")
        } finally {
            println("Finally")
            withContext(NonCancellable) {
                delay(1000L)
                println("Cleanup done")
            }
        }
    }
    delay(100)
    job.cancelAndJoin()
    println("Done")
}
// Finally
// Cleanup done
// Done
//sampleEnd
```


### invokeOnCompletion

Ein weiterer Mechanismus, der häufig genutzt wird, um Ressourcen freizugeben, ist die `invokeOnCompletion` Funktion von `Job`. Sie dient dazu, einen Handler zu bestimmen, der aufgerufen wird, wenn der Job einen finalen Zustand erreicht, also entweder "Abgeschlossen" oder "Abgebrochen".

{crop-start: 3, crop-end: 13}
```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val job = launch {
        delay(1000)
    }
    job.invokeOnCompletion { exception: Throwable? ->
        println("Finished")
    }
    delay(400)
    job.cancelAndJoin()
}
// Finished
//sampleEnd
```


Einer der Parameter dieses Handlers ist eine Ausnahme:
* `null`, wenn der Job ohne Ausnahme abgeschlossen wurde;
* `CancellationException`, wenn die Coroutine abgebrochen wurde;
* die Ausnahme, die eine Coroutine beendet hat (mehr dazu im nächsten Kapitel).

Wenn ein Job wurde abgeschlossen, bevor `invokeOnCompletion` aufgerufen wurde, wird der Handler unmittelbar aufgerufen. Die Parameter `onCancelling`[^204_3] und `invokeImmediately`[^204_4] erlauben zusätzliche Anpassungen.

{crop-start: 4, crop-end: 23}

```kotlin
import kotlinx.coroutines.*
import kotlin.random.Random

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val job = launch {
        delay(Random.nextLong(2400))
        println("Finished")
    }
    delay(800)
    job.invokeOnCompletion { exception: Throwable? ->
        println("Will always be printed")
        println("The exception was: $exception")
    }
    delay(800)
    job.cancelAndJoin()
}
// Will always be printed
// The exception was:
// kotlinx.coroutines.JobCancellationException
// (or)
// Finished
// Will always be printed
// The exception was null
//sampleEnd
```

`invokeOnCompletion` wird synchron während des Abbruchs aufgerufen, und wir haben keine Kontrolle über den Prozess, in dem es ausgeführt wird.

### Das Unstoppable stoppen

Da der Abbruch an den Unterbrechungsstellen auftritt, wird er nicht passieren, wenn es keine Unterbrechungsstelle gibt. Um eine solche Situation zu simulieren, könnten wir `Thread.sleep` anstelle von `delay` verwenden. Dies ist eine schlechte Praxis, also bitte tun Sie dies nicht in realen Projekten. Wir versuchen lediglich, einen Fall zu simulieren, in dem wir unsere coroutines intensiv nutzen, sie aber nicht unterbrechen. In der Praxis könnte eine solche Situation auftreten, wenn wir einige komplexe Berechnungen durchführen, wie das Lernen von neuronalen Netzwerken (ja, wir verwenden auch coroutines für solche Fälle, um die Parallelverarbeitung zu vereinfachen), oder wenn wir einige blockierende Aufrufe durchführen müssen (zum Beispiel das Lesen von Dateien).

Das folgende Beispiel stellt eine Situation dar, in der eine coroutine nicht abgebrochen werden kann, weil es in ihr keine Unterbrechungsstelle gibt (wir verwenden `Thread.sleep` anstelle von `delay`). Die Ausführung dauert über 3 Minuten, obwohl sie eigentlich nach 1 Sekunde abgebrochen werden sollte.

{crop-start: 3, crop-end: 20}
```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val job = Job()
    launch(job) {
        repeat(1_000) { i ->
            Thread.sleep(200) // We might have some
            // complex operations or reading files here
            println("Printing $i")
        }
    }
    delay(1000)
    job.cancelAndJoin()
    println("Cancelled successfully")
    delay(1000)
}
// Printing 0
// Printing 1
// Printing 2
// ... (up to 1000)
//sampleEnd
```

Es gibt einige Möglichkeiten, mit solchen Situationen umzugehen. Die erste Möglichkeit besteht darin, von Zeit zu Zeit die Funktion `yield()` zu verwenden. Diese Funktion unterbricht eine Koroutine und setzt sie sofort wieder fort. Dies gibt die Gelegenheit, während der Unterbrechung (oder Wiederaufnahme) das Notwendige zu tun, einschließlich Abbruch (oder Änderung eines Threads durch einen Dispatcher).

{crop-start: 3, crop-end: 22}
```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val job = Job()
    launch(job) {
        repeat(1_000) { i ->
            Thread.sleep(200)
            yield()
            println("Printing $i")
        }
    }
    delay(1100)
    job.cancelAndJoin()
    println("Cancelled successfully")
    delay(1000)
}
// Printing 0
// Printing 1
// Printing 2
// Printing 3
// Printing 4
// Cancelled successfully
//sampleEnd
```


Es ist eine gute Praxis, `yield` in unterbrechenden Funktionen zu verwenden, zwischen Blöcken von CPU-intensiven oder zeitintensiven Operationen, die nicht unterbrochen werden. 


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


Eine andere Möglichkeit besteht darin, den Zustand des Jobs zu verfolgen. Innerhalb eines Coroutine-Builders bezieht sich `this` (der Empfänger) auf den Bereich dieses Builders. `CoroutineScope` hat einen Kontext, auf den wir mit der Eigenschaft `coroutineContext` verweisen können. So können wir auf den Coroutine-Job (`coroutineContext[Job]` oder `coroutineContext.job`) zugreifen und überprüfen, was sein aktueller Zustand ist. Da ein Job oft verwendet wird, um zu überprüfen, ob eine Coroutine aktiv ist, stellt die Kotlin Coroutines-Bibliothek eine Funktion zur Verfügung, um dies zu vereinfachen:


```kotlin
public val CoroutineScope.isActive: Boolean
  get() = coroutineContext[Job]?.isActive ?: true
```


Wir können die Eigenschaft `isActive` verwenden, um zu überprüfen, ob ein Job noch aktiv ist und die Berechnungen stoppen, wenn er inaktiv ist.

{crop-start: 3, crop-end: 21}
```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val job = Job()
    launch(job) {
        do {
            Thread.sleep(200)
            println("Printing")
        } while (isActive)
    }
    delay(1100)
    job.cancelAndJoin()
    println("Cancelled successfully")
}
// Printing
// Printing
// Printing
// Printing
// Printing
// Printing
// Cancelled successfully
//sampleEnd
```

Ein alternativer Ansatz wäre die Verwendung der Funktion `ensureActive()`, die eine `CancellationException` wirft, falls `Job` nicht im aktiven Zustand ist.

{crop-start: 3, crop-end: 21}

```kotlin
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val job = Job()
    launch(job) {
        repeat(1000) { num ->
            Thread.sleep(200)
            ensureActive()
            println("Printing $num")
        }
    }
    delay(1100)
    job.cancelAndJoin()
    println("Cancelled successfully")
}
// Printing 0
// Printing 1
// Printing 2
// Printing 3
// Printing 4
// Cancelled successfully
//sampleEnd
```

Die Funktionen `ensureActive()` und `yield()` scheinen ähnlich zu funktionieren, aber sie sind sehr unterschiedlich. Die Funktion `ensureActive()` muss auf einem `CoroutineScope` (oder `CoroutineContext`, oder `Job`) aufgerufen werden. Alles, was sie tut, ist eine Ausnahme zu werfen, wenn der Job nicht mehr aktiv ist. Sie ist leichter und sollte daher generell bevorzugt werden. Die Funktion `yield` ist eine reguläre Top-Level-Suspension-Funktion. Sie benötigt keinen Kontext, daher kann sie in regulären suspending Funktionen verwendet werden. Da sie sowohl den Vorgang der Suspendierung als auch den der Wiederaufnahme durchführt, könnten andere Effekte auftreten, wie z.B. Thread-Wechsel, wenn wir einen Dispatcher mit einem Pool von Threads verwenden (mehr dazu im Kapitel [*Dispatchers*](https://kt.academy/article/cc-dispatchers)). `yield` wird häufiger nur in suspending Funktionen verwendet, die CPU-intensiv sind oder Threads blockieren.

### `suspendCancellableCoroutine`

Hier könnten Sie sich vielleicht an die Funktion `suspendCancellableCoroutine` erinnern, die im Kapitel [*Wie funktioniert die Suspendierung?*](https://kt.academy/article/cc-suspension) vorgestellt wurde. Sie verhält sich wie `suspendCoroutine`, aber ihre Fortsetzung ist in `CancellableContinuation<T>` eingewickelt, das einige zusätzliche Methoden bietet. Die wichtigste davon ist `invokeOnCancellation`, die wir verwenden, um zu definieren, was passieren soll, wenn eine Coroutine abgebrochen wird. Meistens verwenden wir sie, um Prozesse in einer Code-Bibliothek abzubrechen oder um einige Ressourcen freizugeben.

```kotlin
suspend fun someTask() = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation {
        // do cleanup
    }
    // rest of the implementation
}
```


Hier ist ein vollständiges Beispiel, in dem wir einen [Retrofit](https://square.github.io/retrofit/) `Call` mit einer suspendierenden Funktion umschließen.


```kotlin
suspend fun getOrganizationRepos(
    organization: String
): List<Repo> =
    suspendCancellableCoroutine { continuation ->
        val orgReposCall = apiService
            .getOrganizationRepos(organization)
        orgReposCall.enqueue(object : Callback<List<Repo>> {
            override fun onResponse(
                call: Call<List<Repo>>,
                response: Response<List<Repo>>
            ) {
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        continuation.resume(body)
                    } else {
                        continuation.resumeWithException(
                            ResponseWithEmptyBody
                        )
                    }
                } else {
                    continuation.resumeWithException(
                        ApiException(
                            response.code(),
                            response.message()
                        )
                    )
                }
            }

            override fun onFailure(
                call: Call<List<Repo>>,
                t: Throwable
            ) {
                continuation.resumeWithException(t)
            }
        })
        continuation.invokeOnCancellation {
            orgReposCall.cancel()
        }
    }
```


Es ist so großartig, dass Retrofit jetzt 'suspending functions' unterstützt!


```kotlin
class GithubApi {
  @GET("orgs/{organization}/repos?per_page=100")
  suspend fun getOrganizationRepos(
      @Path("organization") organization: String
  ): List<Repo>
}

```


Die `CancellableContinuation<T>` erlaubt uns auch, den Status des Jobs zu überprüfen (mit den Eigenschaften `isActive`, `isCompleted` und `isCancelled`) und diese Fortsetzung mit einer optionalen Begründung für die Stornierung zu beenden.

### Zusammenfassung

Die Möglichkeit zur Stornierung ist eine mächtige Eigenschaft. Im Allgemeinen ist sie einfach zu benutzen, kann aber manchmal komplex sein. Daher ist es wichtig zu verstehen, wie sie funktioniert.

Eine korrekte Verwendung der Stornierung bedeutet weniger verschwendete Ressourcen und weniger Speicherlecks. Sie ist für die Performance unserer Anwendung wichtig und es ist zu hoffen, dass diese Vorteile von nun an genutzt werden.

[^204_1]: Ein gutes Beispiel hierfür ist der `CoroutineWorker` auf Android, laut einer Präsentation [*Verstehen von Kotlin Coroutines auf Android* auf Google I/O'19](https://www.youtube.com/watch?v=BOHK_w09pVA) von Sean McQuillan und Yigit Boyar (beide arbeiten bei Google an Android), wurde die Unterstützung für Coroutinen hauptsächlich zur Nutzung des Stornierungsmechanismus hinzugefügt.
[^204_2]: Tatsächlich ist es viel mehr wert, da der Code momentan nicht sehr umfangreich ist (früher war er das, als er auf Lochkarten gespeichert wurde).
[^204_3]: Falls dies zutrifft, wird die Funktion im Zustand "Cancelling" aufgerufen (das heißt, vor "Cancelled"). Standardmäßig ist es `false`.
[^204_4]: Dieser Parameter bestimmt, ob der Handler sofort aufgerufen wird, falls der Handler gesetzt ist und die Coroutine bereits im gewünschten Zustand ist. Standardmäßig ist dies `true`.

