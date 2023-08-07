
## Flow-Verarbeitung

Wir haben Flow als Rohr dargestellt, durch das Werte fließen. Dabei können sie auf verschiedene Weisen verändert werden: ausgelassen, vervielfacht, umgewandelt oder kombiniert. Diese Operationen zwischen der Erzeugung des Flows und der Terminaloperation werden *Flow-Verarbeitung* genannt. In diesem Kapitel werden wir die Funktionen kennenlernen, die wir dafür verwenden.

> Die hier vorgestellten Funktionen könnten Sie an die Funktionen erinnern, die wir für die Verarbeitung von Collections verwenden. Dies ist kein Zufall, da sie die gleichen Konzepte darstellen, mit dem Unterschied, dass Flow-Elemente zeitlich versetzt sein können.

### `map`

Die erste wichtige Funktion, die wir lernen müssen, ist `map`, welche jedes fließende Element gemäß seiner Transformationsfunktion transformiert. Wenn Sie also einen Flow von Zahlen haben und Ihre Operation das Berechnen der Quadrate dieser Zahlen ist, dann enthält der resultierende Flow die Quadrate dieser Zahlen.

{crop-start: 2}
```kotlin
import kotlinx.coroutines.flow.*

suspend fun main() {
    flowOf(1, 2, 3) // [1, 2, 3]
        .map { it * it } // [1, 4, 9]
        .collect { print(it) } // 149
}
```

{width: 100%}
![](map.png)

> Ich werde die oben dargestellten Diagramme nutzen, um zu veranschaulichen, wie Flow-Verarbeitungsfunktionen Elemente über die Zeit hinweg verändern. Die horizontale Linie stellt die Zeit dar und die Elemente auf dieser Linie sind diejenigen, die zu diesem Zeitpunkt im Flow ausgesendet werden. Die obere Linie repräsentiert einen Flow vor der durchgeführten Operation und die untere Linie einen Flow nach der Operation. Dieses Diagramm kann auch dazu genutzt werden, mehrere Operationen darzustellen, die nacheinander ausgeführt werden, wie `map` und `filter` im untenstehenden Diagramm.

{width: 100%}
![](map_filter.png)

Die meisten Flow-Verarbeitungsfunktionen lassen sich mit den Werkzeugen, die wir bereits aus früheren Kapiteln kennen, recht einfach implementieren. Zur Implementierung von `map` könnten wir den `flow`-Builder nutzen, um einen neuen Flow zu erstellen. Anschließend könnten wir Elemente aus dem vorherigen Flow sammeln und jedes gesammelte Element transformiert aussenden. Die unten dargestellte Implementierung ist nur eine etwas vereinfachte Version der tatsächlichen Implementierung aus der kotlinx.coroutines-Bibliothek.

```kotlin
fun <T, R> Flow<T>.map(
    transform: suspend (value: T) -> R
): Flow<R> = flow { // here we create a new flow
    collect { value -> // here we collect from receiver
        emit(transform(value))
    }
}
```


`map` ist eine sehr beliebte Funktion. Ihre Einsatzbereiche beinhalten das Entpacken oder Umwandeln von Daten in einen anderen Typ.


```kotlin
// Here we use map to have user actions from input events
fun actionsFlow(): Flow<UserAction> =
    observeInputEvents()
        .map { toAction(it.code) }

// Here we use map to convert from User to UserJson
fun getAllUser(): Flow<UserJson> =
    userRepository.getAllUsers()
        .map { it.toUserJson() }
```


### `filter`

Die nächste wichtige Funktion ist `filter`, die einen Flow zurückgibt, der nur Werte aus dem ursprünglichen Flow enthält, die das vorgegebene Prädikat erfüllen.

{crop-start: 2}
```kotlin
import kotlinx.coroutines.flow.*

suspend fun main() {
    (1..10).asFlow() // [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
        .filter { it <= 5 } // [1, 2, 3, 4, 5]
        .filter { isEven(it) } // [2, 4]
        .collect { print(it) } // 24
}

fun isEven(num: Int): Boolean = num % 2 == 0
```


{width: 100%}
![](filter.png)

Diese Funktion kann auch ziemlich leicht mit dem Flow Builder implementiert werden. Wir müssten nur eine if-Anweisung mit dem Prädikat einführen (anstelle von Transformation).


```kotlin
fun <T> Flow<T>.filter(
    predicate: suspend (T) -> Boolean
): Flow<T> = flow { // here we create a new flow
    collect { value -> // here we collect from receiver
        if (predicate(value)) {
            emit(value)
        }
    }
}
```


`filter` wird typischerweise verwendet, um Elemente zu eliminieren, an denen wir nicht interessiert sind. 


