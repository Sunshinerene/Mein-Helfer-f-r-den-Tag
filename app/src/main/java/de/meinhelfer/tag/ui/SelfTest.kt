package de.meinhelfer.tag.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.CareLogEntry
import de.meinhelfer.tag.data.CaregiverProfile
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Duties
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.Responsibility
import de.meinhelfer.tag.data.Todo
import de.meinhelfer.tag.data.VitalEntry
import de.meinhelfer.tag.export.CarePdfs
import de.meinhelfer.tag.reminder.Notifications
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Erfundene Beispieldaten fuer den Selbsttest. Werden nie gespeichert. */
internal object DemoData {
    const val PID = 999_999L
    val person = Person(
        id = PID, name = "Erika Beispiel", birthDate = LocalDate.of(1941, 3, 12).toEpochDay(), careLevel = 3,
        important = "Allergie gegen Penicillin. Hört links schlecht. Sturzgefahr nachts.",
        street = "Musterweg 5", zip = "12345", city = "Beispielstadt", insurer = "Beispiel-Krankenkasse", insuranceNumber = "A123456789",
        floor = "2. Stock", elevator = 2, access = "Schlüssel bei Nachbarin Müller", phone = "0123 456789",
    )
    val doctors = listOf(
        Doctor(id = 901, name = "Dr. Beispiel", specialty = "Allgemeinmedizin", phone = "0123 1111", address = "Hauptstr. 1, 12345 Beispielstadt"),
        Doctor(id = 902, name = "Löwen-Apotheke", specialty = "Apotheke", phone = "0123 2222"),
        Doctor(id = 903, name = "Pflegedienst Sonne", specialty = "Pflegedienst", phone = "0123 3333"),
        Doctor(id = 904, name = "Dr. Vertretung", specialty = "Allgemeinmedizin", phone = "0123 4444"),
    )
    val caregiver = CaregiverProfile(role = "angehoerig", name = "Max Beispiel", relation = "Sohn", phone = "0170 0000000")
    fun duties(today: Long) = listOf(
        Responsibility(personId = PID, role = Duties.REZEPT, contactId = 901),
        Responsibility(personId = PID, role = Duties.APOTHEKE, contactId = 902),
        Responsibility(personId = 0, role = Duties.VERTRETUNG, contactId = 904, replacesId = 901, fromDay = today + 3, untilDay = today + 10),
    )
    val meds = listOf(
        Medication(name = "Ramipril", dosage = "5 mg · 1-0-0-0", times = "08:00", personId = PID, stock = 40.0),
        Medication(name = "Metformin", dosage = "500 mg · 1-0-1-0", times = "08:00,18:00", personId = PID, notes = "zum Essen", stock = 8.0, dailyUse = 2.0),
        Medication(name = "Simvastatin", dosage = "20 mg", times = "22:00", personId = PID, stock = 60.0),
        Medication(name = "ASS", dosage = "100 mg · 1-0-0-0", times = "08:00", personId = PID, notes = "Blutverdünner (TAH)", stock = 30.0),
        Medication(
            name = "Insulin glargin (Pen)", dosage = "100 E/ml", times = "08:00,18:00", personId = PID, unit = "IE",
            doses = "08:00=8,18:00=6", stock = 900.0, dailyUse = 14.0, packSize = 1500.0,
        ),
        Medication(name = "Lactulose Sirup", dosage = "10 ml", times = "08:00", personId = PID, unit = "ml", doses = "08:00=10", stock = 200.0, dailyUse = 10.0, packSize = 500.0),
    )

    private fun at(day: Long, hour: Int, minute: Int = 0) =
        LocalDate.ofEpochDay(day).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun vitals(today: Long): List<VitalEntry> = buildList {
        for (i in 13 downTo 0) {
            val d = today - i
            val glasses = listOf(250.0, 200.0, 150.0, 250.0, 200.0).take(3 + (i % 3))
            glasses.forEachIndexed { n, ml -> add(VitalEntry(personId = PID, atMillis = at(d, 8 + n * 3), day = d, kind = "trinken", value = ml, unit = "ml")) }
            add(VitalEntry(personId = PID, atMillis = at(d, 8, 10), day = d, kind = "blutdruck", value = 128.0 + (i * 7 % 19), value2 = 78.0 + (i * 3 % 9), unit = "mmHg"))
            add(VitalEntry(personId = PID, atMillis = at(d, 8, 10), day = d, kind = "puls", value = 66.0 + (i * 5 % 11), unit = "/min"))
            if (i % 3 == 0) add(VitalEntry(personId = PID, atMillis = at(d, 7, 30), day = d, kind = "gewicht", value = 68.4 + (i % 4) * 0.3, unit = "kg"))
            if (i % 2 == 0) add(VitalEntry(personId = PID, atMillis = at(d, 7, 45), day = d, kind = "blutzucker", value = 110.0 + (i * 11 % 40), unit = "mg/dl", note = if (i == 4) "nach dem Frühstück" else ""))
        }
    }

