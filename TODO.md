# TODO

Stand: v0.51.0 · Offene Punkte zuerst, darunter der Verlauf aller Versionen.

## Als Nächstes

- [ ] Punkte aus „Ideen und Optimierungen“ nach Wunsch vorziehen (Vorschlag: Trink- und Vitalprotokoll, Pflegetagebuch, Budget-Übersicht, Übergabe-Bericht)

## Ideen und Optimierungen

- [ ] Trink- und Vitalprotokoll (Trinkmenge, Blutdruck, Gewicht, Blutzucker) mit Verlauf in Auswertung und Tagesplan
- [ ] Pflegetagebuch für die Begutachtung (Hilfebedarf je Lebensbereich), als PDF für Pflegegrad-Antrag oder Höherstufung
- [ ] Budget-Übersicht Entlastungsbetrag, Verhinderungs- und Kurzzeitpflege (verbraucht, übrig, Fristen)
- [ ] Übergabe-Bericht als PDF für Vertretung, Urlaub oder Pflegedienst (Person, Medikamente, Termine, Kontakte, Besonderheiten)
- [ ] Dokumente: Medikamente und Kontrolltermine aus dem erkannten Text vorschlagen (z. B. aus Entlassbericht), immer mit Bestätigung
- [ ] Packungsfoto: Erkennung verbessern (Wirkstoff, Packungsbeilage), Barcode live mit der Kamera
- [ ] Startbildschirm-Widget: nächste Einnahme und nächster Termin
- [ ] Barrierefreiheit: Hochkontrast-Design, Vorlesen, größere Bedienflächen
- [ ] Mehrsprachigkeit für Betreuungskräfte (z. B. Polnisch, Rumänisch)
- [ ] Motivator: Pausen-Erinnerung, Stimmungsverlauf in der Auswertung (nur für dich, nicht in geteilten Berichten), Ziel-Schritte als Aufgabe mit Frist übernehmen
- [ ] KI direkt auf dem Handy: Gemini Nano über ML Kit (nur neuere Geräte, z. B. ab Pixel 9) oder ein eigenes kleines Modell in der App (z. B. Gemma, mehrere GB Download)
- [ ] Designs verfeinern (Schriftarten, weitere Varianten); Effekte: echte Unschärfe hinter Glas-Karten ab Android 12, Glanz auch auf Knöpfen

## Bitte auf dem Handy prüfen (bisher nicht getestet)

- [ ] Datenbank-Umstellungen bis Version 19 (alte Daten bleiben erhalten?), Wiederherstellen einer Sicherung
- [ ] Wiederkehrende Termine: Anlegen, „nur diesen“ und „alle folgenden“ ändern und löschen, Erinnerungen
- [ ] Tagesstart: erscheint einmal am Tag, Rückblick bestätigen, Tagesplan-PDF teilen und drucken
- [ ] PDF-Layouts: Schreiben und Vollmachten, Tagesplan, Nachbestell-Liste
- [ ] Freigaben-Knöpfe auf deinem Handy (Hersteller öffnen teils andere Seiten)
- [ ] KI: Gesamt-Check und Dokument-Zusammenfassung werden sauber ausgewertet
- [ ] Umkreissuche und Fahrdienst-Suche (OpenStreetMap)
- [ ] Benachrichtigung antippen öffnet die richtige Person
- [ ] App zurücksetzen (am besten erst mit Testdaten und vorheriger Sicherung)
- [ ] Tages-Motivator: Stimmungs-Check zur richtigen Zeit (Zeitfenster, „Später“, Benachrichtigung), Vorhaben abhaken, Ziele mit Vorlagen und KI-Weg
- [ ] Ausweis und Krankenkarte auslesen (Erkennung je nach Licht und Kartenversion), Foto wird gelöscht
- [ ] Medikamentenplan übernehmen: Texterkennung bei Tabellen, Code oben rechts auf dem Plan, KI; Vorschlag nur bei neuerem Plan
- [ ] Neue KI-Dienste (Cerebras, SambaNova, GitHub Models, Hugging Face, Cohere, OVHcloud, Scaleway, xAI, Together, Nebius, IONOS) und eigener Server (Ollama, LM Studio) im Heimnetz testen
- [ ] Einstellungen: Übersicht, Bereiche, Suche und Zurück-Taste
- [ ] Fotos zu Medikamenten: Kamera, Galerie, freie Bilder (Wikimedia), Internetsuche, „Fotos ergänzen“ nacheinander; Datenbank 21
- [ ] Vorrat „unbekannt“: Umstellung auf Datenbank 22 (bisheriger Vorrat 0 gilt als unbekannt), Schnelleingabe und „Vorrat eintragen“ nacheinander
- [ ] Datenbank 23 (Packungsgröße), „Neue Packung“, Sicherungs-Erinnerung auf der Heute-Seite, PDFs von Notfallkarte (Karte für die Geldbörse richtig groß?) und Medikamentenplan
- [ ] Aufgaben-Erinnerung (kommt pünktlich, nicht mehr nach „erledigt“), „In Kalender eintragen“ mit deiner Kalender-App, Zoom mit zwei Fingern im Betrachter, Text-Dokumente drucken, Plan-Code beim Hochladen, Zuordnung der unterschriebenen Fassung
- [ ] Ecken und Effekte (Beta): alle Ansichten mit „Eckig“ und „Rund“, Aurora hell und dunkel, bewegter Hintergrund flüssig und akkuschonend

