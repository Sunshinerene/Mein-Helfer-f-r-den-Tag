# Projektregeln

- Chat 00 koordiniert alle Änderungen.
- Vor Änderungen aktuellen Quellcode und Branch prüfen.
- Parallel nur unabhängige Bereiche bearbeiten; keine gleichzeitigen Änderungen derselben Datei.
- Build und Tests abwarten, bevor abhängige Änderungen erfolgen.
- Qualität und Datensicherheit vor Geschwindigkeit.
- Nach wichtigen Schritten Status, Log, Entscheidungen und Tests aktualisieren.
- Öffentliches Repository: keine personenbezogenen Daten, Pflegeunterlagen, Zugangsdaten, Schlüssel oder privaten Chatprotokolle dokumentieren.
- Ungeprüfte Angaben ausdrücklich kennzeichnen.

## Verbindliche Entwicklungsroutine

1. Koordination: Chat 00 legt den nächsten Arbeitsschritt fest.
2. Bestandsprüfung: aktuellen Quellcode, Branch, Status und Abhängigkeiten prüfen; bei Wiederaufnahme zuerst das Projektgedächtnis lesen.
3. Fachbereich: Aufgabe zuordnen und Änderungen umsetzen.
4. Build: Änderungen in GitHub sichern, APK bauen und Ergebnis abwarten.
5. Funktionstest: neue und betroffene bestehende Funktionen prüfen; getestete Funktionen erhalten.
6. Fehlerbehebung: bei Fehlern Ursache analysieren, korrigieren und zu Build und Test zurückkehren. Keine abhängigen Änderungen bei fehlgeschlagenen Builds.
7. Projektgedächtnis: Status, Log, Entscheidungen, Testergebnisse und offene Aufgaben aktualisieren.
8. Meilenstein: nach erfolgreichen Tests Entwicklungsstand freigeben und gegebenenfalls Versions-Tag setzen.
9. Nächster Schritt: abhängige Entwicklung erst nach Abschluss beginnen.

## Chatorganisation

Projektchats fortlaufend zweistellig nummerieren und in CHAT_INDEX.md dokumentieren. Chats 00 bis 14 sind laut Nutzer am 26.11.2026 im Projekt vorhanden; nächste Nummer: 15.

Qualität, Stabilität und Datensicherheit haben Vorrang vor Geschwindigkeit.

## Parallele Entwicklung
AGENTS.md und FILE_OWNERSHIP.md beachten. Work vorerst nicht einsetzen; Codex-Aufträge klein halten und tatsächlichen Verbrauch nach erstem Auftrag erfassen. Keine Implementierung ohne konkretes GO. Gemeinsame Dateien nur nach Koordination.
