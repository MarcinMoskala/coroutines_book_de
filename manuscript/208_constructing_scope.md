## Erstellen eines Coroutine-Scope

In vorherigen Kapiteln haben wir über die Tools gelernt, die benötigt werden, um einen passenden Coroutine-Scope zu erstellen. Jetzt ist es an der Zeit, dieses Wissen zusammenzufassen und zu sehen, wie es typischerweise verwendet wird. Wir werden zwei gängige Beispiele sehen: eines für Android und eines für die Backend-Entwicklung.

### Factory-Funktion für CoroutineScope

`CoroutineScope` ist eine Schnittstelle mit einer einzigen Eigenschaft `coroutineContext`.

```kotlin
interface CoroutineScope {
    val coroutineContext: CoroutineContext
}
```

Daher können wir eine Klasse dazu bringen, diese Schnittstelle zu implementieren und einfach direkt die coroutine builders darin aufrufen.

```kotlin
class SomeClass : CoroutineScope {
   override val coroutineContext: CoroutineContext = Job()

   fun onStart() {
       launch {
           // ...
       }
   }
}
```


Dieser Ansatz ist jedoch nicht sehr beliebt. Einerseits ist er praktisch; andererseits ist es problematisch, dass wir in einer solchen Klasse direkt andere `CoroutineScope` Methoden wie `cancel` oder `ensureActive` aufrufen können. Selbst versehentlich könnte jemand den gesamten Bereich abbrechen, und die Coroutinen werden nicht mehr starten. Stattdessen bevorzugen wir generell, einen Coroutine-Bereich als Objekt in einer Eigenschaft zu halten und ihn zum Aufrufen von Coroutine-Erstellern zu verwenden.


```kotlin
class SomeClass {
   val scope: CoroutineScope = ...

   fun onStart() {
       scope.launch {
           // ...
       }
   }
}
```



Der einfachste Weg, ein Coroutine-Scope-Objekt zu erstellen, besteht darin, die `CoroutineScope` Factory-Funktion[^208_1] zu verwenden. Sie erstellt einen Bereich mit bereitgestelltem Kontext (und einem zusätzlichen `Job` für strukturierte Parallelität, wenn noch kein Job Teil des Kontexts ist).



```kotlin
public fun CoroutineScope(
   context: CoroutineContext
): CoroutineScope =
   ContextScope(
       if (context[Job] != null) context
       else context + Job()
   )

internal class ContextScope(
   context: CoroutineContext
) : CoroutineScope {
   override val coroutineContext: CoroutineContext = context
   override fun toString(): String =
       "CoroutineScope(coroutineContext=$coroutineContext)"
}
```


### Konstruktion eines Scopes in Android

In den meisten Android-Anwendungen nutzen wir eine Architektur, die eine Abstammung von MVC hat: gegenwärtig hauptsächlich MVVM oder MVP. In diesen Architekturen extrahieren wir die Präsentationslogik in Objekte, die wir ViewModels oder Presenters nennen. Hier werden im Allgemeinen Coroutinen gestartet. In anderen Schichten, wie in Use Cases oder Repositories, nutzen wir in der Regel suspendierende Funktionen. Coroutinen könnten auch in Fragments oder Activities gestartet werden. Unabhängig davon, wo auf Android Coroutinen gestartet werden, wird ihre Konstruktion wahrscheinlich gleich sein. Nehmen wir zum Beispiel ein `MainViewModel`: Angenommen, es muss einige Daten in `onCreate` abrufen (das aufgerufen wird, wenn ein Benutzer den Bildschirm betritt). Dieser Datenabruf muss in einer Koroutine stattfinden, die auf einem Scope-Objekt aufgerufen werden muss. Wir werden einen Scope im `BaseViewModel` konstruieren, so dass er nur einmal für alle ViewModels definiert wird. So können wir im `MainViewModel` einfach die `scope`-Eigenschaft aus dem `BaseViewModel` nutzen.