## Rechtliches vor einer Veröffentlichung

- [ ] Nutzungsbedingungen und Haftungsregelung von einer Anwältin bzw. einem Anwalt prüfen lassen
- [ ] Entscheiden: Aufbewahrung der Nachweise 3 Jahre oder länger (30-Jahres-Frist bei Gesundheitsschäden); zusätzlicher Nachweis beim App-Ersteller (Server) ja oder nein
- [ ] Medizinprodukte-Recht (MDR) prüfen lassen, vor allem den KI-Gesamt-Check der Medikamente; Zweckbestimmung als Organisationshilfe klar festhalten
- [ ] Impressum und Datenschutzerklärung für eine Veröffentlichung (z. B. Play Store), eigener Signaturschlüssel statt Test-Schlüssel

## Version 0.51.0 (fertig)

- [x] Aufgaben: Erinnerung per Benachrichtigung (Tag und Uhrzeit, Schnellwahl), 🔔 an der Aufgabe, „In Kalender eintragen“ über die Kalender-App
- [x] Dokumenten-Betrachter: Zoom mit zwei Fingern (auch Text), Prozent antippen setzt zurück; Text-Dokumente drucken
- [x] Code des Medikationsplans wird schon beim Hochladen und bei „Text neu auslesen“ gesucht und hat beim Einlesen Vorrang
- [x] Neue Vorlagen: Kündigung des Pflegedienstes (§ 120 SGB XI), Antrag auf Schwerbehindertenausweis (§ 152 SGB IX), Widerruf einer Vollmacht oder Schweigepflicht-Entbindung
- [x] Unterschriebene Fassung beim Einscannen dem Entwurf zuordnen (Titel, Kategorie, Empfänger; Entwurf vermerkt, Aufgabe abgehakt)

## Version 0.50.0 (fertig)

- [x] Vorrat: „Stück pro Packung“ beim Medikament, Knopf „Neue Packung“ an der Karte und „+ Packung“ im Vorrat-Fenster
- [x] Erinnerung an die Sicherung (aus, alle 2 Wochen, monatlich) als Karte auf der Heute-Seite mit „Jetzt sichern“ und „In einer Woche“
- [x] Notfallkarte als PDF: Seite für den Kühlschrank und Karte im Scheckkartenformat zum Ausschneiden und Falten; teilen und drucken
- [x] Medikamentenplan als PDF im Querformat (morgens, mittags, abends, zur Nacht, Hinweise), angelehnt an den Medikationsplan; teilen und drucken

## Version 0.49.0 (fertig)

- [x] Vorrat „unbekannt“: nach dem Einlesen eines Plans und bei leerem Feld; keine Nachbestell-Warnung, bis der Vorrat eingetragen ist; bisheriger Vorrat 0 wird beim Update zu „unbekannt“
- [x] Schnelleingabe „Vorrat“ an jeder Karte und „📦 Vorrat eintragen“ für alle mit unbekanntem Vorrat nacheinander; Tagesbericht nennt fehlende Vorräte

## Version 0.48.0 (fertig)

