# CODEX-6B.2 – Sunny Android Runtime Adapter v0.1

Ausgangscommit `fd6f4ac1da8dd807b76d8fdf5cb3f3b2e7306565` auf
`entwicklung/sunny-2`. Der Adapter ist eine Android-Library für Java 17,
Kotlin 2.2.21, AGP 8.10.1, Gradle 8.11.1, compileSdk 35 und minSdk 26.
LiteRT-LM bleibt auf 0.16.1.

## Architektur und Lebenszyklus

`LiteRtModelBackend(model: File, cache: File)` implementiert
`LocalModelBackend` und liefert eine einmal nutzbare `LocalModelSession`.
Der Aufrufer kann das Backend an `ManagedLocalRuntime` übergeben. Der
Core erhält ausschließlich die extrahierte einzelne Textantwort eines
Modellaufrufs ohne Tool-Calls oder Zusatzkanäle und prüft weiterhin exakt
auf `ACK` und `CLARIFY`. Bei Fehlern bleibt die 6B.1-Regelantwort lokal.
Die bestehende Host-Routing-Entscheidung bleibt im bisherigen Code.

Der Composite Build bindet das unveränderte JVM-Projekt über eine
explizite Dependency-Substitution ein. Das erhält seinen eigenständigen
JUnit-Build und erlaubt der Android-Library, seine öffentlichen Verträge
als JVM-Artefakt zu verwenden. Die App bindet die Library ein. LiteRT-LM
ist Implementierungsdetail der Library. Coroutines Core 1.9.0 wird direkt
für Dispatcher, Cancellation und Mutex deklariert und entspricht der
bereits transitiv verwendeten LiteRT-Coroutines-Version. JUnit 4.13.2 ist
eine Testabhängigkeit.

`ModelAccess.process` besitzt den einzigen prozessweiten Mutex für die
App-Pfade und produktiven Adapterinstanzen. Import, Löschen und
bestehender Modelltest warten wie zuvor cancellable. Der Adapter meldet
konkurrierende Zugriffe sofort als `Busy`; der unveränderte Core bildet
diesen Fehler auf die jeweilige Phase und den lokalen Fallback ab.
Der Import hält dieselbe Lease vom Kopieren über den Probelauf bis zum
atomaren Ersetzen. Der vorhandene Pfad
`noBackupFilesDir/sunny_models/model.litertlm` bleibt erhalten.

Die Statusprüfung erkennt einen fehlenden Pfad, einen inkompatiblen
Dateityp und unpassende LiteRT-LM-Kopfdaten. Sie prüft Magic, Hauptversion
und Header-Grenzen gemäß dem 0.16.1-Reader. Beim Öffnen wird die Prüfung
unter derselben Lease wiederholt, die anschließend bis zum Ende der
Session gehalten wird. Die vollständige Modell- und Gerätekompatibilität
wird bei der nativen Initialisierung geprüft.

Die eigentliche JNI-Arbeit läuft strukturiert auf `Dispatchers.IO`.
Engine, Conversation, synchroner Modellaufruf und native Freigabe liegen
im selben Worker-Aufruf. Die Core-Session gibt anschließend die Lease
frei. Die Conversation wird vor der Engine geschlossen. Ein
fehlgeschlagenes Schließen der Conversation lässt die Engine unangetastet
und sperrt den gemeinsamen Zugriff bis zum Prozessneustart. Dasselbe
gilt bei fehlgeschlagenem Engine-Close. Bei einer fehlgeschlagenen
Initialisierung ohne freigebbaren Handle gilt native Freigabe als
unbestätigt. Der Import behält in diesem Fall die temporäre Datei. Erst
nach einem Prozessneustart kann ein erneuter Import sie ersetzen. Die installierte Datei wird erst nach erfolgreicher Prüfung
ersetzt.

Der Adapter protokolliert weder Prompt noch Modellpfad oder
Originalfehler. LiteRT-LM wird vor der Engine-Erstellung auf
`LogSeverity.INFINITY` gesetzt. Andere interne native Ausgabekanäle und
Gerätelogging sind in 6B.3 zu verifizieren. Erwartete Fehler liefern
fest definierte Fehlercodes bzw. feste Importmeldungen. Cancellation und
Unterbrechungen werden weitergegeben. Der bisherige Modelltest verwendet
weiterhin seinen deutschen Prompt und `Message.toString()`. Dieser
Legacy-Pfad ist vom Core-Antwortvertrag getrennt.

## Anforderungsabgleich

| ID | Umsetzung und Nachweis |
| --- | --- |
| AR-01 | Library verwendet Dateipfade und Core-Verträge. Der Abhängigkeitsgraph enthält keine Host-App. |
| AR-02 | Composite Build mit eigenständigem Core-Clean-Test. |
| AR-03 | Bestehender Modellpfad und atomarer Import, Headerprüfung und erneute Validierung unter Lease. |
| AR-04 | Gemeinsamer Mutex, synthetische Tests für Konkurrenz zwischen Anfrage, Import und Löschung. |
| AR-05 | Synchroner LiteRT-Aufruf auf IO-Worker mit strukturierter Freigabe. |
| AR-06 | Kooperative Grenzprüfungen, Cancellation und Unterbrechung, Tests für verzögerte native Rückkehr. |
| AR-07 | Quarantäne nach unsicherem Engine- oder Conversation-Close mit gesperrten Folgeoperationen. |
| AR-08 | Feste Fehlercodes und keine Runtime-Netzwerk-, Download- oder Logging-Funktion. APK-Prüfung für bestehende Berechtigungsgrenzen. |
| AR-09 | Unveränderte Core-Validierung mit synthetischen Token-, Ausgabe- und Fallback-Tests. |
| AR-10 | Legacy-Prompts, Modellpfad und Bedienabläufe bleiben erhalten. Debug- und Lab-Builds prüfen die Modul-Integration. |

