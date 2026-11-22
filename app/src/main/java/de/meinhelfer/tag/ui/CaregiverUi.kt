package de.meinhelfer.tag.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.CaregiverProfile
import de.meinhelfer.tag.data.CaregiverRole

private val RELATIONS = listOf(
    "Sohn", "Tochter", "Ehepartner/in", "Enkel/in", "Schwiegersohn", "Schwiegertochter",
    "Bruder", "Schwester", "Nachbar/in", "Freund/in",
)

/** Eingabe der eigenen Daten (Rolle, Name, Kontakt). Bleibt nur auf dem Handy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaregiverDialog(initial: CaregiverProfile, onDismiss: () -> Unit, onSave: (CaregiverProfile) -> Unit) {
    var role by remember { mutableStateOf(initial.role) }
    var name by remember { mutableStateOf(initial.name) }
    var relation by remember { mutableStateOf(initial.relation) }
    var organisation by remember { mutableStateOf(initial.organisation) }
    var phone by remember { mutableStateOf(initial.phone) }
    var email by remember { mutableStateOf(initial.email) }
    var street by remember { mutableStateOf(initial.street) }
    var zip by remember { mutableStateOf(initial.zip) }
    var city by remember { mutableStateOf(initial.city) }
    val current = CaregiverRole.fromKey(role)

    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onDismiss,
        title = { Text("Meine Daten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Damit E-Mails gleich mit deinem Namen und deinen Kontaktdaten vorbereitet werden und du passende Tipps bekommst. Die Daten bleiben nur auf diesem Handy und werden nie automatisch gesendet.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Wer bist du?", style = MaterialTheme.typography.labelLarge)
                CaregiverRole.entries.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { role = r.key },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = role == r.key, onClick = { role = r.key })
                        Text(r.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                CardScanButtons(kinds = listOf(de.meinhelfer.tag.media.CardKind.Ausweis), otherPerson = false) { d ->
                    d.givenNames?.let { name = it }
                    d.street?.let { street = it }
                    d.zip?.let { zip = it }
                    d.city?.let { city = it }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Dein Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (current == CaregiverRole.Angehoeriger || current == CaregiverRole.Ehrenamt) {
                    Text("Du bist …", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RELATIONS.forEach { rel ->
                            FilterChip(selected = relation == rel, onClick = { relation = rel }, label = { Text(rel) })
                        }
                    }
                    OutlinedTextField(
                        value = relation, onValueChange = { relation = it },
                        label = { Text("Beziehung (selbst eintragen)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (current == CaregiverRole.Pflegedienst || current == CaregiverRole.Betreuung) {
                    OutlinedTextField(
                        value = organisation, onValueChange = { organisation = it },
                        label = { Text(if (current == CaregiverRole.Pflegedienst) "Pflegedienst" else "Vermittler / Firma (optional)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Telefon") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("E-Mail") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Deine Anschrift (freiwillig)", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Für Vollmachten, Schreiben und als Absender in E-Mails. Wohnst du bei der betreuten Person, reicht deren Adresse im Profil.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = street, onValueChange = { street = it },
                    label = { Text("Straße und Hausnummer") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = zip, onValueChange = { zip = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("PLZ") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.4f),
                    )
                    OutlinedTextField(
                        value = city, onValueChange = { city = it },
                        label = { Text("Ort") }, singleLine = true,
                        modifier = Modifier.weight(0.6f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = role.isNotBlank(),
                onClick = {
                    onSave(
                        CaregiverProfile(
                            role, name.trim(), relation.trim(), organisation.trim(), phone.trim(), email.trim(),
                            street.trim(), zip.trim(), city.trim(),
                        ),
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Später") } },
    )
}

/** Karte in den Einstellungen. */
@Composable
fun CaregiverCard(caregiver: CaregiverProfile, onEdit: () -> Unit) {
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Meine Daten", style = MaterialTheme.typography.titleMedium)
            if (caregiver.isSet) {
                Text(caregiver.roleValue?.label.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                val lines = listOf(caregiver.name, caregiver.relation, caregiver.organisation, caregiver.address, caregiver.phone, caregiver.email)
                    .filter { it.isNotBlank() }
                if (lines.isNotEmpty()) Text(lines.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "Noch nicht eingetragen. Mit deinen Daten werden E-Mails an Ärzte, Kasse und Pflegedienst gleich mit Absender und Kontakt vorbereitet, und die Infos-Seite zeigt dir Tipps für deine Rolle.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (caregiver.isSet) "Bearbeiten" else "Jetzt eintragen")
            }
        }
    }
}

/** Hinweis auf der Heute-Seite, solange noch keine eigenen Daten eingetragen sind. */
@Composable
fun CaregiverPromptCard(onEdit: () -> Unit) {
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Und du?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Trage kurz ein, wer du bist (z. B. Angehörige/r oder Pflegedienst). Dann stehen in vorbereiteten E-Mails gleich deine Kontaktdaten, und die App zeigt dir Tipps und Hilfen, die zu dir passen.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Meine Daten eintragen") }
        }
    }
}
