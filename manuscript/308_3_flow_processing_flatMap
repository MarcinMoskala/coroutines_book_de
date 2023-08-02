
### `flatMapConcat`, `flatMapMerge` und `flatMapLatest`

Eine andere bekannte Funktion für Sammlungen ist `flatMap`. Im Fall von Sammlungen ähnelt sie einer map, aber die Transformationsfunktion muss eine Sammlung zurückgeben, die dann geglättet wird. Zum Beispiel, wenn Sie eine Liste von Abteilungen haben, von denen jede eine Liste von Mitarbeitern hat, können Sie `flatMap` verwenden, um eine Liste aller Mitarbeiter in allen Abteilungen zu erstellen.


```kotlin
val allEmployees: List<Employee> = departments
    .flatMap { department -> department.employees }

// If we had used map, we would have a list of lists instead
val listOfListsOfEmployee: List<List<Employee>> = departments
    .map { department -> department.employees }
```


Wie sollte `flatMap` auf einem Flow aussehen? Es scheint intuitiv, dass wir erwarten könnten, dass seine Transformationsfunktion einen Flow zurückgibt, der dann geflacht werden sollte. Das Problem ist, dass Flow-Elemente sich über die Zeit verteilen können. Sollte also der vom zweiten Element generierte Flow auf den vom ersten generierten warten, oder sollte der Flow sie gleichzeitig verarbeiten? Da es keine klare Antwort gibt, gibt es keine `flatMap` Funktion für `Flow`, stattdessen gibt es `flatMapConcat`, `flatMapMerge` und `flatMapLatest`.

Die Funktion `flatMapConcat` verarbeitet die generierten Flows nacheinander. So kann der zweite Flow beginnen, wenn der erste abgeschlossen ist. Im folgenden Beispiel erstellen wir einen Flow aus den Zeichen "A", "B", und "C". Der von jedem von ihnen generierte Flow enthält diese Zeichen und die Zahlen 1, 2 und 3, mit einem Intervall von einer Sekunde.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

fun flowFrom(elem: String) = flowOf(1, 2, 3)
    .onEach { delay(1000) }
    .map { "${it}_${elem} " }

suspend fun main() {
    flowOf("A", "B", "C")
        .flatMapConcat { flowFrom(it) }
        .collect { println(it) }
}
// (1 sec)
// 1_A
// (1 sec)
// 2_A
// (1 sec)
// 3_A
// (1 sec)
// 1_B
// (1 sec)
// 2_B
// (1 sec)
// 3_B
// (1 sec)
// 1_C
// (1 sec)
// 2_C
// (1 sec)
// 3_C
```


{width: 100%}
![](flatMapConcat.png)

Die zweitgenannte Funktion, `flatMapMerge`, ist für mich am intuitivsten. Sie verarbeitet erzeugte Flows gleichzeitig.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

fun flowFrom(elem: String) = flowOf(1, 2, 3)
    .onEach { delay(1000) }
    .map { "${it}_${elem} " }

suspend fun main() {
    flowOf("A", "B", "C")
        .flatMapMerge { flowFrom(it) }
        .collect { println(it) }
}
// (1 sec)
// 1_A
// 1_B
// 1_C
// (1 sec)
// 2_A
// 2_B
// 2_C
// (1 sec)
// 3_A
// 3_B
// 3_C
```


{width: 100%}
![](flatMapMerge.png)

Die Anzahl der Datenströme, die gleichzeitig verarbeitet werden können, kann über den `concurrency` Parameter eingestellt werden. Der Standardwert dieses Parameters ist 16, kann aber in der JVM über die Eigenschaft `DEFAULT_CONCURRENCY_PROPERTY_NAME` geändert werden. Seien Sie sich dieser Standardbegrenzung bewusst, denn wenn Sie `flatMapMerge` auf einem Datenstrom mit vielen Elementen verwenden, werden nur 16 gleichzeitig verarbeitet.

{crop-start: 7}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

fun flowFrom(elem: String) = flowOf(1, 2, 3)
    .onEach { delay(1000) }
    .map { "${it}_${elem} " }