- [x] Fotos zu Medikamenten nachträglich: Platzhalter „📷 Foto“ in der Liste und „Foto hinzufügen/ändern“ in den Infos; fotografieren, Galerie, freie Bilder von Wikimedia Commons (Urheber und Lizenz werden gespeichert und angezeigt) oder Internetsuche im Browser
- [x] „Fotos ergänzen“: alle Medikamente ohne Bild nacheinander, mit Überspringen

## Version 0.47.0 (fertig)

- [x] „An KI-App teilen (z. B. Gemini)“ bei allen KI-Funktionen: vorbereiteten Text (ohne Namen) an eine KI-App auf dem Handy weitergeben, auch ohne eingerichteten Dienst

## Version 0.46.0 (fertig)

- [x] Einstellungen aufgeräumt: Übersicht mit 9 Bereichen (Meine Daten und Zustimmung, Darstellung, Heute-Seite und Motivator, Erinnerungen und Freigaben, Dokumente, Sicherheit und Sicherung, KI und Internet, Hilfe und Rückmeldung, App und Updates), je mit kurzem Stand, Suche und Update-Hinweis
- [x] Darstellung in eigene Karten geteilt (Hell/Dunkel und Schrift, Design mit Farben, Ecken, Effekte); Verweise in Anleitung, Rundgang und Hinweisen angepasst

## Version 0.45.0 (fertig)

- [x] Medikamente: Knopf „Medikamentenplan einlesen“ (alle Pläne aus den Dokumenten, neuester zuerst, oder direkt den Code fotografieren); automatischer Hinweis nur noch bei leerer Liste

## Version 0.44.0 (fertig)

- [x] Mehr KI-Dienste: kostenlos Cerebras, SambaNova, GitHub Models, Hugging Face, Cohere, OVHcloud (EU, auch ohne Schlüssel), Scaleway (EU); kostenpflichtig xAI (Grok), Together AI, Nebius, IONOS (Deutschland)
- [x] Eigener KI-Server im Heimnetz (Ollama, LM Studio): Daten bleiben im eigenen Netz, http nur für Adressen im Heimnetz
- [x] Mehr Platz für KI-Antworten (lange Medikamentenpläne und Ziel-Wege wurden sonst abgeschnitten), Denk-Modelle werden besser ausgewertet

## Version 0.43.0 (fertig)

- [x] Medikamentenplan aus Dokumenten übernehmen (Text, Code des bundeseinheitlichen Medikationsplans oder KI): neu, geändert, unverändert, nicht mehr im Plan; Einnahmezeiten aus dem Schema
- [x] Stand des Plans und letzte Änderung der Medikamentenliste werden gemerkt; Vorschlag auf der Medikamente-Seite nur bei neuerem Plan

## Version 0.42.0 (fertig)

- [x] Tages-Motivator: Satz für den Tag, „Das hast du geschafft“ und Vorhaben aus den letzten drei Tagen, Tipps je nach Stimmung, Unterstützung bei schlechter Stimmung
- [x] Stimmungs-Check mit Smileys, Stichworten und Notiz: aus, bei jedem Öffnen, 1×, 2× oder 3× täglich, optional mit Benachrichtigung; Verlauf über 14 Tage
- [x] Ziele Schritt für Schritt (Antrag, Gesundheit, Alltag, Entlastung) mit Vorlagen, laufender Einschätzung und optional KI-Weg, KI-Einschätzung und persönlichem KI-Impuls
- [x] Telefonnummer bei der betreuten Person
- [x] Ausweis und Krankenkarte fotografieren und auf dem Handy auslesen (Foto wird sofort gelöscht), manuelle Eingabe bleibt

## Version 0.41.0 (fertig)

- [x] Meine Daten mit Adresse; wird in Schreiben, Vollmacht, Schweigepflicht-Entbindung und auf der Notfallkarte genutzt
- [x] Ecken einheitlich einstellbar: wie im Design, eckig, dezent eckig, dezent rund, rund (Karten, Knöpfe, Felder, Fenster)
- [x] Neues Design „Aurora (Beta)“: leuchtende Farbverläufe, Glas-Karten, Leuchtrand
- [x] Effekte (Beta): Deckkraft der Karten, Leuchtrand, Farbflecken, bewegter Hintergrund, lebendige Animationen, Glanz

## Version 0.40.0 (fertig)

