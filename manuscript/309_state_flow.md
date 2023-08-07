
## SharedFlow und StateFlow

Flow ist typischerweise kalt, daher werden seine Werte bei Bedarf berechnet. Es gibt jedoch Fälle, in denen mehrere Empfänger an einer einzigen Änderungsquelle abonniert sein sollten. Hier kommt SharedFlow ins Spiel, welches konzeptuell einer Mailingliste ähnelt. Wir kennen auch StateFlow, das einem beobachtbaren Wert ähnelt. Wir erklären beide Schritt für Schritt.

### SharedFlow

Beginnen wir mit `MutableSharedFlow`, das einem Broadcast-Kanal ähnelt: Jeder kann Nachrichten senden (ausstrahlen), die von allen Coroutinen empfangen werden, die zuhören (sammeln).

```kotlin
suspend fun main(): Unit = coroutineScope {
    val mutableSharedFlow =
        MutableSharedFlow<String>(replay = 0)
    // or MutableSharedFlow<String>()

    launch {
        mutableSharedFlow.collect {
            println("#1 received $it")
        }
    }
    launch {
        mutableSharedFlow.collect {
            println("#2 received $it")
        }
    }

    delay(1000)
    mutableSharedFlow.emit("Message1")
    mutableSharedFlow.emit("Message2")
}
// (1 sec)
// #1 received Message1
// #2 received Message1
// #1 received Message2
// #2 received Message2
// (program never ends)
```

> Das obige Programm endet nie, da `coroutineScope` auf die mit `launch` gestarteten `coroutines` wartet, die ständig an `MutableSharedFlow` hängen. Offensichtlich kann `MutableSharedFlow` nicht geschlossen werden, daher muss der gesamte Bereich abgebrochen werden, um dieses Problem zu beheben.

`MutableSharedFlow` kann weiterhin Nachrichten senden. Wenn der `replay` Parameter festgelegt wird (standardmäßig ist er auf 0 gesetzt), werden die festgelegten letzten Werte behalten. Wenn eine `coroutine` nun anfängt zu beobachten, erhält sie zuerst diese Werte. Der Cache kann mit `resetReplayCache` zurückgesetzt werden.

```kotlin
suspend fun main(): Unit = coroutineScope {
    val mutableSharedFlow = MutableSharedFlow<String>(
        replay = 2,
    )
    mutableSharedFlow.emit("Message1")
    mutableSharedFlow.emit("Message2")
    mutableSharedFlow.emit("Message3")

    println(mutableSharedFlow.replayCache)
    // [Message2, Message3]

    launch {
        mutableSharedFlow.collect {
            println("#1 received $it")
        }
        // #1 received Message2
        // #1 received Message3
    }

    delay(100)
    mutableSharedFlow.resetReplayCache()
    println(mutableSharedFlow.replayCache) // []
}
```

> `MutableSharedFlow` ist konzeptionell ähnlich wie RxJava Subjects. Wenn der `replay` Parameter auf 0 gesetzt ist, ähnelt es einem `PublishSubject`. Wenn `replay` 1 ist, ähnelt es einem `BehaviorSubject`. Wenn `replay` `Int.MAX_VALUE` ist, ähnelt es einem `ReplaySubject`.

In Kotlin bevorzugen wir eine Unterscheidung zwischen Schnittstellen, die nur zum Empfangen verwendet werden, und solchen, die zur Modifikation verwendet werden. Zum Beispiel haben wir bereits die Unterscheidung zwischen `SendChannel`, `ReceiveChannel` und einfach `Channel` gesehen. Das gleiche Prinzip gilt hier. `MutableSharedFlow` erbt sowohl von `SharedFlow` als auch von `FlowCollector`. Ersteres erbt von `Flow` und dient zur Beobachtung, während `FlowCollector` zum Senden von Werten verwendet wird.

```kotlin
interface MutableSharedFlow<T> :
    SharedFlow<T>, FlowCollector<T> {

    fun tryEmit(value: T): Boolean
    val subscriptionCount: StateFlow<Int>
    fun resetReplayCache()
}

interface SharedFlow<out T> : Flow<T> {
    val replayCache: List<T>
}

interface FlowCollector<in T> {
    suspend fun emit(value: T)
}
```

Diese Schnittstellen werden oft nur dazu verwendet, Funktionen auszugeben, oder nur zum Erfassen.

