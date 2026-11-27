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


## 2026-11-27 – CODEX-6B.1 accepted
Owner GO in Chat 00; PR #8 merged into entwicklung/sunny-2 at 3b20278e408741f2ec18c908a0c42c9c92160956, reviewed head 00d1f0154fb87dd7a9a8a84c225c31adb8d62201. Independent CI run 45900020601: 131 tests, zero failures/errors/skips; build 42s. Three files exclusively sunny-core; 110 baseline tests unchanged, 21 new synthetic tests. No Android changes. Post-merge CI pending. 6B.2 must enforce shared exclusive model access including import/delete, quarantine backend after uncertain cleanup, and verify cooperative cancellation; 6B.3 real device evidence required. No native runtime/device acceptance yet. Steps 4/5 remain open. Next 6B.2 scope requires separate approval.
