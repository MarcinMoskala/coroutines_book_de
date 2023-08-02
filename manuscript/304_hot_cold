
## Heiße und kalte Datenquellen

Kotlin Coroutines hatten ursprünglich nur `Channel`, aber die Entwickler bemerkten, dass dies nicht ausreichend war. Channels sind ein *heißer* Strom von Werten, aber oft benötigen wir einen Strom, der *kalt* ist.

{width: 100%}
![](cold_issue2.png)

Das Verständnis des Unterschieds zwischen heißen und kalten Datenströmen ist eine nützliche Kenntnis in der Softwareentwicklung, da die meisten Datenquellen, die Sie täglich verwenden, in eine dieser beiden Kategorien fallen. Sammlungen (`List`, `Set`, usw.) sind heiß, während `Sequence` und Java `Stream` kalt sind. `Channel` ist heiß, während `Flow` und RxJava-Streams (`Observable`, `Single`, usw.) kalt sind[^304_1].

|           Heiß           |         Kalt         |
|:-----------------------:|:--------------------:|
| Sammlungen (List, Set) |   Sequence, Stream   |
|         Channel         | Flow, RxJava-Streams |

### Heiß vs Kalt

Heiße Datenströme sind aktiv, erzeugen Elemente unabhängig von deren Verbrauch und speichern die Elemente. Kalte Datenströme sind passiv, führen ihre Operationen auf Abruf durch und speichern nichts.

Wir können diese Unterschiede beobachten, wenn wir Listen (heiß) und Sequenzen (kalt) verwenden. Ersteller und Operationen auf heißen Datenströmen beginnen sofort. Bei kalten Datenströmen starten sie nicht, bis die Elemente benötigt werden.

{crop-start: 3, crop-end: 34}
```kotlin
import kotlin.*

//sampleStart
fun main() {
    val l = buildList {
        repeat(3) {
            add("User$it")
            println("L: Added User")
        }
    }

    val l2 = l.map {
        println("L: Processing")
        "Processed $it"
    }

    val s = sequence {
        repeat(3) {
            yield("User$it")
            println("S: Added User")
        }
    }

    val s2 = s.map {
        println("S: Processing")
        "Processed $it"
    }
}
// L: Added User
// L: Added User
// L: Added User
// L: Processing
// L: Processing
// L: Processing
//sampleEnd
```


Als Ergebnis können kalte Datenströme (wie `Sequence`, `Stream` oder `Flow`):
* unendlich sein;
* eine minimale Anzahl von Operationen durchführen;
* weniger Speicher verwenden (es besteht keine Notwendigkeit, alle Zwischensammlungen zu allokieren).

Die `Sequence`-Verarbeitung führt weniger Operationen durch, da sie Elemente träge verarbeitet. Die Funktionsweise ist sehr einfach. Jede Zwischenoperation (wie `map` oder `filter`) ergänzt die vorherige Sequenz einfach mit einer neuen Operation. Die Endoperation[^304_2] erledigt alle Arbeiten. Betrachten Sie das untenstehende Beispiel. Im Falle einer Sequenz fragt `find` das Ergebnis der `map` nach dem ersten Element. Es fragt die von `sequenceOf` zurückgegebene Sequenz (gibt `1` zurück), bildet sie dann ab (zu `1`) und gibt sie an den `filter` zurück. `filter` prüft, ob es sich um ein Element handelt, das die gestellten Kriterien erfüllt. Wenn das Element die Kriterien nicht erfüllt, fragt `filter` immer wieder nach, bis das passende Element gefunden ist.

Dies unterscheidet sich stark von der Listenverarbeitung, bei der bei jedem Zwischenschritt eine vollständig verarbeitete Sammlung berechnet und zurückgegeben wird. Deshalb ist die Reihenfolge der Elementverarbeitung anders und die Sammlungsverarbeitung benötigt mehr Speicher und kann mehr Operationen erfordern (wie im untenstehenden Beispiel).