```kotlin
abstract class BaseViewModel : ViewModel() {
    protected val scope = CoroutineScope(TODO())
}

class MainViewModel(
    private val userRepo: UserRepository,
    private val newsRepo: NewsRepository,
) : BaseViewModel {

    fun onCreate() {
        scope.launch {
            val user = userRepo.getUser()
            view.showUserData(user)
        }
        scope.launch {
            val news = newsRepo.getNews()
                .sortedByDescending { it.date }
            view.showNews(news)
        }
    }
}
```


Es ist Zeit, einen Kontext für diesen Geltungsbereich zu definieren. Da viele Funktionen in Android auf dem Haupt-Thread aufgerufen werden müssen, wird `Dispatchers.Main` als der beste Standard-Dispatcher betrachtet. Wir werden ihn im Rahmen unseres Standardkontexts auf Android einsetzen.


```kotlin
abstract class BaseViewModel : ViewModel() {
   protected val scope = CoroutineScope(Dispatchers.Main)
}
```



Zweitens, wir müssen in der Lage sein, unseren Geltungsbereich abzubrechen. Es ist eine gängige Funktion, alle unvollendeten Prozesse zu stornieren, sobald ein Benutzer einen Bildschirm verlässt und `onDestroy` (oder `onCleared` im Fall von ViewModels) aufgerufen wird. Um unseren Geltungsbereich abbrechen zu können, brauchen wir einen `Job` (wir brauchen ihn nicht wirklich hinzuzufügen, denn wenn wir es nicht tun, wird er sowieso von der `CoroutineScope` Funktion hinzugefügt, aber es ist auf diese Weise expliziter). Dann können wir ihn in `onCleared` abbrechen.



```kotlin
abstract class BaseViewModel : ViewModel() {
   protected val scope =
       CoroutineScope(Dispatchers.Main + Job())

   override fun onCleared() {
       scope.cancel()
   }
}
```


Besser noch, es ist üblich, nicht den gesamten Scope, sondern nur seine Unterelemente zu stornieren. Dank dem, solange dieses ViewModel aktiv ist, können neue Coroutinen auf seiner `scope` Eigenschaft starten.


```kotlin
abstract class BaseViewModel : ViewModel() {
   protected val scope =
       CoroutineScope(Dispatchers.Main + Job())

   override fun onCleared() {
       scope.coroutineContext.cancelChildren()
   }
}
```


Wir möchten auch, dass verschiedene Coroutinen, die in diesem Bereich gestartet werden, unabhängig sind. Wenn wir `Job` verwenden, werden der Elternjob und alle seine anderen Kinderjobs abgebrochen, wenn einer der Kinderjobs aufgrund eines Fehlers abgebrochen wird. Selbst wenn beim Laden der Benutzerdaten eine Ausnahme auftrat, sollte uns das nicht davon abhalten, die Nachrichten zu sehen. Um eine solche Unabhängigkeit zu haben, sollten wir `SupervisorJob` anstelle von `Job` verwenden.


```kotlin
abstract class BaseViewModel : ViewModel() {
   protected val scope =
       CoroutineScope(Dispatchers.Main + SupervisorJob())

   override fun onCleared() {
       scope.coroutineContext.cancelChildren()
   }
}
```


Die letzte wichtige Funktion ist die Standardmethode für den Umgang mit nicht abgefangenen Ausnahmefällen. Auf Android legen wir häufig fest, was in unterschiedlichen Ausnahmefällen geschehen soll. Erhalten wir eine `401 Unauthorized` Antwort von einem HTTP-Abruf, so könnten wir den Login-Bildschirm aufrufen. Bei einer `503 Service Unavailable` API-Fehlermeldung wäre es möglich, eine Serverproblemmeldung anzuzeigen. In anderen Fällen zeigen wir möglicherweise Dialoge, Snackbars oder Toasts an. Diese Ausnahmebehandlung definieren wir oft nur einmal, beispielsweise in einer `BaseActivity`, und übergeben sie dann an die View-Modelle (meistens über den Konstruktor). Anschließend können wir `CoroutineExceptionHandler` nutzen, um diese Funktion aufzurufen, falls eine Ausnahme nicht behandelt wurde.


