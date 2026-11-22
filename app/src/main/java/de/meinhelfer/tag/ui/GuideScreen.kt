package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Anleitung zum Lesen: Kategorien zum Aufklappen, Suchfeld, Einstieg in den Rundgang. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit, onStartTour: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val q = query.trim()

    // Bei einer Suche nur passende Punkte zeigen, Kategorien mit Treffern sind automatisch offen.
    val shown = GUIDE_SECTIONS.mapNotNull { section ->
        if (q.isEmpty()) {
            section to section.items
        } else {
            val hits = section.items.filter {
                it.title.contains(q, ignoreCase = true) || it.text.contains(q, ignoreCase = true)
            }
            if (hits.isEmpty()) null else section to hits
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Anleitung") },
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
                HCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Lieber zeigen lassen?", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Der Rundgang führt in wenigen Schritten mit Bildern durch die App.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(shape = buttonShape(), onClick = onStartTour) { Text("Rundgang starten") }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("In der Anleitung suchen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (shown.isEmpty()) {
                item { Text("Dazu steht nichts in der Anleitung. Versuch einen anderen Begriff.") }
            }
            items(shown, key = { it.first.id }) { (section, entries) ->
                val expanded = q.isNotEmpty() || open == section.id
                HCard(
                    onClick = { open = if (open == section.id) null else section.id },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(section.title, style = MaterialTheme.typography.titleMedium)
                                Text(section.summary, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(if (expanded) "▲" else "▼")
                        }
                        if (expanded) {
                            SpeakButton(owner = "anleitung_" + section.id) {
                                section.title + ". " + entries.joinToString(" ") { it.title + ". " + it.text }
                            }
                            entries.forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(item.text, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
