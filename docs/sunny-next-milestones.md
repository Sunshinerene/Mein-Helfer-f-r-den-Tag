# Sunny – nächste Entwicklungsetappen

- [ ] Brain: Modellimport, Antwort im Flugmodus, Fehlerbehandlung, Speicher und Laufzeit auf dem Gerät prüfen.
- [ ] Emotion: SU01–SU35 mit stabilen IDs, Originalbeschreibungen und acht Hauptgruppen erfassen; Bild/Video-Fallback, situationsgerechte Auswahl und Wiederholungsvermeidung testen.
- [ ] Voice: Ausdruck und Sprachausgabe synchronisieren; vorhandene Erscheinungsoptionen erhalten.
- [ ] Help & Setup: versionierten lokalen Hilfekatalog mit Voraussetzungen, Downloadlinks, Fehlerlösungen und Navigation vorbereiten; siehe sunny-help-setup-backlog.md.
- [ ] Tasks & Learn: freigegebene Aufgaben und freiwillige Vorschläge erweitern; keine selbst erteilten Berechtigungen.
- [ ] Update: Entwicklungskanal praktisch prüfen, für jede veröffentlichte Testversion eindeutigen Versionsnamen und höheren versionCode vergeben.

Vor jedem neuen Testrelease: Build und Tests erfolgreich, vorhandene Daten und Einstellungen erhalten, keine ungeprüfte Veröffentlichung auf main. Emotion Studio bleibt optionale spätere Erweiterung (siehe sunny-emotion-studio-backlog.md).

## Priorität: Sunny Brain als paralleler Hauptentwicklungsstrang

Sunny Brain bleibt während der Entwicklung der Emotion Engine und weiterer Module ein aktiver Kernbereich. Lokale Inferenz und Modellkompatibilität sind noch praktisch zu prüfen. Eine gemeinsame Schnittstelle für Absichtserkennung, Funktionskatalog, Emotion, Sprache und kontrollierte Aktionen soll schrittweise entstehen. Deterministische Funktionen bleiben ohne KI-Modell nutzbar; keine eigenmächtigen sensiblen Aktionen oder ungefragte Cloud-Übertragung. Emotion und Brain werden unabhängig testbar entwickelt.
