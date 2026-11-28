# CODEX-LAUNCHER-IDEA-01 · Sunny Launcher v0.1

## Entwicklungsstand

| Referenz | Geprüfter Stand |
| --- | --- |
| Ausführungsfreigabe aus Chat 00/1 | `d314d5314847c974902c175317d1e183c267057b` |
| Ausgecheckter und getesteter Integrationsstand von `entwicklung/sunny-2` | `651c9e970c4eaffea5190bbbd514b61b14d44e99` |
| Arbeitsbranch | `codex/sunny-launcher-idea-v01` |
| Paket, Version | `de.meinhelfer.sunny.launcher.idea`, Version Code `1`, Version Name `0.1-idea` |
| Werkzeuge | Temurin JDK 17.0.17, Gradle 8.11.1, AGP 8.10.1, Android SDK 35, Build Tools 35.0.0, minSdk 26, targetSdk 35 |

`651c9e9` ergänzt seit der Freigabe ausschließlich den Projektstatus. Seit dem zuletzt unabhängig geprüften ausführbaren Integrationsstand `545679dce2ab8fcd375f15709fd7627d6dad6cc4` lagen auf der getesteten Basis ausschließlich Änderungen unter `docs/`. Die Core-Baseline wurde trotzdem auf der tatsächlichen Basis neu ausgeführt. Der Arbeitsumfang beschränkt sich auf das neue, eigenständig gebaute Projekt `sunny-launcher-idea/` und diesen Bericht. Die App verwendet Android-Standard-APIs und hat keine Laufzeitbibliotheken als Projektabhängigkeiten. Die automatisierten Tests verwenden das im Bestand eingesetzte JUnit 4.13.2.

Vor dem Push stand der Zielbranch auf `75fbd495341b8aa1a6a65a51c0f26ca665800be3`. Der zusätzliche Commit betraf ausschließlich `docs/PROJECT_STATUS.md` und überschnitt sich mit keinem Auftragspfad. Der Draft behält die tatsächlich getestete Basis.

## Bildschirmkonzept und Bedienung

- **Start.** Sonnys winkendes Motiv steht im Mittelpunkt einer Farbfläche mit einem weichen Lichtkreis hinter dem Bild. Die Kopfzeile kennzeichnet die App als „Ideenprototyp“. Uhr und Datum folgen Gerätesprache, Zeitzone und dem eingestellten Stundenformat. Favoriten dienen als Schnellzugriffe. Auf breiten Ansichten stehen Portrait und Infokarten nebeneinander. Die Karten werden auf schmalen Ansichten und bei größerer Systemschrift untereinander angezeigt. Der Inhaltsbereich kann scrollen, die Hauptnavigation bleibt erreichbar. Schriften verwenden skalierbare Android-Einheiten, die Hauptaktionen haben mindestens 48 dp hohe Touch-Flächen.
- **Apps.** Die Liste zeigt die von Android sichtbaren, gewöhnlichen startbaren Activities mit Label und Symbol. Ein Tipp auf die Zeile fordert den Start des exakten Eintrags an. Der separate Stern setzt oder entfernt den Favoriten. Vor einem Start wird die Activity in ihrem Paket erneut über `MAIN / LAUNCHER` geprüft. Ein inzwischen fehlender oder abgewiesener Eintrag führt zu einer festen Fehlermeldung und erneuter Abfrage. Fehlende Favoriten haben eine Entfernen-Aktion. Liefert Android keine passenden Einträge, zeigt die App ausdrücklich beschriftete synthetische Beispiele ohne Aktionen.
- **Design.** Die App bietet Systemdarstellung, hell oder dunkel. Beide Farbwelten „Abendlicht“ und „Morgenrot“ stehen in jeder Darstellung zur Wahl. Farben und der Lichtkreis gehören zur Oberfläche. Das Bild selbst verwendet `FIT_CENTER` ohne Beschnitt oder Farbfilter. Die Bedienoberfläche ist auf Deutsch und Englisch verfügbar. Datum und Uhrzeit verwenden zusätzlich die sonstigen Gerätesprachen.
- **Assistentin.** Die Startseite enthält eine als „Platzhalter“ beschriftete Textkarte. Für eine spätere, gesondert freizugebende Integration könnte Sunny Core die Anfragen orchestrieren, Sunny Connect den versionierten Hostvertrag vermitteln und Guard Datenzugriffe und Aktionen unabhängig vom Modell prüfen. In v0.1 existiert keine Verbindung zu diesen Diensten.

