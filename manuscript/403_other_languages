

## Verwendung von Koroutinen in anderen Sprachen

Verschiedene Sprachen haben unterschiedliche Ansätze zur Nebenläufigkeit. In Java ist es üblich, Threads zu starten und zu blocken, aber in JavaScript geschieht dies praktisch nie, da asynchrone Prozesse durch Promises und asynchrone Funktionen repräsentiert werden. Das bedeutet, wir müssen lernen, wie eine API in Kotlin für Entwickler von verschiedenen Plattformen definiert wird, aber wir müssen auch einige der Einschränkungen bestimmter Plattformen verstehen. In diesem Kapitel erfahren wir, welche Kotlin-Funktionen plattformspezifisch sind und wie wir Kotlin-Koroutinen für Plattformspezifität anpassen können, wenn wir eine API für andere beliebte Sprachen bereitstellen möchten.

### Threads auf verschiedenen Plattformen

JavaScript läuft auf einem einzigen Thread[^403_1], so kann man einen Thread nicht zum Schlafen zwingen. Deshalb existiert keine `runBlocking` Funktion in Kotlin/JS. Wenn Sie Ihren Kotlin-Code auf der JS-Plattform ausführen möchten, müssen Sie auf das Blockieren von Threads verzichten. Sie können jedoch `runTest` für Tests verwenden, das in Kotlin/JS ein Promise zurückgibt.

Aus dem gleichen Grund können wir `Dispatchers.IO` in Kotlin/JS nicht verwenden, da `Dispatchers.IO` derzeit spezifisch für Kotlin/JVM ist. Ich sehe das jedoch nicht als Problem an: wir sollten `Dispatchers.IO` verwenden, wenn wir Threads blockieren müssen, und das sollte nur geschehen, wenn wir mit JVM oder nativen APIs interagieren.

### Umwandlung von suspendierenden in nicht-suspendierenden Funktionen

Wir wissen bereits, was zu tun ist, wenn wir blockierende oder Callback-Funktionen in suspendierenden Funktionen umwandeln müssen. Aber was ist, wenn wir das Gegenteil tun und unsere suspendierenden Funktionen in anderen Sprachen als Kotlin verwenden müssen? Wie wir im Kapitel *Koroutinen unter der Haube* gesehen haben, benötigen suspendierende Funktionen `Continuation`, die von Kotlin bereitgestellt werden muss. Wir müssen eine Lösung finden, um dies für andere Sprachen zu überwinden, und da Kotlin eine Multiplattform-Sprache ist, könnten diese anderen Sprachen alle JVM-Sprachen (Java, Scala, Groovy, Clojure, ...), JavaScript-Sprachen (JavaScript, TypeScript) oder native Sprachen (Swift, C++, C, ...) umfassen. Lassen Sie uns die wichtigsten Optionen betrachten, die uns zur Verfügung stehen.

Angenommen, Sie implementieren eine Multiplattform-Bibliothek mit suspendierenden Funktionen, und Sie bieten die folgende Fassade[^403_2] als Ihre API an:


```kotlin
class AnkiConnector(
    // ...
) {
    suspend fun checkConnection(): Boolean = ...
    
    suspend fun getDeckNames(): List<String> = ...
    
    suspend fun pushDeck(
        deckName: String,
        markdown: String
    ): AnkiConnectorResult = ...
    
    suspend fun pullDeck(
        deckName: String,
        currentMarkdown: String
    ): AnkiConnectorResult = ...
}
```



Wir könnten davon ausgehen, dass wir suspendierende Funktionen nicht aus anderen Sprachen als Kotlin aufrufen können, daher müssen Sie eine Alternative angeben, wenn Sie möchten, dass Ihre Bibliothek andere Sprachen unterstützt.

#### Umformung von suspendierenden Funktionen zu blockierenden Funktionen

