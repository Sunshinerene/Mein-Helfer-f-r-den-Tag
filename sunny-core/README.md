# Sunny Core Foundation v0.1

Eigenständiges Kotlin/JVM-Projekt für CODEX-6A. Grundlage der Implementierung ist
`0a6a19b273f8c1cbd33ada64159747866c266793` auf `codex/sunny-brain`.
Die Routing-Referenz aus dem 6A-Vertrag (`469dff3`) und der Ausgangscommit
haben denselben App-Quellstand. Die Routing-Tabelle entspricht
`SunnyRouting` in der vorhandenen `SunnyLocalEngine.kt`. Die Schnittstellen
für Modell, Einstellungen und Aktionsfreigabe der Android-App wurden
lesend geprüft.

## Architektur und Grenzen

- `Contracts.kt` enthält eigenständige Request-, Response-, Aktions- und Runtime-Verträge.
- `SunnyRouting.kt` enthält die Routing-Schnittstelle und die deterministische Standardimplementierung.
  Online-Berechtigung setzt Internetverfügbarkeit, aktivierte Online-Funktion,
  anfragebezogene Freigabe und Abwesenheit privater Daten voraus.
  Der Kontext setzt standardmäßig alle Freigaben auf `false` und nimmt private Daten an.
- `SunnyOrchestrator.kt` prüft Eingaben vor Routing, Policy und Runtime.
  Er akzeptiert bis zu 4096 UTF-16-Codeeinheiten und erlaubt Tabulator,
  Wagenrücklauf und Zeilenumbruch neben regulärem Text. Aktions-IDs verwenden
  Kleinbuchstaben, Ziffern und `_.-`, beginnen mit einem Buchstaben und sind
  höchstens 64 Zeichen lang.
- Die ausgewählte Route bleibt als Entscheidungsmetadatum erhalten. Bei fehlender
  Runtime, Modellfehler oder ungültiger Modellausgabe liefert der Orchestrator
  eine feste Regelantwort mit Fallback-Grund. Er trifft dabei keine zweite
  Routing-Entscheidung. Nur die exakten Modell-Tokens `ACK` und `CLARIFY`
  werden in feste Antworttypen umgesetzt. `CancellationException` und
  `InterruptedException` werden an den Aufrufer weitergegeben.
- Die Aktions-Policy prüft ausschließlich vom Aufrufer gelieferte Kandidaten.
  Standardverweigerung und Policy-Fehler ergeben `Denied`.
  `AllowedForReview` ist ein Prüfergebnis. Der Core besitzt keinen Executor.
- `FakeLocalRuntime.kt`, `SunnyRoutingTest.kt` und `SunnyOrchestratorTest.kt`
  verwenden synthetische Daten. Der Executor-Spy ist Teil eines simulierten
  Testkonsumenten mit Positivkontrolle.
- `settings.gradle.kts`, `build.gradle.kts` und `.gitignore` definieren
  den eigenständigen Build und halten lokale Build-Ausgaben aus Git heraus.

Online bleibt ein Metadatum. Der Core enthält keinen Transport, keine
Persistenz, keine Protokollierung und keine Datenbank- oder Host-Anbindung.
Die private Einstufung und die Freigaben im Routing-Kontext werden vom
Aufrufer gesetzt. Das Projekt implementiert keine automatische
Datenerkennung. Die lokale Runtime ist eine Schnittstelle für spätere
Adapter. 6A umfasst feste Antworttypen ohne freie Modellantworten sowie
JUnit-Tests auf der JVM.

## Build und Prüfung

Voraussetzungen sind Java 17, Gradle 8.11.1, Kotlin 2.2.21 und JUnit 4.13.2.
Die Kotlin-Standardbibliothek stammt aus dem Kotlin/JVM-Plugin. JUnit gehört
nur zur Testkonfiguration. Der Build benötigt Zugriff auf die Gradle- und
Maven-Artefakte oder einen vorbereiteten Cache.

