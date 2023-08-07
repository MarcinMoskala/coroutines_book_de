## `select`

Coroutinen bieten die `select` Funktion an, die es uns ermöglicht, auf das Ergebnis der ersten Coroutine, die abgeschlossen wird, zu warten. Sie bietet auch die Möglichkeit, an den ersten Kanal zu senden, der Platz im Puffer hat, oder vom ersten Kanal zu empfangen, der ein verfügbares Element hat. Dies ermöglicht es uns, Wettläufe zwischen Coroutinen zu machen oder Ergebnisse aus mehreren Quellen zusammenzuführen. Schauen wir uns das in der Praxis an.

> Die `select` Funktion ist immer noch experimentell, obwohl diese Funktion seit den frühen Kotlin Coroutinen-Versionen verfügbar ist. Es ist äußerst unwahrscheinlich, dass `select` entfernt wird, aber ihre API könnte sich noch ändern. Diese Funktion wird wahrscheinlich nie stabilisiert werden, weil es wenig Nachfrage danach gibt - sie wird tatsächlich eher selten verwendet, so habe ich mich entschieden, dieses Kapitel so kurz wie möglich zu halten.

### Auswählen von aufgeschobenen Werten

Nehmen wir an, wir möchten Daten von mehreren Quellen anfordern, sind aber nur an der schnellsten Antwort interessiert. Der einfachste Weg, dies zu erreichen, besteht darin, diese Anfragen in asynchronen Prozessen zu starten; dann verwenden Sie die `select` Funktion als Ausdruck und warten auf verschiedene Werte darin. Innerhalb von `select` können wir `onAwait` auf den `Deferred` Wert aufrufen, der ein mögliches Ergebnis des `select` Ausdrucks angibt. Innerhalb ihres Lambda-Ausdrucks können Sie den Wert transformieren. Im folgenden Beispiel geben wir einfach ein Async-Ergebnis zurück, so dass der `select` Ausdruck abgeschlossen wird, sobald die erste asynchrone Aufgabe abgeschlossen ist, und dann wird sein Ergebnis zurückgegeben.

{crop-start: 3}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

suspend fun requestData1(): String {
    delay(100_000)
    return "Data1"
}

suspend fun requestData2(): String {
    delay(1000)
    return "Data2"
}

val scope = CoroutineScope(SupervisorJob())

suspend fun askMultipleForData(): String {
    val defData1 = scope.async { requestData1() }
    val defData2 = scope.async { requestData2() }
    return select {
        defData1.onAwait { it }
        defData2.onAwait { it }
    }
}

suspend fun main(): Unit = coroutineScope {
    println(askMultipleForData())
}
// (1 sec)
// Data2
```

Beachten Sie, dass `async` in dem obigen Beispiel in einem übergeordneten Bereich gestartet werden muss. Das bedeutet, wenn Sie die Koroutine, die `askMultipleForData` gestartet hat, abbrechen, werden diese asynchronen Aufgaben nicht abgebrochen. Dies ist ein Problem, aber ich kenne keine bessere Implementierung. Hätten wir `coroutineScope` verwendet, hätte es auf seine untergeordneten Prozesse gewartet; für die untenstehende Implementierung erhalten wir also immer noch `Data2` als Ergebnis, aber erst nach 100 Sekunden anstatt nach einer.

```kotlin
// ...

suspend fun askMultipleForData(): String {
    val defData1 = scope.async { requestData1() }
    val defData2 = scope.async { requestData2() }
    return select<String> {
        defData1.onAwait { it }
        defData2.onAwait { it }
    }
}

suspend fun main(): Unit = coroutineScope {
    println(askMultipleForData())
}
// (100 sec)
// Data2
```


Ein Coroutine-Wettlauf kann gut mit `async` und `select` implementiert werden, aber es würde eine explizite Gültigkeitsbereich-Absage erfordern. Wir könnten diese anderen Coroutinen in dem `also` abbrechen, das aufgerufen wird, sobald `select` einen Wert erzeugt hat.


```kotlin
suspend fun askMultipleForData(): String = coroutineScope {
    select<String> {
        async { requestData1() }.onAwait { it }
        async { requestData2() }.onAwait { it }
    }.also { coroutineContext.cancelChildren() }
}