Auf Kotlin/JVM und Kotlin/Native ist die einfachste Möglichkeit, unsere suspendierenden Funktionen mit `runBlocking` in blockierende Funktionen zu transformieren. Das ist zwar nicht die beste Lösung, aber die einfachste.

Wenn Sie eine Bibliothek definieren, können Sie eine Wrapper-Klasse bereitstellen, die für die Klassen, die Sie in Ihrer API freigeben möchten, blockierende Funktionen definiert.



```kotlin
class AnkiConnectorBlocking {
    private val connector = AnkiConnector(/*...*/)
    
    fun checkConnection(): Boolean = runBlocking {
        connector.checkConnection()
    }
    
    fun getDeckNames(): List<String> = runBlocking {
        connector.getDeckNames()
    }
    
    fun pushDeck(
        deckName: String,
        markdown: String
    ): AnkiConnectorResult = runBlocking {
        connector.pushDeck(deckName, markdown)
    }
    
    fun pullDeck(
        deckName: String,
        currentMarkdown: String
    ): AnkiConnectorResult = runBlocking {
        connector.pullDeck(deckName, currentMarkdown)
    }
}
```


Wenn Sie Klassen erstellen möchten, die in Kotlin und anderen Sprachen verwendet werden könnten, sollten Sie Ihre nicht-suspendierenden Varianten neben den suspendierten Varianten definieren.


```kotlin
class AnkiConnector(
   // ...
) {
   suspend fun checkConnection(): Boolean = ...
    
   fun checkConnectionBlocking(): Boolean = runBlocking {
       connector.checkConnection()
   }
  
   // ...
}
```

Sie können bereits Kotlin-Plugins finden, die solche blockierenden Varianten implizit für ordnungsgemäß annotierte Funktionen generieren. Ein Beispiel für ein solches Plugin ist `kotlin-jvm-blocking-bridge`.

```kotlin
class AnkiConnector(
   // ...
) {
   @JvmBlockingBridge
   suspend fun checkConnection(): Boolean = ...
}
```

```java
// Java
class JavaTest {
    public static void main(String[] args) {
        AnkiConnector connector = new AnkiConnector();
        boolean connection = connector.checkConnection();
        // ...
    }
}
```



#### Unterbrechungsfunktionen in Callback-Funktionen umwandeln

Eine weitere beliebte Option besteht darin, Unterbrechungsfunktionen in Callback-Funktionen umzuwandeln. Dafür müssen wir einen Kontext definieren und ihn verwenden, um Coroutinen für jede Funktion zu starten. Im folgenden Beispiel verwende ich `Result` in meinen Callback-Funktionen, was einen Erfolg repräsentiert, wenn die Wrapper-Funktion abgeschlossen ist, oder es stellt ein Scheitern dar, im Falle einer Ausnahme. Ich habe diese Callback-Funktion so gestaltet, dass sie `Cancellable` zurückgibt, das verwendet werden kann, um die Ausführung einer bestimmten Funktion abzubrechen.



```kotlin
class AnkiConnectorCallback {
   private val connector = AnkiConnector(/*...*/)
   private val scope = CoroutineScope(SupervisorJob())
    
   fun checkConnection(
       callback: (Result<Boolean>) -> Unit
   ): Cancellable = toCallback(callback) {
       connector.checkConnection()
   }
    
   fun getDeckNames(
       callback: (Result<List<String>>) -> Unit
   ): Cancellable = toCallback(callback) {
       connector.getDeckNames()
   }
    
   fun pushDeck(
       deckName: String,
       markdown: String,
       callback: (Result<AnkiConnectorResult>) -> Unit
   ): Cancellable = toCallback(callback) {
       connector.pushDeck(deckName, markdown)
   }
    
   fun pullDeck(
       deckName: String,
       currentMarkdown: String,
       callback: (Result<AnkiConnectorResult>) -> Unit
   ): Cancellable = toCallback(callback) {
       connector.pullDeck(deckName, currentMarkdown)
   }
    
   fun <T> toCallback(
       callback: (Result<T>) -> Unit,
       body: suspend () -> T
   ): Cancellable {
       val job = scope.launch {
           try {
               val result = body()
               callback(Result.success(result))
           } catch (t: Throwable) {
               callback(Result.failure(t))
           }
       }
       return Cancellable(job)
   }
    
   class Cancellable(private val job: Job) {
       fun cancel() {
           job.cancel()
       }
   }
}
```


