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

private val TOUR_PAGES: List<TourPage> = listOf(
    TourPage(
        title = "Willkommen",
        text = "Ein ruhiger Überblick für den Pflegealltag: Medikamente, Termine, Kontakte und deine Notizen. Der Rundgang zeigt dir in wenigen Schritten, wie alles zusammenspielt.",
        tips = listOf("Du kannst jederzeit zurückblättern oder abbrechen.", "Später findest du alles auch in der Anleitung (Info-Symbol oben)."),
        mock = { MockWelcome() },
    ),
    TourPage(
        title = "Für wen gilt es?",
        text = "Oben steht, für welche Person gerade alles gilt. Du kannst mehrere Personen anlegen, z. B. beide Eltern, und mit einem Tipp wechseln.",
        tips = listOf("Beim ersten Mal die Person umbenennen.", "Kontakte gelten für alle Personen gemeinsam."),
        mock = { MockPersonBar() },
    ),
    TourPage(
        title = "Kontakte anlegen",
        text = "Trag Ärzte, Pflegedienst, Apotheke, Pflegekasse und weitere Ansprechpartner einmal ein. Von dort aus rufst du mit einem Tipp an oder schreibst eine E-Mail.",
        tips = listOf("Die Art des Kontakts lässt sich auswählen oder selbst eintippen.", "Mit den Filtern oben findest du schnell z. B. nur den Pflegedienst."),
        mock = { MockContacts() },
    ),
    TourPage(
        title = "Medikamente anlegen",
        text = "Foto der Packung machen, die App liest Name und Packungsgröße direkt auf dem Handy aus. Dann Einnahmezeiten wählen und den Vorrat eintragen.",
        tips = listOf("Alles vom Foto ist nur ein Vorschlag, bitte kurz prüfen.", "Der Vorrat sinkt jeden Tag automatisch, du wirst rechtzeitig zum Nachbestellen erinnert."),
        mock = { MockMedication() },
    ),
    TourPage(
        title = "Termine eintragen",
        text = "Titel auswählen oder selbst eintippen, Datum im Kalender wählen, Beginn und Ende angeben und einen Kontakt verknüpfen. Die Erinnerung stellst du pro Termin ein.",
        tips = listOf("Mit „In Kalender eintragen“ landet der Termin im Handy-Kalender.", "Das Ende ist freiwillig."),
        mock = { MockAppointment() },
    ),
    TourPage(
        title = "Heute: abhaken",
        text = "Auf der Heute-Seite siehst du, was jetzt ansteht. Haken setzen, wenn eine Einnahme oder ein Termin erledigt ist. Vergessenes wird rot markiert.",
        tips = listOf("Mit „Notiz“ hältst du fest, was aufgefallen ist.", "Dort stehen auch deine To-dos und die Bemerkung zum Tag."),
        mock = { MockToday() },
    ),
    TourPage(
        title = "Notizen und To-dos",
        text = "Schreib kurz auf, was auffällt: „später genommen“, „Termin verschoben“, „wenig gegessen“. Die Schnellauswahl spart Tipparbeit.",
        tips = listOf("Notizen fließen in die Auswertung ein.", "Was du später klären willst, kommt auf die To-do-Liste."),
        mock = { MockNotes() },
    ),
    TourPage(
        title = "Erinnerungen",
        text = "Zur Einnahmezeit meldet sich die App. Mit „Eingenommen“ direkt in der Benachrichtigung hakst du ab, ohne die App zu öffnen.",
        tips = listOf("Wenn nichts erscheint: Benachrichtigungen und Akku-Optimierung in den Handy-Einstellungen prüfen."),
        mock = { MockNotification() },
    ),
    TourPage(
        title = "Auswertung",
        text = "Für Tag, Woche oder Monat zeigt die Auswertung, was gut lief, was auffällt und welche nächsten Schritte sinnvoll sein könnten.",
        tips = listOf("Das sind Anhaltspunkte, kein ärztlicher Rat.", "Empfehlungen lassen sich mit einem Tipp auf die To-do-Liste setzen."),
        mock = { MockSummary() },
    ),
    TourPage(
        title = "Datenschutz und Hilfe",
        text = "Deine Daten bleiben auf deinem Handy. Fotos werden nur dort ausgelesen. Ins Internet geht die App nur für Updates.",
        tips = listOf("Anleitung: Info-Symbol oben rechts.", "Updates, Darstellung und Feedback: Zahnrad oben rechts.", "Dies ist eine Testversion ohne Verschlüsselung und App-Sperre."),
        mock = { MockPrivacy() },
    ),
)

/** Rundgang: Seiten zum Durchblättern mit kleinen Beispielansichten. */
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
                Button(
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
            shape = RoundedCornerShape(20.dp),
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun MockButton(text: String, filled: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
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
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
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
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
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

@Composable
private fun MockTabs(selected: Int) {
    val labels = listOf("Heute", "Medikamente", "Termine", "Kontakte")
    MockCard {
        Text("Mein Helfer für den Tag", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MockNotes() {
    MockField("Bemerkung", "Später genommen; wenig gegessen", Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MockChip("Später genommen")
        MockChip("Verschoben")
        MockChip("Wenig getrunken")
        MockChip("Nebenwirkung aufgefallen")
    }
    MockCard {
        Text("To-dos (1 offen)", style = MaterialTheme.typography.titleSmall)
        MockCheckRow(false, "Medikamentenplan beim Hausarzt besprechen")
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
        Text("+ Auf meine To-do-Liste", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MockPrivacy() {
    MockCard {
        Text("Auf deinem Handy", style = MaterialTheme.typography.titleSmall)
        Text("Personen, Medikamente, Termine, Notizen", style = MaterialTheme.typography.bodyMedium)
    }
    MockCard {
        Text("Nicht in der Cloud", style = MaterialTheme.typography.titleSmall)
        Text("Kein Konto, keine Übertragung von Fotos oder Daten", style = MaterialTheme.typography.bodyMedium)
    }
}
