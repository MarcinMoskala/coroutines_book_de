
# Einleitung

Warum möchten Sie über Kotlin Coroutinen lernen? Diese Frage stelle ich oft den Teilnehmenden meiner Workshops. "Weil sie cool sind" und "Weil alle darüber reden" sind häufige Antworten. Wenn ich genauer nachfrage, höre ich Antworten wie "weil sie leichtere Threads sind", "weil sie einfacher als RxJava sind" oder "weil sie Nebenläufigkeit ermöglichen und dabei einen imperativen Code-Stil beibehalten". Aber Coroutinen sind viel mehr als das. Sie sind der Heilige Gral der Nebenläufigkeit. Als Konzept sind sie in der Informatik seit den 1960er Jahren bekannt, aber in der Mainstream-Programmierung wurden sie nur in sehr begrenzter Form verwendet (wie zum Beispiel async/await). Dies änderte sich mit der Einführung von Golang, welches wesentlich allgemeinere Coroutinen einführte. Kotlin baute darauf auf und schuf, was ich glaube, derzeit die leistungsfähigste und praktischste Umsetzung dieser Idee.

Die Bedeutung der Nebenläufigkeit nimmt zu, aber traditionelle Techniken sind nicht ausreichend. Aktuelle Trends deuten darauf hin, dass Coroutinen die Richtung sind, in die unsere Branche eindeutig steuert, und Kotlin Coroutinen stellen einen bedeutenden Schritt nach vorne dar. Lassen Sie mich Ihnen diese vorstellen, zusammen mit Beispielen, wie gut sie gängige Anwendungsfälle bewältigen. Ich hoffe, Sie werden viel Spaß beim Lesen dieses Buches haben.

### Für wen ist dieses Buch?

Als Entwickler mit Erfahrung sowohl im Backend als auch in Android konzentriere ich mich in diesem Buch hauptsächlich auf diese beiden Perspektiven. Dies sind derzeit die beiden Hauptanwendungen von Kotlin in der Industrie, und es ist offensichtlich, dass die Coroutinen hauptsächlich darauf abzielen, diese Anwendungsfälle gut zu unterstützen[^000_1]. Man könnte also sagen, dass dieses Buch hauptsächlich für Android- und Backend-Entwickler konzipiert ist, aber es sollte genauso nützlich für andere Entwickler sein, die Kotlin verwenden.

Dieses Buch setzt voraus, dass die Lesenden Kotlin gut kennen. Falls Sie das nicht tun, empfehle ich Ihnen, mit meinem anderen Buch, *Kotlin Essentials*, zu beginnen.

### Die Struktur dieses Buches

Das Buch ist in die folgenden Teile gegliedert:
- **Teil 1: Verstehen von Kotlin Coroutinen** - gewidmet der Erklärung, was Kotlin Coroutinen sind und wie sie wirklich funktionieren.
- **Teil 2: Kotlin Coroutinen-Bibliothek** - erklärt die wichtigsten Konzepte aus der kotlinx.coroutines-Bibliothek und wie man sie gut verwendet.
- **Teil 3: Channel und Flow** - konzentriert sich auf Channel und Flow aus der kotlinx.coroutines-Bibliothek.
- **Teil 4: Kotlin Coroutinen in der Praxis** - prüft gängige Anwendungsfälle und die wichtigsten Best Practices für die Verwendung von Kotlin Coroutinen.

### Was wird behandelt?

Dieses Buch basiert auf einem Workshop, den ich leite. Während seiner Iterationen konnte ich beobachten, was die Teilnehmenden interessierte und was nicht. Dies sind die Elemente, die am häufigsten von den Teilnehmenden erwähnt werden:
- **Wie funktionieren Coroutinen wirklich?** (Teil 1)
- **Wie setzt man Coroutinen in der Praxis ein?** (Teil 2, 3 und besonders 4)
- **Was sind die besten Vorgehensweisen?** (Teil 2, 3 und besonders 4)
- **Testen von Kotlin Coroutinen** (*Testen von Kotlin Coroutinen* im Teil 2 und *Testen von Flow* im Teil 3)
- **Was ist Flow und wie funktioniert es?** (Teil 3)

### Die Reihe Kotlin für Entwickler