#### Plattformspezifische Optionen

Viele Plattformen haben ihre eigenen Methoden zur Darstellung von Verweisen auf asynchrone Aufgaben. In JavaScript ist dies zum Beispiel ein `Promise`. Um eine aussetzende Funktion zu starten und ihren Rückgabewert als ein `Promise` darzustellen, das in JavaScript verwendet werden kann, um auf diese Aufgabe zu warten, können wir den `promise` Coroutine-Builder verwenden. Dies funktioniert nur in Kotlin/JS.


```kotlin
@JsExport
@JsName("AnkiConnector")
class AnkiConnectorJs {
   private val connector = AnkiConnector(/*...*/)
   private val scope = CoroutineScope(SupervisorJob())
    
   fun checkConnection(): Promise<Boolean> = scope.promise {
       connector.checkConnection()
   }
    
   fun getDeckNames(): Promise<Array<String>> =
       scope.promise {
           connector.getDeckNames().toTypedArray()
       }
  
   fun pushDeck(
       deckName: String,
       markdown: String
   ): Promise<AnkiConnectorResult> = scope.promise {
       connector.pushDeck(deckName, markdown)
   }
    
   fun pullDeck(
       deckName: String,
       currentMarkdown: String
   ): Promise<AnkiConnectorResult> = scope.promise {
       connector.pullDeck(deckName, currentMarkdown)
   }
}
```

In Kotlin/JVM möchten wir vielleicht eine `CompletableFuture` aus der Java-Standardbibliothek zurückgeben. Dafür sollten wir den `future` Coroutine Builder verwenden.

```kotlin
class AnkiConnectorFuture {
    private val connector = AnkiConnector(/*...*/)
    private val scope = CoroutineScope(SupervisorJob())
    
    fun checkConnection(): CompletableFuture<Boolean> =
        scope.future {
            connector.checkConnection()
        }
    
    fun getDeckNames(): CompletableFuture<List<String>> =
        scope.future {
            connector.getDeckNames()
        }
    
    fun pushDeck(
        deckName: String,
        markdown: String
    ): CompletableFuture<AnkiConnectorResult> = scope.future {
        connector.pushDeck(deckName, markdown)
    }
    
    fun pullDeck(
        deckName: String,
        currentMarkdown: String
    ): CompletableFuture<AnkiConnectorResult> = scope.future {
        connector.pullDeck(deckName, currentMarkdown)
    }
}
```


Wir könnten unsere Funktionen auch in Objekte aus externen Bibliotheken, wie RxJava, das auf der JVM sehr beliebt ist, umwandeln. Um suspendierende Funktionen in RxJava-Objekte wie `Single` oder `Observable` umzuwandeln, bietet Kotlin einen Satz von Abhängigkeiten an. Zum Beispiel zur Umwandlung einer suspendierenden Funktion in ein `Single` von RxJava 3.x können wir die `kotlinx-coroutines-rx3` Abhängigkeit und die `rxSingle` Funktion verwenden.


```kotlin
class AnkiConnectorBlocking {
    private val connector = AnkiConnector(/*...*/)
    
    fun checkConnection(): Single<Boolean> = rxSingle {
        connector.checkConnection()
    }
    
    fun getDeckNames(): Single<List<String>> = rxSingle {
        connector.getDeckNames()
    }
    
    fun pushDeck(
        deckName: String,
        markdown: String
    ): Single<AnkiConnectorResult> = rxSingle {
        connector.pushDeck(deckName, markdown)
    }
    
    fun pullDeck(
        deckName: String,
        currentMarkdown: String
    ): Single<AnkiConnectorResult> = rxSingle {
        connector.pullDeck(deckName, currentMarkdown)
    }
}
```