```kotlin
suspend fun main(): Unit = coroutineScope {
    val mutableSharedFlow = MutableSharedFlow<String>()
    val sharedFlow: SharedFlow<String> = mutableSharedFlow
    val collector: FlowCollector<String> = mutableSharedFlow

    launch {
        mutableSharedFlow.collect {
            println("#1 received $it")
        }
    }
    launch {
        sharedFlow.collect {
            println("#2 received $it")
        }
    }

    delay(1000)
    mutableSharedFlow.emit("Message1")
    collector.emit("Message2")
}
// (1 sec)
// #1 received Message1
// #2 received Message1
// #1 received Message2
// #2 received Message2
```

Hier ist ein Beispiel für die typische Verwendung auf Android:

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


### `shareIn`

Flow wird oft dazu verwendet, Änderungen nachzuverfolgen, wie Benutzeraktionen, Datenbankmodifikationen oder neue Nachrichten. Wir kennen bereits die verschiedenen Wege, auf denen diese Ereignisse verarbeitet und gehandhabt werden können. Wir wissen, wie man mehrere Flows zu einem vereint. Aber was ist, wenn mehrere Klassen an diesen Änderungen interessiert sind und wir möchten einen Flow in mehrere Flows umwandeln? Die Lösung ist `SharedFlow`, und der einfachste Weg, einen `Flow` in einen `SharedFlow` zu verwandeln, ist die Nutzung der Funktion `shareIn`.


```kotlin
suspend fun main(): Unit = coroutineScope {
    val flow = flowOf("A", "B", "C")
        .onEach { delay(1000) }

    val sharedFlow: SharedFlow<String> = flow.shareIn(
        scope = this,
        started = SharingStarted.Eagerly,
        // replay = 0 (default)
    )

    delay(500)

    launch {
        sharedFlow.collect { println("#1 $it") }
    }

    delay(1000)

    launch {
        sharedFlow.collect { println("#2 $it") }
    }

    delay(1000)

    launch {
        sharedFlow.collect { println("#3 $it") }
    }
}
// (1 sec)
// #1 A
// (1 sec)
// #1 B
// #2 B
// (1 sec)
// #1 C
// #2 C
// #3 C
```

Die `shareIn` Funktion erstellt einen `SharedFlow` und sendet Elemente aus ihrem `Flow`. Da wir eine Coroutine starten müssen, um Elemente auf dem Flow zu sammeln, erwartet `shareIn` einen Coroutine-Scope als erstes Argument. Das dritte Argument ist `replay`, welches standardmäßig 0 ist. Das zweite Argument ist interessant: `started` bestimmt, wann das Warten auf Werte beginnen soll, abhängig von der Anzahl der Abonnenten. Die folgenden Optionen werden unterstützt:

* `SharingStarted.Eagerly` - Beginnt sofort mit dem Warten auf Werte und sendet sie an einen Flow. Beachten Sie, dass Sie einige Werte verlieren könnten, wenn Sie einen begrenzten `replay` Wert haben und Ihre Werte veröffentlicht werden, bevor Sie zu abonnieren beginnen (wenn Ihr replay 0 ist, werden Sie alle solche Werte verlieren).

```kotlin
suspend fun main(): Unit = coroutineScope {
    val flow = flowOf("A", "B", "C")

    val sharedFlow: SharedFlow<String> = flow.shareIn(
        scope = this,
        started = SharingStarted.Eagerly,
    )

    delay(100)
    launch {
        sharedFlow.collect { println("#1 $it") }
    }
    print("Done")
}
// (0.1 sec)
// Done
```


* `SharingStarted.Lazily` - startet den Abruf, wenn der erste Abonnent erscheint. Dies garantiert, dass dieser erste Abonnent alle ausgesendeten Werte erhält, während nachfolgenden Abonnenten nur garantiert wird, die neuesten Wiedergabewerte zu erhalten. Der Datenstrom bleibt aktiv, auch wenn alle Abonnenten verschwinden, aber ohne Abonnenten werden nur die neuesten Wiedergabewerte zwischengespeichert.

{crop-start: 6, crop-end: 31}
```kotlin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

//sampleStart
suspend fun main(): Unit = coroutineScope {
    val flow1 = flowOf("A", "B", "C")
    val flow2 = flowOf("D")
        .onEach { delay(1000) }

    val sharedFlow = merge(flow1, flow2).shareIn(
        scope = this,
        started = SharingStarted.Lazily,
    )

    delay(100)
    launch {
        sharedFlow.collect { println("#1 $it") }
    }
    delay(1000)
    launch {
        sharedFlow.collect { println("#2 $it") }
    }
}
// (0.1 sec)
// #1 A
// #1 B
// #1 C
// (1 sec)
// #2 D
// #1 D
//sampleEnd
```


