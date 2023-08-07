
## Coroutinen starten im Vergleich zu Suspending Functions

Betrachten wir einen Anwendungsfall, in dem man eine Reihe von gleichzeitigen Aktionen ausführen muss. Es gibt zwei Arten von Funktionen, die dafür verwendet werden können:
* eine normale Funktion, die mit einem Coroutine Scope-Objekt interagiert,
* eine Suspending Function.

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
class NotificationsSender(
    private val client: NotificationsClient,
) {
    suspend fun sendNotifications(
        notifications: List<Notification>
    ) = supervisorScope {
        for (n in notifications) {
            launch {
                client.send(n)
            }
        }
    }
}
```



Diese beiden Optionen mögen auf den ersten Blick einige Ähnlichkeiten aufweisen, repräsentieren aber grundsätzlich unterschiedliche Verwendungsfälle. Um einige wesentliche Unterschiede zu erkennen, muss man nicht einmal ihren Inhalt lesen.

Wenn wir wissen, dass eine regelmäßige Funktion Coroutines startet, wissen wir, dass sie ein Scope-Objekt verwenden muss. Solche Funktionen starten in der Regel nur Coroutines und warten nicht auf deren Abschluss, daher wird `sendNotifications` vermutlich nur Millisekunden zur Ausführung benötigen. Das Starten von Coroutines in einem externen Geltungsbereich bedeutet auch, dass Ausnahmen in diesen Coroutines von diesem Geltungsbereich behandelt werden (werden meist einfach ausgegeben und ignoriert). Diese Coroutines erben den Kontext vom Geltungsbereichsobjekt, daher müssen wir den Geltungsbereich abbrechen, um sie zu stoppen.



```kotlin
class NotificationsSender(
    private val client: NotificationsClient,
    private val notificationScope: CoroutineScope,
) {
    // Does not wait for started coroutines
    // Exceptions are handled by the scope
    // Takes context from the scope
    // and builds relationship to the scope
    fun sendNotifications(
        notifications: List<Notification>
    ) {
        // ...
    }
}
```



Wenn wir wissen, dass eine aussetzende Funktion Coroutinen startet, können wir davon ausgehen, dass diese Funktion nicht beendet ist, bis alle Coroutinen abgeschlossen sind. Eine solche `sendNotifications` wird angehalten, bis die letzte Benachrichtigung vollständig bearbeitet ist. Dies ist ein wichtiger Synchronisationsmechanismus. Wir wissen auch, dass aussetzende Funktionen ihre aufrufende Funktion nicht abbrechen. Stattdessen können sie Ausnahmen werfen oder unterdrücken, genau wie reguläre Funktionen Ausnahmen werfen oder unterdrücken können. Die von einer solchen Funktion gestarteten Coroutinen sollten den Kontext der aufrufenden Funktion erben und in Beziehung zu dieser stehen.



```kotlin
class NotificationsSender(
    private val client: NotificationsClient,
) {
    // Waits for its coroutines
    // Handles exceptions
    // Takes context and builds relationship to
    // the coroutine that started it
    suspend fun sendNotifications(
        notifications: List<Notification>
    ) {
        // ...
    }
}
```



Beide Anwendungsfälle sind in unseren Anwendungen wichtig. Wenn wir die Wahl haben, sollten wir suspendierende Funktionen bevorzugen, da sie einfacher zu steuern und zu synchronisieren sind. Aber Coroutinen müssen irgendwo beginnen und dafür verwenden wir reguläre Funktionen mit einem Bereichsobjekt.

In manchen Anwendungen könnten wir in eine Situation geraten, in der wir diese beiden Arten von Funktionen mischen müssen, wenn wir suspendierende Funktionen benötigen, die auch externe Prozesse in einem äußeren Bereich starten.

Betrachten wir eine Funktion, die einige Operationen ausführen muss, die ein wesentlicher Teil ihrer Ausführung sind, aber sie muss auch einen unabhängigen Prozess starten. Im untenstehenden Beispiel wird der Prozess des Sendens eines Ereignisses als externer Prozess betrachtet, daher wird er in einem externen Bereich gestartet. Dank dessen:
* `updateUser` wird nicht warten, bis die Ausführung von `sendEvent` abgeschlossen ist.
* der `sendEvent`-Prozess wird nicht abgebrochen, wenn die Coroutine, die `updateUser` gestartet hat, abgebrochen wird. (Das ist sinnvoll, weil die Benutzersynchronisation sowieso abgeschlossen ist.)
* `eventsScope` entscheidet, welcher Kontext zum Senden von Ereignissen angemessen wäre und ob dieses Ereignis stattfinden sollte. (Wenn `eventsScope` nicht aktiv ist, wird kein Ereignis gesendet.)



```kotlin
suspend fun updateUser() = coroutineScope {
    val apiUserAsync = async { api.fetchUser() }
    val dbUserAsync = async { db.getUser() }
    val apiUser = apiUserAsync.await()
    val dbUser = dbUserAsync.await()
    
    if (apiUser.lastUpdate > dbUser.lastUpdate) {
        db.updateUser(apiUser)
    } {
        api.updateUser(dbUser)
    }
    
    eventsScope.launch { sendEvent(UserSunchronized) }
}
```

In einigen Situationen ist dieses hybride Verhalten genau das, was wir in unseren Anwendungen wollen, aber es sollte mit Bedacht verwendet werden.
