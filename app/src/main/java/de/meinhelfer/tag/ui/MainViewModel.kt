package de.meinhelfer.tag.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.meinhelfer.tag.data.AppDatabase
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.DayNote
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Recurrence
import de.meinhelfer.tag.data.Todo
import de.meinhelfer.tag.data.Goal
import de.meinhelfer.tag.data.GoalStep
import de.meinhelfer.tag.data.MoodEntry
import de.meinhelfer.tag.summary.MoodMode
import de.meinhelfer.tag.summary.Motivator
import de.meinhelfer.tag.summary.MotivatorPlan
import de.meinhelfer.tag.media.DocumentStore
import de.meinhelfer.tag.media.PhotoStore
import de.meinhelfer.tag.reminder.ReminderScheduler
import de.meinhelfer.tag.update.AppPrefs
import de.meinhelfer.tag.update.UpdateChecker
import de.meinhelfer.tag.update.UpdateState
import de.meinhelfer.tag.summary.Period
import de.meinhelfer.tag.summary.Summary
import de.meinhelfer.tag.summary.SummaryBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    // ---- Betreute Personen: alles in der App gilt fuer die ausgewaehlte Person
    val persons: StateFlow<List<Person>> = db.personDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _personId = MutableStateFlow(AppPrefs.currentPerson(app))
    val personId: StateFlow<Long> = _personId

    val medications: StateFlow<List<Medication>> =
        combine(db.medicationDao().all(), _personId) { list, id -> list.filter { it.personId == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documents: StateFlow<List<Document>> =
        combine(db.documentDao().all(), _personId) { list, id -> list.filter { it.personId == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appointments: StateFlow<List<Appointment>> =
        combine(db.appointmentDao().all(), _personId) { list, id -> list.filter { it.personId == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val doctors: StateFlow<List<Doctor>> = db.doctorDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Aus einer Benachrichtigung gewuenschte Seite ("trinken", "tagebuch"), wird von der Oberflaeche abgeholt. */
    private val _openScreen = MutableStateFlow<String?>(null)
    val openScreen: StateFlow<String?> = _openScreen

    // Von einer anderen App geteilte Dateien: Dokumente übernimmt sie (einzeln oder als Sammel-Import)
    private val _sharedUris = MutableStateFlow<List<android.net.Uri>?>(null)
    val sharedUris: StateFlow<List<android.net.Uri>?> = _sharedUris

    fun receiveShared(uris: List<android.net.Uri>) {
        _sharedUris.value = uris
    }

    fun consumeShared() {
        _sharedUris.value = null
    }

    // Einnahme-Fenster aus der Erinnerung öffnen (Uhrzeit "08:00"), die Heute-Seite zeigt es dann
    private val _intakeRequest = MutableStateFlow<String?>(null)
    val intakeRequest: StateFlow<String?> = _intakeRequest

    fun requestIntake(slot: String?) {
        _intakeRequest.value = slot
    }

    fun requestScreen(screen: String?) {
        _openScreen.value = screen
    }

    /** Trinkmengen und Messwerte der aktuellen Person, neueste zuerst. */
    val vitals: StateFlow<List<de.meinhelfer.tag.data.VitalEntry>> =
        combine(db.vitalDao().all(), _personId) { list, id -> list.filter { it.personId == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Pflegetagebuch der aktuellen Person, neuester Tag zuerst. */
    val careLog: StateFlow<List<de.meinhelfer.tag.data.CareLogEntry>> =
        combine(db.careLogDao().all(), _personId) { list, id -> list.filter { it.personId == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addVital(
        kind: String,
        value: Double,
        value2: Double? = null,
        unit: String = "",
        note: String = "",
        atMillis: Long = System.currentTimeMillis(),
        context: String = "",
    ) =
        viewModelScope.launch {
            val day = java.time.Instant.ofEpochMilli(atMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
            db.vitalDao().insert(
                de.meinhelfer.tag.data.VitalEntry(
                    personId = _personId.value, atMillis = atMillis, day = day, kind = kind,
                    value = value, value2 = value2, unit = unit, note = note.trim(), context = context,
                ),
            )
        }

    // ---- Messwerte: Schnellknöpfe und Messzeiten je Person (in den Einstellungen gespeichert)
    private val _measurePlanTick = MutableStateFlow(0)
    /** Zählt hoch, wenn Messzeiten oder Schnellknöpfe geändert wurden (damit Anzeigen neu rechnen). */
    val measurePlanTick: StateFlow<Int> = _measurePlanTick

    fun setMeasureSettings(personId: Long, quick: List<String>, plan: String) {
        val app = getApplication<Application>()
        AppPrefs.setVitalQuick(app, personId, quick)
        AppPrefs.setMeasurePlan(app, personId, plan)
        de.meinhelfer.tag.reminder.ReminderScheduler.scheduleMeasures(app, personId)
        _measurePlanTick.value++
    }

    fun deleteVital(item: de.meinhelfer.tag.data.VitalEntry) = viewModelScope.launch { db.vitalDao().delete(item) }

    /** Werte aus Health Connect (nach Bestätigung) für die aktuelle Person speichern, mit Quelle als Notiz. */
    fun importVitals(values: List<de.meinhelfer.tag.summary.HealthValue>, labels: Map<String, String>) = viewModelScope.launch {
        val zone = java.time.ZoneId.systemDefault()
        values.forEach { v ->
            val day = java.time.Instant.ofEpochMilli(v.atMillis).atZone(zone).toLocalDate().toEpochDay()
            val source = "aus " + (labels[v.source] ?: v.source) + " (Health Connect)"
            db.vitalDao().insert(
                de.meinhelfer.tag.data.VitalEntry(
                    personId = _personId.value, atMillis = v.atMillis, day = day, kind = v.kind,
                    value = v.value, value2 = v.value2, unit = v.unit,
                    note = listOf(v.note, source).filter { it.isNotBlank() }.joinToString(" · "),
                ),
            )
        }
    }

    /** Selbsttest: Version, Pruefung der Datenbank und Anzahl der Eintraege je Tabelle. */
    suspend fun dbCheck(): List<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val out = mutableListOf<String>()
        val sdb = db.openHelper.readableDatabase
        val expected = de.meinhelfer.tag.data.AppDatabase.ALL_MIGRATIONS.last().endVersion
        out += "Datenbank-Version ${sdb.version} (erwartet $expected)" + if (sdb.version == expected) " ✓" else " ✗"
        runCatching {
            sdb.query("PRAGMA quick_check").use { c -> if (c.moveToFirst()) out += "Prüfung der Datei: " + c.getString(0) + if (c.getString(0) == "ok") " ✓" else " ✗" }
        }.onFailure { out += "Prüfung der Datei nicht möglich: ${it.message}" }
        listOf(
            "Person", "Medication", "Appointment", "Doctor", "Checkin", "DayNote", "Todo", "Document", "ConsentRecord",
            "MoodEntry", "Goal", "GoalStep", "Responsibility", "VitalEntry", "CareLogEntry", "BudgetEntry",
        ).forEach { t ->
            runCatching { sdb.query("SELECT COUNT(*) FROM `$t`").use { c -> c.moveToFirst(); out += "$t: ${c.getInt(0)} Einträge" } }
                .onFailure { out += "$t: fehlt ✗" }
        }
        out
    }

    /** Ausgaben aus den Budgets der Pflegekasse fuer die aktuelle Person. */
    val budget: StateFlow<List<de.meinhelfer.tag.data.BudgetEntry>> =
        combine(db.budgetDao().all(), _personId) { list, id -> list.filter { it.personId == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveBudget(item: de.meinhelfer.tag.data.BudgetEntry) = viewModelScope.launch {
        if (item.id == 0L) db.budgetDao().insert(item.copy(personId = _personId.value)) else db.budgetDao().update(item)
    }

    fun deleteBudget(item: de.meinhelfer.tag.data.BudgetEntry) = viewModelScope.launch { db.budgetDao().delete(item) }

    fun saveCareLog(item: de.meinhelfer.tag.data.CareLogEntry) = viewModelScope.launch {
        if (item.id == 0L) db.careLogDao().insert(item.copy(personId = _personId.value)) else db.careLogDao().update(item)
    }

    fun deleteCareLog(item: de.meinhelfer.tag.data.CareLogEntry) = viewModelScope.launch { db.careLogDao().delete(item) }

    /** Alle Eintraege eines Tages auf einen anderen Tag uebernehmen ("wie gestern"). */
    fun copyCareDay(fromDay: Long, toDay: Long) = viewModelScope.launch {
        val pid = _personId.value
        val now = System.currentTimeMillis()
        db.careLogDao().range(pid, fromDay, fromDay).forEachIndexed { i, e ->
            db.careLogDao().insert(e.copy(id = 0, day = toDay, atMillis = now + i))
        }
    }

    /** Zustaendigkeiten (Arzt fuer Rezepte, Apotheke) und Vertretungen, fuer alle Personen. */
    val duties: StateFlow<List<de.meinhelfer.tag.data.Responsibility>> = db.responsibilityDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Setzt den zustaendigen Kontakt einer Rolle fuer die Person (null = keiner). */
    fun setDuty(personId: Long, role: String, contactId: Long?) = viewModelScope.launch {
        db.responsibilityDao().clearRole(personId, role)
        if (contactId != null) {
            db.responsibilityDao().insert(de.meinhelfer.tag.data.Responsibility(personId = personId, role = role, contactId = contactId))
        }
    }

    /** Vertretung eintragen: [substituteId] vertritt [absentId] im Zeitraum (gilt fuer alle Personen). */
    fun addSubstitute(absentId: Long, substituteId: Long, fromDay: Long?, untilDay: Long?) = viewModelScope.launch {
        db.responsibilityDao().insert(
            de.meinhelfer.tag.data.Responsibility(
                personId = 0,
                role = de.meinhelfer.tag.data.Duties.VERTRETUNG,
                contactId = substituteId,
                replacesId = absentId,
                fromDay = fromDay,
                untilDay = untilDay,
            ),
        )
    }

    fun deleteDuty(item: de.meinhelfer.tag.data.Responsibility) = viewModelScope.launch { db.responsibilityDao().delete(item) }

    // ---- "Heute": aktueller Tag (Epoch-Tag), Abhaken und Tagesbemerkung
    private val _today = MutableStateFlow(LocalDate.now().toEpochDay())
    val today: StateFlow<Long> = _today

    /** Abhak-Eintraege von heute bis 7 Tage voraus (Termine der naechsten Woche). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val checkins: StateFlow<List<Checkin>> = _today
        .flatMapLatest { d -> db.checkinDao().range(d - 1, d + 7) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Abhak-Einträge der letzten zwei Wochen (für gegebene Mengen und Vorschläge des Assistenten). */
    val recentCheckins: StateFlow<List<Checkin>> = _today
        .flatMapLatest { d -> db.checkinDao().range(d - 15, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _overviewRange = MutableStateFlow(LocalDate.now().toEpochDay() to LocalDate.now().toEpochDay())

    /** Abhak-Eintraege fuer die gerade gezeigte Woche bzw. den Monat. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val overviewCheckins: StateFlow<List<Checkin>> = _overviewRange
        .flatMapLatest { (from, to) -> db.checkinDao().range(from, to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setOverviewRange(from: Long, to: Long) {
        _overviewRange.value = from to to
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val dayNote: StateFlow<DayNote?> = combine(_today, _personId) { d, p -> d to p }
        .flatMapLatest { (d, p) -> db.dayNoteDao().range(p, d, d) }
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Alle Aufgaben der Person, auch ausgeblendete erledigte (Verlauf, Suche, Auswertung). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val taskHistory: StateFlow<List<Todo>> = _personId
        .flatMapLatest { p -> db.todoDao().all(p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Tages-Motivator: Stimmung und Ziele der pflegenden Person (fuer alle betreuten Personen)
    val moods: StateFlow<List<MoodEntry>> = db.moodDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goals: StateFlow<List<Goal>> = db.goalDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goalSteps: StateFlow<List<GoalStep>> = db.goalDao().allSteps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _motivatorTick = MutableStateFlow(0)

    /** Neu berechnen (z. B. nach Abhaken eines eigenen Vorhabens oder geaenderten eigenen Daten). */
    fun refreshMotivator() {
        _motivatorTick.value++
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val recentCheckins = _today.flatMapLatest { d -> db.checkinDao().range(d - 3, d) }

    val motivator: StateFlow<MotivatorPlan?> = combine(
        combine(moods, goals, goalSteps) { m, g, s -> Triple(m, g, s) },
        combine(db.todoDao().everything(), recentCheckins, db.appointmentDao().all()) { t, c, a -> Triple(t, c, a) },
        combine(_today, _motivatorTick) { d, _ -> d },
    ) { (m, g, s), (t, c, a), day ->
        val app = getApplication<Application>()
        val done = AppPrefs.motivatorDone(app, day)
        val plan = Motivator.plan(
            today = day,
            hour = LocalTime.now().hour,
            caregiver = AppPrefs.caregiver(app),
            moods = m,
            todos = t,
            checkins = c,
            appointments = a,
            goals = g,
            steps = s,
            doneKeys = done,
        )
        // KI-Impuls von heute gehoert dazu, damit Karte und Motivator-Seite dieselben Vorhaben zeigen
        val impulse = de.meinhelfer.tag.ai.MotivatorImpulse.fromJson(AppPrefs.motivatorImpulse(app))?.takeIf { it.day == day }
        if (impulse == null) {
            plan
        } else {
            plan.copy(
                intentions = plan.intentions + impulse.intentions.mapIndexed { i, text ->
                    de.meinhelfer.tag.summary.Intention("ki:$i", text, "ki:$i" in done)
                },
                tips = (plan.tips + impulse.tips).distinct(),
                impulse = impulse.sentence.takeIf { it.isNotBlank() },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _motivatorOnToday = MutableStateFlow(AppPrefs.motivatorOnToday(app))
    val motivatorOnToday: StateFlow<Boolean> = _motivatorOnToday

    fun setMotivatorOnToday(on: Boolean) {
        _motivatorOnToday.value = on
        AppPrefs.setMotivatorOnToday(getApplication(), on)
    }

    private val _moodMode = MutableStateFlow(MoodMode.fromKey(AppPrefs.moodMode(app)))
    val moodMode: StateFlow<MoodMode> = _moodMode

    fun setMoodMode(mode: MoodMode) {
        _moodMode.value = mode
        AppPrefs.setMoodMode(getApplication(), mode.key)
        ReminderScheduler.scheduleMood(getApplication())
    }

    fun setMoodNotify(on: Boolean) {
        AppPrefs.setMoodNotify(getApplication(), on)
        ReminderScheduler.scheduleMood(getApplication())
    }

    fun addMood(mood: Int, tags: List<String>, note: String) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        db.moodDao().insert(
            MoodEntry(atMillis = now, day = LocalDate.now().toEpochDay(), mood = mood.coerceIn(1, 5), tags = tags.joinToString(","), note = note.trim()),
        )
        AppPrefs.setMoodAsked(getApplication(), now)
    }

    fun deleteMood(entry: MoodEntry) = viewModelScope.launch { db.moodDao().delete(entry) }

    /** Eigenes Vorhaben (ohne Aufgabe oder Schritt dahinter) abhaken bzw. zuruecknehmen. */
    fun toggleOwnIntention(key: String) {
        val app = getApplication<Application>()
        val day = _today.value
        val keys = AppPrefs.motivatorDone(app, day).toMutableSet()
        if (!keys.add(key)) keys.remove(key)
        AppPrefs.setMotivatorDone(app, day, keys)
        refreshMotivator()
    }

    /** Legt ein Ziel an oder aendert es; die Schritte werden in der angegebenen Reihenfolge gespeichert. */
    fun saveGoal(goal: Goal, steps: List<GoalStep>, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = if (goal.id == 0L) {
            db.goalDao().insert(goal.copy(createdMillis = if (goal.createdMillis > 0) goal.createdMillis else System.currentTimeMillis()))
        } else {
            db.goalDao().update(goal)
            goal.id
        }
        val old = db.goalDao().steps(id)
        val keep = steps.mapNotNull { it.id.takeIf { s -> s != 0L } }.toSet()
        old.filter { it.id !in keep }.forEach { db.goalDao().deleteStep(it) }
        steps.forEachIndexed { i, st ->
            val s = st.copy(goalId = id, position = i)
            if (s.id == 0L) db.goalDao().insertStep(s) else db.goalDao().updateStep(s)
        }
        onSaved(id)
    }

    fun setGoalStatus(goal: Goal, status: String) = viewModelScope.launch {
        db.goalDao().update(goal.copy(status = status, doneMillis = if (status == Goal.REACHED) System.currentTimeMillis() else null))
    }

    fun setGoalCheck(goal: Goal, json: String) = viewModelScope.launch {
        db.goalDao().update(goal.copy(aiCheck = json, aiCheckMillis = System.currentTimeMillis()))
    }

    fun deleteGoal(goal: Goal) = viewModelScope.launch {
        db.goalDao().deleteSteps(goal.id)
        db.goalDao().delete(goal)
    }

    fun toggleStep(step: GoalStep) = viewModelScope.launch {
        db.goalDao().updateStep(step.copy(doneMillis = if (step.doneMillis == null) System.currentTimeMillis() else null))
    }

    fun updateStep(step: GoalStep) = viewModelScope.launch { db.goalDao().updateStep(step) }

    fun addStep(goalId: Long, title: String, detail: String = "", dueDay: Long? = null) = viewModelScope.launch {
        val pos = (db.goalDao().steps(goalId).maxOfOrNull { it.position } ?: -1) + 1
        db.goalDao().insertStep(GoalStep(goalId = goalId, position = pos, title = title.trim(), detail = detail.trim(), dueDay = dueDay))
    }

    fun deleteStep(step: GoalStep) = viewModelScope.launch { db.goalDao().deleteStep(step) }

    /** Schritt eine Position nach oben (-1) oder unten (+1) verschieben. */
    fun moveStep(step: GoalStep, delta: Int) = viewModelScope.launch {
        val list = db.goalDao().steps(step.goalId).sortedBy { it.position }.toMutableList()
        val i = list.indexOfFirst { it.id == step.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@launch
        val a = list[i]
        list[i] = list[j]
        list[j] = a
        list.forEachIndexed { k, s -> if (s.position != k) db.goalDao().updateStep(s.copy(position = k)) }
    }

    /** Aufgabe aus dem Motivator heraus abhaken bzw. wieder oeffnen. */
    fun toggleTodoById(id: Long) = viewModelScope.launch {
        val t = db.todoDao().list().firstOrNull { it.id == id } ?: return@launch
        val done = !t.done
        db.todoDao().update(t.copy(done = done, doneMillis = if (done) System.currentTimeMillis() else null, archived = false))
    }

    // ---- Persoenlicher Assistent: sieht alle betreuten Personen zusammen
    private val _assistantOn = MutableStateFlow(AppPrefs.assistantEnabled(app))
    val assistantOn: StateFlow<Boolean> = _assistantOn

    private val _assistantName = MutableStateFlow(AppPrefs.assistantName(app))
    val assistantName: StateFlow<String> = _assistantName

    private val _assistantDismissed = MutableStateFlow(AppPrefs.assistantDismissed(app))
    val assistantDismissed: StateFlow<Set<String>> = _assistantDismissed

    fun setAssistantOn(on: Boolean) {
        _assistantOn.value = on
        AppPrefs.setAssistantEnabled(getApplication(), on)
        ReminderScheduler.scheduleAssistant(getApplication())
    }

    fun setAssistantName(name: String) {
        AppPrefs.setAssistantName(getApplication(), name)
        _assistantName.value = AppPrefs.assistantName(getApplication())
    }

    val allMedications: StateFlow<List<Medication>> = db.medicationDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAppointments: StateFlow<List<Appointment>> = db.appointmentDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTodos: StateFlow<List<Todo>> = db.todoDao().everything()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVitals: StateFlow<List<de.meinhelfer.tag.data.VitalEntry>> = db.vitalDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _ownHealth = MutableStateFlow<de.meinhelfer.tag.summary.OwnHealth?>(null)

    /** Deine eigenen Werte aus Health Connect (nur gelesen, nicht gespeichert), wenn eingeschaltet. */
    val ownHealth: StateFlow<de.meinhelfer.tag.summary.OwnHealth?> = _ownHealth
    private var ownHealthAt = 0L

    /** Eigene Werte neu lesen (höchstens alle 10 Minuten, außer [force]). */
    fun refreshOwnHealth(force: Boolean = false) = viewModelScope.launch {
        val app = getApplication<Application>()
        if (!AppPrefs.assistantOwnHealth(app) || !_assistantOn.value) {
            _ownHealth.value = null
            return@launch
        }
        if (!force && System.currentTimeMillis() - ownHealthAt < 10 * 60_000L && _ownHealth.value != null) return@launch
        val h = de.meinhelfer.tag.health.HealthConnectReader
        if (!h.available(app) || h.granted(app).none { it in h.OWN_PERMISSIONS }) return@launch
        val persons = db.personDao().list()
        val exclude = persons.flatMap { AppPrefs.healthSources(app, it.id) }.toSet()
        _ownHealth.value = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { h.readOwn(app, AppPrefs.healthOwnSources(app), exclude) }
        }.getOrNull()
        ownHealthAt = System.currentTimeMillis()
    }

    /** Vorschlag ablehnen: für diesen Anlass nicht mehr zeigen. */
    fun dismissProposal(key: String) {
        AppPrefs.addAssistantDismissed(getApplication(), key)
        _assistantDismissed.value = AppPrefs.assistantDismissed(getApplication())
    }

    /** Vorschlag übernehmen (nur nach Tipp auf den Knopf). */
    fun applyProposal(p: de.meinhelfer.tag.summary.Proposal) = viewModelScope.launch {
        when (val a = p.action) {
            is de.meinhelfer.tag.summary.ProposalAction.RaisePriority -> {
                val t = db.todoDao().byId(a.todoId) ?: return@launch
                db.todoDao().update(t.copy(priority = 2))
            }
            is de.meinhelfer.tag.summary.ProposalAction.Postpone -> {
                val t = db.todoDao().byId(a.todoId) ?: return@launch
                val moved = t.copy(dueDay = a.newDueDay)
                db.todoDao().update(moved)
                ReminderScheduler.scheduleTodo(getApplication(), moved)
            }
            is de.meinhelfer.tag.summary.ProposalAction.AdjustDailyUse -> {
                val m = db.medicationDao().byId(a.medId) ?: return@launch
                val today = LocalDate.now().toEpochDay()
                // Vorrat auf heute umrechnen, damit der neue Verbrauch nicht rückwirkend zählt
                db.medicationDao().update(m.copy(stock = m.currentStock(today), stockDay = today, dailyUse = a.daily))
            }
            is de.meinhelfer.tag.summary.ProposalAction.OpenedToday -> {
                val m = db.medicationDao().byId(a.medId) ?: return@launch
                db.medicationDao().update(m.copy(openedDay = LocalDate.now().toEpochDay()))
                ReminderScheduler.scheduleOpened(getApplication(), m.copy(openedDay = LocalDate.now().toEpochDay()))
            }
            is de.meinhelfer.tag.summary.ProposalAction.ReorderTask -> {
                val today = LocalDate.now().toEpochDay()
                db.todoDao().insert(
                    Todo(
                        personId = a.personId,
                        text = a.text,
                        createdMillis = System.currentTimeMillis(),
                        doctorId = a.doctorId,
                        priority = if (a.dueDay <= today) 2 else 1,
                        dueDay = a.dueDay,
                    ),
                )
            }
        }
        // Erledigt: nicht noch einmal vorschlagen
        dismissProposal(p.key)
    }

    /** Schritt aus dem Motivator heraus abhaken bzw. wieder oeffnen. */
    fun toggleStepById(id: Long) = viewModelScope.launch {
        val s = goalSteps.value.firstOrNull { it.id == id } ?: return@launch
        db.goalDao().updateStep(s.copy(doneMillis = if (s.doneMillis == null) System.currentTimeMillis() else null))
    }

    /** Aufgaben fuer die Listen: ohne ausgeblendete erledigte. */
    val todos: StateFlow<List<Todo>> = taskHistory
        .map { list -> list.filter { !it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Theme (System / Hell / Dunkel)
    private val _themeMode = MutableStateFlow(AppPrefs.themeMode(app))
    val themeMode: StateFlow<String> = _themeMode

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        AppPrefs.setThemeMode(getApplication(), mode)
    }

    private val _uiScale = MutableStateFlow(AppPrefs.uiScale(app))

    /** Größe der Oberfläche (Barrierefreiheit): Knöpfe, Abstände und Schrift zusammen. */
    val uiScale: StateFlow<Float> = _uiScale

    fun setUiScale(factor: Float) {
        _uiScale.value = factor
        AppPrefs.setUiScale(getApplication(), factor)
    }

    private val _fontFactor = MutableStateFlow(AppPrefs.fontFactor(app))
    val fontFactor: StateFlow<Float> = _fontFactor

    fun setFontFactor(factor: Float) {
        _fontFactor.value = factor
        AppPrefs.setFontFactor(getApplication(), factor)
    }

    private val _designStyle = MutableStateFlow(DesignStyle.fromKey(AppPrefs.designStyle(app)))
    val designStyle: StateFlow<DesignStyle> = _designStyle

    fun setDesignStyle(style: DesignStyle) {
        _designStyle.value = style
        AppPrefs.setDesignStyle(getApplication(), style.key)
    }

    private val _cornerStyle = MutableStateFlow(CornerStyle.fromKey(AppPrefs.cornerStyle(app)))
    val cornerStyle: StateFlow<CornerStyle> = _cornerStyle

    fun setCornerStyle(corners: CornerStyle) {
        _cornerStyle.value = corners
        AppPrefs.setCornerStyle(getApplication(), corners.key)
    }

    private val _effects = MutableStateFlow(AppPrefs.effects(app))
    val effects: StateFlow<Effects> = _effects

    fun setEffects(effects: Effects) {
        _effects.value = effects
        AppPrefs.setEffects(getApplication(), effects)
    }

    private val _designTweaks = MutableStateFlow(
        DesignStyle.entries.associateWith { AppPrefs.designTweak(app, it.key) },
    )
    val designTweaks: StateFlow<Map<DesignStyle, DesignTweak>> = _designTweaks

    fun setDesignTweak(style: DesignStyle, tweak: DesignTweak) {
        _designTweaks.value = _designTweaks.value + (style to tweak)
        AppPrefs.setDesignTweak(getApplication(), style.key, tweak)
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    init {
        // Mindestens eine Person muss es geben; die gespeicherte Auswahl muss gueltig sein.
        viewModelScope.launch {
            val dao = db.personDao()
            var list = dao.list()
            if (list.isEmpty()) {
                dao.insert(Person(name = "Betreute Person"))
                list = dao.list()
            }
            if (list.none { it.id == _personId.value }) selectPerson(list.first().id)
        }
        // Startbildschirm-Widget aktuell halten: Medikamente, Termine, Abhaken oder andere Person
        viewModelScope.launch {
            val d = LocalDate.now().toEpochDay()
            combine(db.medicationDao().all(), db.appointmentDao().all(), db.checkinDao().range(d - 1, d + 1), _personId) { _, _, _, _ -> }
                .collectLatest {
                    kotlinx.coroutines.delay(1500)
                    de.meinhelfer.tag.widget.HelperWidget.refresh(app)
                }
        }
        // Terminserien ohne Ende fortschreiben, dann Erinnerungen neu planen (Sicherheitsnetz bei jedem App-Start).
        viewModelScope.launch {
            extendSeries()
            ReminderScheduler.rescheduleAll(app)
        }
        if (AppPrefs.autoUpdateCheck(app)) checkForUpdate()
    }

    // ---------------------------------------------------------------- Medikamente

    private fun medListChanged(personId: Long) {
        AppPrefs.setMedListChanged(getApplication(), personId, System.currentTimeMillis())
    }

    fun add(item: Medication) = viewModelScope.launch {
        db.medicationDao().insert(item.copy(personId = _personId.value))
        medListChanged(_personId.value)
        ReminderScheduler.rescheduleAll(getApplication())
    }

    fun update(item: Medication) = viewModelScope.launch {
        val old = db.medicationDao().byId(item.id)
        db.medicationDao().update(item)
        // Nur echte Plan-Aenderungen zaehlen (nicht Vorrat, Foto, Beschreibung)
        if (old == null || old.name != item.name || old.dosage != item.dosage || old.times != item.times) medListChanged(item.personId)
        ReminderScheduler.rescheduleAll(getApplication())
    }

    fun delete(item: Medication) = viewModelScope.launch {
        db.medicationDao().delete(item)
        db.checkinDao().deleteFor(Checkin.MED, item.id)
        PhotoStore.delete(getApplication(), item.photoPath)
        medListChanged(item.personId)
    }

    /**
     * Uebernimmt einen Medikamentenplan: neue anlegen, geaenderte anpassen (Vorrat wird auf heute umgerechnet),
     * auf Wunsch nicht mehr enthaltene entfernen. Merkt sich den Stand des Plans.
     */
    fun applyMedPlan(personId: Long, adds: List<Medication>, updates: List<Medication>, removes: List<Medication>, planDay: Long, docId: Long?) =
        viewModelScope.launch {
            val app = getApplication<Application>()
            adds.forEach { db.medicationDao().insert(it.copy(personId = personId)) }
            updates.forEach { db.medicationDao().update(it) }
            removes.forEach {
                db.medicationDao().delete(it)
                db.checkinDao().deleteFor(Checkin.MED, it.id)
                PhotoStore.delete(app, it.photoPath)
            }
            AppPrefs.setMedPlanStand(app, personId, planDay)
            medListChanged(personId)
            docId?.let { AppPrefs.addMedPlanHandled(app, it) }
            ReminderScheduler.rescheduleAll(app)
        }

    // ---------------------------------------------------------------- Dokumente

    fun add(item: Document) = viewModelScope.launch {
        db.documentDao().insert(item.copy(personId = _personId.value))
    }

    /** Dokument speichern und die neue ID zurueckgeben (z. B. um es mit einer Aufgabe zu verknuepfen). */
    suspend fun addDocumentNow(item: Document): Long = db.documentDao().insert(item.copy(personId = _personId.value))

    fun update(item: Document) = viewModelScope.launch { db.documentDao().update(item) }

    /** Entwurf eines Schreibens als unterschrieben vermerken und die Aufgabe „ausdrucken, unterschreiben …“ abhaken. */
    fun markDraftSigned(draft: Document) = viewModelScope.launch {
        val day = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        db.documentDao().update(
            draft.copy(notes = listOf(draft.notes, "${de.meinhelfer.tag.letters.SignedCopies.SIGNED_NOTE} am $day.").filter { it.isNotBlank() }.joinToString("\n")),
        )
        val prefix = "„" + de.meinhelfer.tag.letters.SignedCopies.baseTitle(draft) + "“"
        db.todoDao().list().filter { !it.done && it.personId == draft.personId && it.text.startsWith(prefix) }.forEach {
            db.todoDao().update(it.copy(done = true, doneMillis = System.currentTimeMillis(), result = "Unterschriebene Fassung eingescannt"))
        }
    }

    fun delete(item: Document) = viewModelScope.launch {
        db.documentDao().delete(item)
        DocumentStore.delete(getApplication(), item.fileName)
        // Verknuepfungen an Terminen und Aufgaben entfernen
        fun without(ids: String): String =
            ids.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != item.id.toString() }.joinToString(",")
        db.appointmentDao().list().filter { a -> a.docIds.split(",").any { it.trim() == item.id.toString() } }
            .forEach { db.appointmentDao().update(it.copy(docIds = without(it.docIds))) }
        db.todoDao().list().filter { t -> t.docIds.split(",").any { it.trim() == item.id.toString() } }
            .forEach { db.todoDao().update(it.copy(docIds = without(it.docIds))) }
    }

    // ---------------------------------------------------------------- Termine

    /** Neuer Termin; mit Wiederholung werden die Folgetermine gleich mit angelegt. */
    fun add(item: Appointment) = viewModelScope.launch {
        val base = item.copy(
            id = 0,
            personId = _personId.value,
            seriesId = if (item.repeatDays != 0) System.currentTimeMillis() else 0L,
        )
        db.appointmentDao().insert(base)
        Recurrence.following(base).forEach { db.appointmentDao().insert(it) }
        ReminderScheduler.rescheduleAll(getApplication())
    }

    /** Einfaches Aendern (z. B. Unterlagen), ohne Serienlogik. */
    fun update(item: Appointment) = viewModelScope.launch {
        db.appointmentDao().update(item)
        ReminderScheduler.rescheduleAll(getApplication())
    }

    private fun epochDayOf(millis: Long): Long =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

    /** Fruehere Termine einer Serie enden vor [millis] (damit nichts neu angelegt wird). */
    private suspend fun endSeriesBefore(series: List<Appointment>, millis: Long) {
        val until = epochDayOf(millis) - 1
        series.filter { it.whenMillis < millis }.forEach {
            db.appointmentDao().update(it.copy(repeatUntil = minOf(it.repeatUntil ?: Long.MAX_VALUE, until)))
        }
    }

    /**
     * Termin speichern. Bei einer Serie: nur diesen Termin ([following] = false, Regel bleibt)
     * oder diesen und alle folgenden (folgende werden nach der neuen Regel neu angelegt).
     */
    fun saveAppointment(original: Appointment, saved: Appointment, following: Boolean) = viewModelScope.launch {
        val dao = db.appointmentDao()
        when {
            original.seriesId == 0L -> {
                if (saved.repeatDays != 0) {
                    val withSeries = saved.copy(seriesId = System.currentTimeMillis())
                    dao.update(withSeries)
                    Recurrence.following(withSeries).forEach { dao.insert(it) }
                } else {
                    dao.update(saved.copy(seriesId = 0L))
                }
            }
            !following -> dao.update(
                saved.copy(seriesId = original.seriesId, repeatDays = original.repeatDays, repeatUntil = original.repeatUntil),
            )
            else -> {
                val series = dao.list().filter { it.seriesId == original.seriesId }
                series.filter { it.whenMillis > original.whenMillis && it.id != original.id }.forEach {
                    dao.delete(it)
                    db.checkinDao().deleteFor(Checkin.APPT, it.id)
                }
                endSeriesBefore(series.filter { it.id != original.id }, original.whenMillis)
                val updated = saved.copy(seriesId = original.seriesId)
                dao.update(updated)
                Recurrence.following(updated).forEach { dao.insert(it) }
            }
        }
        ReminderScheduler.rescheduleAll(getApplication())
    }

    fun delete(item: Appointment) = deleteAppointment(item, following = false)

    /** Loescht einen Termin bzw. bei Serien auf Wunsch auch alle folgenden. */
    fun deleteAppointment(item: Appointment, following: Boolean) = viewModelScope.launch {
        val dao = db.appointmentDao()
        if (item.seriesId == 0L || !following) {
            dao.delete(item)
            db.checkinDao().deleteFor(Checkin.APPT, item.id)
        } else {
            val series = dao.list().filter { it.seriesId == item.seriesId }
            series.filter { it.whenMillis >= item.whenMillis }.forEach {
                dao.delete(it)
                db.checkinDao().deleteFor(Checkin.APPT, it.id)
            }
            endSeriesBefore(series, item.whenMillis)
        }
    }

    /** Serien ohne Ende fortschreiben, damit immer etwa ein halbes Jahr im Voraus angelegt ist. */
    private suspend fun extendSeries() {
        val dao = db.appointmentDao()
        dao.list().filter { it.seriesId != 0L }.groupBy { it.seriesId }.values.forEach { list ->
            val last = list.maxByOrNull { it.whenMillis } ?: return@forEach
            Recurrence.following(last).forEach { dao.insert(it) }
        }
    }

    // ---------------------------------------------------------------- Abhaken, Bemerkungen, Auswertung

    /** Datum neu lesen (z. B. nach Mitternacht). */
    fun refreshToday() {
        _today.value = LocalDate.now().toEpochDay()
    }

    /** Legt einen Abhak-Eintrag an oder aendert ihn; null = Wert unveraendert lassen. */
    fun saveCheckin(
        kind: String,
        refId: Long,
        day: Long,
        slot: String,
        done: Boolean? = null,
        note: String? = null,
        status: String? = null,
        actual: String? = null,
        /** Tatsächlich gegebene Menge (Einnahme-Fenster), null = unverändert */
        amount: Double? = null,
    ) = viewModelScope.launch {
        val normSlot = if (slot.isEmpty()) "" else SummaryBuilder.normSlot(slot)
        val dao = db.checkinDao()
        val now = System.currentTimeMillis()
        val existing = dao.find(kind, refId, day, normSlot)
        if (existing == null) {
            dao.insert(
                Checkin(
                    kind = kind, refId = refId, day = day, slot = normSlot,
                    done = done ?: false, note = note?.trim().orEmpty(), atMillis = now,
                    status = status.orEmpty(), actual = actual.orEmpty(), amount = amount,
                ),
            )
        } else {
            dao.update(
                existing.copy(
                    done = done ?: existing.done,
                    note = note?.trim() ?: existing.note,
                    atMillis = now,
                    status = status ?: existing.status,
                    actual = actual ?: existing.actual,
                    amount = amount ?: existing.amount,
                ),
            )
        }
    }

    /** Setzt Status und Haken zurueck (die Bemerkung bleibt). */
    fun resetCheckin(kind: String, refId: Long, day: Long, slot: String) =
        saveCheckin(kind, refId, day, slot, done = false, status = "", actual = "")

    /** Bemerkung zu einem Tag der aktuellen Person (leer, wenn keine). */
    suspend fun dayNoteText(day: Long): String = db.dayNoteDao().find(_personId.value, day)?.text.orEmpty()

    fun saveDayNote(day: Long, text: String) = viewModelScope.launch {
        val dao = db.dayNoteDao()
        val person = _personId.value
        val existing = dao.find(person, day)
        when {
            text.isBlank() -> if (existing != null) dao.delete(existing)
            existing == null -> dao.insert(DayNote(day = day, personId = person, text = text.trim()))
            else -> dao.update(existing.copy(text = text.trim()))
        }
    }

    // ---------------------------------------------------------------- To-dos

    fun addTodo(text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        db.todoDao().insert(Todo(personId = _personId.value, text = text.trim(), createdMillis = System.currentTimeMillis()))
    }

    fun addTasks(drafts: List<TaskDraft>) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        drafts.filter { it.text.isNotBlank() }.forEach {
            val todo = Todo(
                personId = _personId.value,
                text = it.text.trim(),
                createdMillis = now,
                doctorId = it.doctorId,
                priority = it.priority,
                dueDay = it.dueDay,
                outside = it.outside,
                remindAt = it.remindAt,
                docIds = it.docIds,
            )
            val id = db.todoDao().insert(todo)
            if (todo.remindAt != null) ReminderScheduler.scheduleTodo(getApplication(), todo.copy(id = id))
        }
    }

    fun updateTodo(item: Todo) = viewModelScope.launch {
        db.todoDao().update(item)
        ReminderScheduler.scheduleTodo(getApplication(), item)
    }

    fun setTodoDone(item: Todo, done: Boolean) = viewModelScope.launch {
        db.todoDao().update(item.copy(done = done, doneMillis = if (done) System.currentTimeMillis() else null, archived = false))
    }

    /** Ergebnis und Bewertung einer Aufgabe festhalten. */
    fun saveTodoResult(item: Todo, result: String, rating: Int) = viewModelScope.launch {
        db.todoDao().setResult(item.id, result.trim(), rating.coerceIn(0, 5))
    }

    /** Erledigte Aufgabe aus der Liste ausblenden (bleibt im Verlauf). */
    fun archiveTodo(item: Todo) = viewModelScope.launch { db.todoDao().archive(item.id) }

    /** Aufgabe aus dem Verlauf wieder als offen in die Liste holen. */
    fun reopenTodo(item: Todo) = viewModelScope.launch {
        db.todoDao().update(item.copy(done = false, doneMillis = null, archived = false))
    }

    fun deleteTodo(item: Todo) = viewModelScope.launch { db.todoDao().delete(item) }

    /** Blendet alle erledigten Aufgaben der aktuellen Person aus der Liste aus (sie bleiben im Verlauf). */
    fun archiveDoneTodos() = viewModelScope.launch { db.todoDao().archiveDone(_personId.value) }

    // ---------------------------------------------------------------- Personen

    /** Waehlt die Person nur, wenn es sie (noch) gibt, z. B. beim Oeffnen aus einer Benachrichtigung. */
    fun selectPersonIfExists(id: Long) = viewModelScope.launch {
        if (db.personDao().list().any { it.id == id }) selectPerson(id)
    }

    fun selectPerson(id: Long) {
        _personId.value = id
        AppPrefs.setCurrentPerson(getApplication(), id)
    }

    fun addPerson(name: String) = viewModelScope.launch {
        val id = db.personDao().insert(Person(name = name.trim()))
        selectPerson(id)
    }

    /** Neue Person mit Profil anlegen und auswaehlen. */
    fun addPerson(person: Person) = viewModelScope.launch {
        val id = db.personDao().insert(person.copy(id = 0, name = person.name.trim(), important = person.important.trim()))
        selectPerson(id)
    }

    fun updatePerson(person: Person) = viewModelScope.launch {
        db.personDao().update(person.copy(name = person.name.trim(), important = person.important.trim()))
    }

    fun renamePerson(person: Person, name: String) = viewModelScope.launch {
        db.personDao().update(person.copy(name = name.trim()))
    }

    /** Loescht die Person samt ihren Medikamenten, Terminen und Bemerkungen. Die letzte Person bleibt immer. */
    fun deletePerson(person: Person) = viewModelScope.launch {
        val others = db.personDao().list().filter { it.id != person.id }
        if (others.isEmpty()) return@launch
        db.medicationDao().listFor(person.id).forEach {
            db.medicationDao().delete(it)
            db.checkinDao().deleteFor(Checkin.MED, it.id)
            PhotoStore.delete(getApplication(), it.photoPath)
        }
        db.appointmentDao().listFor(person.id).forEach {
            db.appointmentDao().delete(it)
            db.checkinDao().deleteFor(Checkin.APPT, it.id)
        }
        db.documentDao().listFor(person.id).forEach {
            db.documentDao().delete(it)
            DocumentStore.delete(getApplication(), it.fileName)
        }
        db.dayNoteDao().deleteForPerson(person.id)
        db.todoDao().deleteForPerson(person.id)
        db.responsibilityDao().deleteForPerson(person.id)
        db.vitalDao().deleteForPerson(person.id)
        db.careLogDao().deleteForPerson(person.id)
        db.budgetDao().deleteForPerson(person.id)
        db.goalDao().deleteStepsForPerson(person.id)
        db.goalDao().deleteForPerson(person.id)
        db.personDao().delete(person)
        if (_personId.value == person.id) selectPerson(others.first().id)
        ReminderScheduler.rescheduleAll(getApplication())
    }

    /** Auswertung fuer Tag/Woche/Monat (offset = Perioden zurueck bzw. voraus), rein lokal berechnet. */
    suspend fun buildSummary(period: Period, offset: Int): Summary {
        val today = LocalDate.now()
        val (start, end) = SummaryBuilder.range(period, offset, today)
        val person = _personId.value
        return SummaryBuilder.build(
            period = period,
            start = start,
            end = end,
            today = today,
            now = LocalTime.now(),
            meds = db.medicationDao().listFor(person),
            appointments = db.appointmentDao().listFor(person),
            checkins = db.checkinDao().rangeList(start.toEpochDay(), end.toEpochDay()),
            dayNotes = db.dayNoteDao().rangeList(person, start.toEpochDay(), end.toEpochDay()),
            todos = db.todoDao().list().filter { it.personId == person },
            vitals = db.vitalDao().range(person, start.toEpochDay(), end.toEpochDay()),
            drinkGoal = AppPrefs.drinkGoal(getApplication(), person),
            careLog = db.careLogDao().range(person, start.toEpochDay(), end.toEpochDay()),
            ranges = de.meinhelfer.tag.summary.VitalKind.MEASURES.mapNotNull { k ->
                AppPrefs.vitalRange(getApplication(), person, k.key)?.let { k.key to it }
            }.toMap(),
            measurePlan = de.meinhelfer.tag.summary.MeasurePlans.parse(AppPrefs.measurePlan(getApplication(), person)),
            personId = person,
        )
    }

    // ---------------------------------------------------------------- Aerzte

    fun add(item: Doctor) = viewModelScope.launch { db.doctorDao().insert(item) }
    fun update(item: Doctor) = viewModelScope.launch { db.doctorDao().update(item) }
    /** Loescht einen Kontakt und entfernt ihn ueberall, wo er verknuepft war. */
    fun delete(item: Doctor) = viewModelScope.launch {
        db.doctorDao().delete(item)
        db.medicationDao().clearDoctor(item.id)
        db.medicationDao().clearPharmacy(item.id)
        db.responsibilityDao().deleteForContact(item.id)
        db.appointmentDao().clearDoctor(item.id)
        db.todoDao().clearDoctor(item.id)
        db.documentDao().clearDoctor(item.id)
    }

    // ---------------------------------------------------------------- App-Update

    fun checkForUpdate() {
        val current = _updateState.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return

        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            _updateState.value = try {
                val info = UpdateChecker.fetchLatest()
                val installed = UpdateChecker.installedVersion(getApplication())
                if (info != null && UpdateChecker.isNewer(info.version, installed)) {
                    UpdateState.Available(info)
                } else {
                    UpdateState.UpToDate
                }
            } catch (_: Exception) {
                UpdateState.Error("Keine Verbindung zu GitHub.")
            }
        }
    }

    fun downloadUpdate() {
        val info = (_updateState.value as? UpdateState.Available)?.info ?: return
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(info, 0f)
            _updateState.value = try {
                val file = UpdateChecker.download(getApplication(), info) { progress ->
                    _updateState.value = UpdateState.Downloading(info, progress)
                }
                UpdateState.Ready(info, file)
            } catch (_: Exception) {
                UpdateState.Error("Download fehlgeschlagen. Bitte erneut versuchen.")
            }
        }
    }
}
