package de.meinhelfer.tag.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import de.meinhelfer.tag.data.Duties
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Responsibility
import java.time.LocalDate

private const val NOBODY = "– niemand festgelegt –"

private fun isPharmacy(d: Doctor) = d.specialty.contains("Apotheke", ignoreCase = true)

/** Kurzer Text fuer einen Kontakt mit evtl. laufender Vertretung, z. B. "Dr. A – Vertretung: Dr. B bis 30.11." */
internal fun dutyText(id: Long?, doctors: List<Doctor>, duties: List<Responsibility>): String? {
    val d = doctors.firstOrNull { it.id == id } ?: return null
    val sub = Duties.substituteFor(duties, d.id)
    val subName = sub?.let { s -> doctors.firstOrNull { it.id == s.contactId }?.name }
    return d.name + if (sub != null && subName != null) " – Vertretung: $subName ${Duties.period(sub)}" else ""
}

/** Zeile unter Medikamente: wer fuer Rezepte und Apotheke zustaendig ist, mit "Ändern". */
@Composable
fun DutySummaryRow(person: Person?, doctors: List<Doctor>, duties: List<Responsibility>, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val pid = person?.id ?: return
    val rx = dutyText(Duties.defaultFor(duties, pid, Duties.REZEPT), doctors, duties)
    val ph = dutyText(Duties.defaultFor(duties, pid, Duties.APOTHEKE), doctors, duties)
    Row(modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (rx == null && ph == null) {
                "Zuständigen Arzt (Rezepte) und Apotheke festlegen"
            } else {
                listOfNotNull(rx?.let { "Rezepte: $it" }, ph?.let { "Apotheke: $it" }).joinToString(" · ")
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onEdit) { Text(if (rx == null && ph == null) "Festlegen" else "Ändern") }
    }
}

