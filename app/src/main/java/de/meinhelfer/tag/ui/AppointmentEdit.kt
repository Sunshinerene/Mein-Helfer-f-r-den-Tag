package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.Doctor

/**
 * Termin anlegen oder bearbeiten, mit Rueckfrage bei Serien:
 * nur diesen Termin oder diesen und alle folgenden.
 */
@Composable
internal fun AppointmentEditor(initial: Appointment?, doctors: List<Doctor>, vm: MainViewModel, onDone: () -> Unit) {
    var pending by remember { mutableStateOf<Appointment?>(null) }
    AppointmentDialog(
        initial = initial,
        doctors = doctors,
        onDismiss = onDone,
        onSave = { saved ->
            val orig = initial
            when {
                orig == null || orig.id == 0L -> {
                    vm.add(saved)
                    onDone()
                }
                orig.seriesId != 0L -> pending = saved
                else -> {
                    vm.saveAppointment(orig, saved, following = false)
                    onDone()
                }
            }
        },
    )
    val p = pending
    val orig = initial
    if (p != null && orig != null) {
        SeriesScopeDialog(
            title = "Terminserie ändern",
            text = "Dieser Termin gehört zu einer Serie (${de.meinhelfer.tag.data.Recurrence.describe(orig) ?: "wiederkehrend"}). " +
                "Eine geänderte Wiederholung gilt nur für „diesen und alle folgenden“.",
            thisLabel = "Nur diesen Termin",
            followingLabel = "Diesen und alle folgenden",
            onThis = {
                vm.saveAppointment(orig, p, following = false)
                onDone()
            },
            onFollowing = {
                vm.saveAppointment(orig, p, following = true)
                onDone()
            },
            onDismiss = { pending = null },
        )
    }
}

/** Loeschen mit Rueckfrage; bei Serien wahlweise auch alle folgenden. */
@Composable
internal fun AppointmentDeleteDialog(a: Appointment, vm: MainViewModel, onDone: () -> Unit) {
    if (a.seriesId == 0L) {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Löschen?") },
            text = { Text("„${a.title}“ wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAppointment(a, following = false)
                    onDone()
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = onDone) { Text("Abbrechen") } },
        )
    } else {
        SeriesScopeDialog(
            title = "Termin aus Serie löschen",
            text = "„${a.title}“ gehört zu einer Serie. Was soll gelöscht werden?",
            thisLabel = "Nur diesen Termin",
            followingLabel = "Diesen und alle folgenden",
            destructive = true,
            onThis = {
                vm.deleteAppointment(a, following = false)
                onDone()
            },
            onFollowing = {
                vm.deleteAppointment(a, following = true)
                onDone()
            },
            onDismiss = onDone,
        )
    }
}

@Composable
internal fun SeriesScopeDialog(
    title: String,
    text: String,
    thisLabel: String,
    followingLabel: String,
    onThis: () -> Unit,
    onFollowing: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onThis) { Text(thisLabel, color = color) }
                TextButton(onClick = onFollowing) { Text(followingLabel, color = color) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
