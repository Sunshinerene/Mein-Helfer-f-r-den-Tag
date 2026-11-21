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
import androidx.compose.material.icons.filled.Call
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
    // Ohne Zustimmung zu den Nutzungsbedingungen keine Nutzung
    val gateContext = LocalContext.current
    var consentOk by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { consentOk = de.meinhelfer.tag.legal.ConsentStore.current(gateContext) != null }
    when (consentOk) {
        null -> {
            Box(Modifier.fillMaxSize())
            return
        }
        false -> {
            ConsentScreen(
                onAccepted = { consentOk = true },
                onDecline = { (gateContext as? android.app.Activity)?.finish() },
            )
            return
        }
        else -> Unit
    }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSummary by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var showReorder by rememberSaveable { mutableStateOf(false) }
    var showWegweiser by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showTour by rememberSaveable { mutableStateOf(false) }
    var showCaregiver by rememberSaveable { mutableStateOf(false) }
    var showNotruf by rememberSaveable { mutableStateOf(false) }
    var showLetters by rememberSaveable { mutableStateOf(false) }
    var showMedOverview by rememberSaveable { mutableStateOf(false) }
    val appContext = LocalContext.current
    var caregiver by remember { mutableStateOf(AppPrefs.caregiver(appContext)) }
    // Beim ersten Start einmal den Rundgang anbieten
    var showTourOffer by rememberSaveable { mutableStateOf(!AppPrefs.tourSeen(appContext)) }
    val tab = Tab.entries[tabIndex]

    // Nach dem Willkommens-Dialog einmal nach den eigenen Daten fragen
    LaunchedEffect(showTourOffer, showTour) {
        if (!showTourOffer && !showTour && !caregiver.isSet && !AppPrefs.caregiverAsked(appContext)) {
            AppPrefs.setCaregiverAsked(appContext)
            showCaregiver = true
        }
    }

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

    BackHandler(enabled = showSettings || showSummary || showGuide || showTour || showReorder || showWegweiser || showLetters || showMedOverview) {
        when {
            showMedOverview -> showMedOverview = false
            showTour -> showTour = false
            showGuide -> showGuide = false
            showSettings -> showSettings = false
            showReorder -> showReorder = false
            showLetters -> showLetters = false
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
            onConsentRevoked = {
                showSettings = false
                consentOk = false
            },
            onBack = { showSettings = false },
            onOpenGuide = { showGuide = true },
            onStartTour = { showTour = true },
            caregiver = caregiver,
            onEditCaregiver = { showCaregiver = true },
        )
    } else if (showWegweiser) {
        WegweiserScreen(
            person = persons.firstOrNull { it.id == personId },
            onAddTodo = { vm.addTodo(it) },
            onBack = { showWegweiser = false },
        )
    } else if (showLetters) {
        LetterScreen(
            person = persons.firstOrNull { it.id == personId },
            doctors = doctors,
            meds = meds,
            caregiver = caregiver,
            vm = vm,
            onBack = { showLetters = false },
        )
    } else if (showMedOverview) {
        MedOverviewScreen(
            meds = meds,
            doctors = doctors,
            person = persons.firstOrNull { it.id == personId },
            vm = vm,
            onBack = { showMedOverview = false },
        )
    } else if (showReorder) {
        ReorderScreen(
            meds = meds,
            doctors = doctors,
            personName = persons.firstOrNull { it.id == personId }?.name,
            onBack = { showReorder = false },
            caregiver = caregiver,
        )
    } else if (showSummary) {
        SummaryScreen(vm = vm, onBack = { showSummary = false })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Mein Helfer für den Tag") },
                    actions = {
                        IconButton(onClick = { showNotruf = true }) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = "Notruf und Hilfe",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
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
                    val navLabelSize = remember(androidx.compose.ui.platform.LocalDensity.current.fontScale) { androidx.compose.runtime.mutableFloatStateOf(12f) }
                    Tab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = i == tabIndex,
                            onClick = { tabIndex = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { FitText(t.label, maxSize = 12.sp, shared = navLabelSize) },
                        )
                    }
                }
            },
        ) { inner ->
            Column(Modifier.padding(inner).fillMaxSize()) {
              PersonBar(persons = persons, currentId = personId, vm = vm)
              Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Tab.Heute -> Column(Modifier.fillMaxSize()) {
                        PeriodSwitch(selected = ViewPeriod.entries[periodIndex]) { periodIndex = it.ordinal }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (ViewPeriod.entries[periodIndex]) {
                                ViewPeriod.Tag -> TodayScreen(
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
                                    caregiver = caregiver,
                                    documents = documents,
                                    onEditCaregiver = { showCaregiver = true },
                                )
                                ViewPeriod.Woche -> WeekOverview(
                                    vm = vm,
                                    appointments = appointments,
                                    todos = todos,
                                    meds = meds,
                                    doctors = doctors,
                                    todayDay = todayDay,
                                )
                                ViewPeriod.Monat -> MonthOverview(
                                    vm = vm,
                                    appointments = appointments,
                                    todos = todos,
                                    meds = meds,
                                    doctors = doctors,
                                    todayDay = todayDay,
                                )
                            }
                        }
                    }
                    Tab.Medikamente -> MedicationScreen(meds, doctors, vm, onReorder = { showReorder = true }, onOverview = { showMedOverview = true })
                    Tab.Termine -> AppointmentScreen(appointments, doctors, documents, meds, persons.firstOrNull { it.id == personId }, vm, caregiver) { showCaregiver = true }
                    Tab.Dokumente -> DocumentScreen(documents, doctors, persons.firstOrNull { it.id == personId }, vm, onOpenLetters = { showLetters = true })
                    Tab.Aerzte -> DoctorScreen(
                        doctors, vm, persons.firstOrNull { it.id == personId }, meds,
                        caregiver, onEditCaregiver = { showCaregiver = true },
                        onOpenLetters = { showLetters = true },
                    )
                    Tab.Infos -> InfoScreen(
                        person = persons.firstOrNull { it.id == personId },
                        meds = meds,
                        documents = documents,
                        onOpenWegweiser = { showWegweiser = true },
                        caregiver = caregiver,
                        onEditCaregiver = { showCaregiver = true },
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

    if (showNotruf) {
        NotrufDialog(
            person = persons.firstOrNull { it.id == personId },
            doctors = doctors,
            onSaveContact = { vm.add(it) },
            onClose = { showNotruf = false },
            meds = meds,
            caregiver = caregiver,
        )
    }

    if (showCaregiver) {
        CaregiverDialog(
            initial = caregiver,
            onDismiss = { showCaregiver = false },
            onSave = {
                AppPrefs.setCaregiver(appContext, it)
                caregiver = it
                showCaregiver = false
            },
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicationScreen(
    items: List<Medication>,
    doctors: List<Doctor>,
    vm: MainViewModel,
    onReorder: () -> Unit,
    onOverview: () -> Unit = {},
) {
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Medication?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }

    val lowCount = lowStockMeds(items).size
    Column(Modifier.fillMaxSize()) {
        if (items.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onOverview, modifier = Modifier.weight(1f)) {
                    FitText("Überblick & Check", maxSize = 14.sp)
                }
                OutlinedButton(onClick = onReorder, modifier = Modifier.weight(1f)) {
                    FitText(if (lowCount > 0) "Nachbestellen ($lowCount)" else "Nachbestellen", maxSize = 14.sp)
                }
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
                if (days != null && days <= 7) {
                    Text(
                        if (days <= 0) "Vorrat aufgebraucht – bitte nachbestellen" else "Bald nachbestellen",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                FlowRow {
                    TextButton(onClick = { detailId = m.id }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Infos") }
                    TextButton(onClick = { editItem = m }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Bearbeiten") }
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
    meds: List<Medication>,
    person: Person?,
    vm: MainViewModel,
    caregiver: de.meinhelfer.tag.data.CaregiverProfile,
    onEditCaregiver: () -> Unit,
) {
    val medCount = meds.size
    val context = LocalContext.current
    var mailFor by remember { mutableStateOf<Appointment?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Appointment?>(null) }
    var docsFor by remember { mutableStateOf<Appointment?>(null) }
    var rideFor by remember { mutableStateOf<Appointment?>(null) }

    // Kommende Termine (ab heute) zuerst, vergangene eingeklappt und neueste zuerst
    val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val comingAll = items.filter { it.whenMillis >= todayStart }.sortedBy { it.whenMillis }
    // Bei Serien nur den naechsten Termin zeigen (alle stehen in Woche und Monat)
    val seriesRest = comingAll.filter { it.seriesId != 0L }.groupBy { it.seriesId }
    val coming = comingAll.filter { a -> a.seriesId == 0L || seriesRest[a.seriesId]?.firstOrNull()?.id == a.id }
    val earlier = items.filter { it.whenMillis < todayStart }.sortedByDescending { it.whenMillis }
    var showPast by rememberSaveable { mutableStateOf(false) }
    var deleteFor by remember { mutableStateOf<Appointment?>(null) }

    val apptItem: @Composable (Appointment) -> Unit = { a ->
        val doctor = doctors.firstOrNull { it.id == a.doctorId }
        val attached = a.attachedIds().mapNotNull { id -> documents.firstOrNull { it.id == id } }
        AppointmentCard(
            a = a,
            doctor = doctor,
            attached = attached,
            onEdit = { editItem = a },
            onDelete = { deleteFor = a },
            onDocs = { docsFor = a },
            onCalendar = { addToCalendar(context, a, doctor?.let { doctorLabel(it) }) },
            onFahrdienst = { rideFor = a },
            onMail = { mailFor = a },
            seriesInfo = de.meinhelfer.tag.data.Recurrence.describe(a)?.let { rule ->
                val more = (seriesRest[a.seriesId]?.size ?: 1) - 1
                if (a.whenMillis >= todayStart && more > 0) "$rule · danach noch $more Termine (alle in Woche und Monat)" else rule
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Text(
                "Noch keine Termine. Mit + hinzufügen.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (coming.isEmpty()) {
                    item {
                        Text(
                            "Keine kommenden Termine. Mit + einen neuen anlegen.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                lazyItems(coming, key = { it.id }) { apptItem(it) }
                if (earlier.isNotEmpty()) {
                    item {
                        OutlinedButton(onClick = { showPast = !showPast }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showPast) "Vergangene Termine ausblenden" else "Vergangene Termine anzeigen (${earlier.size})")
                        }
                    }
                    if (showPast) lazyItems(earlier, key = { it.id }) { apptItem(it) }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Termin hinzufügen")
        }
    }

    mailFor?.let { a ->
        val d = doctors.firstOrNull { it.id == a.doctorId }
        if (d != null) {
            MailDraftDialog(
                recipient = d,
                person = person,
                caregiver = caregiver,
                meds = meds,
                appointment = a,
                onEditProfile = onEditCaregiver,
                onClose = { mailFor = null },
            )
        }
    }

    rideFor?.let { a ->
        FahrdienstDialog(
            title = a.title,
            whenText = formatAppointmentTime(a.whenMillis, a.endMillis),
            place = a.place,
            doctor = doctors.firstOrNull { it.id == a.doctorId },
            doctors = doctors,
            person = person,
            onClose = { rideFor = null },
        )
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
        AppointmentEditor(initial = editItem, doctors = doctors, vm = vm) {
            showAdd = false
            editItem = null
        }
    }
    deleteFor?.let { a -> AppointmentDeleteDialog(a, vm) { deleteFor = null } }
}

// ---------------------------------------------------------------- Ärzte

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoctorScreen(
    items: List<Doctor>,
    vm: MainViewModel,
    person: Person?,
    meds: List<Medication>,
    caregiver: de.meinhelfer.tag.data.CaregiverProfile,
    onEditCaregiver: () -> Unit,
    onOpenLetters: () -> Unit = {},
) {
    var mailTo by remember { mutableStateOf<Doctor?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showNearby by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Doctor?>(null) }
    val context = LocalContext.current

    // Filter nach Art des Kontakts (nur sichtbar, wenn es mehrere Arten gibt)
    var filterValue by remember { mutableStateOf<String?>(null) }
    val categories = items.map { it.specialty.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    val active = filterValue?.takeIf { it in categories }
    val shown = if (active == null) items else items.filter { it.specialty.trim() == active }

    Column(Modifier.fillMaxSize()) {
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { showNearby = true }) { Text("In der Nähe suchen") }
        OutlinedButton(onClick = onOpenLetters) { Text("Schreiben / Vollmacht") }
    }
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
                d.phone.takeIf { it.isNotBlank() }?.let { "Tel. $it" },
                d.email.takeIf { it.isNotBlank() },
                d.address.takeIf { it.isNotBlank() },
                listOfNotNull(
                    d.floor.takeIf { it.isNotBlank() },
                    when (d.elevator) { 1 -> "mit Aufzug"; 2 -> "ohne Aufzug"; else -> null },
                ).joinToString(", ").takeIf { it.isNotEmpty() },
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
                    TextButton(onClick = { mailTo = d }) { Text("E-Mail") }
                }
            }
        }
    }
    }

    if (showNearby) {
        NearbyDialog(person = person, onSaveContact = { vm.add(it) }, onClose = { showNearby = false })
    }

    mailTo?.let { d ->
        MailDraftDialog(
            recipient = d,
            person = person,
            caregiver = caregiver,
            meds = meds,
            onEditProfile = onEditCaregiver,
            onClose = { mailTo = null },
        )
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
