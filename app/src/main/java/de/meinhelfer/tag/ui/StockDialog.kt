package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Medication
import java.time.LocalDate

/**
 * Vorrat schnell eintragen (gezaehlt heute). [queueText] z. B. "2 von 7" beim Eintragen mehrerer, [onSkip] zum naechsten.
 */
@Composable
fun StockDialog(
    med: Medication,
    vm: MainViewModel,
    onDone: () -> Unit,
    queueText: String? = null,
    onSkip: (() -> Unit)? = null,
    onSaved: () -> Unit = onDone,
) {
    var stock by remember { mutableStateOf(if (med.stockKnown) med.currentStock().toString() else "") }
    var perDay by remember { mutableStateOf(med.dailyUse.toString()) }
    val value = stock.trim().toIntOrNull()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Vorrat: " + med.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                queueText?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                if (med.dosage.isNotBlank()) Text(med.dosage, style = MaterialTheme.typography.bodySmall)
                Text("Wie viele Stück (Tabletten, Kapseln, Pens …) sind jetzt noch da?", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { stock = it.filter(Char::isDigit).take(5) },
                        label = { Text("Vorrat") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = perDay,
                        onValueChange = { perDay = it.filter(Char::isDigit).take(3) },
                        label = { Text("Pro Tag") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Ab heute zieht die App den Verbrauch pro Tag ab und erinnert rechtzeitig ans Nachbestellen.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Row {
                if (onSkip != null) TextButton(onClick = onSkip) { Text("Überspringen") }
                TextButton(enabled = value != null, onClick = {
                    val today = LocalDate.now().toEpochDay()
                    vm.update(
                        med.copy(
                            stock = value ?: 0,
                            stockDay = today,
                            stockKnown = true,
                            dailyUse = perDay.trim().toIntOrNull() ?: med.dailyUse,
                        ),
                    )
                    onSaved()
                }) { Text("Speichern") }
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(if (onSkip != null) "Fertig" else "Abbrechen") } },
    )
}