```kotlin
abstract class BaseViewModel(
   private val onError: (Throwable) -> Unit
) : ViewModel() {
   private val exceptionHandler =
       CoroutineExceptionHandler { _, throwable ->
           onError(throwable)
       }

   private val context =
       Dispatchers.Main + SupervisorJob() + exceptionHandler

   protected val scope = CoroutineScope(context)

   override fun onCleared() {
       context.cancelChildren()
   }
}
```

Eine Alternative wäre, Ausnahmen als eine 'live data' Eigenschaft zu speichern, die in der `BaseActivity` oder einem anderen View-Element beobachtet werden.

```kotlin
abstract class BaseViewModel : ViewModel() {
   private val _failure: MutableLiveData<Throwable> =
       MutableLiveData()
   val failure: LiveData<Throwable> = _failure

   private val exceptionHandler =
       CoroutineExceptionHandler { _, throwable ->
           _failure.value = throwable
       }

   private val context =
       Dispatchers.Main + SupervisorJob() + exceptionHandler

   protected val scope = CoroutineScope(context)

   override fun onCleared() {
       context.cancelChildren()
   }
}
```


### `viewModelScope` und `lifecycleScope`

In modernen Android-Anwendungen, anstatt Ihren eigenen Bereich zu definieren, können Sie auch `viewModelScope` (benötigt `androidx.lifecycle:lifecycle-viewmodel-ktx` Version `2.2.0` oder höher) oder `lifecycleScope` (benötigt `androidx.lifecycle:lifecycle-runtime-ktx` Version `2.2.0` oder höher) verwenden. Ihre Funktionsweise entspricht fast der von uns gerade erstellten: Sie nutzen `Dispatchers.Main` und `SupervisorJob`, und sie stornieren die Aufgabe, wenn das ViewModel oder der Lifecycle-Besitzer zerstört werden.


```kotlin
// Implementation from lifecycle-viewmodel-ktx version 2.4.0
public val ViewModel.viewModelScope: CoroutineScope
    get() {
        val scope: CoroutineScope? = this.getTag(JOB_KEY)
        if (scope != null) {
            return scope
        }
        return setTagIfAbsent(
            JOB_KEY,
            CloseableCoroutineScope(
                SupervisorJob() +
                    Dispatchers.Main.immediate
            )
        )
    }

internal class CloseableCoroutineScope(
    context: CoroutineContext
) : Closeable, CoroutineScope {
    override val coroutineContext: CoroutineContext = context

    override fun close() {
        coroutineContext.cancel()
    }
}
```


Die Verwendung von `viewModelScope` und `lifecycleScope` ist praktisch und empfehlenswert, wenn wir keinen speziellen Kontext in unserem Anwendungsbereich benötigen (wie `CoroutineExceptionHandler`). Aus diesem Grund ist diese Auswahl von vielen (vielleicht die meisten) Android-Anwendungen getroffen worden.


```kotlin
class ArticlesListViewModel(
    private val produceArticles: ProduceArticlesUseCase,
) : ViewModel() {

    private val _progressBarVisible =
        MutableStateFlow(false)
    val progressBarVisible: StateFlow<Boolean> =
        _progressBarVisible

    private val _articlesListState =
        MutableStateFlow<ArticlesListState>(Initial)
    val articlesListState: StateFlow<ArticlesListState> =
        _articlesListState

    fun onCreate() {
        viewModelScope.launch {
            _progressBarVisible.value = true
            val articles = produceArticles.produce()
            _articlesListState.value =
                ArticlesLoaded(articles)
            _progressBarVisible.value = false
        }
    }
}
```


### Eine Coroutine im Backend erstellen