- [x] App zurücksetzen: Warnung mit Datum der letzten Sicherung, „Erst Sicherung erstellen“, Bestätigung mit PIN, Muster, Fingerabdruck oder Gesicht; Nachweise der Zustimmung bleiben mit Frist erhalten
- [x] Anleitung überarbeitet: Erste Schritte, Einstellungen im Überblick, Termine-Liste, Erinnerungen, KI, häufige Fragen
- [x] Rundgang neu: 17 Schritte mit neuen Beispielansichten (Zustimmung, Freigaben, Medikamenten-Check, Tagesstart, Aufgaben-Verlauf, Dokumente, Notruf)
- [x] TODO neu geordnet; Vorschau in der App zeigt offene Punkte und die letzten Versionen

## Version 0.39.0 (fertig)

- [x] Freigaben (Android) mit Status und direktem Weg in die Einstellungen; rote Hinweis-Karte auf Heute, wenn Erinnerungen nicht zuverlässig sind
- [x] Tagesstart: Rückblick auf gestern (ergänzen, bestätigen), danach Tagesplan für heute als PDF (teilen, drucken)
- [x] Zustimmung gilt je pflegender Person: bei geändertem Namen oder Rolle neu bestätigen, neuer Nachweis

## Version 0.38.0 (fertig)

- [x] Pflicht-Zustimmung zu Nutzungsbedingungen und Hinweisen vor der Nutzung (drei Punkte einzeln bestätigen, Name und Rolle)
- [x] Nachweis verschlüsselt in der Datenbank (mit Prüfsummen-Kette), teilbar; Widerruf; automatische Löschung früherer Nachweise nach Ende des dritten Kalenderjahres
- [x] Jede Fassung der Nutzungsbedingungen mit Prüfsumme im Projekt archiviert (docs/nutzungsbedingungen), Build prüft Übereinstimmung

## Version 0.37.0 (fertig)

- [x] Medikamente im Überblick: alle Medikamente mit Einschätzung, Gesamtbild, Hinweise zu Medikationsplan (ab 3) und Medikationsberatung in der Apotheke (ab 5)
- [x] KI-Gesamt-Check: Kombinationen mit Gewichtung (Gering, Beachten, Wichtig), Einnahmezeiten, Gesamtbild, Fragen an Arzt/Apotheke als Aufgabe, Teilen; Hinweis bei geändertem Plan

## Version 0.36.0 (fertig)

- [x] Aufgaben: Erledigt-Datum, Ergebnis und Sterne-Bewertung; „Erledigte entfernen“ verschiebt nur in den Verlauf
- [x] Aufgaben-Verlauf mit Suche; frühere Aufgaben zum gleichen Thema werden beim Anlegen und an offenen Aufgaben automatisch gezeigt
- [x] Erledigte Aufgaben in Auswertung (Tag, Woche, Monat) und in Woche/Monat am Erledigungstag
- [x] Kategorie „Krankenhaus / Entlassbericht“, gewichtete Kategorie-Erkennung
- [x] Wiederkehrende Termine (wöchentlich, 2/3/4 Wochen, monatlich, mit oder ohne Ende), Ändern/Löschen „nur diesen“ oder „diesen und alle folgenden“
- [x] Einnahmen und Termin-Status auch in Woche und Monat eintragen
- [x] Heute: „Termine der nächsten 7 Tage“ in „Wichtig und demnächst“ aufgenommen

## Version 0.35.0 (fertig) – Gesamt-Check

- [x] Logik: keine Einnahme-Erinnerung, wenn schon abgehakt; Benachrichtigung öffnet die richtige Person
- [x] Logik: gelöschte Dokumente/Kontakte werden überall entfernt (Termine, Aufgaben, Medikamente)
- [x] Logik: Einnahmen in Woche/Monat nur für die gewählte Person gezählt
- [x] Notfallkarte immer über das rote Telefon erreichbar (nicht mehr Beta)
- [x] Termine: kommende zuerst, vergangene eingeklappt, bei vergangenen nur sinnvolle Knöpfe
- [x] Aufgaben: erledigte eingeklappt, „Erledigte entfernen“
- [x] Optik: untere Leiste ohne abgeschnittene Beschriftung, Knöpfe in Medikamenten-Karte umbrechen sauber, lesbare Knöpfe auf roten Karten, eigene Farben bleiben lesbar
- [x] Dokument-Fenster: Öffnen/Drucken/Teilen oben, Kopfleiste kompakter; Kontakte mit Knöpfen statt Links
- [x] Einstellungen in Abschnitte gegliedert; Anleitung auf aktuellen Stand gebracht

