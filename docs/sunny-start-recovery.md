# Sunny – Einrichtung und Wiederherstellungsmodus

Status: Anforderung, noch nicht implementiert. Zuständig: 04 Help & Setup, 06 Guard, 08 Design, 09 Testing; Koordination 00.

## Startzustände
- Unaufgelöste Wiederherstellung hat Vorrang vor allen anderen Startpfaden. Keine Room-Initialisierung, Worker oder schreibende Funktionen bis zur erfolgreichen Rücksicherung.
- Erstinstallation ohne vorhandenen Datenbestand: Auswahl Neu einrichten, Sicherung wiederherstellen, Hilfe & Fehlerbehebung.
- Bestehender Datenbestand oder APK-Update: normaler Start; niemals allein wegen fehlender Onboarding-Markierung als Neuinstallation behandeln.
- Wiederherstellung auch aus Einstellungen erreichbar; vor Überschreiben vorhandener Daten ausdrückliche Bestätigung und Sicherungsmöglichkeit.

## Wiederherstellen
Dateiauswahl über Android Storage Access Framework, Passwort, Prüfung, Zusammenfassung (Version, Datum, Umfang, fehlende Inhalte), Bestätigung, vorbereiteter Austausch, Neustart, Ergebnis. Keine automatische Löschung von Originaldaten bei Fehler. Keine Internetpflicht.

## Notfallmodus
Separater datenbankfreier Einstieg mit verständlicher Fehleranzeige, erneutem sicheren Rücksicherungsversuch und datensparsamer Diagnose. Keine Neuinitialisierung oder Löschung als Standardaktion. Bei nicht auflösbarem Zustand Originale erhalten und Hilfe anbieten. Keine Schlüssel oder Pflegeinhalte in Diagnoseprotokollen.

## Abnahmetests
Erstinstallation, Update mit Altbestand, fehlende Onboarding-Markierung, gültige/ungültige Sicherung, falsches Passwort, Abbruch in jeder Wiederherstellungsphase, erneuter Abbruch beim Rollback, nicht behebbarer Fehler, Offlinebetrieb. Tests ausschließlich mit Testdaten bis zur Freigabe.
