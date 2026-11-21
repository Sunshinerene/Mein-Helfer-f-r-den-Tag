package de.meinhelfer.tag.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Document
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SEARCH_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

enum class DocSort(val label: String) {
    NEWEST("Neueste zuerst"),
    OLDEST("Älteste zuerst"),
    TITLE("A bis Z"),
    CATEGORY("Nach Kategorie"),
}

/** Datum des Dokuments, sonst der Tag, an dem es gespeichert wurde. */
internal fun docEpochDay(d: Document): Long = d.docDate ?: (d.createdMillis / 86_400_000L)

internal fun docYear(d: Document): Int = LocalDate.ofEpochDay(docEpochDay(d)).year

/** Treffer der Suche mit Punkten (je mehr, desto besser passend) und einem Ausschnitt aus dem erkannten Text. */
data class DocHit(val doc: Document, val score: Int, val snippet: String?)

private fun snippetAround(text: String, index: Int, length: Int): String {
    val from = (index - 35).coerceAtLeast(0)
    val to = (index + length + 70).coerceAtMost(text.length)
    val part = text.substring(from, to).replace(Regex("\\s+"), " ").trim()
    return (if (from > 0) "… " else "") + part + (if (to < text.length) " …" else "")
}

/**
 * Sucht in Titel, Kategorie, Arzt/Stelle, Notizen, Datum und erkanntem Text. Mehrere Woerter muessen alle vorkommen
 * (in beliebiger Reihenfolge). Treffer im Titel zaehlen mehr als Treffer im Text.
 */
internal fun searchDocuments(items: List<Document>, doctors: List<Doctor>, query: String): List<DocHit> {
    val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return items.map { DocHit(it, 0, null) }
    return items.mapNotNull { d ->
        val doctorText = doctors.firstOrNull { it.id == d.doctorId }?.let { it.name + " " + it.specialty }.orEmpty()
        val dateText = d.docDate?.let { LocalDate.ofEpochDay(it).format(SEARCH_DATE) }.orEmpty()
        var score = 0
        var snippet: String? = null
        for (t in tokens) {
            var s = 0
            if (d.title.contains(t, ignoreCase = true)) s += 5
            if (d.category.contains(t, ignoreCase = true)) s += 3
            if (doctorText.contains(t, ignoreCase = true)) s += 3
            if (d.notes.contains(t, ignoreCase = true)) s += 2
            if (dateText.contains(t)) s += 2
            val i = d.text.indexOf(t, ignoreCase = true)
            if (i >= 0) {
                s += 1
                if (snippet == null) snippet = snippetAround(d.text, i, t.length)
            }
            if (s == 0) return@mapNotNull null
            score += s
        }
        DocHit(d, score, snippet)
    }
}

/** Suchfeld, Kategorie-Filter, Zusatzfilter (Jahr, Arzt/Stelle) und Sortierung. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DocumentFilterBar(
    query: String,
    onQuery: (String) -> Unit,
    categories: List<String>,
    counts: Map<String, Int>,
    total: Int,
    activeCategory: String?,
    onCategory: (String?) -> Unit,
    years: List<Int>,
    activeYear: Int?,
    onYear: (Int?) -> Unit,
    doctorsUsed: List<Doctor>,
    activeDoctor: Long?,
    onDoctor: (Long?) -> Unit,
    sort: DocSort,
    onSort: (DocSort) -> Unit,
    shown: Int,
    onReset: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    val filtersActive = activeCategory != null || activeYear != null || activeDoctor != null || query.isNotBlank()
    val hasMore = years.size > 1 || doctorsUsed.isNotEmpty()

    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Suchen (Titel, Text, Kategorie, Arzt, Datum)") },
            supportingText = { Text("Mehrere Wörter möglich, z. B. „Befund 2025“") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = activeCategory == null, onClick = { onCategory(null) }, label = { Text("Alle ($total)") })
            categories.forEach { c ->
                FilterChip(
                    selected = activeCategory == c,
                    onClick = { onCategory(if (activeCategory == c) null else c) },
                    label = { Text("$c (${counts[c] ?: 0})") },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { more = !more }) {
                Text(if (more) "Weniger Filter ▴" else "Sortierung und mehr Filter ▾")
            }
            if (filtersActive) TextButton(onClick = onReset) { Text("Zurücksetzen") }
        }
        if (more) {
            Text("Sortieren", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocSort.entries.forEach { s ->
                    FilterChip(selected = sort == s, onClick = { onSort(s) }, label = { Text(s.label) })
                }
            }
            if (years.size > 1) {
                Text("Jahr", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    years.forEach { y ->
                        FilterChip(selected = activeYear == y, onClick = { onYear(if (activeYear == y) null else y) }, label = { Text(y.toString()) })
                    }
                }
            }
            if (doctorsUsed.isNotEmpty()) {
                Text("Arzt / Stelle", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    doctorsUsed.forEach { d ->
                        FilterChip(
                            selected = activeDoctor == d.id,
                            onClick = { onDoctor(if (activeDoctor == d.id) null else d.id) },
                            label = { Text(d.name) },
                        )
                    }
                }
            }
            if (!hasMore) Text("Weitere Filter erscheinen, sobald es Dokumente aus mehreren Jahren oder von mehreren Stellen gibt.", style = MaterialTheme.typography.bodySmall)
        }
        if (filtersActive) {
            Text(
                "$shown von $total Dokumenten" + if (query.isNotBlank()) ", beste Treffer zuerst" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
