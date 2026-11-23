# TODO

Stand: v0.75.0 · Offene Punkte zuerst, darunter der Verlauf aller Versionen.

## Als Nächstes

- [ ] Health-Connect-Bibliothek auf 1.1.0 stabil heben, sobald die App auf compileSdk 36 und Android Gradle Plugin 8.9 umgestellt ist
- [ ] Pflegereform 2027: sobald beschlossen, Selbsteinschätzung an neue Schwellen und Modulbewertung anpassen; daten/leistungen.txt anpassen (Beträge, Übertrag, Hinweis); für die neuen Budgets ggf. neue Bereiche in der App

## Ideen und Optimierungen

- [ ] Weitere kleine Helfer von Sunny: z. B. Erinnerung ans Losfahren mit Wegzeit, Wetterhinweis vor Terminen, Geburtstage im Umfeld, Rezept-Abholung bündeln

- [ ] Packungsfoto: Erkennung verbessern (Wirkstoff, Packungsbeilage), Barcode live mit der Kamera
- [ ] Widget: zweite Größe mit Tagesübersicht
- [ ] Barrierefreiheit: Vorlesen für Schreiben und Medikamentenplan
- [ ] Health Connect: eigene Werte der pflegenden Person (Schlaf, Schritte) freiwillig im Tages-Motivator, Abruf im Hintergrund
- [ ] Mehrsprachigkeit für Betreuungskräfte (z. B. Polnisch, Rumänisch)
- [ ] Motivator: Pausen-Erinnerung, Stimmungsverlauf in der Auswertung (nur für dich, nicht in geteilten Berichten), Ziel-Schritte als Aufgabe mit Frist übernehmen
- [ ] KI direkt auf dem Handy: Gemini Nano über ML Kit (nur neuere Geräte, z. B. ab Pixel 9) oder ein eigenes kleines Modell in der App (z. B. Gemma, mehrere GB Download)
- [ ] Designs verfeinern (Schriftarten, weitere Varianten); Effekte: echte Unschärfe hinter Glas-Karten ab Android 12, Glanz auch auf Knöpfen

## Bitte auf dem Handy prüfen (bisher nicht getestet)

Hilfe dazu: Einstellungen → Hilfe und Rückmeldung → „Selbsttest“ (Datenbank prüfen, Beispiel-PDFs mit erfundenen Daten, Beispiel-Grafiken, Test-Benachrichtigungen). Die Datenbank-Umstellungen von Version 5 bis 29 prüft seit v0.54.0 bei jedem Bauen ein automatischer Test gegen das Schema (Tabellen, Spalten, Typen); auf dem Handy bleibt zu prüfen, dass die eigenen Daten nach dem Update vollständig da sind.

