
### Domänenschicht

Jetzt besprechen wir die gängigen Anwendungsfälle von Kotlin Coroutines in der Domänenschicht. Hier wird die Geschäftslogik implementiert, daher definieren wir hier Anwendungsfälle, Dienstleistungen, Fassadenklassen, usw. **In dieser Schicht sollten wir vermeiden, auf Coroutine-Scope-Objekten zu operieren und aussetzende Funktionen offenzulegen. Die darunterliegende Schicht (die Präsentationsschicht) ist dafür verantwortlich, Coroutinen auf den Scope-Objekten zu starten; in der Domänenschicht sollten wir Funktionen des Coroutine-Scopes verwenden, um Coroutinen zu starten.**

In der Praxis haben wir in der Domänenschicht hauptsächlich aussetzende Funktionen, die andere aussetzende Funktionen aufrufen.

```kotlin
class NetworkUserRepository(
    private val api: UserApi,
) : UserRepository {
    override suspend fun getUser(): User =
        api.getUser().toDomainUser()
}

class NetworkNewsService(
    private val newsRepo: NewsRepository,
    private val settings: SettingsRepository,
) {
    suspend fun getNews(): List<News> = newsRepo
        .getNews()
        .map { it.toDomainNews() }

    suspend fun getNewsSummary(): List<News> {
        val type = settings.getNewsSummaryType()
        return newsRepo.getNewsSummary(type)
    }
}
```

#### Gleichzeitige Aufrufe

Wenn wir möchten, dass zwei Prozesse parallel ablaufen, sollten wir unseren Funktionskörper mit `coroutineScope` umschließen und den `async` Builder darin verwenden, um jeden Prozess zu starten, der asynchron laufen soll.

```kotlin
suspend fun produceCurrentUser(): User = coroutineScope {
    val profile = async { repo.getProfile() }
    val friends = async { repo.getFriends() }
    User(profile.await(), friends.await())
}
```



Es ist unerlässlich zu verstehen, dass diese Änderung nur diese beiden Prozesse parallel laufen lassen sollte. Alle anderen Mechanismen, wie Stornierung, Ausnahmebehandlung oder Kontextweitergabe, sollten gleich bleiben. Wenn Sie also die Funktionen `produceCurrentUserSeq` und `produceCurrentUserPar` unten betrachten, liegt der einzige wichtige Unterschied darin, dass die erste sequentiell ist, während die zweite zwei parallele Prozesse startet[^401_8].



```kotlin
suspend fun produceCurrentUserSeq(): User {
    val profile = repo.getProfile()
    val friends = repo.getFriends()
    return User(profile, friends)
}

suspend fun produceCurrentUserPar(): User = coroutineScope {
    val profile = async { repo.getProfile() }
    val friends = async { repo.getFriends() }
    User(profile.await(), friends.await())
}
```


Wenn wir zwei asynchrone Prozesse starten und dann auf deren Vollendung warten möchten, können wir das tun, indem wir für jeden von ihnen eine neue Coroutine mittels der `async` Funktion erstellen. Allerdings kann das gleiche Ergebnis auch erreicht werden, wenn wir nur einen Prozess mittels `async` starten und den zweiten im gleichen Coroutine laufen lassen. Die folgende Implementierung von `produceCurrentUserPar` wird praktisch das gleiche Verhalten wie die vorherige aufweisen. Welche Option sollte bevorzugt werden? Ich denke, die meisten Entwickler werden die erste Option bevorzugen, weil die Nutzung von `async` für jeden Prozess, den wir parallel starten möchten, unseren Code verständlicher gestaltet. Andererseits würden einige Entwickler die zweite Option vorziehen, da sie effizienter ist, indem sie weniger Coroutines nutzt und weniger Objekte erzeugt. Es ist Ihre Entscheidung, welche Option Sie bevorzugen.


```kotlin
suspend fun produceCurrentUserPar(): User = coroutineScope {
    val profile = async { repo.getProfile() }
    val friends = repo.getFriends()
    User(profile.await(), friends)
}
```

```kotlin
suspend fun getArticlesForUser(
    userToken: String?,
): List<ArticleJson> = coroutineScope {
    val articles = async { articleRepository.getArticles() }
    val user = userService.getUser(userToken)
    articles.await()
        .filter { canSeeOnList(user, it) }
        .map { toArticleJson(it) }
}
```


Wir können `async` zusammen mit Sammlungsfunktionen verwenden, um für jedes Listenelement einen asynchronen Prozess zu initiieren. In solchen Fällen ist es beste Praxis, die Ergebnisse mit der Funktion `awaitAll` zu erwarten.


```kotlin
suspend fun getOffers(
    categories: List<Category>
): List<Offer> = coroutineScope {
    categories
        .map { async { api.requestOffers(it) } }
        .awaitAll()
        .flatten()
}
```