## Version 0.34.0 (fertig)

- [x] Eingebauter Dokumenten-Betrachter (PDF, Fotos, Text) mit Zoom, Drucken, Teilen, „Andere App“; Einstellung intern/extern

## Version 0.33.0 (fertig)

- [x] Karten auf der Tagesansicht einklappbar, mit Kurzinfo (Anzahl offen usw.), Zustand wird gemerkt

## Version 0.32.0 (fertig)

- [x] Schriftgröße der App einstellbar (Klein, Normal, Groß, Sehr groß, Riesig)

## Version 0.31.0 (fertig)

- [x] Heute-Reiter mit Umschalter Tag · Woche · Monat
- [x] Tagesansicht: Karte „Wichtig und demnächst“ (dringend/überfällig zuerst, dann nächste 3 Tage)
- [x] Wochen- und Monatsübersicht, antippbar und änderbar (Termine/Aufgaben bearbeiten, löschen, neu anlegen, Aufgaben abhaken)

## Version 0.30.0 (fertig)

- [x] Dokument-Vorschläge bei Terminen und Aufgaben genauer: Punktesystem (Arzt, Kategorie, Stichwörter in Titel und Text, Aktualität), Grund wird angezeigt, nur neuester Plan/Bescheid, zu alte oder spätere Dokumente aussortiert
- [x] Schreiben und Vollmachten als PDF: Vollmacht, Schweigepflichtentbindung, Pflegegrad-Antrag, Höherstufung, Widerspruch, Verhinderungspflege, Pflegehilfsmittel, Unterlagen anfordern, Folgerezept, freies Schreiben; Vorschau, Drucken, Speichern, Teilen, in Dokumente ablegen, Aufgabe „unterschreiben und abschicken“

## Version 0.29.0 (fertig)

- [x] Sicherung und Wiederherstellung: eine Datei mit Passwort verschlüsselt (Datenbank, Dokumente, Fotos, Einstellungen), speichern wohin man will, Wiederherstellung auch auf neuem Handy, mit Prüfung und Rückfall

## Version 0.28.0 (fertig)

- [x] Fahrdienst-Anfrage: Hinfahrt und/oder Rückfahrt wählbar, Stockwerk und Aufzug für Abholort und Ziel
- [x] Stockwerk, Aufzug und Zugangshinweise im Profil der Person und bei jedem Kontakt (auch Notfallkarte, Hausbesuch-Mail, Notruf)
- [x] Notruf-Schnellzugriff (rotes Telefon oben): 112, 116 117, 110, „Was sage ich?“, eigene Kontakte, Apotheken-Notdienst, Giftnotruf je Bundesland, Telefonseelsorge; „Wählen“ öffnet nur die Telefon-App
- [x] Umkreissuche für Apotheke, Pflegedienst, Sanitätshaus und Notaufnahme (Kontakte und Notruf), Treffer als Kontakt speichern
- [x] Aufgaben mit Dokumenten verknüpfen

## Version 0.27.0 (fertig)

- [x] Datenbank verschlüsselt (SQLCipher, Schlüssel im Android-Schlüsselspeicher), automatische Umwandlung der vorhandenen Daten mit Rückfall auf die alte Datenbank bei Fehlern; Anzeige unter Einstellungen → Sicherheit

## Version 0.26.0 (fertig)

- [x] Infos: Favoriten – Themenkarten und Meldungen mit „☆ Merken“, eigene Favoritenansicht
- [x] Benachrichtigung „Kurz nachgefragt“, wenn eine Einnahme nach dem Zeitfenster offen bleibt (abschaltbar)
- [x] Darstellung: die drei Farben jedes Designs selbst ändern (Hauptfarbe, Zweitfarbe, Hintergrund; bei Glas/Liquid zwei Hintergrundfarben), live, zurücksetzbar
- [x] Beta: Notfallkarte (Einstellungen → Beta), mit Notruf-Taste, Kopieren, Teilen

## Version 0.25.0 (fertig)