- [ ] Wecker: Termin morgen vor 10 Uhr → ab 14 Uhr Vorschlag in Sunnys Karte; „Wecker stellen“ stellt ihn in der Uhr-App (Toast), „Andere Zeit …“ öffnet die Uhr-App; Abendüberblick nennt den Wecker
- [ ] Stimmung „sehr schlecht“ speichern → Sunny erscheint und liest die Unterstützung vor; „Weiter“ stoppt; Schalter „Empfehlungen gleich vorlesen“ aus → nur Knopf
- [ ] Einblendung: Video jetzt rund ausgeschnitten? Ort, Größe, Deckkraft, Rand und Hintergrund wirken sofort; „Bei jedem Vorlesen“ zeigt Sunny auch im Tagesplan, bei Dokumenten und in der Anleitung; Stimme mit Regler unter Persönlicher Assistent
- [ ] Sunny-Bild: Ausdrücke passen zur Lage? Beim „Erzähl mal“ blendet sich das Video unten rechts ein (stumm, Antippen stoppt); „Erzählt von sich aus“ beim Öffnen der Heute-Seite, nicht öfter als alle 3 Stunden, nicht bei stummem Handy; Schlagzeile mit „Lesen“
- [ ] Pflegetagebuch → Selbsteinschätzung: Fragen beantworten, Speichern, Teilen, im PDF beifügen
- [ ] Widget „✓ Eingenommen“ → „Ja“ hakt ab, „Nein“ und Ablauf nach 30 Sekunden; bei Insulin „Eintragen …“; Budgets „Beträge aktuell?“ und Hinweis zur Reform, Assistent fragt nach
- [ ] Einnahme-Fenster (Datenbank 29): Insulin antippen → Menge ändern, Blutzucker dazu, „+ Messwert“, Speichern; Erinnerung öffnet das Fenster; Haltbarkeit nach Anbruch mit „Neu angebrochen“ und Erinnerung am letzten Tag
- [ ] Fenster schließen nicht mehr beim Tippen daneben (alle Eingabe- und Info-Fenster, Datum und Uhrzeit); Ordner mit Unterordnern laden; Dateien aus „Dateien“, Drive oder Dropbox über „Teilen“ → „Mein Helfer“ übernehmen (auch bei gesperrter App erst nach dem Entsperren)
- [ ] Messwerte (Datenbank 28): neue Werte (Atemfrequenz, Schmerz, INR, Urin), Angabe wie „nüchtern“ oder „Ohr“, Schnellknöpfe auf der Heute-Seite, Messzeiten mit Erinnerung (kommt sie nur, wenn noch nicht gemessen? Antippen öffnet das Eintragen), „Heute zu messen“, Assistent und Tagesplan; Bericht mit eigenem Zeitraum, Grafik, E-Mail an den Arzt und „In Dokumente ablegen“; Pflegetagebuch-PDF mit Messungen
- [ ] Medikamente mit Einheit (Datenbank 27): nach dem Update alle Medikamente mit Vorrat da? Insulin mit IE und Menge je Uhrzeit, Saft in ml, ½ Tablette; „Heute einnehmen“, Erinnerung, Tagesplan und Medikamentenplan-PDF zeigen die Menge; „Neue Packung“ und Vorrat-Fenster mit Komma; Packungsfoto mit ml oder Pens
- [ ] Sammel-Import: mehrere Dateien, mehrere Fotos und „Ganzen Ordner laden“; stimmen die Vorschläge (Seite 1 und 2, Fotos kurz nacheinander)? Haken, „↑ nach oben“, „Dokument 2 von 5“, Überspringen, „Restliche verwerfen“; Einstellungen → Persönlicher Assistent
- [ ] Berichte und Auswertung: jedes PDF einmal erzeugen und teilen; Budgets und Übergabe aus dem Profil; „Frag mich“ mit KI
- [ ] Heute-Seite: „Heute einnehmen“ und Aufgaben springen nach oben, wenn etwas dran ist; Tagesstart-Angebot in Sunnys Karte; Stimme wählen (z. B. SherpaTTS mit Piper-Stimme) und Probe hören
- [ ] Assistent morgens und abends (kommt die Benachrichtigung, passt der Text?), Checkliste an der Terminerinnerung, PDF-Knöpfe in der Vorbereitung, Ruhezeit (Trinken/Stimmung schweigen, Medikamente kommen), Erinnerungsübersicht, eigene Werte (Schlaf, Schritte, Ruhepuls) und „Das ist mein eigenes Gerät“
- [ ] Assistent: Karte oben auf der Heute-Seite, Gesicht und Ausdrücke, Vorschläge übernehmen und ablehnen, Terminvorbereitung, Gesicht in der Leiste auf allen Seiten, „Frag mich“, Name ändern, ausschalten (Motivator kommt zurück); Hinweis auf neue Uhr-Werte in „Trinken und Werte“
- [ ] Health Connect (Beta): Zugriff erlauben, Werte holen, Quellen wählen (z. B. Fitbit, Samsung Health, Garmin, Withings, Blutdruckgerät-App), Übernahme ohne Doppelte, Einstellungen → Beta-Funktionen
- [ ] Tagesplan-PDF: Kästchen fürs Trinken und Zeilen für Werte auf Papier; Vorlesen im Tagesplan, bei Dokumenten und in der Anleitung (deutsche Stimme vorhanden?); Design „Hoher Kontrast“ hell und dunkel; „Größer“ und „Sehr groß“ auf allen Seiten (nichts abgeschnitten?)
- [ ] Entlassbericht mit echtem Bericht: Medikation bei Entlassung richtig erkannt (nicht die bei Aufnahme), Kontrollen mit Datum, Übernehmen als Aufgabe und als Termin, KI-Auslesen
- [ ] Widget: hinzufügen über die Einstellungen, Anzeige nach Abhaken (auch über die Benachrichtigung), „Offen seit“, Termine, Namen ausblenden, Antippen öffnet die App ohne Einspieler
- [ ] Neues App-Symbol auf dem Startbildschirm (rund und bei Themensymbolen), Logo in der Kopfzeile; Einspieler beim Start: Bild füllt die Breite, Ton, „Ton aus“, Überspringen, Abschalten in den Einstellungen, kein Einspieler beim Öffnen aus einer Erinnerung, App-Sperre erst danach
- [ ] Nach den Updates: alte Daten vollständig da (Selbsttest → „Datenbank prüfen“ zeigt die Anzahl je Bereich), Wiederherstellen einer Sicherung
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
- [ ] Zuständigkeiten (Datenbank 24): Standard-Arzt und -Apotheke, „Standard“ im Medikament, Vertretung mit Zeitraum in Nachbestellung, Infos, Folgerezept und Medikamentenplan-PDF
- [ ] Trinken und Werte, Pflegetagebuch (Datenbank 25): Schnelltasten und Werte eintragen im Alltag, „Wie am Vortag“, Auswertung (Grafiken und PDFs vorab im Selbsttest ansehen)
- [ ] Budgets (Datenbank 26): Ausgaben eintragen, Übertrag bis 30. Juni, „Beträge und Beginn“; Übergabe-Bericht mit eigenen Daten
- [ ] Auf- und Zuklappen überall (Tipp auf den oberen Rand der Heute-Karten), E-Mail mit mehreren Anhängen aus einer Aufgabe (kommen alle Anhänge in deiner E-Mail-App an?), Drucken aus der Aufgabe
- [ ] Erinnerungen ans Trinken und abends ans Pflegetagebuch (kommen nur, wenn nötig; Antippen öffnet die richtige Seite, zuerst mit den Test-Benachrichtigungen im Selbsttest)
- [ ] Leiste unten auf allen Seiten (Abstände oben und unten, Tastatur), Wischen nach oben und „Springen zu“; Schreiben per E-Mail (mit Anhang in deiner E-Mail-App), „✉ Schreiben mailen“ an der Aufgabe
- [ ] Ecken und Effekte (Beta): alle Ansichten mit „Eckig“ und „Rund“, Aurora hell und dunkel, bewegter Hintergrund flüssig und akkuschonend

