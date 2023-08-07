
## Channel

Die Channel API wurde als Kommunikationsmittel zwischen Coroutinen hinzugefügt. Viele stellen sich einen Kanal als Rohr vor, aber ich bevorzuge eine andere Metapher. Sind Sie mit öffentlichen Bücherschränken zum Austausch von Büchern vertraut? Jemand muss ein Buch hinterlassen, damit es eine andere Person finden kann. Dies ist sehr ähnlich, wie `Channel` von `kotlinx.coroutines` funktioniert.

{width: 60%}
![](book_exchange.png)

Channel unterstützt eine beliebige Anzahl von Sendern und Empfängern und jeder Wert, der an einen Kanal gesendet wird, wird nur einmal empfangen werden.

{width: 100%}
![](channel_pipe.png)

{width: 100%}
![](channel_mimo.png)

`Channel` ist eine Schnittstelle, die zwei andere Schnittstellen implementiert:
* `SendChannel`, welches zum Senden (Hinzufügen) von Elementen und zum Schließen des Kanals dient;
* `ReceiveChannel`, welches die Elemente empfängt (oder abnimmt).


```kotlin
interface SendChannel<in E> {
    suspend fun send(element: E)
    fun close(): Boolean
    //...
}

interface ReceiveChannel<out E> {
    suspend fun receive(): E
    fun cancel(cause: CancellationException? = null)
    // ...
}

interface Channel<E> : SendChannel<E>, ReceiveChannel<E>
```

Dank dieser Unterscheidung können wir nur `ReceiveChannel` oder `SendChannel` bereitstellen, um die Zugangspunkte des Kanals zu beschränken.

Sie könnten bemerken, dass sowohl `send` als auch `receive` suspendierende Funktionen sind. Dies ist eine wesentliche Eigenschaft:
* Wenn wir versuchen, `receive` zu verwenden und es gibt keine Elemente im Kanal, wird die Coroutine solange ausgesetzt, bis das Element verfügbar ist. Wie bei unserem metaphorischen Bücherregal, wenn jemand zum Regal geht, um ein Buch zu finden, das Bücherregal aber leer ist, muss diese Person aussetzen, bis jemand ein Element dort platziert.
* Andererseits wird `send` ausgesetzt, wenn der Kanal seine Kapazität erreicht. Wir werden bald sehen, dass die meisten Kanäle eine begrenzte Kapazität haben. Wie bei unserem metaphorischen Bücherregal, wenn jemand versucht, ein Buch auf ein Regal zu legen, aber es ist voll, muss diese Person aussetzen, bis jemand ein Buch nimmt und Platz schafft.

> Wenn Sie von einer nicht-suspendierenden Funktion senden oder empfangen müssen, können Sie `trySend` und `tryReceive` verwenden. Beide Operationen erfolgen sofort und geben `ChannelResult` zurück, das Informationen über den Erfolg oder Misserfolg der Operation sowie das Ergebnis enthält. Verwenden Sie `trySend` und `tryReceive` nur für Kanäle mit begrenzter Kapazität, da sie für den Rendezvous-Kanal nicht funktionieren werden.

Ein Kanal kann beliebig viele Sender und Empfänger haben. Die häufigste Situation ist jedoch, wenn es eine Coroutine auf beiden Seiten des Kanals gibt.

{width: 100%}
![](channel_1.png)

{width: 100%}
![](channel_2.png)

Um das einfachste Beispiel für einen Kanal zu sehen, benötigen wir einen Produzenten (Sender) und einen Konsumenten (Empfänger) in separaten Coroutinen. Der Produzent wird Elemente senden und der Konsument wird sie empfangen. So kann es implementiert werden:

