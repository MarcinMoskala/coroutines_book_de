
## Coroutines: eingebaut vs Bibliothek

Wenn wir über Coroutines sprechen, beziehen wir uns häufig auf sie als ein einziges Konzept. Tatsächlich bestehen sie aus zwei Teilen: eingebauter Unterstützung, die von der Kotlin-Sprache bereitgestellt wird (Compilerunterstützung und Elemente in der Kotlin-Standardbibliothek), und der Kotlin Coroutines-Bibliothek (genannt kotlinx.coroutines). Manchmal werden sie als die gleiche Einheit behandelt, aber sie sind sehr unterschiedlich voneinander.

Die eingebaute Sprachunterstützung ist darauf ausgelegt, minimalistisch zu sein und so viel Freiheit wie möglich zu bieten. Sie kann verwendet werden, um praktisch jeden Stil der Nebenläufigkeit zu reproduzieren, der aus anderen Programmiersprachen bekannt ist, aber es ist nicht bequem, sie direkt zu verwenden. Die meisten ihrer Elemente, wie `suspendCoroutine` oder `Continuation`, sollen eher von Bibliotheksautoren als von Anwendungsentwicklern verwendet werden.

Auf der anderen Seite haben wir die kotlinx.coroutines-Bibliothek. Dies ist eine separate Abhängigkeit, die dem Projekt hinzugefügt werden muss. Sie ist auf der eingebauten Sprachunterstützung aufgebaut. Sie ist viel einfacher zu verwenden und gibt Entwicklern einen bestimmten Stil der Nebenläufigkeit.

| Eingebaute Unterstützung                                                                                           | kotlinx.coroutines-Bibliothek                                  |
|--------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| Compilerunterstützung und Elemente in der Kotlin-Standardbibliothek.                                              | Separate Abhängigkeit muss dem Projekt hinzugefügt werden.   |
| Elemente sind im `kotlin.coroutines`-Paket.                                                                        | Elemente sind im `kotlinx.coroutines`-Paket.                 |
| Minimalistisch, bietet einige grundlegende Elemente (wie `Continuation` oder `suspendCoroutine`) und das `suspend`-Schlüsselwort. | Bietet viele Elemente (wie `launch`, `async`, `Deferred`).   |
| Schwierig direkt zu verwenden.                                                                                     | Entwickelt für den direkten Gebrauch.                         |
| Erlaubt nahezu jeden Stil der Nebenläufigkeit.                                                                     | Entwickelt für einen bestimmten Stil der Nebenläufigkeit.     |

Derzeit werden eingebaute Unterstützung und die kotlinx.coroutines-Bibliothek fast immer zusammen verwendet, aber das ist keine Voraussetzung. Viele wissenschaftliche Arbeiten in der Informatik[^105_1] zeigen die Universalität des Aussetzungskonzepts. Dies wurde auch vom Team, das an der Kotlin Coroutines-Bibliothek arbeitet, gezeigt. Bei der Suche nach dem besten Stil der Nebenläufigkeit haben sie die eingebaute Kotlin-Unterstützung verwendet, um die Stile der Nebenläufigkeit aus vielen anderen Sprachen zu reproduzieren (wie Go's Goroutines). Der derzeit von kotlinx.coroutines angebotene Stil der Nebenläufigkeit ist elegant, bequem und auf andere Muster im Programmierökosystem abgestimmt. Muster und Programmierstile ändern sich jedoch im Laufe der Zeit. Vielleicht wird unsere Gemeinschaft eines Tages einen besseren Stil der Nebenläufigkeit entwickeln. Wenn ja, wird wahrscheinlich jemand in der Lage sein, ihn mit der eingebauten Kotlin-Unterstützung zu implementieren und ihn als separate Bibliothek auszuliefern. Diese vielversprechende neue Bibliothek könnte sogar kotlinx.coroutines ersetzen. Wer weiß, was die Zukunft bringt?

[^105_1]: Zum Beispiel 'Revisiting coroutines' (2009) von Ana Lúcia De Moura und Roberto Ierusalimschy, und 'Continuations and coroutines' (1984), von Christopher T. Haynes, Daniel P. Friedman, und Mitchell Wand.
