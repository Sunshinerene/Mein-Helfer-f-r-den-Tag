package de.meinhelfer.tag.summary

/**
 * Selbsteinschätzung nach dem Begutachtungsinstrument (SGB XI, Anlagen 1 und 2 zu § 15).
 * Nur ein Anhaltspunkt zur Vorbereitung: Den Pflegegrad legt die Pflegekasse nach der Begutachtung fest.
 * Bereich 5 ist vereinfacht (vier Fragen statt sechzehn Kriterien), rechnet aber mit denselben Umrechnungen.
 */
object SelfCheck {
    data class Option(val label: String, val points: Int)
    data class Question(val key: String, val text: String, val options: List<Option>)
    data class Area(val module: Int, val title: String, val questions: List<Question>)

    private fun self(w: Int) = listOf(
        Option("selbständig", 0),
        Option("überwiegend selbständig", 1 * w),
        Option("überwiegend unselbständig", 2 * w),
        Option("unselbständig", 3 * w),
    )
    private val SELF = self(1)
    private val ABILITY = listOf(
        Option("vorhanden", 0),
        Option("größtenteils vorhanden", 1),
        Option("in geringem Maße vorhanden", 2),
        Option("nicht vorhanden", 3),
    )
    private val OFTEN = listOf(
        Option("nie oder sehr selten", 0),
        Option("selten (1–3× in 2 Wochen)", 1),
        Option("häufig (mehrmals pro Woche, nicht täglich)", 3),
        Option("täglich", 5),
    )
    private val CONT = listOf(Option("entfällt oder selbständig", 0)) + SELF.drop(1)

    private fun q(key: String, text: String, options: List<Option>) = Question(key, text, options)

