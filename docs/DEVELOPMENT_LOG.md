# Entwicklungslog

## 26.11.2026
Projektgedächtnis beschlossen. Dokumentation getrennt vom App-Code angelegt.

Neue Einträge: Datum, Bereich, Commit/PR, Änderung, Ergebnis, offene Punkte und nächster Schritt.

## 26.11.2026 – Codex-Vorbereitung
AGENTS.md, SUNNY_ARCHITECTURE.md, FILE_OWNERSHIP.md, CODEX_WORKPACKAGES.md im Dokumentationsbranch angelegt. Keine App-Codeänderungen. Nächster Schritt: 6A-Vertrag und Build-Scope prüfen, Dokumentationsreview, Freigabe.

## 26.11.2026 – Core Foundation v0.1
Codex-Commit eeea22f; QA-Regeln 488e632; CI 933208e, Push-Trigger 67138b2, Workflow-Abgleich 3ca5748. Unabhängiger Run 45890435686 erfolgreich. Nutzer-GO, PR #7 Merge c19560a. Keine Android-Dateien verändert. Nächster Schritt: Post-Merge-CI und 6B Runtime-Vertrag.


## 2026-11-27 – CODEX-6B.1 accepted
Owner GO in Chat 00; PR #8 merged into entwicklung/sunny-2 at 3b20278e408741f2ec18c908a0c42c9c92160956, reviewed head 00d1f0154fb87dd7a9a8a84c225c31adb8d62201. Independent CI run 45900020601: 131 tests, zero failures/errors/skips; build 42s. Three files exclusively sunny-core; 110 baseline tests unchanged, 21 new synthetic tests. No Android changes. Post-merge CI pending. 6B.2 must enforce shared exclusive model access including import/delete, quarantine backend after uncertain cleanup, and verify cooperative cancellation; 6B.3 real device evidence required. No native runtime/device acceptance yet. Steps 4/5 remain open. Next 6B.2 scope requires separate approval.
