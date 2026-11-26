# Entscheidungen

- Chat 00 koordiniert die Entwicklung.
- Test-App als Dev-Tool weiterführen.
- Projektgedächtnis in GitHub pflegen.
- Neue Projektchats fortlaufend nummerieren.

Entscheidungen mit Datum, Begründung und Auswirkungen ergänzen.

## 26.11.2026
Grundsätzliche Architektur und parallele Entwicklung durch Nutzer bestätigt. Sunny Core unabhängig von MeinHelfer; Guard außerhalb der Modellentscheidung. Schrittweise Modulstruktur. Einzelne technische Verträge und Arbeitspakete noch freizugeben. Dokumentationsbranch bleibt bis Review getrennt.

## 26.11.2026 – Qualität und Abnahme
KI-Denkaufwand risikobasiert, Qualitätscheck nach drei abgenommenen Paketen, unabhängige CI vor Freigabe (PROJECT_RULES.md). Nutzer genehmigt 6A-Integration nach 110/110 CI-Tests. Merge statt Squash zur Erhaltung der Commit-Historie. Core bleibt eigenständiger JVM-Build; Android-Anbindung separat. main bleibt unverändert.
