package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.export.CarePdfs
import de.meinhelfer.tag.export.ReorderExport
import de.meinhelfer.tag.summary.VitalKind
import de.meinhelfer.tag.update.AppPrefs

/**
 * Alle Berichte und Ausdrucke der gewählten Person an einem Ort (Auswertung).
 * Was Einstellungen braucht (Tagesplan, Übergabe), wird geöffnet; der Rest entsteht direkt als PDF.
 */
@Composable
fun ReportsCard(vm: MainViewModel, onOpenDailyPlan: () -> Unit, onOpenHandover: () -> Unit, onOpenBudget: () -> Unit) {
    val persons by vm.persons.collectAsStateWithLifecycle()
    val personId by vm.personId.collectAsStateWithLifecycle()
    val meds by vm.medications.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val duties by vm.duties.collectAsStateWithLifecycle()
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    val careLog by vm.careLog.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val person = persons.firstOrNull { it.id == personId }
    val low = lowStockMeds(meds, today)
    val measured = vitals.filter { it.day > today - 14 }
    val diary = careLog.filter { it.day > today - 30 }

    Foldable(
        key = "berichte",
        title = "Berichte und Ausdrucke",
        summary = "Medikamentenplan, Notfallkarte, Tagesplan, Werte, Tagebuch, Übergabe, Nachbestellung",
    ) {
        HCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Berichte und Ausdrucke" + (person?.let { " · ${it.name}" } ?: ""), style = MaterialTheme.typography.titleMedium)
                Text("Alles als PDF zum Teilen oder Drucken. Nichts wird von selbst verschickt.", style = MaterialTheme.typography.bodySmall)

                ReportRow("💊 Medikamentenplan", if (meds.isEmpty()) "Noch keine Medikamente eingetragen" else "${meds.size} Medikamente, mit Zuständigen") {
                    if (meds.isNotEmpty()) {
                        PdfButtons(shareLabel = "Teilen", chooserTitle = "Medikamentenplan teilen") { ctx ->
                            CarePdfs.medPlan(ctx, person, meds, doctors, AppPrefs.caregiver(ctx), duties)
                        }
                    }
                }
                ReportRow("🆘 Notfallkarte", "Name, Pflegegrad, Wichtiges, Medikamente, Kontakte (ohne Versichertennummer; mit Nummer über das rote Telefon)") {
                    PdfButtons(shareLabel = "Teilen", chooserTitle = "Notfallkarte teilen") { ctx ->
                        CarePdfs.emergency(ctx, person, meds, doctors, AppPrefs.caregiver(ctx), withInsuranceNumber = false)
                    }
                }
                ReportRow("🗓 Tagesplan für heute", "Einnahmen, Termine, Aufgaben, Trinken und Werte, mit Kästchen zum Abhaken") {
                    OutlinedButton(shape = buttonShape(), onClick = onOpenDailyPlan) { Text("Öffnen") }
                }
                ReportRow("💧 Trinken und Werte", if (measured.isEmpty()) "In den letzten 14 Tagen nichts eingetragen" else "Letzte 14 Tage, ${measured.size} Einträge") {
                    if (measured.isNotEmpty()) {
                        PdfButtons(shareLabel = "Teilen", chooserTitle = "Protokoll teilen") { ctx ->
                            CarePdfs.vitals(
                                ctx, person, measured, AppPrefs.drinkGoal(ctx, personId),
                                VitalKind.MEASURES.mapNotNull { k -> AppPrefs.vitalRange(ctx, personId, k.key)?.let { k.key to it } }.toMap(),
                                today - 13, today,
                            )
                        }
                    }
                }
                ReportRow("📓 Pflegetagebuch", if (diary.isEmpty()) "In den letzten 30 Tagen nichts eingetragen" else "Letzte 30 Tage, an ${diary.map { it.day }.distinct().size} Tagen") {
                    if (diary.isNotEmpty()) {
                        PdfButtons(shareLabel = "Teilen", chooserTitle = "Pflegetagebuch teilen") { ctx ->
                            CarePdfs.careDiary(ctx, person, diary, today - 29, today, AppPrefs.caregiver(ctx))
                        }
                    }
                }
                ReportRow("📋 Übergabe-Bericht", "Für Vertretung: Zeitraum, Medikamente, Termine, Kontakte, Hinweise") {
                    OutlinedButton(shape = buttonShape(), onClick = onOpenHandover) { Text("Öffnen") }
                }
                ReportRow("🛒 Nachbestellung", if (low.isEmpty()) "Zurzeit reicht der Vorrat überall länger als eine Woche" else "${low.size} Medikamente bald nachbestellen") {
                    if (low.isNotEmpty()) {
                        PdfButtons(shareLabel = "Teilen", chooserTitle = "Nachbestell-Liste teilen") { ctx ->
                            ReorderExport.createPdf(ctx, person?.name, ReorderExport.rows(low, doctors, today))
                        }
                    }
                }
                ReportRow("💶 Budgets der Pflegekasse", "Entlastungsbetrag, Verhinderungs- und Kurzzeitpflege") {
                    OutlinedButton(shape = buttonShape(), onClick = onOpenBudget) { Text("Öffnen") }
                }
                Text("Die Auswertung darunter lässt sich als Text teilen.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReportRow(title: String, subtitle: String, actions: @Composable () -> Unit) {
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
        actions()
    }
}