Viele Backend-Frameworks haben eingebaute Unterstützung für unterbrechbare Funktionen. Spring Boot erlaubt es, Controller-Funktionen zu unterbrechen. In Ktor sind alle Handler standardmäßig unterbrechbare Funktionen. Daher müssen wir selten selbst einen Gültigkeitsbereich erstellen. Angenommen, wir müssen das doch tun (vielleicht weil wir eine Aufgabe starten müssen oder mit einer älteren Version von Spring arbeiten), was wir höchstwahrscheinlich benötigen, ist:
* ein benutzerdefinierter `Dispatcher` mit einem Pool von Threads (oder `Dispatchers.Default`);
* `SupervisorJob`, um verschiedene Coroutinen unabhängig zu machen;
* wahrscheinlich ein `CoroutineExceptionHandler`, um mit geeigneten Fehlercodes zu antworten, unerreichbare Nachrichten[^208_2] zu senden oder Probleme zu protokollieren.


```kotlin
@Configuration
public class CoroutineScopeConfiguration {

   @Bean
   fun coroutineDispatcher(): CoroutineDispatcher =
       Dispatchers.IO.limitedParallelism(5)

   @Bean
   fun coroutineExceptionHandler() =
       CoroutineExceptionHandler { _, throwable ->
           FirebaseCrashlytics.getInstance()
               .recordException(throwable)
       }

   @Bean
   fun coroutineScope(
       coroutineDispatcher: CoroutineDispatcher,
       coroutineExceptionHandler: CoroutineExceptionHandler,
   ) = CoroutineScope(
       SupervisorJob() +
           coroutineDispatcher +
           coroutineExceptionHandler
   )
}
```


Ein solcher Scope wird meistens durch den Konstruktor in Klassen eingefügt. Dadurch kann der Scope einmal definiert und von vielen Klassen genutzt werden und kann leicht durch einen anderen Scope für Testzwecke ersetzt werden.

### Einen Scope für zusätzliche Aufrufe erstellen

Wie im Abschnitt *Zusätzliche Operationen* des Kapitels *Coroutine-Scope-Funktionen* erklärt, erstellen wir häufig Scopes für den Start zusätzlicher Operationen. Diese Scopes werden dann typischerweise als Argumente zu Funktionen oder dem Konstruktor übergeben. Wenn wir diese Scopes nur für suspendierte Aufrufe nutzen wollen, reicht ein `SupervisorScope` aus.


```kotlin
val analyticsScope = CoroutineScope(SupervisorJob())
```


Alle Ausnahmen werden nur in Protokollen angezeigt, also, wenn Sie diese an ein Überwachungssystem senden möchten, benutzen Sie `CoroutineExceptionHandler`.


```kotlin
private val exceptionHandler =
   CoroutineExceptionHandler { _, throwable ->
       FirebaseCrashlytics.getInstance()
           .recordException(throwable)
   }

val analyticsScope = CoroutineScope(
   SupervisorJob() + exceptionHandler
)
```



Eine weitere übliche Anpassung ist die Festlegung eines anderen Dispatchers. Nutzen Sie zum Beispiel `Dispatchers.IO`, wenn es in diesem Geltungsbereich zu blockierenden Aufrufen kommen könnte, oder verwenden Sie `Dispatchers.Main`, wenn Sie eventuell die Hauptansicht auf Android ändern wollen (wenn wir `Dispatchers.Main` festlegen, wird das Testen auf Android einfacher).



```kotlin
val analyticsScope = CoroutineScope(
   SupervisorJob() + Dispatchers.IO
)
```


### Zusammenfassung

Ich hoffe, dass Sie nach diesem Kapitel wissen, wie man Geltungsbereiche in den meisten typischen Situationen konstruiert. Dies ist wichtig, wenn man Coroutines in realen Projekten verwendet. Das ist genug für viele kleine und einfache Anwendungen, aber für die ernsthafteren müssen wir noch zwei weitere Themen behandeln: richtige Synchronisation und Testen.

[^208_1]: Eine Funktion, die wie ein Konstruktor aussieht, wird als *unechter Konstruktor* oder *künstlicher Konstruktor* bezeichnet. Dieses Muster wird in Effective Kotlin *Punkt 33: Ziehen Sie Fabrikfunktionen Konstruktoren vor* erklärt.
[^208_2]: Dies ist ein verbreitetes Muster bei Microservices, das zum Einsatz kommt, wenn wir einen Softwarebus wie Apache Kafka verwenden.

