## Akteur

In der Informatik gibt es ein Modell der nebenläufigen Berechnung, bekannt als das **Akteur-Modell**, bei dem der Akteur das wichtigste Konzept ist. Dies ist eine Berechnungseinheit, die auf eine Nachricht, die sie erhält, parallel reagieren kann:
- eine endliche Anzahl von Nachrichten an andere Akteure senden;
- eine endliche Anzahl neuer Akteure erstellen;
- das Verhalten bestimmen, das für die nächste Nachricht, die sie erhält, verwendet werden soll.

Akteure können ihren eigenen privaten Zustand ändern, aber sie können sich nur indirekt durch Messaging beeinflussen, daher ist eine Synchronisation zwischen ihnen nicht notwendig. Jeder Akteur läuft auf einem einzelnen Thread und bearbeitet seine Nachrichten nacheinander.

Mit Kotlin Coroutinen lässt sich dieses Modell recht einfach implementieren. Wir verwenden Kanäle, um eine Warteschlange von Nachrichten an den Akteur zu synchronisieren, dann brauchen wir nur eine Coroutine, die diese Nachrichten nacheinander bearbeitet. Im folgenden Ausschnitt sehen Sie, wie unser `massiveRun`-Problem mit dem Akteur-Modell gelöst wird.

{crop-start: 14, crop-end: 45}

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel

suspend fun massiveRun(action: suspend () -> Unit) =
    withContext(Dispatchers.Default) {
        List(1000) {
            launch {
                repeat(1000) { action() }
            }
        }
    }

//sampleStart
sealed class CounterMsg
object IncCounter : CounterMsg()
class GetCounter(
    val response: CompletableDeferred<Int>
) : CounterMsg()

fun CoroutineScope.counterActor(): Channel<CounterMsg> {
    val channel = Channel<CounterMsg>()
    launch {
        var counter = 0
        for (msg in channel) {
            when (msg) {
                is IncCounter -> {
                    counter++
                }
                is GetCounter -> {
                    msg.response.complete(counter)
                }
            }
        }
    }
    return channel
}

suspend fun main(): Unit = coroutineScope {
    val counter: SendChannel<CounterMsg> = counterActor()
    massiveRun { counter.send(IncCounter) }
    val response = CompletableDeferred<Int>()
    counter.send(GetCounter(response))
    println(response.await()) // 1000000
    counter.close()
}
//sampleEnd
```


Hier gibt es keine Probleme mit der Synchronisation, da der actor auf einer einzigen Koroutine arbeitet.

Um dieses Modell zu vereinfachen, gibt es den `actor` Koroutine-Builder, der das tut, was bereits gezeigt wurde (erstellt einen Kanal und startet eine Koroutine) und bietet eine bessere Unterstützung für die Ausnahmehandhabung (d.h., eine Ausnahme in der Builder-Koroutine beendet den Kanal).

{crop-start: 19, crop-end: 27}

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor

suspend fun massiveRun(action: suspend () -> Unit) = 
    withContext(Dispatchers.Default) {
        List(1000) {
            launch {
                repeat(1000) { action() }
            }
        }
    }

sealed class CounterMsg
object IncCounter : CounterMsg()
class GetCounter(
    val response: CompletableDeferred<Int>,
) : CounterMsg()

//sampleStart
fun CoroutineScope.counterActor() = actor<CounterMsg> {
    var counter = 0
    for (msg in channel) {
        when (msg) {
            is IncCounter -> counter++
            is GetCounter -> msg.response.complete(counter)
        }
    }
}
//sampleEnd

suspend fun main(): Unit = coroutineScope {
    val counter: SendChannel<CounterMsg> = counterActor()
    massiveRun { counter.send(IncCounter) }
    val response = CompletableDeferred<Int>()
    counter.send(GetCounter(response))
    println(response.await()) // 1000000
    counter.close()
}
```



### Zusammenfassung

Das Actor Model ist ein wichtiges Modell für die nebenläufige Verarbeitung. Es ist derzeit in Kotlin nicht sehr beliebt, aber es lohnt sich, darüber Bescheid zu wissen, da es Fälle gibt, in denen es perfekt passt. In diesem Modell ist der wichtigste Begriff der Actor, eine Verarbeitungseinheit, die auf Nachrichten reagiert. Da er auf einer einzelnen Coroutine betrieben wird, gibt es keine Konflikte beim Zugriff auf seinen Zustand. Wir können Standardfunktionen in Actors einbetten, die Persistenz oder Priorisierung ermöglichen können, was viele interessante Möglichkeiten eröffnet.

Es gab eine Zeit, in der das Actor Model als vielversprechender Ansatz für die Gestaltung von Backend-Anwendungen (wie in Akka) galt, aber meiner Einschätzung nach scheint dies heute nicht mehr der Fall zu sein. Ich glaube, das liegt daran, dass die meisten Entwickler statt dessen ad-hoc Warteschlangen oder Streaming-Software, wie Kafka oder RabbitMQ, bevorzugen, die besser zu den modernen Praktiken der Backend-Entwicklung zu passen scheinen.