    val AREAS: List<Area> = listOf(
        Area(1, "Mobilität", listOf(
            q("1.1", "Im Bett die Lage wechseln", SELF),
            q("1.2", "Stabil sitzen", SELF),
            q("1.3", "Umsetzen (z. B. vom Bett auf den Stuhl)", SELF),
            q("1.4", "Sich in der Wohnung fortbewegen", SELF),
            q("1.5", "Treppen steigen", SELF),
        )),
        Area(2, "Verstehen und Sprechen", listOf(
            q("2.1", "Vertraute Personen erkennen", ABILITY),
            q("2.2", "Sich örtlich zurechtfinden", ABILITY),
            q("2.3", "Sich zeitlich zurechtfinden", ABILITY),
            q("2.4", "Sich an Wichtiges erinnern", ABILITY),
            q("2.5", "Alltagshandlungen in Schritten steuern", ABILITY),
            q("2.6", "Alltagsentscheidungen treffen", ABILITY),
            q("2.7", "Sachverhalte und Informationen verstehen", ABILITY),
            q("2.8", "Risiken und Gefahren erkennen", ABILITY),
            q("2.9", "Grundbedürfnisse mitteilen (z. B. Hunger, Schmerz)", ABILITY),
            q("2.10", "Aufforderungen verstehen", ABILITY),
            q("2.11", "Sich an einem Gespräch beteiligen", ABILITY),
        )),
        Area(3, "Verhalten und Psyche", listOf(
            q("3.1", "Unruhe, Umherlaufen, Weglaufen", OFTEN),
            q("3.2", "Unruhe in der Nacht", OFTEN),
            q("3.3", "Sich selbst verletzen", OFTEN),
            q("3.4", "Gegenstände beschädigen", OFTEN),
            q("3.5", "Körperlich aggressiv gegen andere", OFTEN),
            q("3.6", "Verbal aggressiv", OFTEN),
            q("3.7", "Rufen, Schreien, Stöhnen ohne Anlass", OFTEN),
            q("3.8", "Hilfe und Pflege abwehren", OFTEN),
            q("3.9", "Wahnvorstellungen", OFTEN),
            q("3.10", "Ängste", OFTEN),
            q("3.11", "Antriebslos, niedergeschlagen", OFTEN),
            q("3.12", "Sozial unpassendes Verhalten", OFTEN),
            q("3.13", "Andere unpassende Handlungen", OFTEN),
        )),
        Area(4, "Selbstversorgung", listOf(
            q("4.1", "Oberkörper vorn waschen", SELF),
            q("4.2", "Kopf pflegen (Kämmen, Zähne, Rasieren)", SELF),
            q("4.3", "Intimbereich waschen", SELF),
            q("4.4", "Duschen oder baden mit Haare waschen", SELF),
            q("4.5", "Oberkörper an- und auskleiden", SELF),
            q("4.6", "Unterkörper an- und auskleiden", SELF),
            q("4.7", "Essen mundgerecht zubereiten, Getränke eingießen", SELF),
            q("4.8", "Essen", self(3)),
            q("4.9", "Trinken", self(2)),
            q("4.10", "Toilette oder Toilettenstuhl benutzen", self(2)),
            q("4.11", "Folgen von Harninkontinenz bewältigen (z. B. Vorlagen, Katheter)", CONT),
            q("4.12", "Folgen von Stuhlinkontinenz bewältigen", CONT),
            q("4.13", "Ernährung über Sonde oder Infusion", listOf(
                Option("entfällt oder selbständig", 0),
                Option("teilweise (zusätzlich zum Essen)", 6),
                Option("vollständig", 3),
            )),
        )),
        Area(5, "Krankheit und Therapie (vereinfacht)", listOf(
            q("5a", "Medikamente, Spritzen, Messungen, Einreibungen, Kompressionsstrümpfe, Sauerstoff – wie oft übernimmt das jemand?", listOf(
                Option("nie oder seltener als täglich", 0),
                Option("1–3× täglich", 1),
                Option("4–8× täglich", 2),
                Option("mehr als 8× täglich", 3),
            )),
            q("5b", "Verbände und Wunden, Stoma, Katheter, Übungen zu Hause – wie oft mit Hilfe?", listOf(
                Option("nie oder seltener als wöchentlich", 0),
                Option("ein- bis mehrmals pro Woche", 1),
                Option("1–2× täglich", 2),
                Option("3× täglich oder öfter", 3),
            )),
            q("5c", "Begleitung zu Arzt und Therapie bzw. aufwendige Behandlung zu Hause", listOf(
                Option("seltener als wöchentlich", 0),
                Option("etwa 1× pro Woche", 1),
                Option("etwa 2× pro Woche", 2),
                Option("3× pro Woche oder öfter", 3),
                Option("täglich aufwendige Behandlung zu Hause (z. B. Heimbeatmung)", 6),
            )),
            q("5d", "Diät oder ärztliche Vorschriften einhalten", listOf(Option("entfällt oder selbständig", 0)) + SELF.drop(1)),
        )),
        Area(6, "Alltag und Kontakte", listOf(
            q("6.1", "Tagesablauf gestalten, sich an Änderungen anpassen", SELF),
            q("6.2", "Ruhen und schlafen", SELF),
            q("6.3", "Sich beschäftigen", SELF),
            q("6.4", "Für die Zukunft planen", SELF),
            q("6.5", "Mit Menschen im direkten Kontakt umgehen", SELF),
            q("6.6", "Kontakt zu Menschen außerhalb halten", SELF),
        )),
    )

    /** Anlage 2: Punktbereiche je Bereich und die gewichteten Punkte. */
    private val BANDS: Map<Int, Pair<List<Int>, List<Double>>> = mapOf(
        1 to (listOf(2, 4, 6, 10) to listOf(0.0, 2.5, 5.0, 7.5, 10.0)),
        2 to (listOf(2, 6, 11, 17) to listOf(0.0, 3.75, 7.5, 11.25, 15.0)),
        3 to (listOf(1, 3, 5, 7) to listOf(0.0, 3.75, 7.5, 11.25, 15.0)),
        4 to (listOf(3, 8, 19, 37) to listOf(0.0, 10.0, 20.0, 30.0, 40.0)),
        5 to (listOf(1, 2, 4, 6) to listOf(0.0, 5.0, 10.0, 15.0, 20.0)),
        6 to (listOf(1, 4, 7, 12) to listOf(0.0, 3.75, 7.5, 11.25, 15.0)),
    )

