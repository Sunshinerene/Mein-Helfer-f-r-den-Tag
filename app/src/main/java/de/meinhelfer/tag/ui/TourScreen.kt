package de.meinhelfer.tag.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private class TourPage(
    val title: String,
    val text: String,
    val tips: List<String>,
    val mock: @Composable () -> Unit,
)

private val TOUR_PAGES = listOf(
    TourPage(
        title = "Willkommen",
        text = "Dein Helfer für den Pflegealltag: Medikamente, Termine, Aufgaben, Dokumente, Kontakte und Notruf an einem Ort. Alles bleibt verschlüsselt auf deinem Handy. Der Rundgang zeigt in wenigen Schritten, wie alles zusammenspielt.",
        tips = listOf("Du kannst jederzeit zurückblättern oder abbrechen.", "Alles Weitere steht in der Anleitung (Info-Symbol oben)."),
        mock = { MockWelcome() },
    ),
    TourPage(
        title = "Wer du bist",
        text = "Unter Einstellungen → Meine Daten und Zustimmung stehen dein Name, deine Rolle (z. B. Angehörige/r, Pflegedienst, Betreuer/in) und auf Wunsch deine Adresse. Damit unterschreibt die App vorbereitete E-Mails und zeigt passende Tipps. Deine Zustimmung zu den Nutzungsbedingungen gilt für genau diese Person.",
        tips = listOf("Änderst du Name oder Rolle, bestätigst du die Nutzungsbedingungen neu.", "Name und Adresse lassen sich auch vom Ausweis auslesen; das Foto wird danach sofort gelöscht.", "Die App ersetzt keinen Arzt und keine Apotheke. Im Notfall immer 112."),
        mock = { MockConsent() },
    ),
    TourPage(
        title = "Freigaben für Erinnerungen",
        text = "Damit Erinnerungen pünktlich kommen, braucht die App Benachrichtigungen und pünktliche Wecker. Unter Einstellungen → Erinnerungen und Freigaben siehst du, was erlaubt ist, und kommst mit einem Tipp in die richtige Android-Einstellung.",
        tips = listOf("Fehlt etwas Wichtiges, zeigt die Heute-Seite oben eine rote Karte.", "Akku-Optimierung ausschalten hilft auf manchen Handys."),
        mock = { MockPermissions() },
    ),
    TourPage(
        title = "Für wen gilt es?",
        text = "Oben steht, für welche Person gerade alles gilt. Du kannst mehrere Personen anlegen, z. B. beide Eltern, und mit einem Tipp wechseln. Im Profil stehen Pflegegrad, Adresse, Stockwerk und Wichtiges.",
        tips = listOf("Beim ersten Mal die Person umbenennen und das Profil ausfüllen.", "Kontakte gelten für alle Personen gemeinsam."),
        mock = { MockPersonBar() },
    ),
    TourPage(
        title = "Kontakte",
        text = "Trag Ärzte, Pflegedienst, Apotheke, Pflegekasse und weitere Ansprechpartner einmal ein. Von dort rufst du an oder schreibst eine vorbereitete E-Mail. „In der Nähe suchen“ findet Apotheken, Pflegedienste und mehr.",
        tips = listOf("Stockwerk und Aufzug beim Kontakt helfen bei Fahrdienst-Anfragen.", "Gesendet wird nie etwas automatisch, du prüfst und schickst selbst."),
        mock = { MockContacts() },
    ),
    TourPage(
        title = "Medikamente",
        text = "Foto der Packung machen: Die App liest Name und Packungsgröße direkt auf dem Handy aus. Dann Einnahmezeiten und Vorrat eintragen. Der Vorrat sinkt täglich, rechtzeitig vor dem Ende erinnert die App ans Nachbestellen.",
        tips = listOf("Alles vom Foto ist nur ein Vorschlag, bitte kurz prüfen.", "Einen Medikamentenplan als Dokument hochladen: Die App schlägt vor, alle Medikamente mit Einnahmezeiten zu übernehmen.", "„Nachbestellen“ bereitet Anruf, E-Mail oder PDF für Arzt und Apotheke vor."),
        mock = { MockMedication() },
    ),
    TourPage(
        title = "Medikamente im Überblick",
        text = "„Überblick & Check“ zeigt alle Medikamente mit Einschätzung. Der freiwillige KI-Gesamt-Check zeigt, welche Medikamente sich beeinflussen können (Gering, Beachten, Wichtig), gibt Hinweise zu Einnahmezeiten und sammelt Fragen für Arzt oder Apotheke.",
        tips = listOf("Nur Anhaltspunkte: nie eigenmächtig absetzen oder ändern.", "Ab 5 Medikamenten gibt es in der Apotheke eine kostenlose Medikationsberatung."),
        mock = { MockMedCheck() },
    ),
    TourPage(
        title = "Termine",
        text = "Titel wählen, Datum, Beginn und Ende, Kontakt verknüpfen, Erinnerung einstellen. Wiederkehrende Termine (z. B. jede Woche Physiotherapie) legst du mit „Wiederholen“ an.",
        tips = listOf("Bei Terminen außer Haus gibt es „Fahrdienst“.", "„Unterlagen“ schlägt passende Dokumente vor.", "Kommende Termine stehen oben, vergangene sind eingeklappt."),
        mock = { MockAppointment() },
    ),
    TourPage(
        title = "Tagesstart",
        text = "Beim ersten Öffnen am Tag kommt zuerst der Rückblick auf gestern: Was fehlt noch, wie lief es? Du ergänzt und bestätigst. Danach folgt der Tagesplan für heute, den du als PDF teilen oder drucken kannst.",
        tips = listOf("Oben auf der Heute-Seite jederzeit über „Rückblick gestern“ und „Tagesplan“.", "Abschaltbar unter Einstellungen → Heute-Seite und Motivator."),
        mock = { MockDailyStart() },
    ),
    TourPage(
        title = "Tages-Motivator",
        text = "Ein Satz für den Tag, was du in den letzten Tagen geschafft hast und kleine Vorhaben für heute. Der kurze Stimmungs-Check mit Smileys hilft dabei. Ziele, etwa einen Antrag oder etwas für deine Gesundheit, gehst du Schritt für Schritt an; der Motivator schätzt deinen Weg ein.",
        tips = listOf("Wie oft nach der Stimmung gefragt wird, stellst du unter Einstellungen → Heute-Seite und Motivator ein.", "Für Ziele gibt es Vorlagen, z. B. „Pflegegrad beantragen“. Auf Wunsch schlägt die KI einen Weg vor."),
        mock = { MockMotivator() },
    ),
    TourPage(
        title = "Tag, Woche, Monat",
        text = "Oben auf dem ersten Reiter wählst du Tag, Woche oder Monat. Der Tag zeigt zuerst „Wichtig und demnächst“: Dringendes, die Termine von heute und die nächsten Tage. In Woche und Monat kannst du Termine, Aufgaben und Einnahmen antippen, ändern und abhaken.",
        tips = listOf("Aufgaben mit hoher Priorität stehen immer ganz oben.", "Jede Karte lässt sich einklappen, dann bleibt nur eine Kurzinfo."),
        mock = { MockPeriods() },
    ),
    TourPage(
        title = "Heute: abhaken",
        text = "Einnahmen lassen sich rund um die Einnahmezeit abhaken. Danach fragt die App nach: genommen, später, vergessen oder bewusst nicht gegeben. Bei Terminen wählst du: stattgefunden, abgesagt oder verschoben.",
        tips = listOf("Mit „Notiz“ hältst du fest, was aufgefallen ist.", "Bei abgesagten Terminen legt die App auf Wunsch die Aufgabe „Neuen Termin ausmachen“ an."),
        mock = { MockToday() },
    ),
    TourPage(
        title = "Aufgaben mit Gedächtnis",
        text = "Aufgaben haben Wichtigkeit, Frist und Kontakt. Beim Abhaken hältst du fest, was herausgekommen ist, mit Sternen. Erledigtes bleibt im Verlauf und ist durchsuchbar. Legst du später eine ähnliche Aufgabe an, zeigt die App, was es dazu schon gab.",
        tips = listOf("„Verlauf und Suche“ findet auch nach Jahren, z. B. „Rollstuhl“.", "Erledigte Aufgaben stehen auch in der Auswertung."),
        mock = { MockTasks() },
    ),
    TourPage(
        title = "Dokumente und Schreiben",
        text = "Befunde, Berichte und Bescheide fotografieren oder laden. Die App liest den Text, schlägt Kategorie, Datum und Arzt vor und legt alles verschlüsselt ab. Öffnen, drucken und teilen geht direkt in der App. Für Anträge und Vollmachten gibt es Vorlagen als PDF.",
        tips = listOf("Die Suche findet auch Wörter im Text, z. B. ein Medikament.", "Eine Zusammenfassung schreibst du selbst oder lässt sie per KI erstellen."),
        mock = { MockDocuments() },
    ),
    TourPage(
        title = "Notruf und Notfallkarte",
        text = "Das rote Telefon oben ist von überall erreichbar: 112, 116 117, 110, „Was sage ich?“ mit Adresse und Stockwerk, deine Kontakte und die Notfallkarte mit Medikamenten und Wichtigem für den Rettungsdienst.",
        tips = listOf("„Wählen“ öffnet nur die Telefon-App, angerufen wird erst dort.", "Bei Lebensgefahr immer zuerst 112."),
        mock = { MockNotruf() },
    ),
    TourPage(
        title = "Erinnerungen",
        text = "Zur Einnahmezeit meldet sich die App. Mit „Eingenommen“ direkt in der Benachrichtigung hakst du ab, ohne die App zu öffnen. Wurde schon abgehakt, bleibt die Erinnerung aus.",
        tips = listOf("Bei mehreren Personen steht der Name dabei, ein Tipp öffnet die richtige Person.", "Kommt nichts an: Einstellungen → Erinnerungen und Freigaben prüfen."),
        mock = { MockNotification() },
    ),
    TourPage(
        title = "Auswertung",
        text = "Für Tag, Woche oder Monat zeigt die Auswertung, was gut lief, was auffällt, welche Aufgaben erledigt wurden und welche nächsten Schritte sinnvoll sein könnten.",
        tips = listOf("Das sind Anhaltspunkte, kein ärztlicher Rat.", "Empfehlungen lassen sich mit einem Tipp als Aufgabe merken."),
        mock = { MockSummary() },
    ),
    TourPage(
        title = "Sicher und privat",
        text = "Deine Daten bleiben verschlüsselt auf deinem Handy, ohne Konto und ohne Cloud. Ins Internet geht die App nur, wenn du etwas anforderst, z. B. Updates, Meldungen, Umkreissuche oder die KI. Eine App-Sperre und eine verschlüsselte Sicherung findest du in den Einstellungen.",
        tips = listOf("Lege regelmäßig eine Sicherung an und bewahre sie außerhalb des Handys auf.", "Anleitung: Info-Symbol oben. Einstellungen: Zahnrad oben, nach Bereichen sortiert und durchsuchbar.", "Aussehen nach Geschmack: Design, Ecken, Schriftgröße und Effekte (Beta) in den Einstellungen."),
        mock = { MockPrivacy() },
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourScreen(onClose: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { TOUR_PAGES.size })
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == TOUR_PAGES.lastIndex

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rundgang") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Rundgang beenden")
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                TourPageView(TOUR_PAGES[index], index + 1, TOUR_PAGES.size)
            }
            // Punkte
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(TOUR_PAGES.size) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (i == pagerState.currentPage) 10.dp else 7.dp)
                            .background(
                                if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    enabled = pagerState.currentPage > 0,
                ) { Text("Zurück") }
                Button(shape = buttonShape(), 
                    onClick = {
                        if (last) onClose() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                ) { Text(if (last) "Fertig" else "Weiter") }
            }
        }
    }
}

