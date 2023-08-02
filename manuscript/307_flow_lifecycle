
## Funktionen des Flow-Lebenszyklus

Ein Flow kann man sich als eine Röhre vorstellen, durch die Anfragen nach nächsten Werten in eine Richtung fließen und die entsprechend erzeugten Werte in die andere Richtung fließen. Wenn der Flow abgeschlossen ist oder eine Ausnahme auftritt, wird auch diese Information weitergegeben und sie schließt die Zwischenschritte auf dem Weg. Da sie also alle fließen, können wir auf Werte, Ausnahmen oder andere charakteristische Ereignisse (wie Starten oder Abschließen) aufmerksam sein. Um dies zu tun, verwenden wir Methoden wie `onEach`, `onStart`, `onCompletion`, `onEmpty` und `catch`. Lass uns diese mal einzeln durchgehen.

### onEach

Um auf jeden fließenden Wert zu reagieren, verwenden wir die Funktion `onEach`.

{crop-start: 2}
```kotlin
import kotlinx.coroutines.flow.*

suspend fun main() {
    flowOf(1, 2, 3, 4)
        .onEach { print(it) }
        .collect() // 1234
}
```

Der `onEach` Lambda-Ausdruck ist suspendierend und die Elemente werden nacheinander verarbeitet (sequentiell). Wenn wir also `delay` in `onEach` hinzufügen, werden wir jeden Wert aufschieben, wie er durchläuft.
{crop-start: 3}

```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

suspend fun main() {
    flowOf(1, 2)
        .onEach { delay(1000) }
        .collect { println(it) }
}
// (1 sec)
// 1
// (1 sec)
// 2
```


### onStart

Die `onStart` Funktion setzt einen Beobachter, der sofort aufgerufen werden sollte, sobald der Flow gestartet wird, also wenn die Terminaloperation aufgerufen wird. Es ist wichtig zu beachten, dass `onStart` nicht auf das erste Element wartet: es wird aufgerufen, wenn wir das erste Element anfordern.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

suspend fun main() {
    flowOf(1, 2)
        .onEach { delay(1000) }
        .onStart { println("Before") }
        .collect { println(it) }
}
// Before
// (1 sec)
// 1
// (1 sec)
// 2
```

Es ist gut zu wissen, dass wir in `onStart` (sowie auch in `onCompletion`, `onEmpty` und `catch`) Elemente ausgeben können. Solche Elemente werden von hier aus weitergeleitet.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

suspend fun main() {
    flowOf(1, 2)
        .onEach { delay(1000) }
        .onStart { emit(0) }
        .collect { println(it) }
}
// 0
// (1 sec)
// 1
// (1 sec)
// 2
```


### onCompletion

Es gibt einige Möglichkeiten, wie ein Flow abgeschlossen werden kann. Die häufigste ist, wenn der Flow-Builder abgeschlossen ist (d.h., das letzte Element wurde gesendet), obwohl dies auch im Falle einer nicht abgefangenen Exception oder eines Koroutinenabbruchs passiert. In all diesen Fällen können wir einen Listener für den Flow-Abschluss hinzufügen, indem wir die `onCompletion` Methode einsetzen.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*

suspend fun main() = coroutineScope {
    flowOf(1, 2)
        .onEach { delay(1000) }
        .onCompletion { println("Completed") }
        .collect { println(it) }
}
// (1 sec)
// 1
// (1 sec)
// 2
// Completed
```

{crop-start: 3}
```kotlin
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*

suspend fun main() = coroutineScope {
    val job = launch {
        flowOf(1, 2)
            .onEach { delay(1000) }
            .onCompletion { println("Completed") }
            .collect { println(it) }
    }
    delay(1100)
    job.cancel()
}
// (1 sec)
// 1
// (0.1 sec)
// Completed
```


In Android verwenden wir oft `onStart`, um einen Fortschrittsbalken anzuzeigen (der Indikator, dass wir eine Netzwerkantwort erwarten), und wir verwenden `onCompletion`, um ihn zu verbergen.


```kotlin
fun updateNews() {
    scope.launch {
        newsFlow()
            .onStart { showProgressBar() }
            .onCompletion { hideProgressBar() }
            .collect { view.showNews(it) }
    }
}
```

### onEmpty

Ein Flow könnte ohne die Ausgabe eines Wertes abgeschlossen werden, was auf ein unerwartetes Ereignis hindeuten könnte. Für solche Fälle gibt es die `onEmpty` Funktion, die die vorgegebene Aktion aktiviert, wenn dieser Flow ohne die Ausgabe von Elementen endet. `onEmpty` kann dann verwendet werden, um einen Standardwert auszugeben.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*

suspend fun main() = coroutineScope {
    flow<List<Int>> { delay(1000) }
        .onEmpty { emit(emptyList()) }
        .collect { println(it) }
}
// (1 sec)
// []
```


