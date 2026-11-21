package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.info.NewsFeed
import de.meinhelfer.tag.info.NewsItem
import de.meinhelfer.tag.info.PersonalTopics
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Symbol "Artikel" fuer die Navigationsleiste. */
val InfoIcon: ImageVector = ImageVector.Builder(
    name = "Infos",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = addPathNodes(
        "M19,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM14,17H7v-2h7v2zM17,13H7v-2h10v2zM17,9H7V7h10v2z",
    ),
    fill = SolidColor(Color.Black),
).build()

/** Stand der festen Themenkarten. Bitte bei Aenderungen aktualisieren. */
private const val INFO_STAND = "21.11.2026"

private class InfoCard(
    val title: String,
    val text: String,
    val query: String? = null,
    val links: List<Pair<String, String>> = emptyList(),
    val wegweiser: Boolean = false,
)

private val INFO_CARDS = listOf(
    InfoCard(
        "Pflegereform 2027 (Pflegeneuordnungsgesetz)",
        "Das Gesetz ist noch nicht beschlossen. Das Kabinett hat den Entwurf am 30.09.2026 beschlossen, der Bundestag hat ihn am 05.11. in erster Lesung beraten. " +
            "Die Anhörung im Gesundheitsausschuss ist für den 25.11. vorgesehen, die Abstimmung im Bundestag für den 11.12.2026 geplant. " +
            "Vorgesehen sind u. a. gebündelte Budgets für die häusliche Pflege (Pflegegeld und Sachleistungen), eine regelmäßige Anpassung der Leistungen (erstmals 2028), " +
            "höhere Beiträge für Kinderlose und eine Pflegebegleitung. Berichten zufolge sind zwei geplante Kürzungen wieder gestrichen " +
            "(halbes Pflegegeld in den ersten drei Monaten bei Pflegegrad 2 und 3, geringere Rentenbeiträge für Pflegepersonen). " +
            "Bis zum Inkrafttreten gelten die heutigen Leistungen. Im Verfahren sind noch Änderungen möglich.",
        query = "Pflegeneuordnungsgesetz",
        links = listOf(
            "Gesundheitsministerium: Fragen und Antworten" to "https://www.bundesgesundheitsministerium.de/service/gesetze-und-verordnungen/guv-21-lp/pnog",
            "Bundestag: Meldung zum Gesetzentwurf" to "https://www.bundestag.de/1230676",
        ),
    ),
    InfoCard(
        "Das gilt heute",
        "Bis die Reform in Kraft ist, bleiben die bisherigen Leistungen bestehen. Die aktuellen Beträge je Pflegegrad findest du im Wegweiser Leistungen.",
        wegweiser = true,
    ),
    InfoCard(
        "Beratung und Hilfe",
        "Pflegekassen müssen kostenlos zur Pflege beraten. Unabhängig beraten auch Pflegestützpunkte und kommunale Beratungsstellen. " +
            "Das Pflegetelefon des Bundesfamilienministeriums erreichst du unter 030 20179131 (derzeit Montag bis Donnerstag, 9 bis 16 Uhr) oder per E-Mail an info@wege-zur-pflege.de.",
        query = "Pflegeberatung Pflegestützpunkt",
        links = listOf(
            "Pflegetelefon anrufen" to "tel:03020179131",
            "Beratungsstellen (gesund.bund.de)" to "https://gesund.bund.de/wege-im-gesundheitswesen/erwachsenenleben/alter/pflege/pflegeberatung",
            "Verbraucherzentrale: Beratung für Pflegende" to "https://www.verbraucherzentrale.de/node/43190",
        ),
    ),
    InfoCard(
        "Pflege und Beruf",
        "Wer einen nahen Angehörigen pflegt, kann sich unter Voraussetzungen freistellen lassen: kurzzeitig bis zu 10 Arbeitstage (mit Pflegeunterstützungsgeld), " +
            "Pflegezeit bis zu 6 Monate und Familienpflegezeit bis zu 24 Monate bei mindestens 15 Wochenstunden. Die Familienpflegezeit gilt in Betrieben mit mehr als 25 Beschäftigten.",
        query = "Familienpflegezeit Pflegezeit",
        links = listOf(
            "Familienportal: Unterstützung für Pflegende" to "https://familienportal.de/familienportal/lebenslagen/leben-mit-behinderung/unterstuetzung-und-finanzielles/welche-unterstuetzung-gibt-es-fuer-pflegende-angehoerige--158548",
        ),
    ),
    InfoCard(
        "Entlastung für pflegende Angehörige",
        "Auch Pflegende brauchen Pausen. Dafür gibt es zum Beispiel Verhinderungspflege und Kurzzeitpflege, den Entlastungsbetrag, Tages- und Nachtpflege, Pflegekurse " +
            "und Kuren für pflegende Angehörige. Was zusteht und was beantragt werden muss, klärt die Pflegekasse oder eine Beratungsstelle.",
        query = "Entlastung pflegende Angehörige Verhinderungspflege",
    ),
    InfoCard(
        "Medikamente im Alter",
        "Wer dauerhaft mindestens drei verordnete Medikamente einnimmt, hat Anspruch auf einen Medikationsplan von Arzt oder Apotheke. " +
            "Bei neuen Medikamenten lohnt sich der Abgleich mit diesem Plan. Fragen dazu beantworten Arzt und Apotheke.",
        query = "Medikationsplan Senioren Wechselwirkungen",
    ),
)

