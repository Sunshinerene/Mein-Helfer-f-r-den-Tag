package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.info.NearbyKind

/** Ein Notruf- oder Hilfsnummer-Eintrag. */
private data class EmergencyNumber(
    val number: String,
    val title: String,
    val hint: String,
    val dialNumber: String = number,
)

private val MAIN_NUMBERS = listOf(
    EmergencyNumber("112", "Notruf: Feuerwehr und Rettungsdienst", "Bei Lebensgefahr, Bewusstlosigkeit, Atemnot, Herz- oder Schlaganfall-Verdacht, schweren Unfällen. Kostenlos, überall in Europa."),
    EmergencyNumber("116 117", "Ärztlicher Bereitschaftsdienst", "Wenn es nicht lebensbedrohlich ist, aber nicht bis zur Sprechstunde warten kann (nachts, am Wochenende, an Feiertagen). Kostenlos, ohne Vorwahl.", "116117"),
    EmergencyNumber("110", "Polizei", "Bei Gefahr, Gewalt oder Vermisstenfällen. Kostenlos."),
)

private val OTHER_NUMBERS = listOf(
    EmergencyNumber("0800 00 22833", "Apotheken-Notdienst", "Findet die nächste dienstbereite Apotheke. Aus dem Festnetz kostenlos; vom Handy kurz 22833 wählen (kann bis zu 69 ct pro Minute kosten).", "08000022833"),
    EmergencyNumber("0800 111 0111", "Telefonseelsorge", "Zuhören in Krisen, rund um die Uhr, kostenlos und anonym.", "08001110111"),
)

/** Giftnotruf je Bundesland (Vorwahl + 19240, Mecklenburg-Vorpommern/Sachsen/Sachsen-Anhalt/Thüringen 0361 730 730). */
private val POISON = listOf(
    "Berlin / Brandenburg" to "03019240",
    "Nordrhein-Westfalen" to "022819240",
    "Baden-Württemberg" to "076119240",
    "Bayern" to "08919240",
    "Bremen / Hamburg / Niedersachsen / Schleswig-Holstein" to "055119240",
    "Hessen / Rheinland-Pfalz / Saarland" to "0613119240",
    "Mecklenburg-Vorpommern / Sachsen / Sachsen-Anhalt / Thüringen" to "0361730730",
)

private fun startNotruf(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // keine passende App installiert
    }
}

