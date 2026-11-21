# Mein Helfer für den Tag

Android-App als täglicher Begleiter für pflegende Angehörige. Alle personenbezogenen Daten bleiben lokal auf dem Gerät.

## Stand: v0.7.0

- Heute-Übersicht: Einnahmen und Termine abhaken, Bemerkungen, Tagesnotiz, Nachbestell-Warnung
- Auswertung für Tag, Woche, Monat (lokal berechnet, nur Anhaltspunkte, kein ärztlicher Rat)
- Medikamente mit Einnahmezeiten und Vorrat
- Termine mit Beginn und Ende
- Kontakte: Ärzte, Pflegedienst, Apotheke, Kassen usw. mit Direktkontakt (Anruf, E-Mail), Filter nach Art
- Mehrere betreute Personen, Wechsel oben auf jeder Seite
- Erinnerungen: Medikamenten-Einnahme (mit Knopf "Eingenommen") und Termine (Zeit pro Termin einstellbar)
- To-do-Liste, Termin in den Kalender übernehmen, Dunkelmodus
- Alle Einträge bearbeitbar, Kalender- und Zeitauswahl, Vorauswahlen, Arzt-Auswahl beim Termin
- Medikament per Foto der Packung anlegen: Text wird lokal auf dem Handy erkannt, das Foto bleibt im privaten App-Ordner
- Einstellungen: App-Update direkt über GitHub-Releases, Feedback, Vorschau auf geplante Funktionen

Offene Punkte und Wünsche: siehe [TODO.md](TODO.md).

## APK bekommen und aktualisieren

Jede neue Version (`versionName` in `app/build.gradle.kts`) wird nach dem Push automatisch gebaut und als **GitHub-Release** veröffentlicht. Die APK kann direkt unter **Releases** heruntergeladen werden.

Ab v0.3.0 aktualisiert sich die App selbst: **Einstellungen (Zahnrad) → Nach Updates suchen → Herunterladen → Installieren**. Im Hintergrund prüft sie regelmäßig und meldet neue Versionen per Benachrichtigung.

Hinweis: Die APK ist mit einem festen Test-Schlüssel signiert (`app/debug.keystore`), damit Updates über die alte Version installiert werden können. Nur für Tests. Vor einer echten Veröffentlichung wird ein privater Schlüssel eingerichtet.

## Lokal bauen

Android Studio öffnen, Projektordner wählen, Run. Oder per Kommandozeile mit Gradle 8.9 und JDK 17: `gradle assembleDebug`.
