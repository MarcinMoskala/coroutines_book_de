## Ausnahmebehandlung

Ein sehr wichtiger Teil des Verhaltens von Coroutinen ist ihre Ausnahmebehandlung. Genau wie ein Programm abbricht, wenn eine nicht abgefangene Ausnahme durchrutscht, so bricht auch eine Koroutine im Falle einer nicht abgefangenen Ausnahme ab. Dieses Verhalten ist nicht neu: zum Beispiel enden auch Threads in solchen Fällen. Der Unterschied besteht darin, dass die Ersteller von Coroutinen auch ihre Elternelemente abbrechen, und jedes abgebrochene Elternelement bricht alle seine untergeordneten Elemente ab. Schauen wir uns das untenstehende Beispiel an. Sobald eine Koroutine eine Ausnahme erhält, bricht sie ab und leitet die Ausnahme an ihr Elternelement (`launch`) weiter. Das Elternelement bricht ab und beendet alle seine untergeordneten Elemente, dann leitet es die Ausnahme an sein übergeordnetes Element (`runBlocking`) weiter. `runBlocking` ist eine Wurzelkoroutine (es hat kein übergeordnetes Element), also beendet es einfach das Programm (`runBlocking` wirft die Ausnahme erneut).

{crop-start: 5, crop-end: 29}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

//sampleStart
fun main(): Unit = runBlocking {
    launch {
        launch {
            delay(1000)
            throw Error("Some error")
        }

        launch {
            delay(2000)
            println("Will not be printed")
        }

        launch {
            delay(500) // faster than the exception
            println("Will be printed")
        }
    }

    launch {
        delay(2000)
        println("Will not be printed")
    }
}
// Will be printed
// Exception in thread "main" java.lang.Error: Some error...
//sampleEnd
```


Das Hinzufügen weiterer `launch` Coroutinen würde nichts ändern. Die Ausnahmeausbreitung ist bidirektional: Die Ausnahme wird vom Kind zum Elternteil propagiert, und wenn diese Elternteile abgebrochen werden, brechen sie ihre Kinder ebenfalls ab. Daher werden, wenn die Ausnahmeausbreitung nicht gestoppt wird, alle Coroutinen in der Hierarchie abgebrochen.

{width: 100%}
![](calcellation.png)

### Bitte unterbrechen Sie meine Coroutinen nicht

Es ist hilfreich, eine Ausnahme zu fangen, bevor sie eine Koroutine unterbricht, aber später ist es zu spät. Die Kommunikation erfolgt über einen Job, daher ist das Umgeben eines Coroutinen-Builder mit einem try-catch überhaupt nicht hilfreich.

{crop-start: 5, crop-end: 21}
```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

//sampleStart
fun main(): Unit = runBlocking {
    // Don't wrap in a try-catch here. It will be ignored.
    try {
        launch {
            delay(1000)
            throw Error("Some error")
        }
    } catch (e: Throwable) { // nope, does not help here
        println("Will not be printed")
    }

    launch {
        delay(2000)
        println("Will not be printed")
    }
}
// Exception in thread "main" java.lang.Error: Some error...
//sampleEnd
```


#### SupervisorJob

Der effektivste Weg um zu verhindern, dass Coroutinen fehlschlagen, ist die Verwendung eines `SupervisorJob`. Dies ist eine besondere Art von Aufgabe, die alle Ausnahmen in ihren Unteraufgaben ignoriert.

{width: 100%}
![](cancellation_supervisor_1.png)

{width: 100%}
![](cancellation_supervisor_2.png)

`SupervisorJob` wird üblicherweise im Rahmen eines Bereichs eingesetzt, in dem wir mehrere Coroutinen starten (mehr dazu im Kapitel *Aufbau des Coroutine-Umfangs*).

{crop-start: 3, crop-end: 18}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun main(): Unit = runBlocking {
  val scope = CoroutineScope(SupervisorJob())
  scope.launch {
      delay(1000)
      throw Error("Some error")
  }

  scope.launch {
      delay(2000)
      println("Will be printed")
  }

  delay(3000)
}
// Exception...
// Will be printed
//sampleEnd
```


{width: 100%}
![](cancellation_supervisor_scope.png)