/** Oeffnet nur die Telefon-App mit der Nummer; gewaehlt wird erst nach Tipp auf "Anrufen" dort. */
private fun dialNumber(context: Context, number: String) =
    startNotruf(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' })))

private fun formatPoison(n: String): String = when {
    n.startsWith("0361") -> "0361 730 730"
    else -> n.removeSuffix("19240") + " 19240"
}

/** Text, den man bei einem Notruf sagen kann: Wer, wo, was. Aus den Angaben der Person. */
internal fun notrufSayText(person: Person?): String = buildString {
    append("Wo? ")
    val p = person
    if (p == null || (p.street + p.zip + p.city).isBlank()) {
        append("(Adresse bitte ergänzen)")
    } else {
        append(listOf(p.street, listOf(p.zip, p.city).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(", "))
        accessSummary(p.floor, p.elevator, p.access).takeIf { it.isNotBlank() }?.let { append(" (").append(it).append(')') }
    }
    append("\nWer? ").append(p?.name?.ifBlank { null } ?: "(Name)")
    p?.birthDate?.let {
        append(", geboren ").append(java.time.LocalDate.ofEpochDay(it).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")))
    }
    if (p != null && p.careLevel in 1..5) append(", Pflegegrad ").append(p.careLevel)
    append("\nWas ist passiert? (kurz beschreiben)")
    append("\nWie viele Betroffene? Wie ist der Zustand (ansprechbar, atmet)?")
    append("\nRückrufnummer: (eigene Nummer)")
    append("\nWarten, bis die Leitstelle das Gespräch beendet.")
}

/**
 * Schnellzugriff auf Notruf- und Hilfsnummern, eigene Kontakte und Hilfe in der Naehe.
 * Die App ruft nie selbst an: "Wählen" oeffnet nur die Telefon-App mit der Nummer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotrufDialog(
    person: Person?,
    doctors: List<Doctor>,
    onSaveContact: (Doctor) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var nearby by remember { mutableStateOf<NearbyKind?>(null) }
    var showPoison by remember { mutableStateOf(false) }
    val sayText = notrufSayText(person)
    val own = doctors.filter { it.phone.isNotBlank() }
    val ownKeys = listOf("hausarzt", "pflegedienst", "notdienst", "bereitschaft", "apotheke", "krankenhaus", "hausnotruf", "palliativ")
    val ownFirst = own.sortedBy { d -> if (ownKeys.any { it in d.specialty.lowercase() }) 0 else 1 }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Notruf und Hilfe") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Bei Lebensgefahr sofort die 112 wählen. „Wählen“ öffnet nur die Telefon-App mit der Nummer, angerufen wird erst dort.",
                    style = MaterialTheme.typography.bodySmall,
                )
                MAIN_NUMBERS.forEachIndexed { i, n ->
                    NumberCard(n, strong = i == 0) { dialNumber(context, n.dialNumber) }
                }

                Text("Wer weiß, was zu sagen ist?", style = MaterialTheme.typography.titleSmall)
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(sayText, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Notruf", sayText))
                            Toast.makeText(context, "Text kopiert", Toast.LENGTH_SHORT).show()
                        }) { Text("Kopieren") }
                    }
                }
                if (person != null && (person.street + person.city).isBlank()) {
                    Text(
                        "Tipp: Trage im Profil der Person Adresse, Stockwerk und Aufzug ein, dann steht hier alles bereit.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (ownFirst.isNotEmpty()) {
                    Text("Deine Kontakte", style = MaterialTheme.typography.titleSmall)
                    ownFirst.forEach { d ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(doctorLabel(d), style = MaterialTheme.typography.bodyMedium)
                                Text(d.phone, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { dialNumber(context, d.phone) }) { Text("Wählen") }
                        }
                    }
                }

                Text("Hilfe in der Nähe", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nearby = NearbyKind.Notaufnahme }) { Text("Notaufnahme") }
                    OutlinedButton(onClick = { nearby = NearbyKind.Apotheke }) { Text("Apotheke") }
                    OutlinedButton(onClick = { nearby = NearbyKind.Pflegedienst }) { Text("Pflegedienst") }
                }

                Text("Weitere Nummern", style = MaterialTheme.typography.titleSmall)
                OTHER_NUMBERS.forEach { n -> NumberCard(n, strong = false) { dialNumber(context, n.dialNumber) } }
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Giftnotruf (bei Vergiftung, auch Medikamente)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Je nach Bundesland ein anderes Giftinformationszentrum, rund um die Uhr, kostenlose Beratung. Bei Bewusstlosigkeit oder Krampfanfall zuerst 112.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { showPoison = !showPoison }) {
                            Text(if (showPoison) "Liste ausblenden" else "Nummer für mein Bundesland")
                        }
                        if (showPoison) {
                            POISON.forEach { (land, number) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(land, style = MaterialTheme.typography.bodySmall)
                                        Text(formatPoison(number), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    TextButton(onClick = { dialNumber(context, number) }) { Text("Wählen") }
                                }
                            }
                        }
                    }
                }

                Text(
                    "Stand Nov. 2026, ohne Gewähr. Quellen: aponet.de (Apotheken-Notdienst), 116117.de, Giftinformationszentren (bfr.bund.de), Telefonseelsorge. Diese Hinweise ersetzen keine ärztliche Beratung.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } },
    )

    nearby?.let { k ->
        NearbyDialog(person = person, initialKind = k, onSaveContact = onSaveContact, onClose = { nearby = null })
    }
}

@Composable
private fun NumberCard(n: EmergencyNumber, strong: Boolean, onDial: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (strong) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(n.title, style = MaterialTheme.typography.titleSmall)
            Text(n.hint, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onDial,
                modifier = Modifier.fillMaxWidth(),
                colors = if (strong) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            ) { Text("${n.number} wählen") }
        }
    }
}