## Rechtliches vor einer Veröffentlichung

- [ ] Nutzungsbedingungen und Haftungsregelung von einer Anwältin bzw. einem Anwalt prüfen lassen
- [ ] Entscheiden: Aufbewahrung der Nachweise 3 Jahre oder länger (30-Jahres-Frist bei Gesundheitsschäden); zusätzlicher Nachweis beim App-Ersteller (Server) ja oder nein
- [ ] Medizinprodukte-Recht (MDR) prüfen lassen, vor allem den KI-Gesamt-Check der Medikamente; Zweckbestimmung als Organisationshilfe klar festhalten
- [ ] Impressum und Datenschutzerklärung für eine Veröffentlichung (z. B. Play Store), eigener Signaturschlüssel statt Test-Schlüssel

## Als Nächstes: Assistent (Plan aus der Prozess-Prüfung erledigt, fest in der App)

- [ ] Rückmeldungen aus dem Alltag einarbeiten
- [ ] Sammel-Import: Vorschläge mit echten Scans verfeinern (z. B. gleicher Absender und gleiches Datum als weiterer Hinweis)

- [ ] Eigene KI-Stimme direkt in der App (nur falls SherpaTTS nicht reicht): sherpa-onnx mit Piper-Stimme Thorsten oder Kerstin (CC0) oder apptor-tiny-tts (Apache-2.0, 21 MB); macht die App 25–70 MB größer, Lizenzen der Bibliotheken prüfen

## Version 0.75.0 (fertig)

- Wecker bei frühen Terminen: Sunny erinnert am Vortag und stellt den Wecker nach Tipp in der Uhr-App

## Version 0.74.0 (fertig)

- Empfehlungen gleich vorlesen: Unterstützung nach schlechter Stimmung und KI-Hinweise liest Sunny sofort vor (abschaltbar), sonst Knopf „🔊 Vorlesen“

