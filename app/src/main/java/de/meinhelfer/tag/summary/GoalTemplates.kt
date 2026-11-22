package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.GoalKind

/** Ein Schritt einer Vorlage; [days] = bis wann, gerechnet ab dem Anlegen (optional). */
data class TemplateStep(val title: String, val detail: String = "", val days: Int? = null)

data class GoalTemplate(
    val title: String,
    val kind: GoalKind,
    /** true = Ziel fuer die betreute Person, false = eigenes Ziel */
    val forPerson: Boolean,
    val steps: List<TemplateStep>,
    /** Vorschlag fuer den Wunschtermin in Tagen */
    val targetDays: Int? = null,
)

/**
 * Vorlagen fuer haeufige Ziele. Nur ein Anhaltspunkt: Ablaeufe und Fristen koennen je nach Kasse,
 * Bundesland und Einzelfall abweichen. Massgeblich sind die Angaben der zustaendigen Stelle und im Bescheid.
 */
object GoalTemplates {
    const val NOTE = "Vorlage als Anhaltspunkt. Abläufe können je nach Kasse, Bundesland und Einzelfall abweichen. Maßgeblich sind die Angaben der zuständigen Stelle und die Fristen im Bescheid."

    val all = listOf(
        GoalTemplate(
            "Pflegegrad beantragen", GoalKind.Antrag, true,
            listOf(
                TemplateStep("Antrag bei der Pflegekasse stellen", "Formlos per Telefon, E-Mail oder Brief bei der Pflegekasse (sitzt bei der Krankenkasse). Datum notieren: Leistungen gibt es frühestens ab dem Monat des Antrags.", 0),
                TemplateStep("Pflegeberatung annehmen", "Nach dem Antrag bietet die Pflegekasse eine Pflegeberatung an. Auch ein Pflegestützpunkt hilft.", 10),
                TemplateStep("Pflegetagebuch führen", "Ein bis zwei Wochen notieren, wobei Hilfe nötig ist (Waschen, Essen, Medikamente, Orientierung, nachts). In der App: Heute → Pflegetagebuch, mit PDF-Bericht.", 14),
                TemplateStep("Unterlagen sammeln", "Arzt- und Entlassberichte, Medikamentenplan, Hilfsmittel, Liste der behandelnden Ärzte.", 14),
                TemplateStep("Begutachtung vorbereiten", "Termin notieren, Pflegeperson dabei, Pflegetagebuch und Fragen bereitlegen, nichts schönreden.", 18),
                TemplateStep("Begutachtung durch den Medizinischen Dienst", "Bei Privatversicherten kommt Medicproof.", 21),
                TemplateStep("Bescheid prüfen", "In der Regel entscheidet die Pflegekasse innerhalb von 25 Arbeitstagen nach dem Antrag. Gutachten lesen.", 40),
                TemplateStep("Bei Bedarf Widerspruch einlegen", "Widerspruch ist innerhalb eines Monats nach Erhalt des Bescheids möglich. Hilfe: Pflegestützpunkt, Sozialverband.", 50),
            ),
            targetDays = 50,
        ),
        GoalTemplate(
            "Höherstufung des Pflegegrads", GoalKind.Antrag, true,
            listOf(
                TemplateStep("Veränderungen festhalten", "Zwei Wochen Pflegetagebuch (Heute → Pflegetagebuch): Was ist schwieriger geworden, wo braucht es mehr Hilfe?", 14),
                TemplateStep("Ärztliche Berichte zur Verschlechterung sammeln", "Arztbriefe, Entlassberichte, neue Diagnosen oder Hilfsmittel.", 14),
                TemplateStep("Antrag auf Höherstufung stellen", "Formlos bei der Pflegekasse, Datum notieren.", 16),
                TemplateStep("Begutachtung vorbereiten", "Pflegeperson dabei, Pflegetagebuch bereitlegen.", 25),
                TemplateStep("Begutachtung", "", 30),
                TemplateStep("Bescheid prüfen, bei Bedarf Widerspruch", "Widerspruch innerhalb eines Monats nach Erhalt des Bescheids.", 55),
            ),
            targetDays = 55,
        ),
        GoalTemplate(
            "Hilfsmittel beantragen", GoalKind.Antrag, true,
            listOf(
                TemplateStep("Bedarf klären", "Was genau wird gebraucht (z. B. Rollator, Pflegebett, Toilettenstuhl) und wofür?", 3),
                TemplateStep("Verordnung beim Arzt holen", "Für viele Hilfsmittel nötig. Pflegehilfsmittel gibt es teils auch direkt über die Pflegekasse.", 7),
                TemplateStep("Sanitätshaus: Beratung und Kostenvoranschlag", "", 12),
                TemplateStep("Genehmigung der Kasse abwarten", "Bei Ablehnung: Bescheid prüfen, Widerspruch möglich.", 25),
                TemplateStep("Lieferung und Einweisung", "Sich die Handhabung zeigen lassen.", 30),
                TemplateStep("Im Alltag ausprobieren", "Passt es? Sonst beim Sanitätshaus melden.", 40),
            ),
            targetDays = 40,
        ),
        GoalTemplate(
            "Schwerbehindertenausweis beantragen", GoalKind.Antrag, true,
            listOf(
                TemplateStep("Zuständige Stelle finden", "Versorgungsamt bzw. Amt für Soziales, je nach Bundesland verschieden.", 3),
                TemplateStep("Antrag ausfüllen", "Oft auch online möglich. Alle Beschwerden und Einschränkungen angeben.", 10),
                TemplateStep("Ärztliche Unterlagen beifügen", "Befunde und Berichte, behandelnde Ärzte mit Anschrift angeben.", 12),
                TemplateStep("Bescheid prüfen", "Grad der Behinderung und Merkzeichen kontrollieren.", 90),
                TemplateStep("Bei Bedarf Widerspruch", "Frist steht im Bescheid.", 110),
                TemplateStep("Ausweis und Nachteilsausgleiche nutzen", "", 120),
            ),
            targetDays = 120,
        ),
        GoalTemplate(
            "Entlastungsbetrag nutzen", GoalKind.Entlastung, true,
            listOf(
                TemplateStep("Anerkannte Angebote in der Nähe finden", "Liste bei der Pflegekasse oder im Pflegestützpunkt anfragen.", 5),
                TemplateStep("Angebot anfragen und Termin vereinbaren", "", 10),
                TemplateStep("Hilfe ausprobieren", "", 21),
                TemplateStep("Rechnungen bei der Pflegekasse einreichen", "Oder eine Abtretung an den Anbieter vereinbaren.", 30),
                TemplateStep("Restbetrag im Blick behalten", "Nicht genutzte Beträge verfallen nach einer Frist, bei der Pflegekasse nachfragen.", 35),
            ),
            targetDays = 35,
        ),
        GoalTemplate(
            "Auszeit planen (Verhinderungspflege)", GoalKind.Entlastung, true,
            listOf(
                TemplateStep("Zeitraum für die Auszeit festlegen", "", 3),
                TemplateStep("Vertretung finden", "Familie, Nachbarn, Pflegedienst oder Kurzzeitpflege.", 14),
                TemplateStep("Verhinderungspflege bei der Pflegekasse beantragen", "", 18),
                TemplateStep("Übergabe vorbereiten", "Medikamente, Termine, Kontakte, Besonderheiten aufschreiben.", 25),
                TemplateStep("Auszeit nehmen", "Wirklich abschalten.", 30),
                TemplateStep("Belege einreichen", "", 40),
            ),
            targetDays = 40,
        ),
        GoalTemplate(
            "Mehr Bewegung im Alltag", GoalKind.Gesundheit, false,
            listOf(
                TemplateStep("Ziel festlegen", "Z. B. dreimal pro Woche 20 Minuten gehen. Bei Beschwerden oder Vorerkrankungen vorher ärztlich abklären.", 1),
                TemplateStep("Feste Zeiten in den Tag einplanen", "Am besten an Termine koppeln, die es sowieso gibt.", 3),
                TemplateStep("Erste Woche durchziehen", "", 10),
                TemplateStep("Nach zwei Wochen Bilanz ziehen", "Was klappt, was nicht? Anpassen statt aufgeben.", 17),
                TemplateStep("Vier Wochen geschafft", "", 31),
            ),
            targetDays = 31,
        ),
        GoalTemplate(
            "Eigener Gesundheits-Check", GoalKind.Gesundheit, false,
            listOf(
                TemplateStep("Termin beim Hausarzt vereinbaren", "", 3),
                TemplateStep("Fragen und Beschwerden notieren", "Auch Schlaf, Rücken, Belastung durch die Pflege.", 10),
                TemplateStep("Termin wahrnehmen", "", 14),
                TemplateStep("Ergebnis und nächste Schritte notieren", "", 15),
            ),
            targetDays = 15,
        ),
        GoalTemplate(
            "Wohnung anpassen (barrierearm)", GoalKind.Alltag, true,
            listOf(
                TemplateStep("Beratung zur Wohnraumanpassung", "Pflegestützpunkt oder Wohnberatung vor Ort.", 7),
                TemplateStep("Kostenvoranschläge einholen", "", 21),
                TemplateStep("Zuschuss bei der Pflegekasse beantragen", "Möglichst vor Beginn der Arbeiten.", 25),
                TemplateStep("Genehmigung abwarten", "", 45),
                TemplateStep("Umbau durchführen", "", 70),
                TemplateStep("Rechnung einreichen", "", 80),
            ),
            targetDays = 80,
        ),
    )
}
