package de.meinhelfer.tag.summary

/**
 * Sunnys Sprachschatz: je Thema mehrere Formulierungen mit gleichem Sinn, damit sie nicht immer gleich klingt.
 * Platzhalter: {name} = Name des Assistenten, {n} = Anzahl. Wächst abends aus den Gesprächen des Tages.
 */
class Phrasebook(private val map: MutableMap<String, MutableList<String>> = mutableMapOf()) {

    fun themes(): Map<String, List<String>> = map

    /** Formulierung zum Thema, reihum nach [turn]; ohne Eintrag der [default]. */
    fun pick(theme: String, default: String, turn: Int, name: String = "", n: Int? = null): String {
        val list = (listOf(default) + map[theme].orEmpty()).distinctBy { norm(it) }
        val chosen = list[Math.floorMod(turn, list.size)]
        return chosen.replace("{name}", name).let { if (n != null) it.replace("{n}", n.toString()) else it }
    }

    /** Neue Sätze aufnehmen; Doppeltes (gleicher Wortlaut oder fast gleich) wird übersprungen. Gibt die Zahl der neuen zurück. */
    fun merge(theme: String, phrases: List<String>, max: Int = MAX_PER_THEME): Int {
        val list = map.getOrPut(theme) { mutableListOf() }
        var added = 0
        phrases.map { it.trim() }.filter { it.length in 3..220 && usable(it) }.forEach { p ->
            if (list.none { similar(it, p) } && list.size < max) {
                list += p
                added++
            }
        }
        return added
    }

    fun encode(): String = map.entries.flatMap { (t, l) -> l.map { "$t\t${it.replace('\n', ' ')}" } }.joinToString("\n")

    companion object {
        const val MAX_PER_THEME = 12

        /** Themen mit Beschreibung (für die KI) und Grundbestand – sofort abwechslungsreich, auch ohne KI. */
        val THEMES: Map<String, Pair<String, List<String>>> = linkedMapOf(
            "gruss_morgen" to ("Begrüßung am Morgen, mit {name}" to listOf("Guten Morgen, hier ist {name}.", "Einen guten Start in den Tag, ich bin's, {name}.", "Morgen! {name} hier.")),
            "gruss_tag" to ("Begrüßung tagsüber, mit {name}" to listOf("Hallo, hier ist {name}.", "Hallo, ich bin's, {name}.", "Hi, {name} hier.")),
            "gruss_abend" to ("Begrüßung am Abend, mit {name}" to listOf("Guten Abend, hier ist {name}.", "Schönen Abend, ich bin's, {name}.", "Hallo am Abend, {name} hier.")),
            "nichts_an" to ("Gerade steht nichts an" to listOf("Gerade steht laut App nichts an.", "Im Moment ist nichts zu erledigen.", "Gerade ist alles ruhig, es steht nichts an.")),
            "vorschlaege" to ("Ankündigen, dass es {n} Vorschläge gibt" to listOf(
                "Ich habe {n} Vorschläge für dich, schau gern in meine Karte.",
                "In meiner Karte warten {n} Vorschläge auf dich.",
                "Ich hätte da {n} Ideen für dich, sie stehen in meiner Karte.",
            )),
            "vorschlag_eins" to ("Ankündigen, dass es einen Vorschlag gibt" to listOf(
                "Ich habe einen Vorschlag für dich, schau gern in meine Karte.",
                "In meiner Karte wartet ein Vorschlag auf dich.",
            )),
            "offline" to ("Einleitung, wenn keine Verbindung zur KI besteht und Sunny selbst vorliest" to listOf(
                "Ohne Verbindung sage ich dir, was ich hier sehe.",
                "Ich bin gerade offline, aber das hier sehe ich:",
                "Kurz und ohne Internet:",
            )),
            "lob" to ("Kurzes Lob, wenn etwas erledigt ist" to listOf("Das hast du gut gemacht.", "Super, das ist erledigt.", "Prima, eins weniger.")),
            "ermutigung" to ("Kurze Ermutigung für pflegende Angehörige" to listOf(
                "Du machst das gut, auch wenn es keiner laut sagt.",
                "Denk auch an dich, eine kleine Pause zählt.",
                "Schritt für Schritt, du schaffst das.",
            )),
            "abschied" to ("Kurzer Abschluss" to listOf("Ich bin da, wenn du mich brauchst.", "Melde dich, wenn ich helfen kann.", "Bis gleich.")),
        )

        fun withDefaults(): Phrasebook = Phrasebook().also { b -> THEMES.forEach { (t, v) -> b.merge(t, v.second) } }

        fun decode(text: String): Phrasebook {
            val b = withDefaults()
            text.lines().forEach { l ->
                val t = l.substringBefore('\t', "")
                val p = l.substringAfter('\t', "")
                if (t.isNotBlank() && p.isNotBlank()) b.merge(t, listOf(p))
            }
            return b
        }

        private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}{} ]"), "").replace(Regex("\\s+"), " ").trim()

        /** Fast gleich: gleicher Wortlaut ohne Satzzeichen oder sehr hohe Wortüberschneidung. */
        fun similar(a: String, b: String): Boolean {
            val x = norm(a)
            val y = norm(b)
            if (x == y) return true
            val wa = x.split(' ').toSet()
            val wb = y.split(' ').toSet()
            val inter = wa.intersect(wb).size.toDouble()
            return inter / (wa.union(wb).size.coerceAtLeast(1)) >= 0.8
        }

        /** Keine Ziffern (außer im Platzhalter), keine Klammern – nur allgemeine, gut sprechbare Sätze. */
        fun usable(s: String) = !Regex("\\d").containsMatchIn(s) && !s.contains('[') && !s.contains("http")
    }
}
