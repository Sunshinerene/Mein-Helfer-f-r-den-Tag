package de.meinhelfer.tag.info

import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person

/** Ein vorgeschlagenes Thema: Beschriftung und Suchwort. */
data class TopicSuggestion(val label: String, val query: String)

/**
 * Schlaegt Themen vor, die zu den eigenen Angaben passen (Pflegegrad, Medikamente, Dokumente, Notizen).
 * Alles wird nur auf dem Handy ausgewertet. Es ist keine Diagnose, nur ein Hinweis auf moegliche Themen.
 */
object PersonalTopics {
    private val RULES: List<Pair<TopicSuggestion, Regex>> = listOf(
        TopicSuggestion("Demenz", "Demenz Pflege Angehörige") to
            Regex("demenz|alzheimer|donepezil|memantin|rivastigmin|galantamin", RegexOption.IGNORE_CASE),
        TopicSuggestion("Diabetes", "Diabetes im Alter Pflege") to
            Regex("diabetes|metformin|insulin|glibenclamid|sitagliptin", RegexOption.IGNORE_CASE),
        TopicSuggestion("Parkinson", "Parkinson Pflege Angehörige") to
            Regex("parkinson|levodopa|l-dopa|madopar|carbidopa|pramipexol|ropinirol", RegexOption.IGNORE_CASE),
        TopicSuggestion("Herz und Blutdruck", "Herzschwäche Bluthochdruck im Alter") to
            Regex("herzinsuffizienz|herzschwäche|bluthochdruck|hypertonie|bisoprolol|ramipril|candesartan|amlodipin|metoprolol|torasemid|furosemid", RegexOption.IGNORE_CASE),
        TopicSuggestion("Schlaganfall, Blutverdünner", "Schlaganfall Pflege Blutverdünner") to
            Regex("schlaganfall|apoplex|marcumar|phenprocoumon|apixaban|eliquis|rivaroxaban|xarelto|clopidogrel", RegexOption.IGNORE_CASE),
        TopicSuggestion("Osteoporose, Stürze", "Sturzprävention Osteoporose im Alter") to
            Regex("osteoporose|alendron|denosumab|sturz", RegexOption.IGNORE_CASE),
        TopicSuggestion("Depression im Alter", "Depression im Alter Angehörige") to
            Regex("depression|citalopram|sertralin|escitalopram|mirtazapin|venlafaxin", RegexOption.IGNORE_CASE),
        TopicSuggestion("Schmerzen im Alter", "Schmerzen im Alter Schmerztherapie") to
            Regex("schmerz|naproxen|ibuprofen|diclofenac|tilidin|tramadol|metamizol|novalgin|apranax", RegexOption.IGNORE_CASE),
        TopicSuggestion("COPD, Atemnot", "COPD Pflege Angehörige") to
            Regex("copd|asthma|salbutamol|tiotropium|formoterol", RegexOption.IGNORE_CASE),
        TopicSuggestion("Krebs und Pflege", "Krebs Pflege zu Hause Angehörige") to
            Regex("krebs|karzinom|tumor|chemotherapie|onkolog", RegexOption.IGNORE_CASE),
        TopicSuggestion("Beruhigungs- und Schlafmittel", "Beruhigungsmittel Schlafmittel ältere Menschen Risiken") to
            Regex("alprazolam|lorazepam|diazepam|tavor|zopiclon|zolpidem|bromazepam|oxazepam|benzodiazepin", RegexOption.IGNORE_CASE),
        TopicSuggestion("Inkontinenz", "Inkontinenz Pflege Hilfsmittel") to
            Regex("inkontinenz|blasenschwäche|oxybutynin|solifenacin", RegexOption.IGNORE_CASE),
        TopicSuggestion("Nierenschwäche", "Niereninsuffizienz Alltag Pflege") to
            Regex("niereninsuffizienz|nierenschwäche|dialyse", RegexOption.IGNORE_CASE),
    )

    fun suggest(person: Person?, meds: List<Medication>, docs: List<Document>): List<TopicSuggestion> {
        val result = mutableListOf<TopicSuggestion>()
        if (person != null) {
            if (person.careLevel > 0) {
                result.add(TopicSuggestion("Pflegegrad ${person.careLevel}", "Pflegegrad ${person.careLevel} Leistungen"))
            } else {
                result.add(TopicSuggestion("Pflegegrad beantragen", "Pflegegrad beantragen Begutachtung"))
            }
            if (person.insurer.isNotBlank()) {
                result.add(TopicSuggestion("Pflegekasse ${person.insurer}", "${person.insurer} Pflegeversicherung"))
            }
        }
        val haystack = buildString {
            person?.important?.let { append(it).append('\n') }
            meds.forEach { append(it.name).append(' ').append(it.notes).append('\n') }
            docs.forEach { append(it.title).append(' ').append(it.category).append(' ').append(it.notes).append(' ').append(it.text.take(20_000)).append('\n') }
        }
        RULES.forEach { (topic, regex) -> if (regex.containsMatchIn(haystack)) result.add(topic) }
        return result.distinctBy { it.label }
    }
}