Dieses Buch ist Teil einer Reihe von Büchern namens *Kotlin für Entwickler*, die die folgenden Bücher umfasst:
* Kotlin Essentials, das alle grundlegenden Funktionen von Kotlin abdeckt.
* Functional Kotlin, das sich den funktionalen Funktionen von Kotlin widmet, einschließlich Funktionstypen, Lambda-Ausdrücken, Sammlungsverarbeitung, DSLs und Scope-Funktionen.
* Kotlin Coroutinen, das alle Funktionen von Kotlin Coroutinen abdeckt, einschließlich deren Verwendung und Tests, der Verwendung von Flow, Best Practices und den häufigsten Fehlern.
* Advanced Kotlin, das sich den fortgeschrittenen Funktionen von Kotlin widmet, einschließlich generischer Varianz-Modifikatoren, Delegation, Multiplattform-Programmierung, Annotation Processing, KSP und Compiler-Plugins.
* Effective Kotlin, das sich den Best Practices der Kotlin-Programmierung widmet.

### Konventionen

Wenn ich ein konkretes Element aus dem Code verwende, werde ich Code-Schrift verwenden. Wenn ich ein Konzept benenne, werde ich das Wort großschreiben. Um ein beliebiges Element eines bestimmten Typs zu bezeichnen, werde ich Kleinbuchstaben verwenden. Zum Beispiel:
- `Flow` ist ein Typ oder eine Schnittstelle, es wird mit Code-Schrift gedruckt (wie in "Funktion muss `Flow` zurückgeben");
- Flow stellt ein Konzept dar, daher beginnt es mit einem Großbuchstaben (wie in "Dies erklärt den wesentlichen Unterschied zwischen Channel und Flow");
- ein flow ist eine Instanz, wie eine Liste oder ein Set, daher ist es in Kleinbuchstaben (wie in "Jeder flow besteht aus mehreren Elementen").

Ein weiteres Beispiel: `List` bedeutet eine Schnittstelle oder einen Typ ("Der Typ von `l` ist `List`"); List repräsentiert ein Konzept (eine Datenstruktur), während eine Liste eine von vielen Listen ist ("die Variable `list` hält eine Liste").

Ich habe in dem Buch amerikanisches Englisch verwendet, mit Ausnahme der Schreibweise von "cancellation/cancelled", die ich aufgrund der Schreibweise des "Cancelled" Coroutine-Zustands gewählt habe.

### Code Konventionen

Die meisten der präsentierten Ausschnitte sind ausführbarer Code ohne Importanweisungen. Einige Kapitel dieses Buches werden als Artikel auf der Kt. Academy-Website veröffentlicht, wo die meisten Ausschnitte ausgeführt werden können und die Leser den Code ausprobieren können. Der Quellcode aller Ausschnitte wird im folgenden Repository veröffentlicht:

