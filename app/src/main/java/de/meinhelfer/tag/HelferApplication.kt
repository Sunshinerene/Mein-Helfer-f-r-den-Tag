package de.meinhelfer.tag

import android.app.Application
import de.meinhelfer.tag.backup.Backup

/** Tauscht vor allem anderen eine vorbereitete Wiederherstellung ein, bevor die Datenbank geoeffnet wird. */
class HelferApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            de.meinhelfer.tag.backup.AppReset.applyPending(this)
        } catch (_: Throwable) {
            // weiter mit dem normalen Start
        }
        try {
            Backup.applyPending(this)
        } catch (_: Throwable) {
            // Die App muss auf jeden Fall starten
        }
    }
}