Ein häufiger Fehler besteht darin, einen `SupervisorJob` als Argument für eine Eltern-Koroutine zu verwenden, wie im unten gezeigten Code. Dies wird uns nicht dabei helfen, Ausnahmen zu behandeln, da in einem solchen Fall der `SupervisorJob` nur ein direktes Kind hat, nämlich das `launch`, das an der Stelle 1 definiert ist und diesen `SupervisorJob` als Argument erhalten hat. Daher gibt es in diesem Fall keinen Vorteil, `SupervisorJob` anstelle von `Job` zu verwenden (in beiden Fällen wird die Ausnahme nicht zu `runBlocking` propagieren, da wir seinen Job nicht verwenden).

{crop-start: 3, crop-end: 20}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun main(): Unit = runBlocking {
    // Don't do that, SupervisorJob with one children
    // and no parent works similar to just Job
    launch(SupervisorJob()) { // 1
        launch {
            delay(1000)
            throw Error("Some error")
        }

        launch {
            delay(2000)
            println("Will not be printed")
        }
    }

    delay(3000)
}
// Exception...
//sampleEnd
```


Es wäre sinnvoller, denselben Job als Kontext für mehrere Coroutine-Builder zu verwenden, weil jeder von ihnen abgebrochen werden kann, ohne die anderen zu beeinflussen.

{crop-start: 3, crop-end: 18}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun main(): Unit = runBlocking {
  val job = SupervisorJob()
  launch(job) {
      delay(1000)
      throw Error("Some error")
  }
  launch(job) {
      delay(2000)
      println("Will be printed")
  }
  job.join()
}
// (1 sec)
// Exception...
// (1 sec)
// Will be printed
//sampleEnd
```


#### supervisorScope

Eine weitere Möglichkeit, die Weiterleitung von Ausnahmen zu unterbrechen, besteht darin, Coroutine-Builder in `supervisorScope` zu verpacken. Dies ist sehr nützlich, da wir immer noch eine Verbindung zur übergeordneten Coroutine behalten, aber alle Ausnahmen von der Coroutine werden ignoriert.

{crop-start: 3, crop-end: 21}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun main(): Unit = runBlocking {
  supervisorScope {
      launch {
          delay(1000)
          throw Error("Some error")
      }

      launch {
          delay(2000)
          println("Will be printed")
      }
  }
  delay(1000)
  println("Done")
}
// Exception...
// Will be printed
// (1 sec)
// Done
//sampleEnd
```


`supervisorScope` ist lediglich eine suspendierende Funktion und kann genutzt werden, um suspendierende Funktionskörper zu umgeben. Diese und andere Funktionalitäten von `supervisorScope` werden im nächsten Kapitel ausführlicher beschrieben. Man verwendet sie üblicherweise, um mehrere unabhängige Aufgaben zu starten.


```kotlin
suspend fun notifyAnalytics(actions: List<UserAction>) =
  supervisorScope {
      actions.forEach { action ->
          launch {
              notifyAnalytics(action)
          }
      }
  }
```


Eine weitere Möglichkeit, die Ausnahme-Weiterleitung zu stoppen, besteht darin, `coroutineScope` zu verwenden. Anstatt einen übergeordneten Prozess zu beeinflussen, wirft diese Funktion eine Ausnahme, die mit try-catch eingefangen werden kann (im Gegensatz zu Coroutinen-Erzeugern). Beides wird im nächsten Kapitel beschrieben.

Seien Sie vorsichtig, `supervisorScope` kann nicht durch `withContext(SupervisorJob())` ersetzt werden! Schauen Sie sich den folgenden Codeausschnitt an.


```kotlin
// DON'T DO THAT!
suspend fun sendNotifications(
   notifications: List<Notification>
) = withContext(SupervisorJob()) {
   for (notification in notifications) {
       launch {
           client.send(notification)
       }
   }
}
```

Das Problem hier ist, dass `Job` der einzige Kontext ist, der nicht vererbt wird. Jede Koroutine benötigt ihren eigenen Job, und wenn man einen Job an eine Koroutine weitergibt, macht es sie zur Elternkoroutine. Also hier ist `SupervisorJob` ein Elternteil von `withContext` Koroutine. Wenn ein Kind eine Exception auslöst, wird sie zur `coroutine` Koroutine weitergeleitet, storniert ihren `Job`, storniert die Kinder und löst eine Exception aus. Die Tatsache, dass `SupervisorJob` ein Elternteil ist, ändert nichts.

{width: 100%}
![](withContextSupervisorJob.png)

### Warten

Also, wir wissen, wie man die Exceptionweiterleitung stoppt, aber manchmal ist das nicht genug. Im Falle einer Exception, bricht der `async` Koroutine-Builder seine Elternkoroutine ab, genau wie `launch` und andere Koroutine-Builder, die eine Relation zu ihren Eltern haben. Aber was, wenn dieser Prozess stillgelegt wird (zum Beispiel mit `SupervisorJob` oder `supervisorScope`) und `warten` aufgerufen wird? Schauen wir uns das folgende Beispiel an:

{crop-start: 3, crop-end: 25}
```kotlin
import kotlinx.coroutines.*