{crop-start: 3, crop-end: 27}
```kotlin
import kotlin.*

//sampleStart
fun m(i: Int): Int {
    print("m$i ")
    return i * i
}

fun f(i: Int): Boolean {
    print("f$i ")
    return i >= 10
}

fun main() {
    listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        .map { m(it) }
        .find { f(it) }
        .let { print(it) }
    // m1 m2 m3 m4 m5 m6 m7 m8 m9 m10 f1 f4 f9 f16 16

    println()

    sequenceOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        .map { m(it) }
        .find { f(it) }
        .let { print(it) }
    // m1 f1 m2 f4 m3 f9 m4 f16 16
}
//sampleEnd
```

Das bedeutet, dass eine Liste eine Sammlung von Elementen ist, aber eine Sequenz ist lediglich eine Definition, wie diese Elemente berechnet werden sollten. Heiße Datenströme:
* stehen jederzeit zur Verfügung (jede Operation kann eine Endoperation sein);
* müssen das Ergebnis nicht neu berechnen, wenn sie mehrmals genutzt werden.

{crop-start: 3, crop-end: 25**}
```kotlin
import kotlin.*

//sampleStart
fun m(i: Int): Int {
    print("m$i ")
    return i * i
}

fun main() {
    val l = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        .map { m(it) } // m1 m2 m3 m4 m5 m6 m7 m8 m9 m10

    println(l) // [1, 4, 9, 16, 25, 36, 49, 64, 81, 100]
    println(l.find { it > 10 }) // 16
    println(l.find { it > 10 }) // 16
    println(l.find { it > 10 }) // 16

    val s = sequenceOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        .map { m(it) }

    println(s.toList())
    // [1, 4, 9, 16, 25, 36, 49, 64, 81, 100]
    println(s.find { it > 10 }) // m1 m2 m3 m4 16
    println(s.find { it > 10 }) // m1 m2 m3 m4 16
    println(s.find { it > 10 }) // m1 m2 m3 m4 16
}
//sampleEnd
```


Java `Stream` teilt Eigenschaften mit Kotlin’s `Sequence`. Beide sind kalte Ströme von Werten.

### Heiße Kanäle, kalter Fluss

Jetzt geht's wieder zu den Coroutinen. Der häufigste Weg, einen Flow zu erstellen, ist die Verwendung eines Builders, der der `produce` Funktion ähnlich ist. Es wird `flow` genannt.


```kotlin
val channel = produce {
    while (true) {
        val x = computeNextValue()
        send(x)
    }
}

val flow = flow {
    while (true) {
        val x = computeNextValue()
        emit(x)
    }
}
```


Diese Builder sind konzeptionell gleichwertig, aber da das Verhalten von Kanälen und Flows sehr unterschiedlich ist, gibt es auch wichtige Unterschiede zwischen diesen beiden Funktionen. Betrachten Sie das untenstehende Beispiel. Kanäle sind aktiv, deshalb beginnen sie sofort die Werte zu berechnen. Diese Berechnung beginnt in einer separaten Coroutine. Aus diesem Grund muss `produce` ein Coroutine Builder sein, der als Erweiterungsfunktion auf `CoroutineScope` definiert ist. Die Berechnung startet sofort, doch da die Standardpuffergröße `0` (Rendezvous) ist, wird sie bald ausgesetzt, bis der Empfänger im folgenden Beispiel bereit ist. Beachten Sie den Unterschied zwischen dem Stoppen der Produktion, wenn kein Empfänger vorhanden ist, und der Produktion auf Anforderung. Kanäle, als aktive Datenkanäle, erzeugen Elemente unabhängig von ihrem Verbrauch und behalten sie dann. Sie machen sich keine Gedanken darüber, wie viele Empfänger es gibt. Da jedes Element nur einmal empfangen werden kann, wird, nachdem der erste Empfänger alle Elemente verbraucht hat, der zweite einen Kanal vorfinden, der bereits leer und geschlossen ist. Aus diesem Grund wird er keine Elemente empfangen.

{crop-start: 4, crop-end: 33}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

//sampleStart
private fun CoroutineScope.makeChannel() = produce {
    println("Channel started")
    for (i in 1..3) {
        delay(1000)
        send(i)
    }
}

