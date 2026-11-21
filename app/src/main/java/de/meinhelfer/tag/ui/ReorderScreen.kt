package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.export.PdfPrintAdapter
import de.meinhelfer.tag.export.ReorderExport
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FILE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY)

/** Alle Medikamente, deren Vorrat nur noch fuer eine Woche reicht (oder aufgebraucht ist). */
fun lowStockMeds(meds: List<Medication>, today: Long = LocalDate.now().toEpochDay()): List<Medication> =
    meds.filter { (it.daysLeft(today) ?: Int.MAX_VALUE) <= 7 }

/**
 * Nachbestell-Uebersicht: was fehlt, wen anrufen oder anschreiben, Liste als PDF.
 * Nichts wird automatisch gesendet, Mails und Anrufe startet immer erst ein Tipp.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReorderScreen(
    meds: List<Medication>,
    doctors: List<Doctor>,
    personName: String?,
    onBack: () -> Unit,
    caregiver: de.meinhelfer.tag.data.CaregiverProfile = de.meinhelfer.tag.data.CaregiverProfile(),
) {
    val context = LocalContext.current
    val today = LocalDate.now().toEpochDay()

    val sorted = meds.sortedBy { it.daysLeft(today) ?: Int.MAX_VALUE }
    // Standard: alles, was knapp wird. Mit den Haken kann man Medikamente zusaetzlich waehlen oder abwaehlen.
    var userSelection by remember { mutableStateOf<Set<Long>?>(null) }
    val selectedIds = userSelection ?: lowStockMeds(meds, today).map { it.id }.toSet()
    val selected = sorted.filter { it.id in selectedIds }

    var byPharmacy by rememberSaveable { mutableStateOf(false) }
    var includeName by rememberSaveable { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var pendingSave by remember { mutableStateOf<File?>(null) }
    val name = if (includeName) personName else null

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = pendingSave
        pendingSave = null
        if (uri != null && file != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                Toast.makeText(context, "PDF gespeichert.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Speichern hat nicht geklappt.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Gruppen: je Arzt bzw. Apotheke, plus "ohne Zuordnung"
    val groups: List<Pair<Doctor?, List<Medication>>> = run {
        val keyOf: (Medication) -> Long? = { if (byPharmacy) it.pharmacyId else it.doctorId }
        val known = selected.filter { m -> doctors.any { it.id == keyOf(m) } }
            .groupBy { m -> doctors.first { it.id == keyOf(m) } }
            .map { (d, list) -> d to list }
        val unassigned = selected.filter { m -> doctors.none { it.id == keyOf(m) } }
        known + (if (unassigned.isEmpty()) emptyList() else listOf<Pair<Doctor?, List<Medication>>>(null to unassigned))
    }

    fun createPdf(list: List<Medication>): File {
        val file = ReorderExport.createPdf(context, name, ReorderExport.rows(list, doctors, today))
        refresh++
        return file
    }

    val files = remember(refresh) { ReorderExport.savedFiles(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nachbestellung") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Auswahl
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
                        Text(
                            "Was nachbestellt werden muss",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                        )
                        Text(
                            "Vorausgewählt ist alles, was nur noch für etwa 7 Tage reicht. Mit den Haken änderst du die Auswahl.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                        )
                        if (sorted.isEmpty()) {
                            Text("Noch keine Medikamente angelegt.", Modifier.padding(start = 8.dp, bottom = 8.dp))
                        }
                        sorted.forEach { m ->
                            val days = m.daysLeft(today)
                            val low = (days ?: Int.MAX_VALUE) <= 7
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        userSelection = if (m.id in selectedIds) selectedIds - m.id else selectedIds + m.id
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = m.id in selectedIds,
                                    onCheckedChange = {
                                        userSelection = if (it) selectedIds + m.id else selectedIds - m.id
                                    },
                                )
                                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                                    Text(
                                        m.name + if (m.dosage.isNotBlank()) " (${m.dosage})" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        "Vorrat ${m.currentStock(today)} Stück" + (days?.let { ", reicht ca. $it Tage" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- Anfragen je Arzt / Apotheke
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Anfragen", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !byPharmacy, onClick = { byPharmacy = false }, label = { Text("Beim Arzt (Rezept)") })
                        FilterChip(selected = byPharmacy, onClick = { byPharmacy = true }, label = { Text("In der Apotheke") })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(checked = includeName, onCheckedChange = { includeName = it })
                        Text(
                            "Namen der Person in Nachricht und PDF nennen" +
                                (personName?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        "Es wird nichts automatisch gesendet. Anrufen und E-Mail öffnen erst nach deinem Tipp die Telefon- bzw. Mail-App, dort siehst du alles vor dem Absenden.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (selected.isEmpty()) {
                item { Text("Es ist nichts ausgewählt.", style = MaterialTheme.typography.bodyMedium) }
            }
            groups.forEach { (contact, list) ->
                item {
                    HCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                contact?.let { doctorLabel(it) }
                                    ?: if (byPharmacy) "Keine Apotheke hinterlegt" else "Kein Arzt hinterlegt",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            list.forEach { m ->
                                Text(
                                    "• ${m.name}" + if (m.dosage.isNotBlank()) " (${m.dosage})" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (contact == null) {
                                Text(
                                    "Ordne beim Medikament (Medikamente → Karte antippen) einen Kontakt zu, dann kannst du hier direkt anrufen oder schreiben.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (contact != null && contact.phone.isNotBlank()) {
                                    TextButton(onClick = {
                                        startIntent(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}")))
                                    }) { Text("Anrufen") }
                                }
                                TextButton(onClick = {
                                    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                                        contact?.email?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
                                        putExtra(Intent.EXTRA_SUBJECT, ReorderExport.mailSubject(name, byPharmacy))
                                        putExtra(Intent.EXTRA_TEXT, ReorderExport.mailText(name, list, today, byPharmacy, caregiver))
                                    }
                                    startIntent(context, mail)
                                }) { Text(if (contact?.email.isNullOrBlank()) "E-Mail (ohne Empfänger)" else "E-Mail mit Liste") }
                                TextButton(onClick = {
                                    val file = createPdf(list)
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        contact?.email?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
                                        putExtra(Intent.EXTRA_SUBJECT, ReorderExport.mailSubject(name, byPharmacy))
                                        putExtra(Intent.EXTRA_TEXT, ReorderExport.mailText(name, list, today, byPharmacy, caregiver))
                                        putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    startIntent(context, Intent.createChooser(send, "PDF senden"))
                                }) { Text("E-Mail mit PDF") }
                            }
                        }
                    }
                }
            }

            // ---- PDF der ganzen Auswahl
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Liste als PDF", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Die PDF wird in deinem privaten App-Ordner abgelegt. Von dort kannst du sie speichern, herunterladen, drucken oder weiterleiten.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            enabled = selected.isNotEmpty(),
                            onClick = {
                                createPdf(selected)
                                Toast.makeText(context, "PDF erstellt und abgelegt.", Toast.LENGTH_SHORT).show()
                            },
                        ) { Text("PDF der Auswahl erstellen") }
                    }
                }
            }

            // ---- Gespeicherte Listen
            if (files.isNotEmpty()) {
                item { Text("Gespeicherte Listen", style = MaterialTheme.typography.titleMedium) }
                files.forEach { file ->
                    item(key = file.name) {
                        HCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    java.time.Instant.ofEpochMilli(file.lastModified())
                                        .atZone(java.time.ZoneId.systemDefault()).format(FILE_TIME) + " Uhr",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = {
                                        val view = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uriFor(context, file), "application/pdf")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        startIntent(context, view)
                                    }) { Text("Öffnen") }
                                    TextButton(onClick = {
                                        pendingSave = file
                                        saveLauncher.launch(file.name)
                                    }) { Text("Speichern unter …") }
                                    TextButton(onClick = {
                                        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                                        pm.print(file.name, PdfPrintAdapter(file, file.name), PrintAttributes.Builder().build())
                                    }) { Text("Drucken") }
                                    TextButton(onClick = {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        startIntent(context, Intent.createChooser(send, "PDF teilen"))
                                    }) { Text("Teilen") }
                                    TextButton(onClick = {
                                        file.delete()
                                        refresh++
                                    }) { Text("Löschen") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun uriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

private fun startIntent(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Dafür ist keine passende App installiert.", Toast.LENGTH_SHORT).show()
    }
}
