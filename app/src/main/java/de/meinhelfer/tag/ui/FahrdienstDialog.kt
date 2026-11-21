package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.info.FahrdienstHit
import de.meinhelfer.tag.info.FahrdienstSearch
import kotlinx.coroutines.launch

private val FIXED_KEYS = listOf("fahrdienst", "taxi", "krankentransport", "krankenfahrt", "patientenfahr", "behindertenfahr")

/** Ist der Kontakt ein fester Fahrdienst (Art oder Name enthaelt z. B. "Fahrdienst" oder "Taxi")? */
internal fun Doctor.isFahrdienst(): Boolean {
    val text = (specialty + " " + name).lowercase()
    return FIXED_KEYS.any { it in text }
}

/** Findet der Termin ausserhalb der Wohnung statt? Grobe Einschaetzung, der Nutzer entscheidet selbst. */
internal fun isOutsideHome(a: Appointment, doctor: Doctor?): Boolean {
    val text = (a.title + " " + a.place + " " + a.notes).lowercase()
    val inside = listOf("hausbesuch", "zuhause", "zu hause", "daheim", "telefon", "video", "online")
    if (inside.any { it in text }) return false
    if (a.place.isNotBlank()) return true
    if (doctor == null) return false
    return !doctor.specialty.lowercase().contains("pflegedienst")
}

private val EXTRAS = listOf("Rollator", "Rollstuhl", "Begleitperson", "Hilfe beim Einsteigen")

private fun buildRequest(
    whenText: String,
    place: String,
    doctor: Doctor?,
    person: Person?,
    extras: Set<String>,
    returnTrip: Boolean,
): String {
    val pickup = person?.let {
        listOf(it.street, listOf(it.zip, it.city).filter { s -> s.isNotBlank() }.joinToString(" "))
            .filter { s -> s.isNotBlank() }.joinToString(", ")
    }.orEmpty().ifBlank { "(Adresse bitte ergänzen)" }
    val target = listOf(place, doctor?.address.orEmpty()).firstOrNull { it.isNotBlank() }
        ?: "(Ziel bitte ergänzen)"
    val sb = StringBuilder("Guten Tag, ich möchte eine Fahrt anfragen.\n")
    sb.append("Abholung: ").append(pickup).append('\n')
    sb.append("Ziel: ").append(target)
    doctor?.let { sb.append(" (").append(it.name).append(')') }
    sb.append('\n')
    sb.append("Termin: ").append(whenText).append('\n')
    sb.append("Rückfahrt: ").append(if (returnTrip) "ja, bitte nach dem Termin" else "nein").append('\n')
    if (extras.isNotEmpty()) sb.append("Hinweise: ").append(extras.joinToString(", ")).append('\n')
    sb.append("Bitte um Rückmeldung zu Verfügbarkeit und Preis. Danke!")
    return sb.toString()
}

private fun startSafe(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // keine passende App installiert
    }
}

private fun dial(context: Context, phone: String) =
    startSafe(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })))

private fun sms(context: Context, phone: String, text: String) =
    startSafe(
        context,
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone.filter { it.isDigit() || it == '+' }))
            .putExtra("sms_body", text),
    )

private fun mail(context: Context, email: String, subject: String, text: String) =
    startSafe(
        context,
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text),
    )

private fun openUrl(context: Context, url: String) {
    val fixed = if (url.startsWith("http")) url else "https://$url"
    startSafe(context, Intent(Intent.ACTION_VIEW, Uri.parse(fixed)))
}

