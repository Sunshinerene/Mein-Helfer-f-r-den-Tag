# Testergebnisse

Historische Nutzerberichte (nicht unabhängig verifiziert): Room-Schema, reguläres Backup, falsches Passwort, abgeschnittene und beschädigte Sicherung sowie zehn Prozessabbruchtests bestanden.

Neue Tests mit Commit, APK-Version, Testfall, Soll/Ist und Ergebnis dokumentieren. Ein grüner Build ersetzt keinen Gerätetest.

## Referenz v1.0
Build #28, Commit 36003ae44a175afe0c48436dd2df47ad66b266d5: 29/29 laut Nutzer bestätigt, einschließlich Parallelinstallation und Datentrennung. Einzelprotokoll siehe docs/devtools-v1.0.md im Entwicklungszweig. Keine neuen Tests in dieser Dokumentationsrunde.

## CODEX-6A – unabhängige CI 26.11.2026
Run https://github.com/Sunshinerene/Mein-Helfer-f-r-den-Tag/actions/runs/45890435686, Head 67138b24a6611813dbc5a9e5e86c9a6e09ac9194, Job 140151466391. Gradle 8.11.1, clean test, JUnit-Auswertung im Joblog direkt geprüft: 110 Tests, 0 failures, 0 errors, 0 skipped. Artefakt-Upload erfolgreich. Warnung: upload-artifact@v5 zielt auf Node 20, Runner nutzt Node 24; Wartung offen. Kein echter Modell-/Gerätetest. Post-Merge-CI separat prüfen.
