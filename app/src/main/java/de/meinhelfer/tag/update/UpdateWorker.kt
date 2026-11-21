package de.meinhelfer.tag.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.meinhelfer.tag.reminder.Notifications
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Prueft im Hintergrund auf neue Versionen und zeigt einmal pro Version eine Benachrichtigung. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!AppPrefs.autoUpdateCheck(context)) return Result.success()

        return try {
            val info = UpdateChecker.fetchLatest()
            if (info != null &&
                UpdateChecker.isNewer(info.version, UpdateChecker.installedVersion(context)) &&
                AppPrefs.lastNotifiedVersion(context) != info.version
            ) {
                Notifications.show(
                    context,
                    id = NOTIFICATION_ID,
                    title = "Neue Version verfügbar",
                    text = "Mein Helfer ${info.version} kann installiert werden: Einstellungen öffnen und Update laden.",
                    updates = true,
                )
                AppPrefs.setLastNotifiedVersion(context, info.version)
            }
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "update-check"
        private const val NOTIFICATION_ID = 4711

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
