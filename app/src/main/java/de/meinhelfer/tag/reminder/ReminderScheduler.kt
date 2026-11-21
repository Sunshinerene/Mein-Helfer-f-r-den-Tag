package de.meinhelfer.tag.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.meinhelfer.tag.data.AppDatabase
import de.meinhelfer.tag.data.Appointment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Plant Erinnerungen per AlarmManager.
 *
 * Alarme fuer geloeschte Eintraege werden nicht aktiv abgebrochen: Der Receiver prueft beim Ausloesen,
 * ob der Eintrag noch existiert, und tut sonst nichts.
 */
object ReminderScheduler {
    const val EXTRA_TYPE = "type"
    const val EXTRA_ID = "id"
    const val EXTRA_TIME = "time"
    const val TYPE_MED = "med"
    const val TYPE_APPT = "appt"

    /** Terminerinnerung: 1 Stunde vorher. */
    const val APPOINTMENT_LEAD_MS = 60L * 60 * 1000

    fun parseTime(text: String): Pair<Int, Int>? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }

    /** Plant alle Medikamenten- und Terminerinnerungen neu (z. B. beim App-Start oder nach Neustart). */
    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val db = AppDatabase.get(app)
        db.medicationDao().list().forEach { med ->
            med.timeList().forEach { scheduleMedication(app, med.id, it) }
        }
        db.appointmentDao().list().forEach { scheduleAppointment(app, it) }
    }

    /** Plant die naechste Einnahme-Erinnerung zur angegebenen Uhrzeit (heute oder morgen). */
    fun scheduleMedication(context: Context, medId: Long, time: String) {
        val (hour, minute) = parseTime(time) ?: return
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val normalized = "%02d:%02d".format(hour, minute)
        setAlarm(
            context = context,
            requestCode = "med:$medId:$normalized".hashCode(),
            triggerAt = trigger,
            type = TYPE_MED,
            id = medId,
            time = normalized,
        )
    }

    fun scheduleAppointment(context: Context, appointment: Appointment) {
        val trigger = appointment.whenMillis - APPOINTMENT_LEAD_MS
        if (trigger <= System.currentTimeMillis()) return
        setAlarm(
            context = context,
            requestCode = "appt:${appointment.id}".hashCode(),
            triggerAt = trigger,
            type = TYPE_APPT,
            id = appointment.id,
            time = null,
        )
    }

    private fun setAlarm(
        context: Context,
        requestCode: Int,
        triggerAt: Long,
        type: String,
        id: Long,
        time: String?,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_ID, id)
            if (time != null) putExtra(EXTRA_TIME, time)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }
}