    fun weighted(module: Int, sum: Int): Double {
        val (starts, values) = BANDS.getValue(module)
        return values[starts.count { sum >= it }]
    }

    data class Result(
        /** Einzelpunkte je Bereich */
        val sums: Map<Int, Int>,
        /** Gewichtete Punkte je Bereich (2 und 3: der höhere zählt) */
        val weighted: Map<Int, Double>,
        val total: Double,
        val answered: Int,
        val questions: Int,
    ) {
        val complete get() = answered == questions
    }

    /** [answers]: Schlüssel der Frage → Index der gewählten Antwort. Fehlende Antworten zählen 0. */
    fun evaluate(answers: Map<String, Int>): Result {
        val sums = AREAS.associate { a ->
            a.module to a.questions.sumOf { q -> answers[q.key]?.let { q.options.getOrNull(it)?.points } ?: 0 }
        }
        val w = sums.mapValues { (m, s) -> weighted(m, s) }
        val cog = maxOf(w.getValue(2), w.getValue(3))
        val total = w.getValue(1) + cog + w.getValue(4) + w.getValue(5) + w.getValue(6)
        val all = AREAS.flatMap { it.questions }
        return Result(sums, w, total, all.count { answers.containsKey(it.key) }, all.size)
    }

    /** Pflegegrad nach geltendem Recht (0 = keiner). */
    fun grade(total: Double): Int = when {
        total >= 90 -> 5
        total >= 70 -> 4
        total >= 47.5 -> 3
        total >= 27 -> 2
        total >= 12.5 -> 1
        else -> 0
    }

    /** Nach den geplanten Schwellen ab 2027 (Reformentwurf, nicht beschlossen; Bewertung in den Bereichen soll sich zusätzlich ändern). */
    fun gradePlanned(total: Double): Int = when {
        total >= 90 -> 5
        total >= 70 -> 4
        total >= 50 -> 3
        total >= 30 -> 2
        total >= 15 -> 1
        else -> 0
    }

    fun encode(answers: Map<String, Int>): String = answers.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

    fun decode(text: String): Map<String, Int> = text.split(";").mapNotNull { p ->
        val k = p.substringBefore("=", "").trim()
        val v = p.substringAfter("=", "").trim().toIntOrNull()
        if (k.isNotEmpty() && v != null) k to v else null
    }.toMap()

    fun points(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else "%.2f".format(java.util.Locale.GERMANY, d).trimEnd('0').trimEnd(',')

    /** Kurzer Text zum Teilen oder für den Bericht. */
    fun report(personName: String, answers: Map<String, Int>): String = buildString {
        val r = evaluate(answers)
        append("Selbsteinschätzung zur Begutachtung").append(if (personName.isNotBlank()) " – $personName" else "").append("\n")
        append("Nur ein Anhaltspunkt, ersetzt keine Begutachtung.\n\n")
        AREAS.forEach { a ->
            append("${a.module} ${a.title}: ${r.sums[a.module]} Punkte → ${points(r.weighted.getValue(a.module))} gewichtet\n")
            a.questions.forEach { q ->
                answers[q.key]?.let { i -> q.options.getOrNull(i) }?.takeIf { it.points > 0 }?.let { o ->
                    append("   • ${q.text}: ${o.label}\n")
                }
            }
        }
        append("\nBereiche 2 und 3: der höhere Wert zählt.\n")
        append("Summe: ${points(r.total)} von 100 Punkten")
        val g = grade(r.total)
        append(" → " + if (g == 0) "kein Pflegegrad" else "etwa Pflegegrad $g").append(" (geltende Schwellen)\n")
        val p = gradePlanned(r.total)
        if (p != g) append("Nach den geplanten Schwellen ab 2027 (Entwurf): " + (if (p == 0) "kein Pflegegrad" else "Pflegegrad $p") + "\n")
        if (!r.complete) append("Noch ${r.questions - r.answered} Fragen offen.\n")
    }
}
