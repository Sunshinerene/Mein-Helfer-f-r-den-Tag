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

## CODEX-6B.1 – Sunny Runtime Foundation v0.1

Ausgangsstand ist `bf7a397ce2b1f67276a2a17e13fbda2ec0a55426` von
`entwicklung/sunny-2`. Die Runtime bleibt ein optionaler Adapter für
`LocalRuntime`. Der Orchestrator und die Verarbeitung von `ACK` und
`CLARIFY` behalten den Stand aus 6A.

### Schnittstellen und Lebenszyklus

`ManagedLocalRuntime` prüft vor jeder Generierung den Modellstatus über
`LocalModelBackend`. Die explizite Statusabfrage liefert `Available`,
`Missing` oder `Incompatible` als Momentaufnahme. Eine fehlende oder
inkompatible Ressource stoppt den Aufruf vor dem Öffnen der Session.
Reale Adapter müssen den Modellstatus beim Öffnen erneut absichern, falls
sich das Modell zwischen Prüfung und Öffnen ändern kann.

Eine atomare Sperre pro Manager schützt alle Backend-Aufrufe vom Modellstatus
bis zum Abschluss der Freigabe. Weitere Aufrufe melden sofort `Busy`.
Der Backend-Vertrag sieht je Backend-Instanz einen Manager vor. Nach
erfolgreichem Öffnen besitzt der Manager die `LocalModelSession` und ruft
`close()` genau einmal auf, sobald die Inferenz zurückkehrt oder vollständig
abgewickelt ist. Das Backend verantwortet Teilallokationen bei fehlgeschlagenem
Öffnen sowie die synchrone Freigabe innerhalb von `close()`.

`RuntimeDeadline` nutzt eine injizierbare monotone Nanosekundenquelle
(`System.nanoTime()` als Standard) und ein positives Zeitbudget pro Aufruf.
Es umfasst Modellprüfung, Initialisierung und Inferenz. Der Manager prüft das
Budget vor und nach den relevanten Phasen. Der Adapter prüft es zusätzlich
an sicheren Unterbrechungspunkten. Die Session-Freigabe bleibt auch bei
abgelaufenem Budget verpflichtend und kann die Gesamtdauer verlängern.

Erwartete Fehler liefern `LocalRuntimeException` mit festem Fehlercode und
ohne Backend-Fehlertext, Ursache oder unterdrückte Backend-Ausnahmen.
Externe `CancellationException` und `InterruptedException` werden im
Original weitergegeben. Adapter müssen daher Abbruchausnahmen ohne sensible
Inhalte erzeugen. Die Runtime protokolliert keine Eingaben oder Fehler.
Eine fehlerhafte Modellausgabe geht zur bestehenden Token-Prüfung im
Orchestrator. Der Orchestrator liefert bei lokalen Fehlern seine feste
Regelantwort mit der ursprünglichen lokalen Routing-Entscheidung.

### Anforderungsabgleich

| ID | Umsetzung und Nachweis |
| --- | --- |
| RT-01 | Austauschbares Backend und Session, JVM-Verträge ohne Android-Importe |
| RT-02 | Statusabfrage unterscheidet verfügbare, fehlende und inkompatible Modelle |
| RT-03 | Synthetische Tests belegen für fehlende und inkompatible Modelle null Öffnungs- und Inferenzaufrufe |
| RT-04 | Injizierte Uhr prüft das Zeitbudget an der Grenze und über mehrere Phasen. Typisierte Fehler decken Status, Öffnen, Inferenz und Freigabe ab |
| RT-05 | Identität der ursprünglichen Cancellation und Unterbrechung in Tests geprüft |
| RT-06 | Busy-Tests decken überlappende Aufrufe und den Zeitraum der Freigabe ab |
| RT-07 | Ereignisreihenfolgen prüfen einmaliges Schließen nach Erfolg, Fehler, Timeout und Abbruch sowie Wiederverwendung |
| RT-08 | Quellen enthalten keinen Transport. Integrationstests prüfen den lokalen Fallback bei erlaubtem Online-Routing |
| RT-09 | Fehlercodes ohne Backend-Texte und Ursachen. Tests prüfen auch fehlende unterdrückte Fehlerdetails |
| RT-10 | Alle 110 Tests aus 6A unverändert erhalten |
| RT-11 | Eigene synthetische Runtime-Tests mit manueller Uhr und kontrollierten Continuations |
| RT-12 | Git-Diff ist auf `sunny-core/` begrenzt. Die Prüfsumme des Room-Schemas ist identisch |