    fun careLog(today: Long): List<CareLogEntry> = buildList {
        for (i in 9 downTo 0) {
            val d = today - i
            add(CareLogEntry(personId = PID, day = d, atMillis = at(d, 8), module = 4, activity = "Waschen (Oberkörper vorne)", help = "teilweise", times = 1, minutes = 15))
            add(CareLogEntry(personId = PID, day = d, atMillis = at(d, 8, 30), module = 4, activity = "Unterkörper an- und auskleiden", help = "komplett", times = 2, minutes = 10))
            add(CareLogEntry(personId = PID, day = d, atMillis = at(d, 9), module = 5, activity = "Medikamente geben", help = "komplett", times = 3, minutes = 10))
            add(CareLogEntry(personId = PID, day = d, atMillis = at(d, 12), module = 2, activity = "Orientierung (Zeit, Tag)", help = "erinnern", times = 4))
            if (i % 2 == 0) add(CareLogEntry(personId = PID, day = d, atMillis = at(d, 23), module = 3, activity = "Nächtliche Unruhe", help = "beruhigen", times = 1, minutes = 20, night = true, note = "sucht nachts die Haustür"))
            if (i % 3 == 0) add(CareLogEntry(personId = PID, day = d, atMillis = at(d, 10), module = 1, activity = "Treppen steigen", help = "aufsicht", times = 2, note = "nur mit Begleitung"))
        }
    }

    fun appointments(today: Long) = listOf(
        Appointment(title = "Hausarzt: Blutabnahme", whenMillis = at(today + 2, 9, 30), personId = PID, doctorId = 901, place = "Hauptstr. 1"),
        Appointment(title = "Fußpflege", whenMillis = at(today + 6, 14), personId = PID),
    )

    fun todos(today: Long) = listOf(
        Todo(personId = PID, text = "Rezept Metformin beim Hausarzt bestellen", dueDay = today + 3, priority = 2),
        Todo(personId = PID, text = "Hörgerät-Batterien kaufen", dueDay = today + 5),
    )
}