suspend fun main(): Unit = coroutineScope {
    println(askMultipleForData())
}
// (1 sec)
// Data2
```


Die obenstehende Lösung ist etwas komplex, weshalb viele Entwickler eine Hilfsfunktion erstellen oder eine externe Bibliothek (wie Splitties von Louis CAD) nutzen, die die folgende `raceOf` Funktion beinhaltet. Eine solche Funktion kann in ein paar Zeilen erstellt werden, wie ich Ihnen im Kapitel *Anleitungen* zeigen werde.


```kotlin
// Implementation using raceOf from Splitties library
suspend fun askMultipleForData(): String = raceOf({
    requestData1()
}, {
    requestData2()
})

suspend fun main(): Unit = coroutineScope {
    println(askMultipleForData())
}
// (1 sec)
// Data2
```


### Auswahl aus Kanälen

Die `select` Funktion kann auch mit Kanälen verwendet werden. Dies sind die Hauptfunktionen, die darin verwendet werden können:
* `onReceive` - wird ausgewählt, wenn dieser Kanal einen Wert hat. Sie empfängt diesen Wert (wie die `receive` Methode) und verwendet ihn als Argument für ihren Lambda-Ausdruck. Wenn `onReceive` ausgewählt ist, gibt `select` das Ergebnis ihres Lambda-Ausdrucks zurück.
* `onReceiveCatching` - wird ausgewählt, wenn dieser Kanal einen Wert hat oder geschlossen ist. Sie empfängt `ChannelResult`, das entweder einen Wert repräsentiert oder signalisiert, dass dieser Kanal geschlossen ist, und verwendet diesen Wert als Argument für ihren Lambda-Ausdruck. Wenn `onReceiveCatching` ausgewählt ist, gibt `select` das Ergebnis ihres Lambda-Ausdrucks zurück.
* `onSend` - wird ausgewählt, wenn dieser Kanal Platz im Puffer hat. Sie sendet einen Wert an diesen Kanal (wie die `send` Methode) und ruft ihren Lambda-Ausdruck mit einer Referenz auf den Kanal auf. Wenn `onSend` ausgewählt ist, gibt `select` `Unit` zurück.

Der Select-Ausdruck kann mit `onReceive` oder `onReceiveCatching` verwendet werden, um von mehreren Kanälen zu empfangen.

{crop-start: 4}
```kotlin
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

suspend fun CoroutineScope.produceString(
    s: String,
    time: Long
) = produce {
    while (true) {
        delay(time)
        send(s)
    }
}

fun main() = runBlocking {
    val fooChannel = produceString("foo", 210L)
    val barChannel = produceString("BAR", 500L)
    
    repeat(7) {
        select {
            fooChannel.onReceive {
                println("From fooChannel: $it")
            }
            barChannel.onReceive {
                println("From barChannel: $it")
            }
        }
    }
    
    coroutineContext.cancelChildren()
}
// From fooChannel: foo
// From fooChannel: foo
// From barChannel: BAR
// From fooChannel: foo
// From fooChannel: foo
// From barChannel: BAR
// From fooChannel: foo
```


Die select function kann zusammen mit `onSend` genutzt werden, um Daten an den ersten verfügbaren Kanal mit freiem Speicherplatz im Buffer zu senden.

{crop-start: 4}
```kotlin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.*

fun main(): Unit = runBlocking {
    val c1 = Channel<Char>(capacity = 2)
    val c2 = Channel<Char>(capacity = 2)
    
    // Send values
    launch {
        for (c in 'A'..'H') {
            delay(400)
            select<Unit> {
                c1.onSend(c) { println("Sent $c to 1") }
                c2.onSend(c) { println("Sent $c to 2") }
            }
        }
    }
    
    // Receive values
    launch {
        while (true) {
            delay(1000)
            val c = select<String> {
                c1.onReceive { "$it from 1" }
                c2.onReceive { "$it from 2" }
            }
            println("Received $c")
        }
    }
}
// Sent A to 1
// Sent B to 1
// Received A from 1
// Sent C to 1
// Sent D to 2
// Received B from 1
// Sent E to 1
// Sent F to 2
// Received C from 1
// Sent G to 1
// Received E from 1
// Sent H to 1
// Received G from 1
// Received H from 1
// Received D from 2
// Received F from 2
```

### Zusammenfassung

`select` ist eine nützliche Funktion, die es uns ermöglicht, auf das Ergebnis der ersten abgeschlossenen Coroutine zu warten, oder zu senden oder zu empfangen von dem ersten von mehreren Kanälen. Sie wird hauptsächlich verwendet, um verschiedene Muster der Arbeit mit Kanälen zu implementieren, sie kann aber auch verwendet werden, um `async` Coroutine-Rennen durchzuführen.