```bash
source /workspace/.mein-helfer-android/activate.sh
cd /workspace/Mein-Helfer-f-r-den-Tag
hash -r
java -version
javac -version
gradle --version
git status --short
git branch --show-current
git rev-parse HEAD
git ls-remote origin refs/heads/codex/sunny-brain
gradle -p sunny-core --no-daemon --max-workers=4 test
```

Vor dem Build wurde der Zielbranch mit `git fetch origin refs/heads/codex/sunny-brain`
abgerufen und mit `git switch -c codex/sunny-brain FETCH_HEAD` lokal angelegt.
Branch, Commit und Status wurden erneut geprüft. Das Room-Schema wurde mit
`sha256sum -c /tmp/sunny-6a-schema.sha` geprüft.

## Testergebnis

Der Gradle-Build lief mit Temurin Java und javac 17.0.17 sowie Gradle 8.11.1
erfolgreich. Die JUnit-XML-Dateien im Verzeichnis
`sunny-core/build/test-results/test/` ergeben:

| Testklasse | Ausgeführt | Bestanden | Fehlgeschlagen | Übersprungen |
| --- | ---: | ---: | ---: | ---: |
| SunnyRoutingTest | 96 | 96 | 0 | 0 |
| SunnyOrchestratorTest | 14 | 14 | 0 | 0 |
| Gesamt | 110 | 110 | 0 | 0 |

Die ausgewerteten Routing-Berichte enthalten 96 eindeutige Kombinationen.
`gradle -p sunny-core --no-daemon --max-workers=4 dependencies --configuration runtimeClasspath`
zeigte ausschließlich die Kotlin-Standardbibliothek 2.2.21 und deren
transitive JetBrains-Annotations-Abhängigkeit (13.0). Der Core deklariert
keine zusätzlichen produktiven Abhängigkeiten.

## Soll-Ist-Abgleich CODEX-6A

| Anforderung | Prüfnachweis |
| --- | --- |
| Eigenständiger JVM-Build | Eigene Gradle-Einstellungen, Toolchain 17 und Testlauf |
| Keine Android- oder Host-Abhängigkeit | Quellcode- und Runtime-Classpath-Prüfung |
| 96 Kombinationen mit unabhängiger Erwartung | Parametrisierte JUnit-Fälle mit expliziter Solltabelle |
| Ungültige und leere Eingaben abweisen | Tests für Leertext, Kontrollzeichen, Längen und Aktions-IDs |
| Fehlende oder fehlerhafte Modelle sicher behandeln | Feste Regelantwort mit eindeutigem Fallback-Grund |
| Fehlerhafte und nicht vertrauenswürdige Ausgabe behandeln | Whitelist-Tests und Tests gegen modellgenerierte Aktionsvorschläge |
| Abbruch und Cancellation weitergeben | Tests für unterbrochene bzw. abgebrochene Aufrufe, einschließlich suspendierter Generierung |
| Deny-by-default und null Executor-Aufrufe bei Ablehnung | Tests für Standardverweigerung, explizite Ablehnung und Policy-Fehler |
| Keine reale Aktionsausführung | Produktschnittstellen enthalten nur Policy und Entscheidungen |
| Keine reale Netzwerkkommunikation | Online-Routen werden ausschließlich als Antwortmetadaten verarbeitet |
| Änderungen ausschließlich in `sunny-core/` | Git-Diff zum Ausgangscommit und erneute Schema-Prüfsumme |
| Alle Pflichttests bestanden und keiner übersprungen | JUnit-XML-Auswertung |

Die Solltabelle umfasst Routine mit 32 × Rules, Language mit 16 × OnDevice,
1 × Online und 15 × Rules sowie FreshInformation mit 2 × Online und 30 ×
Unavailable.

## Rollback

Den 6A-Commit bei Bedarf auf dem Entwicklungsbranch mit
`git revert <6A-Commit>` zurücknehmen. Der Commit enthält ausschließlich
`sunny-core/`. Eine Datenmigration oder Wiederherstellung ist nicht erforderlich.
Lokale, ignorierte Build-Ausgaben unter `sunny-core/` können separat entfernt werden.