## Originalmotiv

Die Datei `sunny-standalone-poc/src/main/res/drawable-nodpi/sunny_su08.webp` stammt aus dem vom Owner freigegebenen Originalbildbestand in `e8fe7bce0f587e98fce57d4850449dd0a6b857c8`. Der Blob `ddae6747d036638751c437a3a543102a25c64b63` ist an diesem Commit und auf der Integrationsbasis identisch. Die Datei wurde bytegleich als `sunny-launcher-idea/src/main/res/drawable-nodpi/sunny_su08.webp` kopiert. Der Vergleich im Arbeitsbaum und die Prüfung der Ressource im APK ergaben denselben SHA-256.

```text
06762e4a36085c639ac92eaa9dbefc111f21517a546857b6e9a50d40eafc7af5  sunny_su08.webp
```

## App-Grenzen und Abnahme

| Anforderung | Umsetzung und lokale Evidenz |
| --- | --- |
| Gewöhnliche Android-App | Das gepackte Manifest enthält genau eine exportierte `MainActivity` mit `ACTION_MAIN / CATEGORY_LAUNCHER`. Keine `HOME`-Kategorie oder Abfrage der Launcher-Rolle. Home und Zurück bleiben beim Android-System. |
| Begrenzte Sichtbarkeit und expliziter Start | Ein `MAIN / LAUNCHER`-Query-Filter. App-Abfrage und Wiederprüfung schließen die eigene App, nicht exportierte, deaktivierte, suspendierte und durch Berechtigung geschützte Activities aus. Acht JVM-Tests decken sichtbare Ziele, exakte Komponenten, synthetische Einträge, Hintergrund, fehlende Ziele und sichere Fehlerfälle ab. Die Tests nutzen simulierte Android-Antworten. |
| Lokale Einstellungen | Private SharedPreferences speichern Favoriten als ausgewählte Komponenten-IDs und zwei Designwerte. App-Labels, der App-Katalog, Gespräche und MeinHelfer-Daten werden nicht gespeichert. Acht Tests decken Speicherung und Lesen der Werte, Entfernen, fehlende Favoriten und Standardwerte ab. |
| Datenschutz und Systemgrenzen | Das APK enthält null `uses-permission`-Einträge, keine Shared UID und keine nativen Bibliotheken. Backup ist deaktiviert. Die Datenextraktionsregeln schließen Einstellungen für Cloud-Backup und Gerätetransfer aus. Es gibt keinen Netzwerk- oder Tracking-Code und keine Anbindung an Brain, MeinHelfer oder eine KI. |
| Platzhalter, Design und APK | Fünf Abgrenzungstests prüfen Manifest, Backup und die rein textliche Platzhalteransicht samt Beschriftung. Zwei Design-Tests prüfen Farbvarianten und einen Mindestkontrast von 4,5:1 für die verwendeten Text- und Bedienelementfarben. Das separate APK-Prüfskript liest gepacktes Manifest, Datenextraktionsregeln und Bilddatei. |

## Ausgeführte Prüfungen

Für die lokalen Befehle wurde die bestehende JDK-/Gradle-Aktivierung geladen und das SDK 35 um die offiziellen Build Tools 35.0.0 ergänzt. Das Download-Archiv wurde mit der veröffentlichten Prüfsumme verifiziert. Die Befehle wurden aus dem isolierten Worktree ausgeführt.

```bash
source /workspace/.mein-helfer-android/activate.sh
export ANDROID_HOME=/workspace/sunny-launcher-sdk.AVcxEz
gradle -p sunny-core --no-daemon --no-parallel --max-workers=2 clean test
gradle -p sunny-launcher-idea --no-daemon --no-parallel --max-workers=2 clean testDebugUnitTest lintDebug assembleDebug

apk=sunny-launcher-idea/build/outputs/apk/debug/sunny-launcher-idea-debug-unsigned.apk
sdk_tools="$ANDROID_HOME/build-tools/35.0.0"
python3 sunny-launcher-idea/tools/verify_apk.py "$apk" --build-tools "$sdk_tools"
cmp sunny-standalone-poc/src/main/res/drawable-nodpi/sunny_su08.webp \
    sunny-launcher-idea/src/main/res/drawable-nodpi/sunny_su08.webp
sha256sum "$apk"
"$sdk_tools/apksigner" verify "$apk"
git diff --cached --check
git diff --cached --name-only 651c9e970c4eaffea5190bbbd514b61b14d44e99
```