## Version 0.73.0 (fertig)

- Stimme und Vorlese-Geschwindigkeit (Regler bis sehr schnell) unter „Persönlicher Assistent“
- Einblendung von Sunny einstellbar: wann (auch bei jedem Vorlesen), wo, Größe, Deckkraft, Rand und Hintergrund mit Glas- und Liquid-Effekt, Vorschau
- Video rund ausgeschnitten (TextureView statt VideoView)

## Version 0.72.0 (fertig)

- Sunny-Bild mit Gesichtsausdrücken, Sprech-Video beim Vorlesen
- Sunny erzählt: „Erzähl mal“ und auf Wunsch von sich aus, mit aktueller Schlagzeile (Anhaltspunkt)
- Pflegetagebuch: Selbsteinschätzung wie bei der Begutachtung mit Punkten nach SGB XI, im PDF

## Version 0.71.0 (fertig)

- [x] Beträge der Pflegekasse: Die App liest selbst die jährliche „Übersicht Leistungsbeträge“ des Bundesgesundheitsministeriums (dieses und nächstes Jahr, alle 3 Tage), die selbst gelesenen Werte haben Vorrang vor der Liste im Repo; Übernahme nur nach Bestätigung
- [x] Automatische Prüfung im Repo: GitHub-Ablauf jeden Montag liest dieselbe Übersicht und aktualisiert daten/leistungen.txt (nur bei eindeutig gefundenen, plausiblen Beträgen)

## Version 0.70.0 (fertig)

- [x] Widget: „✓ Eingenommen“ mit Rückfrage („alle genommen?“ → Ja/Nein), nur wenn die Einnahme dran ist; bei Einnahme-Fenster „Eintragen …“ direkt in die App
- [x] Budget-Beträge aktuell halten: Liste im Repo (daten/leistungen.txt, nach BMG), Abgleich mit den eingestellten Beträgen; Assistent fragt vor dem Übernehmen, Hinweise zu geplanten Änderungen; Übertrag aus dem Vorjahr einstellbar (z. B. verfällt am 31.12.)
- [x] Automatischer Test (BenefitRatesTest, auch für die Datei im Repo)

## Version 0.69.0 (im Build 0.70.0 enthalten)

- [x] Einnahme-Fenster je Uhrzeit: alle Medikamente abhaken, gegebene Menge eintragen (z. B. Insulin nach Korrekturschema), Messwerte wie Blutzucker gleich dazu, weitere hinzufügbar, Bemerkung; Erinnerung öffnet das Fenster (Datenbank 29)
- [x] Assistent: Vorschlag, den Verbrauch anzupassen, wenn die gegebenen Mengen an mindestens 5 Tagen deutlich abweichen; „Pro Tag“ bleibt dafür frei änderbar
- [x] Haltbarkeit nach dem Anbruch (Tage laut Packungsbeilage, angebrochen am): Anzeige „haltbar bis“, Erinnerung am letzten Tag und danach, „Neu angebrochen“ an der Karte und als Vorschlag des Assistenten
- [x] Automatischer Test (MedCareTest)

## Version 0.68.0 (fertig)

- [x] Fenster schließen sich nicht mehr, wenn man aus Versehen daneben tippt (alle 80 Fenster, Datum- und Uhrzeit-Auswahl); schließen nur über die Knöpfe oder die Zurück-Taste
- [x] „Ganzen Ordner laden“ nimmt Unterordner mit (bis 3 Ebenen); klare Hinweise, was Android freigibt (nicht das ganze Handy, nicht „Download“ selbst, keine Drive-Ordner)
- [x] Dateien aus anderen Apps teilen („Teilen“ → „Mein Helfer“): eine oder viele Dateien aus der App „Dateien“, Google Drive, Dropbox oder einem E-Mail-Anhang landen im Sammel-Import unter Dokumente; bei gesperrter App erst nach dem Entsperren

## Version 0.67.0 (fertig)

