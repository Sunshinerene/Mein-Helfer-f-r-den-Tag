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
