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
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.runtime.key
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HelferApp(vm: MainViewModel = viewModel()) {
    // Ohne Zustimmung zu den Nutzungsbedingungen keine Nutzung
    val gateContext = LocalContext.current
    var consentStatus by remember { mutableStateOf<de.meinhelfer.tag.legal.ConsentStore.Status?>(null) }
    var consentCheck by remember { mutableIntStateOf(0) }
    LaunchedEffect(consentCheck) { consentStatus = de.meinhelfer.tag.legal.ConsentStore.status(gateContext) }
    when (val st = consentStatus) {
        null -> {
            Box(Modifier.fillMaxSize())
            return
        }
        de.meinhelfer.tag.legal.ConsentStore.Status.OK -> Unit
        else -> {
            ConsentScreen(
                reason = st,
                onAccepted = { consentStatus = de.meinhelfer.tag.legal.ConsentStore.Status.OK },
                onDecline = { (gateContext as? android.app.Activity)?.finish() },
            )
            return
        }
    }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var settingsCategory by rememberSaveable { mutableStateOf<String?>(null) }
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
    var showDailyStart by rememberSaveable { mutableStateOf(false) }
    var dailyPlanOnly by rememberSaveable { mutableStateOf(false) }
    var showMoodCheck by rememberSaveable { mutableStateOf(false) }
    var showMotivator by rememberSaveable { mutableStateOf(false) }
    var motivatorTab by rememberSaveable { mutableIntStateOf(0) }
    var showVitals by rememberSaveable { mutableStateOf(false) }
    var showDiary by rememberSaveable { mutableStateOf(false) }
    var showBudget by rememberSaveable { mutableStateOf(false) }
    var showHandover by rememberSaveable { mutableStateOf(false) }
    val openScreen by vm.openScreen.collectAsStateWithLifecycle()
    LaunchedEffect(openScreen) {
        when (openScreen) {
            "trinken" -> showVitals = true
            "tagebuch" -> showDiary = true
        }
        if (openScreen != null) vm.requestScreen(null)
    }
    val appContext = LocalContext.current
    var caregiver by remember { mutableStateOf(AppPrefs.caregiver(appContext)) }
    // Beim ersten Start einmal den Rundgang anbieten
    var showTourOffer by rememberSaveable { mutableStateOf(!AppPrefs.tourSeen(appContext)) }
    val tab = Tab.entries[tabIndex]
    val jumpRegistry = remember { JumpRegistry() }

    /** Alle Unterseiten schliessen, z. B. wenn unten ein Reiter angetippt wird. */
    fun closeOverlays() {
        showSettings = false
        settingsCategory = null
        showSummary = false
        showGuide = false
        showReorder = false
        showWegweiser = false
        showLetters = false
        showMedOverview = false
        showDailyStart = false
        showMoodCheck = false
        showMotivator = false
        showVitals = false
        showDiary = false
        showBudget = false
        showHandover = false
    }

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
    val duties by vm.duties.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val checkins by vm.checkins.collectAsStateWithLifecycle()
    val dayNote by vm.dayNote.collectAsStateWithLifecycle()
    val todayDay by vm.today.collectAsStateWithLifecycle()
    val todos by vm.todos.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val personId by vm.personId.collectAsStateWithLifecycle()

    // Tagesstart: einmal am Tag je Person automatisch (Rueckblick auf gestern, dann Tagesplan)
    val hasData = meds.isNotEmpty() || appointments.isNotEmpty() || todos.isNotEmpty()
    LaunchedEffect(personId, todayDay, hasData, showTourOffer) {
        if (hasData && !showTourOffer && personId > 0 && AppPrefs.dailyStartEnabled(appContext) &&
            AppPrefs.lastDailyStart(appContext, personId) < todayDay
        ) {
            AppPrefs.setLastDailyStart(appContext, personId, todayDay)
            dailyPlanOnly = false
            showDailyStart = true
        }
    }

    // Stimmungs-Check: beim Oeffnen der App bzw. im neuen Zeitfenster (nicht beim allerersten Start)
    var resumeTick by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val moodMode by vm.moodMode.collectAsStateWithLifecycle()
    val moods by vm.moods.collectAsStateWithLifecycle()
    LaunchedEffect(resumeTick, moodMode, showTourOffer, showTour, nowSlot(moodMode)) {
        if (showTourOffer || showTour || showMoodCheck) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val last = moods.maxOfOrNull { it.atMillis } ?: 0L
        if (de.meinhelfer.tag.summary.MoodSchedule.isDue(moodMode, now, last, AppPrefs.moodAsked(appContext))) {
            AppPrefs.setMoodAsked(appContext, now)
            showMoodCheck = true
        }
    }

    // Entschluesselte Dokument-Kopien vom letzten Mal aufraeumen
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            DocumentStore.clearTemp(appContext)
            de.meinhelfer.tag.media.CardScanner.clear(appContext)
        }
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

    BackHandler(enabled = showSettings || showSummary || showGuide || showTour || showReorder || showWegweiser || showLetters || showMedOverview || showDailyStart || showMoodCheck || showMotivator || showVitals || showDiary || showBudget || showHandover) {
        when {
            showBudget -> showBudget = false
            showHandover -> showHandover = false
            showVitals -> showVitals = false
            showDiary -> showDiary = false
            showMoodCheck -> showMoodCheck = false
            showDailyStart -> showDailyStart = false
            showMotivator -> showMotivator = false
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
    } else {
    androidx.compose.runtime.CompositionLocalProvider(LocalJumps provides jumpRegistry) {
    // Die Leiste unten ist auf allen Seiten sichtbar; nach oben wischen zeigt die Bereiche der Seite
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            DockBar(tabIndex) { i ->
                closeOverlays()
                tabIndex = i
            }
        },
    ) { dockInner ->
    Box(Modifier.padding(dockInner).consumeWindowInsets(dockInner).fillMaxSize()) {
    if (showMoodCheck) {
        MoodCheckScreen(
            name = caregiver.name,
            professional = de.meinhelfer.tag.summary.Motivator.isProfessional(caregiver),
            onSave = { m, t, n -> vm.addMood(m, t, n) },
            onClose = { showMoodCheck = false },
        )
    } else if (showDailyStart) {
        DailyStartScreen(
            vm = vm,
            person = persons.firstOrNull { it.id == personId },
            meds = meds,
            appointments = appointments,
            todos = todos,
            doctors = doctors,
            checkins = checkins,
            todayDay = todayDay,
            startWithPlan = dailyPlanOnly,
            onDone = { showDailyStart = false },
        )
    } else if (showVitals) {
        VitalScreen(vm = vm, person = persons.firstOrNull { it.id == personId }, onBack = { showVitals = false })
    } else if (showDiary) {
        CareDiaryScreen(vm = vm, person = persons.firstOrNull { it.id == personId }, onBack = { showDiary = false })
    } else if (showBudget) {
        BudgetScreen(vm = vm, person = persons.firstOrNull { it.id == personId }, onBack = { showBudget = false })
    } else if (showHandover) {
        HandoverScreen(
            vm = vm,
            person = persons.firstOrNull { it.id == personId },
            meds = meds,
            appointments = appointments,
            todos = todos,
            doctors = doctors,
            caregiver = caregiver,
            onBack = { showHandover = false },
        )
    } else if (showMotivator) {
        MotivatorScreen(
            vm = vm,
            caregiver = caregiver,
            startTab = motivatorTab,
            onMood = { showMoodCheck = true },
            onBack = { showMotivator = false },
        )
    } else if (showGuide) {
        GuideScreen(onBack = { showGuide = false }, onStartTour = { showTour = true })
    } else if (showSettings) {
        SettingsScreen(
            vm = vm,
            initialCategory = settingsCategory,
            onConsentRevoked = {
                showSettings = false
                consentCheck++
            },
            onBack = {
                showSettings = false
                settingsCategory = null
            },
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
            duties = duties,
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
        ) { inner ->
            Column(Modifier.padding(inner).fillMaxSize()) {
              PersonBar(persons = persons, currentId = personId, vm = vm)
              Box(Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.animation.Crossfade(
                    targetState = tab,
                    animationSpec = if (LocalEffects.current.animations) androidx.compose.animation.core.tween<Float>(220) else androidx.compose.animation.core.snap<Float>(),
                    label = "reiter",
                ) { tab ->
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
                                      onOpenDailyStart = { planOnly ->
                                          dailyPlanOnly = planOnly
                                          showDailyStart = true
                                      },
                                      onOpenMotivator = { t ->
                                          motivatorTab = t
                                          showMotivator = true
                                      },
                                      onOpenMood = { showMoodCheck = true },
                                      onOpenBackup = {
                                          settingsCategory = "Sicherheit"
                                          showSettings = true
                                      },
                                      onOpenVitals = { showVitals = true },
                                      onOpenDiary = { showDiary = true },
                                      onOpenBudget = { showBudget = true },
                                      onOpenHandover = { showHandover = true },
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
                      Tab.Medikamente -> MedicationScreen(meds, doctors, vm, onReorder = { showReorder = true }, onOverview = { showMedOverview = true }, documents = documents, person = persons.firstOrNull { it.id == personId })
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
                          onOpenBudget = { showBudget = true },
                          onOpenHandover = { showHandover = true },
                          caregiver = caregiver,
                          onEditCaregiver = { showCaregiver = true },
                      )
                  }
                }
              }
            }
        }
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
                vm.refreshMotivator()
                // Andere pflegende Person (Name oder Rolle): Zustimmung muss neu bestaetigt werden
                consentCheck++
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
    documents: List<de.meinhelfer.tag.data.Document> = emptyList(),
    person: de.meinhelfer.tag.data.Person? = null,
) {
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Medication?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var photoFor by remember { mutableStateOf<Long?>(null) }
    var photoQueue by remember { mutableStateOf<List<Long>?>(null) }
    var queueIndex by remember { mutableIntStateOf(0) }
    val withoutPhoto = items.filter { it.photoPath == null }
    var stockFor by remember { mutableStateOf<Long?>(null) }
    var packFor by remember { mutableStateOf<Long?>(null) }
    var stockQueue by remember { mutableStateOf<List<Long>?>(null) }
    var stockIndex by remember { mutableIntStateOf(0) }
    val unknownStock = items.filter { !it.stockKnown }
    val duties by vm.duties.collectAsStateWithLifecycle()
    var showDuties by remember { mutableStateOf(false) }

    val lowCount = lowStockMeds(items).size
    Column(Modifier.fillMaxSize()) {
        MedPlanSuggestion(documents = documents, meds = items, person = person, vm = vm)
        if (items.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(shape = buttonShape(), onClick = onOverview, modifier = Modifier.weight(1f)) {
                    FitText("Überblick & Check", maxSize = 14.sp)
                }
                OutlinedButton(shape = buttonShape(), onClick = onReorder, modifier = Modifier.weight(1f)) {
                    FitText(if (lowCount > 0) "Nachbestellen ($lowCount)" else "Nachbestellen", maxSize = 14.sp)
                }
            }
            if (withoutPhoto.isNotEmpty() || unknownStock.isNotEmpty()) {
                FlowRow(Modifier.padding(start = 8.dp)) {
                    if (unknownStock.isNotEmpty()) {
                        TextButton(onClick = {
                            stockIndex = 0
                            stockQueue = unknownStock.map { it.id }
                        }) { Text("📦 Vorrat eintragen (${unknownStock.size})") }
                    }
                    if (withoutPhoto.isNotEmpty()) {
                        TextButton(onClick = {
                            queueIndex = 0
                            photoQueue = withoutPhoto.map { it.id }
                        }) { Text("📷 Fotos ergänzen (${withoutPhoto.size})") }
                    }
                }
            }
        }
        if (doctors.isNotEmpty()) {
            DutySummaryRow(person, doctors, duties, onEdit = { showDuties = true }, modifier = Modifier.padding(start = 16.dp, end = 8.dp))
        }
        ListScreen(
            items = items,
            emptyText = "Noch keine Medikamente. Mit + hinzufügen.",
            onAdd = { showAdd = true },
            modifier = Modifier.weight(1f),
            jumpLabel = { it.name },
        ) { m ->
            val days = m.daysLeft()
            ItemCard(
                title = m.name + if (m.dosage.isNotBlank()) " – ${m.dosage}" else "",
                lines = listOfNotNull(
                    m.times.takeIf { it.isNotBlank() }?.let { "Einnahme: ${it.replace(",", ", ")} Uhr" },
                    m.stockText() + (days?.let { " (ca. $it Tage)" } ?: ""),
                ),
                onClick = { detailId = m.id },
                onDelete = { vm.delete(m) },
                photoName = m.photoPath,
                onPhotoClick = { photoFor = m.id },
            ) {
                if (days != null && days <= 7) {
                    Text(
                        if (days <= 0) "Vorrat aufgebraucht – bitte nachbestellen" else "Bald nachbestellen",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!m.stockKnown) {
                    Text("Vorrat noch nicht eingetragen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                }
                FlowRow {
                    TextButton(onClick = { detailId = m.id }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Infos") }
                    TextButton(onClick = { editItem = m }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Bearbeiten") }
                    TextButton(onClick = { stockFor = m.id }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(if (m.stockKnown) "Vorrat" else "Vorrat eintragen")
                    }
                    if (m.packSize > 0) {
                        TextButton(onClick = { packFor = m.id }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Neue Packung")
                        }
                    }
                }
            }
        }
    }

    if (showDuties) {
        DutyDialog(person = person, doctors = doctors, duties = duties, vm = vm, onClose = { showDuties = false })
    }

    items.firstOrNull { it.id == detailId }?.let { med ->
        MedicationDetailDialog(
            med = med,
            doctors = doctors,
            duties = duties,
            otherNames = items.filter { it.id != med.id }.map { it.name },
            onEdit = {
                editItem = med
                detailId = null
            },
            onClose = { detailId = null },
            onUpdate = { vm.update(it) },
            onAddTodo = { vm.addTodo(it) },
            onPhoto = {
                photoFor = med.id
                detailId = null
            },
        )
    }

    photoFor?.let { id ->
        items.firstOrNull { it.id == id }?.let { m ->
            key(m.id) { MedPhotoDialog(m, vm, onDone = { photoFor = null }) }
        }
    }
    stockFor?.let { id ->
        items.firstOrNull { it.id == id }?.let { m ->
            key(m.id) { StockDialog(m, vm, onDone = { stockFor = null }) }
        }
    }
    packFor?.let { id ->
        items.firstOrNull { it.id == id }?.let { m ->
            key(m.id) { StockDialog(m, vm, onDone = { packFor = null }, addPack = true) }
        }
    }
    stockQueue?.let { queue ->
        val current = queue.drop(stockIndex).firstNotNullOfOrNull { id -> items.firstOrNull { it.id == id } }
        if (current == null) {
            LaunchedEffect(Unit) { stockQueue = null }
        } else {
            val pos = queue.indexOf(current.id)
            val next = { if (pos + 1 < queue.size) stockIndex = pos + 1 else stockQueue = null }
            key(current.id) {
                StockDialog(
                    med = current,
                    vm = vm,
                    onDone = { stockQueue = null },
                    queueText = "${pos + 1} von ${queue.size}",
                    onSkip = next,
                    onSaved = next,
                )
            }
        }
    }
    photoQueue?.let { queue ->
        val current = queue.drop(queueIndex).firstNotNullOfOrNull { id -> items.firstOrNull { it.id == id } }
        if (current == null) {
            LaunchedEffect(Unit) { photoQueue = null }
        } else {
            val pos = queue.indexOf(current.id)
            val next = { if (pos + 1 < queue.size) queueIndex = pos + 1 else photoQueue = null }
            key(current.id) {
                MedPhotoDialog(
                    med = current,
                    vm = vm,
                    onDone = { photoQueue = null },
                    queueText = "${pos + 1} von ${queue.size}",
                    onSkip = next,
                    onSaved = next,
                )
            }
        }
    }

    if (showAdd || editItem != null) {
        MedicationDialog(
            initial = editItem,
            doctors = doctors,
            duties = duties,
            personId = editItem?.personId ?: person?.id ?: 1L,
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

    val apptList = androidx.compose.foundation.lazy.rememberLazyListState()
    val apptJumps = rememberJumpList(apptList)
    Box(Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Text(
                "Noch keine Termine. Mit + hinzufügen.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                state = apptList,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                apptJumps.reset()
                if (coming.isEmpty()) {
                    jumpItem(apptJumps, null) {
                        Text(
                            "Keine kommenden Termine. Mit + einen neuen anlegen.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                jumpItems(apptJumps, coming, label = { formatAppointmentTime(it.whenMillis, it.endMillis) + " – " + it.title }, key = { it.id }) { apptItem(it) }
                if (earlier.isNotEmpty()) {
                    jumpItem(apptJumps, "Vergangene Termine") {
                        OutlinedButton(shape = buttonShape(), onClick = { showPast = !showPast }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showPast) "Vergangene Termine ausblenden" else "Vergangene Termine anzeigen (${earlier.size})")
                        }
                    }
                    if (showPast) jumpItems(apptJumps, earlier, label = { null }, key = { it.id }) { apptItem(it) }
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
    var dutyFor by remember { mutableStateOf<Doctor?>(null) }
    val duties by vm.duties.collectAsStateWithLifecycle()
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
        OutlinedButton(shape = buttonShape(), onClick = { showNearby = true }) { Text("In der Nähe suchen") }
        OutlinedButton(shape = buttonShape(), onClick = onOpenLetters) { Text("Schreiben / Vollmacht") }
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
        jumpLabel = { doctorLabel(it) },
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
                person?.let { p ->
                    listOfNotNull(
                        if (de.meinhelfer.tag.data.Duties.defaultFor(duties, p.id, de.meinhelfer.tag.data.Duties.REZEPT) == d.id) "💊 Rezepte" else null,
                        if (de.meinhelfer.tag.data.Duties.defaultFor(duties, p.id, de.meinhelfer.tag.data.Duties.APOTHEKE) == d.id) "🏥 Apotheke" else null,
                    ).takeIf { it.isNotEmpty() }?.joinToString(" · ", postfix = " für ${p.name}")
                },
                de.meinhelfer.tag.data.Duties.substituteFor(duties, d.id)?.let { s ->
                    "Abwesend, Vertretung: " + (items.firstOrNull { it.id == s.contactId }?.name ?: "–") + " " + de.meinhelfer.tag.data.Duties.period(s)
                },
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
                TextButton(onClick = { dutyFor = d }) { Text("Zuständig …") }
            }
        }
    }
    }

    if (showNearby) {
        NearbyDialog(person = person, onSaveContact = { vm.add(it) }, onClose = { showNearby = false })
    }

    dutyFor?.let { d ->
        DutyDialog(person = person, doctors = items, duties = duties, vm = vm, onClose = { dutyFor = null }, contact = d)
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

// ---------------------------------------------------------------- Leiste unten mit Schnellsprung

/** Reiter-Leiste fuer alle Seiten. Nach oben wischen oder den Griff antippen zeigt die Bereiche der aktuellen Seite. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DockBar(selected: Int, onSelect: (Int) -> Unit) {
    val registry = LocalJumps.current
    var sheet by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(
        Modifier.pointerInput(Unit) {
            androidx.compose.foundation.gestures.awaitEachGesture {
                val down = androidx.compose.foundation.gestures.awaitFirstDown(
                    requireUnconsumed = false,
                    pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial,
                )
                var total = 0f
                while (true) {
                    val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    total += c.position.y - c.previousPosition.y
                    if (total < -40.dp.toPx()) {
                        // Wischen nach oben: Liste oeffnen, Antippen des Reiters verhindern
                        c.consume()
                        sheet = true
                        break
                    }
                }
            }
        },
    ) {
        androidx.compose.material3.Surface(
            color = androidx.compose.material3.NavigationBarDefaults.containerColor,
            modifier = Modifier.fillMaxWidth().clickable { sheet = true },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 1.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                )
                Text(
                    "  Bereiche",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        NavigationBar {
            val navLabelSize = remember(androidx.compose.ui.platform.LocalDensity.current.fontScale) { androidx.compose.runtime.mutableFloatStateOf(12f) }
            Tab.entries.forEachIndexed { i, t ->
                NavigationBarItem(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    icon = { Icon(t.icon, contentDescription = t.label) },
                    label = { FitText(t.label, maxSize = 12.sp, shared = navLabelSize) },
                )
            }
        }
    }
    if (sheet) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { sheet = false }) {
            val targets = registry.current()
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
                Text("Springen zu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                if (targets.isEmpty()) {
                    Text("Auf dieser Seite gibt es keine Bereiche zum Springen.", style = MaterialTheme.typography.bodyMedium)
                }
                targets.forEach { t ->
                    TextButton(
                        onClick = {
                            sheet = false
                            scope.launch { runCatching { t.jump() } }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(t.label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge) }
                }
                Text(
                    "Tipp: Die Leiste unten nach oben wischen öffnet diese Liste jederzeit.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Bausteine

@Composable
internal fun <T> ListScreen(
    items: List<T>,
    emptyText: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    jumpLabel: ((T) -> String)? = null,
    row: @Composable (T) -> Unit,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val jumps = rememberJumpList(listState)
    Box(modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Text(
                emptyText,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                jumps.reset()
                jumpItems(jumps, items, label = { jumpLabel?.invoke(it) }) { row(it) }
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
    onPhotoClick: (() -> Unit)? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }

    HCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (photoName != null) {
                PhotoThumb(photoName, 64.dp, if (onPhotoClick != null) Modifier.clickable { onPhotoClick() } else Modifier)
                Spacer(Modifier.width(12.dp))
            } else if (onPhotoClick != null) {
                AddPhotoThumb(64.dp, onPhotoClick)
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

/** Kennung des aktuellen Zeitfensters (aendert sich z. B. mittags), damit im neuen Fenster gefragt wird. */
private fun nowSlot(mode: de.meinhelfer.tag.summary.MoodMode): Long =
    if (mode.isSlotted) de.meinhelfer.tag.summary.MoodSchedule.slotStart(mode, System.currentTimeMillis()) ?: -1L else -1L