suspend fun main() {
    flowOf("A", "B", "C")
        .flatMapMerge(concurrency = 2) { flowFrom(it) }
        .collect { println(it) }
}
// (1 sec)
// 1_A
// 1_B
// (1 sec)
// 2_A
// 2_B
// (1 sec)
// 3_A
// 3_B
// (1 sec)
// 1_C
// (1 sec)
// 2_C
// (1 sec)
// 3_C
```


Die typische Verwendung von `flatMapMerge` ist, wenn wir Daten für jedes Element in einem Flow anfordern müssen. Zum Beispiel, wenn wir eine Liste von Kategorien haben und für jede von ihnen Angebote anfordern müssen. Du weißt bereits, dass du dies mit der `async` Funktion tun kannst. Es gibt zwei Vorteile, einen Flow mit `flatMapMerge` zu verwenden:
- wir können den Concurrency-Parameter steuern und entscheiden, wie viele Kategorien wir gleichzeitig holen möchten (um zu vermeiden, dass Hunderte von Anfragen gleichzeitig gesendet werden);
- wir können `Flow` zurückgeben und die nächsten Elemente senden, sobald sie eintreffen (also können sie direkt von der Funktion aus verarbeitet werden).


```kotlin
suspend fun getOffers(
    categories: List<Category>
): List<Offer> = coroutineScope {
    categories
        .map { async { api.requestOffers(it) } }
        .flatMap { it.await() }
}

// A better solution
suspend fun getOffers(
    categories: List<Category>
): Flow<Offer> = categories
    .asFlow()
    .flatMapMerge(concurrency = 20) {
        suspend { api.requestOffers(it) }.asFlow()
        // or flow { emit(api.requestOffers(it)) }
    }
```


Die letzte Funktion ist `flatMapLatest`. Sie verliert den vorherigen Prozess aus den Augen, sobald ein neuer beginnt. Bei jeder neuen Eingabe wird die Verarbeitung des vorherigen Prozesses vernachlässigt. Wenn also zwischen "A", "B" und "C" keine Verzögerung besteht, sehen Sie nur "1_C", "2_C", und "3_C".

{crop-start: 3}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

fun flowFrom(elem: String) = flowOf(1, 2, 3)
    .onEach { delay(1000) }
    .map { "${it}_${elem} " }

suspend fun main() {
    flowOf("A", "B", "C")
        .flatMapLatest { flowFrom(it) }
        .collect { println(it) }
}
// (1 sec)
// 1_C
// (1 sec)
// 2_C
// (1 sec)
// 3_C
```


{width: 100%}
![](flatMapLatest1.png)

Es wird interessanter, wenn die Elemente aus dem anfänglichen Fluss verzögert werden. Was im untenstehenden Beispiel passiert, ist, dass (nach 1,2 Sekunden) "A" seinen Fluss startet, der durch `flowFrom` erstellt wurde. Dieser Fluss erzeugt in 1 Sekunde ein Element "1_A", aber 200 Millisekunden später erscheint "B" und der vorherige Fluss wird geschlossen und vergessen. Der "B"-Fluss schafft es, "1_B" zu produzieren, als "C" erscheint und anfängt, seinen Fluss zu erzeugen. Dieser wird schließlich die Elemente "1_C", "2_C" und "3_C" produzieren, jeweils mit einer Verzögerung von 1 Sekunde.

{crop-start: 7}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

fun flowFrom(elem: String) = flowOf(1, 2, 3)
    .onEach { delay(1000) }
    .map { "${it}_${elem} " }

suspend fun main() {
    flowOf("A", "B", "C")
        .onEach { delay(1200) }
        .flatMapLatest { flowFrom(it) }
        .collect { println(it) }
}
// (2.2 sec)
// 1_A
// (1.2 sec)
// 1_B
// (1.2 sec)
// 1_C
// (1 sec)
// 2_C
// (1 sec)
// 3_C
```

{width: 100%}
![](flatMapLatest2.png)


