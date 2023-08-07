
### Präsentationsschicht/API/UI-Schicht

Die letzte Schicht, die wir besprechen werden, ist die Präsentationsschicht. Hier werden typischerweise Coroutines gestartet. Bei einigen Arten von Anwendungen ist diese Schicht einfacher, da Frameworks wie Spring Boot oder Ktor die gesamte Arbeit für uns erledigen. Zum Beispiel können Sie bei Spring Boot mit Webflux einfach den `suspend` Modifikator vor eine Controller-Funktion setzen und Spring wird diese Funktion in einer Coroutine ausführen.

```kotlin
@Controller
class UserController(
    private val tokenService: TokenService,
    private val userService: UserService,
) {
    @GetMapping("/me")
    suspend fun findUser(
        @PathVariable userId: String,
        @RequestHeader("Authorization") authorization: String
    ): UserJson {
        val userId = tokenService.readUserId(authorization)
        val user = userService.findUserById(userId)
        return user.toJson()
    }
}
```

Ähnliche Unterstützung wird von anderen Bibliotheken bereitgestellt. Auf Android verwenden wir Work Manager, um Aufgaben zu planen. Wir können die `CoroutineWorker` Klasse nutzen und ihre `doWork` Methode implementieren, um festzulegen, was von einer Aufgabe ausgeführt werden soll. Diese Methode ist eine suspend Funktion, also wird sie von der Bibliothek in einer Coroutine gestartet, daher müssen wir dies nicht selbst tun.

```kotlin
class CoroutineDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val data = downloadSynchronously()
        saveData(data)
        return Result.success()
    }
}
```


Jedoch, in einigen anderen Situationen brauchen wir zu Coroutines selbst starten. Für das verwenden wir typischerweise `launch` auf einem Scope-Objekt. Auf Android, dank `lifecycle-viewmodel-ktx`, können wir in den meisten Fällen `viewModelScope` oder `lifecycleScope` benutzen.


```kotlin
class UserProfileViewModel(
    private val loadProfileUseCase: LoadProfileUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
) {
    private val userProfile =
        MutableSharedFlow<UserProfileData>()

    val userName: Flow<String> = userProfile
        .map { it.name }
    val userSurname: Flow<String> = userProfile
        .map { it.surname }
    // ...

    fun onCreate() {
        viewModelScope.launch {
            val userProfileData =
                loadProfileUseCase.execute()
            userProfile.value = userProfileData
            // ...
        }
    }

    fun onNameChanged(newName: String) {
        viewModelScope.launch {
            val newProfile = userProfile.copy(name = newName)
            userProfile.value = newProfile
            updateProfileUseCase.execute(newProfile)
        }
    }
}
```



#### Erstellung eines benutzerdefinierten Scopes

Wenn Sie keine Bibliothek oder Klasse zur Verfügung haben, die in der Lage ist, eine Coroutine zu starten oder einen Scope zu erstellen, dann möglicherweise müssen Sie einen eigenen Scope erstellen und ihn nutzen, um eine Coroutine damit zu starten.



```kotlin
class NotificationsSender(
    private val client: NotificationsClient,
    private val notificationScope: CoroutineScope,
) {
    fun sendNotifications(
        notifications: List<Notification>
    ) {
        for (n in notifications) {
            notificationScope.launch {
                client.send(n)
            }
        }
    }
}
```

```kotlin
class LatestNewsViewModel(
    private val newsRepository: NewsRepository
) : BaseViewModel() {
    private val _uiState =
        MutableStateFlow<NewsState>(LoadingNews)
    val uiState: StateFlow<NewsState> = _uiState

    fun onCreate() {
        scope.launch {
            _uiState.value =
                NewsLoaded(newsRepository.getNews())
        }
    }
}
```


Wir definieren einen benutzerdefinierten Coroutine-Bereich mit der `CoroutineScope`-Funktion[^401_16]. Innerhalb davon ist es üblich, `SupervisorJob`[^401_17] zu verwenden.


```kotlin
val analyticsScope = CoroutineScope(SupervisorJob())
```