### catch

Zu jedem Zeitpunkt des Aufbaus oder der Verarbeitung des Flusses kann eine Ausnahme auftreten. Diese Ausnahme fließt nach unten, schließt jeden Verarbeitungsschritt auf dem Weg ab; jedoch kann sie gefangen und verwaltet werden. Um dies zu tun, können wir die `catch` Methode nutzen. Dieser Listener erhält die Ausnahme als Argument und ermöglicht Ihnen, Wiederherstellungsoperationen auszuführen.

{crop-start: 5}
```kotlin
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

class MyError : Throwable("My error")

val flow = flow {
    emit(1)
    emit(2)
    throw MyError()
}

suspend fun main(): Unit {
    flow.onEach { println("Got $it") }
        .catch { println("Caught $it") }
        .collect { println("Collected $it") }
}
// Got 1
// Collected 1
// Got 2
// Collected 2
// Caught MyError: My error
```


> Im obigen Beispiel siehst du, dass `onEach` nicht auf eine Ausnahme reagiert. Das gilt auch für andere Funktionen wie `map`, `filter` etc. Nur der `onCompletion`-Handler wird aufgerufen.

Die `catch`-Methode fängt eine Ausnahme ab und stoppt sie. Die vorherigen Schritte sind schon abgeschlossen, aber `catch` kann weiterhin neue Werte ausgeben und den Rest des Flows am Leben erhalten.

{crop-start: 4}
```kotlin
import kotlinx.coroutines.flow.*

class MyError : Throwable("My error")

val flow = flow {
    emit("Message1")
    throw MyError()
}

suspend fun main(): Unit {
    flow.catch { emit("Error") }
        .collect { println("Collected $it") }
}
// Collected Message1
// Collected Error
```

Das `catch` reagiert nur auf die Ausnahmen, die in der zuvor definierten Funktion ausgelöst werden (man kann sich vorstellen, dass die Ausnahme abgefangen werden muss, während sie herunterfließt).

{width: 100%}
![](flow_catch.png)

In Android verwenden wir oft `catch`, um Ausnahmen zu zeigen, die in einem Fluss aufgetreten sind.

```kotlin
fun updateNews() {
    scope.launch {
        newsFlow()
            .catch { view.handleError(it) }
            .onStart { showProgressBar() }
            .onCompletion { hideProgressBar() }
            .collect { view.showNews(it) }
    }
}
```

Wir könnten auch `catch` verwenden, um Standarddaten auf dem Bildschirm anzuzeigen, wie zum Beispiel eine leere Liste.

```kotlin
fun updateNews() {
    scope.launch {
        newsFlow()
            .catch {
                view.handleError(it)
                emit(emptyList())
            }
            .onStart { showProgressBar() }
            .onCompletion { hideProgressBar() }
            .collect { view.showNews(it) }
    }
}
```

### Nicht abgefangene Ausnahmen

Nicht abgefangene Ausnahmen in einem Datenfluss führen sofort zur Stornierung dieses Datenflusses, und `collect` wirft diese Ausnahme erneut aus. Dieses Verhalten ist typisch für suspendierende Funktionen, und `coroutineScope` verhält sich auf die gleiche Weise. Ausnahmen können außerhalb des Datenflusses mit dem klassischen Try-Catch-Block abgefangen werden.

{crop-start: 4}
```kotlin
import kotlinx.coroutines.flow.*

class MyError : Throwable("My error")

val flow = flow {
    emit("Message1")
    throw MyError()
}

suspend fun main(): Unit {
    try {
        flow.collect { println("Collected $it") }
    } catch (e: MyError) {
        println("Caught")
    }
}
// Collected Message1
// Caught
```


Beachte, dass die Verwendung von `catch` uns nicht vor einer exception in der abschließenden Operation schützt (weil `catch` nicht nach der letzten Operation platziert werden kann). Wenn also eine exception in `collect` auftritt, wird es nicht abgefangen und ein error wird ausgelöst.

{crop-start: 4}
```kotlin
import kotlinx.coroutines.flow.*

class MyError : Throwable("My error")

val flow = flow {
    emit("Message1")
    emit("Message2")
}

suspend fun main(): Unit {
    flow.onStart { println("Before") }
        .catch { println("Caught $it") }
        .collect { throw MyError() }
}
// Before
// Exception in thread "..." MyError: My error
```


