package de.meinhelfer.tag.reminder

import android.app.PendingIntent
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

        // Bei mehreren Personen steht der Name in der Benachrichtigung.
        val persons = db.personDao().list()
        fun forPerson(personId: Long): String =
            if (persons.size > 1) persons.firstOrNull { it.id == personId }?.let { " für ${it.name}" } ?: "" else ""

        when (intent.getStringExtra(ReminderScheduler.EXTRA_TYPE)) {
            ReminderScheduler.TYPE_MED -> {
                val time = intent.getStringExtra(ReminderScheduler.EXTRA_TIME) ?: return
                val wanted = ReminderScheduler.parseTime(time) ?: return
                val med = db.medicationDao().byId(id) ?: return
                // Eintrag oder Uhrzeit wurde inzwischen geloescht: nichts anzeigen, nicht neu planen.
                if (med.timeList().none { ReminderScheduler.parseTime(it) == wanted }) return
                // Schon abgehakt oder beantwortet (z. B. etwas frueher genommen): keine Erinnerung, nur neu planen.
                val today = java.time.LocalDate.now().toEpochDay()
                val already = db.checkinDao().find(de.meinhelfer.tag.data.Checkin.MED, id, today, time)
                if (already != null && (already.done || already.status.isNotEmpty())) {
                    ReminderScheduler.scheduleMedication(context, id, time)
                    return
                }

                val text = med.name + if (med.dosage.isNotBlank()) " (${med.dosage})" else ""
                val notifId = "med:$id:$time".hashCode()
                val taken = Intent(context, TakenReceiver::class.java).apply {
                    putExtra(TakenReceiver.EXTRA_MED, id)
                    putExtra(TakenReceiver.EXTRA_TIME, time)
                    putExtra(TakenReceiver.EXTRA_DAY, java.time.LocalDate.now().toEpochDay())
                    putExtra(TakenReceiver.EXTRA_NOTIF, notifId)
                }
                val takenPending = PendingIntent.getBroadcast(
                    context,
                    "taken:$id:$time".hashCode(),
                    taken,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                Notifications.show(
                    context,
                    notifId,
                    "Medikament${forPerson(med.personId)} einnehmen ($time Uhr)",
                    text,
                    actions = listOf("Eingenommen" to takenPending),
                    personId = med.personId,
                )
                ReminderScheduler.scheduleMedication(context, id, time)
            }

            ReminderScheduler.TYPE_FOLLOWUP -> {
                val time = intent.getStringExtra(ReminderScheduler.EXTRA_TIME) ?: return
                val wanted = ReminderScheduler.parseTime(time) ?: return
                val day = intent.getLongExtra(ReminderScheduler.EXTRA_DAY, -1L)
                val med = db.medicationDao().byId(id) ?: return
                if (med.timeList().none { ReminderScheduler.parseTime(it) == wanted }) return
                // Naechste Nachfrage planen, egal ob jetzt etwas angezeigt wird.
                ReminderScheduler.scheduleMedication(context, id, time)
                if (day < 0 || !de.meinhelfer.tag.update.AppPrefs.followupNotify(context)) return
                if (day < med.createdDay) return
                val existing = db.checkinDao().find(de.meinhelfer.tag.data.Checkin.MED, id, day, time)
                // Schon abgehakt oder beantwortet: keine Nachfrage.
                if (existing != null && (existing.done || existing.status.isNotEmpty())) return
                Notifications.show(
                    context,
                    "fu:$id:$time".hashCode(),
                    "Kurz nachgefragt${forPerson(med.personId)}",
                    "Wurde ${med.name} um $time Uhr genommen? Tippe hier und gib kurz Bescheid. Das macht die Auswertung genauer.",
                    personId = med.personId,
                )
            }

            ReminderScheduler.TYPE_MOOD -> {
                val mode = de.meinhelfer.tag.summary.MoodMode.fromKey(de.meinhelfer.tag.update.AppPrefs.moodMode(context))
                if (!mode.isSlotted || !de.meinhelfer.tag.update.AppPrefs.moodNotify(context)) return
                val hours = de.meinhelfer.tag.summary.MoodSchedule.reminderHours(mode)
                val index = id.toInt()
                if (index in hours.indices) ReminderScheduler.scheduleMoodAt(context, index, hours[index])
                val now = System.currentTimeMillis()
                val start = de.meinhelfer.tag.summary.MoodSchedule.slotStart(mode, now) ?: return
                val today = java.time.LocalDate.now().toEpochDay()
                val entered = db.moodDao().since(today).any { it.atMillis >= start }
                if (entered) return
                Notifications.show(
                    context,
                    "mood".hashCode(),
                    "Wie geht es dir gerade?",
                    "Tippe kurz auf einen Smiley. Das bleibt nur auf deinem Handy und hilft dem Tages-Motivator.",
                    channel = Notifications.CHANNEL_MOOD,
                )
            }

            ReminderScheduler.TYPE_APPT -> {
                val appointment = db.appointmentDao().byId(id) ?: return
                // Termin wurde verschoben oder liegt in der Vergangenheit: alter Alarm, nichts anzeigen.
                val remaining = appointment.whenMillis - System.currentTimeMillis()
                if (appointment.reminderMinutes < 0) return
                val lead = appointment.reminderMinutes * 60_000L
                if (remaining < 0 || remaining > lead + 5 * 60 * 1000L) return
                val whenText = SimpleDateFormat("HH:mm", Locale.GERMANY).format(appointment.whenMillis)
                val text = appointment.title +
                    " um $whenText Uhr" +
                    if (appointment.place.isNotBlank()) ", ${appointment.place}" else ""
                Notifications.show(
                    context,
                    "appt:$id".hashCode(),
                    "Termin${forPerson(appointment.personId)} in ${ReminderScheduler.leadText(appointment.reminderMinutes)}",
                    text,
                    personId = appointment.personId,
                )
            }
        }
    }
}