{crop-start: 4, crop-end: 35}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val channel = Channel<Int>()
    launch {
        repeat(5) { index ->
            delay(1000)
            println("Producing next one")
            channel.send(index * 2)
        }
    }

    launch {
        repeat(5) {
            val received = channel.receive()
            println(received)
        }
    }
}
// (1 sec)
// Producing next one
// 0
// (1 sec)
// Producing next one
// 2
// (1 sec)
// Producing next one
// 4
// (1 sec)
// Producing next one
// 6
// (1 sec)
// Producing next one
// 8
//sampleEnd
```


Eine solche Implementierung ist weit von perfekt entfernt. Zuerst muss der Empfänger wissen, wie viele Elemente gesendet werden; dies ist jedoch selten der Fall, weshalb wir es vorziehen würden, so lange zu empfangen, wie der Sender bereit ist zu senden. Um Elemente auf dem Kanal zu empfangen, bis er geschlossen ist, könnten wir eine "for-loop" oder die Funktion `consumeEach`[^301_3] verwenden.

{crop-start: 4, crop-end: 24}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val channel = Channel<Int>()
    launch {
        repeat(5) { index ->
            println("Producing next one")
            delay(1000)
            channel.send(index * 2)
        }
        channel.close()
    }

    launch {
        for (element in channel) {
            println(element)
        }
        // or
        // channel.consumeEach { element ->
        //     println(element)
        // }
    }
}
//sampleEnd
```


Das häufige Problem mit dieser Art der Elementsendung besteht darin, dass man leicht vergisst, den Kanal zu schließen, besonders bei Ausnahmen. Wenn eine Coroutine aufgrund einer Ausnahme aufhört zu produzieren, wird die andere Coroutine ewig auf Elemente warten. Es ist praktischer, die `produce` Funktion zu nutzen, ein Coroutine-Builder, der `ReceiveChannel` liefert.


```kotlin
// This function produces a channel with
// next positive integers from 0 to `max`
fun CoroutineScope.produceNumbers(
   max: Int
): ReceiveChannel<Int> = produce {
       var x = 0
       while (x < 5) send(x++)
   }
```


Die `produce` Funktion schließt den Kanal immer, wenn die Erzeuger-Coroutine auf irgendeine Weise endet (fertig, gestoppt, abgebrochen). Dank dieser Funktion vergessen wir nie, `close` aufzurufen. Die `produce` Erzeugerfunktion ist ein sehr beliebter Weg, um einen Kanal zu erstellen, und das aus gutem Grund: sie bietet viel Sicherheit und Komfort.

{crop-start: 4, crop-end: 16}
```kotlin
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val channel = produce {
        repeat(5) { index ->
            println("Producing next one")
            delay(1000)
            send(index * 2)
        }
    }

    for (element in channel) {
        println(element)
    }
}
//sampleEnd
```


### Kanaltypen

Abhängig von der Kapazitätsgröße, die wir festlegen, unterscheiden wir vier Arten von Kanälen:
* **Unbegrenzt** - Kanal mit der Kapazität `Channel.UNLIMITED`, der einen unbegrenzten Puffer besitzt, und bei dem `senden` niemals aussetzt.
* **Gepuffert** - Kanal mit konkreter Kapazitätsgröße oder `Channel.BUFFERED` (was standardmäßig 64 ist und durch Setzen der Systemeigenschaft `kotlinx.coroutines.channels.defaultBuffer` in JVM überschrieben werden kann).
* **Rendezvous**[^301_1] (Standard) - Kanal mit Kapazität 0 oder `Channel.RENDEZVOUS` (was gleich `0` ist), was bedeutet, dass ein Austausch nur stattfinden kann, wenn Sender und Empfänger aufeinandertreffen (also es funktioniert wie eine direkte Buchübergabe, nicht wie ein Bücherregal).
* **Konflationskanal** - Kanal mit der Kapazität `Channel.CONFLATED`, der einen Puffer der Größe 1 hat, und bei dem jedes neue Element das vorherige ersetzt.

Schauen wir uns nun diese Kapazitäten in Aktion an. Wir können sie direkt auf `Channel` setzen, aber wir können sie auch festlegen, wenn wir die `produce` Funktion aufrufen.

Wir lassen unseren Produzenten schnell arbeiten und unseren Empfänger langsam. Bei unbegrenzter Kapazität sollte der Kanal alle Elemente akzeptieren und dann lassen sie nacheinander empfangen.

