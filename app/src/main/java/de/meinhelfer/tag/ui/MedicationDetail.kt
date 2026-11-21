package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.info.MedicationInfo
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.launch

/** Alles zu einem Medikament auf einen Blick, inklusive Beschreibung und KI-Hinweis. */
@Composable
fun MedicationDetailDialog(
    med: Medication,
    doctors: List<Doctor>,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onUpdate: (Medication) -> Unit,
    onAddTodo: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(false) }

    fun load() {
        if (loading) return
        scope.launch {
            loading = true
            missing = false
            val info = runCatching { MedicationInfo.load(med.name) }.getOrNull()
            if (info != null) onUpdate(med.copy(description = info.text, descriptionSource = info.sourceUrl)) else missing = true
            loading = false
        }
    }

    // Einstellung "automatisch laden"
    LaunchedEffect(med.id) {
        if (AppPrefs.medInfoAuto(context) && med.description.isBlank()) load()
    }

    val today = java.time.LocalDate.now().toEpochDay()
    val days = med.daysLeft(today)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(med.name + if (med.dosage.isNotBlank()) " – ${med.dosage}" else "") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                med.photoPath?.let { PhotoThumb(it, 140.dp) }
                val facts = listOfNotNull(
                    med.times.takeIf { it.isNotBlank() }?.let { "Einnahme: ${it.replace(",", ", ")} Uhr" },
                    "Vorrat: ${med.currentStock(today)} Stück" + (days?.let { " (reicht ca. $it Tage)" } ?: ""),
                    med.pzn.takeIf { it.isNotBlank() }?.let { "PZN: $it" },
                    doctors.firstOrNull { it.id == med.doctorId }?.let { "Arzt: ${doctorLabel(it)}" },
                    doctors.firstOrNull { it.id == med.pharmacyId }?.let { "Apotheke: ${doctorLabel(it)}" },
                    med.notes.takeIf { it.isNotBlank() }?.let { "Notizen: $it" },
                )
                facts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }

                Text("Beschreibung (Anhaltspunkt)", style = MaterialTheme.typography.labelLarge)
                if (med.description.isNotBlank()) {
                    Text(med.description, style = MaterialTheme.typography.bodyMedium)
                    if (med.descriptionSource.isNotBlank()) {
                        TextButton(onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(med.descriptionSource)))
                            } catch (_: ActivityNotFoundException) {
                            }
                        }) { Text("Quelle: Wikipedia (CC BY-SA) öffnen") }
                    }
                } else if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Suche Beschreibung …", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    OutlinedButton(onClick = { load() }) { Text("Beschreibung aus dem Internet laden") }
                    if (missing) {
                        Text(
                            "Dazu wurde nichts Passendes gefunden. Versuche es mit dem Namen des Wirkstoffs (Medikament bearbeiten).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(MedicationInfo.DISCLAIMER, style = MaterialTheme.typography.bodySmall)

                Text("KI-Hinweis (Anhaltspunkt)", style = MaterialTheme.typography.labelLarge)
                AiHintBox(
                    buttonLabel = "KI-Hinweis zu diesem Medikament",
                    promptText = "Medikament: ${MedicationInfo.cleanName(med.name).ifBlank { med.name }}\n" +
                        "Nenne Anhaltspunkte, worauf man bei diesem Medikament allgemein achten und was man Arzt oder Apotheke fragen könnte.",
                    onTodo = onAddTodo,
                )
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Bearbeiten") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Schließen") } },
    )
}