### Aufrufen von suspendierenden Funktionen aus anderen Sprachen

In einigen der Projekte, an denen ich gearbeitet habe, haben frühere Entwickler beschlossen, Tests mit dem Spock-Framework und Groovy zu schreiben, aber dies wurde problematisch, als wir begannen, Kotlin Coroutines in diesen Projekten zu verwenden. Wir konnten nicht vermeiden, suspendierende Funktionen in Unit-Tests aufzurufen, da dies oft die Funktionen waren, die wir getestet haben. Es war auch zu schwierig, all diese Tests von Groovy nach Kotlin zu migrieren.

Die einfachste Lösung für dieses Problem besteht darin, `runBlocking` zu verwenden, um eine Kontinuität in der anderen Sprache, wie Java, zu konstruieren. Dies kann auf folgende Weise gemacht werden.


```java
// Java
public class MainJava {
   public static void main(String[] args) {
       AnkiConnector connector = new AnkiConnector();
       boolean connected;
       try {
           connected = BuildersKt.runBlocking(
                   EmptyCoroutineContext.INSTANCE,
                   (s, c) -> connector.checkConnection(c)
           );
       } catch (InterruptedException e) {
           throw new RuntimeException(e);
       }
       // ...
   }
}
```


Drei Dinge machen diese Funktion schwer zu benutzen:
1. `BuildersKt` ist möglicherweise nicht der beste Name.
2. Wir müssen einen Kontext angeben, obwohl `EmptyCoroutineContext` in unserem Fall ausreichend ist.
3. Wir müssen stets mit der `InterruptedException` umgehen, die `runBlocking` auslöst, wenn der blockierte Thread unterbrochen wird.

Um diesen Code zu verbessern, können wir eine Wrapper-Funktion implementieren.


```java
// Java
class SuspendUtils {
   public static <T> T runBlocking(
       Function<Continuation<? super T>, T> func
   ) {
       try {
           return BuildersKt.runBlocking(
               EmptyCoroutineContext.INSTANCE,
               (s, c) -> func.apply(c)
           );
       } catch (InterruptedException e) {
           throw new RuntimeException(e);
       }
   }
}

public class MainJava {
   public static void main(String[] args) {
       AnkiConnector connector = new AnkiConnector();
       boolean connected = SuspendUtils.runBlocking((c) ->
           connector.checkConnection(c)
       );
       // ...
   }
}
```


### Flow und Reactive Streams