Innerhalb einer Scope-Definition könnten wir einen dispatcher oder einen exception handler[^401_18] festlegen. Scope-Objekte können auch abgebrochen werden. Tatsächlich werden auf Android die meisten Scopes entweder abgebrochen oder können ihre Kinder unter bestimmten Bedingungen abbrechen. Die Frage "Welchen Scope sollte ich verwenden, um diesen Prozess auszuführen?" kann oft vereinfacht werden zu "Unter welchen Bedingungen sollte dieser Prozess abgebrochen werden?". View-Modelle brechen ihre Scopes ab, wenn sie zerstört werden. WorkManager brechen Scopes ab, wenn die zugehörigen Aufgaben abgebrochen werden.



```kotlin
// Android example with cancellation and exception handler
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

```kotlin
// Spring example with custom exception handler
@Configuration
class CoroutineScopeConfiguration {

    @Bean
    fun coroutineDispatcher(): CoroutineDispatcher =
        Dispatchers.Default

    @Bean
    fun exceptionHandler(): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            FirebaseCrashlytics.getInstance()
                .recordException(throwable)
        }

    @Bean
    fun coroutineScope(
        coroutineDispatcher: CoroutineDispatcher,
        exceptionHandler: CoroutineExceptionHandler,
    ) = CoroutineScope(
        SupervisorJob() +
            coroutineDispatcher +
            exceptionHandler
    )
}
```



#### Nutzung von runBlocking

Anstatt Coroutinen auf einem Scope-Objekt zu starten, können wir auch die Funktion `runBlocking` verwenden, die eine Koroutine startet und den aktuellen Thread blockiert, bis diese Koroutine beendet ist. Daher sollte `runBlocking` nur verwendet werden, wenn wir einen Thread blockieren wollen. Die zwei häufigsten Gründe für die Verwendung sind:
1. Um die `main` Funktion einzuschließen. Dies ist eine korrekte Verwendung von `runBlocking`, da wir den Thread blockieren müssen, bis die von `runBlocking` gestartete Koroutine beendet ist.
2. Um Testfunktionen einzuschließen. In diesem Fall müssen wir auch den Test-Thread blockieren, so dass der Test nicht beendet wird, bis die Koroutine fertig ist.



```kotlin
fun main() = runBlocking {
    // ...
}

class SomeTests {
    @Test
    fun someTest() = runBlocking {
        // ...
    }
}
```


Beide diese Fälle haben modernere Alternativen. Wir können die Hauptfunktion mit `coroutineScope` oder `runTest` in Tests aussetzen. Das heißt jedoch nicht, dass wir `runBlocking` vermeiden sollten, in einigen Fällen könnte es unseren Anforderungen genügen.


```kotlin
suspend fun main() = coroutineScope {
    // ...
}

class SomeTests {
    @Test
    fun someTest() = runTest {
        // ...
    }
}
```


In anderen Situationen, sollten wir `runBlocking` vermeiden. Bedenke, dass `runBlocking` den aktuellen Thread blockiert, was in Kotlin Coroutines vermieden werden sollte. Verwende `runBlocking` nur, wenn du den aktuellen Thread absichtlich blockieren möchtest.


```kotlin
class NotificationsSender(
    private val client: NotificationsClient,
    private val notificationScope: CoroutineScope,
) {
    @Measure
    fun sendNotifications(notifications: List<Notification>){
        val jobs = notifications.map { notification ->
            scope.launch {
                client.send(notification)
            }
        }
        // We block thread here until all notifications are
        // sent to make function execution measurement
        // give us correct execution time
        runBlocking { jobs.joinAll() }
    }
}
```



#### Arbeiten mit Flow

Wenn wir mit Flows arbeiten, behandeln wir oft Änderungen innerhalb von `onEach`, wir starten unseren Flow mit `launchIn` in einer anderen Coroutine, wir rufen eine Aktion auf, wenn der Flow mit `onStart` startet, wir rufen eine Aktion auf, wenn der Flow mit `onCompletion` endet, und wir fangen Ausnahmen mit `catch`. Wenn wir alle Ausnahmen, die in einem Flow auftreten könnten, fangen wollen, setzen wir `catch` an der letzten Position[^401_19].



```kotlin
fun updateNews() {
    newsFlow()
        .onStart { showProgressBar() }
        .onCompletion { hideProgressBar() }
        .onEach { view.showNews(it) }
        .catch { view.handleError(it) }
        .launchIn(viewModelScope)
}
```



Auf Android ist es beliebt, den Zustand unserer Anwendung in Attributen vom Typ `MutableStateFlow` innerhalb von ViewModel-Klassen[^401_20] zu repräsentieren. Diese Attribute werden von coroutines beobachtet, die die Ansicht je nach ihren Änderungen aktualisieren.



```kotlin
class NewsViewModel : BaseViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _news = MutableStateFlow(emptyList<News>())
    val news: StateFlow<List<News>> = _news

    fun onCreate() {
        newsFlow()
            .onStart { _loading.value = true }
            .onCompletion { _loading.value = false }
            .onEach { _news.value = it }
            .catch { _failure.value = it }
            .launchIn(viewModelScope)
    }
}

