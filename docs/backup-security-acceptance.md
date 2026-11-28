# Produktive Backup-Oberfläche und Sicherheitsprüfung

Stand 26.11.2026. Schritte 4 und 5 in Prüfung, nicht freigegeben.

## Referenzen
- DevTools v1.0: Build 28, Commit 36003ae44a175afe0c48436dd2df47ad66b266d5, 29/29 dokumentierte Funktionsprüfungen.
- Backup-Oberfläche: Commit 5d8e989c2d1be41ccba5b9e79ecd98d9dc74a1a7.
- Unvollständige Sicherung wird als solche angezeigt; kein direkter Reset-Folgepfad. Restore benötigt Geräteauthentifizierung; unvollständige Restore-Datei wird angezeigt.

## Abnahme auf Android
Nur synthetische Daten auf separatem Testgerät oder Emulator für destruktive Tests verwenden. Normale App vor Update sichern. Bestehende Daten nicht löschen.
- [ ] Beide APKs und Unit-Tests erfolgreich bauen.
- [ ] Sicherung über Einstellungen > Sicherheit und Sicherung erstellen; Datei extern sichern.
- [ ] Passwortfehler, beschädigte Datei, Abbruch der Dateiauswahl und Restore-Vorschau prüfen.
- [ ] Restore-Vorschau abbrechen, Daten unverändert.
- [ ] Gerätefreigabe abbrechen, Daten unverändert.
- [ ] Auf isoliertem Gerät vollständige Wiederherstellung, Neustart und Datenvergleich durchführen.
- [ ] Teilbackup-Warnung und blockierten direkten Reset-Folgepfad prüfen.
- [ ] Reset-Freigabe und Abbruch auf isoliertem Gerät prüfen.
- [ ] Alle 17 Backup-Referenzprüfungen in DevTools wiederholen.

## Sicherheitsbefunde (statische Teilprüfung, keine Zertifizierung)
- DbCrypto.prepare kann bei SQLCipher-/Keystore-/Migrationsfehler auf unverschlüsselten Betrieb zurückfallen. Fail-closed-Konzept mit nichtdestruktiver Recovery entwickeln; bestehende Daten nicht stillschweigend ersetzen. Tests für Fehler und Migration nötig.
- network_security_config erlaubt Cleartext global für lokale KI-Server. AiConfig und alle Netzwerkpfade inklusive Redirects prüfen, lokale HTTP-Ausnahme explizit begrenzen. Keine ungefragte Datenübertragung.
- UpdateChecker wählt APK anhand Endung. Vor Installer-Aufruf Paket-ID, Signatur und Versionscode prüfen; Release-Kanal und vertrauenswürdige URL sowie Digest-Konzept prüfen.
- Debug-Signierung nur für Entwicklung; private Release-Signierung und Migrationsstrategie vor externer Veröffentlichung.
- Backup-Staging enthält temporäre sensible Daten; Lebenszyklus, Speicherkontingente und Bereinigung prüfen.
- SunnyActionGuard vorhanden; alle Aufrufpfade und unterstützte Android-Versionen prüfen, keine pauschale Freigabe.
- Verschlüsselung der Datenbank und Backups im Code vorhanden; vollständiges Dateninventar, Log-/Export-/Cloud-Pfade, Löschkonzept, Einwilligung und Datenschutzprüfung noch offen.

## Codex-Übergabe
Schritte 6–8 in kleine PRs unterteilen, Architektur und Berechtigungsgrenzen vor Implementierung festlegen. Keine echten Pflegeinformationen in Cloud-Aufgaben, keine Secrets, keine direkten main-Pushes oder Releases. Tests und Review vor Merge. Erst lokale Kernfunktionen, dann Modulvertrag, dann appübergreifende Integration.
