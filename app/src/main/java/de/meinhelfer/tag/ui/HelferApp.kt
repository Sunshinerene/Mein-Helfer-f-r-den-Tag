package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import de.meinhelfer.tag.media.DocumentStore
import androidx.compose.ui.unit.sp
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
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.OutlinedButton
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
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: ImageVector) {
    Heute("Heute", Icons.Default.Home),
    Medikamente("Medikamente", Icons.Default.Favorite),
    Termine("Termine", Icons.Default.DateRange),
    Dokumente("Dokumente", DocumentIcon),
    Aerzte("Kontakte", Icons.Default.Person),
    Infos("Infos", InfoIcon),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelferApp(vm: MainViewModel = viewModel()) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSummary by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var showReorder by rememberSaveable { mutableStateOf(false) }
    var showWegweiser by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showTour by rememberSaveable { mutableStateOf(false) }
    val appContext = LocalContext.current
    // Beim ersten Start einmal den Rundgang anbieten
    var showTourOffer by rememberSaveable { mutableStateOf(!AppPrefs.tourSeen(appContext)) }
    val tab = Tab.entries[tabIndex]

    val meds by vm.medications.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val checkins by vm.checkins.collectAsStateWithLifecycle()
    val dayNote by vm.dayNote.collectAsStateWithLifecycle()
    val todayDay by vm.today.collectAsStateWithLifecycle()
    val todos by vm.todos.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val personId by vm.personId.collectAsStateWithLifecycle()

    // Entschluesselte Dokument-Kopien vom letzten Mal aufraeumen
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { DocumentStore.clearTemp(appContext) }
    }

    // Jede Minute: Uhrzeit und Datum aktualisieren (ueberfaellige Einnahmen, Tageswechsel)
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
            vm.refreshToday()
        }
    }

    BackHandler(enabled = showSettings || showSummary || showGuide || showTour || showReorder || showWegweiser) {
        when {
            showTour -> showTour = false
            showGuide -> showGuide = false
            showSettings -> showSettings = false
            showReorder -> showReorder = false
            showWegweiser -> showWegweiser = false
            else -> showSummary = false
        }
    }

    if (showTour) {
        TourScreen(onClose = { showTour = false })
    } else if (showGuide) {
        GuideScreen(onBack = { showGuide = false }, onStartTour = { showTour = true })
    } else if (showSettings) {
        SettingsScreen(
            vm = vm,
            onBack = { showSettings = false },
            onOpenGuide = { showGuide = true },
            onStartTour = { showTour = true },
        )
    } else if (showWegweiser) {
        WegweiserScreen(
            person = persons.firstOrNull { it.id == personId },
            onAddTodo = { vm.addTodo(it) },
            onBack = { showWegweiser = false },
        )
    } else if (showReorder) {
        ReorderScreen(
            meds = meds,
            doctors = doctors,
            personName = persons.firstOrNull { it.id == personId }?.name,
            onBack = { showReorder = false },
        )
    } else if (showSummary) {
        SummaryScreen(vm = vm, onBack = { showSummary = false })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Mein Helfer für den Tag") },
                    actions = {
                        IconButton(onClick = { showGuide = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Anleitung")
                        }
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
                            label = { Text(t.label, maxLines = 1, softWrap = false, fontSize = 10.sp) },
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
                        todos = todos,
                        todayDay = todayDay,
                        nowMillis = nowMillis,
                        updateState = updateState,
                        onOpenSettings = { showSettings = true },
                        onOpenSummary = { showSummary = true },
                        onOpenReorder = { showReorder = true },
                        person = persons.firstOrNull { it.id == personId },
                        onEditProfile = { showProfile = true },
                        onOpenWegweiser = { showWegweiser = true },
                    )
                    Tab.Medikamente -> MedicationScreen(meds, doctors, vm, onReorder = { showReorder = true })
                    Tab.Termine -> AppointmentScreen(appointments, doctors, documents, meds.size, persons.firstOrNull { it.id == personId }, vm)
                    Tab.Dokumente -> DocumentScreen(documents, doctors, vm)
                    Tab.Aerzte -> DoctorScreen(doctors, vm)
                    Tab.Infos -> InfoScreen(
                        person = persons.firstOrNull { it.id == personId },
                        meds = meds,
                        documents = documents,
                        onOpenWegweiser = { showWegweiser = true },
                    )
                }
              }
            }
        }
    }

    if (showProfile) {
        persons.firstOrNull { it.id == personId }?.let { p ->
            PersonDialog(
                initial = p,
                onDismiss = { showProfile = false },
                onSave = {
                    vm.updatePerson(it)
                    showProfile = false
                },
            )
        }
    }

    if (showTourOffer && !showTour) {
        AlertDialog(
            onDismissRequest = {
                AppPrefs.setTourSeen(appContext)
                showTourOffer = false
            },
            title = { Text("Willkommen!") },
            text = { Text("Möchtest du einen kurzen Rundgang durch die App? Du findest ihn später auch über das Info-Symbol oben.") },
            confirmButton = {
                TextButton(onClick = {
                    AppPrefs.setTourSeen(appContext)
                    showTourOffer = false
                    showTour = true
                }) { Text("Rundgang starten") }
            },
            dismissButton = {
                TextButton(onClick = {
                    AppPrefs.setTourSeen(appContext)
                    showTourOffer = false
                }) { Text("Später") }
            },
        )
    }
}