{crop-start: 5, crop-end: 40}
```kotlin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val channel = produce(capacity = Channel.UNLIMITED) {
        repeat(5) { index ->
            send(index * 2)
            delay(100)
            println("Sent")
        }
    }

    delay(1000)
    for (element in channel) {
        println(element)
        delay(1000)
    }
}

// Sent
// (0.1 sec)
// Sent
// (0.1 sec)
// Sent
// (0.1 sec)
// Sent
// (0.1 sec)
// Sent
// (1 - 4 * 0.1 = 0.6 sec)
// 0
// (1 sec)
// 2
// (1 sec)
// 4
// (1 sec)
// 6
// (1 sec)
// 8
// (1 sec)
//sampleEnd
```

Mit einer bestimmten Kapazität werden wir zuerst produzieren, bis der Puffer voll ist. Danach muss der Produzent auf den Empfänger warten.

{crop-start: 4, crop-end: 37}
```kotlin
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val channel = produce(capacity = 3) {
        repeat(5) { index ->
            send(index * 2)
            delay(100)
            println("Sent")
        }
    }

    delay(1000)
    for (element in channel) {
        println(element)
        delay(1000)
    }
}

// Sent
// (0.1 sec)
// Sent
// (0.1 sec)
// Sent
// (1 - 2 * 0.1 = 0.8 sec)
// 0
// Sent
// (1 sec)
// 2
// Sent
// (1 sec)
// 4
// (1 sec)
// 6
// (1 sec)
// 8
// (1 sec)
//sampleEnd
```


Mit einem Kanal der Voreinstellung (oder `Channel.RENDEZVOUS`) Kapazität, wird der Produzent stets auf einen Empfänger warten.

{crop-start: 4, crop-end: 35}
```kotlin
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val channel = produce {
        // or produce(capacity = Channel.RENDEZVOUS) {
        repeat(5) { index ->
            send(index * 2)
            delay(100)
            println("Sent")
        }
    }

    delay(1000)
    for (element in channel) {
        println(element)
        delay(1000)
    }
}

// 0
// Sent
// (1 sec)
// 2
// Sent
// (1 sec)
// 4
// Sent
// (1 sec)
// 6
// Sent
// (1 sec)
// 8
// Sent
// (1 sec)
//sampleEnd
```


Schließlich speichern wir keine vergangenen Elemente, wenn wir die Kapazität `Channel.CONFLATED` verwenden. Neue Elemente ersetzen die vorherigen, sodass wir das letzte nur empfangen können, daher übersehen wir Elemente, die früher gesendet wurden.

{crop-start: 5, crop-end: 31}
```kotlin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val channel = produce(capacity = Channel.CONFLATED) {
        repeat(5) { index ->
            send(index * 2)
            delay(100)
            println("Sent")
        }
    }

    delay(1000)
    for (element in channel) {
        println(element)
        delay(1000)
    }
}

// Sent
// (0.1 sec)
// Sent
// (0.1 sec)
// Sent
// (0.1 sec)
// Sent
// (0.1 sec)
// Sent
// (1 - 4 * 0.1 = 0.6 sec)
// 8
//sampleEnd
```


### Über Buffer-Overflow

Um Kanäle weiter anzupassen, können wir steuern, was passiert, wenn der Puffer voll ist (`onBufferOverflow` Parameter). Es gibt folgende Optionen:
* `SUSPEND` (Standard) - Wenn der Puffer voll ist, wird die Operation `send` ausgesetzt.
* `DROP_OLDEST` - Wenn der Puffer voll ist, wird das älteste Element verworfen.
* `DROP_LATEST` - Wenn der Puffer voll ist, wird das neueste Element verworfen.

Wie Sie vielleicht vermuten, ist die Kanalkapazität `Channel.CONFLATED` dasselbe wie die Kapazität auf 1 zu setzen und `onBufferOverflow` auf `DROP_OLDEST`. Derzeit erlaubt die `produce` Funktion uns nicht, einen benutzerdefinierten `onBufferOverflow` einzustellen, also um dies zu tun, müssen wir einen Kanal mit der Funktion `Channel`[^301_2] definieren.

