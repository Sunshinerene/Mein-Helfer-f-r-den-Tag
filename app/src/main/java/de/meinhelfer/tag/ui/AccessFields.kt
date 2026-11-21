package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Aufzug: 0 = unbekannt, 1 = ja, 2 = nein. */
internal fun elevatorText(elevator: Int): String? = when (elevator) {
    1 -> "Aufzug: ja"
    2 -> "Aufzug: nein (Treppen)"
    else -> null
}

/** "3. Stock, Aufzug: ja" fuer Anfragen. Leer, wenn nichts bekannt ist. */
internal fun accessSummary(floor: String, elevator: Int, access: String = ""): String =
    listOfNotNull(
        floor.trim().takeIf { it.isNotEmpty() },
        elevatorText(elevator),
        access.trim().takeIf { it.isNotEmpty() },
    ).joinToString(", ")

/** Stockwerk, Aufzug und Hinweise zum Zugang. Wird bei Person und Kontakt gebraucht. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccessFields(
    floor: String,
    onFloor: (String) -> Unit,
    elevator: Int,
    onElevator: (Int) -> Unit,
    access: String,
    onAccess: (String) -> Unit,
    hint: String,
) {
    Text(hint, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        value = floor,
        onValueChange = onFloor,
        label = { Text("Stockwerk (z. B. EG, 3. Stock)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Aufzug vorhanden?", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1 to "Ja", 2 to "Nein", 0 to "Unbekannt").forEach { (v, label) ->
            FilterChip(selected = elevator == v, onClick = { onElevator(v) }, label = { Text(label) })
        }
    }
    OutlinedTextField(
        value = access,
        onValueChange = onAccess,
        label = { Text("Hinweise zum Zugang (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
}