- [x] „Meine Daten“: Rolle (pflegende/r Angehörige/r, Pflegedienst, Betreuungskraft, Betreuer/in, Ehrenamt, selbst, sonstiges), Name, Beziehung bzw. Pflegedienst, Telefon, E-Mail; einmalige Nachfrage beim Start, Hinweiskarte auf Heute, Karte in den Einstellungen
- [x] Vorbereitete E-Mails zu Themen (Arzt: Termin, Absage/Verschiebung, Rezept, Überweisung, Befund, Krankenfahrt, Hausbesuch; Pflegekasse: Beratung, Pflegegrad, Verhinderungspflege, Pflegekurs; Pflegedienst; Apotheke) mit Absender, Kontaktdaten, Angaben zur Person und Medikamenten; prüfen, ändern, in E-Mail-App öffnen oder kopieren; aus Kontakten, Aufgaben und Terminen
- [x] Nachbestell-Mail enthält deine Kontaktdaten
- [x] Infos „Für dich“: Tipps und Hilfen je Rolle, Reihenfolge der allgemeinen Themen und Themenvorschläge nach Rolle

## Version 0.24.0 (fertig)

- [x] Einnahmen nur im Zeitfenster abhaken (30 Min., 1 Std. oder 2 Std. vor und nach der Zeit, einstellbar); davor „abhakbar ab …“, danach Nachtragen
- [x] Rückfragen („Kurz nachgefragt“): genommen aber nicht abgehakt, später genommen (mit Uhrzeit), früher genommen, vergessen, bewusst nicht gegeben, mit Bemerkung; Strich-Kästchen für „nicht genommen, aber beantwortet“
- [x] Termine: hat stattgefunden, abgesagt, verschoben; bei Absage oder Verschiebung Aufgabe „Neuen Termin ausmachen“ für morgen mit Arzt
- [x] Auswertung zählt pünktlich, später (Ø Verspätung), früher, vergessen, bewusst nicht gegeben, ohne Antwort und gibt passende Hinweise (auch für die KI-Hinweise)

## Version 0.23.0 (fertig)

- [x] To-do-Liste auf der Heute-Seite wird zu „Aufgaben“: zuständiger Arzt bzw. Stelle (Anrufen, E-Mail), Wichtigkeit, „Erledigt bis“ mit Überfällig-Hinweis, Außer Haus mit Fahrdienst, sortiert und abhakbar, bearbeitbar
- [x] KI-Zusammenfassung eines Dokuments: „Aufgaben vorschlagen“ (mit Bestätigung des genauen Textes), Auswahl der Vorschläge, Kontakt wird lokal zugeordnet und ist änderbar
- [x] Beschriftungen „Als Aufgabe merken“ statt To-do-Liste

## Version 0.22.0 (fertig)

- [x] Termine: neue Karten mit großem Datum und Wochentag, Countdown („heute“, „in 3 Tagen“), Uhrzeit, Kontakt mit Anruf, Route, Unterlagen und Erinnerung; vergangene Termine grau
- [x] Fahrdienst bei Terminen außer Haus: feste Fahrdienste aus den Kontakten immer zuerst, Suche im Umkreis (OpenStreetMap, nur Ort wird nach Bestätigung gesendet), Anruf/SMS/E-Mail mit vorbereiteter Anfrage, Infos zur Krankenfahrt und Kostenübernahme

## Version 0.21.0 (fertig)

- [x] Mehrseitige Dokumente: mehrere Fotos/PDFs zusammen auswählen, weitere Seiten nachträglich hinzufügen (Kamera, Galerie, Datei), letzte Seite entfernen; alles bleibt ein Dokument
- [x] Zusammenfassung je Dokument (selbst schreiben oder per KI, mit Bestätigung und Platzhaltern für Name/Nummern), auf Karte, im Detail und in der Suche

## Version 0.20.0 (fertig)

- [x] Neuer Reiter „Infos“: aktuelle Themenkarten (Pflegereform/PNOG mit Stand, Beratung, Pflege und Beruf, Entlastung, Medikamente), Meldungssuche, eigene Themen, Themenvorschläge aus den eigenen Daten
- [x] Dokumente: Vorschlag für neue Kategorie mit Namen, eigene Kategorien in Auswahl und Filter
- [x] Dokumente: Word (docx) und LibreOffice (odt) werden gelesen, „Text neu auslesen“ bei bestehenden Dokumenten

