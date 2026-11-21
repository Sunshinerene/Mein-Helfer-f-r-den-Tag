package de.meinhelfer.tag.ui

/**
 * Wegweiser zu Leistungen und Antraegen der Pflegeversicherung (Deutschland, SGB XI).
 * Betraege je Pflegegrad 1 bis 5, leer = gibt es dort nicht. Stand: November 2026
 * (Betraege seit 01.01.2025 unveraendert). Angaben ohne Gewaehr, bei Aenderungen der Gesetze anpassen.
 */
const val WEGWEISER_STAND = "November 2026"

data class Benefit(
    val title: String,
    val text: String,
    /** Betrag je Pflegegrad 1..5 (5 Eintraege), leer = nicht vorgesehen. null = kein Betrag. */
    val amounts: List<String>? = null,
    val todo: String? = null,
)

data class BenefitSection(val title: String, val intro: String, val items: List<Benefit>)

val WEGWEISER_SECTIONS: List<BenefitSection> = listOf(
    BenefitSection(
        title = "Erste Schritte",
        intro = "Wenn noch kein Pflegegrad besteht oder er überprüft werden soll.",
        items = listOf(
            Benefit(
                "Pflegegrad beantragen",
                "Den Antrag stellt man bei der Pflegekasse der Krankenkasse, formlos und auch telefonisch. Der Medizinische Dienst (bei Privatversicherten Medicproof) schaut sich zu Hause an, wie selbstständig die Person im Alltag ist. Die Leistungen gelten ab dem Tag des Antrags, deshalb nicht lange warten.",
                todo = "Pflegegrad bei der Pflegekasse beantragen",
            ),
            Benefit(
                "Begutachtung vorbereiten",
                "Hilfreich sind ein Pflegetagebuch über ein bis zwei Wochen (was geht allein, wobei braucht es Hilfe), Arztberichte und eine aktuelle Medikamentenliste. Diese App kann die Medikamentenliste als PDF erzeugen (Nachbestellung). Am besten ist eine vertraute Person beim Termin dabei.",
                todo = "Unterlagen für die Begutachtung zusammenstellen (Pflegetagebuch, Arztberichte, Medikamentenliste)",
            ),
            Benefit(
                "Bescheid prüfen, Widerspruch",
                "Der Bescheid soll nach etwa 25 Arbeitstagen kommen. Ist man mit dem Pflegegrad nicht einverstanden, kann man innerhalb von einem Monat nach Erhalt schriftlich Widerspruch einlegen. Das Gutachten darf man anfordern. Eine Pflegeberatung hilft dabei.",
                todo = "Pflegegrad-Bescheid prüfen (Widerspruch nur innerhalb von 1 Monat)",
            ),
            Benefit(
                "Kostenlose Pflegeberatung",
                "Jede pflegebedürftige Person hat Anspruch auf eine kostenlose, unabhängige Pflegeberatung, z. B. beim Pflegestützpunkt oder bei der Pflegekasse. Dort bekommt man auch Hilfe bei den Anträgen.",
                todo = "Termin beim Pflegestützpunkt oder zur Pflegeberatung vereinbaren",
            ),
        ),
    ),
    BenefitSection(
        title = "Leistungen für zu Hause",
        intro = "Beträge pro Monat bzw. Jahr, je nach Pflegegrad.",
        items = listOf(
            Benefit(
                "Pflegegeld",
                "Wird überwiesen, wenn Angehörige oder andere Privatpersonen pflegen. Das Geld darf frei verwendet werden, z. B. als Anerkennung für die Pflegeperson.",
                amounts = listOf("", "347 € / Monat", "599 € / Monat", "800 € / Monat", "990 € / Monat"),
            ),
            Benefit(
                "Pflegesachleistung",
                "Für einen ambulanten Pflegedienst, der direkt mit der Pflegekasse abrechnet. Man kann Pflegegeld und Sachleistung kombinieren (Kombinationsleistung), dann wird das Pflegegeld anteilig gekürzt.",
                amounts = listOf("", "796 € / Monat", "1.497 € / Monat", "1.859 € / Monat", "2.299 € / Monat"),
                todo = "Pflegedienst anfragen",
            ),
            Benefit(
                "Entlastungsbetrag",
                "Steht schon ab Pflegegrad 1 zu. Zweckgebunden für Entlastung im Alltag, z. B. Betreuung, Haushaltshilfe, Begleitung oder Eigenanteil der Tagespflege. Der Anbieter muss anerkannt sein. Nicht genutzte Beträge können bis 30. Juni des Folgejahres genutzt werden. Meist wird gegen Rechnung erstattet.",
                amounts = List(5) { "131 € / Monat" },
                todo = "Entlastungsbetrag nutzen und Rechnungen einreichen",
            ),
            Benefit(
                "Entlastungsbudget (Verhinderungs- und Kurzzeitpflege)",
                "Gemeinsamer Jahresbetrag, wenn die Pflegeperson Urlaub braucht oder krank ist (Verhinderungspflege) oder zeitweise Pflege rund um die Uhr nötig ist, z. B. nach einem Krankenhausaufenthalt (Kurzzeitpflege). Je bis zu 8 Wochen pro Jahr.",
                amounts = listOf("", "3.539 € / Jahr", "3.539 € / Jahr", "3.539 € / Jahr", "3.539 € / Jahr"),
                todo = "Verhinderungs- oder Kurzzeitpflege planen und beantragen",
            ),
            Benefit(
                "Tages- und Nachtpflege",
                "Die Person wird tagsüber (oder nachts) in einer Einrichtung betreut und abends nach Hause gebracht. Das Budget ist zusätzlich zu Pflegegeld oder Sachleistung.",
                amounts = listOf("", "721 € / Monat", "1.357 € / Monat", "1.685 € / Monat", "2.085 € / Monat"),
                todo = "Tagespflege in der Nähe anfragen",
            ),
            Benefit(
                "Pflegehilfsmittel zum Verbrauch",
                "Z. B. Einmalhandschuhe, Desinfektionsmittel, Bettschutzeinlagen. Ab Pflegegrad 1, über die Pflegekasse bzw. einen Lieferanten.",
                amounts = List(5) { "bis 42 € / Monat" },
                todo = "Pflegehilfsmittel zum Verbrauch beantragen",
            ),
            Benefit(
                "Technische Pflegehilfsmittel und Hausnotruf",
                "Z. B. Pflegebett, Lagerungshilfen, Hausnotruf. Wird meist geliehen oder bezuschusst, die Zuzahlung beträgt höchstens 10 € je Hilfsmittel. Für den Hausnotruf gibt es einen monatlichen Zuschuss.",
                todo = "Pflegehilfsmittel oder Hausnotruf bei der Pflegekasse beantragen",
            ),
            Benefit(
                "Wohnumfeld verbessern",
                "Zuschuss für Umbauten, die die Pflege zu Hause erleichtern, z. B. ebenerdige Dusche, Rampe, breitere Türen. Wichtig: erst beantragen, dann umbauen. Wohnen mehrere Pflegebedürftige zusammen, gibt es insgesamt mehr.",
                amounts = List(5) { "bis 4.180 € je Maßnahme" },
                todo = "Zuschuss für Wohnumfeldverbesserung vor dem Umbau beantragen",
            ),
        ),
    ),
    BenefitSection(
        title = "Pflegeheim",
        intro = "Wenn Pflege zu Hause nicht mehr geht.",
        items = listOf(
            Benefit(
                "Vollstationäre Pflege",
                "Die Pflegekasse zahlt einen festen Betrag je Pflegegrad. Dazu kommt ein Zuschlag auf den Eigenanteil, der mit der Dauer des Aufenthalts steigt. Den Rest zahlt man selbst, das Sozialamt kann bei geringem Einkommen helfen (Hilfe zur Pflege).",
                amounts = listOf("131 € / Monat", "805 € / Monat", "1.319 € / Monat", "1.855 € / Monat", "2.096 € / Monat"),
            ),
        ),
    ),
    BenefitSection(
        title = "Für pflegende Angehörige",
        intro = "Auch die Pflegenden haben Ansprüche.",
        items = listOf(
            Benefit(
                "Pflegekurse",
                "Kostenlose Kurse der Pflegekassen für Angehörige, auch als Einzelschulung zu Hause. Dort lernt man z. B. Umlagern, Heben und den Umgang mit Demenz.",
                todo = "Pflegekurs bei der Pflegekasse anfragen",
            ),
            Benefit(
                "Beratungsbesuch bei Pflegegeld",
                "Wer Pflegegeld bekommt, muss regelmäßig einen Beratungsbesuch durch einen Pflegedienst nachweisen: bei Pflegegrad 2 und 3 halbjährlich, bei Pflegegrad 4 und 5 vierteljährlich, bei Pflegegrad 1 freiwillig. Ohne Besuch kann das Pflegegeld gekürzt werden.",
                todo = "Beratungsbesuch (Pflegegeld) vereinbaren",
            ),
            Benefit(
                "Pflegeunterstützungsgeld",
                "Bei einer akut auftretenden Pflegesituation darf man bis zu 10 Arbeitstage der Arbeit fernbleiben. Die Pflegekasse zahlt dafür einen Lohnersatz. Der Antrag geht an die Pflegekasse der pflegebedürftigen Person.",
            ),
            Benefit(
                "Pflegezeit und Familienpflegezeit",
                "Wer berufstätig ist, kann sich teilweise oder ganz freistellen lassen (bis 6 Monate Pflegezeit, bis 24 Monate Familienpflegezeit, je nach Betriebsgröße). Dafür gibt es ein zinsloses Darlehen beim Bundesamt für Familie.",
            ),
            Benefit(
                "Rentenversicherung für Pflegepersonen",
                "Die Pflegekasse zahlt unter Umständen Rentenbeiträge für die Pflegeperson: ab Pflegegrad 2, wenn mindestens 10 Stunden pro Woche an mindestens 2 Tagen gepflegt wird und die Pflegeperson höchstens 30 Stunden pro Woche arbeitet. Pflegende sind außerdem gesetzlich unfallversichert.",
                todo = "Rentenversicherung für Pflegepersonen bei der Pflegekasse melden",
            ),
        ),
    ),
    BenefitSection(
        title = "Weitere Hilfen",
        intro = "Oft vergessen, aber wichtig.",
        items = listOf(
            Benefit(
                "Schwerbehindertenausweis",
                "Beim Versorgungsamt beantragen. Je nach Grad der Behinderung und Merkzeichen gibt es Nachteilsausgleiche, z. B. Steuerfreibeträge, Parkausweis, Ermäßigungen.",
                todo = "Schwerbehindertenausweis beim Versorgungsamt prüfen/beantragen",
            ),
            Benefit(
                "Häusliche Krankenpflege vom Arzt",
                "Medizinische Pflege wie Verbandswechsel, Spritzen oder Medikamentengabe kann der Arzt verordnen. Die Krankenkasse zahlt dann, unabhängig vom Pflegegrad.",
                todo = "Beim Hausarzt häusliche Krankenpflege ansprechen",
            ),
            Benefit(
                "Zuzahlungsbefreiung",
                "Die Zuzahlungen bei Medikamenten und Hilfsmitteln sind pro Jahr auf 2 % des Bruttoeinkommens begrenzt, bei chronischen Erkrankungen auf 1 %. Quittungen sammeln und bei der Krankenkasse einreichen.",
                todo = "Zuzahlungsquittungen sammeln und Befreiung bei der Krankenkasse beantragen",
            ),
            Benefit(
                "Vorsorgevollmacht und Patientenverfügung",
                "Solange die Person entscheiden kann, sollte geklärt werden, wer für sie entscheiden darf, wenn sie es nicht mehr kann. Vordrucke und Beratung gibt es bei Betreuungsvereinen und Betreuungsbehörden.",
                todo = "Vorsorgevollmacht und Patientenverfügung klären",
            ),
        ),
    ),
)
