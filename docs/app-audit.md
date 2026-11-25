# App-Audit – erste statische Prüfung

Stand: 2026-11-25. Entwicklungsbranch entwicklung/sunny-2. Keine Laufzeittests durchgeführt; keine App-Codeänderungen.

| ID | Priorität | Fundstelle | Befund / Maßnahme |
|---|---|---|---|
| AUD-001 | P1 | update/UpdateChecker.kt fetchLatest | Regex für dev-Tags überescaped; gültige Tags können nicht matchen. Regex korrigieren und Versionsfälle testen. |
| AUD-002 | P2 | update/UpdateChecker.kt download APK | Keine explizite Prüfung der übertragenen Länge bei bekannter Content-Length; temporäre Datei, Bereinigung und Integritätsprüfung ergänzen. Android-Signaturprüfung bleibt erforderlich. |
| AUD-003 | P2 | ai/SunnyModelStore.kt importModel | Nichtleere inkompatible Datei kann bisheriges Modell ersetzen. Kandidat vor endgültigem Wechsel prüfen oder Rollback ermöglichen. |
| AUD-004 | P2 | ai/SunnyModelStore.kt / ai/SunnyLiteRtTest.kt | Unterschiedliche Mutex-Instanzen für Modellwechsel und Inferenz; mögliche Konkurrenz prüfen und gemeinsamen Lebenszyklus absichern. |
| AUD-005 | P2 | update/UpdateChecker.kt download PDF | HttpURLConnection nicht zuverlässig per finally getrennt; temporäre Downloads und PDF-Prüfung ergänzen. |

Positiv: Modellimport mit temporärer Datei, atomarem rename, Größenlimit, Speicherreserve und Bereinigung.

Offen: vollständiges Dateiinventar, Stammdaten, Medikamente, Termine, Aufgaben, Pflege, Dokumente, Datenbank, Backup, Benachrichtigungen, Sunny-Module, Datenschutz, UI, Performance, Tests und Build-Workflow. P2-Risiken sind noch nicht praktisch reproduziert. Keine Aussage über Fehlerfreiheit ungeprüfter Module.

## Zweiter Prüfblock

| ID | Priorität | Fundstelle | Befund / Maßnahme |
|---|---|---|---|
| AUD-006 | P0 | backup/Backup.kt applyPending | Catch löscht alle Originalpfade auch wenn aside vorher scheiterte und nicht alle Pfade gesichert wurden. Nur tatsächlich ersetzte Pfade zurückrollen; persistentes Wiederherstellungsjournal und Fehler-Injektionstests. |
| AUD-007 | P1 | backup/Backup.kt create | Live-Kopie DB und WAL nach Checkpoint ohne Schreibsperre; konsistenten Snapshot sicherstellen. |
| AUD-008 | P1 | backup/Backup.kt probeVersion | Nur Versionsabfrage, keine vollständige Integritätsprüfung vor Austausch. |
| AUD-009 | P1 | summary/MedPlan.kt match | Namensschlüssel und Teilstring können trotz abweichender PZN/Stärke falsche Zuordnung erzeugen; eindeutige Identität und Bestätigung prüfen. |
| AUD-010 | P1 | summary/MedPlan.kt dailyUse | Freitext/Bedarf ohne Schema liefert 1.0; unbekannten Verbrauch separat behandeln. Auswirkungen auf Import und Bestand noch prüfen. |
| AUD-011 | P1 | summary/MedPlan.kt parseText | Jede erkannte Darreichungsform erhält Stück, auch Tropfen/Spray; Einheit nicht ungeprüft ableiten. UI-Bestätigung prüfen. |
| AUD-012 | P2 | backup/Backup.kt create | lastBackup auch bei skipped > 0 gesetzt; vollständige und teilweise Sicherung unterscheiden. |

Statische Prüfung von Backup.kt, MedPlan.kt und ausgewählten DAO-Deklarationen. Keine Reproduktion am Gerät, keine Codekorrekturen. AppDatabase, DbCrypto, BackupCrypto, Import-UI, Einnahmebuchungen und tatsächliche Bestandslogik noch offen. Prioritäten vorläufig anhand möglicher Auswirkungen.
