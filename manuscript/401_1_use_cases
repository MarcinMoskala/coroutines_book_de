
### Data/Adapters Layer

Ich beginne mit der Vorstellung typischer Use Cases für Kotlin Coroutines aus dem Data/Adapters Layer, in dem wir Repository, Anbieter, Adapter, Datenquellen usw. umsetzen. Dieser Layer ist heutzutage relativ einfach, da viele populäre JVM-Libraries Kotlin Coroutines entweder out of the box unterstützen oder mit einigen zusätzlichen Abhängigkeiten.

Als Beispiel könnten wir Retrofit verwenden, eine populäre Library für Netzwerkanfragen. Sie bietet eine out-of-the-box Unterstützung für suspendierende Funktionen (suspending functions). Es genügt, den `suspend` Modifikator hinzuzufügen, um seine Request-Definitionsfunktionen suspendierend statt blockierend zu gestalten.


```kotlin
// Retrofit
class GithubApi {
    @GET("orgs/{organization}/repos?per_page=100")
    suspend fun getOrganizationRepos(
        @Path("organization") organization: String
    ): List<Repo>
}
```


Ein weiteres gutes Beispiel ist Room, eine beliebte Bibliothek zur Kommunikation mit SQLite-Datenbanken auf Android. Sie unterstützt sowohl den `suspend`-Modifizierer, um seine Funktionen unterbrechend zu gestalten, als auch `Flow` zur Überwachung von Änderungen im Tabellendaten.


```kotlin
// Room
@Dao
interface LocationDao {
    @Insert
    suspend fun insertLocation(location: Location)

    @Query("DELETE FROM location_table")
    suspend fun deleteLocations()

    @Query("SELECT * FROM location_table ORDER BY time")
    fun observeLocations(): Flow<List<Location>>
}
```



#### Callback-Funktionen

Wenn Sie eine Bibliothek verwenden, die keine Kotlin Coroutines unterstützt, sondern stattdessen Callback-Funktionen verwendet, verwandeln Sie diese in "Suspending Functions" mit Hilfe von `suspendCancellableCoroutine`[^401_0]. Wenn eine Callback-Funktion aufgerufen wird, sollte die Coroutine mit der `resume` Methode auf dem `Continuation` Objekt fortgesetzt werden. Wenn diese Callback-Funktion abbrechbar ist, sollte sie im `invokeOnCancellation` Lambda-Ausdruck abgebrochen werden[^401_1].



```kotlin
suspend fun requestNews(): News {
    return suspendCancellableCoroutine<News> { cont ->
        val call = requestNewsApi { news ->
            cont.resume(news)
        }
        cont.invokeOnCancellation {
            call.cancel()
        }
    }
}
```


Rückruffunktionen, die es uns ermöglichen, separate Funktionen für Erfolg und Fehler einzustellen, können auf verschiedenen Wegen implementiert werden. Wir könnten Rückruffunktionen umschließen und `Result` zurückgeben, und dann entweder unsere Coroutine mit `Result.success` oder mit `Result.failure` fortsetzen.


```kotlin
suspend fun requestNews(): Result<News> {
    return suspendCancellableCoroutine<News> { cont ->
        val call = requestNewsApi(
            onSuccess = { news -> 
                cont.resume(Result.success(news))
            },
            onError = { e -> 
                cont.resume(Result.failure(e)) 
            }
        )
        cont.invokeOnCancellation {
            call.cancel()
        }
    }
}
```

Eine andere Option besteht darin, einen möglicherweise null Wert zurückzugeben und unsere Coroutine entweder mit den von der Coroutine zurückgegebenen Daten oder mit dem Wert "`null`" fortzusetzen.

```kotlin
suspend fun requestNews(): News? {
    return suspendCancellableCoroutine<News> { cont ->
        val call = requestNewsApi(
            onSuccess = { news -> cont.resume(news) },
            onError = { e -> cont.resume(null) }
        )
        cont.invokeOnCancellation {
            call.cancel()
        }
    }
}
```


Die letzte beliebte Option besteht darin, im Falle eines Erfolgs der Rückruffunktion mit einem Ergebnis fortzusetzen oder im Falle eines Fehlers mit einer Ausnahme fortzusetzen. Im letzteren Fall wird die Ausnahme vom Anhaltepunkt[^401_2] ausgelöst.


```kotlin
suspend fun requestNews(): News {
    return suspendCancellableCoroutine<News> { cont ->
        val call = requestNewsApi(
            onSuccess = { news -> cont.resume(news) },
            onError = { e -> cont.resumeWithException(e) }
        )
        cont.invokeOnCancellation {
            call.cancel()
        }
    }
}
```



