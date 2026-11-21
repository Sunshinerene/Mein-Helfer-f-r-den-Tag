package de.meinhelfer.tag.ui

import de.meinhelfer.tag.data.CaregiverRole

/** Tipps und Hilfen je nach Rolle der pflegenden Person. Stand wie die festen Themenkarten, ohne Gewaehr. */
internal val ROLE_TIPS: Map<CaregiverRole, List<InfoCard>> = mapOf(
    CaregiverRole.Angehoeriger to listOf(
        InfoCard(
            "Auf dich selbst achten",
            "Pflege ist ein Dauerlauf, kein Sprint. Plane feste Pausen und eigene Termine ein, teile Aufgaben auf und nimm Hilfe an. " +
                "Dauerhafte Müdigkeit, Gereiztheit, Schlafprobleme oder Rückzug von Freunden sind häufige Warnzeichen für Überlastung. " +
                "Sprich früh mit Hausarzt oder einer Beratungsstelle darüber. Wenn dir alles zu viel wird: Die Telefonseelsorge ist kostenlos und rund um die Uhr erreichbar (0800 111 0 111).",
            query = "pflegende Angehörige Belastung Hilfe",
            links = listOf(
                "Telefonseelsorge" to "https://www.telefonseelsorge.de",
                "Zentrum für Qualität in der Pflege (Ratgeber)" to "https://www.zqp.de",
            ),
        ),
        InfoCard(
            "Auszeit nehmen",
            "Ab Pflegegrad 2 gibt es Verhinderungspflege (wenn du ausfällst oder Urlaub brauchst) und Kurzzeitpflege (die pflegebedürftige Person wird vorübergehend im Heim betreut). " +
                "Beides lässt sich über den gemeinsamen Jahresbetrag von bis zu 3.539 € im Jahr flexibel nutzen. Zusätzlich gibt es den Entlastungsbetrag von 131 € im Monat, z. B. für Haushaltshilfe, Betreuung oder Begleitung durch anerkannte Anbieter. " +
                "Frage bei der Pflegekasse nach den genauen Voraussetzungen. Unter Kontakte findest du dafür eine vorbereitete E-Mail an die Pflegekasse.",
            query = "Verhinderungspflege Kurzzeitpflege gemeinsamer Jahresbetrag",
            wegweiser = true,
        ),
        InfoCard(
            "Deine Absicherung",
            "Die Pflegekasse zahlt unter Voraussetzungen Beiträge zur Rentenversicherung für Pflegepersonen. Meist gilt das ab Pflegegrad 2, wenn du die Person mindestens 10 Stunden pro Woche verteilt auf mindestens 2 Tage pflegst und nicht mehr als 30 Stunden pro Woche erwerbstätig bist. " +
                "Du bist bei der Pflege außerdem gesetzlich unfallversichert, und auch eine Absicherung über die Arbeitslosenversicherung kann bestehen. " +
                "Die Pflegekasse schickt dir dafür einen Fragebogen. Frage dort nach, damit nichts verloren geht.",
            query = "Rentenversicherung Pflegeperson Voraussetzungen",
        ),
        InfoCard(
            "Pflegekurs und Austausch",
            "Pflegekurse nach § 45 SGB XI sind für Angehörige kostenlos. Dort lernst du praktische Handgriffe und triffst andere Pflegende. " +
                "Auch der Austausch in Selbsthilfegruppen entlastet. Die Pflegekasse nennt Kurse in deiner Nähe. Eine vorbereitete E-Mail dafür findest du bei den Kontakten.",
            query = "Pflegekurs pflegende Angehörige kostenlos",
            links = listOf(
                "NAKOS: Selbsthilfegruppen finden" to "https://www.nakos.de",
                "wir pflegen e. V.: Interessenvertretung pflegender Angehöriger" to "https://www.wir-pflegen.net",
            ),
        ),
        InfoCard(
            "Kur und Reha für Pflegende",
            "Auch pflegende Angehörige können eine Vorsorge- oder Rehabilitationsmaßnahme bekommen, bei Bedarf sogar gemeinsam mit der pflegebedürftigen Person. " +
                "Dafür sprichst du mit deinem Hausarzt. Der Antrag geht an deine eigene Krankenkasse.",
            query = "Kur pflegende Angehörige Antrag",
        ),
    ),
    CaregiverRole.Pflegedienst to listOf(
        InfoCard(
            "Diese App ersetzt keine Pflegedokumentation",
            "Die App ist ein privater Helfer für Angehörige. Pflegedokumentation, Medikamentengabe nach Plan und Abstimmung mit Ärzten laufen weiter über die Wege deines Arbeitgebers. " +
                "Frage vor dem Eintragen von Daten betreuter Personen bei deinem Arbeitgeber bzw. der Datenschutzbeauftragten nach, ob das erlaubt ist.",
        ),
        InfoCard(
            "Zusammenarbeit mit Angehörigen",
            "Klare Absprachen helfen allen: Wer ist Ansprechperson, wann wird angerufen, wer informiert den Arzt? Halte Änderungen bei Einsätzen und Medikamenten schriftlich fest. " +
                "Bei den Kontakten findest du vorbereitete E-Mails zum Ändern von Einsätzen und für Rückmeldungen.",
        ),
        InfoCard(
            "Auffälligkeiten melden",
            "Bei Sturz, plötzlicher Verwirrtheit, deutlicher Verschlechterung oder Problemen mit Medikamenten wartest du nicht ab: Informiere sofort deine Leitung und die Angehörigen, in dringenden Fällen den ärztlichen Bereitschaftsdienst (116 117) oder den Notruf (112). Halte fest, was du beobachtet hast.",
        ),
        InfoCard(
            "Gesund bleiben im Beruf",
            "Rückengerechtes Arbeiten, Hilfsmittel beim Umlagern und feste Pausen schützen dich langfristig. Die Berufsgenossenschaft BGW bietet dazu Informationen und Angebote für Pflegekräfte.",
            query = "Pflegekräfte Gesundheit Rücken Prävention",
            links = listOf("BGW: Gesundheitsdienst und Wohlfahrtspflege" to "https://www.bgw-online.de"),
        ),
    ),
    CaregiverRole.Betreuung to listOf(
        InfoCard(
            "Klare Absprachen",
            "Halte mit der Familie schriftlich fest: Aufgaben, Arbeits- und Ruhezeiten, Ansprechpartner und Notfallnummern. Die Heute-Seite und die Aufgaben in der App helfen, den Tag gemeinsam im Blick zu behalten.",
        ),
        InfoCard(
            "Pausen und freie Zeit",
            "Auch du hast Anspruch auf Pausen und Ruhezeiten. Wenn Absprachen nicht eingehalten werden oder du unsicher bist, berät dich die Beratungsstelle Faire Mobilität.",
            links = listOf("Faire Mobilität: Beratung für mobile Beschäftigte" to "https://www.faire-mobilitaet.de"),
        ),
        InfoCard(
            "Im Notfall",
            "Notruf 112, ärztlicher Bereitschaftsdienst 116 117. Lege den Medikationsplan und die wichtigsten Kontakte gut sichtbar bereit. In der App findest du Ärzte und Angehörige unter Kontakte.",
        ),
        InfoCard(
            "Was darfst du übernehmen?",
            "Welche medizinischen Aufgaben du übernehmen darfst (z. B. Medikamente geben, Verbände), klärst du vorher mit der Familie, dem Arzt und dem Pflegedienst. Frage im Zweifel nach, statt etwas zu raten.",
        ),
    ),
    CaregiverRole.Betreuer to listOf(
        InfoCard(
            "Aufgaben und Grenzen",
            "Deine Befugnis gilt für die Aufgabenkreise, die im Betreuerausweis bzw. in der Vollmacht stehen. Die Wünsche der betreuten Person haben Vorrang. Betreuungsbehörde und Betreuungsvereine beraten kostenlos.",
            query = "rechtliche Betreuung Aufgaben Betreuungsbehörde",
        ),
        InfoCard(
            "Nachweise griffbereit",
            "Speichere Betreuerausweis oder Vollmacht als Dokument in der App (bleibt verschlüsselt auf dem Handy) und lege Ärzten bei Bedarf eine Kopie vor. Das erleichtert Anfragen wie Befunde oder Auskünfte.",
        ),
        InfoCard(
            "Berichte und Rechenschaft",
            "Das Betreuungsgericht erwartet regelmäßige Berichte. Notizen, Termine und Aufgaben aus der App sind eine gute Gedächtnisstütze, ersetzen aber keine amtlichen Unterlagen.",
        ),
    ),
    CaregiverRole.Ehrenamt to listOf(
        InfoCard(
            "Grenzen setzen",
            "Du darfst helfen, musst aber nicht alles übernehmen. Sprich mit den Angehörigen ab, was du machst und was nicht. Medizinische Aufgaben übernimmst du nur nach Absprache und Anleitung.",
        ),
        InfoCard(
            "Pflegekurs auch für dich",
            "Pflegekurse nach § 45 SGB XI stehen auch Nachbarn, Freunden und anderen ehrenamtlich Helfenden offen und sind kostenlos. Die Pflegekasse der betreuten Person nennt Angebote.",
            query = "Pflegekurs ehrenamtlich Pflegende kostenlos",
        ),
        InfoCard(
            "Nachbarschaftshilfe",
            "Der Entlastungsbetrag (131 € im Monat) kann je nach Bundesland auch für anerkannte Nachbarschaftshilfe eingesetzt werden. Frage bei der Pflegekasse der betreuten Person nach den Regeln in deinem Bundesland.",
            query = "Nachbarschaftshilfe Entlastungsbetrag Bundesland",
        ),
    ),
    CaregiverRole.Selbst to listOf(
        InfoCard(
            "Kostenlos beraten lassen",
            "Pflegekassen müssen kostenlos zur Pflege beraten. Auch Pflegestützpunkte beraten unabhängig. Ein Beratungsgespräch kann auch bei dir zu Hause stattfinden. Unter Kontakte findest du eine vorbereitete E-Mail an deine Pflegekasse.",
            query = "Pflegeberatung Pflegestützpunkt",
        ),
        InfoCard(
            "Pflegegrad und Leistungen",
            "Einen Pflegegrad beantragst du formlos bei deiner Pflegekasse, ein Anruf genügt. Danach kommt eine Begutachtung. Ein Pflegetagebuch hilft, den Alltag genau zu beschreiben. Was dir je Pflegegrad zusteht, siehst du im Wegweiser.",
            wegweiser = true,
        ),
        InfoCard(
            "Hilfsmittel und Wohnung",
            "Hilfsmittel wie Rollator oder Pflegebett gibt es auf ärztliche Verordnung. Für Umbauten in der Wohnung (z. B. Badumbau) kann die Pflegekasse einen Zuschuss geben. Frage vor dem Umbau nach, nicht erst danach.",
            query = "Wohnraumanpassung Zuschuss Pflegekasse",
        ),
        InfoCard(
            "Vorsorge rechtzeitig regeln",
            "Mit Vorsorgevollmacht und Patientenverfügung bestimmst du, wer für dich entscheiden darf und welche Behandlung du willst. Beratung gibt es bei der Betreuungsbehörde, bei Betreuungsvereinen und beim Notar.",
            query = "Vorsorgevollmacht Patientenverfügung",
        ),
    ),
    CaregiverRole.Sonstige to listOf(
        InfoCard(
            "Gut zu wissen",
            "Diese App soll allen helfen, die sich um einen Menschen kümmern. Unter „Meine Daten“ kannst du deine Rolle jederzeit ändern. Die allgemeinen Themen unten gelten für alle.",
        ),
    ),
)

/** Reihenfolge der allgemeinen Karten je Rolle: Titelanfaenge, die zuerst kommen. Rest bleibt in Originalreihenfolge. */
internal fun infoOrder(role: CaregiverRole?): List<String> = when (role) {
    CaregiverRole.Angehoeriger -> listOf("Entlastung", "Pflege und Beruf", "Beratung")
    CaregiverRole.Pflegedienst, CaregiverRole.Betreuung -> listOf("Medikamente", "Pflegereform", "Das gilt heute")
    CaregiverRole.Betreuer -> listOf("Beratung", "Das gilt heute", "Pflegereform")
    CaregiverRole.Selbst -> listOf("Beratung", "Das gilt heute", "Medikamente", "Pflegereform")
    CaregiverRole.Ehrenamt -> listOf("Entlastung", "Beratung")
    else -> emptyList()
}

/** Weniger wichtige Karten kommen ans Ende. */
internal fun infoLast(role: CaregiverRole?): List<String> = when (role) {
    CaregiverRole.Pflegedienst, CaregiverRole.Betreuung, CaregiverRole.Selbst, CaregiverRole.Betreuer -> listOf("Pflege und Beruf", "Entlastung")
    else -> emptyList()
}
