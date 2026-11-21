package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: ImageVector) {
    Heute("Heute", Icons.Default.Home),
    Medikamente("Medikamente", Icons.Default.Favorite),
    Termine("Termine", Icons.Default.DateRange),
    Aerzte("Kontakte", Icons.Default.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelferApp(vm: MainViewModel = viewModel()) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSummary by rememberSaveable { mutableStateOf(false) }
    val tab = Tab.entries[tabIndex]

    val meds by vm.medications.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val checkins by vm.checkins.collectAsStateWithLifecycle()
    val dayNote by vm.dayNote.collectAsStateWithLifecycle()
    val todayDay by vm.today.collectAsStateWithLifecycle()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val personId by vm.personId.collectAsStateWithLifecycle()

    // Jede Minute: Uhrzeit und Datum aktualisieren (ueberfaellige Einnahmen, Tageswechsel)
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
            vm.refreshToday()
        }
    }

    BackHandler(enabled = showSettings || showSummary) {
        if (showSettings) showSettings = false else showSummary = false
    }

    if (showSettings) {
        SettingsScreen(vm = vm, onBack = { showSettings = false })
    } else if (showSummary) {
        SummaryScreen(vm = vm, onBack = { showSummary = false })
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
            Column(Modifier.padding(inner).fillMaxSize()) {
              PersonBar(persons = persons, currentId = personId, vm = vm)
              Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Tab.Heute -> TodayScreen(
                        vm = vm,
                        meds = meds,
                        appointments = appointments,
                        doctors = doctors,
                        checkins = checkins,
                        dayNote = dayNote,
                        todayDay = todayDay,
                        nowMillis = nowMillis,
                        updateState = updateState,
                        onOpenSettings = { showSettings = true },
                        onOpenSummary = { showSummary = true },
                    )
                    Tab.Medikamente -> MedicationScreen(meds, vm)
                    Tab.Termine -> AppointmentScreen(appointments, doctors, vm)
                    Tab.Aerzte -> DoctorScreen(doctors, vm)
                }
              }
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
                "Vorrat: ${m.currentStock()} Stück" + (days?.let { " (ca. $it Tage)" } ?: ""),
                m.notes.takeIf { it.isNotBlank() },
            ),
            onClick = { editItem = m },
            onDelete = { vm.delete(m) },
            photoName = m.photoPath,
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
                formatAppointmentTime(a.whenMillis, a.endMillis),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoctorScreen(items: List<Doctor>, vm: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Doctor?>(null) }
    val context = LocalContext.current

    // Filter nach Art des Kontakts (nur sichtbar, wenn es mehrere Arten gibt)
    var filterValue by remember { mutableStateOf<String?>(null) }
    val categories = items.map { it.specialty.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    val active = filterValue?.takeIf { it in categories }
    val shown = if (active == null) items else items.filter { it.specialty.trim() == active }

    Column(Modifier.fillMaxSize()) {
    if (categories.size > 1) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = active == null, onClick = { filterValue = null }, label = { Text("Alle") })
            categories.forEach { c ->
                FilterChip(selected = active == c, onClick = { filterValue = c }, label = { Text(c) })
            }
        }
    }

    ListScreen(
        items = shown,
        emptyText = "Noch keine Kontakte (Ärzte, Pflegedienst, Apotheke, Pflegekasse …). Mit + hinzufügen.",
        onAdd = { showAdd = true },
        modifier = Modifier.weight(1f),
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
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    Box(modifier.fillMaxSize()) {
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
    photoName: String? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (photoName != null) {
                PhotoThumb(photoName, 64.dp)
                Spacer(Modifier.width(12.dp))
            }
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