- [x] Weitere Messwerte: Atemfrequenz, Schmerz (0–10), INR (Gerinnung), Urin (Ausfuhr); Vorauswahl der Angabe je Wert (Blutzucker nüchtern/vor/nach dem Essen, Fieber Ohr/Stirn/Achsel, Blutdruck im Sitzen …), gespeichert in Datenbank 28
- [x] Schnellknöpfe je Person auf der Heute-Seite (eigene Auswahl oder die häufigsten) und feste Messzeiten mit Erinnerung, nur wenn noch nicht gemessen; Antippen öffnet das Eintragen
- [x] „Heute zu messen“ auf der Heute-Seite und in „Trinken und Werte“; offene Messzeiten beim Assistenten (Als Nächstes, Morgen und Abend), im Tagesplan als Zeile zum Eintragen, in der Übergabe
- [x] Auswertung: je Wert Spanne und Schnitt (auch je Angabe), geplante Messungen erledigt (z. B. 18 von 21); Pflegetagebuch-PDF zeigt, wie oft gemessen wurde (Bereich 5)
- [x] Bericht über Messwerte: Zeitraum 7/14/30/90 Tage oder von–bis, Werte wählbar, Verlauf als Grafik, Zusammenfassung und alle Messungen; teilen, drucken, per E-Mail (Empfänger aus den Kontakten) oder unter Dokumente ablegen (Befund / Labor); auch für die Terminvorbereitung des Assistenten
- [x] Automatische Tests: Messzeiten, Zuordnung der Messungen, Zahlen für Bericht und Auswertung (MeasurePlanTest)

## Version 0.66.0 (fertig)

- [x] Medikamente mit Einheit: Stück, ml, IE (Insulin), Tropfen, Hub, g; Vorrat, Verbrauch und Packungsinhalt als Kommazahl (Datenbank 27, Tabelle wird beim Update umgebaut, alle Angaben bleiben)
- [x] Menge je Einnahmezeit (z. B. 8 IE um 8 Uhr, 6 IE um 18 Uhr); Verbrauch pro Tag ergibt sich daraus; Vorschlag aus der Dosierung („5 ml“, „½ Tablette“)
- [x] Menge erscheint bei „Heute einnehmen“, Rückfragen, Wochenübersicht, Erinnerung, Tagesplan, Medikamentenplan-PDF (Spalten morgens bis zur Nacht), Übergabe und Notfallblatt
- [x] Medikamentenplan einlesen übernimmt Einheit und Mengen; Packungsfoto erkennt „100 ml“, „5 x 3 ml“ mit „100 Einheiten/ml“ (Insulin), Hübe und Stückzahl
- [x] Automatische Tests: Einheiten und Mengen (MedUnitsTest), Umbau der Tabelle mit allen Daten (MigrationSchemaTest)

## Version 0.65.0 (fertig)

- [x] Persönlicher Assistent fest übernommen: kein Beta mehr, eigener Bereich „Persönlicher Assistent“ in den Einstellungen (eigene Werte über Health Connect bleiben Beta)
- [x] Sammel-Import bei Dokumenten: mehrere Dateien, mehrere Fotos aus der Galerie oder ein ganzer Ordner (bis 30 auf einmal); alles wird zuerst auf dem Handy gelesen
- [x] Vorschlag, was zusammengehört: Seitenangaben im Dateinamen, Seitenzahlen im Text („Seite 2 von 3“), Fotos kurz nacheinander; Gegenhinweise („Seite 1“, neue Anrede, „Seite 3 von 3“); der Assistent fragt mit Gesicht und Namen
- [x] Im Dialog: „Alle einzeln“, „Vorschlag“, „Alles ein Dokument“, Haken je Datei, Reihenfolge ändern; danach jedes Dokument nacheinander mit dem gewohnten Ablauf („Dokument 2 von 5“, Überspringen, Restliche verwerfen)
- [x] Automatischer Test für die Vorschläge (DocGroupingTest)

## Version 0.64.0 (fertig)

- [x] „Berichte und Ausdrucke“ oben in der Auswertung (jetzt „Berichte und Auswertung“): Medikamentenplan, Notfallkarte, Tagesplan, Werte (14 Tage), Pflegetagebuch (30 Tage), Übergabe, Nachbestellung, Budgets; erreichbar auch im Profil unter „Berichte“
- [x] Budgets und Übergabe aus dem Reiter Infos ins Profil der Person verlegt (Infos bleibt für Wissen und Meldungen); Anleitung und Rundgang angepasst
- [x] „Frag mich“ beim Assistenten auf Wunsch mit KI: nur Frage und passende Abschnitte der Anleitung werden gesendet, keine Daten aus der App; allgemeine Pflegefragen nur als Anhaltspunkt