{crop-start: 4, crop-end: 38}
```kotlin
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.*

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val channel = Channel<Int>(
        capacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    launch {
        repeat(5) { index ->
            channel.send(index * 2)
            delay(100)
            println("Sent")
        }
        channel.close()
    }

    delay(1000)
    for (element in channel) {
        println(element)
        delay(1000)
    }
}

// Sent
// (0.1 sec)
// Sent
// (0.1 sec)
// Sent
// (0.1 sec)
// Sent
// (0.1 sec)
// Sent
// (1 - 4 * 0.1 = 0.6 sec)
// 6
// (1 sec)
// 8
//sampleEnd
```

### Über den Handler für nicht zugestellte Elemente

Ein weiterer Funktionsparameter des `Channel`, den wir kennen sollten, ist `onUndeliveredElement`. Er wird aktiviert, wenn ein Element aus irgendeinem Grund nicht bearbeitet werden konnte. Meistens bedeutet es, dass ein Kanal geschlossen oder storniert wurde, es kann aber auch passieren, wenn `send`, `receive`, `receiveOrNull` oder `hasNext` einen Fehler auslösen. Wir nutzen ihn im Allgemeinen, um Ressourcen freizugeben, die von diesem Kanal gesendet werden.

```kotlin
val channel = Channel<Resource>(capacity) { resource ->
    resource.close()
}
// or
// val channel = Channel<Resource>(
//      capacity,
//      onUndeliveredElement = { resource ->
//          resource.close()
//      }
// )

// Producer code
val resourceToSend = openResource()
channel.send(resourceToSend)

// Consumer code
val resourceReceived = channel.receive()
try {
    // work with received resource
} finally {
    resourceReceived.close()
}
```

### Fan-Out

Mehrere Coroutinen können von einem einzelnen Kanal empfangen; jedoch, um sie richtig zu verarbeiten, sollten wir eine For-Schleife verwenden (`consumeEach` ist nicht sicher für die Verwendung von mehreren Coroutinen).

{width: 100%}
![](channel_fanout.png)

{crop-start: 5, crop-end: 36}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce

//sampleStart
fun CoroutineScope.produceNumbers() = produce {
    repeat(10) {
        delay(100)
        send(it)
    }
}

fun CoroutineScope.launchProcessor(
    id: Int,
    channel: ReceiveChannel<Int>
) = launch {
    for (msg in channel) {
        println("#$id received $msg")
    }
}

suspend fun main(): Unit = coroutineScope {
    val channel = produceNumbers()
    repeat(3) { id ->
        delay(10)
        launchProcessor(id, channel)
    }
}

// #0 received 0
// #1 received 1
// #2 received 2
// #0 received 3
// #1 received 4
// #2 received 5
// #0 received 6
// ...
//sampleEnd
```


Die Elemente werden fair verteilt. Der Kanal hat eine FIFO (First-In-First-Out) Warteschlange von Coroutines, die auf ein Element warten. Deshalb können Sie im obigen Beispiel sehen, dass die Elemente von den nächsten Coroutines empfangen werden (0, 1, 2, 0, 1, 2, usw).

> Um besser zu verstehen warum, stellen Sie sich vor, Kinder in einem Kindergarten stehen für Süßigkeiten an. Sobald sie welche bekommen, essen sie diese sofort und gehen an die letzte Stelle in der Schlange. Solche Verteilung ist fair (angenommen die Anzahl der Süßigkeiten ist ein Vielfaches der Anzahl der Kinder, und vorausgesetzt, ihre Eltern haben nichts dagegen, dass ihre Kinder Süßigkeiten essen).

### Fan-in

Mehrere Coroutines können an einen einzigen Kanal senden. Im untenstehenden Beispiel können Sie sehen, dass zwei Coroutines Elemente an denselben Kanal senden.

{width: 100%}
![](channel_fanin.png)

{crop-start: 4, crop-end: 33}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

//sampleStart
suspend fun sendString(
    channel: SendChannel<String>,
    text: String,
    time: Long
) {
    while (true) {
        delay(time)
        channel.send(text)
    }
}

fun main() = runBlocking {
    val channel = Channel<String>()
    launch { sendString(channel, "foo", 200L) }
    launch { sendString(channel, "BAR!", 500L) }
    repeat(50) {
        println(channel.receive())
    }
    coroutineContext.cancelChildren()
}
// (200 ms)
// foo
// (200 ms)
// foo
// (100 ms)
// BAR!
// (100 ms)
// foo
// (200 ms)
// ...
//sampleEnd
```


