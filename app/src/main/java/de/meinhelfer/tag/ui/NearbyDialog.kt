package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.info.FahrdienstHit
import de.meinhelfer.tag.info.FahrdienstSearch
import de.meinhelfer.tag.info.NearbyKind
import de.meinhelfer.tag.info.NearbySearch
import kotlinx.coroutines.launch

private fun nearbyStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // keine passende App installiert
    }
}

/**
 * Einrichtungen in der Naehe suchen (Apotheke, Pflegedienst, Sanitaetshaus, Notaufnahme).
 * Gesucht wird erst nach Bestaetigung; gesendet wird nur "PLZ Ort". Treffer lassen sich als Kontakt speichern.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NearbyDialog(
    person: Person?,
    initialKind: NearbyKind = NearbyKind.Apotheke,
    onSaveContact: (Doctor) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(initialKind) }
    var confirm by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf<List<FahrdienstHit>?>(null) }
    var searchedKind by remember { mutableStateOf<NearbyKind?>(null) }
    var saved by remember { mutableStateOf(setOf<String>()) }

    val zip = person?.zip.orEmpty()
    val city = person?.city.orEmpty()
    val canSearch = (zip + city).isNotBlank()

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("In der Nähe suchen") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NearbyKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = {
                                kind = k
                                hits = null
                                error = null
                            },
                            label = { Text(k.label) },
                        )
                    }
                }
                if (canSearch) {
                    Text("Gesucht wird rund um $zip $city.".trim(), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        "Trage im Profil der Person Postleitzahl und Ort ein, dann kann ich in der Nähe suchen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = buttonShape(), enabled = canSearch && !loading, onClick = { confirm = true }) {
                        Text(if (loading) "Suche läuft …" else "Suchen")
                    }
                    OutlinedButton(shape = buttonShape(), onClick = {
                        nearbyStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(NearbySearch.mapsUrl(kind, zip, city))))
                    }) { Text("In Karten-App") }
                }
                error?.let {
                    Text(
                        "Die Suche hat nicht geklappt ($it). Versuche es später noch einmal oder nutze die Karten-App.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                hits?.let { list ->
                    if (list.isEmpty()) {
                        Text(
                            "Nichts gefunden. Die Daten von OpenStreetMap sind unvollständig, bitte in der Karten-App nachsehen.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    list.forEach { h ->
                        val key = h.name + "|" + h.phone
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                if (h.note.isNotBlank()) {
                                    Text(h.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(h.name, style = MaterialTheme.typography.titleSmall)
                                val dist = if (h.distanceKm < 900) "ca. %.1f km entfernt".format(java.util.Locale.GERMANY, h.distanceKm) else null
                                listOfNotNull(
                                    dist,
                                    h.address.takeIf { it.isNotBlank() },
                                    h.phone.takeIf { it.isNotBlank() },
                                    h.openingHours.takeIf { it.isNotBlank() }?.let { "Öffnungszeiten: $it" },
                                ).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (h.phone.isNotBlank()) {
                                        TextButton(onClick = {
                                            nearbyStart(
                                                context,
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + h.phone.filter { it.isDigit() || it == '+' })),
                                            )
                                        }) { Text("Anrufen") }
                                    }
                                    TextButton(onClick = {
                                        val dest = h.address.ifBlank { h.name + " " + zip + " " + city }
                                        nearbyStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(FahrdienstSearch.routeUrl(dest))))
                                    }) { Text("Route") }
                                    if (h.website.isNotBlank()) {
                                        TextButton(onClick = {
                                            val u = if (h.website.startsWith("http")) h.website else "https://${h.website}"
                                            nearbyStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                                        }) { Text("Webseite") }
                                    }
                                    TextButton(
                                        enabled = key !in saved,
                                        onClick = {
                                            onSaveContact(
                                                Doctor(
                                                    name = h.name,
                                                    specialty = (searchedKind ?: kind).contactType,
                                                    phone = h.phone,
                                                    email = h.email,
                                                    address = h.address,
                                                ),
                                            )
                                            saved = saved + key
                                        },
                                    ) { Text(if (key in saved) "Gespeichert" else "Als Kontakt speichern") }
                                }
                            }
                        }
                    }
                    if (list.isNotEmpty()) {
                        Text(
                            "Daten aus OpenStreetMap, unvollständig und ohne Gewähr. Bitte vor Besuch oder Nutzung kurz anrufen und Öffnungszeiten prüfen. Für den Notfall gilt immer die 112.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } },
    )

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("In der Nähe suchen?") },
            text = {
                Text(
                    "Dafür wird nur dieser Ort an OpenStreetMap (Nominatim und Overpass) gesendet:\n\n" +
                        "$zip $city\n\n" +
                        "Nicht gesendet werden Name, Straße oder sonstige Daten.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    loading = true
                    error = null
                    val k = kind
                    scope.launch {
                        NearbySearch.search(k, zip, city)
                            .onSuccess {
                                hits = it
                                searchedKind = k
                            }
                            .onFailure { error = it.message ?: "Fehler" }
                        loading = false
                    }
                }) { Text("Suchen") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
        )
    }
}
