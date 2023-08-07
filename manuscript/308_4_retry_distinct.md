

### `retry`

Eine Ausnahme durchläuft einen Ablauf und schließt jeden Schritt ab, einer nach dem anderen. Diese Schritte werden inaktiv, es ist daher nicht möglich, nach einer Ausnahme Nachrichten zu senden, aber jeder Schritt gibt Ihnen eine Referenz auf die vorherigen. Sie können diese Referenz verwenden, um diesen Ablauf erneut zu starten. Auf dieser Idee basierend, bietet Kotlin die Funktionen `retry` und `retryWhen` an. Hier ist eine vereinfachte Implementierung von `retryWhen`:

```kotlin
// Simplified implementation of retryWhen
fun <T> Flow<T>.retryWhen(
    predicate: suspend FlowCollector<T>.(
        cause: Throwable,
        attempt: Long,
    ) -> Boolean,
): Flow<T> = flow {
        var attempt = 0L
        do {
            val shallRetry = try {
                collect { emit(it) }
                false
            } catch (e: Throwable) {
                predicate(e, attempt++)
                    .also { if (!it) throw e }
            }
        } while (shallRetry)
    }
```


Wie Sie sehen können, hat `retryWhen` ein Prädikat, das überprüft wird, wann immer eine Ausnahme aus den vorherigen Schritten des Ablaufs auftritt. Dieses Prädikat entscheidet, ob eine Ausnahme ignoriert werden und die vorherigen Schritte erneut gestartet werden sollen, oder ob der Ablauf weiterhin geschlossen bleiben soll. Dies ist eine universelle Retry-Funktion. In den meisten Fällen möchten wir angeben, dass wir eine spezifische Anzahl von Wiederholungen und/oder nur dann, wenn eine bestimmte Klasse von Ausnahmen auftritt (wie eine Netzwerkverbindungs-Ausnahme). Dafür gibt es eine andere Funktion namens `retry`, die `retryWhen` intern verwendet.


```kotlin
// Actual implementation of retry
fun <T> Flow<T>.retry(
    retries: Long = Long.MAX_VALUE,
    predicate: suspend (cause: Throwable) -> Boolean = {true}
): Flow<T> {
    require(retries > 0) {
      "Expected positive amount of retries, but had $retries"
    }
    return retryWhen { cause, attempt ->
        attempt < retries && predicate(cause)
    }
}
```

So funktioniert `retry`:

{crop-start: 3}
```kotlin
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.flow

suspend fun main() {
    flow {
        emit(1)
        emit(2)
        error("E")
        emit(3)
    }.retry(3) {
        print(it.message)
        true
    }.collect { print(it) } // 12E12E12E12(exception thrown)
}
```


Betrachten wir einige gängige Anwendungen dieser Funktionen. Ich sehe oft, dass Versuche immer wieder ausgeführt werden. Ein häufiger Ansatz für diese ist es, ein Prädikat zu definieren, um eine Protokollierung festzulegen und zwischen neuen Verbindungsversuchen eine Verzögerung einzuführen.


```kotlin
fun makeConnection(config: ConnectionConfig) = api
    .startConnection(config)
    .retry { e ->
        delay(1000)
        log.error(e) { "Error for $config" }
        true
    }
```


Es gibt eine andere beliebte Praxis, die darin besteht, die Verzögerung zwischen den nachfolgenden Verbindungsversuchen schrittweise zu erhöhen. Wir können auch ein Prädikat implementieren, das es erneut versucht, wenn eine Ausnahme eines bestimmten Typs vorliegt oder nicht.


```kotlin
fun makeConnection(config: ConnectionConfig) = api
    .startConnection(config)
    .retryWhen { e, attempt ->
        delay(100 * attempt)
        log.error(e) { "Error for $config" }
        e is ApiException && e.code !in 400..499
    }
```

### Unterschiedliche Funktionen

```kotlin
// Simplified distinctUntilChanged implementation
fun <T> Flow<T>.distinctUntilChanged(): Flow<T> = flow {
    var previous: Any? = NOT_SET
    collect {
        if (previous == NOT_SET || previous != it) {
            emit(it)
            previous = it
        }
    }
}

private val NOT_SET = Any()
```

Eine andere Funktion, die ich sehr nützlich finde, ist `distinctUntilChanged`, welche uns hilft, wiederholende Elemente zu eliminieren, die als identisch betrachtet werden. Beachten Sie, dass diese Funktion nur Elemente eliminiert, die mit dem entsprechenden **vorherigen Element** identisch sind.
{crop-start: 2}

```kotlin
import kotlinx.coroutines.flow.*

suspend fun main() {
    flowOf(1, 2, 2, 3, 2, 1, 1, 3)
        .distinctUntilChanged()
        .collect { print(it) } // 123213
}
```


Es gibt auch Varianten dieser Funktion. Die erste, `distinctUntilChangedBy`, gibt eine Schlüsselauswahl-Funktion an, die verglichen wird, um zu überprüfen, ob zwei Elemente gleich sind. Die zweite, `distinctUntilChanged` mit einer Lambda Expression, gibt an, wie zwei Elemente verglichen werden sollten (anstatt `equals`, das normalerweise verwendet wird).

{crop-start: 2}
```kotlin
import kotlinx.coroutines.flow.*

data class User(val id: Int, val name: String) {
    override fun toString(): String = "[$id] $name"
}

suspend fun main() {
    val users = flowOf(
        User(1, "Alex"),
        User(1, "Bob"),
        User(2, "Bob"),
        User(2, "Celine")
    )
    
    println(users.distinctUntilChangedBy { it.id }.toList())
    // [[1] Alex, [2] Bob]
    println(users.distinctUntilChangedBy{ it.name }.toList())
    // [[1] Alex, [1] Bob, [2] Celine]
    println(users.distinctUntilChanged { prev, next ->
        prev.id == next.id || prev.name == next.name
    }.toList()) // [[1] Alex, [2] Bob]
    // [2] Bob was emitted,
    // because we compare to the previous emitted
}
```
