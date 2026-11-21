package de.meinhelfer.tag.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.meinhelfer.tag.MainActivity

object Notifications {
    const val CHANNEL_REMINDERS = "erinnerungen"
    private const val CHANNEL_UPDATES = "updates"
    const val CHANNEL_MOOD = "stimmung"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_REMINDERS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_REMINDERS, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH)
                        .apply { description = "Medikamente und Termine" },
                )
            }
            if (nm.getNotificationChannel(CHANNEL_MOOD) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_MOOD, "Stimmungs-Check", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "Kurze Frage, wie es dir geht (Tages-Motivator)" },
                )
            }
            if (nm.getNotificationChannel(CHANNEL_UPDATES) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_UPDATES, "App-Updates", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "Hinweis auf neue Versionen" },
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun show(
        context: Context,
        id: Int,
        title: String,
        text: String,
        updates: Boolean = false,
        actions: List<Pair<String, PendingIntent>> = emptyList(),
        personId: Long? = null,
        channel: String? = null,
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannels(context)
        // Antippen oeffnet die App bei der richtigen Person.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (personId != null) putExtra(MainActivity.EXTRA_PERSON, personId)
        }
        val open = PendingIntent.getActivity(
            context,
            id,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, channel ?: if (updates) CHANNEL_UPDATES else CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (updates || channel != null) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
        actions.forEach { (label, intent) -> builder.addAction(0, label, intent) }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}