@Composable
private fun TourPageView(page: TourPage, number: Int, total: Int) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Beispielansicht
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(Modifier.padding(12.dp)) { page.mock() }
        }
        Text("Schritt $number von $total", style = MaterialTheme.typography.labelMedium)
        Text(page.title, style = MaterialTheme.typography.headlineSmall)
        Text(page.text, style = MaterialTheme.typography.bodyLarge)
        page.tips.forEach { tip ->
            Text("• $tip", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- Beispielansichten (nicht antippbar)

@Composable
private fun MockCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun MockButton(text: String, filled: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MockField(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MockChip(text: String, selected: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MockCheckRow(checked: Boolean, text: String, warn: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (warn != null) Text(warn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text("Notiz", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MockWelcome() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLogo(88.dp)
        Text("Mein Helfer für den Tag", style = MaterialTheme.typography.titleMedium)
        Text("by $AUTHOR", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        MockTabs(selected = 0)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MockTabs(selected: Int) {
    val labels = listOf("Heute", "Medikamente", "Termine", "Dokumente", "Kontakte", "Infos")
    MockCard {
        Text("Mein Helfer für den Tag", style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MockPersonBar() {
    MockCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Für:", style = MaterialTheme.typography.labelLarge)
            Text("Mutter  ▾", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    MockCard {
        Text("✓ Mutter", style = MaterialTheme.typography.bodyMedium)
        Text("Vater", style = MaterialTheme.typography.bodyMedium)
        Text("Neue Person anlegen …", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MockContacts() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MockChip("Alle", selected = true)
        MockChip("Hausarzt")
        MockChip("Pflegedienst")
        MockChip("Apotheke")
    }
    MockCard {
        Text("Praxis Dr. Muster (Hausarzt)", style = MaterialTheme.typography.titleSmall)
        Text("030 123456", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MockButton("Anrufen")
            MockButton("E-Mail")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MockMail() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MockChip("Termin anfragen", selected = true)
        MockChip("Rezept anfordern")
        MockChip("Überweisung")
    }
    MockField("Betreff", "Terminanfrage – Anita Muster")
    MockCard {
        Text("Guten Tag, ich melde mich als Tochter von Anita Muster …", style = MaterialTheme.typography.bodyMedium)
        Text("Mit freundlichen Grüßen, dein Name, Telefon, E-Mail", style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MockButton("In E-Mail-App öffnen")
        MockButton("Text kopieren")
    }
}

@Composable
private fun MockMedication() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MockButton("Packung fotografieren")
        MockButton("Galerie")
    }
    MockField("Name", "Beispiel 500 mg")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MockChip("Morgens 08:00", selected = true)
        MockChip("Abends 18:00", selected = true)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MockField("Vorrat", "30", Modifier.weight(1f))
        MockField("Pro Tag", "2", Modifier.weight(1f))
    }
}

@Composable
private fun MockAppointment() {
    MockField("Titel", "Kontrolltermin", Modifier.fillMaxWidth())
    MockField("Datum", "Sa., 28.11.2026", Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MockField("Von", "10:00 Uhr", Modifier.weight(1f))
        MockField("Bis (optional)", "10:30 Uhr", Modifier.weight(1f))
    }
    MockField("Wiederholen", "Jede Woche", Modifier.fillMaxWidth())
    MockField("Erinnerung", "1 Stunde vorher", Modifier.fillMaxWidth())
}

@Composable
private fun MockToday() {
    MockCard {
        Text("Heute einnehmen (1 von 3)", style = MaterialTheme.typography.titleSmall)
        MockCheckRow(true, "08:00 Uhr – Beispiel (1 Tablette)")
        MockCheckRow(false, "12:00 Uhr – Beispiel (½ Tablette)", warn = "noch nicht abgehakt")
        MockCheckRow(false, "18:00 Uhr – Beispiel (1 Tablette)")
    }
}

@Composable
private fun MockNotification() {
    MockCard {
        Text("Medikament einnehmen (08:00 Uhr)", style = MaterialTheme.typography.titleSmall)
        Text("Beispiel (1 Tablette)", style = MaterialTheme.typography.bodyMedium)
        MockButton("Eingenommen")
    }
}

@Composable
private fun MockSummary() {
    MockCard {
        Text("Überblick", style = MaterialTheme.typography.titleSmall)
        Text("• Medikamente: 5 von 6 Einnahmen abgehakt (83 %)", style = MaterialTheme.typography.bodyMedium)
    }
    MockCard {
        Text("Auffällig", style = MaterialTheme.typography.titleSmall)
        Text("• Vorrat reicht nur noch ca. 5 Tage.", style = MaterialTheme.typography.bodyMedium)
    }
    MockCard {
        Text("Empfehlungen (nur Anhaltspunkte)", style = MaterialTheme.typography.titleSmall)
        Text("• Rezept rechtzeitig anfragen.", style = MaterialTheme.typography.bodyMedium)
        Text("+ Als Aufgabe merken", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MockPrivacy() {
    MockCard {
        Text("Verschlüsselt auf deinem Handy", style = MaterialTheme.typography.titleSmall)
        Text("Personen, Medikamente, Termine, Dokumente, Notizen", style = MaterialTheme.typography.bodyMedium)
    }
    MockCard {
        Text("Kein Konto, keine Cloud", style = MaterialTheme.typography.titleSmall)
        Text("Internet nur auf deinen Wunsch", style = MaterialTheme.typography.bodyMedium)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MockButton("App-Sperre")
        MockButton("Sicherung erstellen")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MockConsent() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MockChip("Pflegende/r Angehörige/r", selected = true)
        MockChip("Pflegedienst")
        MockChip("Betreuer/in")
    }
    MockField("Dein Name", "Maria Muster", Modifier.fillMaxWidth())
    MockCard {
        MockConfirm("App ersetzt keinen Arzt, im Notfall 112")
        MockConfirm("Einschätzungen sind nur Anhaltspunkte")
        MockConfirm("Nutzungsbedingungen akzeptiert")
    }
}

@Composable
private fun MockConfirm(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = true, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MockPermissions() {
    MockCard {
        Text("Freigaben (Android)", style = MaterialTheme.typography.titleSmall)
        Text("✓ Benachrichtigungen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text("✓ Erinnerungen mit Ton", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✗ Pünktliche Wecker", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            MockButton("Erlauben")
        }
    }
}

@Composable
private fun MockMedCheck() {
    MockCard {
        Text("Wie passen die Medikamente zusammen?", style = MaterialTheme.typography.titleSmall)
        MockLevel("Beachten", MaterialTheme.colorScheme.tertiary, "Beispiel A + Beispiel B: Abstand von 2 Stunden einhalten")
        MockLevel("Gering", MaterialTheme.colorScheme.primary, "Beispiel A + Beispiel C: kaum zu erwarten")
    }
    MockCard {
        Text("Fragen an Arzt oder Apotheke", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("• Ist Beispiel B noch nötig?", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("Merken", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MockLevel(level: String, color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = MaterialTheme.shapes.small, color = color) {
            Text(level, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.surface)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MockDailyStart() {
    MockCard {
        Text("Rückblick auf gestern", style = MaterialTheme.typography.titleSmall)
        Text("• Medikamente: 5 von 6 genommen", style = MaterialTheme.typography.bodyMedium)
        Text("20:00 Uhr – Beispiel: noch ohne Antwort", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        MockButton("Bestätigen und weiter", filled = true)
    }
    MockCard {
        Text("Heute zu tun", style = MaterialTheme.typography.titleSmall)
        Text("☐ 08:00 Uhr – Beispiel (1 Tablette)", style = MaterialTheme.typography.bodyMedium)
        Text("☐ 10:30 Uhr – Hausarzt", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MockButton("Als PDF teilen")
            MockButton("Drucken")
        }
    }
}

@Composable
private fun MockMotivator() {
    MockCard {
        Text("Wie geht es dir gerade?", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("😄", "🙂", "😐", "😕", "😢").forEach { Text(it, style = MaterialTheme.typography.headlineSmall) }
        }
    }
    MockCard {
        Text("Kleine Schritte bringen dich auch ans Ziel.", style = MaterialTheme.typography.bodyMedium)
        Text("Geschafft: 4 Aufgaben erledigt · 12 Einnahmen gegeben", style = MaterialTheme.typography.bodySmall)
        MockCheckRow(true, "„Pflegegrad beantragen“: Antrag stellen")
        MockCheckRow(false, "Eine kleine Pause zwischendurch einplanen")
    }
}

@Composable
private fun MockPeriods() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MockChip("Tag", selected = true)
        MockChip("Woche")
        MockChip("Monat")
    }
    MockCard {
        Text("Wichtig und demnächst", style = MaterialTheme.typography.titleSmall)
        Text("Dringend: Rezept abholen (heute fällig)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Text("Termine heute: 10:30 Uhr – Hausarzt", style = MaterialTheme.typography.bodyMedium)
        Text("Morgen: 14:00 Uhr – Physiotherapie", style = MaterialTheme.typography.bodyMedium)
        Text("Einklappen ▴", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MockTasks() {
    MockCard {
        Text("Erledigt – was kam heraus?", style = MaterialTheme.typography.titleSmall)
        MockField("Ergebnis", "Firma Muster, Tel. 0123 456, schnell und günstig", Modifier.fillMaxWidth())
        Text("★★★★☆", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    MockCard {
        Text("Dazu gab es schon etwas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("erledigt am 12.03.2025 · ★★★★☆", style = MaterialTheme.typography.labelMedium)
        Text("Reparaturdienst für den Rollstuhl suchen", style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MockDocuments() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MockChip("Alle (12)", selected = true)
        MockChip("Arztbericht (4)")
        MockChip("Krankenhaus / Entlassbericht (1)")
    }
    MockCard {
        Text("Entlassbericht 14.11.2026", style = MaterialTheme.typography.titleSmall)
        Text("Krankenhaus / Entlassbericht · 14.11.2026", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MockButton("Öffnen")
            MockButton("Drucken")
            MockButton("Teilen")
        }
    }
}

@Composable
private fun MockNotruf() {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Notruf: Feuerwehr und Rettungsdienst", style = MaterialTheme.typography.titleSmall)
            MockButton("112 wählen", filled = true)
        }
    }
    MockCard {
        Text("Was sage ich?", style = MaterialTheme.typography.titleSmall)
        Text("Wo: Musterstraße 1, 4. Stock, ohne Aufzug", style = MaterialTheme.typography.bodyMedium)
    }
    MockButton("Notfallkarte zeigen")
}