class LatestNewsActivity : AppCompatActivity() {
    @Inject
    val newsViewModel: NewsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        // ...
        launchOnStarted {
            newsViewModel.loading.collect {
                progressBar.visbility =
                    if (it) View.VISIBLE else View.GONE
            }
        }
        launchOnStarted {
            newsViewModel.news.collect {
                newsList.adapter = NewsAdapter(it)
            }
        }
    }
}
```


Wenn eine Eigenschaft, die einen Zustand repräsentiert, nur von einem einzigen Flow abhängt, könnten wir die `stateIn` Methode verwenden. Abhängig vom `started` Parameter, wird dieser Flow entweder sofort (wenn diese Klasse initialisiert wird), bei Bedarf (wenn die erste Coroutine damit beginnt, diesen zu sammeln), oder während der Abonnementzeit gestartet[^401_21].


```kotlin
class NewsViewModel : BaseViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _news = MutableStateFlow(emptyList<News>())
    val newsState: StateFlow<List<News>> = newsFlow()
        .onStart { _loading.value = true }
        .onCompletion { _loading.value = false }
        .catch { _failure.value = it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList(),
        )
}
```

```kotlin
class LocationsViewModel(
    locationService: LocationService
) : ViewModel() {

    private val location = locationService.observeLocations()
        .map { it.toLocationsDisplay() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = LocationsDisplay.Loading,
        )

    // ...
}
```


StateFlow sollte genutzt werden, um einen Zustand zu repräsentieren. Um einige Ereignisse oder Updates von mehreren Coroutines beobachten zu lassen, nutzt man SharedFlow.


```kotlin
class UserProfileViewModel {
    private val _userChanges =
        MutableSharedFlow<UserChange>()
    val userChanges: SharedFlow<UserChange> = _userChanges

    fun onCreate() {
        viewModelScope.launch {
            userChanges.collect(::applyUserChange)
        }
    }

    fun onNameChanged(newName: String) {
        // ...
        _userChanges.emit(NameChange(newName))
    }

    fun onPublicKeyChanged(newPublicKey: String) {
        // ...
        _userChanges.emit(PublicKeyChange(newPublicKey))
    }
}
```



[^401_16]: Weitere Einzelheiten finden Sie im Kapitel *Aufbau eines Coroutine-Bereichs*.
[^401_17]: Um mehr über die Funktionsweise von `SupervisorJob` zu erfahren, siehe das Kapitel *Ausnahmebehandlung*.
[^401_18]: Weitere Einzelheiten finden Sie im Kapitel *Aufbau eines Coroutine-Bereichs*. Dispatchers und Exception-Handler werden in den Kapiteln *Dispatchers* und *Ausnahmebehandlung* beschrieben, jeweils.
[^401_19]: Weitere Einzelheiten finden Sie im Kapitel *Flow-Lebenszyklusfunktionen*.
[^401_20]: Weitere Einzelheiten finden Sie im Kapitel *SharedFlow und StateFlow*.
[^401_21]: Alle diese Optionen werden im Kapitel *SharedFlow und StateFlow*, Unterkapiteln *shareIn* und *stateIn* beschrieben.


