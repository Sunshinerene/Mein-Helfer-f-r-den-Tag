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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import de.meinhelfer.tag.data.CaregiverProfile
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.export.CarePdfs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EMERGENCY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

/** Text der Notfallkarte: alles Wichtige auf einen Blick fuer Rettungsdienst, Notaufnahme oder Vertretung. */
internal fun emergencyText(
    person: Person?,
    meds: List<Medication>,
    doctors: List<Doctor>,
    caregiver: CaregiverProfile,
    withInsuranceNumber: Boolean,
): String = buildString {
    append("NOTFALLKARTE\n")
    person?.let { p ->
        append(p.name).append('\n')
        p.birthDate?.let { append("Geboren am ").append(LocalDate.ofEpochDay(it).format(EMERGENCY_DATE)).append('\n') }
        if (p.careLevel in 1..5) append("Pflegegrad ").append(p.careLevel).append('\n')
        if (p.insurer.isNotBlank()) append("Krankenkasse: ").append(p.insurer).append('\n')
        if (withInsuranceNumber && p.insuranceNumber.isNotBlank()) append("Versichertennummer: ").append(p.insuranceNumber).append('\n')
        val addr = listOf(p.street, listOf(p.zip, p.city).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(", ")
        if (addr.isNotBlank()) append("Adresse: ").append(addr).append('\n')
        if (p.phone.isNotBlank()) append("Telefon: ").append(p.phone).append('\n')
        accessSummary(p.floor, p.elevator, p.access).takeIf { it.isNotBlank() }?.let {
            append("Zugang: ").append(it).append('\n')
        }
        if (p.important.isNotBlank()) append("\nWichtig zu wissen:\n").append(p.important.trim()).append('\n')
    }
    val own = meds.filter { person == null || it.personId == person.id }
    if (own.isNotEmpty()) {
        append("\nMedikamente:\n")
        own.forEach { m ->
            append("- ").append(m.name)
            if (m.dosage.isNotBlank()) append(" (").append(m.dosage).append(')')
            val times = m.timeList().joinToString(", ")
            if (times.isNotBlank()) append(", ").append(times).append(" Uhr")
            append('\n')
        }
    }
    val contacts = doctors.filter { it.phone.isNotBlank() }
    if (contacts.isNotEmpty()) {
        append("\nKontakte:\n")
        contacts.forEach { d ->
            append("- ").append(d.name)
            if (d.specialty.isNotBlank()) append(" (").append(d.specialty).append(')')
            append(": ").append(d.phone).append('\n')
        }
    }
    if (caregiver.isSet && (caregiver.name.isNotBlank() || caregiver.phone.isNotBlank())) {
        append("\nAnsprechperson:\n")
        append(caregiver.name.ifBlank { "(ohne Namen)" })
        caregiver.roleValue?.let { append(" – ").append(it.label) }
        if (caregiver.phone.isNotBlank()) append(", ").append(caregiver.phone)
        if (caregiver.address.isNotBlank()) append(", ").append(caregiver.address)
        append('\n')
    }
    append("\nStand: ").append(LocalDate.now().format(EMERGENCY_DATE)).append(". Ohne Gewähr, Angaben bitte prüfen.")
}

/** Notfallkarte mit den wichtigsten Angaben, zum Zeigen, Kopieren oder Teilen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmergencyDialog(
    person: Person?,
    meds: List<Medication>,
    doctors: List<Doctor>,
    caregiver: CaregiverProfile,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var withNumber by remember { mutableStateOf(false) }
    val text = emergencyText(person, meds, doctors, caregiver, withNumber)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Notfallkarte") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(shape = buttonShape(), onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
                    } catch (_: ActivityNotFoundException) {
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Notruf 112 wählen") }
                Text(text, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = withNumber, onClick = { withNumber = !withNumber }, label = { Text("Versichertennummer anzeigen") })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(contentPadding = PaddingValues(horizontal = 8.dp), onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Notfallkarte", text))
                        Toast.makeText(context, "Text kopiert", Toast.LENGTH_SHORT).show()
                    }) { Text("Kopieren") }
                    TextButton(contentPadding = PaddingValues(horizontal = 8.dp), onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        try {
                            context.startActivity(Intent.createChooser(send, "Notfallkarte teilen"))
                        } catch (_: ActivityNotFoundException) {
                        }
                    }) { Text("Teilen") }
                }
                Text("Zum Ausdrucken (Kühlschrank, Geldbörse):", style = MaterialTheme.typography.labelLarge)
                PdfButtons(shareLabel = "Als PDF teilen", chooserTitle = "Notfallkarte teilen") { ctx ->
                    CarePdfs.emergency(ctx, person, meds, doctors, caregiver, withNumber)
                }
                Text(
                    "Die PDF hat eine ganze Seite für den Kühlschrank und unten eine Karte im Scheckkartenformat zum Ausschneiden und Falten.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Die Karte entsteht nur aus deinen Angaben in der App und bleibt auf dem Handy. Geteilt wird nur, wenn du „Teilen“ oder „Als PDF teilen“ antippst. Die Notruf-Taste öffnet nur die Telefon-App.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } },
    )
}

/** Karte auf der Heute-Seite (wenn in den Einstellungen eingeschaltet). */
@Composable
fun EmergencyEntryCard(onOpen: () -> Unit) {
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Notfallkarte", style = MaterialTheme.typography.titleMedium)
            Text(
                "Alle wichtigen Angaben auf einen Blick für Rettungsdienst oder Vertretung.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Notfallkarte öffnen") }
        }
    }
}
