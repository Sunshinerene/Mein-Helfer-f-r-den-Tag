package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.update.UpdateState

private enum class Tab(val label: String, val icon: ImageVector) {
    Heute("Heute", Icons.Default.Home),
    Medikamente("Medikamente", Icons.Default.Favorite),
    Termine("Termine", Icons.Default.DateRange),
    Aerzte("Ärzte", Icons.Default.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelferApp(vm: MainViewModel = viewModel()) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val tab = Tab.entries[tabIndex]

    val meds by vm.medications.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()

    BackHandler(enabled = showSettings) { showSettings = false }

    if (showSettings) {
        SettingsScreen(vm = vm, onBack = { showSettings = false })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Mein Helfer für den Tag") },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                        }
                    },
                )
            },
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
                    Tab.Heute -> TodayScreen(
                        meds = meds,
                        appointments = appointments,
                        updateState = updateState,
                        onOpenSettings = { showSettings = true },
                    )
                    Tab.Medikamente -> MedicationScreen(meds, vm)
                    Tab.Termine -> AppointmentScreen(appointments, doctors, vm)
                    Tab.Aerzte -> DoctorScreen(doctors, vm)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Heute

@Composable
private fun TodayScreen(
    meds: List<Medication>,
    appointments: List<Appointment>,
    updateState: UpdateState,
    onOpenSettings: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val weekAhead = now + 7L * 24 * 60 * 60 * 1000
    val upcoming = appointments.filter { it.whenMillis in now..weekAhead }
    val lowStock = meds.filter { (it.daysLeft() ?: Int.MAX_VALUE) <= 7 }
    val intakes = meds
        .flatMap { m -> m.timeList().map { time -> time to m } }
        .sortedBy { it.first }

    val newVersion = when (updateState) {
        is UpdateState.Available -> updateState.info.version
        is UpdateState.Downloading -> updateState.info.version
        is UpdateState.Ready -> updateState.info.version
        else -> null
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (newVersion != null) {
            item {
                Card(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Neue Version $newVersion verfügbar", style = MaterialTheme.typography.titleMedium)
                        Text("Tippen, um zu den Einstellungen zu gelangen und das Update zu laden.")
                    }
                }
            }
        }
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
                lines = upcoming.map { formatDateTime(it.whenMillis) + " – " + it.title },
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
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Medication?>(null) }

    ListScreen(
        items = items,
        emptyText = "Noch keine Medikamente. Mit + hinzufügen.",
        onAdd = { showAdd = true },
    ) { m ->
        val days = m.daysLeft()
        ItemCard(
            title = m.name + if (m.dosage.isNotBlank()) " – ${m.dosage}" else "",
            lines = listOfNotNull(
                m.times.takeIf { it.isNotBlank() }?.let { "Einnahme: ${it.replace(",", ", ")} Uhr" },
                "Vorrat: ${m.stock} Stück" + (days?.let { " (ca. $it Tage)" } ?: ""),
                m.notes.takeIf { it.isNotBlank() },
            ),
            onClick = { editItem = m },
            onDelete = { vm.delete(m) },
        )
    }

    if (showAdd || editItem != null) {
        MedicationDialog(
            initial = editItem,
            onDismiss = {
                showAdd = false
                editItem = null
            },
            onSave = { saved ->
                if (editItem == null) vm.add(saved) else vm.update(saved)
                showAdd = false
                editItem = null
            },
        )
    }
}

// ---------------------------------------------------------------- Termine

@Composable
private fun AppointmentScreen(items: List<Appointment>, doctors: List<Doctor>, vm: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Appointment?>(null) }

    ListScreen(
        items = items,
        emptyText = "Noch keine Termine. Mit + hinzufügen.",
        onAdd = { showAdd = true },
    ) { a ->
        val doctor = doctors.firstOrNull { it.id == a.doctorId }
        ItemCard(
            title = a.title,
            lines = listOfNotNull(
                formatDateTime(a.whenMillis) + " Uhr",
                doctor?.let { doctorLabel(it) },
                a.place.takeIf { it.isNotBlank() },
                a.notes.takeIf { it.isNotBlank() },
            ),
            onClick = { editItem = a },
            onDelete = { vm.delete(a) },
        )
    }

    if (showAdd || editItem != null) {
        AppointmentDialog(
            initial = editItem,
            doctors = doctors,
            onDismiss = {
                showAdd = false
                editItem = null
            },
            onSave = { saved ->
                if (editItem == null) vm.add(saved) else vm.update(saved)
                showAdd = false
                editItem = null
            },
        )
    }
}

// ---------------------------------------------------------------- Ärzte

@Composable
private fun DoctorScreen(items: List<Doctor>, vm: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Doctor?>(null) }
    val context = LocalContext.current

    ListScreen(
        items = items,
        emptyText = "Noch keine Ärzte oder Institutionen. Mit + hinzufügen.",
        onAdd = { showAdd = true },
    ) { d ->
        ItemCard(
            title = doctorLabel(d),
            lines = listOfNotNull(
                d.phone.takeIf { it.isNotBlank() },
                d.email.takeIf { it.isNotBlank() },
                d.address.takeIf { it.isNotBlank() },
            ),
            onClick = { editItem = d },
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

    if (showAdd || editItem != null) {
        DoctorDialog(
            initial = editItem,
            onDismiss = {
                showAdd = false
                editItem = null
            },
            onSave = { saved ->
                if (editItem == null) vm.add(saved) else vm.update(saved)
                showAdd = false
                editItem = null
            },
        )
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

/** Karte: Antippen = bearbeiten, Papierkorb = loeschen (mit Rueckfrage). */
@Composable
private fun ItemCard(
    title: String,
    lines: List<String>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                extra()
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Löschen")
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Löschen?") },
            text = { Text("„$title“ wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}
