package de.meinhelfer.tag.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.media.CardData
import de.meinhelfer.tag.media.CardKind
import de.meinhelfer.tag.media.CardScanner
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** Ein erkanntes Feld zum Pruefen: anhaken, aendern, uebernehmen. */
private class ScanField(val key: String, val label: String, value: String) {
    var value by mutableStateOf(value)
    var use by mutableStateOf(true)
}

private fun fieldsOf(data: CardData, withBirth: Boolean, withInsurance: Boolean): List<ScanField> = buildList {
    data.fullName?.let { add(ScanField("name", "Name", it)) }
    if (withBirth) data.birthDate?.let { add(ScanField("geburt", "Geburtsdatum", it.format(CardScanner.DISPLAY))) }
    data.street?.let { add(ScanField("strasse", "Straße und Hausnummer", it)) }
    data.zip?.let { add(ScanField("plz", "PLZ", it)) }
    data.city?.let { add(ScanField("ort", "Ort", it)) }
    if (withInsurance) {
        data.insurer?.let { add(ScanField("kasse", "Krankenkasse", it)) }
        data.insuranceNumber?.let { add(ScanField("nummer", "Versichertennummer", it)) }
    }
}

private fun parseDay(text: String): LocalDate? = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""").find(text.trim())?.let { m ->
    runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
}

/** Ergebnis nach dem Pruefen: nur die angehakten Felder sind gesetzt. */
private fun resultOf(fields: List<ScanField>): CardData {
    fun v(key: String) = fields.firstOrNull { it.key == key && it.use }?.value?.trim()?.takeIf { it.isNotBlank() }
    return CardData(
        givenNames = v("name"),
        birthDate = v("geburt")?.let { parseDay(it) },
        street = v("strasse"),
        zip = v("plz")?.filter { it.isDigit() }?.take(5),
        city = v("ort"),
        insurer = v("kasse"),
        insuranceNumber = v("nummer")?.replace(" ", ""),
    )
}

/**
 * Knoepfe "Ausweis fotografieren" und ggf. "Gesundheitskarte fotografieren". Das Foto wird nur auf dem Handy
 * ausgelesen und danach sofort geloescht. Uebernommen wird nur, was angehakt ist; [onResult] bekommt den
 * vollen Namen in [CardData.givenNames].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardScanButtons(
    kinds: List<CardKind>,
    otherPerson: Boolean,
    onResult: (CardData) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingKind by rememberSaveable { mutableStateOf(CardKind.Ausweis.name) }
    var infoFor by remember { mutableStateOf<CardKind?>(null) }
    var reading by remember { mutableStateOf(false) }
    var collected by remember { mutableStateOf<CardData?>(null) }
    val fields = remember { mutableStateListOf<ScanField>() }
    var showReview by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPath?.let { File(it) }
        pendingPath = null
        if (file == null) return@rememberLauncherForActivityResult
        if (!ok) {
            file.delete()
            return@rememberLauncherForActivityResult
        }
        val kind = CardKind.valueOf(pendingKind)
        reading = true
        scope.launch {
            val data = runCatching { CardScanner.read(context, file, kind) }.getOrElse {
                file.delete()
                CardData()
            }
            reading = false
            val merged = collected?.merge(data) ?: data
            collected = merged
            fields.clear()
            fields.addAll(fieldsOf(merged, withBirth = true, withInsurance = CardKind.Gesundheitskarte in kinds))
            notFound = data.isEmpty
            showReview = true
        }
    }

    fun shoot(kind: CardKind) {
        val file = CardScanner.newFile(context)
        pendingPath = file.path
        pendingKind = kind.name
        try {
            camera.launch(CardScanner.uriFor(context, file))
        } catch (_: Exception) {
            file.delete()
            pendingPath = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (CardKind.Ausweis in kinds) {
                OutlinedButton(shape = buttonShape(), onClick = { infoFor = CardKind.Ausweis }, enabled = !reading) { Text("Ausweis fotografieren") }
            }
            if (CardKind.Gesundheitskarte in kinds) {
                OutlinedButton(shape = buttonShape(), onClick = { infoFor = CardKind.Gesundheitskarte }, enabled = !reading) { Text("Krankenkarte fotografieren") }
            }
        }
        if (reading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Wird auf dem Handy ausgelesen …", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Text("Optional. Eintragen von Hand geht wie immer.", style = MaterialTheme.typography.bodySmall)
        }
    }

    infoFor?.let { kind ->
        AlertDialog(
            onDismissRequest = { infoFor = null },
            title = { Text(if (kind == CardKind.Ausweis) "Ausweis fotografieren" else "Krankenkarte fotografieren") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (kind == CardKind.Ausweis) {
                            "Vorderseite: Name und Geburtsdatum. Rückseite: Anschrift. Du kannst beide Seiten nacheinander fotografieren."
                        } else {
                            "Am besten die Rückseite (Europäische Krankenversicherungskarte): Dort stehen Name, Geburtsdatum, Versichertennummer und Kasse beschriftet. Die Vorderseite geht auch."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Karte flach hinlegen, gut beleuchtet, ohne Spiegelung, möglichst bildfüllend.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Das Foto wird nur auf diesem Handy ausgelesen (ohne Internet) und danach sofort gelöscht. Gespeichert wird nur, was du übernimmst und speicherst.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (otherPerson) {
                        Text("Die Karte einer anderen Person bitte nur mit deren Einverständnis fotografieren.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    infoFor = null
                    shoot(kind)
                }) { Text("Foto machen") }
            },
            dismissButton = { TextButton(onClick = { infoFor = null }) { Text("Abbrechen") } },
        )
    }

    if (showReview) {
        val close = {
            showReview = false
            collected = null
            fields.clear()
        }
        AlertDialog(
            onDismissRequest = close,
            title = { Text("Erkannte Angaben prüfen") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (fields.isEmpty()) {
                        Text(
                            "Es wurde nichts erkannt. Versuche es mit mehr Licht, ohne Spiegelung und mit der Karte näher an der Kamera, oder trag die Angaben von Hand ein.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        if (notFound) Text("Auf dem letzten Foto wurde nichts Neues erkannt.", style = MaterialTheme.typography.bodySmall)
                        Text("Bitte prüfen. Nur Angehaktes wird ins Formular übernommen.", style = MaterialTheme.typography.bodySmall)
                        fields.forEach { f ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = f.use, onCheckedChange = { f.use = it })
                                OutlinedTextField(
                                    value = f.value,
                                    onValueChange = { f.value = it.take(100) },
                                    label = { Text(f.label) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    TextButton(onClick = {
                        showReview = false
                        infoFor = CardKind.valueOf(pendingKind)
                    }) { Text(if (fields.isEmpty()) "Nochmal fotografieren" else "Weitere Seite fotografieren") }
                }
            },
            confirmButton = {
                TextButton(enabled = fields.any { it.use }, onClick = {
                    onResult(resultOf(fields.toList()))
                    close()
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = close) { Text("Abbrechen") } },
        )
    }
}
