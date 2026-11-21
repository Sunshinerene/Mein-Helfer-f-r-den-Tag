package de.meinhelfer.tag.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.meinhelfer.tag.data.AppDatabase
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Medication
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

    fun add(item: Medication) = viewModelScope.launch { db.medicationDao().insert(item) }
    fun delete(item: Medication) = viewModelScope.launch { db.medicationDao().delete(item) }

    fun add(item: Appointment) = viewModelScope.launch { db.appointmentDao().insert(item) }
    fun delete(item: Appointment) = viewModelScope.launch { db.appointmentDao().delete(item) }

    fun add(item: Doctor) = viewModelScope.launch { db.doctorDao().insert(item) }
    fun delete(item: Doctor) = viewModelScope.launch { db.doctorDao().delete(item) }
}