## Version 0.19.0 (fertig)

- [x] Dokumente: Kategorie-Filter mit Anzahl, Filter nach Jahr und Arzt/Stelle, Sortierung
- [x] Dokumente: bessere Suche (mehrere Wörter, Arzt, Datum, Fundstelle im Text, beste Treffer zuerst)
- [x] Termine: Unterlagen anhängen, passende Dokumente vorgeschlagen, Mitnehm-Anregungen mit „+ To-do“

## Version 0.18.0 (fertig)

- [x] Medikamentenliste schlanker: nur Foto, Name, Einnahme, Vorrat; alles Weitere über „Infos & Beschreibung“
- [x] KI-Einschätzung je Medikament mit Skalen (Wirkstärke, Nebenwirkungen, Wechselwirkungen) als Anhaltspunkt

## Version 0.17.0 (fertig)

- [x] KI-Dienst direkt bei den KI-Hinweisen wechseln (Schnellwahl bei mehreren eingerichteten Diensten)
- [x] Hinweis bei erreichtem Limit / überlastetem Dienst mit Aufforderung, einen anderen Dienst zu wählen
- [x] Modellliste des Dienstes laden und auswählen
- [x] Haken ✓ bei eingerichteten Diensten in den Einstellungen

## Version 0.16.0 (fertig)

- [x] App-Sperre mit Fingerabdruck, Gesicht oder Handy-PIN (Sperre nach Zeit im Hintergrund wählbar)
- [x] Optional: Bildschirmfotos und App-Vorschau sperren

## Version 0.15.0 (fertig)

- [x] Profil erweitert: Adresse, Krankenkasse/Pflegekasse, Versichertennummer (nur lokal)
- [x] Wegweiser mit Links: Pflegekasse, Pflegestützpunkt, Pflegedienste, Tagespflege, Versorgungsamt in der Nähe (Karten-App), Suche nach Antragsformularen der eigenen Kasse, Pflegelotse, BAFzA, Vordrucke Vorsorgevollmacht

## Version 0.14.0 (fertig)

- [x] Neuer Bereich Dokumente: fotografieren (mehrere Seiten), Foto aus Galerie, Datei laden (PDF, Bild, Text)
- [x] Text wird auf dem Handy gelesen (Foto und PDF), daraus Vorschläge für Titel, Kategorie, Datum, Kontakt; Suche über den Text
- [x] Dokumente verschlüsselt gespeichert, Öffnen und Teilen nur auf Tipp

## Version 0.13.0 (fertig)

- [x] Profil je Person: Geburtsdatum (Alter), Pflegegrad 1 bis 5, "Wichtig zu wissen", nur lokal; Profilkarte auf der Heute-Seite
- [x] Wegweiser Leistungen: Erste Schritte, Leistungen für zu Hause, Pflegeheim, Hilfen für Angehörige, weitere Hilfen; Beträge je Pflegegrad (Stand 11/2026), Schritte auf die To-do-Liste
- [x] Anleitung aktualisiert

## Version 0.12.0 (fertig)

- [x] Medikament antippen zeigt alle Infos auf einen Blick (Beschreibung, PZN, Arzt, Apotheke, Notizen), Beschreibung auch gekürzt auf der Karte
- [x] KI-Assistent frei wählbar: Gemini, Groq, Mistral, OpenRouter (kostenlos), OpenAI, Anthropic, DeepSeek (kostenpflichtig), eigener Schlüssel verschlüsselt auf dem Handy
- [x] KI-Hinweise in Auswertung und Medikament: nur nach Bestätigung mit Textvorschau, ohne Namen der Person, als To-do übernehmbar
- [x] Anleitung aktualisiert

## Version 0.11.0 (fertig)

- [x] Beschreibung zum Medikament aus dem Internet (Wikipedia), nur Name wird gesendet, nur Anhaltspunkt; in den Einstellungen wählbar: nur auf Knopfdruck oder automatisch
- [x] PZN-/Barcode-Erkennung vom Packungsfoto (auf dem Handy), PZN steht auch in der Nachbestell-Liste

## Version 0.10.0 (fertig)

