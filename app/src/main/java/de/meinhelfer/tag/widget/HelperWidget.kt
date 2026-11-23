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
        var faceAttentive = false
        var faceOverdue = false

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
            takeButtons(context, views, intake, now, person.id)
            faceOverdue = intake?.overdue == true
            faceAttentive = intake != null && intake.day == today &&
                runCatching { java.time.LocalTime.parse(intake.time).isBefore(now.toLocalTime().plusMinutes(30)) }.getOrDefault(false)
            views.setTextViewText(R.id.widget_appointment, WidgetInfo.appointmentLine(appointment, now, details))
        }
        views.setTextViewText(R.id.widget_stamp, "Stand " + now.format(STAMP))
        // Sunny statt Logo: Ausdruck nach Lage (offene Einnahme = aufmerksam, sonst freundlich)
        if (de.meinhelfer.tag.ui.SunnyBitmap.enabled(context)) {
            val face = when {
                person == null -> R.drawable.sunny_freundlich
                now.hour >= 21 || now.hour < 6 -> R.drawable.sunny_portrait
                faceOverdue -> R.drawable.sunny_besorgt
                faceAttentive -> R.drawable.sunny_ernst
                else -> R.drawable.sunny_froh
            }
            val px = (34 * context.resources.displayMetrics.density).toInt()
            de.meinhelfer.tag.ui.SunnyBitmap.circle(context, face, px)?.let { views.setImageViewBitmap(R.id.widget_logo, it) }
            views.setContentDescription(R.id.widget_logo, AppPrefs.assistantName(context))
        } else {
            views.setImageViewResource(R.id.widget_logo, R.drawable.logo_mark)
        }

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

    /**
     * „✓ Eingenommen“ nur für heute und erst ab Beginn des Zeitfensters. Mit Einnahme-Fenster (z. B. Insulin)
     * öffnet der Knopf die App zum Eintragen von Menge und Werten, sonst kommt erst die Rückfrage im Widget.
     */
    private fun takeButtons(context: Context, views: RemoteViews, intake: de.meinhelfer.tag.summary.NextIntake?, now: LocalDateTime, personId: Long) {
        val window = AppPrefs.intakeWindow(context).toLong()
        val slot = intake?.let { runCatching { java.time.LocalTime.parse(it.time) }.getOrNull() }
        val show = intake != null && slot != null && intake.medIds.isNotEmpty() && intake.day == now.toLocalDate().toEpochDay() &&
            !now.isBefore(now.toLocalDate().atTime(slot).minusMinutes(window))
        if (!show) {
            views.setViewVisibility(R.id.widget_take_row, android.view.View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_take_row, android.view.View.VISIBLE)
        fun broadcast(action: String, code: Int, extras: Intent.() -> Unit = {}): PendingIntent =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, WidgetActionReceiver::class.java).setAction(action).apply(extras),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        if (intake!!.ask) {
            views.setTextViewText(R.id.widget_take, "✎ Eintragen …")
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_PERSON, personId)
                putExtra(MainActivity.EXTRA_SCREEN, "einnahme:" + intake.time)
            }
            views.setOnClickPendingIntent(R.id.widget_take, PendingIntent.getActivity(context, 4712, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            views.setViewVisibility(R.id.widget_take, android.view.View.VISIBLE)
            listOf(R.id.widget_confirm_text, R.id.widget_yes, R.id.widget_no).forEach { views.setViewVisibility(it, android.view.View.GONE) }
            return
        }
        val asking = System.currentTimeMillis() < AppPrefs.widgetConfirm(context)
        views.setViewVisibility(R.id.widget_take, if (asking) android.view.View.GONE else android.view.View.VISIBLE)
        listOf(R.id.widget_confirm_text, R.id.widget_yes, R.id.widget_no).forEach {
            views.setViewVisibility(it, if (asking) android.view.View.VISIBLE else android.view.View.GONE)
        }
        views.setTextViewText(R.id.widget_take, "✓ Eingenommen")
        views.setTextViewText(R.id.widget_confirm_text, "${intake.time} Uhr: alle genommen?")
        views.setOnClickPendingIntent(R.id.widget_take, broadcast(WidgetActionReceiver.ACTION_ASK, 4720))
        views.setOnClickPendingIntent(R.id.widget_no, broadcast(WidgetActionReceiver.ACTION_NO, 4721))
        views.setOnClickPendingIntent(
            R.id.widget_yes,
            broadcast(WidgetActionReceiver.ACTION_YES, 4722) {
                putExtra(WidgetActionReceiver.EXTRA_DAY, intake.day)
                putExtra(WidgetActionReceiver.EXTRA_TIME, intake.time)
                putExtra(WidgetActionReceiver.EXTRA_MEDS, intake.medIds.toLongArray())
            },
        )
        // Rückfrage läuft ab: danach wieder den normalen Knopf zeigen
        if (asking) {
            CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(WidgetActionReceiver.CONFIRM_MS + 500)
                refresh(context)
            }
        }
    }
}
