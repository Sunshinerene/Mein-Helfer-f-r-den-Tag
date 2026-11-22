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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.CaregiverProfile
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.export.MailContext
import de.meinhelfer.tag.export.MailTemplates
import de.meinhelfer.tag.export.MailTopic
import java.time.LocalDate

/**
 * Vorbereitete E-Mail zu einem Thema: Absender, Angaben zur Person und Kontaktdaten sind schon eingesetzt.
 * Du pruefst und aenderst den Text, dann oeffnet ein Tipp deine E-Mail-App. Gesendet wird nichts automatisch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MailDraftDialog(
    recipient: Doctor,
    person: Person?,
    caregiver: CaregiverProfile,
    meds: List<Medication>,
    appointment: Appointment? = null,
    note: String = "",
    initialTopic: MailTopic? = null,
    onEditProfile: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val today = LocalDate.now().toEpochDay()
    val kind = MailTemplates.kindOf(recipient)
    val topics = MailTemplates.topicsFor(kind)

    var topic by remember { mutableStateOf(initialTopic?.takeIf { it in topics } ?: MailTemplates.defaultTopic(kind, appointment)) }
    var extra by remember { mutableStateOf(note) }
    var medIds by remember { mutableStateOf(MailTemplates.defaultMeds(meds, recipient, kind, today)) }
    val defaults = MailTemplates.defaultIncludes(kind, topic)
    var incBirth by remember { mutableStateOf(defaults.first) }
    var incInsNr by remember { mutableStateOf(defaults.second) }
    var incCare by remember { mutableStateOf(defaults.third) }
    var incAddr by remember { mutableStateOf(topic == MailTopic.Hausbesuch) }

    val ctx = MailContext(
        person = person,
        caregiver = caregiver,
        meds = meds.filter { it.id in medIds },
        appointment = appointment,
        extra = extra,
        includeBirth = incBirth,
        includeInsuranceNumber = incInsNr,
        includeCareLevel = incCare,
        includeAddress = incAddr,
        today = today,
    )
    // Aendert man oben etwas, wird der Text neu erzeugt; danach kann man ihn frei bearbeiten.
    val key = listOf(topic, extra, medIds, incBirth, incInsNr, incCare, incAddr, caregiver)
    var subject by remember(key) { mutableStateOf(MailTemplates.subject(topic, ctx)) }
    var body by remember(key) { mutableStateOf(MailTemplates.body(topic, recipient, ctx)) }

    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onClose,
        title = { Text("E-Mail an ${recipient.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recipient.email.isBlank()) {
                    Text(
                        "Für diesen Kontakt ist keine E-Mail-Adresse hinterlegt. Du kannst die E-Mail trotzdem vorbereiten und die Adresse in der E-Mail-App eintragen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text("Thema", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    topics.forEach { t ->
                        FilterChip(selected = topic == t, onClick = {
                            topic = t
                            val d = MailTemplates.defaultIncludes(kind, t)
                            incCare = d.third
                            incAddr = t == MailTopic.Hausbesuch
                        }, label = { Text(t.label) })
                    }
                }
                if (topic.usesMeds) {
                    Text("Medikamente", style = MaterialTheme.typography.labelLarge)
                    if (meds.isEmpty()) {
                        Text("Noch keine Medikamente angelegt.", style = MaterialTheme.typography.bodySmall)
                    }
                    meds.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = m.id in medIds, onCheckedChange = {
                                medIds = if (it) medIds + m.id else medIds - m.id
                            })
                            val days = m.daysLeft(today)
                            Text(
                                m.name + (days?.let { " (reicht ca. $it Tage)" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = extra, onValueChange = { extra = it },
                    label = { Text("Grund / Anliegen (kommt in den Text)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
                Text("Angaben zur Person im Text", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = incBirth, onClick = { incBirth = !incBirth }, label = { Text("Geburtsdatum") })
                    FilterChip(selected = incCare, onClick = { incCare = !incCare }, label = { Text("Pflegegrad") })
                    FilterChip(selected = incInsNr, onClick = { incInsNr = !incInsNr }, label = { Text("Versichertennummer") })
                    FilterChip(selected = incAddr, onClick = { incAddr = !incAddr }, label = { Text("Adresse") })
                }
                if (!caregiver.isSet) {
                    Text(
                        "Deine Kontaktdaten fehlen noch. Trage sie ein, dann stehen sie automatisch am Ende jeder E-Mail.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onEditProfile) { Text("Meine Daten eintragen") }
                }
                OutlinedTextField(
                    value = subject, onValueChange = { subject = it },
                    label = { Text("Betreff") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body, onValueChange = { body = it },
                    label = { Text("Text – bitte prüfen und ergänzen") },
                    modifier = Modifier.fillMaxWidth(), minLines = 8,
                )
                if (body.contains(MailTemplates.PLACEHOLDER)) {
                    Text(
                        "Im Text steht noch „${MailTemplates.PLACEHOLDER}“. Bitte ausfüllen, z. B. oben bei „Grund / Anliegen“.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(shape = buttonShape(), onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("E-Mail", subject + "\n\n" + body))
                    Toast.makeText(context, "Text kopiert", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Text kopieren") }
                Text(
                    "Hinweis: Normale E-Mails sind nicht verschlüsselt. Schreibe nur, was nötig ist. Du prüfst alles und schickst selbst ab, die App sendet nichts automatisch.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                openMailWithFiles(context, recipient.email, subject, body, emptyList())
            }) { Text("In E-Mail-App öffnen") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Schließen") } },
    )
}
