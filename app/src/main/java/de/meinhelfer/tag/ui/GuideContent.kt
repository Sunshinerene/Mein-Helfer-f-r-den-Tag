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
                "„Mein Helfer für den Tag“ unterstützt pflegende Angehörige im Alltag: Medikamente und Einnahmen, Termine, Kontakte zu Ärzten und Diensten, eigene Notizen und eine Auswertung.",
            ),
            GuideItem(
                "Die vier Bereiche unten",
                "Heute: was jetzt ansteht und abgehakt werden kann. Medikamente: Pläne und Vorrat. Termine: Arzt- und Pflegetermine. Kontakte: Ärzte, Pflegedienst, Apotheke und mehr.",
            ),
            GuideItem(
                "Oben rechts",
                "Das Info-Symbol öffnet diese Anleitung, das Zahnrad die Einstellungen (Updates, Darstellung, Feedback).",
            ),
            GuideItem(
                "Empfohlene Reihenfolge",
                "1. Person anlegen oder umbenennen. 2. Kontakte eintragen (z. B. Hausarzt). 3. Medikamente mit Einnahmezeiten anlegen. 4. Termine eintragen. Danach genügt meist die Heute-Seite.",
            ),
        ),
    ),
    GuideSection(
        id = "person",
        title = "Personen",
        summary = "Für wen gilt was – mehrere Personen betreuen",
        items = listOf(
            GuideItem(
                "Für wen gilt es?",
                "Oben auf jeder Seite steht „Für: Name“. Medikamente, Termine, Abhaken, Bemerkungen, To-dos und Auswertung gelten immer für diese Person.",
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
                "Im selben Menü kannst du die Person umbenennen. Beim Löschen gehen ihre Medikamente, Termine, Bemerkungen und To-dos verloren. Das lässt sich nicht rückgängig machen. Kontakte bleiben erhalten, weil sie für alle gelten.",
            ),
        ),
    ),
    GuideSection(
        id = "heute",
        title = "Heute",
        summary = "Abhaken, Notizen, To-dos und Nachbestell-Warnung",
        items = listOf(
            GuideItem(
                "Einnahmen abhaken",
                "Unter „Heute einnehmen“ steht jede Einnahme mit Uhrzeit. Haken setzen, wenn sie erledigt ist. Ist die Zeit vorbei und der Haken fehlt, erscheint „noch nicht abgehakt“ in Rot.",
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
                "To-dos",
                "Eine kleine Liste für Dinge, die erledigt werden sollen, z. B. „Rezept anfordern“. Haken setzen, wenn erledigt. „Löschen“ entfernt den Eintrag.",
            ),
            GuideItem(
                "Bald nachbestellen",
                "Die rote Karte oben erscheint, wenn der Vorrat eines Medikaments nur noch für etwa 7 Tage reicht.",
            ),
            GuideItem(
                "Termine der nächsten 7 Tage",
                "Hier siehst du, was in der kommenden Woche ansteht. Auch Termine lassen sich abhaken und mit einer Notiz versehen.",
            ),
        ),
    ),
    GuideSection(
        id = "medikamente",
        title = "Medikamente",
        summary = "Anlegen, Foto, PZN, Beschreibung, Vorrat",
        items = listOf(
            GuideItem(
                "Medikament anlegen",
                "Im Bereich „Medikamente“ auf das Plus tippen. Pflicht ist nur der Name. Der Papierkorb auf der Karte löscht das Medikament (mit Rückfrage).",
            ),
            GuideItem(
                "Infos auf einen Blick",
                "Eine Medikamentenkarte antippen (oder „Infos & Beschreibung“) zeigt alles in einer Übersicht: Einnahme, Vorrat, PZN, Arzt, Apotheke, Notizen, Beschreibung und KI-Hinweis. Von dort kommst du mit „Bearbeiten“ zum Ändern. Eine gekürzte Beschreibung steht schon auf der Karte.",
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
                "Im Bereich „Medikamente“ oben auf „Nachbestellung“ tippen, oder auf der Heute-Seite in der roten Karte „Bald nachbestellen“.",
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
        id = "wegweiser",
        title = "Wegweiser Leistungen",
        summary = "Welche Hilfen und Anträge je Pflegegrad",
        items = listOf(
            GuideItem(
                "Öffnen",
                "Auf der Heute-Seite in der Profilkarte „Wegweiser Leistungen“ antippen.",
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
                "Auf die To-do-Liste",
                "Bei vielen Punkten gibt es „+ Auf meine To-do-Liste“, z. B. „Pflegegrad beantragen“ oder „Verhinderungspflege planen“. So behältst du den Überblick, was noch zu tun ist.",
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
        summary = "Eintragen, von – bis, Erinnerung, Kalender",
        items = listOf(
            GuideItem(
                "Termin anlegen",
                "Im Bereich „Termine“ auf das Plus tippen. Den Titel kannst du aus der Liste wählen oder selbst eintippen, z. B. für etwas, das nicht in der Liste steht.",
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
                "Dokument hinzufügen",
                "Im Bereich „Dokumente“ auf das Plus tippen und auswählen: „Dokument fotografieren“ (bei mehreren Seiten mit „Weitere Seite fotografieren“), „Foto(s) aus der Galerie“ oder „Datei laden“. Bei „Datei laden“ suchst du eine Datei aus einem Ordner auf dem Handy, z. B. eine PDF vom Arzt, ein Bild oder eine Textdatei.",
            ),
            GuideItem(
                "Text wird gelesen und genutzt",
                "Die App liest den Text direkt auf dem Handy (bei PDFs bis zu 12 Seiten). Daraus schlägt sie Titel, Kategorie (z. B. Arztbericht, Befund, Rezept, Pflegegrad-Bescheid), Datum und den passenden Kontakt vor. Alles lässt sich ändern. Der erkannte Text ist durchsuchbar und korrigierbar.",
            ),
            GuideItem(
                "Suchen",
                "Oben in der Dokumentenliste suchst du nach Titel, Kategorie oder einem Wort aus dem Text, z. B. dem Namen eines Medikaments.",
            ),
            GuideItem(
                "Öffnen und teilen",
                "Eine Karte antippen zeigt die Details. „Öffnen“ zeigt die Datei in einer App deiner Wahl, „Teilen“ gibt sie weiter, z. B. per E-Mail. Das passiert nur nach deinem Tipp.",
            ),
            GuideItem(
                "Sicher abgelegt",
                "Die Dateien werden verschlüsselt im privaten App-Ordner gespeichert und nirgends hin gesendet. Beim Öffnen entsteht kurz eine Kopie, die beim nächsten App-Start gelöscht wird. Nicht lesbar sind passwortgeschützte PDFs und Dateitypen wie Word. Sie werden trotzdem gespeichert, nur ohne Text.",
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
                "Auf der Karte öffnet „Anrufen“ die Telefon-App mit der Nummer und „E-Mail“ dein Mailprogramm. Es wird nichts automatisch gesendet.",
            ),
            GuideItem(
                "Filtern",
                "Sobald es mehrere Arten gibt, erscheinen oben Filter, z. B. nur „Pflegedienst“.",
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
                "Wo erscheint sie?",
                "In der Auswertung („KI-Hinweise holen“) und im Medikament („KI-Hinweis zu diesem Medikament“). Bei jeder Antwort kannst du einen Punkt mit „Auf meine To-do-Liste“ übernehmen und später beim Arzt ansprechen oder eben nicht.",
            ),
            GuideItem(
                "Was wird gesendet?",
                "Nichts passiert von allein. Vor dem Senden zeigt die App den genauen Text und du bestätigst. Der Name der Person ist nie dabei. Deine Bemerkungen werden nur mitgesendet, wenn du es einschaltest. Beim Medikament geht nur der Medikamentenname raus.",
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
                "Medikamente",
                "Zu jeder Einnahmezeit erscheint eine Benachrichtigung. Mit „Eingenommen“ direkt in der Benachrichtigung wird die Einnahme abgehakt, ohne die App zu öffnen. Bei mehreren Personen steht der Name dabei.",
            ),
            GuideItem(
                "Termine",
                "Die Erinnerung kommt so lange vorher, wie du es beim Termin eingestellt hast.",
            ),
            GuideItem(
                "Wenn nichts erscheint",
                "Prüfe in den Handy-Einstellungen, ob Benachrichtigungen für die App erlaubt sind und ob die Akku-Optimierung die App ausbremst. Falls ja, die Akku-Optimierung für diese App ausschalten.",
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
                "Auf der Heute-Seite die Karte „Auswertung“ antippen.",
            ),
            GuideItem(
                "Zeitraum",
                "Oben zwischen Tag, Woche und Monat umschalten. Mit „Zurück“ und „Weiter“ blätterst du durch frühere Zeiträume.",
            ),
            GuideItem(
                "Was steht drin?",
                "Überblick (wie viele Einnahmen abgehakt wurden), Gut gelaufen, Auffällig, Empfehlungen und deine Bemerkungen. Alles wird aus deinen Eingaben auf dem Handy berechnet.",
            ),
            GuideItem(
                "Nur Anhaltspunkte",
                "Die Empfehlungen sind Anregungen, kein ärztlicher Rat. Sie ersetzen weder Arzt noch Apotheke. „Nicht abgehakt“ heißt nur, dass nichts abgehakt wurde. Vielleicht wurde es trotzdem genommen.",
            ),
            GuideItem(
                "Auf die To-do-Liste",
                "Bei jeder Empfehlung kannst du „Auf meine To-do-Liste“ tippen, z. B. um etwas beim Hausarzt anzusprechen.",
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
                "Nur auf deinem Handy",
                "Alle Angaben zu Personen, Medikamenten, Terminen und Notizen werden nur lokal im privaten App-Ordner gespeichert. Es gibt kein Benutzerkonto und keine Cloud.",
            ),
            GuideItem(
                "Mail, Druck und PDF",
                "Nachbestell-Listen werden nur dann per Mail geteilt oder gedruckt, wenn du es selbst auslöst. Die PDFs bleiben im privaten App-Ordner, bis du sie speicherst oder teilst.",
            ),
            GuideItem(
                "Texterkennung",
                "Das Auslesen von Packungsfotos passiert auf dem Handy. Fotos werden nirgends hin gesendet.",
            ),
            GuideItem(
                "Wann geht die App ins Internet?",
                "Um nach App-Updates zu suchen, sie über GitHub zu laden und die Vorschau auf geplante Funktionen anzuzeigen. Außerdem für die Medikamenten-Beschreibung (Wikipedia), aber nur auf Knopfdruck oder wenn du das automatische Laden einschaltest. Gesendet wird dabei nur der Medikamentenname, keine persönlichen Daten.",
            ),
            GuideItem(
                "Aktueller Stand",
                "Dies ist eine Testversion. Dokumente sind verschlüsselt gespeichert, die übrigen Angaben (Medikamente, Termine, Notizen) aber noch nicht, und es gibt noch keine App-Sperre und keine Datensicherung. Beides steht auf der Liste der geplanten Funktionen. Bitte nur mit Testdaten oder auf eigene Verantwortung nutzen.",
            ),
        ),
    ),
    GuideSection(
        id = "updates",
        title = "Updates und Feedback",
        summary = "App aktualisieren, Wünsche und Fehler melden",
        items = listOf(
            GuideItem(
                "App aktualisieren",
                "Zahnrad → „Nach Updates suchen“ → „Herunterladen“ → „Installieren“. Die App meldet neue Versionen auch von selbst, das lässt sich in den Einstellungen abschalten.",
            ),
            GuideItem(
                "Darstellung",
                "In den Einstellungen wählst du, ob die App wie das Handy, immer hell oder immer dunkel aussehen soll. Außerdem gibt es sechs Designs zur Auswahl: Schlicht, Minimalistisch, Glas, Liquid, Sonnig und Natur. Die Vorschau zeigt die Farben, ausprobieren kannst du jedes mit einem Tipp.",
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
                "Kann ich meine Daten sichern?",
                "Eine eigene Datensicherung gibt es noch nicht. Sie steht auf der Liste der geplanten Funktionen.",
            ),
        ),
    ),
)
