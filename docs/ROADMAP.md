# Roadmap

- Phase 3: Sunny Test Lab als Dev-Tool; aktuellen Stand mit Chat 00 abgleichen.
- Weitere Meilensteine durch Chat 00 priorisieren.
- Lokale KI, Emotion, Voice, Hilfe, Aufgaben, Sicherheit, Online, Design und Testing bleiben getrennte Fachbereiche.

## Aktualisierte Reihenfolge
4/5 Backup-Oberfläche und Sicherheit; parallel nach Freigabe 6A Core, 6B Runtime, 6C Memory, 6D Hybrid, 7A Registry, 7B Learn, 7C Modulerstellung, 8A Hostadapter, 8B Cross-App, 8C Standalone-Test. Abhängige Pakete erst nach Abnahme. Siehe CODEX_WORKPACKAGES.md.

## Fortschritt 26.11.2026
6A Core Foundation v0.1 abgenommen und integriert (PR #7). Post-Merge-CI prüfen; 6B Local Runtime als nächstes spezifizieren und separat freigeben. Schritte 4/5 weiterhin offen.


## 2026-11-27 – CODEX-6B.1 accepted
Owner GO in Chat 00; PR #8 merged into entwicklung/sunny-2 at 3b20278e408741f2ec18c908a0c42c9c92160956, reviewed head 00d1f0154fb87dd7a9a8a84c225c31adb8d62201. Independent CI run 45900020601: 131 tests, zero failures/errors/skips; build 42s. Three files exclusively sunny-core; 110 baseline tests unchanged, 21 new synthetic tests. No Android changes. Post-merge CI pending. 6B.2 must enforce shared exclusive model access including import/delete, quarantine backend after uncertain cleanup, and verify cooperative cancellation; 6B.3 real device evidence required. No native runtime/device acceptance yet. Steps 4/5 remain open. Next 6B.2 scope requires separate approval.