## Geänderte Dateien

- `settings.gradle.kts`, Root-`build.gradle.kts` und `app/build.gradle.kts` für die Modulanbindung.
- `SunnyModelStore.kt` und `SunnyLiteRtTest.kt` für gemeinsame Lease, sichere Freigabe und bereinigte Importfehler.
- `sunny-runtime-android/` mit Build-Datei, Ignore-Regel, Modellzugriff, LiteRT-Backend, nativer Fassade, Legacy-Aufruf, synthetischen Tests und diesem Bericht.

## Ausgeführte Prüfungen

Startprüfung mit `java -version`, `javac -version`, `gradle --version`,
`git status --porcelain --untracked-files=all`, `git ls-remote`,
`git fetch origin refs/heads/entwicklung/sunny-2` und `git rev-parse HEAD`.
Der Arbeitsbranch wurde mit `git switch -c codex/sunny-runtime-android FETCH_HEAD`
erstellt. Die Umgebungsprüfung bestätigte Temurin 17.0.17 und Gradle
8.11.1. Für den Android-Build wurden SDK 35 und Build-Tools 35.0.0
verwendet. Das Build-Tools-Archiv wurde gegen Googles Repository-Metadaten
geprüft. Die Toolchain wurde pro Bash-Sitzung aktiviert.

```bash
source /workspace/.mein-helfer-android/activate.sh
cd /workspace/Mein-Helfer-f-r-den-Tag
export ANDROID_HOME=/workspace/.meinhelfer-toolchain/android-sdk
export ANDROID_USER_HOME=/workspace/.meinhelfer-toolchain/android-user
gradle -p sunny-core --no-daemon --max-workers=4 clean test
gradle --no-daemon --max-workers=4 :sunny-runtime-android:test
gradle --no-daemon --max-workers=4 :app:testDebugUnitTest :app:assembleDebug :app:assembleLab
gradle --no-daemon --max-workers=4 :sunny-runtime-android:dependencies --configuration debugRuntimeClasspath
```

Die JUnit-XML-Berichte wurden mit Python 3 ausgewertet.

| Suite | Ausgeführt | Bestanden | Fehlgeschlagen | Fehler | Übersprungen |
| --- | ---: | ---: | ---: | ---: | ---: |
| Core vor Änderungen | 131 | 131 | 0 | 0 | 0 |
| Core im Abschlusslauf | 131 | 131 | 0 | 0 | 0 |
| Runtime-Adapter Debug | 13 | 13 | 0 | 0 | 0 |
| Runtime-Adapter Release | 13 | 13 | 0 | 0 | 0 |
| App Debug | 93 | 93 | 0 | 0 | 0 |

Der erste kombinierte Adaptertestlauf deckte eine wartende Statusabfrage
bei belegter Lease auf. Sie wurde auf den sofortigen Busy-Pfad
umgestellt. Der Unterbrechungstest berücksichtigt die JVM-Stacktrace-
Rekonstruktion von Coroutines. Die Tabelle zeigt die vollständigen
Abschlussläufe mit der korrigierten Fassung.

Debug und Lab wurden als APK gebaut. Die Shell-Schritte aus
`.github/workflows/build-lab.yml` wurden mit `aapt dump badging`,
`aapt dump xmltree` und `aapt dump permissions` wiederholt. Lab hat die
Paket-ID `de.meinhelfer.tag.lab` und startet `LabActivity`. Produktive
Einstiegspunkte und die im Workflow gesperrten Berechtigungen fehlen im
Lab. Debug hat die Paket-ID `de.meinhelfer.tag` und enthält keine
`LabActivity`. Der SDK-Metrikhinweis der Cloud-Umgebung und AGPs
Hinweis auf unverändert verpackte native Bibliotheken verhinderten die
Builds nicht.

`git diff --check`, der Diff zum Ausgangscommit und der Abgleich der
unversionierten Dateien bestätigten die Dateigrenzen. Das Room-Schema
`app/schemas/de.meinhelfer.tag.data.AppDatabase/31.json` wurde vor und
nach den Builds per `sha256sum -c /tmp/sunny-6b2-schema` geprüft.
SHA-256: `5c69b02f6e4a30d07693f61d1fd796d180ea068df65d9560d5619d61e4dd8c37`.

## Grenzen und 6B.3

Blockierender JNI-Code hält die Lease bis zu seiner sicheren Rückkehr.
Kooperative Prüfpunkte können JNI-Aufrufe erst bei der Rückkehr
beenden. Die JVM-Tests verwenden eine injizierte native Fassade und
synthetische Header. 6B.3 prüft auf Geräten echte Modellkompatibilität,
CPU-Inferenz, Latenz, Speicherverbrauch, native Logs,
Cancellation-Grenzen, Quarantäne und Wiederanlauf nach Prozessneustart.

Rollback erfolgt mit `git revert <6B.2-Commit>` auf dem zuständigen
Entwicklungsbranch. Die Änderung erfordert keine Datenmigration.