/** Fahrdienst fuer einen Termin: feste Fahrdienste zuerst, dann Suche im Umkreis, dazu Infos zur Krankenfahrt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FahrdienstDialog(
    title: String,
    whenText: String,
    place: String,
    doctor: Doctor?,
    doctors: List<Doctor>,
    person: Person?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var extras by remember { mutableStateOf(emptySet<String>()) }
    var returnTrip by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmSearch by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf<List<FahrdienstHit>?>(null) }

    val fixed = doctors.filter { it.isFahrdienst() }
    val request = buildRequest(whenText, place, doctor, person, extras, returnTrip)
    val subject = "Fahrtanfrage"
    val zip = person?.zip.orEmpty()
    val city = person?.city.orEmpty()
    val canSearch = (zip + city).isNotBlank()
    val destination = listOf(place, doctor?.address.orEmpty()).firstOrNull { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Fahrdienst") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title + "\n" + whenText, style = MaterialTheme.typography.titleSmall)

                Text("Was soll in der Anfrage stehen?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = returnTrip,
                        onClick = { returnTrip = !returnTrip },
                        label = { Text("Rückfahrt") },
                    )
                    EXTRAS.forEach { e ->
                        FilterChip(
                            selected = e in extras,
                            onClick = { extras = if (e in extras) extras - e else extras + e },
                            label = { Text(e) },
                        )
                    }
                }
                Text(
                    "Die Anfrage enthält Abholadresse, Ziel und Termin, aber nie den Namen der Person. " +
                        "Du siehst den Text vor dem Senden und kannst ihn dort ändern.",
                    style = MaterialTheme.typography.bodySmall,
                )

                // ---- feste Fahrdienste
                Text("Deine Fahrdienste", style = MaterialTheme.typography.titleSmall)
                if (fixed.isEmpty()) {
                    Text(
                        "Noch keiner hinterlegt. Lege unter Kontakte einen Kontakt mit der Art „Fahrdienst“ an, " +
                            "dann steht er hier immer ganz oben.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                fixed.forEach { d ->
                    ServiceCard(
                        title = d.name,
                        lines = listOfNotNull(
                            d.phone.takeIf { it.isNotBlank() },
                            d.email.takeIf { it.isNotBlank() },
                            d.address.takeIf { it.isNotBlank() },
                        ),
                        badge = "Fester Fahrdienst",
                    ) {
                        if (d.phone.isNotBlank()) {
                            TextButton(onClick = { dial(context, d.phone) }) { Text("Anrufen") }
                            TextButton(onClick = { sms(context, d.phone, request) }) { Text("SMS") }
                        }
                        if (d.email.isNotBlank()) {
                            TextButton(onClick = { mail(context, d.email, subject, request) }) { Text("E-Mail") }
                        }
                    }
                }

                // ---- Umkreissuche
                Text("Fahrdienste in der Nähe", style = MaterialTheme.typography.titleSmall)
                if (canSearch) {
                    Text(
                        "Gesucht wird rund um $zip $city.".trim(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Trage im Profil der Person Postleitzahl und Ort ein, dann kann ich in der Nähe suchen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = canSearch && !loading,
                        onClick = { confirmSearch = true },
                    ) { Text(if (loading) "Suche läuft …" else "In der Nähe suchen") }
                    OutlinedButton(onClick = { openUrl(context, FahrdienstSearch.mapsUrl(zip, city)) }) {
                        Text("Karte öffnen")
                    }
                }
                error?.let {
                    Text(
                        "Die Suche hat nicht geklappt ($it). Bitte später noch einmal versuchen oder die Karte nutzen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                hits?.let { list ->
                    if (list.isEmpty()) {
                        Text(
                            "Nichts gefunden. Die Daten stammen aus OpenStreetMap und sind nicht vollständig. " +
                                "Probiere die Karte oder frage bei der Pflegekasse bzw. dem Pflegestützpunkt nach.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            "Quelle: OpenStreetMap, unvollständig und ohne Gewähr. Bitte immer telefonisch bestätigen.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    list.forEach { h ->
                        ServiceCard(
                            title = h.name,
                            lines = listOfNotNull(
                                "ca. " + String.format(java.util.Locale.GERMANY, "%.1f", h.distanceKm) + " km entfernt",
                                h.phone.takeIf { it.isNotBlank() },
                                h.address.takeIf { it.isNotBlank() },
                                h.openingHours.takeIf { it.isNotBlank() }?.let { "Zeiten: $it" },
                                when (h.wheelchair) {
                                    "yes" -> "Rollstuhl: ja"
                                    "limited" -> "Rollstuhl: eingeschränkt"
                                    "no" -> "Rollstuhl: nein"
                                    else -> null
                                },
                            ),
                            badge = null,
                        ) {
                            if (h.phone.isNotBlank()) {
                                TextButton(onClick = { dial(context, h.phone) }) { Text("Anrufen") }
                                TextButton(onClick = { sms(context, h.phone, request) }) { Text("SMS") }
                            }
                            if (h.email.isNotBlank()) {
                                TextButton(onClick = { mail(context, h.email, subject, request) }) { Text("E-Mail") }
                            }
                            if (h.website.isNotBlank()) {
                                TextButton(onClick = { openUrl(context, h.website) }) { Text("Webseite") }
                            }
                        }
                    }
                }

                // ---- Weg und Info
                if (destination != null) {
                    TextButton(onClick = { openUrl(context, FahrdienstSearch.routeUrl(destination)) }) {
                        Text("Route zum Ziel anzeigen")
                    }
                }
                TextButton(onClick = { showInfo = !showInfo }) {
                    Text(if (showInfo) "Infos zur Krankenfahrt ausblenden" else "Infos zur Krankenfahrt")
                }
                if (showInfo) {
                    Text(KRANKENFAHRT_INFO, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } },
    )

    if (confirmSearch) {
        AlertDialog(
            onDismissRequest = { confirmSearch = false },
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
                    confirmSearch = false
                    loading = true
                    error = null
                    scope.launch {
                        FahrdienstSearch.search(zip, city)
                            .onSuccess { hits = it }
                            .onFailure { error = it.message ?: "Fehler" }
                        loading = false
                    }
                }) { Text("Suchen") }
            },
            dismissButton = { TextButton(onClick = { confirmSearch = false }) { Text("Abbrechen") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceCard(
    title: String,
    lines: List<String>,
    badge: String?,
    buttons: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            badge?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { buttons() }
        }
    }
}

private const val KRANKENFAHRT_INFO =
    "Wer zahlt die Fahrt? (Stand Nov. 2026, ohne Gewähr)\n\n" +
        "• Die Krankenkasse zahlt Krankenfahrten nur, wenn sie medizinisch zwingend nötig sind und der Arzt sie verordnet hat (Formular „Verordnung einer Krankenbeförderung“, Muster 4). Eine Taxifahrt aus Bequemlichkeit wird nicht bezahlt.\n" +
        "• Zu ambulanten Terminen gilt die Fahrt mit Taxi oder Mietwagen als genehmigt, wenn die Person im Schwerbehindertenausweis das Merkzeichen aG, Bl oder H hat, Pflegegrad 4 oder 5 hat, oder Pflegegrad 3 mit dauerhaft eingeschränkter Mobilität. Dann muss man vorher nicht bei der Kasse nachfragen.\n" +
        "• In allen anderen Fällen gibt es Fahrten zu ambulanten Terminen nur in Ausnahmefällen und meist mit vorheriger Genehmigung der Kasse (z. B. Dialyse, Chemo- oder Strahlentherapie).\n" +
        "• Bei Aufnahme und Entlassung im Krankenhaus reicht bei medizinischer Notwendigkeit die Verordnung, eine Genehmigung vorab ist nicht nötig.\n" +
        "• Eigenanteil: 10 % des Fahrpreises pro Fahrt, mindestens 5 €, höchstens 10 €, nie mehr als die tatsächlichen Kosten.\n" +
        "• Am besten die Verordnung schon beim Arzttermin ausstellen lassen, bevor die Fahrt stattfindet. Fragen beantworten die Krankenkasse oder der Pflegestützpunkt.\n\n" +
        "Quellen: Bundesgesundheitsministerium (bundesgesundheitsministerium.de/fahrkosten), GKV-Spitzenverband, Verbraucherzentrale."
