## Häufige Anwendungsfälle

Schauen wir uns die am häufigsten auftretenden Muster in Anwendungen an, die gut mit Kotlin Coroutines implementiert sind. Die meisten dieser Muster werden sowohl im Backend als auch auf Android verwendet.

Die meisten modernen Anwendungen können in drei Ebenen unterteilt werden:
* Daten/Adapter-Ebene - Die Ebene, die für die Datenspeicherung oder Interaktion mit anderen Systemen verantwortlich ist. Diese Ebene besteht hauptsächlich aus Repositories, die jeweils eine oder mehrere Datenquellen enthalten können. In dieser Ebene interagieren unsere Coroutines mit anderen Bibliotheken, einschließlich einiger, die möglicherweise keine Coroutines unterstützen.
* Domänenebene - Die Ebene, in der die Geschäftslogik unserer Anwendung implementiert wird. Sie umfasst Klassen, die spezifische Operationen kapseln, wie Use Cases, Services, Fassaden, usw. In dieser Ebene werden Coroutines verwendet, um unsere Kernprozesse zu optimieren.
* Präsentation/API/UI-Ebene - Die Ebene, die die Prozesse unserer Anwendung startet. Sie könnte als Einstieg in unsere Anwendung betrachtet werden. In dieser Ebene starten wir Coroutines und wir verarbeiten die Ergebnisse ihrer Ausführung.

Jede dieser Ebenen hat unterschiedliche Anwendungsfälle für Coroutines. In den nächsten Abschnitten dieses Kapitels werde ich die typischen Anwendungsfälle für jede dieser Ebenen beschreiben.