/**
 * Zustaendigkeiten bearbeiten. Mit [contact]: fuer genau diesen Kontakt (aus den Kontakten geoeffnet),
 * sonst fuer die Person (aus den Medikamenten geoeffnet).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DutyDialog(
    person: Person?,
    doctors: List<Doctor>,
    duties: List<Responsibility>,
    vm: MainViewModel,
    onClose: () -> Unit,
    contact: Doctor? = null,
) {
    val pid = person?.id ?: 1L
    val who = person?.name?.let { " für $it" }.orEmpty()
    val rxId = Duties.defaultFor(duties, pid, Duties.REZEPT)
    val phId = Duties.defaultFor(duties, pid, Duties.APOTHEKE)
    val today = LocalDate.now().toEpochDay()

    // Neue Vertretung
    var absentId by remember { mutableStateOf(contact?.id ?: rxId) }
    var substituteId by remember { mutableStateOf<Long?>(null) }
    var from by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var until by remember { mutableStateOf<LocalDate?>(null) }
    var adding by remember { mutableStateOf(false) }

    val subs = duties.filter { it.role == Duties.VERTRETUNG && (it.untilDay ?: Long.MAX_VALUE) >= today }
        .filter { contact == null || it.replacesId == contact.id || it.contactId == contact.id }
        .sortedBy { it.fromDay ?: 0L }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (contact != null) "Zuständigkeit: ${contact.name}" else "Zuständig$who") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (contact != null) {
                    RoleCheck(contact, doctors, "Zuständig für Rezepte und Medikamente$who", rxId) { on -> vm.setDuty(pid, Duties.REZEPT, if (on) contact.id else null) }
                    RoleCheck(contact, doctors, "Apotheke für Bestellung und Abholung$who", phId) { on -> vm.setDuty(pid, Duties.APOTHEKE, if (on) contact.id else null) }
                } else {
                    DropdownField(
                        label = "Arzt für Rezepte und Medikamente",
                        value = doctors.firstOrNull { it.id == rxId }?.let { doctorLabel(it) } ?: "",
                        options = listOf(NOBODY) + doctors.sortedBy { if (isPharmacy(it)) 1 else 0 }.map { doctorLabel(it) },
                        editable = false,
                        onValueChange = { chosen -> vm.setDuty(pid, Duties.REZEPT, doctors.firstOrNull { doctorLabel(it) == chosen }?.id) },
                    )
                    DropdownField(
                        label = "Apotheke für Bestellung und Abholung",
                        value = doctors.firstOrNull { it.id == phId }?.let { doctorLabel(it) } ?: "",
                        options = listOf(NOBODY) + doctors.sortedBy { if (isPharmacy(it)) 0 else 1 }.map { doctorLabel(it) },
                        editable = false,
                        onValueChange = { chosen -> vm.setDuty(pid, Duties.APOTHEKE, doctors.firstOrNull { doctorLabel(it) == chosen }?.id) },
                    )
                }
                Text(
                    "Gilt für alle Medikamente$who, bei denen nichts anderes eingetragen ist, auch für neu eingelesene. Beim einzelnen Medikament kannst du weiterhin einen anderen Arzt oder eine andere Apotheke wählen.",
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Vertretung (z. B. Urlaub)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Im Zeitraum gilt die Vertretung überall, wo der abwesende Kontakt zuständig ist (Nachbestellung, Medikamentenplan, Infos). Danach gilt wieder der übliche Kontakt.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (subs.isEmpty() && !adding) Text("Keine Vertretung eingetragen.", style = MaterialTheme.typography.bodyMedium)
                subs.forEach { s ->
                    val absent = doctors.firstOrNull { it.id == s.replacesId }?.name ?: "–"
                    val sub = doctors.firstOrNull { it.id == s.contactId }?.name ?: "–"
                    val active = (s.fromDay ?: Long.MIN_VALUE) <= today
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$sub vertritt $absent ${Duties.period(s)}" + if (active) " (läuft)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.deleteDuty(s) }) { Text("Entfernen") }
                    }
                }
                if (!adding) {
                    OutlinedButton(shape = buttonShape(), onClick = { adding = true }) { Text("+ Vertretung eintragen") }
                } else {
                    if (contact == null) {
                        DropdownField(
                            label = "Wer ist abwesend?",
                            value = doctors.firstOrNull { it.id == absentId }?.let { doctorLabel(it) } ?: "",
                            options = doctors.map { doctorLabel(it) },
                            editable = false,
                            onValueChange = { chosen -> absentId = doctors.firstOrNull { doctorLabel(it) == chosen }?.id },
                        )
                    }
                    val choices = doctors.filter { it.id != absentId }
                    if (choices.isEmpty()) {
                        Text("Lege die Vertretung zuerst unter Kontakte an.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        DropdownField(
                            label = "Vertretung durch",
                            value = doctors.firstOrNull { it.id == substituteId }?.let { doctorLabel(it) } ?: "",
                            options = choices.map { doctorLabel(it) },
                            editable = false,
                            onValueChange = { chosen -> substituteId = doctors.firstOrNull { doctorLabel(it) == chosen }?.id },
                        )
                    }
                    DatePickerField(label = "Von", date = from, onChange = { from = it })
                    DatePickerField(label = "Bis", date = until, onChange = { until = it })
                    val f = from
                    val u = until
                    val valid = absentId != null && substituteId != null && u != null && (f == null || !u.isBefore(f))
                    if (u != null && f != null && u.isBefore(f)) {
                        Text("„Bis“ liegt vor „Von“.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(shape = buttonShape(), enabled = valid, onClick = {
                            val a = absentId
                            val s = substituteId
                            if (a != null && s != null && u != null) {
                                vm.addSubstitute(a, s, f?.toEpochDay(), u.toEpochDay())
                                adding = false
                                substituteId = null
                                until = null
                            }
                        }) { Text("Vertretung speichern") }
                        TextButton(onClick = { adding = false }) { Text("Abbrechen") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Fertig") } },
    )
}

@Composable
private fun RoleCheck(contact: Doctor, doctors: List<Doctor>, label: String, current: Long?, onChange: (Boolean) -> Unit) {
    val checked = current == contact.id
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            val other = doctors.firstOrNull { it.id == current && it.id != contact.id }
            if (other != null) Text("Bisher: ${other.name} (wird ersetzt)", style = MaterialTheme.typography.bodySmall)
        }
    }
}