[https://github.com/MarcinMoskala/coroutines_sources](https://github.com/MarcinMoskala/coroutines_sources)

Die Ergebnisse der Ausschnitte werden mit der `println` Funktion dargestellt. Das Ergebnis wird oft am Ende dieser Ausschnitte in Kommentaren platziert. Falls es zu einer Verzögerung zwischen den Ausgabezeilen kommt, wird diese in Klammern dargestellt. Hier ist ein Beispiel:


```kotlin
suspend fun main(): Unit = coroutineScope {
  launch {
      delay(1000L)
      println("World!")
  }
  println("Hello,")
}
// Hello,
// (1 sec)
// World!
```

Manchmal sind einige Teile des Codes oder eines Ergebnisses mit `...` abgekürzt. In solchen Fällen, verstehen Sie es als "hier sollte mehr sein, aber es ist für das Beispiel nicht von Bedeutung".

```kotlin
launch(CoroutineName("Name1")) { ... }
launch(CoroutineName("Name2") + Job()) { ... }
```


In manchen Fällen zeige ich Kommentare neben der Zeile, die sie ausgibt. Ich mache das, wenn die Reihenfolge eindeutig ist:


```kotlin
suspend fun main(): Unit = coroutineScope {
  println("Hello,") // Hello,
  delay(1000L) // (1 sec)
  println("World!") // World!
}
```

In einigen Ausschnitten habe ich eine Nummer nach der Zeile hinzugefügt, um das Verhalten des Ausschnitts leichter erklären zu können. So könnte es aussehen:

```kotlin
suspend fun main(): Unit = coroutineScope {
   println("Hello,") // 1
   delay(1000L) // 2
   println("World!") // 3
}
```


In der ersten Zeile drucken wir "Hello,", dann warten wir aufgrund des `delay` in der zweiten Zeile eine Sekunde, und in der dritten Zeile drucken wir "World!".

{pagebreak}

### Danksagungen

Ohne die großartigen Anregungen und Kommentare der Gutachter wäre dieses Buch nicht so gut. Ich möchte mich bei ihnen allen bedanken. Hier ist die vollständige Liste der Gutachter, beginnend mit den aktivsten.

{width: 25%, float: left, }
![](nicola_corti.jpeg)

**Nicola Corti** - ein Google Developer Expert für Kotlin. Er arbeitet seit der Version 1.0 mit der Sprache und ist der Verwalter mehrerer Open-Source Bibliotheken und Tools für mobile Entwickler (Detekt, Chucker, AppIntro). Derzeit arbeitet er im React Native Core-Team bei Meta und erstellt eines der beliebtesten plattformübergreifenden mobilen Frameworks. Darüber hinaus ist er ein aktives Mitglied der Entwicklergemeinschaft. Sein Engagement reicht von Vorträgen auf internationalen Konferenzen bis hin zur Mitgliedschaft in CFP-Komitees und Unterstützung von Entwicklergemeinschaften in ganz Europa. In seiner Freizeit backt, podcastet und läuft er gerne.

{width: 25%, float: left, }
![](garima.jpeg)

**Garima Jain** - ein Google Developer Expert in Android aus Indien. Sie ist in der Community auch als @ragdroid bekannt. Garima arbeitet als Principal Android Engineer bei GoDaddy. Sie ist auch eine internationale Referentin und eine aktive technische Bloggerin. Sie tauscht sich gerne mit anderen Menschen aus der Community aus und teilt ihre Gedanken mit ihnen. In ihrer Freizeit sieht sie gerne Fernsehsendungen, spielt Tischtennis und Basketball. Aufgrund ihrer Liebe zu Fiktion und Codierung liebt sie es, Technologie mit Fiktion zu mischen und dann ihre Ideen durch Vorträge und Blogposts mit anderen zu teilen.

{width: 25%, float: left, }
![](ilmir.jpg)

**Ilmir Usmanov** - ein Softwareentwickler bei JetBrains, der seit 2017 an der Unterstützung von Coroutinen im Kotlin-Compiler arbeitet. War verantwortlich für die Stabilisierung und Implementierung des Coroutinen-Designs. Seitdem hat er sich anderen Features zugewandt, nämlich Inline-Klassen. Derzeit beschränkt sich seine Arbeit mit Coroutinen auf das Beheben von Fehlern und die Optimierung, da Coroutinen als Sprachfeature vollständig und stabil sind und nicht viel Aufmerksamkeit erfordern.

{width: 25%, float: left, }
![](sean.jpeg)

**Sean McQuillan** - ein Developer Advocate bei Google. Mit zehn Jahren Erfahrung bei Twilio und anderen Start-ups in San Francisco ist er ein Experte für die Entwicklung von skalierbaren Apps. Sean ist leidenschaftlich daran interessiert, mit großartigen Tools schnell hochwertige Apps zu erstellen. Wenn er nicht an Android arbeitet, finden Sie ihn am Klavier oder er häkelt Hüte.

{width: 25%, float: left, }
![](igor.png)

**Igor Wojda** - ein leidenschaftlicher Ingenieur mit über einem Jahrzehnt Erfahrung in der Softwareentwicklung. Er interessiert sich sehr für Android-App-Architektur und die Kotlin-Sprache und ist ein aktives Mitglied der Open-Source-Community. Igor ist ein Konferenzsprecher, technischer Lektor für das Buch 'Kotlin In Action' und Autor des Buches 'Android Development with Kotlin'. Igor teilt seine Leidenschaft für das Codieren gerne mit anderen Entwicklern.

**Jana Jarolimova** - eine Android-Entwicklerin bei Avast. Sie begann ihre Karriere mit Java-Kursen an der Prager Stadtuniversität, bevor sie zur mobilen Entwicklung wechselte, was zwangsläufig zu Kotlin und ihrer Liebe dazu führte.

**Richard Schielek** - ein erfahrener Entwickler und früher Anwender von Kotlin und Coroutinen, die er beide vor ihrer Stabilisierung in der Produktion einsetzte. Er hat mehrere Jahre in der europäischen Raumfahrtindustrie gearbeitet.

**Vsevolod Tolstopyatov** - ein Teamleiter des Kotlin Libraries Teams. Er arbeitet bei JetBrains und interessiert sich für API-Design, Parallelverarbeitung, JVM-Internals, Performance-Tuning und Methoden.

**Lukas Lechner**, **Ibrahim Yilmaz**, **Dean Djermanović** und **Dan O'Neill**.

Ich möchte auch **Michael Timberlake**, unserem Sprachgutachter, für seine ausgezeichneten Korrekturen des gesamten Buches danken.

[^000_1]: Googles Android-Team hat bei der Gestaltung und Erstellung einiger Features, die wir in diesem Buch präsentieren werden, mitgewirkt.

