package de.meinhelfer.tag

import android.app.Application
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
        Backup.applyPending(this)
        startupReady = true
    }
}
