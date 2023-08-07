Die Kotlin Coroutines Bibliothek wurde speziell für unsere üblichen Backend- und Android-Anwendungsfälle entwickelt. Das heißt, obwohl jedes Problem auf viele Weisen gelöst werden kann, gibt es für die meisten Situationen anerkannte Muster, die bevorzugt angewendet werden sollten. Einige von ihnen wurden von den Schöpfern der Kotlin Coroutines selbst formuliert, während andere sich aus Diskussionen in unserer Gemeinschaft ergeben haben. Ich habe diese Anwendungsfälle zusammengetragen und möchte meine Vorstellungen darüber präsentieren, wie Kotlin Coroutines bei üblichen Anwendungsfällen und in typischen Anwendungen eingesetzt werden sollten.

Ich habe diese Anwendungsfälle nach den Schichten unterteilt, in denen sie typischerweise eingesetzt werden. Diese drei Schichten sind sowohl für die meisten modernen Backend- als auch Android-Anwendungen gebräuchlich:

* [Data/Adapters Layer](https://kt.academy/article/cc-use-cases-data-layer) - Die Schicht, die für die Datenspeicherung oder die Interaktion mit anderen Systemen zuständig ist. Diese Schicht besteht hauptsächlich aus Repositories, die jeweils keine oder mehrere Datenquellen enthalten können. In dieser Schicht interagieren unsere Coroutines mit anderen Bibliotheken, einschließlich solchen, die sie möglicherweise nicht unterstützen.
* [Domain Layer](https://kt.academy/article/cc-use-cases-domain-layer) - Die Schicht, in der die Geschäftslogik unserer Anwendung umgesetzt wird. Sie enthält Klassen, die spezielle Operationen wie Use Cases, Services, Fassaden usw. kapseln. In dieser Schicht werden Coroutines eingesetzt, um unsere Kernprozesse der Anwendung zu optimieren.
* [Presentation/API/UI layer](https://kt.academy/article/cc-use-cases-presentation-layer) - Die Schicht, die die Prozesse unserer Anwendung startet. Sie kann als Einstiegspunkt unserer Anwendung angesehen werden. In dieser Schicht starten wir Coroutines und verarbeiten ihre Ausführungsergebnisse.

Ich hoffe, die Lektüre dieser Anwendungsfälle bereitet Ihnen Freude. Wenn Sie mit einem von ihnen nicht einverstanden sind oder wenn Ihnen ein wichtiger Anwendungsfall fehlt, lassen Sie es mich bitte per E-Mail an contact@kt.academy wissen.


