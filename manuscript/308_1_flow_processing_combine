
### `merge`, `zip` und `combine`

Lassen Sie uns über die Kombination von zwei Datenströmen zu einem sprechen. Es gibt mehrere Möglichkeiten, dies zu erreichen. Die einfachste besteht darin, die Elemente aus zwei Datenströmen zu einem zu verschmelzen. Es werden keine Änderungen vorgenommen, egal aus welchem Datenstrom die Elemente kommen. Hierfür nutzen wir die globale `merge` Funktion.

{crop-start: 2}
```kotlin
import kotlinx.coroutines.flow.*

suspend fun main() {
    val ints: Flow<Int> = flowOf(1, 2, 3)
    val doubles: Flow<Double> = flowOf(0.1, 0.2, 0.3)

    val together: Flow<Number> = merge(ints, doubles)
    print(together.toList())
    // [1, 0.1, 0.2, 0.3, 2, 3]
    // or [1, 0.1, 0.2, 0.3, 2, 3]
    // or [0.1, 1, 2, 3, 0.2, 0.3]
    // or any other combination
}
```


{width: 100%}
![](merge.png)

Es ist wichtig zu wissen, dass die Elemente eines Flusses beim Verwenden von `merge` nicht auf einen anderen Fluss warten. Wie im untenstehenden Beispiel, obwohl die Elemente des ersten Flusses verzögert werden, hindert dies die Elemente des zweiten Flusses nicht.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

suspend fun main() {
    val ints: Flow<Int> = flowOf(1, 2, 3)
        .onEach { delay(1000) }
    val doubles: Flow<Double> = flowOf(0.1, 0.2, 0.3)

    val together: Flow<Number> = merge(ints, doubles)
    together.collect { println(it) }
}
// 0.1
// 0.2
// 0.3
// (1 sec)
// 1
// (1 sec)
// 2
// (1 sec)
// 3
```


Wir nutzen `merge`, wenn verschiedene Ereignisquellen dieselben Aktionen auslösen müssen.


```kotlin
fun listenForMessages() {
    merge(userSentMessages, messagesNotifications)
        .onEach { displayMessage(it) }
        .launchIn(scope)
}
```

Die nächste Funktion ist `zip`, die Paare aus beiden Flows bildet. Wir müssen auch eine Funktion angeben, die entscheidet, wie Elemente gepaart (in das umgewandelt werden, was im neuen Flow emittiert wird) werden. Jedes Element kann nur Teil eines Paares sein, daher muss es auf sein Paar warten. Elemente, die ohne Paar übrig bleiben, gehen verloren, daher ist der resultierende Flow, wenn das Zusammenfügen eines Flows abgeschlossen ist, ebenfalls vollständig (genau wie der andere Flow).

{crop-start: 3}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

suspend fun main() {
    val flow1 = flowOf("A", "B", "C")
        .onEach { delay(400) }
    val flow2 = flowOf(1, 2, 3, 4)
        .onEach { delay(1000) }
    flow1.zip(flow2) { f1, f2 -> "${f1}_${f2}" }
        .collect { println(it) }
}
// (1 sec)
// A_1
// (1 sec)
// B_2
// (1 sec)
// C_3
```

{width: 100%}
![](zip.png)

> Die `zip`-Funktion erinnert mich an die Polonaise - einen traditionellen polnischen Tanz. Ein Merkmal dieses Tanzes ist, dass eine Reihe von Paaren in der Mitte getrennt wird und sich erneut formt, wenn sie sich wieder treffen. 

{width: 100%}
![Eine Szene aus dem Film Pan Tadeusz, unter der Regie von Andrzej Wajda, präsentiert den Polonaise Tanz.](polonaise_dance.jpeg)

Die letzte wichtige Funktion beim Kombinieren zweier Flows ist `combine`. Genau wie `zip`, bildet es auch Paare aus Elementen, die auf den langsameren Flow warten müssen, um das erste Paar zu bilden. Die Ähnlichkeiten zur Polonaise enden jedoch hier. Bei der Verwendung von `combine` ersetzt jedes neue Element seinen Vorgänger. Hat sich das erste Paar bereits gebildet, bildet es mit dem vorherigen Element aus dem anderen Flow ein neues Paar.

{width: 100%}
![](combine.png)

Beachten Sie, dass `zip` Paare benötigt und schließt, sobald der erste Flow geschlossen ist. `combine` hat diese Einschränkung nicht und strahlt weiter aus, bis beide Flows geschlossen sind.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

suspend fun main() {
    val flow1 = flowOf("A", "B", "C")
        .onEach { delay(400) }
    val flow2 = flowOf(1, 2, 3, 4)
        .onEach { delay(1000) }
    flow1.combine(flow2) { f1, f2 -> "${f1}_${f2}" }
        .collect { println(it) }
}
// (1 sec)
// B_1
// (0.2 sec)
// C_1
// (0.8 sec)
// C_2
// (1 sec)
// C_3
// (1 sec)
// C_4
```


`combine` wird typischerweise verwendet, wenn wir zwei Quellen von Änderungen aktiv beobachten müssen. Wenn Sie möchten, dass Elemente ausgegeben werden, wann immer eine Änderung auftritt, können Sie Anfangswerte zu jedem kombinierten Datenstrom hinzufügen (um das Anfangsduo zu haben).


```kotlin
userUpdateFlow.onStart { emit(currentUser) }
```


Ein typischer Anwendungsfall könnte sein, wenn eine View entweder auf die Änderungen von zwei beobachtbaren Elementen reagieren muss. Zum Beispiel, wenn ein Benachrichtigungsabzeichen von sowohl dem aktuellen Zustand eines Benutzers als auch jeglichen Benachrichtigungen abhängig ist, könnten wir beide beobachten und ihre Änderungen kombinieren, um die View zu aktualisieren.


```kotlin
userStateFlow
    .combine(notificationsFlow) { userState, notifications ->
        updateNotificationBadge(userState, notifications)
    }
    .collect()
```