## Version 0.63.0 (fertig)

- [x] Heute-Seite nach Dringlichkeit: offene Einnahme im Zeitfenster und fällige oder überfällige Aufgaben stehen direkt unter dem Assistenten
- [x] Gemeinsame Regeln: Einnahme-Zeitfenster aus den Einstellungen gilt für Heute-Seite, Assistent, Widget und Benachrichtigungen; Tagesplan lässt abgesagte und verschobene Termine weg
- [x] Tagesstart über den Assistenten: Angebot in seiner Karte statt automatischem Fenster; Rückblick und Tagesplan als Knöpfe in der Karte
- [x] Vorlesen: Sprachausgabe und Stimme wählbar (nur deutsche Stimmen ohne Internet), Hinweis auf SherpaTTS mit Piper-Stimmen (Thorsten, Kerstin; CC0)
- [x] Automatische Tests: Tagesplan ohne abgesagte Termine

## Version 0.62.0 (fertig)

- [x] Assistent meldet sich morgens (Überblick, Vorbereitung für heutige Termine, Vorschläge) und abends (geschafft, offen, für morgen bereitlegen, Pflegetagebuch); Zeiten wählbar, eigener Benachrichtigungskanal
- [x] Terminerinnerung mit Checkliste vom Assistenten; Medikamentenplan und Werte (14 Tage) direkt aus der Terminvorbereitung als PDF
- [x] Ruhezeit (Standard 22–7 Uhr) für Trinken, Stimmungs-Check und Assistent; Erinnerungsübersicht in den Einstellungen mit Hinweis, wenn eine Zeit in der Ruhezeit liegt
- [x] Health Connect: eigene Geräte markieren (nie bei der gepflegten Person); eigene Werte Schlaf, Schritte, Ruhepuls mit eigenem Schalter und Rechten, nur gelesen, Hinweise in „Für dich“
- [x] Automatische Tests für Ruhezeit, Morgen- und Abendtext, Checkliste und Hinweise zu eigenen Werten

## Version 0.61.0 (fertig)

- [x] Prozess-Prüfung aller Funktionen mit Plan (Dokument „Funktionen, Prozess-Prüfung und persönlicher Assistent“)
- [x] Persönlicher Assistent (Beta) statt Tages-Motivator: Name wählbar (Sunny, Lumi, Elio, Hella, Mila, Frieda, Kompass oder eigener), digitales Gesicht mit sechs Ausdrücken, Karte oben auf der Heute-Seite
- [x] Gemeinsame Tageslage (Einnahmen, Termine, fällige Aufgaben über alle Personen), vom Widget mitgenutzt
- [x] Vorschläge mit „Übernehmen“ und „Nein, danke“: Priorität, neue Frist, Rezept bestellen beim Zuständigen (Vertretung beachtet)
- [x] Terminvorbereitung für die nächsten 36 Stunden
- [x] Gesicht in der Leiste unten: Hinweise zur Seite, Vorschläge, „So geht's hier“, „Frag mich“ (Anleitung ohne Internet)
- [x] Hinweis auf neue Werte aus Health Connect in der Heute-Karte „Trinken und Werte“
- [x] Automatische Tests für Tageslage, Vorschläge und Terminvorbereitung

## Version 0.60.0 (fertig)

- [x] Beta: Werte von Uhr, Fitnessband, Waage oder Blutdruckgerät über Health Connect: Blutdruck, Puls (Einzelmessungen, Ruhepuls), Gewicht, Blutzucker, Temperatur, Sauerstoff, Trinkmenge; nur lesen, 2/7/30 Tage, nach Quelle sortiert, Quellen je Person gemerkt, keine Doppelten, Quelle als Notiz
- [x] Neue Einstellungs-Kategorie „Beta-Funktionen“ mit Status, Rechten und „Health Connect öffnen“; Erklärseite für Health Connect („Was wird gelesen?“)
- [x] Automatische Tests für Abgleich und Plausibilität der übernommenen Werte

