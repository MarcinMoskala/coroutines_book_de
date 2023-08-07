
## Sequenz-Builder

In einigen anderen Sprachen, wie Python oder JavaScript, finden Sie Strukturen, die begrenzte Formen von Coroutinen verwenden:
* asynchrone Funktionen (auch async/await genannt);
* Generatorfunktionen (Funktionen, in denen nachfolgende Werte erzeugt werden).

Wir haben bereits gesehen, wie `async` in Kotlin verwendet werden kann, aber dies wird im Kapitel *Coroutine-Builder* ausführlich erklärt. Anstelle von Generatoren bietet Kotlin einen Sequenz-Builder - eine Funktion, die zum Erstellen einer Sequenz[^102_1] verwendet wird.

Eine Kotlin-Sequenz ist ein ähnliches Konzept wie eine Sammlung (wie `List` oder `Set`), wird aber faul ausgewertet, was bedeutet, dass das nächste Element immer auf Anforderung berechnet wird, wenn es benötigt wird. Daher:
* führen Sequenzen die minimale Anzahl von erforderlichen Operationen durch;
* können Sequenzen unendlich sein;
* sind Sequenzen effizienter im Speicherverbrauch[^102_2].

Es macht viel Sinn, einen Builder zu definieren, in dem nachfolgende Elemente bei Bedarf berechnet und "erzeugt" werden. Wir definieren ihn mit der Funktion `sequence`. Innerhalb ihres Lambda-Ausdrucks können wir die Funktion `yield` verwenden, um die nächsten Elemente dieser Sequenz zu erzeugen.

```kotlin
val seq = sequence {
    yield(1)
    yield(2)
    yield(3)
}
```

{pagebreak}

```kotlin
fun main() {
  for (num in seq) {
      print(num)
  } // 123
}
```


> Die `sequence` Funktion hier ist eine kleine DSL. Ihr Argument ist ein Lambda-Ausdruck mit einem Receiver (`suspend SequenceScope<T>.() -> Unit`). In diesem Kontext bezieht sich der Receiver `this` auf ein Objekt vom Typ `SequenceScope<T>`. Es verfügt über Funktionen wie `yield`. Wenn wir `yield(1)` aufrufen, ist dies äquivalent zum Aufruf von `this.yield(1)`, weil `this` implizit verwendet werden kann. Wenn dies Ihr erster Kontakt mit Lambda-Ausdrücken, die Empfänger haben, ist, empfehle ich, zuerst über sie und die Erstellung von DSLs zu lernen, da sie weithin in Kotlin Coroutines verwendet werden.

Was hier wichtig ist, ist, dass jede Zahl auf Anfrage generiert wird, nicht im Voraus. Sie können diesen Prozess deutlich beobachten, wenn wir sowohl im Builder als auch dort, wo wir unsere Sequenz behandeln, etwas ausdrucken.

{crop-start: 3, crop-end: 24}
```kotlin
import kotlin.*

//sampleStart
val seq = sequence {
    println("Generating first")
    yield(1)
    println("Generating second")
    yield(2)
    println("Generating third")
    yield(3)
    println("Done")
}

fun main() {
    for (num in seq) {
        println("The next number is $num")
    }
}
// Generating first
// The next number is 1
// Generating second
// The next number is 2
// Generating third
// The next number is 3
// Done
//sampleEnd
```


Analysieren wir, wie es funktioniert. Wir fordern die erste Nummer an, also betreten wir den Ersteller. Wir drucken "Erste Nummer generieren", und wir liefern die Nummer 1. Dann kehren wir mit dem gelieferten Wert zur Schleife zurück, und so wird "Nächste Nummer ist 1" gedruckt. Dann passiert etwas Entscheidendes: Die Ausführung wechselt zu der Stelle, an der wir zuvor gestoppt haben, um eine andere Zahl zu finden. Dies wäre ohne einen Unterbrechungsmechanismus unmöglich, da es nicht möglich wäre, eine Funktion in der Mitte zu stoppen und sie in der Zukunft von der gleichen Stelle aus fortzusetzen. Dank der Unterbrechung können wir dies tun, da die Ausführung zwischen `main` und dem Sequenzgenerator wechseln kann.

{width: 100%}
![Wenn wir den nächsten Wert in der Sequenz anfordern, setzen wir im Ersteller direkt nach dem vorherigen `yield` fort.](sequence.png)

Um es klarer zu sehen, fordern wir manuell einige Werte aus der Sequenz an.

