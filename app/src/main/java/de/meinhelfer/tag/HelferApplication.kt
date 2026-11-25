package de.meinhelfer.tag

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import de.meinhelfer.tag.backup.Backup

/** Tauscht vor allem anderen eine vorbereitete Wiederherstellung ein, bevor die Datenbank geoeffnet wird. */
class HelferApplication : Application() {
    companion object {
        @Volatile var startupReady: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
        try {
            de.meinhelfer.tag.backup.AppReset.applyPending(this)
        } catch (_: Throwable) {
            // weiter mit dem normalen Start
        }
        // Fail closed: opening Room after a failed rollback could mutate incomplete data.
        try {
            Backup.applyPending(this)
            startupReady = true
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { de.meinhelfer.tag.reminder.ReminderScheduler.rescheduleAll(this@HelferApplication) }
            }
        } catch (_: Exception) {
            // Keep Room closed; MainActivity offers a database-free recovery screen.
            de.meinhelfer.tag.widget.HelperWidget.refresh(this)
        }
    }
}
