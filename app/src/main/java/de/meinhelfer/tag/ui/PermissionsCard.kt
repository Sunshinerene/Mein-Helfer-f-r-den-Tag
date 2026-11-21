package de.meinhelfer.tag.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.meinhelfer.tag.reminder.Notifications
import de.meinhelfer.tag.reminder.ReminderScheduler
import de.meinhelfer.tag.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Freigaben, die die App fuer zuverlaessige Erinnerungen und Updates braucht. */
internal object AppPermissions {
    fun notificationsOn(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Kanal "Erinnerungen" nicht stummgeschaltet (mindestens mit Ton). */
    fun reminderChannelOk(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return true
        Notifications.ensureChannels(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val importance = nm.getNotificationChannel(Notifications.CHANNEL_REMINDERS)?.importance ?: NotificationManager.IMPORTANCE_HIGH
        return importance >= NotificationManager.IMPORTANCE_DEFAULT
    }

    fun exactAlarms(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun batteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun installAllowed(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Ohne diese kommen Erinnerungen nicht oder nicht puenktlich. */
    fun remindersReliable(ctx: Context): Boolean = notificationsOn(ctx) && reminderChannelOk(ctx) && exactAlarms(ctx)

    fun needsNotificationRequest(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun start(ctx: Context, intent: Intent, fallback: Intent? = null) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            if (fallback != null) runCatching { ctx.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))

    fun openAppSettings(ctx: Context) = start(ctx, appDetails(ctx))

    fun openNotificationSettings(ctx: Context) = start(
        ctx,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
        appDetails(ctx),
    )

    fun openChannelSettings(ctx: Context) = start(
        ctx,
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.CHANNEL_REMINDERS),
        appDetails(ctx),
    )

    fun openExactAlarmSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 31) {
            start(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)), appDetails(ctx))
        }
    }

    @android.annotation.SuppressLint("BatteryLife")
    fun requestBattery(ctx: Context) = start(
        ctx,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    fun openInstallSettings(ctx: Context) = start(
        ctx,
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)),
        appDetails(ctx),
    )
}

private class PermRow(val title: String, val text: String, val ok: Boolean, val important: Boolean, val action: String, val onFix: () -> Unit)

/**
 * Freigaben mit Status und direktem Weg in die Android-Einstellungen.
 * [onlyProblems] = nur zeigen, was fehlt (fuer den Hinweis auf der Heute-Seite).
 */
@Composable
internal fun PermissionRows(onlyProblems: Boolean = false, onlyImportant: Boolean = false) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    // Nach der Rueckkehr aus den Android-Einstellungen neu pruefen
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        if (!granted) AppPermissions.openNotificationSettings(context)
        else CoroutineScope(Dispatchers.IO).launch { ReminderScheduler.rescheduleAll(context) }
    }
    val tick = refresh

    val rows = if (tick < 0) emptyList() else listOf(
        PermRow(
            "Benachrichtigungen",
            "Für Erinnerungen an Einnahmen und Termine und für Nachfragen.",
            AppPermissions.notificationsOn(context), true, "Einschalten",
        ) {
            if (AppPermissions.needsNotificationRequest(context)) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else AppPermissions.openNotificationSettings(context)
        },
        PermRow(
            "Erinnerungen mit Ton",
            "Der Kanal „Erinnerungen“ darf nicht stumm oder ausgeschaltet sein.",
            AppPermissions.reminderChannelOk(context), true, "Öffnen",
        ) { AppPermissions.openChannelSettings(context) },
        PermRow(
            "Pünktliche Wecker",
            "Damit Erinnerungen zur genauen Uhrzeit kommen (Alarme und Erinnerungen).",
            AppPermissions.exactAlarms(context), true, "Erlauben",
        ) { AppPermissions.openExactAlarmSettings(context) },
        PermRow(
            "Akku-Optimierung aus",
            "Empfohlen: Manche Handys verzögern sonst Erinnerungen im Energiesparmodus.",
            AppPermissions.batteryUnrestricted(context), false, "Ändern",
        ) {
            AppPermissions.requestBattery(context)
        },
        PermRow(
            "Updates installieren",
            "Damit die App neue Versionen selbst installieren kann (nur nach deinem Tipp).",
            AppPermissions.installAllowed(context), false, "Erlauben",
        ) { AppPermissions.openInstallSettings(context) },
    ).filter { (!onlyProblems || !it.ok) && (!onlyImportant || it.important) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (r.ok) "✓ " else if (r.important) "✗ " else "○ ") + r.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = when {
                            r.ok -> MaterialTheme.colorScheme.primary
                            r.important -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(r.text, style = MaterialTheme.typography.bodySmall)
                }
                if (!r.ok) OutlinedButton(shape = buttonShape(), onClick = r.onFix) { Text(r.action) }
            }
        }
    }
}

/** Einstellungen: alle Freigaben auf einen Blick. */
@Composable
internal fun PermissionsCard() {
    val context = LocalContext.current
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Freigaben (Android)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Damit Erinnerungen zuverlässig kommen. „✓“ heißt erlaubt. Die Knöpfe öffnen die passende Stelle in den Android-Einstellungen. " +
                    "Für Fotos öffnet die App nur die Kamera-App, dafür braucht es keine Freigabe.",
                style = MaterialTheme.typography.bodySmall,
            )
            PermissionRows()
            TextButton(onClick = { AppPermissions.openAppSettings(context) }) { Text("Alle App-Einstellungen in Android öffnen") }
        }
    }
}

/** Heute-Seite: deutlicher Hinweis, wenn Erinnerungen nicht zuverlaessig kommen koennen. */
@Composable
internal fun ReminderWarningCard() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val tick = refresh
    if (tick >= 0 && AppPermissions.remindersReliable(context)) return
    HCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Erinnerungen sind ausgeschaltet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ohne diese Freigaben kommen Erinnerungen an Einnahmen und Termine nicht oder nicht pünktlich.",
                style = MaterialTheme.typography.bodyMedium,
            )
            PermissionRows(onlyProblems = true, onlyImportant = true)
        }
    }
}