#### Blockierende Funktionen

Eine weitere übliche Situation ist, wenn eine von Ihnen verwendete Bibliothek die Verwendung von blockierenden Funktionen verlangt. Sie sollten niemals blockierende Funktionen auf regulären suspendierenden Funktionen aufrufen. In Kotlin Coroutines, verwenden wir Threads mit hoher Genauigkeit, und ihre Blockierung stellt ein großes Problem dar. Wenn wir den Thread von `Dispatchers.Main` auf Android blockieren, friert unsere gesamte Anwendung ein. Wenn wir den Thread von `Dispatchers.Default` blockieren, können wir eine effiziente Prozessornutzung vergessen. Deshalb sollten wir niemals einen blockierenden Aufruf durchführen, ohne zuerst den Dispatcher[^401_3] festzulegen.

Wenn wir einen blockierenden Aufruf durchführen müssen, sollten wir den Dispatcher mit `withContext` spezifizieren. In den meisten Fällen genügt es, `Dispatchers.IO`[^401_4] zu verwenden, wenn wir Repositories in Anwendungen implementieren.



```kotlin
class DiscSaveRepository(
    private val discReader: DiscReader
) : SaveRepository {

    override suspend fun loadSave(name: String): SaveData =
        withContext(Dispatchers.IO) {
            discReader.read("save/$name")
        }
}
```


Allerdings ist es wichtig zu verstehen, dass `Dispatchers.IO` auf 64 Threads begrenzt ist, was auf dem Backend und Android möglicherweise nicht ausreicht. Wenn jede Anfrage einen blockierenden Aufruf machen muss und Sie tausende von Anfragen pro Sekunde haben, könnte die Warteschlange für diese 64 Threads schnell ansteigen. In einer solchen Situation könnten Sie in Erwägung ziehen, `limitedParallelism` auf `Dispatchers.IO` zu verwenden, um einen neuen Dispatcher mit einem unabhängigen Limit zu erstellen, das mehr als 64 Threads umfasst[^401_5].


```kotlin
class LibraryGoogleAccountVerifier : GoogleAccountVerifier {
    private val dispatcher = Dispatchers.IO
        .limitedParallelism(100)

    private var verifier =
        GoogleIdTokenVerifier.Builder(..., ...)
    .setAudience(...)
    .build()

    override suspend fun getUserData(
        googleToken: String
    ): GoogleUserData? = withContext(dispatcher) {
        verifier.verify(googleToken)
            ?.payload
            ?.let {
                GoogleUserData(
                    email = it.email,
                    name = it.getString("given_name"),
                    surname = it.getString("family_name"),
                    imageUrl = it.getString("picture"),
                )
            }
    }
}

```


Ein Dispatcher mit einem Limit, das unabhängig von `Dispatchers.IO` ist, sollte immer dann verwendet werden, wenn wir befürchten, dass unsere Funktion von so vielen Coroutinen aufgerufen werden könnte, dass sie eine wesentliche Anzahl von Threads in Anspruch nehmen könnten. In solchen Fällen möchten wir keine Threads von `Dispatchers.IO` blockieren, weil wir nicht wissen, welche Prozesse warten werden, bis unser Prozess abgeschlossen ist.

Bei der Implementierung einer Bibliothek wissen wir oft nicht, wie unsere Funktionen eingesetzt werden, und wir sollten grundsätzlich mit Dispatchern arbeiten, die über unabhängige Thread-Pools verfügen. Wie sollte das Limit für solche Disponenten festgelegt werden? Diese Entscheidung liegt bei Ihnen. Wenn Sie das Limit niedrig setzen, könnten Coroutinen möglicherweise aufeinander warten müssen. Wenn Sie es zu hoch setzen, könnten Sie riskieren, viel Speicher und CPU-Zeit durch die vielen aktiven Threads zu verbrauchen.


```kotlin
class CertificateGenerator {
    private val dispatcher = Dispatchers.IO
        .limitedParallelism(5)

    suspend fun generate(data: CertificateData): UserData =
        withContext(dispatcher) {
            Runtime.getRuntime()
                .exec("generateCertificate " + data.toArgs())
        }
}
```

Wir sollten auch sicherstellen, dass alle CPU-intensiven Vorgänge auf `Dispatchers.Default` laufen, und alle Vorgänge, die die Hauptansicht ändern, auf `Dispatchers.Main.immediate` laufen. Dafür könnte `withContext` auch nützlich sein.

