package de.meinhelfer.tag.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Person

private const val SOURCE_URL = "https://www.familienratgeber.de/rechte-leistungen/pflege/leistungen-der-pflegeversicherung"

/** Wegweiser: Leistungen und Antraege je Pflegegrad, mit Uebernahme auf die To-do-Liste. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WegweiserScreen(person: Person?, onAddTodo: (String) -> Unit, onBack: () -> Unit) {
    val personName = person?.name
    val careLevel = person?.careLevel ?: 0
    val context = LocalContext.current
    // 0 = alle Pflegegrade zeigen
    var level by remember { mutableStateOf(careLevel) }
    val added = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wegweiser Leistungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Welche Hilfe steht zu?", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (careLevel in 1..5) {
                                "Für ${personName ?: "die Person"} ist Pflegegrad $careLevel eingetragen. Unten siehst du die Beträge dafür. Du kannst auch andere Pflegegrade ansehen."
                            } else {
                                "Für ${personName ?: "die Person"} ist noch kein Pflegegrad eingetragen. Wähle einen Pflegegrad oder sieh dir alle an. Unter „Erste Schritte“ steht, wie man ihn beantragt."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("Pflegegrad ansehen", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = level == 0, onClick = { level = 0 }, label = { Text("Alle") })
                            (1..5).forEach { l ->
                                FilterChip(selected = level == l, onClick = { level = l }, label = { Text("$l") })
                            }
                        }
                        Text(
                            "Mit „+ Als Aufgabe merken“ übernimmst du einen Schritt als Aufgabe auf die Heute-Seite.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val kasse = person?.insurer.orEmpty()
                        val place = place(person)
                        Text(
                            if (kasse.isNotBlank()) {
                                "Pflegekasse: $kasse" + if (place.isNotBlank()) " · $place" else ""
                            } else {
                                "Trage im Profil Krankenkasse und Ort ein, dann werden die Links zu Stellen und Vordrucken genauer."
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                openLink(context, WegLink("", LinkKind.MAPS, "{kasse} Geschäftsstelle {ort}"), person)
                            }) { Text("Meine Pflegekasse (Karte)") }
                            TextButton(onClick = {
                                openLink(context, WegLink("", LinkKind.SEARCH, "Antrag Pflegegrad Pflegeleistungen {kasse} Formular"), person)
                            }) { Text("Antrag Pflegegrad (Formular)") }
                        }
                    }
                }
            }

            WEGWEISER_SECTIONS.forEach { section ->
                item(key = "h_${section.title}") {
                    Column {
                        Text(section.title, style = MaterialTheme.typography.titleLarge)
                        Text(section.intro, style = MaterialTheme.typography.bodySmall)
                    }
                }
                section.items.forEach { b ->
                    item(key = "b_${b.title}") {
                        HCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(b.title, style = MaterialTheme.typography.titleMedium)
                                b.amounts?.let { amounts -> AmountLines(amounts, level) }
                                Text(b.text, style = MaterialTheme.typography.bodyMedium)
                                WEGWEISER_LINKS[b.title]?.let { links ->
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        links.forEach { link ->
                                            TextButton(onClick = { openLink(context, link, person) }) {
                                                Text((if (link.kind == LinkKind.URL) "↗ " else "🔍 ") + link.label)
                                            }
                                        }
                                    }
                                }
                                b.todo?.let { todo ->
                                    val isAdded = todo in added
                                    TextButton(onClick = {
                                        onAddTodo(todo)
                                        added.add(todo)
                                    }, enabled = !isAdded) {
                                        Text(if (isAdded) "✓ Als Aufgabe gemerkt" else "+ Als Aufgabe merken")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Stand der Beträge: $WEGWEISER_STAND (gesetzliche Pflegeversicherung, Deutschland). Angaben ohne Gewähr, Gesetze und Beträge ändern sich. " +
                            "Verbindlich sind nur die Auskünfte der Pflegekasse, des Pflegestützpunkts oder einer Beratungsstelle. Das ist keine Rechtsberatung.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { openUrl(context, SOURCE_URL) }) { Text("Quelle der Beträge öffnen (familienratgeber.de)") }
                    Text(
                        "Links mit Lupe öffnen deinen Browser oder deine Karten-App mit einer Suche. Dabei werden nur Krankenkasse und PLZ/Ort aus dem Profil eingesetzt, nie Name, Straße oder Versichertennummer. " +
                            "Vordrucke gibt es meist auf der Internetseite deiner Pflegekasse. Prüfe, dass du das aktuelle Formular der richtigen Stelle nimmst.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountLines(amounts: List<String>, level: Int) {
    if (level in 1..5) {
        val value = amounts.getOrNull(level - 1).orEmpty()
        Text(
            if (value.isBlank()) "Pflegegrad $level: gibt es nicht" else "Pflegegrad $level: $value",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        amounts.forEachIndexed { i, v ->
            Text(
                "Pflegegrad ${i + 1}: " + v.ifBlank { "–" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}

private fun place(person: Person?): String =
    listOfNotNull(person?.zip?.takeIf { it.isNotBlank() }, person?.city?.takeIf { it.isNotBlank() }).joinToString(" ")

/** Setzt Krankenkasse und Ort in die Vorlage ein und oeffnet Suche, Karte oder Webseite. */
private fun openLink(context: Context, link: WegLink, person: Person?) {
    val kasse = person?.insurer?.takeIf { it.isNotBlank() } ?: "Pflegekasse"
    val query = link.target.replace("{kasse}", kasse).replace("{ort}", place(person)).replace(Regex("\\s+"), " ").trim()
    val uri = when (link.kind) {
        LinkKind.URL -> Uri.parse(link.target)
        LinkKind.SEARCH -> Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
        LinkKind.MAPS -> Uri.parse("geo:0,0?q=" + Uri.encode(query))
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: Exception) {
        // Karten-App fehlt: im Browser suchen
        if (link.kind == LinkKind.MAPS) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query))))
            } catch (_: Exception) {
            }
        }
    }
}
