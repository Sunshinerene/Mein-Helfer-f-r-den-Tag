package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.reminder.ReminderScheduler
import de.meinhelfer.tag.update.AppPrefs
import java.time.LocalTime

/** Trink-Erinnerung: Aus, alle 2 oder alle 3 Stunden. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrinkReminderSetting() {
    val context = LocalContext.current
    var interval by remember { mutableIntStateOf(AppPrefs.drinkReminder(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Ans Trinken erinnern", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Aus", 2 to "Alle 2 Std.", 3 to "Alle 3 Std.").forEach { (h, label) ->
                FilterChip(selected = interval == h, onClick = {
                    interval = h
                    AppPrefs.setDrinkReminder(context, h)
                    ReminderScheduler.scheduleDrink(context)
                }, label = { Text(label) })
            }
        }
        Text(
            when (interval) {
                2 -> "Um 10, 12, 14, 16 und 18 Uhr, aber nur, wenn bis dahin weniger getrunken wurde als geplant."
                3 -> "Um 10, 13, 16 und 19 Uhr, aber nur, wenn bis dahin weniger getrunken wurde als geplant."
                else -> "Erinnert tagsüber, wenn weniger getrunken wurde als bis dahin geplant (nur bei Personen mit Trinkziel und Einträgen in den letzten 7 Tagen)."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Abends ans Pflegetagebuch erinnern, wenn heute noch nichts eingetragen ist. */
@Composable
fun DiaryReminderSetting() {
    val context = LocalContext.current
    var minute by remember { mutableIntStateOf(AppPrefs.diaryReminder(context)) }
    fun save(m: Int) {
        minute = m
        AppPrefs.setDiaryReminder(context, m)
        ReminderScheduler.scheduleDiary(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Abends ans Pflegetagebuch erinnern", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Switch(checked = minute >= 0, onCheckedChange = { on -> save(if (on) 19 * 60 + 30 else -1) })
        }
        if (minute >= 0) {
            TimePickerField(
                label = "Uhrzeit",
                time = LocalTime.of(minute / 60, minute % 60),
                onChange = { t -> save(t.hour * 60 + t.minute) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("Nur wenn für die Person heute noch nichts im Pflegetagebuch steht. Antippen öffnet das Tagebuch.", style = MaterialTheme.typography.bodySmall)
    }
}

/** Karte in den Einstellungen unter „Erinnerungen und Freigaben“. */
@Composable
fun CareReminderCard() {
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Trinken und Pflegetagebuch", style = MaterialTheme.typography.titleMedium)
            DrinkReminderSetting()
            DiaryReminderSetting()
        }
    }
}
