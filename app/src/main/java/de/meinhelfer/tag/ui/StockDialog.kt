package de.meinhelfer.tag.ui

import de.meinhelfer.tag.summary.Amounts

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
    addPack: Boolean = false,
) {
    val start = if (med.stockKnown) med.currentStock() else 0.0
    val unit = med.unit
    val fromDoses = de.meinhelfer.tag.summary.Doses.daily(med.timeList(), med.doseMap())
    var stock by remember {
        mutableStateOf(
            when {
                addPack && med.packSize > 0 -> Amounts.fmt(start + med.packSize)
                med.stockKnown -> Amounts.fmt(start)
                else -> ""
            },
        )
    }
    var perDay by remember { mutableStateOf(Amounts.fmt(med.dailyUse)) }
    var pack by remember { mutableStateOf(med.packSize.takeIf { it > 0 }?.let { Amounts.fmt(it) } ?: "") }
    val value = Amounts.parse(stock)
    fun clean(text: String) = text.filter { c -> c.isDigit() || c in ",." }.take(8)
    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onDone,
        title = { Text("Vorrat: " + med.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                queueText?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                if (med.dosage.isNotBlank()) Text(med.dosage, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (unit == "Stück") "Wie viele Stück (Tabletten, Kapseln …) sind jetzt noch da?" else "Wie viel ist jetzt noch da, in $unit?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (unit == "IE") Text("Insulin: Pens oder Patronen × IE je Pen, z. B. 3 Pens zu 300 IE = 900 IE.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { stock = clean(it) },
                        label = { Text("Vorrat ($unit)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = perDay,
                        onValueChange = { perDay = clean(it) },
                        label = { Text("Pro Tag ($unit)") },
                        enabled = fromDoses == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (addPack) Text("Eine neue Packung ist schon dazugezählt. Bitte kurz prüfen und speichern.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pack,
                        onValueChange = { pack = clean(it) },
                        label = { Text("Inhalt pro Packung ($unit)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    val p = Amounts.parse(pack) ?: 0.0
                    androidx.compose.material3.OutlinedButton(
                        shape = buttonShape(),
                        enabled = p > 0,
                        onClick = { stock = Amounts.fmt((Amounts.parse(stock) ?: 0.0) + p) },
                    ) { Text("+ Packung") }
                }
                Text(
                    "„+ Packung“ zählt eine neue Packung dazu. Ab heute zieht die App den Verbrauch pro Tag ab und erinnert rechtzeitig ans Nachbestellen.",
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
                            stock = value ?: 0.0,
                            stockDay = today,
                            stockKnown = true,
                            dailyUse = fromDoses ?: Amounts.parse(perDay) ?: med.dailyUse,
                            packSize = Amounts.parse(pack) ?: med.packSize,
                        ),
                    )
                    onSaved()
                }) { Text("Speichern") }
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(if (onSkip != null) "Fertig" else "Abbrechen") } },
    )
}