Manchmal müssen wir mehrere Kanäle in einen zusammenfassen. Für diesen Zweck könnte die folgende Funktion hilfreich sein, da sie mehrere Kanäle durch die `produce` Funktion zusammenfasst:


```kotlin
fun <T> CoroutineScope.fanIn(
   channels: List<ReceiveChannel<T>>
): ReceiveChannel<T> = produce {
   for (channel in channels) {
       launch {
           for (elem in channel) {
               send(elem)
           }
       }
   }
}
```


### Pipelines

Wir stellen manchmal zwei Kanäle so ein, dass der eine auf Grundlage der Elemente, die er vom anderen Kanal erhält, Elemente produziert. Diesen Vorgang bezeichnen wir als Pipeline.

{crop-start: 4, crop-end: 28}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

//sampleStart
// A channel of number from 1 to 3
fun CoroutineScope.numbers(): ReceiveChannel<Int> =
    produce {
        repeat(3) { num ->
            send(num + 1)
        }
    }

fun CoroutineScope.square(numbers: ReceiveChannel<Int>) =
    produce {
        for (num in numbers) {
            send(num * num)
        }
    }

suspend fun main() = coroutineScope {
    val numbers = numbers()
    val squared = square(numbers)
    for (num in squared) {
        println(num)
    }
}
// 1
// 4
// 9
//sampleEnd
```


### Kanäle als Kommunikationsprimitive

Kanäle sind nützlich, wenn verschiedene Routinen miteinander kommunizieren müssen. Sie garantieren keine Konflikte (d.h., kein Problem mit dem gemeinsamen Zustand) und Gerechtigkeit.

Um sie in Aktion zu sehen, stellen Sie sich vor, dass verschiedene Baristas Kaffee zubereiten. Jeder Barista sollte eine separate Routine sein, die unabhängig arbeitet. Verschiedene Kaffeesorten benötigen unterschiedliche Zeit zur Vorbereitung, aber wir möchten die Bestellungen in der Reihenfolge bearbeiten, in der sie erscheinen. Der einfachste Weg, dieses Problem zu lösen, besteht darin, sowohl die Bestellungen als auch die resultierenden Kaffees in Kanäle zu senden. Ein Barista kann mit dem `produce` Builder definiert werden:


```kotlin
suspend fun CoroutineScope.serveOrders(
    orders: ReceiveChannel<Order>,
    baristaName: String
): ReceiveChannel<CoffeeResult> = produce {
    for (order in orders) {
        val coffee = prepareCoffee(order.type)
        send(
            CoffeeResult(
                coffee = coffee,
                customer = order.customer,
                baristaName = baristaName
            )
        )
    }
}
```


Wenn wir eine Pipeline einrichten, können wir die zuvor definierte `fanIn` Funktion verwenden, um die von den verschiedenen Baristas erzeugten Ergebnisse zu einem zu vereinen:

{crop-start: 25, crop-end: 29}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

data class Order(val customer: String, val type: CoffeeType)
enum class CoffeeType { ESPRESSO, LATTE }
class Milk
class GroundCoffee

sealed class Coffee

class Espresso(val ground: GroundCoffee) : Coffee() {
    override fun toString(): String = "Espresso"
}

class Latte(val milk: Milk, val espresso: Espresso) : Coffee() {
    override fun toString(): String = "Latte"
}

//sampleStart
suspend fun main() = coroutineScope<Unit> {
    val orders = List(100) { Order("Customer$it", CoffeeType.values().random()) }
    val ordersChannel = produce {
        orders.forEach { send(it) }
    }

    val coffeeResults = fanIn(
        serveOrders(ordersChannel, "Alex"),
        serveOrders(ordersChannel, "Bob"),
        serveOrders(ordersChannel, "Celine"),
    )

    for (coffeeResult in coffeeResults) {
        println("Serving $coffeeResult")
    }
}
//sampleEnd

fun <T> CoroutineScope.fanIn(
    vararg channels: ReceiveChannel<T>
): ReceiveChannel<T> = produce {
    for (channel in channels) {
        launch {
            for (elem in channel) {
                send(elem)
            }
        }
    }
}

data class CoffeeResult(val coffee: Coffee, val customer: String, val baristaName: String)

fun CoroutineScope.serveOrders(
    orders: ReceiveChannel<Order>,
    baristaName: String
): ReceiveChannel<CoffeeResult> = produce {
    for (order in orders) {
        val coffee = prepareCoffee(order.type)
        send(CoffeeResult(coffee, order.customer, baristaName))
    }
}

private fun prepareCoffee(type: CoffeeType): Coffee {
    val groundCoffee = groundCoffee()
    val espresso = makeEspresso(groundCoffee)
    val coffee = when (type) {
        CoffeeType.ESPRESSO -> espresso
        CoffeeType.LATTE -> {
            val milk = brewMilk()
            Latte(milk, espresso)
        }
    }
    return coffee
}

fun groundCoffee(): GroundCoffee {
    longOperation()
    return GroundCoffee()
}

fun brewMilk(): Milk {
    longOperation()
    return Milk()
}


fun makeEspresso(ground: GroundCoffee): Espresso {
    longOperation()
    return Espresso(ground)
}

fun longOperation() {
    //    val size = 820 // ~1 second on my MacBook
    val size = 350 // ~0.1 second on my MacBook
    val list = List(size) { it }
    val listOfLists = List(size) { list }
    val listOfListsOfLists = List(size) { listOfLists }
    listOfListsOfLists.hashCode()
}
```