Daher ist es gängige Praxis, die Operation von `collect` zu `onEach` zu verschieben und sie vor dem `catch` zu platzieren. Dies ist besonders hilfreich, falls wir befürchten, dass `collect` eine Ausnahme auslösen könnte. Wenn wir die Operation von `collect` verschieben, können wir uns sicher sein, dass `catch` alle Ausnahmen fangen wird.

{crop-start: 4}
```kotlin
import kotlinx.coroutines.flow.*

class MyError : Throwable("My error")

val flow = flow {
    emit("Message1")
    emit("Message2")
}

suspend fun main(): Unit {
    flow.onStart { println("Before") }
        .onEach { throw MyError() }
        .catch { println("Caught $it") }
        .collect()
}
// Before
// Caught MyError: My error
```


### flowOn

Lambda-Ausdrücke, die als Argumente für Flow-Operationen (wie `onEach`, `onStart`, `onCompletion` usw.) und deren Ersteller (wie `flow` oder `channelFlow`) verwendet werden, sind alle pausierend. Pausierende Funktionen müssen einen Kontext haben und sollten in Beziehung zu ihrer übergeordneten Funktion stehen (für strukturierte Parallelität). Man könnte sich fragen, wo diese Funktionen ihren Kontext hernehmen. Die Antwort ist: aus dem Kontext, in dem `collect` aufgerufen wird.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

fun usersFlow(): Flow<String> = flow {
    repeat(2) {
        val ctx = currentCoroutineContext()
        val name = ctx[CoroutineName]?.name
        emit("User$it in $name")
    }
}

suspend fun main() {
    val users = usersFlow()
    withContext(CoroutineName("Name1")) {
        users.collect { println(it) }
    }
    withContext(CoroutineName("Name2")) {
        users.collect { println(it) }
    }
}
// User0 in Name1
// User1 in Name1
// User0 in Name2
// User1 in Name2
```


Wie funktioniert dieser Code? Der Aufruf der Terminaloperation fordert Elemente von der vorhergehenden Stufe an und überträgt dabei den Coroutine-Kontext. Allerdings kann dies auch durch die Funktion `flowOn` verändert werden.

{crop-start: 5}
```kotlin
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

suspend fun present(place: String, message: String) {
    val ctx = coroutineContext
    val name = ctx[CoroutineName]?.name
    println("[$name] $message on $place")
}

fun messagesFlow(): Flow<String> = flow {
    present("flow builder", "Message")
    emit("Message")
}

suspend fun main() {
    val users = messagesFlow()
    withContext(CoroutineName("Name1")) {
        users
            .flowOn(CoroutineName("Name3"))
            .onEach { present("onEach", it) }
            .flowOn(CoroutineName("Name2"))
            .collect { present("collect", it) }
    }
}
// [Name3] Message on flow builder
// [Name2] Message on onEach
// [Name1] Message on collect
```

Denken Sie daran, dass `flowOn` nur für Funktionen funktioniert, die flussaufwärts im Flow sind.

{width: 100%}
![](flowOn_upstream.png)

### launchIn

`collect` ist eine unterbrechende Operation, die eine Coroutine unterbricht, bis der Flow abgeschlossen ist. Es ist üblich, es mit einem `launch` Builder zu verknüpfen, damit die Flow-Verarbeitung in einer anderen Coroutine beginnen kann. Um solche Szenarien zu unterstützen, gibt es die Funktion `launchIn`, welche `collect` in einer neuen Coroutine im Scope-Objekt, das als einziges Argument übergeben wird, startet.

```kotlin
fun <T> Flow<T>.launchIn(scope: CoroutineScope): Job =
    scope.launch { collect() }
```

`launchIn` wird oft verwendet, um die Flussverarbeitung in einer separaten Coroutine zu starten.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*

suspend fun main(): Unit = coroutineScope {
    flowOf("User1", "User2")
        .onStart { println("Users:") }
        .onEach { println(it) }
        .launchIn(this)
}
// Users:
// User1
// User2
```

### Zusammenfassung

In diesem Kapitel haben wir über verschiedene Flow-Funktionen gelernt. Jetzt wissen wir, wie man etwas ausführt, wenn unser Flow startet, wenn er schließt, oder für jedes Element; wir wissen auch, wie man Fehler abfängt und wie man einen Flow innerhalb einer neuen Coroutine startet. Diese sind typische Tools, die weit verbreitet sind, vor allem in der Android-Entwicklung. Zum Beispiel, so könnte ein Flow in Android implementiert werden:

```kotlin
fun updateNews() {
    newsFlow()
        .onStart { showProgressBar() }
        .onCompletion { hideProgressBar() }
        .onEach { view.showNews(it) }
        .catch { view.handleError(it) }
        .launchIn(viewModelScope)
}
```