{crop-start: 3, crop-end: 28}
```kotlin
import kotlin.*

//sampleStart
val seq = sequence {
    println("Generating first")
    yield(1)
    println("Generating second")
    yield(2)
    println("Generating third")
    yield(3)
    println("Done")
}

fun main() {
    val iterator = seq.iterator()
    println("Starting")
    val first = iterator.next()
    println("First: $first")
    val second = iterator.next()
    println("Second: $second")
    // ...
}

// Prints:
// Starting
// Generating first
// First: 1
// Generating second
// Second: 2
//sampleEnd
```

Hier haben wir einen Iterator verwendet, um die nächsten Werte zu bekommen. Zu jedem Zeitpunkt können wir ihn erneut aufrufen, um mitten in der Builder-Funktion fortzufahren und den nächsten Wert zu generieren. Wäre dies ohne Koroutinen (coroutines) möglich? Vielleicht, wenn wir einen eigenen Thread dafür einrichten würden. Ein solcher Thread müsste jedoch gepflegt werden, was enorme Kosten verursachen würde. Mit Koroutinen ist es schnell und einfach. Außerdem können wir diesen Iterator so lange behalten, wie wir möchten, da er kaum etwas kostet. Bald werden wir lernen, wie dieser Mechanismus unter der Haube funktioniert (im Kapitel *Suspension unter der Haube*).

### Praktische Anwendungen

Es gibt einige Anwendungsfälle, in denen Sequenz-Builder verwendet werden. Der geläufigste ist die Generierung einer mathematischen Sequenz, wie etwa der Fibonacci-Sequenz.

{crop-start: 3, crop-end: 17}
```kotlin
import java.math.BigInteger

//sampleStart
val fibonacci: Sequence<BigInteger> = sequence {
    var first = 0.toBigInteger()
    var second = 1.toBigInteger()
    while (true) {
        yield(first)
        val temp = first
        first += second
        second = temp
    }
}

fun main() {
    print(fibonacci.take(10).toList())
}
// [0, 1, 1, 2, 3, 5, 8, 13, 21, 34]
//sampleEnd
```

Dieser Builder lässt sich auch nutzen, um zufällige Zahlen oder Texte zu generieren.

```kotlin
fun randomNumbers(
    seed: Long = System.currentTimeMillis()
): Sequence<Int> = sequence {
    val random = Random(seed)
    while (true) {
        yield(random.nextInt())
    }
}

fun randomUniqueStrings(
    length: Int,
    seed: Long = System.currentTimeMillis()
): Sequence<String> = sequence {
    val random = Random(seed)
    val charPool = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    while (true) {
        val randomString = (1..length)
            .map { i -> random.nextInt(charPool.size) }
            .map(charPool::get)
            .joinToString("");
        yield(randomString)
    }
}.distinct()
```

Der Sequenz-Builder sollte keine suspendierenden Operationen außer yield-Operationen[^102_3] verwenden. Wenn Sie beispielsweise Daten abrufen müssen, ist es besser, Flow zu verwenden, wie später im Buch erläutert wird. Die Arbeitsweise von Flow's Builder ähnelt der des Sequenz-Builders, aber Flow unterstützt andere Coroutine-Funktionen.

```kotlin
fun allUsersFlow(
  api: UserApi
): Flow<User> = flow {
  var page = 0
  do {
      val users = api.takePage(page++) // suspending
      emitAll(users)
  } while (!users.isNullOrEmpty())
}
```

Wir haben über den Sequenzerzeuger gelernt und warum er eine Suspension zur korrekten Funktion benötigt. Jetzt, wo wir die Suspension in Aktion gesehen haben, ist es an der Zeit, noch tiefer einzutauchen, um zu verstehen, wie die Suspension funktioniert, wenn wir sie direkt verwenden.

[^102_1]: Besser noch, es bietet Flow-Builder. Flow ist ein ähnliches, aber viel leistungsfähigeres Konzept, das wir später im Buch erklären werden.
[^102_2]: Siehe Punkt [*Vorzug von Sequenzen für große Sammlungen mit mehr als einer Verarbeitungsphase*](https://kt.academy/article/ek-sequence) in [Effective Kotlin](https://leanpub.com/effectivekotlin/).
[^102_3]: Das ist nicht möglich, weil `SequenceScope` mit `RestrictsSuspension` annotiert ist, was verhindert, dass die Suspension-Funktion aufgerufen wird, es sei denn, ihr Empfänger ist `SequenceScope`.


