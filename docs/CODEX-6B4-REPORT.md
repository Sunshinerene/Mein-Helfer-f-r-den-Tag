# CODEX-6B.4 · Gesprächsvertrag

Freigabe durch Projektzentrale 00/1 in `docs/PROJECT_STATUS.md`. Basis und geprüfter HEAD von `entwicklung/sunny-2` waren `e69eebbacc80125165c1b53ca6e9e000a9052be0`. Die Entwicklung erfolgte auf `codex/sunny-conversation-contract`.

## Anforderungen und Umsetzung

| Anforderung aus dem freigegebenen Paket | Umsetzung und synthetische Prüfung |
| --- | --- |
| Getrennter, typisierter Gesprächsvertrag | `ConversationRequest`, `ConversationSession`, `ConversationBackend` und `ConversationResult` bleiben im neuen Paket. Der vorhandene ACK/CLARIFY-Vertrag ist unverändert. |
| Sitzungsverlauf und explizites Budget | Jede Session besitzt ihren Verlauf. Tests prüfen mehrstufige Gespräche, Session-Isolation, unveränderbare Snapshots sowie Begrenzung nach Anzahl und UTF-16-Codeeinheiten. Die aktuelle Eingabe und beide Seiten vorheriger Turns zählen zum Kontextbudget. Der Verlauf enthält die jüngsten zusammenhängenden, vollständig passenden Turns. |
| Ungültige Eingaben, Backendfehler und Busy | Eingabevalidierung erfolgt vor dem Backendaufruf. Typisierte Fehler enthalten keine Backend-Meldungen. Pro Engine hat das Backend höchstens einen laufenden Aufruf über alle Sessions hinweg. Der Busy-Test prüft Konkurrenz innerhalb und zwischen Sessions. |
| Kooperativer Abbruch | Ein explizites Cancel-Signal und Backend-Checkpoints beenden die simulierte Verarbeitung. Cancel und finaler Verlaufs-Commit sind gegeneinander serialisiert. Ausbleibende Checkpoints verzögern die Rückkehr. Der Abbruchtest prüft die Freigabe des Backend-Zugriffs erst nach Rückkehr. Ein Thread-Interrupt bleibt gesetzt. |
| Untrusted Modelltext, keine Aktionen oder Netzwerk | Antworten sind als `UntrustedConversationText` markiert. Nichtdruckbare Steuerzeichen und ungültiges UTF-16 werden abgewiesen. Anweisungs- oder URL-ähnliche Fixtures bleiben Text. Es gibt keinen Aktionspfad, Netzwerktransport oder Cloud-Fallback. |
| Fake und Grenzen | `FakeConversationBackend` arbeitet ausschließlich mit synthetischen, lokalen Testantworten. Android, native Inferenz, Memory Foundation und Orchestrator sind unberührt. |

## Dateien

- `sunny-core/src/main/kotlin/de/meinhelfer/sunny/core/conversation/ConversationContract.kt`
- `sunny-core/src/main/kotlin/de/meinhelfer/sunny/core/conversation/ConversationEngine.kt`
- `sunny-core/src/test/kotlin/de/meinhelfer/sunny/core/conversation/FakeConversationBackend.kt`
- `sunny-core/src/test/kotlin/de/meinhelfer/sunny/core/conversation/ConversationEngineTest.kt`
- `docs/CODEX-6B4-REPORT.md`

## Ausgeführte Prüfungen

- `gradle --version` bestätigte Gradle 8.11.1 und Eclipse Adoptium JDK 17.0.17.
- `gradle -p sunny-core --no-parallel --max-workers=2 clean test` lief auf der unveränderten Basis und separat auf diesem Branch erfolgreich.
- JUnit-XML auf der Basis: 131 Tests, darunter 96 Routing-, 14 Orchestrator- und 21 Runtime-Tests. 0 Fehler, 0 Ausfälle, 0 übersprungen.
- JUnit-XML auf dem Paketbranch: 147 Tests, davon 131 namentlich identische Bestandstests und 16 neue Gesprächsvertragstests. 0 Fehler, 0 Ausfälle, 0 übersprungen. Gradles Kotlin-Konfigurations-Task meldete `SKIPPED`. Die Test-Task lief vollständig.
- `jdeps -verbose:class -filter:none sunny-core/build/libs/sunny-core-0.1.jar` zeigte für das Paket ausschließlich Java/Kotlin und eigene Klassen. Der Diff wurde mit `git diff --cached --check` und der freigegebenen Pfadliste geprüft.

## Grenzen und Rücknahme

Der Vertrag sieht eine Engine pro Backend vor. Die aufrufende Seite besitzt die Session und verwirft den Handle zum Sitzungsende. Die Freigabe läuft bis zur Rückkehr oder bis zur Ausnahme des Backends. Ein nicht kooperierendes Backend lässt sich durch diesen Vertrag nicht unterbrechen. Die Größenlimits zählen UTF-16-Codeeinheiten. Die Umrechnung auf ein späteres Modell-Tokenbudget und die Eignung für native Inferenz bleiben offen. Eine künftige Anzeige muss die markierte Ausgabe als Nur-Text behandeln.

Rollback vor Abnahme durch Schließen des Draft PR. Bei späterer genehmigter Übernahme kann ein eigener Revert die hinzugefügten Dateien entfernen. Unabhängige CI und Review durch Projektzentrale 00/1 stehen aus.