Im nächsten Kapitel erwarten Sie mehr praktische Beispiele.

### Praktische Anwendung

Ein typischer Anwendungsfall, in dem wir Kanäle nutzen, ist wenn Werte auf der einen Seite produziert werden und wir sie auf der anderen Seite bearbeiten wollen. Beispiele hierfür sind das Reagieren auf Benutzerklicks, neue Benachrichtigungen von einem Server oder die Aktualisierung von Suchergebnissen über die Zeit (ein gutes Beispiel ist SkyScanner, das die günstigsten Flüge sucht, indem es mehrere Fluggesellschaftswebsites durchsucht). In den meisten dieser Fälle ist es jedoch besser, `channelFlow` oder `callbackFlow` zu verwenden, beides Hybriden aus Channel und Flow (wir werden diese im Kapitel *Flow building* erklären).

{width: 100%}
![SkyScanner zeigt immer bessere Flugsuchergebnisse an, je mehr Airlines antworten.](skyscanner.png)

In reiner Form finde ich Kanäle besonders nützlich in komplexeren Verarbeitungsfällen. Nehmen wir an, wir betreiben einen Online-Shop ähnlich wie Amazon. Stellen Sie sich vor, Ihr Service erhält eine Vielzahl von Änderungen von Verkäufern, die ihre Angebote beeinflussen könnten. Bei jeder Änderung müssen wir zunächst eine Liste der zu aktualisierenden Angebote finden, um sie dann nacheinander zu aktualisieren.

{width: 100%}
![](update_offers_service.png)

Dies auf die traditionelle Art und Weise zu tun, wäre suboptimal. Ein Verkäufer könnte sogar hunderttausende Angebote haben. Alles in einem einzigen langen Prozess zu erledigen, ist keine gute Idee.

