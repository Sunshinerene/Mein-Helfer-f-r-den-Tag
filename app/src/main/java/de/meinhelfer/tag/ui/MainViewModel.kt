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
import de.meinhelfer.tag.data.Todo
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

    // ---- "Heute": aktueller Tag (Epoch-Tag), Abhaken und Tagesbemerkung
    private val _today = MutableStateFlow(LocalDate.now().toEpochDay())
    val today: StateFlow<Long> = _today

    /** Abhak-Eintraege von heute bis 7 Tage voraus (Termine der naechsten Woche). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val checkins: StateFlow<List<Checkin>> = _today
        .flatMapLatest { d -> db.checkinDao().range(d - 1, d + 7) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dayNote: StateFlow<DayNote?> = combine(_today, _personId) { d, p -> d to p }
        .flatMapLatest { (d, p) -> db.dayNoteDao().range(p, d, d) }
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val todos: StateFlow<List<Todo>> = _personId
        .flatMapLatest { p -> db.todoDao().all(p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Theme (System / Hell / Dunkel)
    private val _themeMode = MutableStateFlow(AppPrefs.themeMode(app))
    val themeMode: StateFlow<String> = _themeMode

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        AppPrefs.setThemeMode(getApplication(), mode)
    }

    private val _designStyle = MutableStateFlow(DesignStyle.fromKey(AppPrefs.designStyle(app)))
    val designStyle: StateFlow<DesignStyle> = _designStyle

    fun setDesignStyle(style: DesignStyle) {
        _designStyle.value = style
        AppPrefs.setDesignStyle(getApplication(), style.key)
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
        // Sicherheitsnetz: Erinnerungen bei jedem App-Start neu planen.
        viewModelScope.launch { ReminderScheduler.rescheduleAll(app) }
        if (AppPrefs.autoUpdateCheck(app)) checkForUpdate()
    }

    // ---------------------------------------------------------------- Medikamente

    fun add(item: Medication) = viewModelScope.launch {
        db.medicationDao().insert(item.copy(personId = _personId.value))
        ReminderScheduler.rescheduleAll(getApplication())
    }

    fun update(item: Medication) = viewModelScope.launch {
        db.medicationDao().update(item)
        ReminderScheduler.rescheduleAll(getApplication())
    }

    fun delete(item: Medication) = viewModelScope.launch {
        db.medicationDao().delete(item)
        db.checkinDao().deleteFor(Checkin.MED, item.id)
        PhotoStore.delete(getApplication(), item.photoPath)
    }

    // ---------------------------------------------------------------- Dokumente

    fun add(item: Document) = viewModelScope.launch {
        db.documentDao().insert(item.copy(personId = _personId.value))
    }

    fun update(item: Document) = viewModelScope.launch { db.documentDao().update(item) }

    fun delete(item: Document) = viewModelScope.launch {
        db.documentDao().delete(item)
        DocumentStore.delete(getApplication(), item.fileName)
    }

    // ---------------------------------------------------------------- Termine

    fun add(item: Appointment) = viewModelScope.launch {
        db.appointmentDao().insert(item.copy(personId = _personId.value))
        ReminderScheduler.rescheduleAll(getApplication())
    }

    fun update(item: Appointment) = viewModelScope.launch {
        db.appointmentDao().update(item)
        ReminderScheduler.rescheduleAll(getApplication())
    }

    fun delete(item: Appointment) = viewModelScope.launch {
        db.appointmentDao().delete(item)
        db.checkinDao().deleteFor(Checkin.APPT, item.id)
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
                    status = status.orEmpty(), actual = actual.orEmpty(),
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
                ),
            )
        }
    }

    /** Setzt Status und Haken zurueck (die Bemerkung bleibt). */
    fun resetCheckin(kind: String, refId: Long, day: Long, slot: String) =
        saveCheckin(kind, refId, day, slot, done = false, status = "", actual = "")

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
            db.todoDao().insert(
                Todo(
                    personId = _personId.value,
                    text = it.text.trim(),
                    createdMillis = now,
                    doctorId = it.doctorId,
                    priority = it.priority,
                    dueDay = it.dueDay,
                    outside = it.outside,
                ),
            )
        }
    }

    fun updateTodo(item: Todo) = viewModelScope.launch { db.todoDao().update(item) }

    fun setTodoDone(item: Todo, done: Boolean) = viewModelScope.launch { db.todoDao().update(item.copy(done = done)) }

    fun deleteTodo(item: Todo) = viewModelScope.launch { db.todoDao().delete(item) }

    // ---------------------------------------------------------------- Personen

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
        )
    }

    // ---------------------------------------------------------------- Aerzte

    fun add(item: Doctor) = viewModelScope.launch { db.doctorDao().insert(item) }
    fun update(item: Doctor) = viewModelScope.launch { db.doctorDao().update(item) }
    fun delete(item: Doctor) = viewModelScope.launch { db.doctorDao().delete(item) }

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
