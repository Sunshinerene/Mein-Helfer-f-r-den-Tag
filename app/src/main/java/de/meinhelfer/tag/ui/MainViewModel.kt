package de.meinhelfer.tag.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.meinhelfer.tag.data.AppDatabase
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.media.PhotoStore
import de.meinhelfer.tag.reminder.ReminderScheduler
import de.meinhelfer.tag.update.AppPrefs
import de.meinhelfer.tag.update.UpdateChecker
import de.meinhelfer.tag.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    val medications: StateFlow<List<Medication>> = db.medicationDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appointments: StateFlow<List<Appointment>> = db.appointmentDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val doctors: StateFlow<List<Doctor>> = db.doctorDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun delete(item: Appointment) = viewModelScope.launch { db.appointmentDao().delete(item) }

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
