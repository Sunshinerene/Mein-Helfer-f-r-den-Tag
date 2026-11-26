# MeinHelfer DevTools v1.0

Stand: 26.11.2026 — Build und 22 Funktionsprüfungen bestanden; Release ausstehend.

## Referenz
Backup & Restore Stable: `backup-restore-stable-v1.0-lab22`, Commit `0a7bc5a6dea63defbb80616f7304dbcb56a85492`, Lab-Build 22. Nutzerbericht: 17/17 bestanden am 25.11.2026. Referenz nicht verändern.

## Architektur
- Produktions-ID `de.meinhelfer.tag`; Lab-ID `de.meinhelfer.tag.lab` bleibt zur Update-Kompatibilität bestehen.
- LabApplication und LabActivity als separate Einstiegspunkte; produktive Dienste und Internet-/Gesundheitsberechtigungen im Lab entfernt.
- Gemeinsamer Backup-Kern bleibt unverändert. Nur synthetische Testdaten verwenden.
- Menü: Übersicht, Database Lab, Backup & Restore, Testberichte. Roundtrip-Bericht lokal gespeichert.
- Workflow build-lab.yml baut beide APKs, führt Unit-Tests und Manifestprüfungen aus; keine automatische Release.
- Weitere Sunny-Module bewusst zurückgestellt.

## Freigabecheckliste
- [ ] Sunny-Bild als Lab-only Launcher-Icon integrieren und auf Gerät prüfen.
- [x] Beide APKs aus identischem Commit erfolgreich bauen.
- [ ] Vor Update der normalen App reguläres Backup erstellen; beide App-IDs und parallele Installation prüfen.
- [x] Menü, Ergebnisanzeige und kombinierte Berichtsausgabe geprüft.
- [x] Roundtrip (6 Prüfkategorien) und Wiederherstellung (READY, APPLYING, ASIDE:mein_helfer.db, PLACE:mein_helfer.db, PLACE:docs_enc, PLACE:photos, KEY, PREFS, COMMITTED, CLEAN:mein_helfer.db.vorher, REPEAT_ROLLBACK) erneut durchführen.
- [x] A/B-Zustände mit Referenz verglichen. Keine echten Pflegedaten ins Lab importieren.
- [ ] Nach erfolgreicher Geräteprüfung Testprotokoll und Pre-release `devtools-v1.0` am exakten getesteten Commit erstellen. Nur Lab-APK als Release-Asset anhängen.

## Entwicklungsroutine
Änderungen klein halten, auf Entwicklungsbranch bauen, Build-Erfolg prüfen, am Gerät testen, Ergebnis dokumentieren, erst danach Referenzstand markieren. Koordination über Chat 00.

## Testprotokoll 26.11.2026
Getesteter Build #28, Commit `36003ae44a175afe0c48436dd2df47ad66b266d5`. Beide APK-Artefakte erstellt, Unit-Tests und Manifestprüfungen laut erfolgreichem Workflow. Eine Node.js-20-Deprecation-Warnung bleibt.

Nutzerbericht: 22/22 bestanden.
- Database Lab: A_PREPARED, ASIDE, PLACED → A; COMMITTED, CLEANED → B.
- Roundtrip: Room-Schema/Backup, falsches Passwort, abgeschnittene Sicherung, beschädigte Sicherung, Restore ohne vorhandene DB, Dokumente/Fotos/Einstellungen/Schlüssel/Sprachschatz: alle bestanden.
- Restore: READY, COMMITTED, CLEAN:mein_helfer.db.vorher → B; APPLYING, ASIDE:mein_helfer.db, PLACE:mein_helfer.db, PLACE:docs_enc, PLACE:photos, KEY, PREFS, REPEAT_ROLLBACK → A. Alle bestanden.
- Kombinierter Bericht mit 17 Prüfergebnissen erfolgreich übermittelt.
- Launcher-Icon und parallele Installation sind nicht separat durch den Nutzer bestätigt; normale APK nicht aktualisiert.
- Release muss auf den getesteten Commit zeigen, nicht auf diesen nachträglichen Dokumentationscommit.