| Prüfung | Ergebnis |
| --- | --- |
| Core `clean test` | 160 Tests, 0 Fehlschläge, 0 Fehler, 0 übersprungen |
| Launcher `testDebugUnitTest` | 23 neue Tests, 0 Fehlschläge, 0 Fehler, 0 übersprungen |
| Android `lintDebug` | Erfolgreich |
| `assembleDebug` | Erfolgreich, lokales unsigniertes Debug-APK |
| APK- und Avatarprüfung | Erfolgreich. `apksigner verify` meldete erwartungsgemäß `DOES NOT VERIFY` und `Missing META-INF/MANIFEST.MF`. |
| SHA-256 des lokal geprüften APK | `fd5ffb653024249439c06bf1d111452430f68d027a13602f34206f5543d073bf` |
| Pfadprüfung | Ausschließlich neue Dateien in `sunny-launcher-idea/` und dieser Bericht. Bestehende Projektdateien sind gegenüber der geprüften Basis identisch. |

Der erste Gradle-Versuch im Standardsandbox-Modus konnte den lokalen Daemon-Socket nicht öffnen. Beim Offline-Wiederholungsversuch fehlte das Kotlin-Plugin 2.2.21 im Cache. Die Baseline war nach dem Abruf der bestehenden Buildabhängigkeiten erfolgreich. Der erste Launcher-Lauf meldete einen mehrdeutigen Java-Aufruf. Der nächste Lauf fand zwei Lint-Fehler zur Android-API und zu einer redundant gesetzten Umbruchkonstante. Alle drei Stellen wurden korrigiert und der vollständige Launcher-Lauf anschließend erfolgreich wiederholt.

## Installation, Rücknahme und offene Prüfungen

- **Installation nach separater Freigabe.** Android setzt eine APK-Signatur voraus. Für die Geräteabnahme muss die zuständige Stelle nach Freigabe eine lokale Testkopie mit einem dafür vorgesehenen Debug-Schlüssel signieren. Danach das Paket anhand der APK-Prüfung bestätigen, per `adb install <lokal-signierte-testkopie.apk>` installieren und „Sunny Launcher“ über die Android-App-Liste öffnen. Der hier geprüfte Build ist unsigniert. Es gibt kein bereitgestelltes Installationsartefakt.
- **Geräteprüfung ausstehend.** Auf einem freigegebenen Testgerät Start und App-Ende, System-Home und Zurück, App-Start und Rückkehr, Favoriten nach Prozessneustart, Entfernen eines synthetischen Testziels, Hell/Dunkel und beide Farbwelten prüfen. Darstellung im Hoch- und Querformat, hohe Systemschrift, TalkBack, Zeitzone und 12-/24-Stundenanzeige prüfen. Plattformabhängig bleiben der tatsächliche Umfang der App-Sichtbarkeit und die visuelle Darstellung auf dem Gerät zu bestätigen. Es wurden keine Geräte-, Emulator- oder UI-Interaktionstests ausgeführt.
- **Rücknahme.** Bei einem später installierten Testexemplar entfernt `adb uninstall de.meinhelfer.sunny.launcher.idea` ausschließlich das Ideenprototyp-Paket und dessen lokale Einstellungen. Auf Repository-Ebene kann die Projektzentrale den Draft PR schließen. Bei einer später genehmigten Übernahme kann ein eigener Revert ausschließlich den Prototyp und seinen Bericht zurücknehmen.
- **Abnahme.** Unabhängiges Review, separate CI-Freigabe und Installationsfreigabe stehen aus. Die lokale Prüfung deckt den Build und die oben ausgewiesenen automatisierten Tests ab. APK-Signierung, APK-Veröffentlichung und Merge wurden im Auftrag nicht ausgeführt.
