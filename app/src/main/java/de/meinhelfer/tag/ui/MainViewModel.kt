package de.meinhelfer.tag.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.meinhelfer.tag.data.AppDatabase
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.DayNote
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    val medications: StateFlow<List<Medication>> = db.medicationDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appointments: StateFlow<List<Appointment>> = db.appointmentDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val doctors: StateFlow<List<Doctor>> = db.doctorDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- "Heute": aktueller Tag (Epoch-Tag), Abhaken und Tagesbemerkung
    private val _today = MutableStateFlow(LocalDate.now().toEpochDay())
    val today: StateFlow<Long> = _today

    /** Abhak-Eintraege von heute bis 7 Tage voraus (Termine der naechsten Woche). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val checkins: StateFlow<List<Checkin>> = _today
        .flatMapLatest { d -> db.checkinDao().range(d, d + 7) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dayNote: StateFlow<DayNote?> = _today
        .flatMapLatest { d -> db.dayNoteDao().range(d, d) }
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    init {
        // Sicherheitsnetz: Erinnerungen bei jedem App-Start neu planen.
        viewModelScope.launch { ReminderScheduler.rescheduleAll(app) }
        if (AppPrefs.autoUpdateCheck(app)) checkForUpdate()
    }

    // ---------------------------------------------------------------- Medikamente

    fun add(item: Medication) = viewModelScope.launch {
        db.medicationDao().insert(item)
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

    // ---------------------------------------------------------------- Termine

    fun add(item: Appointment) = viewModelScope.launch {
        db.appointmentDao().insert(item)
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
    fun saveCheckin(kind: String, refId: Long, day: Long, slot: String, done: Boolean? = null, note: String? = null) =
        viewModelScope.launch {
            val normSlot = if (slot.isEmpty()) "" else SummaryBuilder.normSlot(slot)
            val dao = db.checkinDao()
            val now = System.currentTimeMillis()
            val existing = dao.find(kind, refId, day, normSlot)
            if (existing == null) {
                dao.insert(
                    Checkin(
                        kind = kind, refId = refId, day = day, slot = normSlot,
                        done = done ?: false, note = note?.trim().orEmpty(), atMillis = now,
                    ),
                )
            } else {
                dao.update(
                    existing.copy(
                        done = done ?: existing.done,
                        note = note?.trim() ?: existing.note,
                        atMillis = now,
                    ),
                )
            }
        }

    fun saveDayNote(day: Long, text: String) = viewModelScope.launch {
        if (text.isBlank()) db.dayNoteDao().delete(day) else db.dayNoteDao().upsert(DayNote(day, text.trim()))
    }

    /** Auswertung fuer Tag/Woche/Monat (offset = Perioden zurueck bzw. voraus), rein lokal berechnet. */
    suspend fun buildSummary(period: Period, offset: Int): Summary {
        val today = LocalDate.now()
        val (start, end) = SummaryBuilder.range(period, offset, today)
        return SummaryBuilder.build(
            period = period,
            start = start,
            end = end,
            today = today,
            now = LocalTime.now(),
            meds = db.medicationDao().list(),
            appointments = db.appointmentDao().list(),
            checkins = db.checkinDao().rangeList(start.toEpochDay(), end.toEpochDay()),
            dayNotes = db.dayNoteDao().rangeList(start.toEpochDay(), end.toEpochDay()),
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
