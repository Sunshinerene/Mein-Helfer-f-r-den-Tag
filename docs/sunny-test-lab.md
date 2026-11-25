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
