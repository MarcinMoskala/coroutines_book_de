
### Endoperationen

Zum Schluss haben wir Operationen, die die Verarbeitung des Flusses beenden. Diese werden als Endoperationen bezeichnet. Bisher haben wir nur `collect` eingesetzt, aber es gibt auch andere, die ähnlich denen sind, die von Sammlungen und `Sequence` angeboten werden: `count` (zählt die Anzahl der Elemente im Fluss), `first` und `firstOrNull` (um das erste vom Fluss ausgegebene Element zu erhalten), `fold` und `reduce` (zum Anhäufen von Elementen in einem Objekt). Endoperationen sind aufgeschoben und sie liefern den Wert, sobald der Fluss vollständig ist (oder sie vollenden den Fluss selbst).

{crop-start: 2}
```kotlin
import kotlinx.coroutines.flow.*

suspend fun main() {
    val flow = flowOf(1, 2, 3, 4) // [1, 2, 3, 4]
        .map { it * it } // [1, 4, 9, 16]

    println(flow.first()) // 1
    println(flow.count()) // 4

    println(flow.reduce { acc, value -> acc * value }) // 576
    println(flow.fold(0) { acc, value -> acc + value }) // 30
}
```


Es gibt derzeit nicht viele mehr Endoperationen für Datenstrom, aber wenn Sie etwas anderes benötigen, können Sie es immer selbst implementieren. So könnten Sie zum Beispiel `sum` für einen Datenstrom von `Int` implementieren:


```kotlin
suspend fun Flow<Int>.sum(): Int {
    var sum = 0
    collect { value ->
        sum += value
    }
    return sum
}
```


Ähnlich können Sie fast jede Endoperation nur mit der Methode `collect` implementieren.

### Zusammenfassung

Es gibt viele Werkzeuge, die die Flow-Verarbeitung unterstützen. Es ist gut, über sie Bescheid zu wissen, da sie sowohl in der Backend- als auch in der Android-Entwicklung nützlich sind. Außerdem, wenn Sie einige unterschiedliche Funktionen benötigen, können sie dank der Methode `collect` und dem `flow`-Erzeuger ziemlich einfach implementiert werden.

