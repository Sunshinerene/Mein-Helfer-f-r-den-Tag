package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.info.FahrdienstSearch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** "heute", "morgen", "in 3 Tagen", "vor 2 Tagen" */
internal fun countdownText(whenMillis: Long, now: LocalDate = LocalDate.now()): String {
    val day = Instant.ofEpochMilli(whenMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (val d = ChronoUnit.DAYS.between(now, day)) {
        0L -> "heute"
        1L -> "morgen"
        2L -> "übermorgen"
        -1L -> "gestern"
        else -> if (d > 0) "in $d Tagen" else "vor ${-d} Tagen"
    }
}

private fun reminderText(minutes: Int): String? = when {
    minutes <= 0 -> null
    minutes % 1440 == 0 -> "Erinnerung ${minutes / 1440} ${if (minutes / 1440 == 1) "Tag" else "Tage"} vorher"
    minutes % 60 == 0 -> "Erinnerung ${minutes / 60} Std. vorher"
    else -> "Erinnerung $minutes Min. vorher"
}

private fun startIntent(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // keine passende App installiert
    }
}

/**
 * Termin-Karte: grosser Datumsblock, Wochentag, Countdown, Uhrzeit, Kontakt mit Anruf und Route,
 * Unterlagen und bei Terminen ausserhalb der Wohnung der Fahrdienst.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppointmentCard(
    a: Appointment,
    doctor: Doctor?,
    attached: List<Document>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDocs: () -> Unit,
    onCalendar: () -> Unit,
    onFahrdienst: () -> Unit,
    onMail: (() -> Unit)? = null,
    /** z. B. "Jede Woche · noch 12 weitere Termine" bei Serien */
    seriesInfo: String? = null,
) {
    val context = LocalContext.current

    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(a.whenMillis).atZone(zone)
    val past = (a.endMillis ?: a.whenMillis) < System.currentTimeMillis()
    val timeText = start.format(TIME_FORMAT) +
        (a.endMillis?.let { "–" + Instant.ofEpochMilli(it).atZone(zone).format(TIME_FORMAT) } ?: "") + " Uhr"
    val outside = isOutsideHome(a, doctor)
    val target = listOf(a.place, doctor?.address.orEmpty()).firstOrNull { it.isNotBlank() }
    val countdown = countdownText(a.whenMillis)
    val soon = !past && (countdown == "heute" || countdown == "morgen")

    HCard(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Datumsblock
            val blockColor = when {
                past -> MaterialTheme.colorScheme.surfaceVariant
                soon -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }
            val blockText = when {
                past -> MaterialTheme.colorScheme.onSurfaceVariant
                soon -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            }
            Column(
                Modifier
                    .width(62.dp)
                    .background(blockColor, MaterialTheme.shapes.small)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    start.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMANY).trimEnd('.').uppercase(Locale.GERMANY),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = blockText,
                )
                Text(
                    start.dayOfMonth.toString(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = blockText,
                )
                Text(
                    start.month.getDisplayName(TextStyle.SHORT, Locale.GERMANY).trimEnd('.'),
                    fontSize = 12.sp,
                    color = blockText,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(a.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    timeText + " · " + if (past) "vorbei" else countdown,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (soon) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
                doctor?.let {
                    Text(doctorLabel(it), style = MaterialTheme.typography.bodyMedium)
                }
                a.place.takeIf { it.isNotBlank() }?.let {
                    Text("Ort: $it", style = MaterialTheme.typography.bodyMedium)
                }
                if (a.place.isBlank()) {
                    doctor?.address?.takeIf { it.isNotBlank() }?.let {
                        Text("Adresse: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                a.notes.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                if (attached.isNotEmpty()) {
                    Text(
                        "Unterlagen: " + attached.joinToString(", ") { it.title },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                seriesInfo?.let {
                    Text("Wiederkehrend: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (!past) {
                    reminderText(a.reminderMinutes)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (outside && !past) {
                    Text(
                        "Außer Haus – Fahrdienst möglich",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Löschen")
            }
        }

        FlowRow(
            Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (doctor != null && doctor.phone.isNotBlank()) {
                TextButton(onClick = {
                    startIntent(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + doctor.phone.filter { it.isDigit() || it == '+' })))
                }) { Text("Anrufen") }
            }
            // Vergangene Termine: nur noch Anrufen, Nachricht und Unterlagen
            if (target != null && !past) {
                TextButton(onClick = {
                    startIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(FahrdienstSearch.routeUrl(target))))
                }) { Text("Route") }
            }
            if (outside && !past) {
                TextButton(onClick = onFahrdienst) { Text("Fahrdienst") }
            }
            if (onMail != null && doctor != null) {
                TextButton(onClick = onMail) { Text("Nachricht") }
            }
            TextButton(onClick = onDocs) {
                Text(if (attached.isEmpty()) "Unterlagen" else "Unterlagen (${attached.size})")
            }
            if (!past) TextButton(onClick = onCalendar) { Text("In Kalender eintragen") }
        }
    }
}
