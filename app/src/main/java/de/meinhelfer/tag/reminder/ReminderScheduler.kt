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
    const val TYPE_FOLLOWUP = "followup"
    const val EXTRA_DAY = "day"
    const val TYPE_MOOD = "mood"
    const val TYPE_TODO = "todo"

    /** "30 Minuten", "1 Stunde", "2 Stunden", "1 Tag" */
    fun leadText(minutes: Int): String = when {
        minutes < 60 -> "$minutes Minuten"
        minutes == 60 -> "1 Stunde"
        minutes < 1440 -> "${minutes / 60} Stunden"
        minutes == 1440 -> "1 Tag"
        else -> "${minutes / 1440} Tage"
    }

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
        db.todoDao().list().forEach { scheduleTodo(app, it) }
        scheduleMood(app)
    }

    /**
     * Erinnerungen an den Stimmungs-Check (nur wenn eingeschaltet). Alte Alarme werden abgebrochen.
     * Der Receiver zeigt nur etwas, wenn im aktuellen Zeitfenster noch nichts eingetragen wurde.
     */
    fun scheduleMood(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        for (i in 0..2) {
            val pending = PendingIntent.getBroadcast(
                app, "mood:$i".hashCode(), Intent(app, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
        val mode = de.meinhelfer.tag.summary.MoodMode.fromKey(de.meinhelfer.tag.update.AppPrefs.moodMode(app))
        if (!mode.isSlotted || !de.meinhelfer.tag.update.AppPrefs.moodNotify(app)) return
        de.meinhelfer.tag.summary.MoodSchedule.reminderHours(mode).forEachIndexed { i, hour -> scheduleMoodAt(app, i, hour) }
    }

    fun scheduleMoodAt(context: Context, index: Int, hour: Int) {
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, TYPE_MOOD)
            putExtra(EXTRA_ID, index.toLong())
            putExtra(EXTRA_TIME, "%02d:00".format(hour))
        }
        val pending = PendingIntent.getBroadcast(
            context, "mood:$index".hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Ungefaehr reicht hier, kein genauer Wecker noetig
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
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
        scheduleFollowUp(context, medId, normalized, hour, minute)
    }

    /**
     * Plant die Nachfrage fuer den Fall, dass eine Einnahme nach dem Zeitfenster noch offen ist.
     * Der Receiver prueft beim Ausloesen, ob inzwischen abgehakt oder beantwortet wurde.
     */
    private fun scheduleFollowUp(context: Context, medId: Long, normalized: String, hour: Int, minute: Int) {
        if (!de.meinhelfer.tag.update.AppPrefs.followupNotify(context)) return
        val window = de.meinhelfer.tag.update.AppPrefs.intakeWindow(context)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, window)
            if (timeInMillis <= now + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }
        // Der Einnahme-Tag ist der Tag der Einnahmezeit (nicht unbedingt der Tag der Nachfrage, z. B. 23:30 + 1 Std.).
        val slotDay = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            add(Calendar.MINUTE, -window)
        }
        val day = java.time.LocalDate.of(
            slotDay.get(Calendar.YEAR), slotDay.get(Calendar.MONTH) + 1, slotDay.get(Calendar.DAY_OF_MONTH),
        ).toEpochDay()
        setAlarm(
            context = context,
            requestCode = "fu:$medId:$normalized".hashCode(),
            triggerAt = cal.timeInMillis,
            type = TYPE_FOLLOWUP,
            id = medId,
            time = normalized,
            day = day,
        )
    }

    fun scheduleAppointment(context: Context, appointment: Appointment) {
        if (appointment.reminderMinutes < 0) return
        val trigger = appointment.whenMillis - appointment.reminderMinutes * 60_000L
        if (trigger <= System.currentTimeMillis()) return
        // Nur die naechsten 60 Tage planen (Serien); spaetere werden beim naechsten App-Start oder Neustart geplant.
        if (trigger > System.currentTimeMillis() + 60L * 24 * 60 * 60 * 1000) return
        setAlarm(
            context = context,
            requestCode = "appt:${appointment.id}".hashCode(),
            triggerAt = trigger,
            type = TYPE_APPT,
            id = appointment.id,
            time = null,
        )
    }

    /** Erinnerung an eine Aufgabe (nur offene mit Erinnerungszeit in der Zukunft). */
    fun scheduleTodo(context: Context, todo: de.meinhelfer.tag.data.Todo) {
        val at = todo.remindAt ?: return
        if (todo.done || todo.archived || at <= System.currentTimeMillis()) return
        setAlarm(
            context = context,
            requestCode = "todo:${todo.id}".hashCode(),
            triggerAt = at,
            type = TYPE_TODO,
            id = todo.id,
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
        day: Long? = null,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_ID, id)
            if (time != null) putExtra(EXTRA_TIME, time)
            if (day != null) putExtra(EXTRA_DAY, day)
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