Wenn Sie die Anzahl der gleichzeitigen Aufrufe begrenzen möchten, können Sie einen Rate Limiter verwenden. Beispielsweise bietet die Resilience4j-Bibliothek Rate Limiter für suspendierten Funktionen an. Sie können Ihre Liste auch in `Flow` umwandeln und dann `flatMapMerge` mit dem `concurrency` Parameter verwenden, der angibt, wie viele gleichzeitige Aufrufe Sie senden werden[^401_10].


```kotlin
fun getOffers(
    categories: List<Category>
): Flow<List<Offer>> = categories
    .asFlow()
    .flatMapMerge(concurrency = 20) {
        suspend { api.requestOffers(it) }.asFlow()
        // or flow { emit(api.requestOffers(it)) }
    }
```



Wenn Sie `coroutineScope` verwenden, denken Sie daran, dass eine Ausnahme in irgendeiner untergeordneten Coroutine die durch `coroutineScope` erstellte Coroutine abbricht, alle anderen Unterprozesse abbricht und dann eine Ausnahme auslöst. Dies ist das Verhalten, das wir normalerweise erwarten, aber in einigen Fällen ist es für uns nicht sehr passend. Wenn wir eine Reihe von gleichzeitigen Prozessen starten möchten, die wir als unabhängig betrachten, sollten wir stattdessen `supervisorScope` verwenden, welches Ausnahmen in seinen Unterprozessen ignoriert[^401_11].



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


Wenn wir die Ausführungszeit eines Prozesses begrenzen möchten, können wir `withTimeout` oder `withTimeoutOrNull` verwenden, die beide ihren Prozess abbrechen, wenn er länger dauert als die durch das Argument angegebene Zeit[^401_12].


```kotlin
suspend fun getUserOrNull(): User? =
    withTimeoutOrNull(5000) {
        fetchUser()
    }
```



#### Flow-Transformationen

Bevor wir diesen Abschnitt abschließen, sollten wir auch die typischen Wege besprechen, wie wir Flow verarbeiten. In den meisten Fällen verwenden wir einfach grundlegende Flow-Verarbeitungsfunktionen wie `map`, `filter` oder `onEach`, und gelegentlich weniger gebräuchliche Funktionen wie `scan` oder `flatMapMerge`[^401_13].



```kotlin
class UserStateProvider(
    private val userRepository: UserRepository
) {

    fun userStateFlow(): Flow<User> = userRepository
        .observeUserChanges()
        .filter { it.isSignificantChange }
        .scan(userRepository.currentUser()) { user, update ->
            user.with(update)
        }
        .map { it.toDomainUser() }
}
```

Wenn Sie zwei Ströme zusammenführen möchten, könnten Sie Funktionen wie `merge`, `zip` oder `combine`[^401_14] verwenden.

```kotlin
class ArticlesProvider(
    private val ktAcademy: KtAcademyRepository,
    private val kotlinBlog: KtAcademyRepository,
) {
    fun observeArticles(): Flow<Article> = merge(
        ktAcademy.observeArticles().map { it.toArticle() },
        kotlinBlog.observeArticles().map { it.toArticle() },
    )
}

class NotificationStatusProvider(
    private val userStateProvider: UserStateProvider,
    private val notificationsProvider: NotificationsProvider,
    private val statusFactory: NotificationStatusFactory,
) {
    fun notificationStatusFlow(): NotificationStatus =
        notificationsProvider.observeNotifications()
            .filter { it.status == Notification.UNSEEN }
            .combine(userStateProvider.userStateFlow()) { 
                    notifications, user ->
                statusFactory.produce(notifications, user)
            }
}
```


Wenn Sie möchten, dass ein einzelner Flow von mehreren Coroutinen überwacht wird, transformieren Sie ihn in `SharedFlow`. Eine gängige Methode, um dies zu tun, besteht darin, `shareIn` mit einem Scope zu verwenden. Um diesen Flow nur dann aktiv zu halten, wenn es Abonnenten gibt, verwenden Sie die Option `WhileSubscribed` für den `started` Parameter[^401_15].


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



[^401_8]: Siehe das Kapitel *Coroutine Scope Functions* für weitere Details.
[^401_10]: Siehe den Abschnitt *`flatMapConcat`, `flatMapMerge`, `flatMapLatest`* im Kapitel *Flow processing* für weitere Informationen.
[^401_11]: Weitere Einzelheiten finden Sie im Abschnitt *supervisorScope* des Kapitels *Coroutine Scope Functions*.
[^401_12]: Weitere Informationen finden Sie im Abschnitt *withTimeout* des Kapitels *Coroutine Scope Functions*.
[^401_13]: Für weitere Details siehe das Kapitel *Flow processing*.
[^401_14]: Siehe den Abschnitt *`merge`, `zip`, `combine`* im Kapitel *Flow processing* für weitere Einzelheiten.
[^401_15]: Weitere Informationen finden Sie im Kapitel *SharedFlow and StateFlow*.


