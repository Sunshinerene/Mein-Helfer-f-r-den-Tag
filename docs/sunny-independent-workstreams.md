# Unabhängige Arbeitspakete – Vorschläge

Status: Planung, keine Implementierung. Vor Umsetzung bestehende Funktionen prüfen und Doppelentwicklungen vermeiden.

- [ ] Diagnose-Center: App-Version, Android-Version, freier Speicher, tatsächlicher Modellstatus, Berechtigungen und lokale technische Fehler. Keine Pflegeinformationen in Diagnoseexporten; Export nur nach Vorschau und Freigabe.
- [ ] Funktionsregister: stabile IDs, Voraussetzungen, Status, Hilfetexte und Navigation; bestehendes SunnyCapabilities-Register prüfen und erweitern statt duplizieren.
- [ ] Datensicherung: vorhandene Sicherung und Wiederherstellung prüfen, Integrität und Datenmigration testen.
- [ ] Datenschutz-Center: lokale Speicherung, Internetzugriffe und Freigaben transparent darstellen.
- [ ] Ersteinrichtung: freiwillige, überspringbare Einführung.
- [ ] Feedback: lokale Fehlerberichte und Vorschläge, Versand nur nach Freigabe.
- [ ] Barrierefreiheit: Schriftgröße, Kontrast, TalkBack und Bedienbarkeit prüfen.
- [ ] Demo-Modus: fiktive Daten strikt von realen Pflegedaten isolieren.

Empfohlener Einstieg: Diagnose-Center und Funktionsregister als getrennte, offline nutzbare Komponenten. Keine Änderungen an Brain, Emotion Engine oder Datenbankschema ohne Koordination. Vor Implementierung vorhandenen Code prüfen.