Wir haben dieses Thema bereits im Kapitel *Flow-Erstellung* besprochen. Wenn Sie jedoch `Flow` in Bibliotheken, die reactive Streams repräsentieren (wie [Reactor](https://projectreactor.io/), [RxJava 2.x.](https://github.com/ReactiveX/RxJava) oder [RxJava 3.x](https://github.com/ReactiveX/RxJava)), umwandeln müssen, können Sie einfache Konvertierungsfunktionen verwenden.

Alle Objekte wie `Flux`, `Flowable` oder `Observable` implementieren die `Publisher` Schnittstelle aus der Java-Standardbibliothek. Diese kann mit der Funktion `asFlow` aus der `kotlinx-coroutines-reactive` Bibliothek in `Flow` umgewandelt werden.


```kotlin
suspend fun main() = coroutineScope {
   Flux.range(1, 5).asFlow()
       .collect { print(it) } // 12345
   Flowable.range(1, 5).asFlow()
       .collect { print(it) } // 12345
   Observable.range(1, 5).asFlow()
       .collect { print(it) } // 12345
}
```


Um in die andere Richtung zu konvertieren, benötigen Sie spezifischere Bibliotheken. Mit `kotlinx-coroutines-reactor` können Sie `Flow` in `Flux` umwandeln. Mit `kotlinx-coroutines-rx3` (oder `kotlinx-coroutines-rx2`) können Sie `Flow` in `Flowable` oder `Observable` umwandeln.


```kotlin
suspend fun main(): Unit = coroutineScope {
    val flow = flowOf(1, 2, 3, 4, 5)
    
    flow.asFlux()
        .doOnNext { print(it) } // 12345
        .subscribe()
    
    flow.asFlowable()
        .subscribe { print(it) } // 12345
    
    flow.asObservable()
        .subscribe { print(it) } // 12345
}
```


Sie können auch einfache Funktionen definieren, um einen Flow auf der JVM zu überwachen oder den aktuellen Thread anzuhalten, bis ein Flow abgeschlossen ist.


```kotlin
// Kotlin
object FlowUtils {
   private val scope = CoroutineScope(SupervisorJob())
    
   @JvmStatic
   @JvmOverloads
   fun <T> observe(
       flow: Flow<T>,
       onNext: OnNext<T>? = null,
       onError: OnError? = null,
       onCompletion: OnCompletion? = null,
   ) {
       scope.launch {
           flow.onCompletion { onCompletion?.handle() }
               .onEach { onNext?.handle(it) }
               .catch { onError?.handle(it) }
               .collect()
       }
   }
    
   @JvmStatic
   @JvmOverloads
   fun <T> observeBlocking(
       flow: Flow<T>,
       onNext: OnNext<T>? = null,
       onError: OnError? = null,
       onCompletion: OnCompletion? = null,
   ) = runBlocking {
       flow.onCompletion { onCompletion?.handle() }
           .onEach { onNext?.handle(it) }
           .catch { onError?.handle(it) }
           .collect()
   }
    
   fun interface OnNext<T> {
       fun handle(value: T)
   }
    
   fun interface OnError {
       fun handle(value: Throwable)
   }
    
   fun interface OnCompletion {
       fun handle()
   }
}

class FlowTest {
   fun test(): Flow<String> = flow {
       emit("A")
       delay(1000)
       emit("B")
   }
}
```

```java
// Java
public class Main {
    public static void main(String[] args) {
        FlowTest obj = new FlowTest();
        FlowUtils.observeBlocking(
                obj.test(),
                System.out::println
        );
    }
}
// A
// (1 sec)
// B
```


Ich sehe nicht viel Nutzen darin, `Flow` in `Promise` oder `CompletableFuture` zu transformieren. `Flow` kann viele Werte repräsentieren, aber `Promise` oder `CompletableFuture` kann nur einen repräsentieren.

### Zusammenfassung

In diesem Abschnitt haben wir einige Einschränkungen von Coroutinen auf verschiedenen Plattformen kennengelernt. Wir haben auch gelernt, wie man Kotlin Coroutine-Konzepte so transformiert, dass sie in anderen Sprachen verwendet werden können. Die zwei grundlegendsten Methoden sind das Transformieren von suspendierten Funktionen in entweder blockierende oder Callback-Funktionen. Flow kann in ein Objekt aus einer Bibliothek wie RxJava oder Reactor transformiert werden, oder es kann in einer Klasse gekapselt werden, die in anderen Sprachen verwendet werden kann. Es gibt jedoch auch viele plattformspezifische Optionen. Ich hoffe, Sie finden dieses Kapitel nützlich, wenn Sie gezwungen sind, Kotlin Coroutinen aus anderen Sprachen zu verwenden.

[^403_1]: Diese Aussage ist nicht völlig wahr, da auf npm mehrere Threads verwendet werden können und es die Web Workers API für Browser gibt, um Prozesse auf unterschiedlichen Threads zu starten; JavaScript kann jedoch generell als Single-Threaded betrachtet werden.
[^403_2]: Dies ist eine vereinfachte Fassade aus meinem AnkiMarkdown-Projekt.