//sampleStart
class MyException : Throwable()

suspend fun main() = supervisorScope {
  val str1 = async<String> {
      delay(1000)
      throw MyException()
  }

  val str2 = async {
      delay(2000)
      "Text2"
  }

  try {
      println(str1.await())
  } catch (e: MyException) {
      println(e)
  }

  println(str2.await())
}
// MyException
// Text2
//sampleEnd
```


Wir haben keinen Wert zurückzugeben, da die Coroutine mit einer Exception beendet wurde, stattdessen wird die `MyException` Exception von `await` geworfen. Deshalb wird `MyException` gedruckt. Die andere `async` wird ununterbrochen ausgeführt, weil wir `supervisorScope` verwenden.

### CancellationException propagiert nicht zur übergeordneten Einheit

Wenn eine Exception eine Unterklasse von `CancellationException` ist, wird sie nicht zur übergeordneten Einheit propagiert. Sie führt nur zur Stornierung der aktuellen Coroutine. `CancellationException` ist eine offene Klasse, daher kann sie durch unsere eigenen Klassen oder Objekte erweitert werden.

{crop-start: 2}

```kotlin
import kotlinx.coroutines.*

object MyNonPropagatingException : CancellationException()

suspend fun main(): Unit = coroutineScope {
  launch { // 1
      launch { // 2
          delay(2000)
          println("Will not be printed")
      }
      throw MyNonPropagatingException // 3
  }
  launch { // 4
      delay(2000)
      println("Will be printed")
  }
}
// (2 sec)
// Will be printed
```


Im obigen Ausschnitt starten wir zwei Coroutinen mit Erstellern an den Positionen 1 und 4. An Punkt 3 werfen wir eine `MyNonPropagatingException` Ausnahme, die ein Subtyp von `CancellationException` ist. Diese Ausnahme wird von `launch` (gestartet bei 1) eingefangen. Dieser Ersteller storniert sich selbst und anschließend auch seine Kinder, nämlich den Ersteller, der bei 2 definiert ist. Das bei 4 gestartete `launch` ist nicht betroffen, daher gibt es "Wird gedruckt" nach 2 Sekunden aus.

### Coroutine-Ausnahmehandler

Bei der Behandlung von Ausnahmen ist es manchmal nützlich, ein Standardverhalten für alle zu definieren. Hier kommt der `CoroutineExceptionHandler` Kontext ins Spiel. Er stoppt nicht die Ausbreitung der Ausnahme, kann aber verwendet werden, um zu definieren, was im Falle einer Ausnahme geschehen sollte (standardmäßig gibt er den Ausnahmestapel aus).

{crop-start: 3, crop-end: 22}
```kotlin
import kotlinx.coroutines.*

//sampleStart
fun main(): Unit = runBlocking {
  val handler =
      CoroutineExceptionHandler { ctx, exception ->
          println("Caught $exception")
      }
  val scope = CoroutineScope(SupervisorJob() + handler)
  scope.launch {
      delay(1000)
      throw Error("Some error")
  }

  scope.launch {
      delay(2000)
      println("Will be printed")
  }

  delay(3000)
}
// Caught java.lang.Error: Some error
// Will be printed
//sampleEnd
```


Dieser Kontext ist auf vielen Plattformen nützlich, um eine Standardmethode zur Ausnahmebehandlung hinzuzufügen. Bei Android informiert er oft den Benutzer über ein Problem, indem er einen Dialog oder eine Fehlermeldung anzeigt.

### Zusammenfassung

Die Ausnahmebehandlung ist ein wichtiger Teil der kotlinx.coroutines-Bibliothek. Mit der Zeit werden wir zwangsläufig zu diesen Themen zurückkehren. Ich hoffe, dass Sie jetzt verstehen, wie Ausnahmen von Unterprozess zu Hauptprozess in den grundlegenden Bausteinen weitergeleitet werden und wie sie gestoppt werden können. Jetzt ist es Zeit für ein lang erwartetes, eng verbundenes Thema. Es ist Zeit, über Funktionen des Coroutine-Bereichs zu sprechen.

