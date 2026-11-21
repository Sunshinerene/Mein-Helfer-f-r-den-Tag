# Mein Helfer für den Tag

Android-App als täglicher Begleiter für pflegende Angehörige. Alle personenbezogenen Daten bleiben lokal auf dem Gerät.

## Stand: v0.2.0

- Heute-Übersicht: Einnahmen, anstehende Termine, Nachbestell-Warnung
- Medikamente mit Einnahmezeiten und Vorrat
- Termine
- Ärzte mit Direktkontakt (Anruf, E-Mail)
- Erinnerungen: Medikamenten-Einnahme und Termine (1 Stunde vorher)

Offene Punkte und Wünsche: siehe [TODO.md](TODO.md).

## Geplant

1. Erinnerungen für Medikamente und Termine, Kalender-Eintrag
2. Dokumente scannen und verschlüsselt ablegen, Texterkennung
3. KI-Vorschläge aus Dokumenten (immer mit Bestätigung durch den Nutzer)
4. Wegweiser für Leistungen und Anträge
5. Umkreissuche für Ärzte und Pflegedienste
6. Update-Funktion über GitHub-Releases, Feedback-Funktion

## APK bekommen

Jeder Push auf `main` baut automatisch eine APK: Reiter **Actions** → letzter Lauf → **Artifacts** → `MeinHelfer-APK`.
Mit einem Tag wie `v0.1.0` wird sie zusätzlich als **Release** veröffentlicht.

Hinweis: Die APK ist mit einem festen Test-Schlüssel signiert (`app/debug.keystore`), damit Updates über die alte Version installiert werden können. Nur für Tests. Vor einer echten Veröffentlichung wird ein privater Schlüssel eingerichtet.

## Lokal bauen

Android Studio öffnen, Projektordner wählen, Run. Oder per Kommandozeile mit Gradle 8.9 und JDK 17: `gradle assembleDebug`.
