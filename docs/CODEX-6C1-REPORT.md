# CODEX-6C.1 · Memory Foundation

Freigabe durch Projektzentrale 00/1 in `docs/PROJECT_STATUS.md`. Basis und geprüfter HEAD von `entwicklung/sunny-2` waren `e69eebbacc80125165c1b53ca6e9e000a9052be0`. Die Entwicklung erfolgte auf `codex/sunny-memory-foundation`.

## Anforderungen und Umsetzung

| Anforderung aus dem freigegebenen Paket | Umsetzung und synthetische Prüfung |
| --- | --- |
| Kurzlebiger Sitzungskontext und explizites Opt-in | Sitzungsdaten bleiben in ihrer Sitzung. Langzeitdaten erfordern ein explizites Opt-in pro Nutzer und Person sowie eine aktuelle Freigabe für die angefragte Operation. Widerruf löscht die Langzeiteinträge des exakten Scopes. |
| Deny by default, Scope und Export | Der Standard-Guard verweigert jede Operation. Tests prüfen den exakten Nutzer-, Personen- und Sitzungsscope, verweigerte Änderungen und ein vom Kontextlesen getrenntes Exportrecht. IDs und Modelltext erteilen keine Berechtigung. |
| Explizites Speichern, Korrigieren und Löschen | UUID bleibt bei Korrektur stabil. Ursprüngliche Herkunft und Herkunft der letzten Korrektur sind getrennt. Korrektur und Löschung wirken auf alle zukünftigen Kontextabfragen und Exporte. Früher ausgegebene Snapshots bleiben beim Aufrufer. |
| Begrenzter Kontext und injizierbare Zeit | Sitzungseinträge laufen bei Erreichen der monotonen TTL ab. Auswahl liefert passende ganze Einträge in absteigender Einfügereihenfolge mit Limit für Anzahl und UTF-16-Codeeinheiten. Korrektur verändert weder Position noch ursprüngliche Laufzeit. |
| Ungültige Daten und Nebenläufigkeit | Opaque IDs, Text und Limits werden geprüft. Operationen sind pro Store-Instanz synchronisiert. Der parallele Kapazitätstest prüft zwei konkurrierende Writes und die Unabhängigkeit ausgegebener Snapshots. |
| JVM-Verträge und synthetischer Store | `MemoryRecords`, `SessionContext` und `LongTermMemory` definieren den Zugriff. `InMemoryStore` speichert ausschließlich im Arbeitsspeicher. Orchestrator, Android, Datenbank, Netzwerk und Abhängigkeiten liegen außerhalb des Diffs. |

## Dateien

- `sunny-core/src/main/kotlin/de/meinhelfer/sunny/core/memory/MemoryContracts.kt`
- `sunny-core/src/main/kotlin/de/meinhelfer/sunny/core/memory/InMemoryStore.kt`
- `sunny-core/src/test/kotlin/de/meinhelfer/sunny/core/memory/InMemoryStoreTest.kt`
- `docs/CODEX-6C1-REPORT.md`

## Ausgeführte Prüfungen

- `gradle --version` bestätigte Gradle 8.11.1 und Eclipse Adoptium JDK 17.0.17.
- `gradle -p sunny-core --no-parallel --max-workers=2 clean test` lief auf der unveränderten Basis und separat auf diesem Branch erfolgreich.
- JUnit-XML auf der Basis: 131 Tests, darunter 96 Routing-, 14 Orchestrator- und 21 Runtime-Tests. 0 Fehler, 0 Ausfälle, 0 übersprungen.
- JUnit-XML auf dem Paketbranch: 144 Tests, davon 131 namentlich identische Bestandstests und 13 neue Memory-Tests. 0 Fehler, 0 Ausfälle, 0 übersprungen. Gradles Kotlin-Konfigurations-Task meldete `SKIPPED`. Die Test-Task lief vollständig.
- `jdeps -verbose:class -filter:none sunny-core/build/libs/sunny-core-0.1.jar` zeigte für das Paket ausschließlich Java/Kotlin, eigene Klassen und den bestehenden Core-`NanoClock`. Der Diff wurde mit `git diff --cached --check` und der freigegebenen Pfadliste geprüft.

## Grenzen und Rücknahme

Der Speicher ist ein synthetischer Prozessspeicher. Abgelaufene Sitzungsdaten sind ab der TTL-Grenze nicht mehr abrufbar und werden beim nächsten autorisierten Sitzungsschreibvorgang freigegeben. Explizites Leeren räumt eine Sitzung vollständig auf. Die Kapazitätsgrenzen gelten pro Store-Instanz. Ein künftiger Host muss authentifizierte Aufrufer und aktuelle Grants liefern, Snapshot-Lebensdauer kontrollieren und persistente Verschlüsselung, Keystore und Löschverhalten separat umsetzen und abnehmen lassen.

Rollback vor Abnahme durch Schließen des Draft PR. Bei späterer genehmigter Übernahme kann ein eigener Revert die hinzugefügten Dateien entfernen. Unabhängige CI und Review durch Projektzentrale 00/1 stehen aus.