* `WhileSubscribed()` - beginnt den Flow zu überwachen, wenn der erste Abonnent erscheint; es stoppt, wenn der letzte Abonnent verschwindet. Wenn ein neuer Abonnent erscheint, wenn unser `SharedFlow` gestoppt ist, wird es erneut starten. `WhileSubscribed` hat zusätzliche optionale Konfigurationsparameter: `stopTimeoutMillis` (wie lange zu warten, nachdem der letzte Abonnent verschwunden ist, standardmäßig 0) und `replayExpirationMillis` (wie lange die Wiedergabe nach dem Stoppen beibehalten wird, standardmäßig `Long.MAX_VALUE`).


```kotlin
suspend fun main(): Unit = coroutineScope {
    val flow = flowOf("A", "B", "C", "D")
        .onStart { println("Started") }
        .onCompletion { println("Finished") }
        .onEach { delay(1000) }

    val sharedFlow = flow.shareIn(
        scope = this,
        started = SharingStarted.WhileSubscribed(),
    )

    delay(3000)
    launch {
        println("#1 ${sharedFlow.first()}")
    }
    launch {
        println("#2 ${sharedFlow.take(2).toList()}")
    }
    delay(3000)
    launch {
        println("#3 ${sharedFlow.first()}")
    }
}
// (3 sec)
// Started
// (1 sec)
// #1 A
// (1 sec)
// #2 [A, B]
// Finished
// (1 sec)
// Started
// (1 sec)
// #3 A
// Finished
```

* Es ist auch möglich, eine benutzerdefinierte Strategie zu definieren, indem das `SharingStarted` Interface implementiert wird.

Die Verwendung von `shareIn` ist sehr praktisch, wenn mehrere Dienste an den gleichen Änderungen interessiert sind. Nehmen wir an, Sie müssen beobachten, wie gespeicherte Orte sich im Laufe der Zeit ändern. So könnte ein DTO (Data Transfer Object - Datenübertragungsobjekt) auf Android mit der Room-Bibliothek implementiert werden:

```kotlin
@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocation(location: Location)

    @Query("DELETE FROM location_table")
    suspend fun deleteLocations()

    @Query("SELECT * FROM location_table ORDER BY time")
    fun observeLocations(): Flow<List<Location>>
}
```

Das Problem ist, dass es nicht optimal ist, wenn mehrere Dienste auf diese Standorte angewiesen sind und jeder davon die Datenbank separat überwachen muss. Stattdessen könnten wir einen Dienst erstellen, der auf diese Änderungen reagiert und sie in `SharedFlow` einfließt. Das ist der Punkt, an dem wir `shareIn` nutzen werden. Aber wie sollten wir es konfigurieren? Diese Entscheidung müssen Sie selbst treffen. Möchten Sie, dass Ihre Abonnenten sofort die aktuellste Liste der Standorte erhalten? Wenn ja, setzen Sie `replay` auf 1. Wenn Sie nur auf Änderungen reagieren möchten, setzen Sie es auf 0. Und was ist mit `started`? `WhileSubscribed()` scheint das beste Einsatzszenario für diesen Fall zu sein.

```kotlin
class LocationService(
    locationDao: LocationDao,
    scope: CoroutineScope
) {
    private val locations = locationDao.observeLocations()
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
        )

    fun observeLocations(): Flow<List<Location>> = locations
}
```


> Achtung! Erstellen Sie nicht bei jedem Aufruf einen neuen SharedFlow. Erstellen Sie einen und speichern Sie ihn in einer Variable.

### StateFlow

StateFlow ist eine Erweiterung des SharedFlow-Konzepts. Es verhält sich ähnlich wie SharedFlow, wenn der Parameter `replay` auf 1 gesetzt ist. Es speichert stets einen Wert, auf den über das Attribut `value` zugegriffen werden kann.


```kotlin
interface StateFlow<out T> : SharedFlow<T> {
    val value: T
}

interface MutableStateFlow<T> :
    StateFlow<T>, MutableSharedFlow<T> {

    override var value: T

    fun compareAndSet(expect: T, update: T): Boolean
}
```

> Bitte beachten Sie, wie die `value` Property in `MutableStateFlow` überschrieben wird. In Kotlin kann eine `open val` Property mit einer `var` Property überschrieben werden. `val` erlaubt nur das Abrufen eines Wertes (getter), während `var` auch das Setzen eines neuen Wertes (setter) unterstützt.