## Version 0.59.0 (fertig)

- [x] Tagesplan: Abschnitt „Trinken und Werte“ mit Trinkziel und einem Kästchen je Glas (200 ml), gestern getrunken, Zeilen zum Eintragen für jeden in 14 Tagen gemessenen Wert mit letztem Wert (Bildschirm und PDF)
- [x] Barrierefreiheit (Einstellungen → Darstellung): Design „Hoher Kontrast“, Größe von Knöpfen und Feldern (Normal, Größer, Sehr groß), Vorlesen mit Geschwindigkeit und Probe
- [x] „🔊 Vorlesen“ im Tagesplan, bei Dokumenten und in jedem Abschnitt der Anleitung; hört beim Verlassen der Seite auf
- [x] Automatische Tests für den Tagesplan-Abschnitt

## Version 0.58.0 (fertig)

- [x] Entlassbericht: Karte „Aus dem Entlassbericht“ im Dokument (öffnet sich nach dem Speichern): Entlassungsdatum, Medikation bei Entlassung aus dem passenden Abschnitt (Aufnahme- und Vormedikation zählen nicht, abgesetzte oder pausierte Mittel werden genannt), Übernahme über den bekannten Plan-Vergleich
- [x] Kontrollen und Termine aus dem Bericht (Labor, Wiedervorstellung, Fäden, Wunde, Ultraschall, EKG, Röntgen …) mit zuständiger Stelle; Zeiträume ab Entlassungstag; übernehmen als Aufgabe „Termin vereinbaren“ (Frist eine Woche vorher) oder als Termin, Bericht als Unterlage verknüpft
- [x] „Mit KI genauer auslesen“ für Entlassberichte (Medikamente, abgesetzte Mittel, Kontrollen in einem Schritt; Name und Nummern der Person ersetzt)
- [x] Startbildschirm-Widget: nächste Einnahme und nächster Termin, „Widget hinzufügen“ in den Einstellungen, Namen ausblendbar, aktualisiert sich beim Abhaken, bei Erinnerungen und regelmäßig
- [x] Automatische Tests für Entlassbericht und Widget-Inhalt

## Version 0.57.0 (fertig)

- [x] Logo von Sunshine@R als App-Symbol (anpassbares Symbol mit Hintergrund, einfarbige Fassung für Themensymbole) und in der App: Kopfzeile, Sperrbildschirm, Rundgang, Einstellungen, Ende der Heute-Seite
- [x] Einspieler beim Start (Video auf 0,8 MB verkleinert): Tippen, „Überspringen“ oder Zurück beendet ihn; „Ton aus“ wird gemerkt; ohne Ton läuft Musik anderer Apps weiter; nicht beim Öffnen aus einer Erinnerung; Einstellungen → Darstellung → „Einspieler beim Start“ (an/aus, Ton, Jetzt ansehen)
- [x] Startbild ab Android 12 in dunklem Petrol passend zum Einspieler

## Version 0.56.1 (fertig)

- [x] E-Mail: installierte E-Mail-Apps werden gezielt gesucht und angesprochen (bei mehreren mit Auswahl), sonst die Teilen-Auswahl des Handys; gilt für Aufgaben, Schreiben, Vorlagen, Nachbestellung und Fahrdienst
- [x] E-Mail zur Aufgabe findet das Schreiben auch über den Titel in der Aufgabe und hängt es an; Hinweis, wenn es nicht unter Dokumente liegt

## Version 0.56.0 (fertig)

- [x] Aufgaben: „✉ E-Mail“ bzw. „✉ Schreiben mailen“ mit Empfänger, Betreff und Text (aus dem Schreiben), Anhänge frei aus allen Dokumenten wählbar (verknüpfte vorausgewählt, Suche, auf Wunsch verknüpfen); „🖨 Drucken“ der verknüpften Unterlagen mit Teilen und Ansehen
- [x] Einheitliches Auf- und Zuklappen: Dokumente, Medikamente, Kontakte, Termine und Aufgaben per Tipp, oben „Alle aufklappen“ bzw. „Alle zuklappen“; Karten auf der Heute-Seite ohne „Einklappen“-Schrift, ein Tipp auf den oberen Rand klappt zu
- [x] Aufgaben: Tipp auf den Text klappt auf statt abzuhaken (abhaken nur mit dem Kästchen)