### Verifikation und Dateiumfang

Die Toolchain wurde pro Bash-Sitzung aktiviert. Die Startprüfung verwendete
`java -version`, `javac -version`, `gradle --version`,
`git status --porcelain --untracked-files=all`, `git branch --show-current`,
`git rev-parse HEAD` und `git ls-remote origin` für Basis und Arbeitsbranch.
Der Checkout wurde mit `git fetch origin refs/heads/entwicklung/sunny-2` und
`git switch -c codex/sunny-runtime FETCH_HEAD` aufgesetzt.

```bash
source /workspace/.mein-helfer-android/activate.sh
cd /workspace/Mein-Helfer-f-r-den-Tag
gradle -p sunny-core --no-daemon --max-workers=4 clean test
gradle -p sunny-core --no-daemon --max-workers=4 dependencies --configuration runtimeClasspath
```

Beide Testläufe liefen mit Temurin und javac 17.0.17 und Gradle 8.11.1.
Die JUnit-XML-Berichte wurden mit Python 3 ausgewertet.

| Testlauf | Ausgeführt | Bestanden | Fehlgeschlagen | Übersprungen |
| --- | ---: | ---: | ---: | ---: |
| Ausgangsstand vor Änderungen | 110 | 110 | 0 | 0 |
| Abschlusslauf, vorhandene Tests | 110 | 110 | 0 | 0 |
| Abschlusslauf, neue Runtime-Tests | 21 | 21 | 0 | 0 |
| Abschlusslauf, gesamt | 131 | 131 | 0 | 0 |

Die XML-Berichte enthalten zusätzlich null Testfehler (`errors`).
Der Runtime-Classpath enthält weiterhin nur Kotlin-Standardbibliothek
2.2.21 und die transitiven JetBrains-Annotationen 13.0.

`git diff --check` und der Diff zum Ausgangscommit prüfen Dateigrenzen
und unveränderte Basistests. `sha256sum -c /tmp/sunny-6b1-schemas.sha`
prüft das unversionierte Room-Schema
`app/schemas/de.meinhelfer.tag.data.AppDatabase/31.json`.
SHA-256 vor dem Checkout und nach dem Build:
`5c69b02f6e4a30d07693f61d1fd796d180ea068df65d9560d5619d61e4dd8c37`.

| Datei | Änderung |
| --- | --- |
| `src/main/kotlin/de/meinhelfer/sunny/core/ManagedLocalRuntime.kt` | Runtime, Backend- und Session-Verträge, Status, Frist und Fehlercodes |
| `src/test/kotlin/de/meinhelfer/sunny/core/ManagedLocalRuntimeTest.kt` | Synthetische Backend- und Integrationstests |
| `README.md` | Architektur, Testnachweise und Übergabe |

### Grenzen und Folgepakete

Kooperative Zeitlimits greifen erst, wenn der native Modellcode einen
sicheren Prüfpunkt erreicht oder an den Manager zurückkehrt. Ein blockierendes Backend hält den Slot und seine
Ressourcen bis zur sicheren Rückkehr. Externe Coroutine-Cancellation muss
der Adapter über seine Suspendierung bzw. eigene sichere Abbruchpunkte
melden. 6B.1 enthält kein konkretes Modell und keine gerätespezifische
Verifikation.

- **6B.2** benötigt einen separat freigegebenen Android-Adapter mit lokalem
  Modellstatus und Kompatibilitätsprüfung, sicherem Besitzwechsel sowie
  kooperativer Cancellation und nachgewiesener Ressourcenfreigabe.
- **6B.3** benötigt separat freigegebene Gerätetests für native Abbruchgrenzen,
  Ressourcenfreigabe, Busy-Verhalten und Wiederverwendbarkeit.

Rollback auf dem Entwicklungsbranch per `git revert <6B.1-Commit>`.
Der Revert entfernt die Runtime-Erweiterung und ihre Tests und stellt die
vorherige README wieder her. Datenmigrationen sind für 6B.1 nicht erforderlich.