/** Selbsttest in den Einstellungen: Datenbank pruefen, Beispiel-PDFs und -Grafiken ansehen, Erinnerungen ausprobieren. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelfTestCard(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Selbsttest", style = MaterialTheme.typography.titleMedium)
            Text(
                "Hilft beim Ausprobieren neuer Funktionen. Die Beispiele nutzen erfundene Daten („Erika Beispiel“), es wird nichts gespeichert und nichts gesendet.",
                style = MaterialTheme.typography.bodySmall,
            )

            Text("1. Datenbank", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(shape = buttonShape(), enabled = !running, onClick = {
                running = true
                scope.launch {
                    result = runCatching { vm.dbCheck() }.getOrElse { listOf("Fehler: ${it.message}") }
                    running = false
                }
            }) { Text(if (running) "Prüfe …" else "Datenbank prüfen") }
            result.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (result.isNotEmpty()) {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Selbsttest Mein Helfer für den Tag\n" + result.joinToString("\n"))
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "Ergebnis teilen")) }
                }) { Text("Ergebnis teilen (ohne persönliche Daten)") }
            }

            HorizontalDivider()
            Text("2. Beispiel-PDFs", style = MaterialTheme.typography.titleSmall)
            Text("„Drucken“ zeigt eine Vorschau, ohne dass du wirklich drucken musst.", style = MaterialTheme.typography.bodySmall)
            Text("Trink- und Vitalprotokoll (Querformat)", style = MaterialTheme.typography.bodyMedium)
            PdfButtons(shareLabel = "Teilen", chooserTitle = "Beispiel teilen") { ctx ->
                CarePdfs.vitals(ctx, DemoData.person, DemoData.vitals(today), 1300, mapOf("blutdruck" to (100.0 to 135.0)), today - 13, today)
            }
            Text("Pflegetagebuch-Bericht", style = MaterialTheme.typography.bodyMedium)
            PdfButtons(shareLabel = "Teilen", chooserTitle = "Beispiel teilen") { ctx ->
                CarePdfs.careDiary(ctx, DemoData.person, DemoData.careLog(today), today - 9, today, DemoData.caregiver)
            }
            Text("Übergabe-Bericht", style = MaterialTheme.typography.bodyMedium)
            PdfButtons(shareLabel = "Teilen", chooserTitle = "Beispiel teilen") { ctx ->
                CarePdfs.handover(
                    ctx, DemoData.person, DemoData.meds, DemoData.doctors, DemoData.duties(today), DemoData.appointments(today),
                    DemoData.todos(today), DemoData.vitals(today), DemoData.careLog(today), DemoData.caregiver, 1300,
                    CarePdfs.HandoverOptions(
                        fromDay = today, toDay = today + 14, substitute = "Nachbarin Frau Müller",
                        notes = listOf("Tagesablauf" to "8 Uhr aufstehen, Frühstück mit Tabletten\n13 Uhr Mittagsruhe\n20 Uhr ins Bett", "Wichtige Hinweise" to "Pflegedienst kommt morgens um 7:30 Uhr."),
                    ),
                )
            }
            Text("Notfallkarte mit Karte für die Geldbörse", style = MaterialTheme.typography.bodyMedium)
            PdfButtons(shareLabel = "Teilen", chooserTitle = "Beispiel teilen") { ctx ->
                CarePdfs.emergency(ctx, DemoData.person, DemoData.meds, DemoData.doctors, DemoData.caregiver, true)
            }
            Text("Medikamentenplan (Querformat)", style = MaterialTheme.typography.bodyMedium)
            PdfButtons(shareLabel = "Teilen", chooserTitle = "Beispiel teilen") { ctx ->
                CarePdfs.medPlan(ctx, DemoData.person, DemoData.meds, DemoData.doctors, DemoData.caregiver, DemoData.duties(today))
            }

            HorizontalDivider()
            Text("3. Beispiel-Grafiken", style = MaterialTheme.typography.titleSmall)
            val demo = remember(today) { DemoData.vitals(today) }
            Text("Trinkmenge 14 Tage (gestrichelt: Ziel 1300 ml)", style = MaterialTheme.typography.bodySmall)
            MiniChart((13 downTo 0).map { i -> demo.filter { it.kind == "trinken" && it.day == today - i }.sumOf { it.value } }, null, listOf(1300.0), bars = true)
            Text("Blutdruck (gestrichelt: Bereich 100 bis 135)", style = MaterialTheme.typography.bodySmall)
            val bp = demo.filter { it.kind == "blutdruck" }
            MiniChart(bp.map { it.value }, bp.map { it.value2 ?: it.value }, listOf(100.0, 135.0))

            HorizontalDivider()
            Text("4. Erinnerungen", style = MaterialTheme.typography.titleSmall)
            Text("Schickt sofort eine Test-Benachrichtigung. Antippen sollte die passende Seite öffnen.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(shape = buttonShape(), onClick = {
                    Notifications.show(context, "test-trinken".hashCode(), "Test: Zeit für ein Getränk", "So sieht die Trink-Erinnerung aus. Antippen öffnet „Trinken und Werte“.", personId = AppPrefs.currentPerson(context), channel = Notifications.CHANNEL_CARE, screen = "trinken")
                }) { Text("Trinken") }
                OutlinedButton(shape = buttonShape(), onClick = {
                    Notifications.show(context, "test-tagebuch".hashCode(), "Test: Pflegetagebuch", "So sieht die Erinnerung am Abend aus. Antippen öffnet das Pflegetagebuch.", personId = AppPrefs.currentPerson(context), channel = Notifications.CHANNEL_CARE, screen = "tagebuch")
                }) { Text("Pflegetagebuch") }
            }
            Text("Kommt nichts an: Einstellungen → Erinnerungen und Freigaben → Benachrichtigungen erlauben.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