```kotlin
// Here we use filter to drop invalid actions
fun actionsFlow(): Flow<UserAction> =
    observeInputEvents()
        .filter { isValidAction(it.code) }
        .map { toAction(it.code) }
```


### `take` und `drop`

Wir verwenden `take`, um nur eine bestimmte Anzahl von Elementen weiterzugeben. `drop` wird verwendet, um eine bestimmte Anzahl von Elementen zu entfernen.

{crop-start: 2}
```kotlin
import kotlinx.coroutines.flow.*

suspend fun main() {
    ('A'..'Z').asFlow()
        .take(5) // [A, B, C, D, E]
        .collect { print(it) } // ABCDE
}
```


{width: 100%}
![](take.png)

Wir nutzen `drop`, um eine bestimmte Anzahl an Elementen zu übergehen.

{crop-start: 2}
```kotlin
import kotlinx.coroutines.flow.*

suspend fun main() {
    ('A'..'Z').asFlow()
        .drop(20) // [U, V, W, X, Y, Z]
        .collect { print(it) } // UVWXYZ
}
```

{width: 100%}
![](drop.png)

### Wie funktioniert die Verarbeitung von Collections?

Wir haben schon einige Funktionen zur Verarbeitung von Flows und Lifecycle-Funktionen gesehen. Ihre Implementierung ist recht einfach, also kann man annehmen, dass dort keine Magie im Spiel ist. Die meisten dieser Funktionen können mit dem `flow` Builder und `collect` mit einem Lambda implementiert werden. Hier ist ein einfaches Beispiel für die Verarbeitung von Flows und einige vereinfachte Implementierungen von `map` und `flowOf`:

```kotlin
suspend fun main() {
    flowOf('a', 'b')
        .map { it.uppercase() }
        .collect { print(it) } // AB
}

fun <T, R> Flow<T>.map(
    transform: suspend (value: T) -> R
): Flow<R> = flow {
    collect { value ->
        emit(transform(value))
    }
}

fun <T> flowOf(vararg elements: T): Flow<T> = flow {
    for (element in elements) {
        emit(element)
    }
}
```


Wenn Sie die `filter` und `map` Funktionen einbetten, erhalten Sie den folgenden Code (Ich habe Labels zu den Lambdas hinzugefügt und Kommentare mit Nummern hinzugefügt).


```kotlin
suspend fun main() {
    flow map@{ // 1
        flow flowOf@{ // 2
            for (element in arrayOf('a', 'b')) { // 3
                this@flowOf.emit(element) // 4
            }
        }.collect { value -> // 5
            this@map.emit(value.uppercase()) // 6
        }
    }.collect { // 7
        print(it) // 8
    }
}
```


Lassen wir dies Schritt für Schritt analysieren. Wir starten einen Flow an 1 und collect ihn an 7. Wenn wir anfangen zu collecten, rufen wir das Lambda @map auf (welches an 1 startet), das einen anderen Builder an 2 aufruft und ihn an 5 collected. Wenn wir collecten, starten wir Lambda @flowOn (welches an 2 startet). Dieses Lambda (an 2) iteriert über ein Array mit `'a'` und `'b'`. Der erste Wert `'a'` wird an 4 emittiert, was das Lambda an 5 aufruft. Dieses Lambda (an 5) transformiert den Wert zu `'A'` und emittiert ihn zum Flow @map, dadurch wird das Lambda an 7 aufgerufen. Der Wert wird ausgegeben; wir schließen dann das Lambda an 7 ab und nehmen das Lambda an 6 wieder auf. Es wird abgeschlossen, also nehmen wir @flowOf an 4 wieder auf. Wir setzen die Iteration fort und emittieren `'b'` an 4. Daher rufen wir das Lambda an 5 auf, transformieren den Wert zu `'B'`, und emittieren ihn an 6 zum Flow @map. Der Wert wird an 7 collected und ausgegeben an 8. Das Lambda an 7 ist abgeschlossen, also nehmen wir das Lambda an 6 wieder auf. Dies wird abgeschlossen, also nehmen wir das Lambda @flowOf an 4 wieder auf. Dies wird ebenfalls abgeschlossen, also nehmen wir das @map auf `collect` an 5 wieder auf. Da es nichts mehr gibt, schließen wir @map ab. Damit nehmen wir das `collect` an 7 wieder auf, und wir schließen die `main` Funktion ab.

Das Gleiche passiert in den meisten Flow-Verarbeitungs- und Lebenszyklusfunktionen, daher gibt uns das Verständnis davon ein ziemlich gutes Verständnis davon, wie Flow funktioniert.