Der Anfangswert muss an den Konstruktor übergeben werden. Wir greifen auf den Wert zu und setzen ihn mit der `value` Property. Wie Sie sehen können, ist `MutableStateFlow` wie ein beobachtbarer Behälter für einen Wert.

```kotlin
suspend fun main() = coroutineScope {
    val state = MutableStateFlow("A")
    println(state.value) // A
    launch {
        state.collect { println("Value changed to $it") }
        // Value changed to A
    }

    delay(1000)
    state.value = "B" // Value changed to B

    delay(1000)
    launch {
        state.collect { println("and now it is $it") }
        // and now it is B
    }

    delay(1000)
    state.value = "C" // Value changed to C and now it is C
}
```

Auf Android wird StateFlow als moderne Alternative zu LiveData verwendet. Erstens hat es volle Unterstützung für Coroutinen. Zweitens hat es einen Anfangswert, so dass es nicht notwendig ist, dass es null sein kann. Daher wird StateFlow oft in ViewModels verwendet, um deren Zustand darzustellen. Dieser Zustand wird beobachtet, und eine View wird entsprechend dieser Basis angezeigt und aktualisiert.

```kotlin
class LatestNewsViewModel(
    private val newsRepository: NewsRepository
) : ViewModel() {
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


Seien Sie vorsichtig, dass StateFlow 'verschmolzen' ist, sodass langsamere Beobachter eventuell nicht alle 'Zwischenzustände' empfangen könnten. Um alle 'events' zu empfangen, sollten Sie 'SharedFlow' benutzen.

{crop-start: 4}
```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*

suspend fun main(): Unit = coroutineScope {
    val state = MutableStateFlow('X')

    launch {
        for (c in 'A'..'E') {
            delay(300)
            state.value = c
            // or state.emit(c)
        }
    }

    state.collect {
        delay(1000)
        println(it)
    }
}
// X
// C
// E
```


Dieses Verhalten ist so konzipiert. StateFlow repräsentiert den aktuellen Zustand und wir könnten davon ausgehen, dass niemand an einem veralteten Zustand interessiert ist.

### `stateIn`

`stateIn` ist eine Funktion, die `Flow<T>` in `StateFlow<T>` umwandelt. Sie kann nur innerhalb eines Scopes aufgerufen werden, aber es ist eine suspendierende Funktion. Bedenken Sie, dass ein StateFlow immer einen Wert haben muss; wenn Sie also keinen Wert angeben, müssen Sie warten, bis der erste Wert berechnet ist.


```kotlin
suspend fun main() = coroutineScope {
    val flow = flowOf("A", "B", "C")
        .onEach { delay(1000) }
        .onEach { println("Produced $it") }
    val stateFlow: StateFlow<String> = flow.stateIn(this)

    println("Listening")
    println(stateFlow.value)
    stateFlow.collect { println("Received $it") }
}
// (1 sec)
// Produced A
// Listening
// A
// Received A
// (1 sec)
// Produced B
// Received B
// (1 sec)
// Produced C
// Received C
```

Die zweite Variante von `stateIn` ist nicht suspendierend, aber sie erfordert einen Initialwert und einen `gestartet` Modus. Dieser Modus hat die gleichen Optionen wie `shareIn` (wie zuvor erläutert).

```kotlin
suspend fun main() = coroutineScope {
    val flow = flowOf("A", "B")
        .onEach { delay(1000) }
        .onEach { println("Produced $it") }

    val stateFlow: StateFlow<String> = flow.stateIn(
        scope = this,
        started = SharingStarted.Lazily,
        initialValue = "Empty"
    )

    println(stateFlow.value)

    delay(2000)
    stateFlow.collect { println("Received $it") }
}
// Empty
// (2 sec)
// Received Empty
// (1 sec)
// Produced A
// Received A
// (1 sec)
// Produced B
// Received B
```


Wir verwenden typischerweise `stateIn`, wenn wir einen Wert aus einer Änderungsquelle beobachten möchten. In diesem Prozess können diese Änderungen verarbeitet werden und schließlich können sie durch unsere Sichten beobachtet werden.


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

### Zusammenfassung

In diesem Kapitel haben wir über `SharedFlow` und `StateFlow` gelernt, die beide besonders wichtig für Android-Entwickler sind, da sie häufig als Teil des MVVM-Musters verwendet werden. Denken Sie an sie und erwägen Sie, sie zu verwenden, insbesondere wenn Sie View-Modelle in der Android-Entwicklung verwenden.

