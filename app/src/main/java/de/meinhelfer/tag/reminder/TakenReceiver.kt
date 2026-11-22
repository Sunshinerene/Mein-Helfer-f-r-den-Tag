package de.meinhelfer.tag.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import de.meinhelfer.tag.data.AppDatabase
import de.meinhelfer.tag.data.Checkin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Knopf "Eingenommen" in der Benachrichtigung: hakt die Einnahme ab und schliesst die Benachrichtigung. */
class TakenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        val medId = intent.getLongExtra(EXTRA_MED, -1L)
        val time = intent.getStringExtra(EXTRA_TIME)
        val day = intent.getLongExtra(EXTRA_DAY, -1L)
        val notifId = intent.getIntExtra(EXTRA_NOTIF, 0)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (medId >= 0 && time != null && day >= 0) {
                    val dao = AppDatabase.get(app).checkinDao()
                    val existing = dao.find(Checkin.MED, medId, day, time)
                    val now = System.currentTimeMillis()
                    if (existing == null) {
                        dao.insert(Checkin(kind = Checkin.MED, refId = medId, day = day, slot = time, done = true, atMillis = now))
                    } else {
                        dao.update(existing.copy(done = true, atMillis = now, status = "", actual = ""))
                    }
                }
                NotificationManagerCompat.from(app).cancel(notifId)
                de.meinhelfer.tag.widget.HelperWidget.refresh(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_MED = "med"
        const val EXTRA_TIME = "time"
        const val EXTRA_DAY = "day"
        const val EXTRA_NOTIF = "notif"
    }
}
