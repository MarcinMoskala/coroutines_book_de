
## Flusstest

%% > Dieses Kapitel setzt [Testing Kotlin Coroutines](https://kt.academy/article/cc-testing) fort, in dem `runTest`, `backgroundScope`, `TestScope` usw. vorgestellt werden.

Sie sollten bereits über das notwendige Wissen verfügen, um Funktionen, die einen Flow zurückgeben, ordnungsgemäß zu testen. Die Regeln zum Testen von unterbrechenden Funktionen sind ziemlich einfach, aber um Ihre Fantasie anzuregen, betrachten wir ein paar Beispiele der typischsten Anwendungsfälle und der wichtigsten Probleme, mit denen wir beim Testen von Flows konfrontiert sind.

### Transformationsfunktionen

Die meisten Funktionen, die einen `Flow` zurückgeben, rufen andere Funktionen auf, die ebenfalls einen `Flow` zurückgeben. Dies ist der häufigste und einfachste Fall, daher beginnen wir mit dem Erlernen, wie wir solche Funktionen testen können. Betrachten Sie die folgende Klasse:


```kotlin
class ObserveAppointmentsService(
    private val appointmentRepository: AppointmentRepository
) {
    fun observeAppointments(): Flow<List<Appointment>> =
        appointmentRepository
            .observeAppointments()
            .filterIsInstance<AppointmentsUpdate>()
            .map { it.appointments }
            .distinctUntilChanged()
            .retry {
                it is ApiException && it.code in 500..599
            }
}
```



Die Methode `observeAppointments` erweitert `observeAppointments` aus dem `AppointmentRepository` mit einigen Operationen, darunter Elementfilterung, Mapping, Eliminierung wiederholender Elemente und Wiederholung bei bestimmten Arten von Ausnahmen. Wenn Sie mich bitten würden, zu erklären, was diese Funktion tut, aber mit einem separaten Satz für jede Funktionalität, hätten Sie eine Liste der Unit-Tests, die diese Funktion haben sollte:
* sollte nur Termine aus Updates behalten,
* sollte Elemente eliminieren, die mit dem vorherigen Element identisch sind,
* sollte es bei einer API-Ausnahme mit dem Code 5XX erneut versuchen.

Um diese Tests zu implementieren, müssen wir `AppointmentRepository` simulieren oder ein Mock-Objekt erstellen. Für diese Tests könnten wir ein Mock-Objekt erstellen, dessen `observeAppointments` Funktion einen konstanten `Flow` zurückgibt, der als Quelle verwendet wird. Der einfachste Weg, eine Funktion, wie `observeAppointments`, zu testen, besteht darin, ihren Quellfluss mit `flowOf` zu definieren, was einen endlichen `Flow` erstellt, in dem die Zeit keine Rolle spielt. Wenn die Zeit in der zu testenden Funktion keine Rolle spielt, können wir ihr Ergebnis einfach in eine Liste umwandeln, indem wir die Funktion `toList` verwenden, und es dann mit dem erwarteten Ergebnis in den Zusicherungen vergleichen.



```kotlin
class FakeAppointmentRepository(
    private val flow: Flow<AppointmentsEvent>
) : AppointmentRepository {
    override fun observeAppointments() = flow
}

class ObserveAppointmentsServiceTest {
    val aDate1 = Instant.parse("2020-08-30T18:43:00Z")
    val anAppointment1 = Appointment("APP1", aDate1)
    val aDate2 = Instant.parse("2020-08-31T18:43:00Z")
    val anAppointment2 = Appointment("APP2", aDate2)
    
    @Test
    fun `should keep only appointments from...`() = runTest {
        // given
        val repo = FakeAppointmentRepository(
            flowOf(
                AppointmentsConfirmed,
                AppointmentsUpdate(listOf(anAppointment1)),
                AppointmentsUpdate(listOf(anAppointment2)),
                AppointmentsConfirmed,
            )
        )
        val service = ObserveAppointmentsService(repo)
        
        // when
        val result = service.observeAppointments().toList()
        
        // then
        assertEquals(
            listOf(
                listOf(anAppointment1),
                listOf(anAppointment2),
            ),
            result
        )
    }
    
    // ...
}
```



Der zweite Test könnte auf die gleiche Weise durchgeführt werden, aber ich möchte noch ein zusätzliches Element einführen, das unseren Test etwas komplizierter gestaltet und uns dabei hilft, das zu prüfen, was wir bisher noch nicht getestet haben. Das Problem mit Tests wie dem oben beschriebenen besteht darin, dass sie einen Flow wie eine Liste behandeln; ein solcher Ansatz vereinfacht diese Tests, allerdings wird dabei nicht überprüft, ob Elemente tatsächlich ohne jegliche Verzögerung übertragen werden. Angenommen, ein Entwickler hat Ihrer Flowtransformation eine Verzögerung hinzugefügt, wie im folgenden Codeausschnitt. Eine solche Änderung würde vom oben beschriebenen Test nicht bemerkt werden.



```kotlin
class ObserveAppointmentsService(
    private val appointmentRepository: AppointmentRepository
) {
    fun observeAppointments(): Flow<List<Appointment>> =
        appointmentRepository
            .observeAppointments()
            .onEach { delay(1000) } // Will not influence
            // the above test
            .filterIsInstance<AppointmentsUpdate>()
            .map { it.appointments }
            .distinctUntilChanged()
            .retry {
                it is ApiException && it.code in 500..599
            }
}
```


Betrachten wir ein noch extremeres Beispiel, in dem jemand eine List Transformation anstelle einer Flow Transformation verwendet. Dies wäre absoluter Unsinn, doch würden Tests wie der vorangegangene immer noch bestehen.


```kotlin
class ObserveAppointmentsService(
    private val appointmentRepository: AppointmentRepository,
) {
    // Don't do that!
    fun observeAppointments(): Flow<List<Appointment>> =
        flow {
            val list = appointmentRepository
                .observeAppointments()
                .filterIsInstance<AppointmentsUpdate>()
                .map { it.appointments }
                .distinctUntilChanged()
                .retry {
                    it is ApiException && it.code in 500..599
                }
                .toList()
            emitAll(list)
        }
}
```


Ich führe gerne einen Test durch, der Zeitabhängigkeiten überprüft, und dafür müssen wir `runTest` und etwas `delay` in unserem Quellcode-Datenfluss verwenden. Der Ergebnis-Datenfluss muss Informationen darüber speichern, wann seine Elemente ausgesendet wurden, und wir können das Ergebnis in einer Überprüfung bestätigen.


```kotlin
class ObserveAppointmentsServiceTest {
    // ...
    
    @Test
    fun `should eliminate elements that are...`() = runTest {
        // given
        val repo = FakeAppointmentRepository(flow {
            delay(1000)
            emit(AppointmentsUpdate(listOf(anAppointment1)))
            emit(AppointmentsUpdate(listOf(anAppointment1)))
            delay(1000)
            emit(AppointmentsUpdate(listOf(anAppointment2)))
            delay(1000)
            emit(AppointmentsUpdate(listOf(anAppointment2)))
            emit(AppointmentsUpdate(listOf(anAppointment1)))
        })
        val service = ObserveAppointmentsService(repo)
        
        // when
        val result = service.observeAppointments()
            .map { currentTime to it }
            .toList()
        
        // then
        assertEquals(
            listOf(
                1000L to listOf(anAppointment1),
                2000L to listOf(anAppointment2),
                3000L to listOf(anAppointment1),
            ), result
        )
    }
    
    // ...
}
```



> Testname aufgrund von begrenzter Seitenzahl verkürzt - würde in einem echten Projekt nicht verkürzt werden.

Betrachten Sie schließlich die dritte Funktionalität: "Sollte erneut versuchen, wenn es eine API-Ausnahme mit dem Code 5XX gibt". Wenn wir einen Datenfluss zurückgeben würden, der keinen Wiederholungsversuch durchführen sollte, könnten wir das Wiederholungsverhalten nicht testen. Wenn wir einen Datenfluss zurückgeben würden, der einen Wiederholungsversuch durchführen sollte, würde die zu testende Funktion unendlich oft wiederholen und einen unendlichen Datenfluss erzeugen. Der einfachste Weg, einen unendlichen Datenfluss zu testen, besteht darin, die Anzahl seiner Elemente mit `take` zu begrenzen.



```kotlin
class ObserveAppointmentsServiceTest {
    // ...
    
    @Test
    fun `should retry when API exception...`() = runTest {
        // given
        val repo = FakeAppointmentRepository(flow {
            emit(AppointmentsUpdate(listOf(anAppointment1)))
            throw ApiException(502, "Some message")
        })
        val service = ObserveAppointmentsService(repo)
        
        // when
        val result = service.observeAppointments()
            .take(3)
            .toList()
        
        // then
        assertEquals(
            listOf(
                listOf(anAppointment1),
                listOf(anAppointment1),
                listOf(anAppointment1),
            ), result
        )
    }
}
```



> Wir sollten auch "sollte bei einer nicht-API-Ausnahme nicht erneut versuchen" und "sollte bei einer API-Ausnahme mit einem nicht-5XX-Code nicht erneut versuchen" testen, aber ich werde diese Testfälle in diesem Buch überspringen.

Eine andere Option besteht darin, einen Ablauf zu erstellen, der zunächst eine Ausnahme auslöst, die einen erneuten Versuch verursachen sollte, und dann einen, der dies nicht sollte. Auf diese Weise können wir nicht nur eine beispielsweise Ausnahme testen, die einen erneuten Versuch verursachen sollte, sondern auch eine, die es nicht sollte.



```kotlin
class ObserveAppointmentsServiceTest {
    // ...
    
    @Test
    fun `should retry when API exception...`() = runTest {
        // given
        var retried = false
        val someException = object : Exception() {}
        val repo = FakeAppointmentRepository(flow {
            emit(AppointmentsUpdate(listOf(anAppointment1)))
            if (!retried) {
                retried = true
                throw ApiException(502, "Some message")
            } else {
                throw someException
            }
        })
        val service = ObserveAppointmentsService(repo)
        
        // when
        val result = service.observeAppointments()
            .catch<Any> { emit(it) }
            .toList()
        
        // then
        assertTrue(retried)
        assertEquals(
            listOf(
                listOf(anAppointment1),
                listOf(anAppointment1),
                someException,
            ), result
        )
    }
}
```



### Testen unendlicher Flows

Klassen zu testen, die StateFlow oder SharedFlow nutzen, ist etwas komplizierter. Erstens benötigen sie einen Scope; wenn wir unseren Test mit `runTest` definieren, sollte dieser Scope `backgroundScope` sein, nicht `this`, damit unser Test nicht auf das Ende dieses Scopes erwartet. Zweitens sind diese Flows unendlich, sie werden also nicht abgeschlossen, es sei denn, ihr Scope wird abgebrochen. Es gibt einige Möglichkeiten, unendliche Flows zu testen, die ich mittels eines Beispiels vorstellen werde.

Betrachten Sie den folgenden Service, der dazu verwendet werden kann, Nachrichten von einem bestimmten Benutzer zu überwachen. Diese Klasse nutzt auch SharedFlow, sodass nicht mehr als ein Anschluss zur Nachrichtenquelle hergestellt wird, selbst wenn es mehrere Beobachter gibt. Das bedeutet, dass `observeMessages` einen Flow zurückgibt, der niemals abgeschlossen wird, es sei denn, `scope` wird abgebrochen.



```kotlin
class MessagesService(
    messagesSource: Flow<Message>,
    scope: CoroutineScope
) {
    private val source = messagesSource
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed()
        )
    
    fun observeMessages(fromUserId: String) = source
        .filter { it.fromUserId == fromUserId }
}
```

Um das Problem besser zu verstehen, betrachtet man den folgenden **fehlerhaften** Test:

```kotlin
class MessagesServiceTest {
    // Failing test!
    @Test
    fun `should emit messages from user`() = runTest {
        // given
        val source = flowOf(
            Message(fromUserId = "0", text = "A"),
            Message(fromUserId = "1", text = "B"),
            Message(fromUserId = "0", text = "C"),
        )
        val service = MessagesService(
            messagesSource = source,
            scope = backgroundScope,
        )
        
        // when
        val result = service.observeMessages("0")
            .toList() // Here we'll wait forever!
        
        // then
        assertEquals(
            listOf(
                Message(fromUserId = "0", text = "A"),
                Message(fromUserId = "0", text = "C"),
            ), result
        )
    }
}
```


Der oben genannte Test wird durch `toList` dauerhaft angehalten. Die einfachste (und leider auch häufigste) Lösung für dieses Problem nutzt `take` mit einer bestimmten Anzahl von erwarteten Elementen. Der folgende Test verläuft erfolgreich, verliert jedoch viele Informationen. Nehmen wir als Beispiel eine Nachricht, die von `observeMessages` noch nicht hätte ausgegeben werden sollen, es aber doch getan hat, und zwar an der nächsten Position. Ein Unit-Test würde diese Situation nicht erkennen. Ein noch größeres Problem entsteht, wenn jemand eine Änderung im Code vornimmt, die dazu führt, dass der Code unendlich läuft. Es ist viel schwieriger, den Grund dafür zu finden, als wenn unser Test so umgesetzt würde, wie in den folgenden Beispielen.


```kotlin
class MessagesServiceTest {
    @Test
    fun `should emit messages from user`() = runTest {
        // given
        val source = flowOf(
            Message(fromUserId = "0", text = "A"),
            Message(fromUserId = "1", text = "B"),
            Message(fromUserId = "0", text = "C"),
        )
        val service = MessagesService(
            messagesSource = source,
            scope = backgroundScope,
        )
        
        // when
        val result = service.observeMessages("0")
            .take(2)
            .toList()
        
        // then
        assertEquals(
            listOf(
                Message(fromUserId = "0", text = "A"),
                Message(fromUserId = "0", text = "C"),
            ), result
        )
    }
}
```

Der nächste Ansatz besteht darin, unseren Ablauf in `backgroundScope` zu starten und alle Elemente, die er ausgibt, in einer Sammlung zu speichern. Dieser Ansatz zeigt uns nicht nur besser "was ist" und "was sein sollte" in fehlerhaften Fällen; er bietet uns auch viel mehr Flexibilität bei der Testzeit. Im folgenden Beispiel habe ich einige Verzögerungen hinzugefügt, um zu überprüfen, wann Nachrichten gesendet werden.

```kotlin
class MessagesServiceTest {
    @Test
    fun `should emit messages from user`() = runTest {
        // given
        val source = flow {
            emit(Message(fromUserId = "0", text = "A"))
            delay(1000)
            emit(Message(fromUserId = "1", text = "B"))
            emit(Message(fromUserId = "0", text = "C"))
        }
        val service = MessagesService(
            messagesSource = source,
            scope = backgroundScope,
        )
        
        // when
        val emittedMessages = mutableListOf<Message>()
        service.observeMessages("0")
            .onEach { emittedMessages.add(it) }
            .launchIn(backgroundScope)
        delay(1)
        
        // then
        assertEquals(
            listOf(
                Message(fromUserId = "0", text = "A"),
            ), emittedMessages
        )
        
        // when
        delay(1000)
        
        // then
        assertEquals(
            listOf(
                Message(fromUserId = "0", text = "A"),
                Message(fromUserId = "0", text = "C"),
            ), emittedMessages
        )
    }
}
```


Eine gute Alternative ist die `toList` Funktion, die nur für eine bestimmte Zeit angewendet wird. Sie bietet weniger Flexibilität, aber ich mag es, sie zu verwenden, da sie einfach und verständlich ist. Hier zeige ich, wie ich eine solche Funktion implementiere und nutze:


```kotlin
suspend fun <T> Flow<T>.toListDuring(
    duration: Duration
): List<T> = coroutineScope {
    val result = mutableListOf<T>()
    val job = launch {
        this@toListDuring.collect(result::add)
    }
    delay(duration)
    job.cancel()
    return@coroutineScope result
}

class MessagesServiceTest {
    @Test
    fun `should emit messages from user`() = runTest {
        // given
        val source = flow {
            emit(Message(fromUserId = "0", text = "A"))
            emit(Message(fromUserId = "1", text = "B"))
            emit(Message(fromUserId = "0", text = "C"))
        }
        val service = MessagesService(
            messagesSource = source,
            scope = backgroundScope,
        )
        
        // when
        val emittedMessages = service.observeMessages("0")
            .toListDuring(1.milliseconds)
        
        // then
        assertEquals(
            listOf(
                Message(fromUserId = "0", text = "A"),
                Message(fromUserId = "0", text = "C"),
            ), emittedMessages
        )
    }
}
```

Es ist auch erwähnenswert, dass es Bibliotheken wie Turbine gibt, die Werkzeuge zur Vereinfachung von Testflüssen bereitstellen, die es erlauben, durch ein Objekt zu sammeln, und die es ermöglichen, auf Elemente zu warten.

```kotlin
class MessagesServiceTest {
    @Test
    fun `should emit messages from user`() = runTest {
        // given
        val source = flow {
            emit(Message(fromUserId = "0", text = "A"))
            emit(Message(fromUserId = "1", text = "B"))
            emit(Message(fromUserId = "0", text = "C"))
        }
        val service = MessagesService(
            messagesSource = source,
            scope = backgroundScope,
        )
        
        // when
        val messagesTurbine = service.observeMessages("0")
            .testIn(backgroundScope)
        
        // then
        assertEquals(
            Message(fromUserId = "0", text = "A"),
            messagesTurbine.awaitItem()
        )
        assertEquals(
            Message(fromUserId = "0", text = "C"),
            messagesTurbine.awaitItem()
        )
        messagesTurbine.expectNoEvents()
    }
}
```


Turbine scheint ziemlich beliebt für Flusstests zu sein, aber ich bin kein großer Fan von der Verwendung von Drittanbieterbibliotheken, wenn sie nicht wirklich benötigt werden.

### Wie viele Verbindungen wurden geöffnet?

Eine der wichtigsten Funktionalitäten von `MessagesService` ist, dass er nur eine Verbindung zur Quelle starten sollte, egal wie viele aktive Beobachter wir haben.


```kotlin
// Starts at most one connection to the source
class MessagesService(
    messagesSource: Flow<Message>,
    scope: CoroutineScope
) {
    private val source = messagesSource
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed()
        )
    
    fun observeMessages(fromUserId: String) = source
        .filter { it.fromUserId == fromUserId }
}

// Can start multiple connections to the source
class MessagesService(
    messagesSource: Flow<Message>,
) {
    fun observeMessages(fromUserId: String) = messagesSource
        .filter { it.fromUserId == fromUserId }
}
```


Der einfachste Weg, dieses Verhalten zu testen, ist einen Datenfluss zu erstellen, der zählt, wie viele Abonnenten er hat. Du kannst dies tun, indem du einen Zähler in `onStart` inkrementierst und ihn in `onCompletion` dekrementierst.


```kotlin
private val infiniteFlow =
    flow<Nothing> {
        while (true) {
            delay(100)
        }
    }

class MessagesServiceTest {
    // ...
    
    @Test
    fun `should start at most one connection`() = runTest {
        // given
        var connectionsCounter = 0
        val source = infiniteFlow
            .onStart { connectionsCounter++ }
            .onCompletion { connectionsCounter-- }
        val service = MessagesService(
            messagesSource = source,
            scope = backgroundScope,
        )
        
        // when
        service.observeMessages("0")
            .launchIn(backgroundScope)
        service.observeMessages("1")
            .launchIn(backgroundScope)
        service.observeMessages("0")
            .launchIn(backgroundScope)
        service.observeMessages("2")
            .launchIn(backgroundScope)
        delay(1000)
        
        // then
        assertEquals(1, connectionsCounter)
    }
}
```



### Testen von View-Models

Flow Builder ermöglicht es uns zu beschreiben, wie Elemente von der Quelle gesendet werden sollten. Dies ist eine einfache und effektive Methode. Es gibt jedoch auch eine andere Option: die Verwendung von `SharedFlow` als Quelle und das Senden von Elementen während des Tests. Ich finde diese Option besonders nützlich für das Testen von View-Models.



```kotlin
class ChatViewModel(
    private val messagesService: MessagesService,
) : ViewModel() {
    private val _lastMessage =
        MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage
    
    private val _messages =
        MutableStateFlow(emptyList<String>())
    val messages: StateFlow<List<String>> = _messages
    
    fun start(fromUserId: String) {
        messagesService.observeMessages(fromUserId)
            .onEach {
                val text = it.text
                _lastMessage.value = text
                _messages.value = _messages.value + text
            }
            .launchIn(viewModelScope)
    }
}

class ChatViewModelTest {
    @Test
    fun `should expose messages from user`() = runTest {
        // given
        val source = MutableSharedFlow<Message>()
        
        // when
        val viewModel = ChatViewModel(
            messagesService = FakeMessagesService(source)
        )
        viewModel.start("0")
        
        // then
        assertEquals(null, viewModel.lastMessage.value)
        assertEquals(emptyList(), viewModel.messages.value)
        
        // when
        source.emit(Message(fromUserId = "0", text = "ABC"))
        
        // then
        assertEquals("ABC", viewModel.lastMessage.value)
        assertEquals(listOf("ABC"), viewModel.messages.value)
        
        // when
        source.emit(Message(fromUserId = "0", text = "DEF"))
        source.emit(Message(fromUserId = "1", text = "GHI"))
        
        // then
        assertEquals("DEF", viewModel.lastMessage.value)
        assertEquals(
            listOf("ABC", "DEF"),
            viewModel.messages.value
        )
    }
}
```



Auf diese Weise können wir das Verhalten unserer Funktionen korrekt testen, ohne uns überhaupt auf die virtuelle Zeit verlassen zu müssen, und unser Unit-Test ist einfacher zu lesen.

### Zusammenfassung

Ich hoffe, dieses Kapitel hat Ihnen einen Überblick darüber gegeben, wie wir Klassen testen können, die Flow verwenden. Die Logik ist im Allgemeinen sehr ähnlich wie bei Tests von suspendierenden Funktionen, aber Flow hat seine eigenen Besonderheiten, und in diesem Kapitel, ich habe verschiedene Beispiele gezeigt, wie man mit ihnen umgeht.