private val NEWS_DATE = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

private fun openWeb(context: Context, url: String) {
    try {
        val intent = if (url.startsWith("tel:")) Intent(Intent.ACTION_DIAL, Uri.parse(url)) else Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

/**
 * Infos rund um Pflege: aktuelle Themen, Meldungen zu einem Suchwort, Themen-Vorschlaege aus den eigenen Daten
 * und eigene Themen. Beim Suchen wird nur das Suchwort gesendet, nie Name oder Daten der Person.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoScreen(
    person: Person?,
    meds: List<Medication>,
    documents: List<Document>,
    onOpenWegweiser: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var topics by remember { mutableStateOf(AppPrefs.infoTopics(context)) }
    var personalized by remember { mutableStateOf(AppPrefs.infoPersonalized(context)) }
    val suggestions = remember(person, meds, documents, personalized) {
        if (personalized) PersonalTopics.suggest(person, meds, documents) else emptyList()
    }

    fun search(raw: String) {
        val text = raw.trim()
        if (text.length < 2 || loading) return
        query = text
        active = text
        loading = true
        error = null
        results = emptyList()
        scope.launch {
            NewsFeed.search(text)
                .onSuccess { results = it }
                .onFailure { error = "Die Meldungen konnten nicht geladen werden. Prüfe die Internetverbindung oder öffne die Suche im Browser." }
            loading = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Meldungen suchen", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Thema, z. B. Pflegegeld, Demenz, Pflegereform") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search(query) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(enabled = query.trim().length >= 2 && !loading, onClick = { search(query) }) { Text("Suchen") }
                    Text(
                        "Beim Suchen wird nur dein Suchwort an Google News gesendet, nie ein Name oder Daten aus der App. " +
                            "Angezeigt werden Überschriften mit Quelle. Gelesen wird auf der Seite der Quelle.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (active != null) {
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Meldungen zu „$active“", style = MaterialTheme.typography.titleMedium)
                        if (loading) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Suche …", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                        if (!loading && error == null && results.isEmpty()) {
                            Text("Dazu wurde nichts Aktuelles gefunden. Versuche ein anderes Wort.", style = MaterialTheme.typography.bodyMedium)
                        }
                        results.forEach { n ->
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                TextButton(onClick = { openWeb(context, n.link) }, contentPadding = PaddingValues(0.dp)) {
                                    Text(n.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    listOfNotNull(n.source.takeIf { it.isNotBlank() }, n.published?.let { NEWS_DATE.format(Date(it)) }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val current = active.orEmpty()
                            val saved = topics.any { it.equals(current, ignoreCase = true) }
                            TextButton(onClick = {
                                topics = if (saved) topics.filterNot { it.equals(current, ignoreCase = true) } else topics + current
                                AppPrefs.setInfoTopics(context, topics)
                            }) { Text(if (saved) "Thema entfernen" else "Thema merken") }
                            TextButton(onClick = { openWeb(context, NewsFeed.browserUrl(current)) }) { Text("Im Browser öffnen") }
                            TextButton(onClick = {
                                active = null
                                results = emptyList()
                                error = null
                            }) { Text("Schließen") }
                        }
                    }
                }
            }
        }

        if (topics.isNotEmpty()) {
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Meine Themen", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            topics.forEach { t -> FilterChip(selected = active == t, onClick = { search(t) }, label = { Text(t) }) }
                        }
                        Text("Antippen sucht aktuelle Meldungen. Entfernen: Thema suchen und „Thema entfernen“ antippen.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Themen für dich", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(checked = personalized, onCheckedChange = {
                            personalized = it
                            AppPrefs.setInfoPersonalized(context, it)
                        })
                        Text("Vorschläge aus meinen Angaben", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                    if (personalized) {
                        if (suggestions.isEmpty()) {
                            Text("Noch keine Vorschläge. Sie entstehen aus Pflegegrad, Medikamenten, Dokumenten und „Wichtig zu wissen“.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                suggestions.forEach { s ->
                                    FilterChip(selected = active == s.query, onClick = { search(s.query) }, label = { Text(s.label) })
                                }
                            }
                        }
                        Text(
                            "Erkannt nur auf diesem Handy aus deinen Angaben. Das ist keine Diagnose. Erst beim Antippen wird das Suchwort gesendet.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            Text("Aktuelle Themen (Stand $INFO_STAND)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
            Text(
                "Allgemeine Informationen, ohne Gewähr. Gesetze ändern sich. Maßgeblich sind Gesetzestext, Pflegekasse und Beratungsstellen.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        INFO_CARDS.forEach { card ->
            item {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(card.title, style = MaterialTheme.typography.titleMedium)
                        Text(card.text, style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (card.wegweiser) OutlinedButton(onClick = onOpenWegweiser) { Text("Wegweiser öffnen") }
                            card.query?.let { q -> OutlinedButton(onClick = { search(q) }) { Text("Aktuelle Meldungen") } }
                        }
                        card.links.forEach { (label, url) ->
                            TextButton(onClick = { openWeb(context, url) }, contentPadding = PaddingValues(0.dp)) { Text(label) }
                        }
                    }
                }
            }
        }
    }
}
