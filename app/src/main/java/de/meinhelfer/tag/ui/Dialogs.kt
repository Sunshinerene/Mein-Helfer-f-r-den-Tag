package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import de.meinhelfer.tag.media.PackageScanner
import de.meinhelfer.tag.media.PhotoStore
import de.meinhelfer.tag.media.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private const val NO_DOCTOR = "– keine Auswahl –"

fun doctorLabel(doctor: Doctor): String =
    doctor.name + if (doctor.specialty.isNotBlank()) " (${doctor.specialty})" else ""

@Composable
private fun DialogForm(
    title: String,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { content() }
        },
        confirmButton = { TextButton(enabled = canSave, onClick = onSave) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationDialog(initial: Medication?, onDismiss: () -> Unit, onSave: (Medication) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var dosage by remember { mutableStateOf(initial?.dosage ?: "") }
    var times by remember { mutableStateOf(parseTimes(initial?.times ?: "")) }
    // Angezeigt wird der aktuell errechnete Vorrat (gezaehlter Vorrat minus Verbrauch seit dem Zaehltag).
    val initialStockText = initial?.currentStock()?.toString() ?: ""
    var stock by remember { mutableStateOf(initialStockText) }
    var dailyUse by remember { mutableStateOf(initial?.dailyUse?.toString() ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    val originalPhoto = initial?.photoPath
    var photoName by remember { mutableStateOf(originalPhoto) }
    var scanning by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf<ScanResult?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    // Neues Foto: verkleinern, Text lokal erkennen, leere Felder vorbelegen (alles bleibt aenderbar).
    fun handleNewPhoto(file: File) {
        scope.launch {
            scanning = true
            val bitmap = PhotoStore.normalize(file)
            if (bitmap == null) {
                file.delete()
                scanning = false
                return@launch
            }
            val previous = photoName
            if (previous != null && previous != originalPhoto) PhotoStore.delete(context, previous)
            photoName = file.name

            val result = runCatching { PackageScanner.scan(bitmap) }.getOrNull()
            scan = result
            if (result != null) {
                if (name.isBlank()) result.nameCandidates.firstOrNull()?.let { name = it }
                if (stock.isBlank()) result.packSize?.let { stock = it.toString() }
                if (notes.isBlank()) result.strength?.let { notes = "Stärke: $it" }
            }
            scanning = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingFile
        pendingFile = null
        if (ok && file != null) handleNewPhoto(file) else file?.delete()
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val file = PhotoStore.newFile(context)
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                handleNewPhoto(file)
            }
        }
    }

    val cancel = {
        if (photoName != originalPhoto) PhotoStore.delete(context, photoName)
        onDismiss()
    }

    DialogForm(
        title = if (initial == null) "Medikament hinzufügen" else "Medikament bearbeiten",
        canSave = name.isNotBlank() && !scanning,
        onDismiss = cancel,
        onSave = {
            if (originalPhoto != null && photoName != originalPhoto) PhotoStore.delete(context, originalPhoto)
            val today = LocalDate.now().toEpochDay()
            val stockChanged = initial == null || stock.trim() != initialStockText
            onSave(
                Medication(
                    id = initial?.id ?: 0,
                    name = name.trim(),
                    dosage = dosage.trim(),
                    times = times.joinToString(",") { it.format(TIME_FORMAT) },
                    // Nur bei geaendertem Vorrat neu zaehlen (Stichtag heute), sonst alte Zaehlung behalten.
                    stock = if (stockChanged) stock.trim().toIntOrNull() ?: 0 else initial!!.stock,
                    stockDay = if (stockChanged) today else initial!!.stockDay,
                    createdDay = initial?.createdDay ?: today,
                    // Leer = Anzahl der Einnahmen pro Tag
                    dailyUse = dailyUse.trim().toIntOrNull() ?: maxOf(1, times.size),
                    notes = notes.trim(),
                    photoPath = photoName,
                ),
            )
        },
    ) {
        // ---- Foto der Packung
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val file = PhotoStore.newFile(context)
                    pendingFile = file
                    try {
                        cameraLauncher.launch(PhotoStore.uriForCamera(context, file))
                    } catch (_: ActivityNotFoundException) {
                        pendingFile = null
                    }
                },
                enabled = !scanning,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Packung fotografieren", maxLines = 1) }
            OutlinedButton(
                onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !scanning,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text("Galerie", maxLines = 1) }
        }
        if (scanning) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Erkenne Text …", style = MaterialTheme.typography.bodyMedium)
            }
        }
        photoName?.let { photo ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoThumb(photo, 96.dp)
                TextButton(
                    onClick = {
                        if (photoName != originalPhoto) PhotoStore.delete(context, photoName)
                        photoName = null
                        scan = null
                    },
                ) { Text("Foto entfernen") }
            }
        }
        scan?.let { result ->
            if (result.nameCandidates.isEmpty()) {
                Text(
                    "Auf dem Foto wurde kein Text erkannt. Bitte die Daten von Hand eintragen.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "Vom Foto erkannt, bitte prüfen. Antippen übernimmt den Namen:",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    result.nameCandidates.forEach { candidate ->
                        AssistChip(onClick = { name = candidate }, label = { Text(candidate) })
                    }
                }
            }
        }

        // ---- Angaben
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownField(
            label = "Dosierung pro Einnahme",
            value = dosage,
            options = DOSAGE_PRESETS,
            onValueChange = { dosage = it },
        )
        TimesChooser(times) { times = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = stock,
                onValueChange = { stock = it },
                label = { Text("Vorrat", maxLines = 1) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = dailyUse,
                onValueChange = { dailyUse = it },
                label = { Text("Pro Tag", maxLines = 1) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Vorrat in Stück, „Pro Tag“ = Verbrauch pro Tag (leer = Anzahl der Einnahmen). Der Vorrat sinkt danach automatisch jeden Tag.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notizen") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun AppointmentDialog(
    initial: Appointment?,
    doctors: List<Doctor>,
    onDismiss: () -> Unit,
    onSave: (Appointment) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initialDateTime = initial?.let { Instant.ofEpochMilli(it.whenMillis).atZone(zone) }

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var doctorId by remember { mutableStateOf<Long?>(initial?.doctorId) }
    var date by remember { mutableStateOf<LocalDate?>(initialDateTime?.toLocalDate() ?: LocalDate.now()) }
    var time by remember {
        mutableStateOf<LocalTime?>(
            initialDateTime?.toLocalTime()?.withSecond(0)?.withNano(0)
                ?: LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0),
        )
    }
    var endTime by remember {
        mutableStateOf<LocalTime?>(
            initial?.endMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime().withSecond(0).withNano(0) },
        )
    }
    var place by remember { mutableStateOf(initial?.place ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    val selectedDoctor = doctors.firstOrNull { it.id == doctorId }

    DialogForm(
        title = if (initial == null) "Termin hinzufügen" else "Termin bearbeiten",
        canSave = title.isNotBlank() && date != null && time != null &&
            (endTime == null || time?.let { endTime!! > it } == true),
        onDismiss = onDismiss,
        onSave = {
            val d = date
            val t = time
            if (d != null && t != null) {
                onSave(
                    Appointment(
                        id = initial?.id ?: 0,
                        title = title.trim(),
                        whenMillis = LocalDateTime.of(d, t).atZone(zone).toInstant().toEpochMilli(),
                        endMillis = endTime?.let { LocalDateTime.of(d, it).atZone(zone).toInstant().toEpochMilli() },
                        place = place.trim(),
                        notes = notes.trim(),
                        doctorId = doctorId?.takeIf { id -> doctors.any { it.id == id } },
                    ),
                )
            }
        },
    ) {
        DropdownField(
            label = "Titel",
            value = title,
            options = APPOINTMENT_TITLES,
            onValueChange = { title = it },
        )

        if (doctors.isEmpty()) {
            Text(
                "Noch keine Ärzte oder Institutionen angelegt. Du kannst sie im Tab „Ärzte“ hinzufügen.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            DropdownField(
                label = "Arzt / Institution",
                value = selectedDoctor?.let { doctorLabel(it) } ?: "",
                options = listOf(NO_DOCTOR) + doctors.map { doctorLabel(it) },
                editable = false,
                onValueChange = { chosen ->
                    val doctor = doctors.firstOrNull { doctorLabel(it) == chosen }
                    doctorId = doctor?.id
                    if (doctor != null && place.isBlank()) place = doctor.address
                },
            )
        }

        DatePickerField(
            label = "Datum",
            date = date,
            onChange = { date = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            TimePickerField(
                label = "Von",
                time = time,
                onChange = { time = it },
                modifier = Modifier.weight(1f),
            )
            TimePickerField(
                label = "Bis (optional)",
                time = endTime,
                onChange = { endTime = it },
                modifier = Modifier.weight(1f),
                placeholder = "– wählen –",
                defaultTime = time?.plusHours(1) ?: LocalTime.of(10, 0),
            )
        }
        if (endTime != null) {
            TextButton(onClick = { endTime = null }) { Text("Ende entfernen") }
            if (time?.let { endTime!! <= it } == true) {
                Text(
                    "Das Ende muss nach dem Beginn liegen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        OutlinedTextField(
            value = place,
            onValueChange = { place = it },
            label = { Text("Ort") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notizen") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun DoctorDialog(initial: Doctor?, onDismiss: () -> Unit, onSave: (Doctor) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var specialty by remember { mutableStateOf(initial?.specialty ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }

    DialogForm(
        title = if (initial == null) "Arzt / Institution hinzufügen" else "Arzt / Institution bearbeiten",
        canSave = name.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                Doctor(
                    id = initial?.id ?: 0,
                    name = name.trim(),
                    specialty = specialty.trim(),
                    phone = phone.trim(),
                    email = email.trim(),
                    address = address.trim(),
                ),
            )
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownField(
            label = "Fachrichtung / wofür",
            value = specialty,
            options = SPECIALTIES,
            onValueChange = { specialty = it },
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Telefon") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-Mail") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Adresse") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