// ---------------------------------------------------------------- Medikamente

@Composable
private fun MedicationScreen(items: List<Medication>, doctors: List<Doctor>, vm: MainViewModel, onReorder: () -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Medication?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }

    val lowCount = lowStockMeds(items).size
    Column(Modifier.fillMaxSize()) {
        if (items.isNotEmpty()) {
            OutlinedButton(
                onClick = onReorder,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            ) {
                Text(if (lowCount > 0) "Nachbestellung ($lowCount fällig)" else "Nachbestellung")
            }
        }
        ListScreen(
            items = items,
            emptyText = "Noch keine Medikamente. Mit + hinzufügen.",
            onAdd = { showAdd = true },
            modifier = Modifier.weight(1f),
        ) { m ->
            val days = m.daysLeft()
            ItemCard(
                title = m.name + if (m.dosage.isNotBlank()) " – ${m.dosage}" else "",
                lines = listOfNotNull(
                    m.times.takeIf { it.isNotBlank() }?.let { "Einnahme: ${it.replace(",", ", ")} Uhr" },
                    "Vorrat: ${m.currentStock()} Stück" + (days?.let { " (ca. $it Tage)" } ?: ""),
                ),
                onClick = { detailId = m.id },
                onDelete = { vm.delete(m) },
                photoName = m.photoPath,
            ) {
                Row {
                    TextButton(onClick = { detailId = m.id }) { Text("Infos & Beschreibung") }
                    TextButton(onClick = { editItem = m }) { Text("Bearbeiten") }
                }
            }
        }
    }

    items.firstOrNull { it.id == detailId }?.let { med ->
        MedicationDetailDialog(
            med = med,
            doctors = doctors,
            otherNames = items.filter { it.id != med.id }.map { it.name },
            onEdit = {
                editItem = med
                detailId = null
            },
            onClose = { detailId = null },
            onUpdate = { vm.update(it) },
            onAddTodo = { vm.addTodo(it) },
        )
    }

    if (showAdd || editItem != null) {
        MedicationDialog(
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

// ---------------------------------------------------------------- Termine

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppointmentScreen(
    items: List<Appointment>,
    doctors: List<Doctor>,
    documents: List<Document>,
    medCount: Int,
    person: Person?,
    vm: MainViewModel,
) {
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Appointment?>(null) }
    var docsFor by remember { mutableStateOf<Appointment?>(null) }

    ListScreen(
        items = items,
        emptyText = "Noch keine Termine. Mit + hinzufügen.",
        onAdd = { showAdd = true },
    ) { a ->
        val doctor = doctors.firstOrNull { it.id == a.doctorId }
        val attached = a.attachedIds().mapNotNull { id -> documents.firstOrNull { it.id == id } }
        ItemCard(
            title = a.title,
            lines = listOfNotNull(
                formatAppointmentTime(a.whenMillis, a.endMillis),
                doctor?.let { doctorLabel(it) },
                a.place.takeIf { it.isNotBlank() },
                a.notes.takeIf { it.isNotBlank() },
                attached.takeIf { it.isNotEmpty() }?.let { list -> "Unterlagen: " + list.joinToString(", ") { it.title } },
            ),
            onClick = { editItem = a },
            onDelete = { vm.delete(a) },
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { docsFor = a }) {
                    Text(if (attached.isEmpty()) "Unterlagen" else "Unterlagen (${attached.size})")
                }
                TextButton(onClick = { addToCalendar(context, a, doctor?.let { doctorLabel(it) }) }) {
                    Text("In Kalender eintragen")
                }
            }
        }
    }

    docsFor?.let { a ->
        AppointmentDocsDialog(
            appointment = a,
            doctor = doctors.firstOrNull { it.id == a.doctorId },
            doctors = doctors,
            docs = documents,
            medCount = medCount,
            person = person,
            onSave = { ids ->
                vm.update(a.copy(docIds = ids.joinToString(",")))
                docsFor = null
            },
            onAddTodo = { vm.addTodo(it) },
            onClose = { docsFor = null },
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

/** Oeffnet den Kalender mit vorausgefuelltem Termin; gespeichert wird erst nach Bestaetigung dort. */
private fun addToCalendar(context: Context, a: Appointment, doctorText: String?) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = android.provider.CalendarContract.Events.CONTENT_URI
        putExtra(android.provider.CalendarContract.Events.TITLE, a.title)
        putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, a.whenMillis)
        putExtra(
            android.provider.CalendarContract.EXTRA_EVENT_END_TIME,
            a.endMillis ?: (a.whenMillis + 60L * 60 * 1000),
        )
        if (a.place.isNotBlank()) putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, a.place)
        val description = listOfNotNull(doctorText, a.notes.takeIf { it.isNotBlank() }).joinToString("\n")
        if (description.isNotBlank()) putExtra(android.provider.CalendarContract.Events.DESCRIPTION, description)
    }
    launch(context, intent)
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
internal fun <T> ListScreen(
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
internal fun ItemCard(
    title: String,
    lines: List<String>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    photoName: String? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }

    HCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
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
