package de.meinhelfer.tag.ui

/** Ein Punkt in der Anleitung: Überschrift und kurzer Erklärtext. */
data class GuideItem(val title: String, val text: String)

/** Eine Kategorie der Anleitung. */
data class GuideSection(val id: String, val title: String, val summary: String, val items: List<GuideItem>)

/** Inhalt der Anleitung zum Lesen. Wenn sich die App ändert, hier mitpflegen. */
val GUIDE_SECTIONS: List<GuideSection> = listOf(
    GuideSection(
        id = "start",
        title = "Erste Schritte",
        summary = "Überblick über die App und wie du startest",
        items = listOf(
            GuideItem(
                "Was kann die App?",
                "„Mein Helfer für den Tag“ unterstützt pflegende Angehörige im Alltag: Medikamente mit Einnahmen und Vorrat, Termine mit Fahrdienst und Unterlagen, Aufgaben, Dokumente (verschlüsselt, mit Betrachter und Druck), Schreiben und Vollmachten als PDF, Kontakte, Notruf-Schnellzugriff, Tages-, Wochen- und Monatsübersicht, Auswertung, Wegweiser zu Leistungen und aktuelle Infos. Alles bleibt auf dem Handy.",
            ),
            GuideItem(
                "Die sechs Bereiche unten",
                "Heute: was jetzt ansteht (Tag, Woche, Monat). Medikamente: Pläne, Vorrat und Nachbestellung. Termine: Arzt- und Pflegetermine. Dokumente: Befunde, Bescheide, Schreiben. Kontakte: Ärzte, Pflegedienst, Apotheke und mehr. Infos: Aktuelles zu Pflege und Leistungen.",
            ),
            GuideItem(
                "Oben rechts",
                "Das rote Telefon-Symbol öffnet den Notruf-Schnellzugriff mit Notfallkarte, das Info-Symbol diese Anleitung, das Zahnrad die Einstellungen (Updates, Darstellung, Sicherung, Feedback).",
            ),
            GuideItem(
                "Notruf und Hilfe (rotes Telefon)",
                "Von jeder Seite aus erreichbar. Oben stehen 112 (Notruf), 116 117 (Ärztlicher Bereitschaftsdienst, wenn es nicht lebensbedrohlich ist, aber nicht bis zur Sprechstunde warten kann) und 110 (Polizei). Darunter „Was sage ich?“: Wo (Adresse mit Stockwerk und Aufzug aus dem Profil), Wer (Name, Geburtstag, Pflegegrad), Was ist passiert, Rückrufnummer. Dann deine eigenen Kontakte mit Telefonnummer (Hausarzt, Pflegedienst, Apotheke zuerst), „Hilfe in der Nähe“ (Notaufnahme, Apotheke, Pflegedienst per Umkreissuche) und weitere Nummern: Apotheken-Notdienst (0800 00 22833, vom Handy 22833), Giftnotruf je Bundesland, Telefonseelsorge 0800 111 0111. „Wählen“ öffnet nur die Telefon-App mit der Nummer, die App ruft nie selbst an. Bei Lebensgefahr immer zuerst 112. Stand Nov. 2026, ohne Gewähr.",
            ),
            GuideItem(
                "Empfohlene Reihenfolge",
                "1. Nutzungsbedingungen bestätigen (mit deinem Namen und deiner Rolle). 2. Unter Einstellungen → Freigaben Benachrichtigungen und pünktliche Wecker erlauben. 3. Person umbenennen und Profil ausfüllen (Pflegegrad, Adresse, Stockwerk, Wichtiges). 4. Kontakte eintragen (z. B. Hausarzt, Apotheke). 5. Medikamente mit Einnahmezeiten und Vorrat anlegen. 6. Termine eintragen. 7. Eine Sicherung anlegen. Danach genügt meist die Heute-Seite mit dem Tagesstart.",
            ),
        ),
    ),
    GuideSection(
        id = "meinedaten",
        title = "Meine Daten und E-Mails",
        summary = "Wer du bist, vorbereitete E-Mails, Tipps für dich",
        items = listOf(
            GuideItem(
                "Meine Daten",
                "Unter Einstellungen → Meine Daten (und beim ersten Start) trägst du ein, wer du bist: pflegende/r Angehörige/r, Pflegekraft vom Pflegedienst, Betreuungs- oder Haushaltskraft, Betreuer/in oder Bevollmächtigte/r, Nachbar/in oder Ehrenamt, du selbst oder etwas anderes. Dazu kommen Name, ggf. Beziehung (z. B. Sohn) oder Pflegedienst, Telefon, E-Mail und deine Adresse. Die Adresse steht dann bei Bedarf unter E-Mails und Schreiben, in der Vollmacht und Schweigepflicht-Entbindung (als Adresse der bevollmächtigten Person) und auf der Notfallkarte bei der Ansprechperson. Das bleibt nur auf deinem Handy und wird nie automatisch gesendet. Name und Rolle stammen aus deiner Zustimmung zu den Nutzungsbedingungen. Änderst du sie hier, musst du die Nutzungsbedingungen neu bestätigen.",
            ),
            GuideItem(
                "Vorbereitete E-Mails",
                "Bei Kontakten („E-Mail“), bei Aufgaben und bei Terminen („Nachricht“) öffnet sich ein Entwurf. Wähle oben das Thema, z. B. Termin anfragen, Termin absagen oder verschieben, Rezept anfordern, Überweisung, Befund, Krankenfahrt, Hausbesuch, Pflegeberatung, Verhinderungspflege oder Pflegegrad beantragen. Die Themen passen zur Art des Kontakts (Arzt, Pflegekasse, Pflegedienst, Apotheke).",
            ),
            GuideItem(
                "Was schon drinsteht",
                "Anrede, wer du bist, Name der betreuten Person, passende Angaben zum Thema (bei Rezepten die Medikamente mit Vorrat und PZN, bei Terminen Datum und Uhrzeit) und am Ende deine Kontaktdaten. Mit den Haken legst du fest, ob Geburtsdatum, Pflegegrad, Versichertennummer oder Adresse im Text stehen. Stellen mit „(bitte ergänzen)“ füllst du über „Grund / Anliegen“ oder direkt im Text aus.",
            ),
            GuideItem(
                "Prüfen und abschicken",
                "Du kannst Betreff und Text frei ändern. „In E-Mail-App öffnen“ übergibt alles an dein Mailprogramm, dort schickst du selbst ab. „Text kopieren“ legt ihn in die Zwischenablage. Die App sendet nie selbst. Normale E-Mails sind nicht verschlüsselt, schreibe darum nur, was nötig ist.",
            ),
            GuideItem(
                "Tipps für dich",
                "Je nach Rolle zeigt der Reiter „Infos“ zuerst Tipps und Hilfen für dich: z. B. Auszeit und Entlastung, Absicherung, Pflegekurs und Austausch, Kur für Pflegende, bei Pflegediensten Zusammenarbeit und Gesundheit im Beruf, bei Betreuungskräften Absprachen und Pausen, bei Betreuern Aufgaben und Nachweise. Auch die Reihenfolge der allgemeinen Themen und die Themenvorschläge richten sich danach. Alle Angaben ohne Gewähr.",
            ),
        ),
    ),
    GuideSection(
        id = "person",
        title = "Personen",
        summary = "Für wen gilt was – mehrere Personen betreuen",
        items = listOf(
            GuideItem(
                "Stockwerk und Aufzug",
                "Im Profil der Person (Name antippen, Bearbeiten) trägst du Stockwerk, Aufzug (ja / nein) und Hinweise zum Zugang ein (Klingel, Treppen, Hof). Das wird bei jeder Fahrdienst-Anfrage als Abholort angegeben, steht bei „Was sage ich?“ im Notruf-Fenster, in der Notfallkarte und in Hausbesuch-E-Mails.",
            ),
            GuideItem(
                "Für wen gilt es?",
                "Oben auf jeder Seite steht „Für: Name“. Medikamente, Termine, Abhaken, Bemerkungen, Aufgaben und Auswertung gelten immer für diese Person.",
            ),
            GuideItem(
                "Profil: Geburtsdatum, Pflegegrad, Wichtiges",
                "Beim Anlegen oder über den Namen → „Profil bearbeiten“ kannst du Geburtsdatum, Pflegegrad (1 bis 5) und „Wichtig zu wissen“ eintragen, z. B. Allergien oder Besonderheiten. Dazu kommen Adresse (Straße, PLZ, Ort), Krankenkasse und Versichertennummer, damit der Wegweiser passende Links liefert und du Anträge schneller ausfüllen kannst. Alles ist freiwillig, bleibt nur in der App und wird nie automatisch gesendet. Auf der Heute-Seite steht oben das Profil mit Alter und Pflegegrad.",
            ),
            GuideItem(
                "Person wechseln oder anlegen",
                "Auf den Namen tippen. Dort wählst du eine andere Person oder legst eine neue an. Praktisch, wenn du z. B. für beide Eltern sorgst.",
            ),
            GuideItem(
                "Umbenennen und löschen",
                "Im selben Menü kannst du die Person umbenennen. Beim Löschen gehen ihre Medikamente, Termine, Bemerkungen und Aufgaben verloren. Das lässt sich nicht rückgängig machen. Kontakte bleiben erhalten, weil sie für alle gelten.",
            ),
        ),
    ),
    GuideSection(
        id = "heute",
        title = "Heute",
        summary = "Tag, Woche, Monat, Abhaken, Aufgaben und Nachbestell-Warnung",
        items = listOf(
            GuideItem(
                "Tagesstart: Rückblick und Tagesplan",
                "Beim ersten Öffnen am Tag zeigt die App den Rückblick auf gestern: Zusammenfassung, Einnahmen und Termine (was noch fehlt, ist rot und lässt sich direkt nachtragen), erledigte Aufgaben und die Bemerkung zum Tag. Mit „Bestätigen“ hältst du fest, dass der Tag geprüft ist. Danach kommt der Tagesplan für heute: Wichtiges, Einnahmen, Termine, Aufgaben (überfällig, heute, dringend, nächste 3 Tage), Nachbestellen und Termine morgen. Er lässt sich als PDF teilen oder drucken (mit Kästchen zum Abhaken auf Papier). Oben auf der Heute-Seite erreichst du beides jederzeit („Rückblick gestern“, „Tagesplan (PDF)“). Bei mehreren Personen gibt es den Tagesstart je Person. Abschalten: Einstellungen → Heute-Seite.",
            ),
            GuideItem(
                "Tag, Woche, Monat",
                "Oben auf dem ersten Reiter („Heute“) wählst du zwischen Tag, Woche und Monat. „Tag“ ist immer der aktuelle Tag. „Woche“ zeigt sieben Tage untereinander mit Terminen, Aufgaben, erledigten Aufgaben und Einnahmen. Einnahmen klappst du je Tag mit „Einnahmen: … ▾“ auf und hakst sie dort ab (vergangene werden wie auf der Heute-Seite nachgefragt). Bei Terminen trägst du mit „Wie lief es?“ ein, ob sie stattgefunden haben. „Monat“ zeigt einen Kalender mit Punkten (blau = Termin, grün = Aufgabe, rot = dringend oder überfällig); ein Tipp auf einen Tag zeigt ihn darunter. Mit ‹ und › blätterst du, ein Tipp auf den Titel springt zu heute. Alles ist antippbar: Termin oder Aufgabe antippen zum Bearbeiten oder Löschen, Aufgaben direkt abhaken, mit „+ Termin“ und „+ Aufgabe“ legst du etwas für genau diesen Tag an.",
            ),
            GuideItem(
                "Karten einklappen",
                "Auf der Tagesansicht lässt sich jede Karte mit „Einklappen ▴“ unter der Karte verkleinern. Dann bleibt nur Titel und eine Kurzinfo stehen, z. B. „2 von 3 abgehakt, 1 offen“ oder „3 offen, davon 1 dringend“. Ein Tipp auf die kleine Karte klappt sie wieder auf. Die App merkt sich, was eingeklappt ist.",
            ),
            GuideItem(
                "Wichtig und demnächst",
                "Auf der Tagesansicht steht oben die Karte „Wichtig und demnächst“. Zuerst kommt, was dringend (hohe Priorität), überfällig oder heute fällig ist, rot hervorgehoben und nach Priorität sortiert. Darunter: die Termine von heute (zum Abhaken), die nächsten 3 Tage mit Terminen und Aufgaben und weitere Termine bis 7 Tage. Dringende Aufgaben legst du mit Priorität „hoch“ an.",
            ),
            GuideItem(
                "Einnahmen abhaken",
                "Unter „Heute einnehmen“ steht jede Einnahme mit Uhrzeit. Weil bei Medikamenten die Zeit zählt, lässt sich nur rund um die Einnahmezeit normal abhaken (Standard: 1 Stunde davor und danach, einstellbar unter Einstellungen → Einnahmen abhaken). Vorher steht „abhakbar ab …“. Ist das Fenster vorbei, steht die Zeile in Rot und du tippst darauf, um nachzutragen, wie es lief.",
            ),
            GuideItem(
                "Kurz nachgefragt",
                "Wurde eine Einnahme (von gestern oder heute) oder ein Termin nicht beantwortet, erscheint oben die Karte „Kurz nachgefragt“. Mit „Antworten“ wählst du: zur Zeit genommen (nur nicht abgehakt), später genommen (optional mit Uhrzeit), vergessen oder bewusst nicht gegeben (z. B. abgelehnt oder auf Anweisung). Dazu kannst du eine Bemerkung schreiben. Wer schon früher genommen hat, tippt vorher auf die Zeile und wählt „Schon früher genommen“. Eine Zeile mit Strich im Kästchen heißt: nicht genommen, aber beantwortet. Mit „Zurücksetzen“ nimmst du eine Antwort zurück. Alles fließt in die Auswertung ein: pünktlich, später (mit durchschnittlicher Verspätung), vergessen, bewusst nicht gegeben. Bleibt eine Einnahme nach dem Zeitfenster offen, erinnert dich die App einmal per Benachrichtigung („Kurz nachgefragt“). Das lässt sich in den Einstellungen unter „Einnahmen abhaken“ ausschalten.",
            ),
            GuideItem(
                "Termin abgesagt oder verschoben",
                "Tippst du bei einem Termin auf das Kästchen, fragt die App: hat stattgefunden, abgesagt oder ausgefallen, verschoben. Bei „abgesagt“ und „verschoben“ legt sie auf Wunsch gleich die Aufgabe „Neuen Termin ausmachen“ für morgen an, mit dem Arzt zum Anrufen. So geht der eigentliche Grund nicht verloren.",
            ),
            GuideItem(
                "Bemerkung zu einer Einnahme oder einem Termin",
                "Mit „Notiz“ schreibst du dazu, was aufgefallen ist, z. B. „später genommen“ oder „Termin verschoben“. Die Schnellauswahl fügt häufige Sätze mit einem Tipp ein.",
            ),
            GuideItem(
                "Bemerkung zum Tag",
                "Eine freie Notiz für den ganzen Tag: wie der Tag lief, was auffiel. Sie fließt in die Auswertung ein.",
            ),
            GuideItem(
                "Aufgaben",
                "Hier stehen Dinge, die erledigt werden sollen, z. B. „Blutuntersuchung beim Hausarzt“. Mit „+ Aufgabe hinzufügen“ legst du eine an: Text, Wichtigkeit (niedrig, normal, hoch), „Erledigt bis“ und optional den zuständigen Arzt bzw. die Stelle aus deinen Kontakten. Die Liste ist nach Wichtigkeit und Frist sortiert. Dringendes und Überfälliges steht in Rot. Mit dem Haken erledigst du eine Aufgabe. Die App fragt dann, was dabei herausgekommen ist (z. B. Firma, Telefonnummer, Erfahrung) und nach einer Bewertung mit Sternen, beides freiwillig. Erledigte sind eingeklappt („Erledigte anzeigen“). Hast du einen Kontakt gewählt, kannst du direkt „Anrufen“ oder „E-Mail“ tippen. Ist „Außer Haus“ angehakt, gibt es zusätzlich „Fahrdienst“ (siehe Termine). „Bearbeiten“ ändert alles, „Löschen“ entfernt eine versehentlich angelegte Aufgabe ganz.",
            ),
            GuideItem(
                "Aufgaben-Verlauf und frühere Themen",
                "Erledigte Aufgaben gehen nicht verloren. „In den Verlauf“ nimmt sie nur aus der Liste. Unter „Verlauf und Suche“ findest du alle Aufgaben mit Datum, Ergebnis und Sternen, auch nach Jahren, z. B. mit dem Suchwort „Rollstuhl“. Erledigte Aufgaben stehen außerdem in der Auswertung und in der Wochen- und Monatsansicht am Tag der Erledigung. Legst du eine neue Aufgabe an, prüft die App automatisch, ob es früher schon etwas zum gleichen Thema gab, und zeigt es mit Ergebnis an („Dazu gab es schon etwas“). Das passiert nur auf deinem Handy.",
            ),
            GuideItem(
                "Unterlagen zur Aufgabe",
                "Bei jeder Aufgabe gibt es „Unterlagen“, sobald Dokumente gespeichert sind. Dort hakst du an, welche Dokumente (Befund, Rezept, Bericht, Bescheid) zur Aufgabe gehören. Die Zahl in Klammern zeigt, wie viele verknüpft sind, mit „Ansehen“ öffnest du sie direkt. So findest du den Bericht, aus dem die Aufgabe stammt, immer wieder. Unter „Passt vermutlich zur Aufgabe“ schlägt die App Dokumente vor, mit Grund (z. B. „gleicher Arzt“, „Stichwort im Titel: niere“, „aktuell“). Die Vorschläge werden nur auf deinem Handy aus Titel, Kategorie, Arzt, Stichwörtern im Text und Datum berechnet. Zu alte oder erst später entstandene Dokumente werden aussortiert, bei Medikamentenplan und Pflegebescheid zählt nur der neueste.",
            ),
            GuideItem(
                "Bald nachbestellen",
                "Die rote Karte oben erscheint, wenn der Vorrat eines Medikaments nur noch für etwa 7 Tage reicht.",
            ),
            GuideItem(
                "Termine abhaken",
                "Die Termine von heute und der nächsten 7 Tage stehen in „Wichtig und demnächst“. Ein Tipp aufs Kästchen fragt, wie es lief. Mit „Notiz“ schreibst du etwas dazu.",
            ),
        ),
    ),
    GuideSection(
        id = "medikamente",
        title = "Medikamente",
        summary = "Anlegen, Foto, PZN, Beschreibung, Vorrat",
        items = listOf(
            GuideItem(
                "Überblick und Gesamt-Check",
                "Im Reiter „Medikamente“ oben „Überblick & Check“ antippen. Dort siehst du alle Medikamente mit ihrer Einschätzung (Wirkstärke, Nebenwirkungen, Wechselwirkungen) und das Gesamtbild: Anzahl, Einnahmen pro Tag und Hinweise zu deinen Ansprüchen (Medikationsplan vom Hausarzt ab 3 dauerhaft verordneten Medikamenten, kostenlose erweiterte Medikationsberatung in der Apotheke ab 5). Mit „Gesamt-Check per KI“ (freiwillig, KI muss eingerichtet sein) schätzt die KI alle Medikamente zusammen ein: welche Paare sich beeinflussen können (Gering, Beachten, Wichtig), Hinweise zu Einnahmezeiten, ein Gesamtbild und Fragen für Arzt oder Apotheke. Vorher siehst du genau, was gesendet wird: nur Medikamente mit Dosierung und Zeiten, ohne Namen der Person, das Alter nur auf Wunsch. Fragen lassen sich als Aufgabe merken, der Überblick lässt sich teilen, z. B. für den Arztbesuch. Ändert sich der Plan, weist die App darauf hin. Alles nur Anhaltspunkte: nie eigenmächtig absetzen oder ändern, sondern prüfen lassen.",
            ),
            GuideItem(
                "Medikament anlegen",
                "Im Bereich „Medikamente“ auf das Plus tippen. Pflicht ist nur der Name. Der Papierkorb auf der Karte löscht das Medikament (mit Rückfrage).",
            ),
            GuideItem(
                "Infos auf einen Blick",
                "Eine Medikamentenkarte antippen (oder „Infos“) zeigt alles in einer Übersicht: Einnahme, Vorrat, PZN, Arzt, Apotheke, Notizen, Beschreibung und KI-Hinweis. Von dort kommst du mit „Bearbeiten“ zum Ändern. Eine gekürzte Beschreibung steht schon auf der Karte.",
            ),
            GuideItem(
                "Per Foto der Packung",
                "„Packung fotografieren“ oder „Galerie“ wählen. Die App liest den Text direkt auf dem Handy aus (nichts wird übertragen) und schlägt Name, Packungsgröße und Stärke vor. Namensvorschläge lassen sich antippen. Bitte immer prüfen und bei Bedarf korrigieren.",
            ),
            GuideItem(
                "Einnahmezeiten",
                "Morgens, Mittags, Abends, Nachts antippen oder eine eigene Uhrzeit hinzufügen. Zu jeder Zeit gibt es eine Erinnerung und einen Eintrag auf der Heute-Seite.",
            ),
            GuideItem(
                "Vorrat und „Pro Tag“",
                "Vorrat = Stückzahl, die du jetzt hast. „Pro Tag“ = Verbrauch pro Tag (leer lassen = Anzahl der Einnahmen). Der Vorrat sinkt danach automatisch jeden Tag. Neue Packung gekauft? Medikament öffnen und den neuen Vorrat eintragen, dann beginnt die Zählung ab heute neu.",
            ),
            GuideItem(
                "Dosierung und Notizen",
                "Die Dosierung (z. B. „1 Tablette“) wählst du aus oder tippst sie selbst. In die Notizen gehört alles, was wichtig ist, z. B. „zum Essen“.",
            ),
            GuideItem(
                "PZN und Barcode",
                "Beim Foto sucht die App auf dem Handy auch nach dem Barcode bzw. der aufgedruckten PZN (Pharmazentralnummer) und trägt sie ein, wenn sie eine gültige Prüfziffer hat. Du kannst sie auch von Hand eintippen. Sie steht dann in der Nachbestell-Liste, was die Apotheke freut. „PZN im Internet nachschlagen“ öffnet eine Suche in deinem Browser.",
            ),
            GuideItem(
                "Beschreibung aus dem Internet",
                "Im Medikament ganz unten „Beschreibung aus dem Internet laden“ antippen. Die App holt einen kurzen Text aus Wikipedia und zeigt die Quelle. Es wird nur der Name des Medikaments gesendet. In den Einstellungen kannst du umschalten: nur auf Knopfdruck (Standard) oder automatisch laden. Die Beschreibung ist nur ein Anhaltspunkt, keine ärztliche Empfehlung, und ersetzt weder Arzt, Apotheke noch Packungsbeilage.",
            ),
            GuideItem(
                "Arzt und Apotheke zuordnen",
                "Beim Medikament kannst du den zuständigen Arzt (für das Rezept) und die Apotheke aus deinen Kontakten wählen. Beides ist freiwillig. Die Zuordnung nutzt die Nachbestellung.",
            ),
        ),
    ),
    GuideSection(
        id = "nachbestellung",
        title = "Nachbestellung",
        summary = "Was fehlt, anrufen, mailen, PDF drucken",
        items = listOf(
            GuideItem(
                "Öffnen",
                "Im Bereich „Medikamente“ oben auf „Nachbestellen“ tippen (die Zahl zeigt, wie viele fällig sind), oder auf der Heute-Seite in der roten Karte „Bald nachbestellen“ auf „Nachbestellung öffnen“. Auf der Medikamentenkarte steht bei knappem Vorrat „Bald nachbestellen“.",
            ),
            GuideItem(
                "Was wird bestellt?",
                "Vorausgewählt ist alles, was nur noch für etwa 7 Tage reicht. Mit den Haken wählst du Medikamente zusätzlich oder ab.",
            ),
            GuideItem(
                "Arzt oder Apotheke",
                "Oben wählst du, ob du nach Arzt (Rezept) oder nach Apotheke gruppieren willst. Zu jedem Kontakt siehst du, was dort fehlt. „Anrufen“ öffnet die Telefon-App, „E-Mail mit Liste“ dein Mailprogramm mit fertigem Text.",
            ),
            GuideItem(
                "Nichts passiert automatisch",
                "Anrufe und Mails starten erst nach deinem Tipp. Du siehst alles in der Telefon- bzw. Mail-App und sendest selbst ab. Der Name der Person lässt sich in Nachricht und PDF ausschalten.",
            ),
            GuideItem(
                "Liste als PDF",
                "„PDF der Auswahl erstellen“ legt die Liste im privaten App-Ordner ab. Von dort kannst du sie öffnen, unter „Speichern unter“ auf dein Handy herunterladen, drucken oder teilen. Gespeicherte Listen kannst du dort auch löschen.",
            ),
        ),
    ),
    GuideSection(
        id = "infos",
        title = "Infos",
        summary = "Aktuelles zu Pflege, Gesetzen und deinen Themen",
        items = listOf(
            GuideItem(
                "Was ist das?",
                "Der Reiter „Infos“ sammelt aktuelle Themen rund um Pflege: Pflegereform und Gesetze, Beratung und Hilfe, Pflege und Beruf, Entlastung für Angehörige. Die Themenkarten tragen ein Datum („Stand“) und sind ohne Gewähr. Gesetze ändern sich, maßgeblich sind Gesetzestext, Pflegekasse und Beratungsstellen.",
            ),
            GuideItem(
                "Meldungen suchen",
                "Gib ein Thema ein (z. B. Pflegegeld, Demenz) und tippe auf „Suchen“. Die App zeigt aktuelle Überschriften mit Quelle und Datum. Ein Tipp öffnet die Seite der Quelle im Browser. Dabei wird nur dein Suchwort an Google News gesendet, nie ein Name oder Daten aus der App. „Aktuelle Meldungen“ auf einer Themenkarte sucht direkt zu diesem Thema.",
            ),
            GuideItem(
                "Eigene Themen",
                "Nach einer Suche kannst du „Thema merken“ antippen. Gemerkte Themen stehen unter „Meine Themen“ und lassen sich mit einem Tipp erneut suchen. Zum Entfernen das Thema suchen und „Thema entfernen“ antippen.",
            ),
            GuideItem(
                "Favoriten",
                "Bei jeder Themenkarte und jeder gefundenen Meldung gibt es „☆ Merken“. Oben im Reiter „Infos“ schaltest du zwischen „Alle Infos“ und „★ Favoriten“ um. Dort stehen gemerkte Karten, gemerkte Meldungen (Link zur Quelle) und deine Themen. Gespeichert wird nur auf deinem Handy.",
            ),
            GuideItem(
                "Themen für dich",
                "Aus Pflegegrad, Krankenkasse, Medikamenten, Dokumenten und „Wichtig zu wissen“ schlägt die App passende Themen vor, z. B. „Pflegegrad 3“ oder ein Thema zu einem erkannten Medikament. Das wird nur auf deinem Handy ausgewertet, ist keine Diagnose und lässt sich mit dem Schalter ausschalten. Erst beim Antippen eines Vorschlags wird das Suchwort gesendet.",
            ),
        ),
    ),
    GuideSection(
        id = "wegweiser",
        title = "Wegweiser Leistungen",
        summary = "Welche Hilfen und Anträge je Pflegegrad",
        items = listOf(
            GuideItem(
                "Öffnen",
                "Auf der Heute-Seite in der Profilkarte „Wegweiser Leistungen“ antippen (ist die Karte eingeklappt, erst antippen zum Aufklappen). Auch im Reiter „Infos“ gibt es einen Weg dorthin.",
            ),
            GuideItem(
                "Was steht drin?",
                "Erste Schritte (Pflegegrad beantragen, Begutachtung, Widerspruch), Leistungen für zu Hause, Pflegeheim, Hilfen für pflegende Angehörige und weitere Hilfen. Die Beträge für den eingetragenen Pflegegrad sind hervorgehoben. Mit den Pflegegrad-Schaltflächen siehst du andere Stufen oder alle auf einmal.",
            ),
            GuideItem(
                "Links zu Stellen und Vordrucken",
                "Bei vielen Einträgen gibt es Links: Mit Lupe öffnet sich eine Suche in deinem Browser (z. B. „Antrag Pflegegrad“ deiner Krankenkasse) oder deine Karten-App (z. B. Pflegestützpunkt, Pflegedienst, Tagespflege, Versorgungsamt in deiner Nähe). Mit Pfeil öffnet sich eine feste Seite, z. B. der Pflegelotse oder das Bundesamt für Familie. Eingesetzt werden nur Krankenkasse und PLZ/Ort aus dem Profil, nie Name, Straße oder Versichertennummer. Je genauer das Profil, desto besser die Treffer.",
            ),
            GuideItem(
                "Als Aufgabe merken",
                "Bei vielen Punkten gibt es „+ Als Aufgabe merken“, z. B. „Pflegegrad beantragen“ oder „Verhinderungspflege planen“. So behältst du den Überblick, was noch zu tun ist.",
            ),
            GuideItem(
                "Ohne Gewähr",
                "Die Beträge gelten für die gesetzliche Pflegeversicherung in Deutschland (Stand der Angaben steht unten im Wegweiser). Gesetze ändern sich. Verbindlich sind nur Auskünfte der Pflegekasse oder eines Pflegestützpunkts. Es ist keine Rechtsberatung.",
            ),
        ),
    ),
    GuideSection(
        id = "termine",
        title = "Termine",
        summary = "Liste, Serien, Unterlagen, Fahrdienst, Erinnerung, Kalender",
        items = listOf(
            GuideItem(
                "Termin anlegen",
                "Im Bereich „Termine“ auf das Plus tippen. Den Titel kannst du aus der Liste wählen oder selbst eintippen, z. B. für etwas, das nicht in der Liste steht.",
            ),
            GuideItem(
                "Wiederkehrende Termine",
                "Bei „Wiederholen“ wählst du: jede Woche, alle 2, 3 oder 4 Wochen oder jeden Monat. Optional „Wiederholen bis“. Ohne Ende legt die App die Termine immer etwa ein halbes Jahr im Voraus an. Jeder Termin der Serie hat eine eigene Erinnerung, lässt sich einzeln abhaken und bekommt eigene Unterlagen. In der Liste „Termine“ steht nur der nächste Termin einer Serie, alle stehen in Woche und Monat. Beim Ändern oder Löschen fragt die App: „Nur diesen Termin“ oder „Diesen und alle folgenden“.",
            ),
            GuideItem(
                "Von – bis",
                "Datum über den Kalender wählen, dann „Von“ und optional „Bis“. Lässt du das Ende leer, ist der Termin ohne Endzeit.",
            ),
            GuideItem(
                "Kontakt verknüpfen",
                "Wähle den Arzt oder Dienst aus deinen Kontakten. Der Ort wird dann mit dessen Adresse vorbelegt, wenn er noch leer ist.",
            ),
            GuideItem(
                "Unterlagen anhängen",
                "Auf der Terminkarte „Unterlagen“ antippen. Die App schlägt passende Dokumente vor (gleicher Arzt, passende Kategorie, z. B. Medikamentenplan oder Befund). Du hakst an, was zum Termin gehört, und kannst jedes Dokument mit „Ansehen“ öffnen. Angehängte Unterlagen stehen auf der Karte. Darunter findest du Anregungen, was man mitnehmen könnte (z. B. Versichertenkarte, Medikamentenplan). Jeden Punkt kannst du mit „+ Aufgabe“ als Aufgabe merken. Das sind allgemeine Anregungen, was gebraucht wird, sagt die Praxis.",
            ),
            GuideItem(
                "Die Liste der Termine",
                "Oben stehen die kommenden Termine ab heute, der nächste zuerst. Von einer Serie steht nur der nächste Termin da, mit dem Hinweis, wie viele noch folgen (alle siehst du in Woche und Monat). Vergangene Termine sind eingeklappt: „Vergangene Termine anzeigen“ blendet sie ein, die neuesten zuerst. Bei vergangenen Terminen bleiben nur Anrufen, Nachricht und Unterlagen.",
            ),
            GuideItem(
                "Die Terminkarte",
                "Links steht das Datum groß mit Wochentag, daneben Titel, Uhrzeit und wie lange es noch dauert („heute“, „morgen“, „in 3 Tagen“). Termine von heute und morgen sind farblich hervorgehoben, vergangene grau mit „vorbei“. Dazu kommen Arzt bzw. Kontakt, Ort oder Adresse, Notiz, angehängte Unterlagen und die eingestellte Erinnerung. Unten: „Anrufen“ (ruft den verknüpften Kontakt an), „Route“ (öffnet die Karte zum Ziel), „Fahrdienst“, „Unterlagen“ und „In Kalender eintragen“.",
            ),
            GuideItem(
                "Fahrdienst bestellen",
                "Bei Terminen außer Haus erscheint „Fahrdienst“. Oben stehen deine festen Fahrdienste, also alle Kontakte mit der Art „Fahrdienst“ (oder Taxi, Krankentransport im Namen), immer zuerst, mit Anrufen, SMS und E-Mail. Die Anfrage (Abholadresse aus dem Profil mit Stockwerk und Aufzug, Ziel mit Stockwerk und Aufzug, Termin, Hinfahrt und/oder Rückfahrt zum Ankreuzen, Rollator/Rollstuhl/Begleitperson) wird vorbereitet und du siehst sie vor dem Senden in deiner SMS- oder E-Mail-App. Der Name der Person steht nie drin. „In der Nähe suchen“ fragt nach Bestätigung bei OpenStreetMap nach Krankenfahrten, Taxis und Fahrdiensten rund um PLZ und Ort der Person (nur Ort wird gesendet) und zeigt Entfernung, Telefon, Zeiten und Rollstuhl-Angaben, soweit eingetragen. Die Daten sind unvollständig und ohne Gewähr, bitte immer telefonisch bestätigen. „Karte öffnen“ sucht zusätzlich in deiner Karten-App.",
            ),
            GuideItem(
                "Wer zahlt die Fahrt?",
                "Unter „Infos zur Krankenfahrt“ im Fahrdienst-Fenster steht, wann die Krankenkasse zahlt: nur bei ärztlicher Verordnung und medizinischer Notwendigkeit. Bei Merkzeichen aG, Bl oder H, Pflegegrad 4 oder 5 und Pflegegrad 3 mit dauerhaft eingeschränkter Mobilität gilt eine verordnete Taxifahrt zum ambulanten Termin als genehmigt. Eigenanteil 10 %, mindestens 5 €, höchstens 10 € pro Fahrt. Das ist eine Orientierung, verbindlich sagt es die Krankenkasse.",
            ),
            GuideItem(
                "Erinnerung",
                "Pro Termin einstellbar: keine, 15 oder 30 Minuten, 1 oder 2 Stunden, 1 Tag vorher.",
            ),
            GuideItem(
                "In den Kalender übernehmen",
                "Auf der Terminkarte „In Kalender eintragen“ antippen. Dein Kalender öffnet sich mit den Angaben und du bestätigst dort das Speichern.",
            ),
        ),
    ),
    GuideSection(
        id = "dokumente",
        title = "Dokumente",
        summary = "Berichte, Befunde, Bescheide fotografieren oder laden",
        items = listOf(
            GuideItem(
                "Ansehen und Drucken",
                "Tippe ein Dokument an und dann auf „Öffnen“. Es erscheint im eingebauten Betrachter: PDF und Fotos mit Zoom (− und +), Text zum Lesen. Unten stehen „Drucken“, „Teilen“ und „Andere App“. Drucken geht für PDF und Fotos über den Druckdienst des Handys (auch als PDF speichern). In den Einstellungen unter „Dokumente ansehen“ wählst du, ob „Öffnen“ den eingebauten Betrachter oder eine andere App nutzt. Word und andere Formate öffnen immer mit einer anderen App. Das Dokument bleibt verschlüsselt, nur für die Anzeige entsteht kurz eine Kopie im Zwischenspeicher.",
            ),
            GuideItem(
                "Mehrere Seiten, ein Dokument",
                "Besteht ein Dokument aus mehreren Blättern, bleiben alle zusammen. Wähle mehrere Fotos oder PDFs auf einmal aus (sie werden in der gewählten Reihenfolge zu einem Dokument), oder tippe nach dem Einlesen auf „Weitere Seiten hinzufügen“ (fotografieren, Galerie oder PDF/Bild laden). Der Text aller Seiten wird gelesen und zusammengefügt, gespeichert wird eine PDF. „Letzte Seite entfernen“ nimmt eine versehentliche Seite wieder weg. Word- und Textdateien werden nicht zusammengefügt, die lädst du einzeln.",
            ),
            GuideItem(
                "Zusammenfassung",
                "Im Formular gibt es „Zusammenfassung“. Du kannst sie selbst schreiben oder per KI erstellen lassen („Per KI zusammenfassen“). Vor dem Senden siehst du den genauen Text. Name, Geburtsdatum, Versichertennummer und Straße der Person werden durch Platzhalter ersetzt, andere Namen im Dokument können aber enthalten sein. Brich ab, wenn etwas nicht raus soll. Die Zusammenfassung ist ein Anhaltspunkt in einfacher Sprache und kann Fehler enthalten. Sie steht danach auf der Karte, im Detail und wird von der Suche gefunden.",
            ),
            GuideItem(
                "Aufgaben vorschlagen",
                "Unter der Zusammenfassung gibt es „Aufgaben vorschlagen“. Die KI liest die Zusammenfassung (oder den Text) und schlägt vor, was zu tun ist, z. B. „Blutuntersuchung der Nierenwerte beim Hausarzt“, mit Wichtigkeit, Frist („Anfang nächster Woche“ wird in ein Datum umgerechnet) und ob man dafür aus dem Haus muss. Vor dem Senden siehst du wieder den genauen Text. Dann hakst du an, was übernommen werden soll. Der passende Kontakt (z. B. dein Hausarzt) wird nur auf deinem Handy zugeordnet, die Namen deiner Kontakte werden nie gesendet. Du kannst den Kontakt pro Vorschlag ändern. Übernommene Aufgaben stehen auf der Heute-Seite, abhakbar, mit Direktkontakt und Fahrdienst. Auch das ist nur ein Anhaltspunkt: bitte prüfen, ob es zum Dokument passt.",
            ),
            GuideItem(
                "Dokument hinzufügen",
                "Im Bereich „Dokumente“ auf das Plus tippen und auswählen: „Dokument fotografieren“ (bei mehreren Seiten mit „Weitere Seite fotografieren“), „Foto(s) aus der Galerie“ oder „Datei laden“. Bei „Datei laden“ suchst du eine Datei aus einem Ordner auf dem Handy, z. B. eine PDF vom Arzt, ein Bild oder eine Textdatei.",
            ),
            GuideItem(
                "Text wird gelesen und genutzt",
                "Die App liest den Text direkt auf dem Handy (bei PDFs bis zu 12 Seiten). Daraus schlägt sie Titel, Kategorie (z. B. Arztbericht, Befund, Rezept, Pflegegrad-Bescheid), Datum und den passenden Kontakt vor. Alles lässt sich ändern. Der erkannte Text ist durchsuchbar und korrigierbar.",
            ),
            GuideItem(
                "Suchen",
                "Oben in der Dokumentenliste suchst du nach Titel, Kategorie, Arzt, Datum oder Wörtern aus dem Text, z. B. dem Namen eines Medikaments. Du kannst mehrere Wörter eingeben („Befund 2025“), alle müssen vorkommen. Die besten Treffer stehen oben, bei Treffern im Text siehst du den passenden Ausschnitt.",
            ),
            GuideItem(
                "Filtern und sortieren",
                "Die Leiste unter dem Suchfeld zeigt alle Kategorien mit Anzahl. Ein Tipp filtert, nochmal tippen hebt den Filter auf. Unter „Sortieren und filtern“ gibt es Sortierung (neueste, älteste, A bis Z, nach Kategorie) sowie Filter nach Jahr und nach Arzt oder Stelle. „Zurücksetzen“ löscht alle Filter.",
            ),
            GuideItem(
                "Kategorien und Vorschläge",
                "Die App erkennt die Kategorie aus dem Text, gewichtet nach eindeutigen Wörtern: z. B. „Entlassung“ oder „stationär“ für „Krankenhaus / Entlassbericht“, „Medikationsplan“ für den Medikamentenplan. Stimmt die Kategorie bei einem älteren Dokument nicht, öffne es mit „Bearbeiten“, dort steht oft ein besserer „Vorschlag“ zum Antippen. Passt keine Standard-Kategorie, schlägt die App unter „Kategorie“ einen Namen vor (z. B. „Wohnung / Miete“ oder „Schwerbehinderung“). Antippen übernimmt ihn. Du kannst auch eine vorhandene Kategorie wählen oder selbst einen Namen eintippen. Eigene Kategorien stehen danach in der Auswahl und im Filter.",
            ),
            GuideItem(
                "Text nicht erkannt?",
                "Word-Dateien (docx) und LibreOffice-Dateien (odt) werden gelesen. Wurde bei einem Dokument kein Text erkannt, öffne es mit „Bearbeiten“ und tippe auf „Text neu auslesen“. Den Text kannst du dort auch selbst eintragen oder korrigieren, dann findet ihn die Suche.",
            ),
            GuideItem(
                "Öffnen und teilen",
                "Eine Karte antippen zeigt die Details mit „Öffnen“, „Drucken“ und „Teilen“ ganz oben. „Öffnen“ zeigt das Dokument im eingebauten Betrachter oder in einer anderen App (einstellbar). „Teilen“ gibt es weiter, z. B. per E-Mail. Das passiert nur nach deinem Tipp.",
            ),
            GuideItem(
                "Sicher abgelegt",
                "Die Dateien werden verschlüsselt im privaten App-Ordner gespeichert und nirgends hin gesendet. Beim Öffnen entsteht kurz eine Kopie, die beim nächsten App-Start gelöscht wird. Nicht lesbar sind passwortgeschützte PDFs und Dateitypen wie Word. Sie werden trotzdem gespeichert, nur ohne Text.",
            ),
            GuideItem(
                "Schreiben und Vollmachten erstellen",
                "Im Tab „Dokumente“ und bei den Kontakten gibt es „Schreiben / Vollmacht erstellen“. Du wählst eine Vorlage: Vollmacht für Anträge und Auskünfte, Entbindung von der Schweigepflicht, Antrag auf Pflegegrad, Höherstufung, Widerspruch gegen einen Bescheid, Verhinderungspflege, Pflegehilfsmittel, Kopie der Behandlungsunterlagen, Folgerezept oder ein freies Schreiben. Name, Anschrift, Versichertennummer und Medikamente setzt die App aus deinen Angaben ein, den Empfänger wählst du aus den Kontakten. Du kannst festlegen, ob die Person selbst schreibt oder du mit Vollmacht. Den Text prüfst und änderst du, Stellen mit „(bitte ergänzen)“ füllst du aus. Dann „PDF erstellen“: mit Vorschau, „Drucken“, „Speichern unter …“, „Teilen“, „In Dokumente ablegen“ und „Als Aufgabe merken“ (ausdrucken, unterschreiben, abschicken, einscannen). Die unterschriebene Fassung scannst du später wie jedes Dokument ein. Die Texte sind Entwürfe und keine Rechtsberatung. Vorsorgevollmacht und Patientenverfügung gibt es nicht als Vorlage, weil dafür besondere Formvorschriften gelten.",
            ),
        ),
    ),
    GuideSection(
        id = "kontakte",
        title = "Kontakte",
        summary = "Ärzte, Pflegedienst, Apotheke, Kassen und mehr",
        items = listOf(
            GuideItem(
                "Alles an einem Ort",
                "Hier kommen alle Ansprechpartner hinein: Ärzte, Pflegedienst, Tagespflege, Hausnotruf, Apotheke, Sanitätshaus, Pflegekasse, Sozialamt und viele mehr. Die Art wählst du aus oder tippst sie selbst.",
            ),
            GuideItem(
                "Direkt anrufen oder schreiben",
                "Auf der Karte öffnet „Anrufen“ die Telefon-App mit der Nummer. „E-Mail“ öffnet einen vorbereiteten Entwurf zu einem Thema, den du prüfst und in deiner E-Mail-App abschickst (siehe „Meine Daten und E-Mails“). Es wird nichts automatisch gesendet.",
            ),
            GuideItem(
                "Filtern",
                "Sobald es mehrere Arten gibt, erscheinen oben Filter, z. B. nur „Pflegedienst“.",
            ),
            GuideItem(
                "In der Nähe suchen",
                "Oben auf der Kontakte-Seite findest du „In der Nähe suchen“. Wähle Apotheke, Pflegedienst, Sanitätshaus oder Notaufnahme / Krankenhaus. Nach deiner Bestätigung wird nur „PLZ Ort“ der Person an OpenStreetMap gesendet (kein Name, keine Straße). Du siehst Entfernung, Adresse, Telefon, Öffnungszeiten (soweit eingetragen) und kannst anrufen, die Route öffnen, die Webseite ansehen oder den Treffer mit „Als Kontakt speichern“ übernehmen. Die Daten sind unvollständig und ohne Gewähr, bitte kurz anrufen und Öffnungszeiten prüfen. „In Karten-App“ sucht zusätzlich in deiner Karten-App.",
            ),
            GuideItem(
                "Stockwerk und Aufzug",
                "Bei jedem Kontakt kannst du Stockwerk, Aufzug (ja / nein) und Hinweise zum Zugang eintragen, z. B. bei Arztpraxen im 2. Stock ohne Aufzug. Diese Angaben stehen dann bei jeder Fahrdienst-Anfrage zum Ziel.",
            ),
            GuideItem(
                "Für alle Personen gleich",
                "Kontakte gelten für alle betreuten Personen. Du legst sie nur einmal an.",
            ),
        ),
    ),
    GuideSection(
        id = "ki",
        title = "KI-Assistent",
        summary = "Optionale Hinweise, Dienst frei wählbar",
        items = listOf(
            GuideItem(
                "Wofür?",
                "Die KI gibt Anhaltspunkte, z. B. „Medikamentenplan vom Hausarzt prüfen lassen“ oder Fragen für den Apotheker. Sie ist kein Arzt und kein Apotheker und ersetzt beide nie. Sie stellt keine Diagnosen und empfiehlt keine Dosierungen.",
            ),
            GuideItem(
                "Einrichten",
                "Einstellungen → KI-Assistent. Dienst wählen: kostenlos nutzbar sind Google Gemini, Groq, Mistral und OpenRouter (Modelle mit „:free“). Kostenpflichtig sind OpenAI, Anthropic (Claude) und DeepSeek. Dann den eigenen API-Schlüssel eintragen („Schlüssel besorgen“ öffnet die Seite des Anbieters) und „Testen“ antippen. Ein Abo bei ChatGPT oder Claude reicht nicht, es braucht den API-Schlüssel.",
            ),
            GuideItem(
                "Einschätzung zu einem Medikament",
                "Im Medikament („Infos“) kannst du per KI eine grobe Einschätzung holen: Skalen von 1 bis 5 für Wirkstärke, Nebenwirkungen und mögliche Wechselwirkungen mit den übrigen Medikamenten der Person, dazu ein paar Stichpunkte zum Nachfragen. Das ist nur eine Orientierung, kein Urteil. Dosis, Alter und Vorerkrankungen sind nicht berücksichtigt. Gesendet werden die Medikamentennamen (ohne Namen der Person) erst nach deiner Bestätigung. Das Ergebnis bleibt auf dem Handy gespeichert. Ändert sich der Plan, lass es neu einschätzen.",
            ),
            GuideItem(
                "Modell auswählen",
                "Nach dem Eintragen des Schlüssels kannst du „Verfügbare Modelle laden“ antippen. Dann zeigt das Feld „Modell“ eine Auswahlliste dieses Dienstes. Der Modellname ist sonst vorausgefüllt und lässt sich auch selbst eintippen.",
            ),
            GuideItem(
                "Dienst schnell wechseln",
                "Hast du für mehrere Dienste einen Schlüssel eingetragen (Haken ✓ in den Einstellungen), erscheint bei jedem KI-Hinweis eine Schnellwahl. Dort tippst du den Dienst an, der verwendet werden soll. Der gewählte Dienst bleibt gespeichert. Ein automatischer Wechsel passiert nie.",
            ),
            GuideItem(
                "Limit erreicht?",
                "Ist das Kontingent eines Dienstes aufgebraucht oder der Dienst überlastet, zeigt die App einen Hinweis. Wähle dann in der Schnellwahl einen anderen eingerichteten Dienst und sende erneut. Ist keiner eingerichtet, richte unter Einstellungen einen weiteren ein (z. B. Groq oder Mistral, kostenlos).",
            ),
            GuideItem(
                "Wo erscheint sie?",
                "In der Auswertung („KI-Hinweise holen“) und im Medikament („KI-Hinweis zu diesem Medikament“). Bei jeder Antwort kannst du einen Punkt mit „Auf meine To-do-Liste“ übernehmen und später beim Arzt ansprechen oder eben nicht. Außerdem bei Dokumenten (Zusammenfassung, Aufgaben vorschlagen), bei „Medikamente im Überblick“ (Gesamt-Check aller Medikamente) und in der Auswertung.",
            ),
            GuideItem(
                "Was wird gesendet?",
                "Nichts passiert von allein. Vor dem Senden zeigt die App den genauen Text und du bestätigst. Der Name der Person ist nie dabei. Deine Bemerkungen werden nur mitgesendet, wenn du es einschaltest. Beim Medikament geht nur der Medikamentenname raus. Beim Gesamt-Check gehen alle Medikamente mit Dosierung und Einnahmezeiten, das Alter nur, wenn du es anhakst.",
            ),
            GuideItem(
                "Schlüssel und Kosten",
                "Der Schlüssel wird verschlüsselt nur auf diesem Handy gespeichert. Bei kostenlosen Diensten gibt es Mengengrenzen, bei kostenpflichtigen zahlst du nach Verbrauch direkt beim Anbieter (für diese kurzen Anfragen meist wenige Cent).",
            ),
        ),
    ),
    GuideSection(
        id = "erinnerungen",
        title = "Erinnerungen",
        summary = "Benachrichtigungen und der Knopf „Eingenommen“",
        items = listOf(
            GuideItem(
                "Freigaben in Android",
                "Unter Einstellungen → „Freigaben (Android)“ siehst du, was erlaubt ist (✓) und was fehlt: Benachrichtigungen, Erinnerungen mit Ton, pünktliche Wecker, Akku-Optimierung aus (empfohlen) und Updates installieren. Die Knöpfe öffnen direkt die passende Stelle in den Android-Einstellungen. Fehlt etwas Wichtiges für Erinnerungen, erscheint oben auf der Heute-Seite eine rote Karte mit den Knöpfen zum Einschalten.",
            ),
            GuideItem(
                "Medikamente",
                "Zu jeder Einnahmezeit erscheint eine Benachrichtigung. Mit „Eingenommen“ direkt in der Benachrichtigung wird die Einnahme abgehakt, ohne die App zu öffnen. Ist die Einnahme schon abgehakt (z. B. etwas früher), bleibt die Erinnerung aus. Bleibt sie nach dem Zeitfenster offen, fragt die App einmal nach („Kurz nachgefragt“). Bei mehreren Personen steht der Name dabei, und ein Tipp öffnet die App bei der richtigen Person.",
            ),
            GuideItem(
                "Termine",
                "Die Erinnerung kommt so lange vorher, wie du es beim Termin eingestellt hast.",
            ),
            GuideItem(
                "Wenn nichts erscheint",
                "Öffne Einstellungen → „Freigaben (Android)“. Dort siehst du, was fehlt, und kommst mit einem Tipp in die richtige Android-Einstellung. Auf manchen Handys hilft zusätzlich, die Akku-Optimierung für die App auszuschalten. Termin-Erinnerungen werden für die nächsten 60 Tage geplant und bei jedem Öffnen der App aufgefrischt.",
            ),
        ),
    ),
    GuideSection(
        id = "auswertung",
        title = "Auswertung",
        summary = "Tag, Woche, Monat: was gut lief und was auffällt",
        items = listOf(
            GuideItem(
                "Öffnen",
                "Auf der Heute-Seite ganz unten die Karte „Auswertung“ antippen. Die Zusammenfassung von gestern siehst du außerdem im Tagesstart („Rückblick gestern“).",
            ),
            GuideItem(
                "Zeitraum",
                "Oben zwischen Tag, Woche und Monat umschalten. Mit „Zurück“ und „Weiter“ blätterst du durch frühere Zeiträume.",
            ),
            GuideItem(
                "Was steht drin?",
                "Überblick (wie viele Einnahmen abgehakt wurden, Termine), Gut gelaufen, Auffällig, Empfehlungen, erledigte Aufgaben mit Ergebnis und deine Bemerkungen. Alles wird aus deinen Eingaben auf dem Handy berechnet.",
            ),
            GuideItem(
                "Nur Anhaltspunkte",
                "Die Empfehlungen sind Anregungen, kein ärztlicher Rat. Sie ersetzen weder Arzt noch Apotheke. „Nicht abgehakt“ heißt nur, dass nichts abgehakt wurde. Vielleicht wurde es trotzdem genommen.",
            ),
            GuideItem(
                "Als Aufgabe merken",
                "Bei jeder Empfehlung kannst du „+ Als Aufgabe merken“ tippen, z. B. um etwas beim Hausarzt anzusprechen.",
            ),
            GuideItem(
                "Teilen",
                "„Teilen / Kopieren“ gibt den Text an eine App deiner Wahl, z. B. Nachrichten oder E-Mail, aber nur, wenn du das selbst auslöst.",
            ),
        ),
    ),
    GuideSection(
        id = "datenschutz",
        title = "Datenschutz und Sicherheit",
        summary = "Wo deine Daten liegen",
        items = listOf(
            GuideItem(
                "Nutzungsbedingungen und Zustimmung",
                "Vor der ersten Nutzung (und nach jeder neuen Fassung) bestätigst du einzeln: Die App ersetzt keinen Arzt, keine Apotheke, keine Pflegefachkraft und kein Krankenhaus, im Notfall gilt 112. Einschätzungen und Empfehlungen sind nur Anhaltspunkte, die du prüfst bzw. prüfen lässt. Und du akzeptierst die Nutzungsbedingungen mit der Haftungsregelung. Dazu gibst du Name und Rolle an. Ohne Zustimmung lässt sich die App nicht nutzen. Die Zustimmung wird als Nachweis verschlüsselt auf dem Handy gespeichert (Name, Rolle, Zeitpunkt, App-Version, Gerät, Fassung und Prüfsumme des Textes, verkettet gegen nachträgliche Änderungen). Unter Einstellungen → „Nutzungsbedingungen und Zustimmung“ kannst du den Text lesen, den Nachweis teilen und die Zustimmung widerrufen. Frühere Nachweise werden bis zum Ende des dritten Kalenderjahres aufbewahrt und dann automatisch gelöscht. Ändern sich Name oder Rolle unter „Meine Daten“, muss erneut zugestimmt werden: Die Zustimmung gilt immer für eine bestimmte pflegende Person. Der neue Nachweis wird abgelegt, der alte bleibt mit Frist erhalten.",
            ),
            GuideItem(
                "Nur auf deinem Handy",
                "Alle Angaben zu Personen, Medikamenten, Terminen, Dokumenten und Notizen werden nur lokal im privaten App-Ordner gespeichert, und zwar verschlüsselt. Es gibt kein Benutzerkonto und keine Cloud.",
            ),
            GuideItem(
                "Mail, Druck und PDF",
                "E-Mails, Nachbestell-Listen, Tagespläne, Schreiben, Dokumente und der Medikamenten-Überblick werden nur geteilt, gesendet oder gedruckt, wenn du es selbst auslöst. Die App schickt nie etwas von allein. PDFs bleiben im privaten App-Ordner, bis du sie speicherst oder teilst.",
            ),
            GuideItem(
                "Texterkennung",
                "Das Auslesen von Packungsfotos und Dokumenten passiert auf dem Handy. Fotos werden nirgends hin gesendet. Nur wenn du den KI-Assistenten eingerichtet hast und eine Zusammenfassung anforderst, geht der Text an den gewählten Dienst.",
            ),
            GuideItem(
                "Wann geht die App ins Internet?",
                "Für App-Updates (GitHub) und die Vorschau auf geplante Funktionen. Für die Medikamenten-Beschreibung (Wikipedia, nur der Medikamentenname). Für Meldungen im Reiter „Infos“ (nur das Suchwort). Für die Umkreissuche und den Fahrdienst (OpenStreetMap, nur Postleitzahl und Ort). Für den KI-Assistenten, nur wenn du ihn eingerichtet hast und etwas anforderst. Persönliche Daten werden dabei nicht gesendet, außer dem Text, den du der KI bewusst gibst.",
            ),
            GuideItem(
                "Aktueller Stand",
                "Dies ist eine Testversion. Datenbank und Dokumente sind verschlüsselt gespeichert. Eine App-Sperre (Fingerabdruck, Gesicht, PIN) und eine verschlüsselte Sicherung gibt es in den Einstellungen. Lege regelmäßig eine Sicherung an und bewahre sie außerhalb des Handys auf. Nutzung auf eigene Verantwortung.",
            ),
        ),
    ),
    GuideSection(
        id = "sicherheit",
        title = "App-Sperre",
        summary = "Mit Fingerabdruck, Gesicht oder PIN schützen",
        items = listOf(
            GuideItem(
                "Verschlüsselte Datenbank",
                "Ab Version 0.27.0 ist die Datenbank mit Medikamenten, Terminen, Kontakten, Notizen, Aufgaben und Dokumenttexten verschlüsselt (SQLCipher). Beim ersten Start nach dem Update wandelt die App die vorhandenen Daten selbst um, das dauert nur einen Moment. Die alte Datei wird erst gelöscht (und überschrieben), wenn die neue sicher funktioniert. Bei einem Fehler bleibt alles wie bisher. Den Schlüssel erzeugt die App selbst und legt ihn im Schlüsselspeicher des Handys ab. Hinweis: Setzt du das Handy zurück oder löschst die App-Daten, sind die Daten weg. Sie lassen sich ohne den Schlüssel nicht wiederherstellen.",
            ),
            GuideItem(
                "Einschalten",
                "Einstellungen → Sicherheit → „App-Sperre“. Voraussetzung ist eine Bildschirmsperre auf dem Handy (PIN, Muster, Fingerabdruck oder Gesicht). Zur Sicherheit musst du das Ein- und Ausschalten selbst bestätigen.",
            ),
            GuideItem(
                "Wann sperrt die App?",
                "Beim Start und wenn sie länger als eingestellt im Hintergrund war (sofort, 1 oder 5 Minuten). Die App speichert selbst keine PIN, es gilt die Sperre deines Handys.",
            ),
            GuideItem(
                "Bildschirmfotos und Vorschau sperren",
                "Optional. Dann zeigt die Liste der zuletzt benutzten Apps keinen Inhalt und Bildschirmfotos sind nicht möglich. Für Feedback mit Bildschirmfotos besser ausgeschaltet lassen.",
            ),
        ),
    ),
    GuideSection(
        id = "sicherung",
        title = "Sicherung, Wiederherstellung und Zurücksetzen",
        summary = "Alle Daten in einer verschlüsselten Datei sichern, auch für ein neues Handy",
        items = listOf(
            GuideItem(
                "Sicherung erstellen",
                "Einstellungen → „Sicherung und Wiederherstellung“ → „Sicherung erstellen“. Du wählst ein Passwort (mindestens 8 Zeichen) und dann, wo die Datei gespeichert wird, z. B. in Downloads, in einer Cloud oder auf einem USB-Stick. Gesichert werden Personen, Medikamente, Termine, Kontakte, Aufgaben, Dokumente, Fotos und die Einstellungen. Die Datei ist mit deinem Passwort verschlüsselt. Nicht enthalten ist der Zugangsschlüssel für die KI, den trägst du neu ein.",
            ),
            GuideItem(
                "Wiederherstellen",
                "„Wiederherstellen“ → Datei auswählen → Passwort eingeben. Die App prüft die Sicherung und zeigt Datum und Umfang. Erst nach deiner Bestätigung werden die jetzigen Daten ersetzt und die App startet neu. Geht dabei etwas schief, bleiben die bisherigen Daten erhalten. Das funktioniert auch auf einem neuen Handy: App installieren, Sicherungsdatei dorthin kopieren, wiederherstellen.",
            ),
            GuideItem(
                "Passwort und Aufbewahrung",
                "Das Passwort kennt nur du. Wenn du es vergisst, kann niemand die Sicherung öffnen, auch die App nicht. Bewahre Datei und Passwort getrennt auf, am besten außerhalb des Handys. Sichere regelmäßig, z. B. nach größeren Änderungen oder einmal im Monat. Unter der Überschrift steht, wann die letzte Sicherung war.",
            ),
            GuideItem(
                "App zurücksetzen",
                "Einstellungen → „Sicherung, Wiederherstellung und Zurücksetzen“ → „App zurücksetzen …“. Zuerst warnt die App und zeigt, wann zuletzt gesichert wurde. Mit „Erst Sicherung erstellen“ legst du vorher eine Sicherung an und gehst danach weiter. Zum Schluss bestätigst du mit der Bildschirmsperre deines Handys (PIN, Muster, Fingerabdruck oder Gesicht). Ohne eingerichtete Bildschirmsperre ist kein Zurücksetzen möglich. Gelöscht werden alle Personen, Medikamente, Termine, Aufgaben samt Verlauf, Dokumente, Fotos, Kontakte, Bemerkungen, PDFs, Einstellungen und KI-Schlüssel. Danach startet die App wie neu. Erhalten bleiben nur die Nachweise deiner Zustimmung mit ihrer Aufbewahrungsfrist, das Zurücksetzen selbst wird dort vermerkt.",
            ),
        ),
    ),
    GuideSection(
        id = "einstellungen",
        title = "Einstellungen im Überblick",
        summary = "Was wo eingestellt wird (Zahnrad oben rechts)",
        items = listOf(
            GuideItem(
                "Ganz oben",
                "App-Updates: installierte Version, „Nach Updates suchen“ und die automatische Suche im Hintergrund.",
            ),
            GuideItem(
                "Allgemein",
                "Meine Daten (wer du bist, für E-Mails und Zustimmung), Freigaben (Android) für zuverlässige Erinnerungen, Darstellung (hell/dunkel, Schriftgröße, Design, Ecken, eigene Farben), Effekte (Beta), Heute-Seite (Notfallkarte einblenden, Tagesstart automatisch), Einnahmen abhaken (Zeitfenster und Nachfrage) und Dokumente ansehen (Betrachter in der App oder andere App).",
            ),
            GuideItem(
                "Daten und Sicherheit",
                "Sicherheit (App-Sperre, Bildschirmfotos sperren), Sicherung, Wiederherstellung und Zurücksetzen sowie Nutzungsbedingungen und Zustimmung (lesen, Nachweis teilen, widerrufen).",
            ),
            GuideItem(
                "Hilfen aus dem Internet (freiwillig)",
                "Medikamenten-Beschreibung aus Wikipedia (auf Knopfdruck oder automatisch) und der KI-Assistent mit Dienst, Schlüssel und Modell.",
            ),
            GuideItem(
                "Hilfe und Rückmeldung",
                "Anleitung und Rundgang, Feedback, die Vorschau auf geplante Funktionen und Infos zur App.",
            ),
        ),
    ),
    GuideSection(
        id = "updates",
        title = "Updates, Darstellung und Feedback",
        summary = "App aktualisieren, Schrift, Design, Ecken und Effekte, Notfallkarte, Feedback",
        items = listOf(
            GuideItem(
                "App aktualisieren",
                "Zahnrad → „Nach Updates suchen“ → „Herunterladen“ → „Installieren“. Die App meldet neue Versionen auch von selbst, das lässt sich in den Einstellungen abschalten.",
            ),
            GuideItem(
                "Darstellung",
                "In den Einstellungen wählst du, ob die App wie das Handy, immer hell oder immer dunkel aussehen soll. Die Schriftgröße der ganzen App stellst du von „Klein“ bis „Riesig“ ein (zusätzlich zur Schriftgröße des Handys). Außerdem gibt es sieben Designs zur Auswahl: Schlicht, Minimalistisch, Glas, Liquid, Sonnig, Natur und Aurora (Beta, leuchtende Farbverläufe mit Glas-Karten und Leuchtrand). Die Vorschau zeigt die Farben, ausprobieren kannst du jedes mit einem Tipp. Mit „Farben anpassen“ änderst du die drei Farben des gewählten Designs selbst: Hauptfarbe, Zweitfarbe und Hintergrund (bei Glas und Liquid die beiden Hintergrundfarben). Du siehst die Änderung sofort, „Standard“ und „Alles zurücksetzen“ stellen das Original wieder her. Unter „Ecken“ legst du einheitlich für Karten, Knöpfe, Eingabefelder und Fenster fest, wie die Ecken aussehen: eckig, dezent eckig, dezent rund oder rund („Wie im Design“ nimmt die Ecken des gewählten Designs).",
            ),
            GuideItem(
                "Effekte (Beta)",
                "Unter Einstellungen → „Effekte (Beta)“ → „Einstellen“ machst du die App lebendiger: Deckkraft der Karten (weniger = mehr Glas, der Hintergrund scheint durch), Leuchtrand um die Karten, Stärke der Farbflecken im Hintergrund, ein sanft bewegter Hintergrund, lebendige Animationen (Karten geben beim Antippen nach, Auf- und Zuklappen und Reiterwechsel gleiten weich) und Glanz auf Skalen und Balken. Am schönsten wirkt das mit „Aurora“, „Glas“ oder „Liquid“. „Alles wie im Design“ stellt die Werte des Designs wieder her. Der bewegte Hintergrund braucht etwas mehr Akku. Die Effekte sind neu und noch in Erprobung.",
            ),
            GuideItem(
                "Notfallkarte",
                "Die Notfallkarte öffnest du immer über das rote Telefon oben („Notfallkarte zeigen“). Unter Einstellungen → „Heute-Seite“ kannst du sie zusätzlich als Karte auf der Heute-Seite einblenden. Sie zeigt Name, Geburtsdatum, Pflegegrad, Wichtiges, Medikamente, Kontakte und deine Ansprechdaten auf einen Blick, mit Notruf-Taste (öffnet nur die Telefon-App), zum Kopieren oder Teilen. Die Versichertennummer zeigst du nur auf Wunsch.",
            ),
            GuideItem(
                "Feedback",
                "Wünsche, Fehler und Ideen kannst du über den Feedback-Bereich in den Einstellungen melden. Bitte dabei keine persönlichen Daten angeben. Dort steht auch, was als Nächstes geplant ist.",
            ),
        ),
    ),
    GuideSection(
        id = "faq",
        title = "Häufige Fragen",
        summary = "Wenn etwas nicht so ist wie erwartet",
        items = listOf(
            GuideItem(
                "Der Vorrat stimmt nicht.",
                "Medikament öffnen und den tatsächlichen Vorrat eintragen. Die App rechnet ab diesem Tag neu.",
            ),
            GuideItem(
                "Die Packung wurde falsch gelesen.",
                "Die erkannten Namen sind nur Vorschläge. Einen anderen Vorschlag antippen oder Name und Angaben selbst eintippen. Ein besseres Licht und ein gerades Foto helfen.",
            ),
            GuideItem(
                "Ich habe etwas falsch eingetragen.",
                "Die Karte antippen und ändern. Alles lässt sich nachträglich bearbeiten.",
            ),
            GuideItem(
                "Eine Einnahmezeit fehlt oder ist falsch.",
                "Medikament öffnen und die Zeiten ändern. Die Erinnerungen passen sich automatisch an.",
            ),
            GuideItem(
                "Erinnerungen kommen nicht oder zu spät.",
                "Einstellungen → „Freigaben (Android)“ öffnen und alles mit ✗ einschalten. Auf manchen Handys zusätzlich die Akku-Optimierung für die App ausschalten. Ist etwas Wichtiges aus, zeigt die Heute-Seite oben eine rote Karte.",
            ),
            GuideItem(
                "Warum soll ich wieder zustimmen?",
                "Weil sich die Nutzungsbedingungen geändert haben, die Zustimmung widerrufen wurde oder unter „Meine Daten“ Name oder Rolle geändert wurden. Die Zustimmung gilt immer für eine bestimmte Person und eine bestimmte Fassung.",
            ),
            GuideItem(
                "Wie drucke ich den Plan für heute?",
                "Auf der Heute-Seite oben „Tagesplan (PDF)“ antippen, dann „Drucken“ oder „Als PDF teilen“. Im Druckfenster kannst du auch „Als PDF speichern“ wählen.",
            ),
            GuideItem(
                "Eine erledigte Aufgabe ist weg.",
                "Erledigte Aufgaben sind eingeklappt („Erledigte anzeigen“) oder im Verlauf. „Verlauf und Suche“ in der Aufgaben-Karte zeigt alle, auch ausgeblendete. Dort kannst du sie auch wieder aufnehmen.",
            ),
            GuideItem(
                "Die Kategorie eines Dokuments stimmt nicht.",
                "Dokument antippen, „Bearbeiten“ und eine andere Kategorie wählen. Oft steht dort schon ein besserer „Vorschlag“ zum Antippen.",
            ),
            GuideItem(
                "Kann ich meine Daten sichern?",
                "Ja. Einstellungen → „Sicherung und Wiederherstellung“. Die App legt eine verschlüsselte Datei mit allen Daten an (mit deinem Passwort). Mit ihr kannst du alles auf diesem oder einem neuen Handy wiederherstellen.",
            ),
        ),
    ),
)