Erstens könnte ein interner Fehler oder ein Serverneustart uns im Unklaren lassen, wo wir aufgehört haben. Zweitens könnte ein großer Verkäufer den Server für eine lange Zeit blockieren, was dazu führen könnte, dass kleinere Verkäufer auf die Umsetzung ihrer Änderungen warten müssen. Darüber hinaus sollten wir nicht zu viele Netzwerkanfragen gleichzeitig senden, um den Dienst, der sie bearbeiten muss (und unsere Netzwerkschnittstelle), nicht zu überlasten.

Die Lösung für dieses Problem könnte darin bestehen, eine Pipeline aufzubauen. Der erste Kanal könnte die zu verarbeitenden Verkäufer enthalten, während der zweite die zu aktualisierenden Angebote enthalten würde. Diese Kanäle hätten einen Puffer. Der Puffer im zweiten könnte unseren Service davon abhalten, weitere Angebote zu erhalten, wenn bereits zu viele in der Warteschlange sind. Auf diese Weise könnte unser Server die Anzahl der gleichzeitig aktualisierten Angebote ausbalancieren.

Wir könnten auch problemlos einige Zwischenschritte hinzufügen, wie das Entfernen von Duplikaten. Indem wir die Anzahl der Koroutinen definieren, die auf jedem Kanal lauschen, entscheiden wir, wie viele gleichzeitige Anfragen wir an den externen Dienst stellen möchten. Die Manipulation dieser Parameter gibt uns eine Menge Freiheit. Es gibt auch viele Verbesserungen, die recht einfach hinzugefügt werden können, wie die Persistenz (für den Fall, dass der Server neu startet) oder die Einzigartigkeit der Elemente (für den Fall, dass der Verkäufer eine weitere Änderung vornimmt, bevor die vorherige verarbeitet wurde).

{width: 100%}
![](channel_offer_update.png)

```kotlin
// A simplified implementation
suspend fun handleOfferUpdates() = coroutineScope {
   val sellerChannel = listenOnSellerChanges()

   val offerToUpdateChannel = produce(capacity = UNLIMITED) {
       repeat(NUMBER_OF_CONCURRENT_OFFER_SERVICE_REQUESTS) {
           launch {
               for (seller in sellerChannel) {
                   val offers = offerService
                       .requestOffers(seller.id)
                   offers.forEach { send(it) }
               }
           }
       }
   }

   repeat(NUMBER_OF_CONCURRENT_UPDATE_SENDERS) {
       launch {
           for (offer in offerToUpdateChannel) {
               sendOfferUpdate(offer)
           }
       }
   }
}
```

### Zusammenfassung

Channel ist ein mächtiges Kommunikationsprinzip zwischen Coroutinen. Es unterstützt beliebig viele Sender und Empfänger, und jeder an einen Channel gesendete Wert wird genau einmal empfangen. Oft erstellen wir einen Channel mit dem `produce` Builder. Channels können auch genutzt werden, um eine Pipeline zu etablieren, in der wir die Anzahl der an bestimmten Aufgaben arbeitenden Coroutinen kontrollieren können. In der heutigen Zeit nutzen wir Channels vornehmlich in Verbindung mit Flow, das später im Buch präsentiert wird.

[^301_1]: Der Ursprung liegt im französischen Wort "rendez-vous", was üblicherweise "Verabredung" bedeutet. Dieses schöne Wort hat die Grenzen überschritten: im Englischen gibt es das weniger gebräuchliche Wort "rendezvous"; im Polnischen das Wort "randka", was ein romantisches Date bedeutet.
[^301_2]: `Channel` ist eine Schnittstelle, daher stellt `Channel()` den Aufruf einer Funktion dar, die sich als Konstruktor ausgibt.
[^301_3]: Die Funktion `consumeEach` nutzt eine for-Schleife im Hintergrund, aber sie beendet auch den Channel, sobald sie alle ihre Elemente genutzt hat (also, sobald sie geschlossen ist).


