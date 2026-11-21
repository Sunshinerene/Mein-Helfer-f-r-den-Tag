# Mein Helfer für den Tag

Android-App als täglicher Begleiter für pflegende Angehörige. Alle personenbezogenen Daten bleiben lokal auf dem Gerät.

## Stand: v0.3.0

- Heute-Übersicht: Einnahmen, anstehende Termine, Nachbestell-Warnung
- Medikamente mit Einnahmezeiten und Vorrat
- Termine
- Ärzte mit Direktkontakt (Anruf, E-Mail)
- Erinnerungen: Medikamenten-Einnahme und Termine (1 Stunde vorher)
- Alle Einträge bearbeitbar, Kalender- und Zeitauswahl, Vorauswahlen, Arzt-Auswahl beim Termin
- Einstellungen: App-Update direkt über GitHub-Releases, Feedback, Vorschau auf geplante Funktionen

Offene Punkte und Wünsche: siehe [TODO.md](TODO.md).

## APK bekommen und aktualisieren

Jede neue Version (`versionName` in `app/build.gradle.kts`) wird nach dem Push automatisch gebaut und als **GitHub-Release** veröffentlicht. Die APK kann direkt unter **Releases** heruntergeladen werden.

Ab v0.3.0 aktualisiert sich die App selbst: **Einstellungen (Zahnrad) → Nach Updates suchen → Herunterladen → Installieren**. Im Hintergrund prüft sie regelmäßig und meldet neue Versionen per Benachrichtigung.

Hinweis: Die APK ist mit einem festen Test-Schlüssel signiert (`app/debug.keystore`), damit Updates über die alte Version installiert werden können. Nur für Tests. Vor einer echten Veröffentlichung wird ein privater Schlüssel eingerichtet.

## Lokal bauen

Android Studio öffnen, Projektordner wählen, Run. Oder per Kommandozeile mit Gradle 8.9 und JDK 17: `gradle assembleDebug`.