suspend fun main() = coroutineScope {
    val channel = makeChannel()

    delay(1000)
    println("Calling channel...")
    for (value in channel) {
        println(value)
    }
    println("Consuming again...")
    for (value in channel) {
        println(value)
    }
}
// Channel started
// (1 sec)
// Calling channel...
// 1
// (1 sec)
// 2
// (1 sec)
// 3
// Consuming again...
//sampleEnd
```

Die Verarbeitung mit Flow unterscheidet sich stark. Da es sich um eine kalte Datenquelle handelt, findet die Erzeugung auf Anforderung statt. Das bedeutet, dass `flow` kein Erzeuger ist und keine Verarbeitung durchführt. Es ist nur eine Definition, wie Elemente produziert werden sollten, die verwendet werden, wenn eine Endoperation (wie `collect`) angewendet wird. Deshalb benötigt der `flow`-Erzeuger keinen `CoroutineScope`. Es wird im Kontext der Endoperation ausgeführt, die es gestartet hat (es nimmt den Kontext von der Fortsetzung der aussetzenden Funktion, genau wie `coroutineScope` und andere Coroutine-Kontextfunktionen). Jede Endoperation in einem Flow startet die Verarbeitung von Neuem. Vergleichen Sie die vorherigen und folgenden Beispiele, da sie die Schlüsselunterschiede zwischen Channel und Flow zeigen.

{crop-start: 4, crop-end: 37}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

//sampleStart
private fun makeFlow() = flow {
    println("Flow started")
    for (i in 1..3) {
        delay(1000)
        emit(i)
    }
}

suspend fun main() = coroutineScope {
    val flow = makeFlow()

    delay(1000)
    println("Calling flow...")
    flow.collect { value -> println(value) }
    println("Consuming again...")
    flow.collect { value -> println(value) }
}
// (1 sec)
// Calling flow...
// Flow started
// (1 sec)
// 1
// (1 sec)
// 2
// (1 sec)
// 3
// Consuming again...
// Flow started
// (1 sec)
// 1
// (1 sec)
// 2
// (1 sec)
// 3
//sampleEnd
```


RxJava Streams teilen die meisten Eigenschaften mit Kotlin's `Flow`. Einige sagen sogar, dass man `Flow` "RxCoroutines" nennen könnte[^304_3].

### Zusammenfassung

Die meisten Datenquellen sind entweder heiß oder kalt:
* Heiße Datenquellen sind eifrig. Sie produzieren Elemente so schnell wie möglich und speichern sie. Sie erstellen Elemente unabhängig von ihrem Verbrauch. Das sind Sammlungen (`List`, `Set`) und `Channel`.
* Kalte Datenquellen sind träge. Sie verarbeiten Elemente bei Bedarf bei der Endoperation. Alle Zwischenfunktionen definieren nur, was getan werden soll (meistens mit dem Decorator-Muster). Sie speichern im Allgemeinen keine Elemente und erstellen sie bei Bedarf. Sie führen nur die minimal notwendigen Operationen durch und können unendlich sein. Ihre Erstellung und Verarbeitung von Elementen ist in der Regel der gleiche Prozess wie der Verbrauch. Diese Elemente sind `Sequence`, Java `Stream`, `Flow` und RxJava Streams (`Observable`, `Single`, etc).

Dies erklärt den wesentlichen Unterschied zwischen Channel und Flow. Jetzt ist es an der Zeit, alle verschiedenen Funktionen zu diskutieren, die von letzterem unterstützt werden.

[^304_1]: Das stimmt im Allgemeinen, aber es gibt Ausnahmen. Einige Funktionen und Builder, wie `buffer` oder `channelFlow`, bringen ein gewisses Maß an Heißheit in den Flow ein. Auch `SharedFlow` und `StateFlow` sind heiß.
[^304_2]: Eine Operation auf einer Sequenz, die einen anderen Typ zurückgibt. Typischerweise `find` oder `toList`.
[^304_3]: Das habe ich zum ersten Mal von Alex Piotrowski auf Kotlin/Everywhere Warsaw 21.11.2019 gehört, https://youtu.be/xV1XRakSoWI. Vielleicht ist er ja derjenige, der diesen Begriff bekannt gemacht hat.

