package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import java.text.SimpleDateFormat
import java.util.Locale

private enum class Tab(val label: String, val icon: ImageVector) {
    Heute("Heute", Icons.Default.Home),
    Medikamente("Medikamente", Icons.Default.Favorite),
    Termine("Termine", Icons.Default.DateRange),
    Aerzte("Ärzte", Icons.Default.Person),
}

private fun newDateFormat() =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).apply { isLenient = false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelferApp(vm: MainViewModel = viewModel()) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]

    val meds by vm.medications.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mein Helfer für den Tag") }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = i == tabIndex,
                        onClick = { tabIndex = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when (tab) {
                Tab.Heute -> TodayScreen(meds, appointments)
                Tab.Medikamente -> MedicationScreen(meds, vm)
                Tab.Termine -> AppointmentScreen(appointments, vm)
                Tab.Aerzte -> DoctorScreen(doctors, vm)
            }
        }
    }
}

// ---------------------------------------------------------------- Heute

@Composable
private fun TodayScreen(meds: List<Medication>, appointments: List<Appointment>) {
    val now = System.currentTimeMillis()
    val weekAhead = now + 7L * 24 * 60 * 60 * 1000
    val upcoming = appointments.filter { it.whenMillis in now..weekAhead }
    val lowStock = meds.filter { (it.daysLeft() ?: Int.MAX_VALUE) <= 7 }
    val intakes = meds
        .flatMap { m -> m.timeList().map { time -> time to m } }
        .sortedBy { it.first }
    val fmt = newDateFormat()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (lowStock.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Bald nachbestellen",
                    lines = lowStock.map { "${it.name}: reicht noch ca. ${it.daysLeft()} Tage" },
                    emptyText = "",
                    warning = true,
                )
            }
        }
        item {
            SectionCard(
                title = "Heute einnehmen",
                lines = intakes.map { (time, m) ->
                    "$time Uhr – ${m.name}" + if (m.dosage.isNotBlank()) " (${m.dosage})" else ""
                },
                emptyText = "Noch keine Einnahmezeiten eingetragen.",
            )
        }
        item {
            SectionCard(
                title = "Termine in den nächsten 7 Tagen",
                lines = upcoming.map { fmt.format(it.whenMillis) + " – " + it.title },
                emptyText = "Keine anstehenden Termine.",
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    lines: List<String>,
    emptyText: String,
    warning: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (warning) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (lines.isEmpty()) {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium)
            } else {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

// ---------------------------------------------------------------- Medikamente

@Composable
private fun MedicationScreen(items: List<Medication>, vm: MainViewModel) {
    var showDialog by remember { mutableStateOf(false) }

    ListScreen(
        items = items,
        emptyText = "Noch keine Medikamente. Mit + hinzufügen.",
        onAdd = { showDialog = true },
    ) { m ->
        val days = m.daysLeft()
        ItemCard(
            title = m.name + if (m.dosage.isNotBlank()) " – ${m.dosage}" else "",
            lines = listOfNotNull(
                m.times.takeIf { it.isNotBlank() }?.let { "Einnahme: $it Uhr" },
                "Vorrat: ${m.stock} Stück" + (days?.let { " (ca. $it Tage)" } ?: ""),
                m.notes.takeIf { it.isNotBlank() },
            ),
            onDelete = { vm.delete(m) },
        )
    }

    if (showDialog) {
        FormDialog(
            title = "Medikament hinzufügen",
            labels = listOf(
                "Name",
                "Dosierung (z. B. 1 Tablette)",
                "Einnahmezeiten (z. B. 08:00,20:00)",
                "Vorrat (Stück)",
                "Verbrauch pro Tag (Stück)",
            ),
            onDismiss = { showDialog = false },
        ) { v ->
            vm.add(
                Medication(
                    name = v[0].trim(),
                    dosage = v[1].trim(),
                    times = v[2].trim(),
                    stock = v[3].trim().toIntOrNull() ?: 0,
                    dailyUse = v[4].trim().toIntOrNull() ?: 1,
                ),
            )
            showDialog = false
            true
        }
    }
}

// ---------------------------------------------------------------- Termine

@Composable
private fun AppointmentScreen(items: List<Appointment>, vm: MainViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    val fmt = newDateFormat()

    ListScreen(
        items = items,
        emptyText = "Noch keine Termine. Mit + hinzufügen.",
        onAdd = { showDialog = true },
    ) { a ->
        ItemCard(
            title = a.title,
            lines = listOfNotNull(
                fmt.format(a.whenMillis) + " Uhr",
                a.place.takeIf { it.isNotBlank() },
                a.notes.takeIf { it.isNotBlank() },
            ),
            onDelete = { vm.delete(a) },
        )
    }

    if (showDialog) {
        FormDialog(
            title = "Termin hinzufügen",
            labels = listOf("Titel", "Datum (TT.MM.JJJJ)", "Uhrzeit (HH:MM)", "Ort", "Notizen"),
            onDismiss = { showDialog = false },
        ) { v ->
            val millis = runCatching { newDateFormat().parse("${v[1].trim()} ${v[2].trim()}")?.time }
                .getOrNull()
            if (millis == null) {
                false
            } else {
                vm.add(Appointment(title = v[0].trim(), whenMillis = millis, place = v[3].trim(), notes = v[4].trim()))
                showDialog = false
                true
            }
        }
    }
}

// ---------------------------------------------------------------- Ärzte

@Composable
private fun DoctorScreen(items: List<Doctor>, vm: MainViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ListScreen(
        items = items,
        emptyText = "Noch keine Ärzte. Mit + hinzufügen.",
        onAdd = { showDialog = true },
    ) { d ->
        ItemCard(
            title = d.name + if (d.specialty.isNotBlank()) " – ${d.specialty}" else "",
            lines = listOfNotNull(
                d.phone.takeIf { it.isNotBlank() },
                d.email.takeIf { it.isNotBlank() },
                d.address.takeIf { it.isNotBlank() },
            ),
            onDelete = { vm.delete(d) },
        ) {
            Row {
                if (d.phone.isNotBlank()) {
                    TextButton(onClick = { launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${d.phone}"))) }) {
                        Text("Anrufen")
                    }
                }
                if (d.email.isNotBlank()) {
                    TextButton(onClick = { launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${d.email}"))) }) {
                        Text("E-Mail")
                    }
                }
            }
        }
    }

    if (showDialog) {
        FormDialog(
            title = "Arzt hinzufügen",
            labels = listOf("Name", "Fachrichtung / wofür", "Telefon", "E-Mail", "Adresse"),
            onDismiss = { showDialog = false },
        ) { v ->
            vm.add(
                Doctor(
                    name = v[0].trim(),
                    specialty = v[1].trim(),
                    phone = v[2].trim(),
                    email = v[3].trim(),
                    address = v[4].trim(),
                ),
            )
            showDialog = false
            true
        }
    }
}

private fun launch(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // keine passende App installiert
    }
}

// ---------------------------------------------------------------- Bausteine

@Composable
private fun <T> ListScreen(
    items: List<T>,
    emptyText: String,
    onAdd: () -> Unit,
    row: @Composable (T) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Text(
                emptyText,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                lazyItems(items) { row(it) }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
        }
    }
}

@Composable
private fun ItemCard(
    title: String,
    lines: List<String>,
    onDelete: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                extra()
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Löschen")
            }
        }
    }
}

/** Einfacher Eingabedialog. [onSave] liefert true, wenn der Dialog geschlossen werden darf. */
@Composable
private fun FormDialog(
    title: String,
    labels: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Boolean,
) {
    val values = remember { mutableStateListOf<String>().apply { repeat(labels.size) { add("") } } }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                labels.forEachIndexed { i, label ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { values[i] = it },
                        label = { Text(label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (error) {
                    Text(
                        "Bitte Eingaben prüfen (z. B. Datum und Uhrzeit).",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = values[0].isNotBlank(),
                onClick = { error = !onSave(values.toList()) },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
