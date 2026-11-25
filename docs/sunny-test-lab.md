# Sunny Test Lab

Status: approved concept, not implemented or tested.
Owner: 09 – Sunny Update & Testing; coordination 00; design 08.

## Isolation
Separate application ID and APK, separate private storage and encryption keys. Synthetic fixtures only. No access to production backups, real care data or production notifications. Fault injection compiled only into dedicated lab variant, never normal debug/release APKs. Distinct app label and persistent test banner.

## Restore interruption tests
Inject process termination after verification (before approval), after approval, after rollback metadata persistence, after APPLYING marker, after each file move, after key/prefs changes, after integrity check, after COMMITTED/ROLLED_BACK and during cleanup. Reopen and verify original or committed fixture, key consistency, no unapproved restore, idempotent repeated startup, and no database access during failed recovery. Process termination is not equivalent to power loss.

## Developer UI
Plain Material UI is sufficient. No Sunny artwork or custom design integration required. Prioritize readable controls, explicit test status and reproducible results. Production design requirements remain unchanged.

## Release gate
Verify separate package IDs and absence of fault injection in normal APK. Build both variants and execute tests with synthetic data before any restore test on a real device. Report actual results and limitations. Future extensions: model, voice, storage, performance and database diagnostics.


## Praxistest 2026-11-25
Build #2, Commit 7142d18: erfolgreich. Nutzer bestätigt Installation, Auswertung und alle fünf synthetischen Abbruchszenarien bestanden. Kein Screenshot wegen FLAG_SECURE. Dies ist kein Nachweis der produktiven verschlüsselten Wiederherstellung. Nächster Meilenstein: echte Restore-Logik mit isolierten synthetischen Daten testen.


## Praxistest Phase 2 – 2026-11-25
Nutzer bestätigt auf Smartphone nach SQLCipher-Initialisierungskorrektur e183039 alle zehn echten Restore-Abbruchszenarien mit synthetischen Daten bestanden.

| Checkpoint | Ergebnis |
|---|---|
| READY | BESTANDEN B |
| APPLYING | BESTANDEN A |
| ASIDE:mein_helfer.db | BESTANDEN A |
| PLACE:mein_helfer.db | BESTANDEN A |
| PLACE:docs_enc | BESTANDEN A |
| PLACE:photos | BESTANDEN A |
| KEY | BESTANDEN A |
| PREFS | BESTANDEN A |
| COMMITTED | BESTANDEN B |
| CLEAN:mein_helfer.db.vorher | BESTANDEN B |

Erster APPLYING-Test scheiterte an fehlender SQLCipher-Initialisierung nach Prozessneustart; erneute Auswertung nach Update und alle weiteren Tests erfolgreich laut Nutzer. Insgesamt 15/15 Szenarien in beiden Phasen. Grenzen: synthetisches SQLCipher-Schema, künstlich erzeugtes Backup, kein vollständiger Room-Roundtrip, keine separate Sprachschatzprüfung, keine Stromausfallprüfung. Keine Freigabe für produktive Wiederherstellung allein aufgrund dieser Tests.