## Version 0.55.0 (fertig)

- [x] Schreiben und Vollmachten: „Als E-Mail senden“ mit demselben Text, Empfänger-Adresse aus den Kontakten, PDF als Anhang wählbar; „Als Aufgabe merken“ verknüpft Entwurf und Empfänger, an der Aufgabe und im Dokument „✉ Schreiben mailen“
- [x] Tages-Motivator: Vorhaben aus dem KI-Impuls stehen auch auf der Heute-Seite, Karte zeigt alle Vorhaben und dieselbe Stimmung wie die Smiley-Leiste
- [x] Leiste unten auf allen Seiten; Antippen eines Bereichs schließt die offene Seite; nach oben wischen oder Griff antippen: „Springen zu“ mit den Abschnitten der Seite (Heute, Medikamente, Kontakte, Termine, Überblick, Trinken und Werte, Pflegetagebuch, Budgets, Motivator)

## Version 0.54.0 (fertig)

- [x] Trink-Erinnerung (aus, alle 2 oder 3 Stunden, nur wenn unter Plan) und abendliche Erinnerung ans Pflegetagebuch (nur wenn heute leer); Antippen öffnet die passende Seite
- [x] Budgets der Pflegekasse: Entlastungsbetrag (131 €/Monat, Übertrag bis 30. Juni) und Gemeinsamer Jahresbetrag für Verhinderungs- und Kurzzeitpflege (3.539 €, je bis 8 Wochen) mit Ausgaben, Stand der Erstattung, Rest; Hinweis auf die geplante Reform 2027; Beträge änderbar
- [x] Übergabe-Bericht als PDF für Vertretung, Urlaub oder Pflegedienst: Zeitraum, Vertretung, eigene Hinweise (je Person gemerkt), Medikamente mit Vorrats-Hinweis, Termine, Kontakte und Zuständigkeiten, Aufgaben, Werte, Hilfebedarf aus dem Pflegetagebuch
- [x] Prüfhilfen: automatischer Test der Datenbank-Umstellungen gegen das exportierte Schema und Tests der Rechenlogik bei jedem Bauen; Selbsttest in der App mit Datenbank-Prüfung, Beispiel-PDFs, Beispiel-Grafiken und Test-Benachrichtigungen
- [x] KI für Einsteiger erklärt (Was ist ein Schlüssel, Kosten, Daten, Schritt für Schritt mit Gemini, Probleme lösen) in den Einstellungen und in der Anleitung; Anleitung und Rundgang ergänzt

## Version 0.53.0 (fertig)

- [x] Trink- und Vitalprotokoll: Trinkmenge mit Schnelltasten und Ziel (Richtwert DGE ab 65: 1300 ml, änderbar), Blutdruck mit Puls, Puls, Gewicht, Blutzucker (mg/dl oder mmol/l), Temperatur, Sauerstoff; Verlauf als Grafik, eigener Bereich je Wert, PDF für 7/14/30 Tage
- [x] Pflegetagebuch nach den sechs Bereichen der Begutachtung: wobei, Art der Hilfe, wie oft, Minuten, nachts, Notiz; Tage blättern, „Wie am Vortag übernehmen“; PDF-Bericht mit Zusammenfassung je Bereich und allen Einträgen
- [x] Auswertung nennt Trinkmenge, Messwerte, Werte außerhalb des eigenen Bereichs und Pflegetagebuch-Einträge

## Version 0.52.0 (fertig)

- [x] Zuständigkeiten je Person: Arzt für Rezepte und Medikamente, Apotheke für Bestellung und Abholung; festlegen unter Medikamente oder beim Kontakt („Zuständig …“)
- [x] Medikamente ohne eigene Angabe nutzen den zuständigen Kontakt („Standard“), auch nach dem Einlesen eines Plans; einzeln weiterhin änderbar
- [x] Vertretung mit Von und Bis (z. B. Urlaub), gilt automatisch in Nachbestellung, Medikament-Infos, Folgerezept-Schreiben und Überblick
- [x] Medikamentenplan-PDF nennt zuständigen Arzt und Apotheke

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
