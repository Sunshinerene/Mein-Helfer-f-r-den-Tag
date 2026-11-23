package de.meinhelfer.tag.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.meinhelfer.tag.data.AppDatabase
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * „Eingenommen“ im Widget mit Rückfrage: Der erste Tipp fragt „Alle genommen?“ (30 Sekunden lang),
 * erst „Ja“ hakt die offenen Medikamente dieser Uhrzeit ab. „Nein“ bricht ab.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_ASK -> AppPrefs.setWidgetConfirm(app, System.currentTimeMillis() + CONFIRM_MS)
                    ACTION_NO -> AppPrefs.setWidgetConfirm(app, 0L)
                    ACTION_YES -> {
                        // Nur wenn die Rückfrage noch gilt (etwas Spielraum für langsame Startbildschirme)
                        if (System.currentTimeMillis() <= AppPrefs.widgetConfirm(app) + 60_000L) {
                            val day = intent.getLongExtra(EXTRA_DAY, -1L)
                            val time = intent.getStringExtra(EXTRA_TIME)
                            val ids = intent.getLongArrayExtra(EXTRA_MEDS) ?: LongArray(0)
                            if (day >= 0 && time != null) {
                                val dao = AppDatabase.get(app).checkinDao()
                                val now = System.currentTimeMillis()
                                ids.forEach { id ->
                                    val existing = dao.find(Checkin.MED, id, day, time)
                                    if (existing == null) {
                                        dao.insert(Checkin(kind = Checkin.MED, refId = id, day = day, slot = time, done = true, atMillis = now))
                                    } else if (!existing.done && existing.status.isEmpty()) {
                                        dao.update(existing.copy(done = true, atMillis = now))
                                    }
                                }
                            }
                        }
                        AppPrefs.setWidgetConfirm(app, 0L)
                    }
                }
                HelperWidget.refresh(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ASK = "de.meinhelfer.tag.widget.FRAGEN"
        const val ACTION_YES = "de.meinhelfer.tag.widget.JA"
        const val ACTION_NO = "de.meinhelfer.tag.widget.NEIN"
        const val EXTRA_DAY = "tag"
        const val EXTRA_TIME = "zeit"
        const val EXTRA_MEDS = "medikamente"
        const val CONFIRM_MS = 30_000L
    }
}