- [x] Pro Medikament zuständigen Arzt (Rezept) und Apotheke aus den Kontakten wählbar
- [x] Nachbestell-Übersicht: was fehlt, gruppiert nach Arzt oder Apotheke, direkt anrufen
- [x] Liste per E-Mail an den hinterlegten Kontakt, nur auf Tipp (nie automatisch), optional mit PDF
- [x] Liste als PDF im eigenen App-Ordner, speichern unter, drucken, teilen, löschen
- [x] Anleitung um Nachbestellung ergänzt

## Version 0.9.0 (fertig)

- [x] Sechs Designs zur Auswahl (Einstellungen → Darstellung): Schlicht, Minimalistisch, Glas, Liquid, Sonnig, Natur, jeweils hell und dunkel, mit Farbvorschau
- [x] Hell, Dunkel oder wie das Handy bleibt getrennt einstellbar

## Version 0.8.0 (fertig)

- [x] Anleitung zum Lesen: nach Themen aufklappbar, mit Suchfeld (Info-Symbol oben oder Einstellungen)
- [x] Rundgang mit Beispielansichten zum Durchblättern, wird beim ersten Start einmal angeboten
- [x] App-Logo (Sonne mit Herz) und "by Sunshine@R" in Einstellungen, Rundgang und Heute-Seite

## Version 0.7.0 (fertig)

- [x] "Eingenommen"-Knopf direkt in der Erinnerung (hakt ab, schließt die Benachrichtigung). Der Vorrat sinkt schon täglich automatisch
- [x] Erinnerungszeit pro Termin (keine, 15 Min., 30 Min., 1 Std., 2 Std., 1 Tag vorher)
- [x] Eigene To-do-Liste je Person auf der Heute-Seite, Empfehlungen aus der Auswertung lassen sich dorthin übernehmen
- [x] Termin per Knopf in den Handy-Kalender übernehmen
- [x] Darstellung: wie das Handy, hell oder dunkel (Einstellungen)

## Version 0.6.0 (fertig)

- [x] Mehrere betreute Personen: Auswahl oben auf jeder Seite, anlegen, umbenennen, löschen. Medikamente, Termine, Abhaken und Bemerkungen gelten je Person, Kontakte sind für alle gemeinsam
- [x] Tab "Kontakte" statt "Ärzte": auch Pflegedienst, Hausnotruf, Pflegekasse, Apotheke, Sozialamt usw. mit vielen Vorauswahlen und Filter nach Art
- [x] Vorauswahl-Felder zeigen jetzt "Auswahl antippen oder selbst eintippen"
- [x] Erinnerungen nennen bei mehreren Personen den Namen

## Version 0.5.0 (fertig)

- [x] Heute-Seite: Einnahmen und Termine abhaken, Bemerkung dazu (z. B. später genommen, Termin verschoben), Bemerkung zum Tag
- [x] Auswertung für Tag, Woche und Monat: was gut lief, was auffällt, Empfehlungen als Anhaltspunkte, teilen möglich (alles lokal berechnet)
- [x] Termin mit Beginn und Ende (von – bis)
- [x] Vorrat rechnet sich jeden Tag automatisch herunter, Nachbestell-Warnung stimmt dadurch wirklich
- [x] Kürzere Feldnamen bei Vorrat und Verbrauch

## Version 0.4.0 (fertig)

- [x] Medikament per Foto der Packung anlegen, Texterkennung direkt auf dem Handy (keine Datenübertragung)
- [x] Foto der Packung wird beim Medikament gespeichert und angezeigt (nur lokal)
- [x] Kleinigkeiten: Datumsfeld einzeilig, GitHub-Projektlink aus den Einstellungen entfernt

## Version 0.3.0 (fertig)

- [x] Einstellungen mit App-Update über GitHub, Hintergrund-Hinweis bei neuen Versionen
- [x] Feedback-Funktion und Vorschau auf geplante Funktionen
- [x] Alle Einträge nachträglich bearbeitbar, Löschen mit Rückfrage
- [x] Datum per Kalenderansicht, Uhrzeit per Zeitauswahl
- [x] Vorauswahlen für Fachrichtung, Dosierung, Tageszeiten und Terminarten
- [x] Beim Termin Arzt oder Institution aus der eigenen Liste wählen

## Version 0.2.0 (fertig)

- [x] Erinnerung zur Einnahmezeit
- [x] Terminerinnerung 1 Stunde vorher
