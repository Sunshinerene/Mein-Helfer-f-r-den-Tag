package de.meinhelfer.tag.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.meinhelfer.tag.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** Wird vom AlarmManager ausgeloest und zeigt die Benachrichtigung an. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(app, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val db = AppDatabase.get(context)
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        if (id < 0) return

        when (intent.getStringExtra(ReminderScheduler.EXTRA_TYPE)) {
            ReminderScheduler.TYPE_MED -> {
                val time = intent.getStringExtra(ReminderScheduler.EXTRA_TIME) ?: return
                val wanted = ReminderScheduler.parseTime(time) ?: return
                val med = db.medicationDao().byId(id) ?: return
                // Eintrag oder Uhrzeit wurde inzwischen geloescht: nichts anzeigen, nicht neu planen.
                if (med.timeList().none { ReminderScheduler.parseTime(it) == wanted }) return

                val text = med.name + if (med.dosage.isNotBlank()) " (${med.dosage})" else ""
                Notifications.show(
                    context,
                    "med:$id:$time".hashCode(),
                    "Medikament einnehmen ($time Uhr)",
                    text,
                )
                ReminderScheduler.scheduleMedication(context, id, time)
            }

            ReminderScheduler.TYPE_APPT -> {
                val appointment = db.appointmentDao().byId(id) ?: return
                // Termin wurde verschoben oder liegt in der Vergangenheit: alter Alarm, nichts anzeigen.
                val remaining = appointment.whenMillis - System.currentTimeMillis()
                if (remaining < 0 || remaining > ReminderScheduler.APPOINTMENT_LEAD_MS + 5 * 60 * 1000L) return
                val whenText = SimpleDateFormat("HH:mm", Locale.GERMANY).format(appointment.whenMillis)
                val text = appointment.title +
                    " um $whenText Uhr" +
                    if (appointment.place.isNotBlank()) ", ${appointment.place}" else ""
                Notifications.show(
                    context,
                    "appt:$id".hashCode(),
                    "Termin in 1 Stunde",
                    text,
                )
            }
        }
    }
}
