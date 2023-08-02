

### `fold` und `scan`

Wenn Sie mit Sammlungsverarbeitungsfunktionen arbeiten, werden Sie `fold` wahrscheinlich kennen. Es dient dazu, alle Werte in einer Sammlung durch eine Operation, die zwei Werte zu einem vereint, zu kombinieren (beginnend mit einem Anfangswert).

Als Beispiel nehmen wir an, der Anfangswert ist `0` und die Operation ist die Addition. Dann ist das Ergebnis die Summe aller Zahlen: Wir starten mit dem Anfangswert `0`, addieren darauf das erste Element `1`, darauf die zweite Zahl `2`, zur Summe davon kommen `3` und schließlich `4`. Das Ergebnis dieser Reihe von Operationen ist `10`, das von `fold` zurückgegeben wird.

```kotlin
fun main() {
    val list = listOf(1, 2, 3, 4)
    val res = list.fold(0) { acc, i -> acc + i }
    println(res) // 10
    val res2 = list.fold(1) { acc, i -> acc * i }
    println(res2) // 24
}
```


{width: 70%}
![](list_fold.png)

`fold` ist eine Endoperation. Sie kann auch für Flow verwendet werden, wird jedoch pausiert, bis dieser Flow abgeschlossen ist (genau wie `collect`).

{crop-start: 3}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

suspend fun main() {
    val list = flowOf(1, 2, 3, 4)
        .onEach { delay(1000) }
    val res = list.fold(0) { acc, i -> acc + i }
    println(res)
}
// (4 sec)
// 10
```


Es gibt eine Alternative zu `fold`, die `scan` genannt wird. Es handelt sich um eine Zwischenoperation, die alle Zwischenwerte des Akkumulators erzeugt.


```kotlin
fun main() {
    val list = listOf(1, 2, 3, 4)
    val res = list.scan(0) { acc, i -> acc + i }
    println(res) // [0, 1, 3, 6, 10]
}
```


{width: 70%}
![](list_scan.png)

`scan` ist bei `Flow` nützlich, da es sofort einen neuen Wert erstellt, sobald es einen Wert vom vorherigen Schritt erhält.

{crop-start: 4}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.*

suspend fun main() {
    flowOf(1, 2, 3, 4)
        .onEach { delay(1000) }
        .scan(0) { acc, v -> acc + v }
        .collect { println(it) }
}
// 0
// (1 sec)
// 1
// (1 sec)
// 3
// (1 sec)
// 6
// (1 sec)
// 10
```

{width: 100%}
![](scan.png)

Wir können `scan` problemlos mit dem `flow`-Builder und `collect` implementieren. Zuerst geben wir die Anfangswerte aus, dann geben wir bei jedem neuen Element das Ergebnis der nächsten Wertansammlung aus.

```kotlin
fun <T, R> Flow<T>.scan(
    initial: R,
    operation: suspend (accumulator: R, value: T) -> R
): Flow<R> = flow {
    var accumulator: R = initial
    emit(accumulator)
    collect { value ->
        accumulator = operation(accumulator, value)
        emit(accumulator)
    }
}
```

Der typische Anwendungsfall für `scan` ist, wenn wir einen Fluss von Updates oder Änderungen haben und ein Objekt benötigen, das das Ergebnis dieser Änderungen ist.

```kotlin
val userStateFlow: Flow<User> = userChangesFlow
    .scan(user) { user, change -> user.withChange(change) }

val messagesListFlow: Flow<List<Message>> = messagesFlow
    .scan(messages) { acc, message -> acc + message }
```