```kotlin
suspend fun calculateModel() =
    withContext(Dispatchers.Default) {
        model.fit(
            dataset = newTrain,
            epochs = 10,
            batchSize = 100,
            verbose = false
        )
    }
```

```kotlin
suspend fun setUserName(name: String) =
    withContext(Dispatchers.Main.immediate) {
        userNameView.text = name
    }
```



#### Beobachten mit Flow

Suspend-Funktionen eignen sich hervorragend zur Darstellung des Prozesses der Erzeugung/Beschaffung eines einzelnen Wertes; aber wenn wir mehr als einen Wert erwarten, sollten wir `Flow` anstatt nutzen. Wir haben bereits ein Beispiel gesehen: In der Room-Bibliothek verwenden wir Suspend-Funktionen, um eine Datenbankoperation durchzuführen, und wir verwenden den `Flow`-Typ, um Änderungen in einer Tabelle zu verfolgen.



```kotlin
// Room
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


Wir haben eine ähnliche Situation, wenn wir Netzwerkanrufe berücksichtigen. Wenn wir einen einzelnen Wert von einer API abrufen, ist es am besten, eine "suspend function" zu verwenden; jedoch, wenn wir einen WebSocket einrichten und auf Nachrichten warten, sollten wir stattdessen `Flow` verwenden. Um einen solchen Flow zu erstellen (falls die von uns verwendete Bibliothek keine Flow-Rückgabe unterstützt), sollten wir `callbackFlow` (oder `channelFlow`) verwenden. Denken Sie daran, Ihren Builder mit `awaitClose`[^401_6] zu beenden.


```kotlin
fun listenMessages(): Flow<List<Message>> = callbackFlow {
    socket.on("NewMessage") { args ->
        trySendBlocking(args.toMessage())
    }
    awaitClose()
}
```

Eine beliebte Verwendung für `Flow` ist die Beobachtung von UI-Ereignissen, wie Button-Klicks oder Textänderungen. 

```kotlin
fun EditText.listenTextChange(): Flow<String> = callbackFlow {
    val watcher = doAfterTextChanged {
        trySendBlocking(it.toString())
    }
    awaitClose { removeTextChangedListener(watcher) }
}
```

Flow kann auch in anderen Callback-Funktionen genutzt werden, und es sollte angewendet werden, wenn diese Callbacks mehrere Werte liefern mögen.

```kotlin
fun flowFrom(api: CallbackBasedApi): Flow<T> = callbackFlow {
    val callback = object : Callback {
        override fun onNextValue(value: T) {
            trySendBlocking(value)
        }
        override fun onApiError(cause: Throwable) {
            cancel(CancellationException("API Error", cause))
        }
        override fun onCompleted() = channel.close()
    }
    api.register(callback)
    awaitClose { api.unregister(callback) }
}
```

Wenn Sie einen bestimmten Dispatcher in einem Flow Builder verwenden müssen, verwenden Sie `flowOn` auf dem erzeugten Flow[^401_7].

```kotlin
fun fibonacciFlow(): Flow<BigDecimal> = flow {
    var a = BigDecimal.ZERO
    var b = BigDecimal.ONE
    emit(a)
    emit(b)
    while (true) {
        val temp = a
        a = b
        b += temp
        emit(b)
    }
}.flowOn(Dispatchers.Default)

fun filesContentFlow(path: String): Flow<String> =
    channelFlow {
        File(path).takeIf { it.exists() }
            ?.listFiles()
            ?.forEach {
                send(it.readText())
            }
    }.flowOn(Dispatchers.IO)
```

[^401_0]: Für Details, siehe das Kapitel *How does suspension work?*, Abschnitt *Weiterfahren mit einem Wert*.
[^401_1]: Für Details, siehe das Kapitel *Cancellation*, Abschnitt *invokeOnCompletion*.
[^401_2]: Für Details, siehe das Kapitel *How does suspension work?*, Abschnitt *Weiterfahren mit einer Ausnahme*.
[^401_3]: Für Details, siehe das Kapitel *Dispatchers*.
[^401_4]: Für Details, siehe das Kapitel *Dispatchers*, Abschnitt *IO dispatcher*.
[^401_5]: Für Details, siehe das Kapitel *Dispatchers*, Abschnitt *IO dispatcher mit einem individuellen Thread-Pool*.
[^401_6]: Für Details, siehe das Kapitel *Flow building*, Abschnitt *callbackFlow*.
[^401_7]: Für Details, siehe das Kapitel *Flow lifecycle functions*, Abschnitt *flowOn*.
