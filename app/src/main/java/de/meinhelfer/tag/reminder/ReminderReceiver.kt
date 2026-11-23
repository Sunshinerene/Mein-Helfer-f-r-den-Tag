package de.meinhelfer.tag.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.meinhelfer.tag.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
                // Widget zeigt nach jeder Erinnerung den neuen Stand (z. B. die nächste Einnahme)
                runCatching { de.meinhelfer.tag.widget.HelperWidget.refresh(app) }
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

                val text = med.intakeText(time)
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
                // Mit Einnahme-Fenster (z. B. Insulin): Antippen öffnet das Eintragen von Menge und Werten
                Notifications.show(
                    context,
                    notifId,
                    "Medikament${forPerson(med.personId)} einnehmen ($time Uhr)",
                    if (med.askAtIntake) "$text\nAntippen: gegebene Menge und Werte eintragen." else text,
                    actions = if (med.askAtIntake) emptyList() else listOf("Eingenommen" to takenPending),
                    personId = med.personId,
                    screen = if (med.askAtIntake) "einnahme:$time" else null,
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
                if (de.meinhelfer.tag.update.AppPrefs.isQuietNow(context)) return
                Notifications.show(
                    context,
                    "mood".hashCode(),
                    "Wie geht es dir gerade?",
                    "Tippe kurz auf einen Smiley. Das bleibt nur auf deinem Handy und hilft dem Tages-Motivator.",
                    channel = Notifications.CHANNEL_MOOD,
                )
            }

            ReminderScheduler.TYPE_DRINK -> {
                val interval = de.meinhelfer.tag.update.AppPrefs.drinkReminder(context)
                val hours = ReminderScheduler.drinkHours(interval)
                val index = id.toInt()
                if (index !in hours.indices) return
                ReminderScheduler.scheduleDrinkAt(context, index, hours[index])
                if (de.meinhelfer.tag.update.AppPrefs.isQuietNow(context)) return
                val today = java.time.LocalDate.now().toEpochDay()
                val hour = java.time.LocalTime.now().hour
                val all = db.vitalDao().all().first()
                persons.forEach { p ->
                    val goal = de.meinhelfer.tag.update.AppPrefs.drinkGoal(context, p.id)
                    if (goal <= 0) return@forEach
                    val mine = all.filter { it.personId == p.id && it.kind == de.meinhelfer.tag.summary.VitalKind.Trinken.key }
                    // Nur, wenn das Trinkprotokoll fuer die Person in letzter Zeit genutzt wird
                    if (mine.none { it.day >= today - 7 }) return@forEach
                    val drunk = mine.filter { it.day == today }.sumOf { it.value }.toInt()
                    // Bis zu dieser Uhrzeit geplanter Anteil (Tag von 8 bis 20 Uhr)
                    val expected = goal * ((hour - 8).coerceIn(0, 12)) / 12
                    if (drunk >= expected) return@forEach
                    Notifications.show(
                        context,
                        "drink:${p.id}".hashCode(),
                        "Zeit für ein Getränk" + forPerson(p.id),
                        "Bisher $drunk von $goal ml. Ein Glas Wasser oder eine Tasse Tee? Antippen zum Eintragen.",
                        personId = p.id,
                        channel = Notifications.CHANNEL_CARE,
                        screen = "trinken",
                    )
                }
            }

            ReminderScheduler.TYPE_OPENED -> {
                val med = db.medicationDao().byId(id) ?: return
                val last = intent.getLongExtra(ReminderScheduler.EXTRA_DAY, -1L)
                // Inzwischen neu angebrochen oder Angabe geändert: nichts mehr zu tun
                if (de.meinhelfer.tag.summary.MedOpen.lastDay(med.openedDay, med.openLifeDays) != last) return
                val after = intent.getStringExtra(ReminderScheduler.EXTRA_TIME) == "1"
                Notifications.show(
                    context,
                    "opened:$id".hashCode(),
                    (if (after) "Abgelaufen: " else "Heute letzter Tag: ") + med.name + forPerson(med.personId),
                    if (after) {
                        "Die Haltbarkeit nach dem Anbruch ist vorbei. Bitte einen neuen anbrechen und in der App „Neu angebrochen“ antippen."
                    } else {
                        "Nach dem Anbruch nur noch heute haltbar. Ab morgen einen neuen nehmen (siehe Packungsbeilage)."
                    },
                    personId = med.personId,
                )
            }

            ReminderScheduler.TYPE_MEASURE -> {
                val spec = intent.getStringExtra(ReminderScheduler.EXTRA_TIME) ?: return
                val kind = de.meinhelfer.tag.summary.VitalKind.of(spec.substringBefore("@")) ?: return
                val hm = ReminderScheduler.parseTime(spec.substringAfter("@")) ?: return
                val time = java.time.LocalTime.of(hm.first, hm.second)
                // Plan geändert oder Person gelöscht: alter Alarm, nichts tun und nicht neu planen
                val plan = de.meinhelfer.tag.summary.MeasurePlans.parse(de.meinhelfer.tag.update.AppPrefs.measurePlan(context, id))
                if (plan[kind]?.contains(time) != true || persons.none { it.id == id }) return
                ReminderScheduler.scheduleMeasure(context, id, kind, time)
                val all = db.vitalDao().all().first()
                // Schon gemessen (z. B. etwas früher): keine Erinnerung
                if (de.meinhelfer.tag.summary.MeasurePlans.measured(all, id, kind, java.time.LocalDate.now().atTime(time))) return
                val timeText = spec.substringAfter("@")
                Notifications.show(
                    context,
                    "measure:$id:$spec".hashCode(),
                    "${kind.emoji} ${kind.label} messen" + forPerson(id),
                    "Geplant um $timeText Uhr. Antippen zum Eintragen.",
                    personId = id,
                    channel = Notifications.CHANNEL_CARE,
                    screen = "messen:${kind.key}",
                )
            }

            ReminderScheduler.TYPE_DIARY -> {
                ReminderScheduler.scheduleDiary(context)
                if (de.meinhelfer.tag.update.AppPrefs.diaryReminder(context) < 0) return
                val today = java.time.LocalDate.now().toEpochDay()
                val all = db.careLogDao().all().first()
                // Personen, fuer die das Tagebuch gefuehrt wird; sonst die gerade gewaehlte Person
                val used = persons.filter { p -> all.any { it.personId == p.id && it.day >= today - 30 } }
                    .ifEmpty { persons.filter { it.id == de.meinhelfer.tag.update.AppPrefs.currentPerson(context) } }
                used.forEach { p ->
                    if (all.any { it.personId == p.id && it.day == today }) return@forEach
                    Notifications.show(
                        context,
                        "diary:${p.id}".hashCode(),
                        "Pflegetagebuch" + forPerson(p.id),
                        "Heute ist noch nichts eingetragen. Kurz festhalten, wobei geholfen wurde? Mit „Wie am Vortag“ geht es schnell.",
                        personId = p.id,
                        channel = Notifications.CHANNEL_CARE,
                        screen = "tagebuch",
                    )
                }
            }

            ReminderScheduler.TYPE_ASSIST -> {
                ReminderScheduler.scheduleAssistant(context)
                if (!de.meinhelfer.tag.update.AppPrefs.assistantEnabled(context)) return
                if (de.meinhelfer.tag.update.AppPrefs.isQuietNow(context)) return
                val name = de.meinhelfer.tag.update.AppPrefs.assistantName(context)
                val now = java.time.LocalDateTime.now()
                val today = now.toLocalDate().toEpochDay()
                val zone = java.time.ZoneId.systemDefault()
                val meds = db.medicationDao().list()
                val appointments = db.appointmentDao().list()
                val todos = db.todoDao().list()
                val checkins = db.checkinDao().rangeList(today - 1, today + 2)
                val doctors = db.doctorDao().all().first()
                val vitals = db.vitalDao().all().first()
                val multi = persons.size > 1
                val measured = persons.associate { p -> p.id to vitals.any { it.personId == p.id && it.kind != "trinken" && it.day >= today - 14 } }
                val plans = persons.associate { p -> p.id to de.meinhelfer.tag.summary.MeasurePlans.parse(de.meinhelfer.tag.update.AppPrefs.measurePlan(context, p.id)) }
                val line = de.meinhelfer.tag.summary.Tageslage.dayLine(
                    persons, meds, checkins, appointments, todos, now, zone, de.meinhelfer.tag.update.AppPrefs.intakeWindow(context).toLong(),
                    de.meinhelfer.tag.summary.MeasurePlans.openToday(plans, vitals, now, zone),
                )
                val preps = de.meinhelfer.tag.summary.Assistant.preps(persons, appointments, checkins, meds, doctors, measured, now, zone)
                fun dayOf(millis: Long) = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
                val (title, text) = if (id == 0L) {
                    val proposals = de.meinhelfer.tag.summary.Assistant.proposals(
                        persons, todos, meds, db.responsibilityDao().list(), doctors, today, de.meinhelfer.tag.update.AppPrefs.assistantDismissed(context),
                        db.checkinDao().rangeList(today - 15, today),
                    ).size
                    de.meinhelfer.tag.summary.AssistantBrief.morning(name, line, preps.filter { dayOf(it.appointment.whenMillis) == today }, proposals, multi)
                } else {
                    val done = checkins.count { it.day == today && it.done } +
                        todos.count { it.done && it.doneMillis != null && dayOf(it.doneMillis) == today }
                    val careLog = db.careLogDao().all().first()
                    // Tagebuch nur fuer Personen, fuer die es in den letzten 30 Tagen gefuehrt wurde
                    val diaryMissing = persons.filter { p ->
                        careLog.any { it.personId == p.id && it.day >= today - 30 } && careLog.none { it.personId == p.id && it.day == today }
                    }.map { it.name }
                    val wake = if (de.meinhelfer.tag.update.AppPrefs.wakeOn(context)) {
                        de.meinhelfer.tag.summary.WakeUp.eveningLine(
                            de.meinhelfer.tag.summary.WakeUp.plans(
                                persons, appointments, checkins, now, zone,
                                de.meinhelfer.tag.update.AppPrefs.wakeEarly(context), de.meinhelfer.tag.update.AppPrefs.wakeLead(context),
                            ),
                        )
                    } else {
                        ""
                    }
                    de.meinhelfer.tag.summary.AssistantBrief.evening(
                        name, done, line, preps.filter { dayOf(it.appointment.whenMillis) == today + 1 }, diaryMissing, multi,
                    ).let { (t, x) -> t to if (wake.isBlank()) x else x.replace(" Gute Nacht!", " $wake Gute Nacht!") }
                }
                Notifications.show(
                    context,
                    "assist:$id".hashCode(),
                    title,
                    text,
                    channel = Notifications.CHANNEL_ASSISTANT,
                )
            }

            ReminderScheduler.TYPE_TODO -> {
                val todo = db.todoDao().byId(id) ?: return
                val at = todo.remindAt ?: return
                // Erledigt, geloescht oder Erinnerung verschoben: alter Alarm, nichts anzeigen.
                if (todo.done || todo.archived) return
                if (kotlin.math.abs(System.currentTimeMillis() - at) > 10 * 60 * 1000L) return
                val due = todo.dueDay?.let { " · " + de.meinhelfer.tag.ui.dueText(it) }.orEmpty()
                Notifications.show(
                    context,
                    "todo:$id".hashCode(),
                    "Aufgabe${forPerson(todo.personId)}",
                    todo.text + due,
                    personId = todo.personId,
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
                // Mit Assistent: kurze Checkliste, was mit soll
                val assistant = de.meinhelfer.tag.update.AppPrefs.assistantEnabled(context)
                val addon = if (assistant) {
                    runCatching {
                        val now = java.time.LocalDateTime.now()
                        val today = java.time.LocalDate.now().toEpochDay()
                        val vitals = db.vitalDao().all().first()
                        val measured = mapOf(appointment.personId to vitals.any { it.personId == appointment.personId && it.kind != "trinken" && it.day >= today - 14 })
                        val prep = de.meinhelfer.tag.summary.Assistant.preps(
                            persons, listOf(appointment), emptyList(), db.medicationDao().list(), db.doctorDao().all().first(), measured, now.minusMinutes(1),
                        ).firstOrNull()
                        de.meinhelfer.tag.summary.AssistantBrief.appointmentAddon(prep)
                    }.getOrDefault("")
                } else ""
                val name = if (assistant) de.meinhelfer.tag.update.AppPrefs.assistantName(context) + ": " else ""
                Notifications.show(
                    context,
                    "appt:$id".hashCode(),
                    name + "Termin${forPerson(appointment.personId)} in ${ReminderScheduler.leadText(appointment.reminderMinutes)}",
                    text + addon,
                    personId = appointment.personId,
                )
            }
        }
    }
}
