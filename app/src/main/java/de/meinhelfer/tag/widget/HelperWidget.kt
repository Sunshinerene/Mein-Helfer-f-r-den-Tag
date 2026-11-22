package de.meinhelfer.tag.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import de.meinhelfer.tag.MainActivity
import de.meinhelfer.tag.R
import de.meinhelfer.tag.data.AppDatabase
import de.meinhelfer.tag.summary.WidgetInfo
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Startbildschirm-Widget: nächste Einnahme und nächster Termin der gewählten Person. Antippen öffnet die App. */
class HelperWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = HelperWidget.build(app)
                manager.updateAppWidget(ids, views)
            } catch (_: Exception) {
                // Datenbank noch nicht bereit: beim nächsten Mal
            } finally {
                pending.finish()
            }
        }
    }
}

object HelperWidget {
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Ist mindestens ein Widget auf dem Startbildschirm? */
    fun installed(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, HelperWidgetProvider::class.java)).isNotEmpty()

    /** Widgets neu füllen (im Hintergrund); ohne Widget passiert nichts. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, HelperWidgetProvider::class.java))
        if (ids.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { manager.updateAppWidget(ids, build(app)) }
        }
    }

    internal suspend fun build(context: Context): RemoteViews {
        val db = AppDatabase.get(context)
        val now = LocalDateTime.now()
        val today = now.toLocalDate().toEpochDay()
        val persons = db.personDao().list()
        val wanted = AppPrefs.currentPerson(context)
        val person = persons.firstOrNull { it.id == wanted } ?: persons.firstOrNull()
        val details = AppPrefs.widgetDetails(context)
        val views = RemoteViews(context.packageName, R.layout.widget_today)

        val title = if (details && person != null && persons.size > 1) "Mein Helfer · " + person.name.substringBefore(' ') else "Mein Helfer für den Tag"
        views.setTextViewText(R.id.widget_title, title)

        if (person == null) {
            views.setTextViewText(R.id.widget_intake, "Noch keine Person angelegt")
            views.setTextViewText(R.id.widget_appointment, "Zum Einrichten antippen")
        } else {
            val meds = db.medicationDao().listFor(person.id)
            val appointments = db.appointmentDao().listFor(person.id)
            val checkins = db.checkinDao().rangeList(today - 1, today + 400)
            val intake = WidgetInfo.nextIntake(meds, checkins, now, AppPrefs.intakeWindow(context).toLong())
            val appointment = WidgetInfo.nextAppointment(appointments, checkins, now)
            views.setTextViewText(R.id.widget_intake, WidgetInfo.intakeLine(intake, now.toLocalDate(), details))
            views.setTextViewText(R.id.widget_appointment, WidgetInfo.appointmentLine(appointment, now, details))
        }
        views.setTextViewText(R.id.widget_stamp, "Stand " + now.format(STAMP))

        // Antippen öffnet die App (bei der Person des Widgets, ohne Einspieler)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            person?.let { putExtra(MainActivity.EXTRA_PERSON, it.id) }
            putExtra(MainActivity.EXTRA_FROM_WIDGET, true)
        }
        val pi = PendingIntent.getActivity(context, 4711, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        return views
    }
}
