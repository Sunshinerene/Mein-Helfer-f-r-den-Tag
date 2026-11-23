package de.meinhelfer.tag.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.ai.AiClient
import de.meinhelfer.tag.ai.AiConfig
import de.meinhelfer.tag.ai.AiMode
import de.meinhelfer.tag.ai.AiTask
import de.meinhelfer.tag.summary.HelperPrompt
import de.meinhelfer.tag.summary.HelperTool
import de.meinhelfer.tag.summary.Household
import de.meinhelfer.tag.summary.Nutrition
import de.meinhelfer.tag.summary.Presence
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Ablage der Alltagshelfer: eigene Werkzeuge, angeheftete, Einkaufsliste, letzte Ergebnisse. Nur auf dem Handy. */
object HelperStore {
    private fun prefs(context: Context) = context.getSharedPreferences("alltagshelfer", Context.MODE_PRIVATE)

    fun toJson(t: HelperTool): JSONObject = JSONObject()
        .put("id", t.id).put("titel", t.title).put("emoji", t.emoji).put("beschreibung", t.description)
        .put("anweisung", t.instruction).put("ausgabe", t.output)
        .put("daten", JSONArray(t.context.toList()))
        .put("felder", JSONArray().apply {
            t.fields.forEach { f ->
                put(JSONObject().put("key", f.key).put("name", f.label).put("art", f.type).put("optionen", JSONArray(f.options)).put("hinweis", f.hint))
            }
        })

    fun fromJson(o: JSONObject, id: String? = null): HelperTool? = runCatching {
        val fields = o.optJSONArray("felder")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val f = arr.optJSONObject(i) ?: return@mapNotNull null
                val type = f.optString("art", "text").let { if (it in setOf("text", "auswahl", "mehrfach", "foto")) it else "text" }
                val opts = f.optJSONArray("optionen")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.take(8) }.orEmpty()
                HelperTool.Field(
                    f.optString("key").ifBlank { "f$i" }, f.optString("name").ifBlank { "Angabe" }.take(60),
                    if (type in setOf("auswahl", "mehrfach") && opts.isEmpty()) "text" else type, opts, f.optString("hinweis").take(80),
                )
            }.take(6)
        }.orEmpty()
        val ctx = o.optJSONArray("daten")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it in HelperTool.CONTEXTS }.toSet() }.orEmpty()
        HelperTool(
            id ?: o.getString("id"),
            o.optString("titel").ifBlank { "Werkzeug" }.take(40),
            o.optString("emoji").ifBlank { "🧩" }.take(4),
            o.optString("beschreibung").take(200),
            fields.ifEmpty { listOf(HelperTool.Field("frage", "Worum geht es genau?")) },
            o.optString("anweisung").ifBlank { "Gib kurze, alltagstaugliche Vorschläge." }.take(800),
            o.optString("ausgabe", "liste").let { if (it in setOf("liste", "checkliste", "text")) it else "liste" },
            ctx,
        )
    }.getOrNull()

    fun custom(context: Context): List<HelperTool> = runCatching {
        val arr = JSONArray(prefs(context).getString("werkzeuge", "[]"))
        (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun all(context: Context) = HelperTool.BUILTIN + custom(context)

    fun saveCustom(context: Context, list: List<HelperTool>) {
        prefs(context).edit().putString("werkzeuge", JSONArray().apply { list.forEach { put(toJson(it)) } }.toString()).apply()
    }

    fun pinned(context: Context): Set<String> = prefs(context).getStringSet("angeheftet", setOf("kochen", "einkauf"))!!.toSet()

    fun setPinned(context: Context, ids: Set<String>) = prefs(context).edit().putStringSet("angeheftet", ids).apply()

    fun lastResult(context: Context, id: String): String = prefs(context).getString("ergebnis_$id", "")!!

    fun setLastResult(context: Context, id: String, answer: String) = prefs(context).edit().putString("ergebnis_$id", answer).apply()

    /** Listen: allgemeine Einkäufe und getrennt Apotheke/Sanitätshaus. */
    const val GENERAL = "einkauf"
    const val PHARMACY = "einkauf_apotheke"

    /** Einkaufsliste: Einträge mit Haken. */
    fun shopping(context: Context, list: String = GENERAL): List<Pair<String, Boolean>> = runCatching {
        val arr = JSONArray(prefs(context).getString(list, "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("t") to o.optBoolean("ok") } }
    }.getOrDefault(emptyList())

    fun setShopping(context: Context, list: List<Pair<String, Boolean>>, name: String = GENERAL) {
        prefs(context).edit().putString(name, JSONArray().apply { list.forEach { (t, ok) -> put(JSONObject().put("t", t).put("ok", ok)) } }.toString()).apply()
    }

    /** Neue Einträge anhängen (Doppeltes überspringen). Gibt die Zahl der neuen zurück. */
    fun addShopping(context: Context, items: List<String>, name: String = GENERAL): Int {
        val existing = shopping(context, name)
        val fresh = items.map { it.trim() }.filter { it.isNotBlank() }.distinct().filter { n -> existing.none { it.first.equals(n, true) } }
        setShopping(context, existing + fresh.map { it to false }, name)
        return fresh.size
    }

    /** Grundvorrat: was immer da sein soll, mit Ort (Kühlschrank, Vorratsschrank, Tiefkühler, Bad / Drogerie). */
    val PLACES = listOf("Kühlschrank", "Vorratsschrank", "Tiefkühler", "Bad / Drogerie")

    fun baseStock(context: Context): List<Pair<String, String>> = runCatching {
        val arr = JSONArray(prefs(context).getString("grundvorrat", "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("t") to o.optString("ort", PLACES[0]) } }
    }.getOrDefault(emptyList())

    fun setBaseStock(context: Context, list: List<Pair<String, String>>) {
        prefs(context).edit().putString("grundvorrat", JSONArray().apply { list.forEach { (t, o) -> put(JSONObject().put("t", t).put("ort", o)) } }.toString()).apply()
    }

    /** Gemerkte Rezepte und Vorschläge (Titel, Text). */
    fun recipes(context: Context): List<Pair<String, String>> = runCatching {
        val arr = JSONArray(prefs(context).getString("rezepte", "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("t") to o.optString("x") } }
    }.getOrDefault(emptyList())

    fun setRecipes(context: Context, list: List<Pair<String, String>>) {
        prefs(context).edit().putString("rezepte", JSONArray().apply { list.forEach { (t, x) -> put(JSONObject().put("t", t).put("x", x)) } }.toString()).apply()
    }

    /** Werkzeug zum Teilen als Text (JSON) und wieder einlesen. */
    fun exportTool(t: HelperTool): String = "MeinHelfer-Werkzeug " + toJson(t).toString()

    fun importTool(context: Context, text: String): HelperTool? {
        val json = text.substringAfter("MeinHelfer-Werkzeug", text).trim()
        val start = json.indexOf('{')
        val end = json.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val tool = runCatching { fromJson(JSONObject(json.substring(start, end + 1)), "eigen_" + System.currentTimeMillis()) }.getOrNull() ?: return null
        saveCustom(context, custom(context) + tool)
        return tool
    }

    /** Neues Werkzeug zu einem Thema: mit KI nach dem gemeinsamen Grundgerüst, sonst das einfache Gerüst. */
    suspend fun createFromTopic(context: Context, topic: String): HelperTool {
        val id = "eigen_" + System.currentTimeMillis()
        val system = "Du baust für die App „Mein Helfer für den Tag“ (pflegende Angehörige) ein kleines Alltagswerkzeug nach einem festen Grundgerüst. " +
            "Antworte nur als JSON: {\"titel\": \"kurz\", \"emoji\": \"ein Emoji\", \"beschreibung\": \"ein Satz\", " +
            "\"felder\": [{\"key\": \"kurz_ohne_leerzeichen\", \"name\": \"Beschriftung\", \"art\": \"text|auswahl|mehrfach|foto\", \"optionen\": [\"...\"], \"hinweis\": \"Beispiel\"}], " +
            "\"anweisung\": \"Was die KI mit den Angaben tun soll, konkret und kurz\", \"ausgabe\": \"liste|checkliste|text\", " +
            "\"daten\": [\"anwesend\", \"ernaehrung\", \"haushalt\", \"vorrat\", \"termine\"]}. " +
            "Höchstens 4 Felder. Bei „daten“ nur, was das Werkzeug wirklich braucht. Nichts Medizinisches, keine Diagnosen. Einfach und nützlich."
        val answer = AiClient.ask(context, system, "Thema: " + topic.trim().take(200), 900, AiTask.Helfer, cache = false).getOrNull()
        val json = answer?.let { a -> a.substring(a.indexOf('{').coerceAtLeast(0), (a.lastIndexOf('}') + 1).coerceAtLeast(1)) }
        val tool = json?.let { runCatching { fromJson(JSONObject(it), id) }.getOrNull() } ?: HelperTool.basic(id, topic)
        saveCustom(context, custom(context) + tool)
        return tool
    }
}

/** Angeheftete Helfer als kleine Knöpfe in Sunnys Karte; „🧰 Alle“ öffnet die Übersicht. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HelperChips(vm: MainViewModel) {
    val context = LocalContext.current
    if (!de.meinhelfer.tag.update.AppPrefs.helpersBeta(context)) return
    var open by remember { mutableStateOf<String?>(null) }
    var hub by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    val tools = remember(tick, hub) { HelperStore.all(context) }
    val pinned = remember(tick, hub) { HelperStore.pinned(context) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tools.filter { it.id in pinned }.forEach { t -> AssistChip(onClick = { open = t.id }, label = { Text(t.emoji + " " + t.title) }) }
        AssistChip(onClick = { hub = true }, label = { Text("🧰 Alltagshelfer (Beta)") })
    }
    if (hub) HelperHub(vm, onClose = { hub = false; tick++ })
    open?.let { id -> tools.firstOrNull { it.id == id }?.let { t -> ToolDialog(t, vm) { open = null } } }
}

/** Übersicht: alle Helfer, anheften, eigenes Werkzeug von Sunny erstellen lassen, Einkaufsliste. */
@Composable
fun HelperHub(vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    val tools = remember(tick) { HelperStore.all(context) }
    var pinned by remember { mutableStateOf(HelperStore.pinned(context)) }
    var open by remember { mutableStateOf<HelperTool?>(null) }
    var topic by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    var showRecipes by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importInfo by remember { mutableStateOf<String?>(null) }
    val aiReady = !AiMode.isOff(context) && AiConfig.isConfigured(context)
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        DesignSurface(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(8.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧰 Alltagshelfer (Beta)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Schließen") }
                }
                Text(
                    "Kleine Werkzeuge für den Alltag. Sie nutzen, was die App weiß (wer wann zu Hause ist, worauf beim Essen zu achten ist) – an die KI geht das ohne Namen. " +
                        "📌 heftet ein Werkzeug als Knopf in Sunnys Karte.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!aiReady) Text("Die Helfer brauchen KI (Stufe 2 oder 3, Einstellungen → Persönlicher Assistent → KI-Dienst).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                tools.forEach { t ->
                    HCard(Modifier.fillMaxWidth().clickable { open = t }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.emoji + " " + t.title, style = MaterialTheme.typography.titleMedium)
                                Text(t.description, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = {
                                pinned = if (t.id in pinned) pinned - t.id else pinned + t.id
                                HelperStore.setPinned(context, pinned)
                            }) { Text(if (t.id in pinned) "📌 an" else "📌") }
                            if (!t.builtin) {
                                TextButton(onClick = { shareHelperText(context, HelperStore.exportTool(t)) }) { Text("Teilen") }
                                TextButton(onClick = {
                                    HelperStore.saveCustom(context, HelperStore.custom(context).filter { it.id != t.id })
                                    tick++
                                }) { Text("✕") }
                            }
                        }
                    }
                }
                OutlinedButton(shape = buttonShape(), onClick = { showList = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "🛒 Listen: Einkauf ${HelperStore.shopping(context).count { !it.second }} · Apotheke ${HelperStore.shopping(context, HelperStore.PHARMACY).count { !it.second }} offen",
                    )
                }
                OutlinedButton(shape = buttonShape(), onClick = { showRecipes = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("⭐ Gemerkte Rezepte und Pläne (${HelperStore.recipes(context).size})")
                }
                HorizontalDivider()
                Text("Neues Werkzeug von Sunny", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Nenne ein Thema, z. B. „Wäscheplan“, „Ideen für einen Ausflug mit Rollstuhl“, „Geschenkideen“, „Beschäftigung bei Demenz“. " +
                        "Sunny baut daraus ein kleines Werkzeug nach demselben Grundgerüst wie die anderen (ein paar Felder, eine Anfrage, eine Liste als Ergebnis).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = topic, onValueChange = { topic = it.take(120) }, label = { Text("Thema") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(shape = buttonShape(), enabled = topic.isNotBlank() && !creating, onClick = {
                        creating = true
                        scope.launch {
                            val t = if (aiReady) HelperStore.createFromTopic(context, topic) else HelperTool.basic("eigen_" + System.currentTimeMillis(), topic).also {
                                HelperStore.saveCustom(context, HelperStore.custom(context) + it)
                            }
                            creating = false
                            topic = ""
                            tick++
                            open = t
                        }
                    }) { Text(if (creating) "Sunny baut …" else "Werkzeug erstellen") }
                }
                Text("Werkzeug von jemand anderem? Den geteilten Text hier einfügen:", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = importText, onValueChange = { importText = it.take(6000) }, label = { Text("Geteiltes Werkzeug") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(enabled = importText.isNotBlank(), onClick = {
                        val t = HelperStore.importTool(context, importText)
                        importInfo = if (t != null) "„${t.title}“ übernommen." else "Das ist kein Werkzeug-Text."
                        if (t != null) {
                            importText = ""
                            tick++
                        }
                    }) { Text("Übernehmen") }
                }
                importInfo?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    open?.let { t -> ToolDialog(t, vm) { open = null } }
    if (showList) ShoppingListDialog { showList = false }
    if (showRecipes) RecipesDialog { showRecipes = false }
}

/** Ein Werkzeug ausführen: Felder ausfüllen, Zeitpunkt, Foto, KI fragen, Ergebnis anzeigen und vorlesen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolDialog(tool: HelperTool, vm: MainViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val appointments by vm.allAppointments.collectAsStateWithLifecycle()
    val meds by vm.allMedications.collectAsStateWithLifecycle()
    val allVitals by vm.allVitals.collectAsStateWithLifecycle()
    val values = remember { mutableStateMapOf<String, String>() }
    var image by remember { mutableStateOf<String?>(null) }
    var pendingPath by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var raw by remember { mutableStateOf(HelperStore.lastResult(context, tool.id)) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var added by remember { mutableStateOf(false) }
    // Zeitpunkt: nach Mahlzeit oder jetzt
    val now = LocalDateTime.now()
    var slotChosen by remember { mutableStateOf<String?>(null) }
    val slots = linkedMapOf("jetzt" to now, "Mittag" to now.toLocalDate().atTime(12, 30), "Abend" to now.toLocalDate().atTime(18, 30), "Morgen früh" to now.toLocalDate().plusDays(1).atTime(7, 30))
    // Ohne eigene Wahl: Zeitpunkt nach der Mahlzeit
    val slot = slotChosen ?: when (values["mahlzeit"]) {
        "Mittagessen" -> if (now.hour < 14) "Mittag" else "jetzt"
        "Abendessen" -> if (now.hour < 20) "Abend" else "jetzt"
        "Frühstück" -> if (now.hour < 10) "jetzt" else "Morgen früh"
        else -> "jetzt"
    }
    val at = slots[slot] ?: now
    val zone = ZoneId.systemDefault()
    val people = persons.map { p ->
        Presence.Who(
            p.id, p.name, Household.decode(p.household), Nutrition.decode(p.nutrition),
            p.birthDate?.let { java.time.Period.between(LocalDate.ofEpochDay(it), LocalDate.now()).years },
        )
    }
    val apptMap = appointments.groupBy { it.personId }.mapValues { (_, l) -> l.map { java.time.Instant.ofEpochMilli(it.whenMillis).atZone(zone).toLocalDateTime() } }
    val home = if ("anwesend" in tool.context) Presence.homeAt(people, at, apptMap) else null
    val legend = people.withIndex().joinToString(" · ") { (i, p) -> Presence.label(i) + " = " + p.name }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPath?.let { java.io.File(it) }
        pendingPath = null
        if (file != null && ok) image = runCatching { photoBase64(file) }.getOrNull()
        file?.delete()
    }

    fun prompt(): String {
        val extra = mutableMapOf<String, String>()
        if ("vorrat" in tool.context) {
            extra["Medikamente bald knapp (gehören auf die Apotheken-Liste, art apotheke)"] = lowStockMeds(meds).joinToString(", ") { it.name }.ifBlank { "nichts" }
        }
        if ("medikamente" in tool.context) {
            val ids = persons.map { it.id }
            extra["Medikamente"] = meds.filter { it.personId in ids }.groupBy { it.personId }.entries.joinToString("; ") { (pid, l) ->
                Presence.label(ids.indexOf(pid)) + ": " + l.joinToString(", ") { m -> m.name + (m.doseSummary()?.let { " ($it)" } ?: "") }
            }.ifBlank { "keine eingetragen" }
        }
        if ("rueckblick" in tool.context) {
            val ids = persons.map { it.id }
            val since = LocalDate.now().minusDays(7).toEpochDay()
            extra["Messwerte (7 Tage)"] = allVitals.filter { it.day > since && it.personId in ids }.groupBy { it.personId }.entries.joinToString("; ") { (pid, l) ->
                Presence.label(ids.indexOf(pid)) + ": " + l.groupBy { it.kind }.entries.joinToString(", ") { (k, v) ->
                    val vk = de.meinhelfer.tag.summary.VitalKind.of(k)
                    (vk?.label ?: k) + " " + v.size + "× (" + de.meinhelfer.tag.summary.Vitals.number(v.minOf { it.value }, vk?.decimals ?: 0) + "–" +
                        de.meinhelfer.tag.summary.Vitals.number(v.maxOf { it.value }, vk?.decimals ?: 0) + " " + (vk?.unit ?: "") + ")"
                }
            }.ifBlank { "keine" }
            extra["Termine (7 Tage)"] = appointments.filter { it.whenMillis in System.currentTimeMillis() - 7 * 86_400_000L..System.currentTimeMillis() && it.personId in ids }
                .joinToString("; ") { Presence.label(ids.indexOf(it.personId)) + ": " + it.title }.ifBlank { "keine" }
            extra["Medikamente knapp"] = lowStockMeds(meds).joinToString(", ") { it.name }.ifBlank { "nichts" }
        }
        if ("grundvorrat" in tool.context) {
            val base = HelperStore.baseStock(context)
            val area = values["bereich"].orEmpty()
            val relevant = if (area.isNotBlank()) base.filter { it.second == area } else base
            extra["Grundvorrat (soll immer da sein" + (if (area.isNotBlank()) ", Bereich $area" else "") + ")"] =
                relevant.groupBy { it.second }.entries.joinToString("; ") { (place, l) -> place + ": " + l.joinToString(", ") { it.first } }.ifBlank { "nicht festgelegt" }
        }
        if ("woche" in tool.context) {
            extra["Wochenübersicht (wer zu Hause ist)"] = (0..6).joinToString("; ") { d ->
                val day = now.toLocalDate().plusDays(d.toLong())
                val name = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")[day.dayOfWeek.value - 1]
                fun who(t: LocalDateTime) = Presence.homeAt(people, t, apptMap).joinToString(", ") { p -> Presence.label(people.indexOf(p)) }.ifBlank { "niemand" }
                "$name mittags ${who(day.atTime(12, 30))}, abends ${who(day.atTime(18, 30))}"
            }
        }
        if ("termine" in tool.context) {
            extra["Termine der nächsten Tage"] = appointments.filter { it.whenMillis in System.currentTimeMillis()..System.currentTimeMillis() + 5 * 86_400_000L }
                .sortedBy { it.whenMillis }.take(6).joinToString("; ") { a ->
                    val t = java.time.Instant.ofEpochMilli(a.whenMillis).atZone(zone)
                    "%02d.%02d. %02d:%02d %s".format(t.dayOfMonth, t.monthValue, t.hour, t.minute, a.title)
                }
        }
        val v = values.toMap() + (if (image != null) mapOf("foto" to "1") else emptyMap())
        val whenText = if ("anwesend" in tool.context) "%s, %02d:%02d Uhr".format(if (at.toLocalDate() == now.toLocalDate()) "heute" else "morgen", at.hour, at.minute) else ""
        return HelperPrompt.build(tool, v, people, home, whenText, extra)
    }

    fun send(text: String) {
        loading = true
        error = null
        added = false
        scope.launch {
            AiClient.ask(context, HelperTool.SYSTEM, text, 1800, AiTask.Helfer, image = image)
                .onSuccess {
                    raw = it
                    HelperStore.setLastResult(context, tool.id, it)
                }
                .onFailure { error = it.message ?: "Das hat nicht geklappt." }
            loading = false
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        DesignSurface(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(8.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tool.emoji + " " + tool.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Speech.stop(); onClose() }) { Text("Schließen") }
                }
                Text(tool.description, style = MaterialTheme.typography.bodySmall)
                tool.fields.forEach { f ->
                    when (f.type) {
                        "auswahl", "mehrfach" -> {
                            Text(f.label, style = MaterialTheme.typography.bodyMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val chosen = values[f.key].orEmpty().split(", ").filter { it.isNotBlank() }.toSet()
                                f.options.forEach { o ->
                                    FilterChip(selected = o in chosen, onClick = {
                                        values[f.key] = if (f.type == "auswahl") (if (o in chosen) "" else o)
                                        else (if (o in chosen) chosen - o else chosen + o).joinToString(", ")
                                    }, label = { Text(o) })
                                }
                            }
                        }
                        "foto" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(shape = buttonShape(), onClick = {
                                val file = de.meinhelfer.tag.media.CardScanner.newFile(context)
                                pendingPath = file.path
                                runCatching { camera.launch(de.meinhelfer.tag.media.CardScanner.uriFor(context, file)) }
                            }) { Text(if (image == null) "📷 " + f.label else "📷 Neues Foto") }
                            if (image != null) TextButton(onClick = { image = null }) { Text("Foto entfernen") }
                        }
                        else -> OutlinedTextField(
                            value = values[f.key].orEmpty(),
                            onValueChange = { values[f.key] = it.take(500) },
                            label = { Text(f.label) },
                            placeholder = { if (f.hint.isNotBlank()) Text(f.hint) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if ("anwesend" in tool.context) {
                    Text("Für wann?", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        slots.keys.forEach { k -> FilterChip(selected = slot == k, onClick = { slotChosen = k }, label = { Text(k) }) }
                    }
                    Text(
                        "Dann zu Hause: " + (home?.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name } ?: "keine der betreuten Personen") +
                            (people.filter { p -> home?.none { it.id == p.id } == true }.takeIf { it.isNotEmpty() }?.let { l -> " · unterwegs: " + l.joinToString(", ") { it.name } }.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (image != null) Text("✓ Foto wird mitgeschickt (nur an Dienste, die Bilder verstehen, z. B. Gemini).", style = MaterialTheme.typography.bodySmall)
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Sunny überlegt …", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Button(shape = buttonShape(), modifier = Modifier.fillMaxWidth(), enabled = !AiMode.isOff(context), onClick = {
                        val p = prompt()
                        if (AiMode.skipConfirm(context)) send(p) else confirm = p
                    }) { Text("Sunny fragen") }
                }
                if (AiMode.isOff(context)) Text(AiMode.OFF_TEXT, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (raw.isNotBlank() && !loading) {
                    val r = remember(raw) { HelperPrompt.parse(raw) }
                    HorizontalDivider()
                    if (r.short.isNotBlank()) Text("🤖 " + r.short, style = MaterialTheme.typography.bodyLarge)
                    r.items.forEach { item ->
                        Column {
                            Text((if (item.pharmacy) "💊 " else "• ") + item.title, style = MaterialTheme.typography.titleSmall)
                            if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodyMedium)
                            if (tool.output != "checkliste") {
                                Row {
                                    TextButton(onClick = {
                                        HelperStore.setRecipes(context, HelperStore.recipes(context).filterNot { it.first == item.title } + (item.title to item.text))
                                        android.widget.Toast.makeText(context, "Gemerkt", android.widget.Toast.LENGTH_SHORT).show()
                                    }) { Text("⭐ Merken") }
                                    TextButton(onClick = {
                                        vm.addTodo(tool.emoji + " " + item.title)
                                        android.widget.Toast.makeText(context, "Als Aufgabe angelegt", android.widget.Toast.LENGTH_SHORT).show()
                                    }) { Text("＋ Aufgabe") }
                                    if (item.title.equals("Einkauf", true) && item.text.isNotBlank()) {
                                        TextButton(onClick = {
                                            val n = HelperStore.addShopping(context, item.text.split(',', ';', '\n'))
                                            android.widget.Toast.makeText(context, "$n auf die Einkaufsliste", android.widget.Toast.LENGTH_SHORT).show()
                                        }) { Text("🛒 Auf die Liste") }
                                    }
                                }
                            }
                        }
                    }
                    if (r.note.isNotBlank()) Text("ℹ " + r.note, style = MaterialTheme.typography.bodySmall)
                    if ("anwesend" in tool.context || "ernaehrung" in tool.context) Text(legend, style = MaterialTheme.typography.bodySmall)
                    HintReader("helfer-${tool.id}-${raw.hashCode()}") { r.short + " " + r.items.joinToString(". ") { it.title } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (tool.output == "checkliste") {
                            OutlinedButton(shape = buttonShape(), enabled = !added && r.items.isNotEmpty(), onClick = {
                                // Apotheke/Sanitätshaus getrennt von der allgemeinen Liste
                                HelperStore.addShopping(context, r.items.filterNot { it.pharmacy }.map { it.title })
                                HelperStore.addShopping(context, r.items.filter { it.pharmacy }.map { it.title }, HelperStore.PHARMACY)
                                added = true
                            }) { Text(if (added) "✓ Auf den Listen" else "🛒 Auf die Listen") }
                        }
                        OutlinedButton(shape = buttonShape(), onClick = { shareHelperText(context, tool.title + "\n\n" + r.short + "\n\n" + r.items.joinToString("\n") { "• " + it.title + (if (it.text.isNotBlank()) ": " + it.text else "") }) }) { Text("Teilen") }
                    }
                    Text("KI-Vorschlag, nur ein Anhaltspunkt. Bei Gesundheitsfragen zählt, was Arzt, Apotheke oder Ernährungsberatung sagen.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    confirm?.let { p ->
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { confirm = null },
            title = { Text("An ${AiConfig.provider(context).label.substringBefore(" (")} senden?") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Genau dieser Text geht an den KI-Dienst – ohne Namen (Person A, B …)." + if (image != null) " Dazu das Foto." else "", style = MaterialTheme.typography.bodySmall)
                    Text(de.meinhelfer.tag.summary.Anonymizer.scrub(p), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { confirm = null; send(p) }) { Text("Senden") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Abbrechen") } },
        )
    }
}

/** Listen: allgemeiner Einkauf, getrennt Apotheke/Sanitätshaus, und der Grundvorrat (was immer da sein soll). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShoppingListDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    val name = if (tab == 1) HelperStore.PHARMACY else HelperStore.GENERAL
    var list by remember(tab) { mutableStateOf(HelperStore.shopping(context, name)) }
    var base by remember { mutableStateOf(HelperStore.baseStock(context)) }
    var input by remember { mutableStateOf("") }
    var place by remember { mutableStateOf(HelperStore.PLACES[0]) }
    val missing = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    fun save(l: List<Pair<String, Boolean>>) {
        list = l
        HelperStore.setShopping(context, l, name)
    }
    fun saveBase(l: List<Pair<String, String>>) {
        base = l
        HelperStore.setBaseStock(context, l)
    }
    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onClose,
        title = { Text("🛒 Listen") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Einkauf", "Apotheke & Sanitätshaus", "Grundvorrat").forEachIndexed { i, l -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(l) }) }
                }
                if (tab < 2) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = input, onValueChange = { input = it.take(80) }, label = { Text("Hinzufügen") }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(enabled = input.isNotBlank(), onClick = {
                            save(list + (input.trim() to false))
                            input = ""
                        }) { Text("+") }
                    }
                    if (list.isEmpty()) {
                        Text(
                            if (tab == 0) "Noch leer. Im Helfer „Einkaufsliste“ macht Sunny Vorschläge, auch aus einem Foto im Vergleich mit dem Grundvorrat."
                            else "Medikamente, Verbandsmaterial und Pflegehilfsmittel landen hier, getrennt vom normalen Einkauf. Nachbestellungen mit Rezept laufen weiter über „Nachbestellung“ bei den Medikamenten.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    list.forEachIndexed { i, (t, ok) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = ok, onCheckedChange = { c -> save(list.mapIndexed { j, e -> if (j == i) e.first to c else e }) })
                            Text(t, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { save(list.filterIndexed { j, _ -> j != i }) }) { Text("✕") }
                        }
                    }
                } else {
                    Text(
                        "Was immer da sein soll. Beim Foto vom Kühlschrank oder Vorratsschrank vergleicht Sunny damit und setzt Fehlendes auf die Liste. Ohne KI: unten abhaken, was fehlt.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HelperStore.PLACES.forEach { p -> FilterChip(selected = place == p, onClick = { place = p }, label = { Text(p) }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = input, onValueChange = { input = it.take(60) }, label = { Text("z. B. Milch, Butter, Eier") }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(enabled = input.isNotBlank(), onClick = {
                            val add = input.split(',').map { it.trim() }.filter { it.isNotBlank() && base.none { b -> b.first.equals(it, true) } }
                            saveBase(base + add.map { it to place })
                            input = ""
                        }) { Text("+") }
                    }
                    HelperStore.PLACES.forEach { p ->
                        val items = base.filter { it.second == p }
                        if (items.isNotEmpty()) {
                            Text(p, style = MaterialTheme.typography.labelLarge)
                            items.forEach { (t, _) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = t in missing, onCheckedChange = { c -> if (c) missing += t else missing -= t })
                                    Text(t + if (t in missing) " – fehlt" else "", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { saveBase(base.filterNot { it.first == t }) }) { Text("✕") }
                                }
                            }
                        }
                    }
                    if (missing.isNotEmpty()) {
                        Button(shape = buttonShape(), onClick = {
                            val n = HelperStore.addShopping(context, missing.toList())
                            missing.clear()
                            android.widget.Toast.makeText(context, "$n auf die Einkaufsliste", android.widget.Toast.LENGTH_SHORT).show()
                        }) { Text("Fehlendes auf die Einkaufsliste") }
                    }
                }
            }
        },
        confirmButton = {
            if (tab < 2) Row {
                TextButton(enabled = list.any { it.second }, onClick = { save(list.filterNot { it.second }) }) { Text("Erledigte weg") }
                TextButton(enabled = list.isNotEmpty(), onClick = {
                    shareHelperText(context, (if (tab == 1) "Apotheke / Sanitätshaus\n" else "Einkaufsliste\n") + list.filterNot { it.second }.joinToString("\n") { "• " + it.first })
                }) { Text("Teilen") }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Schließen") } },
    )
}

/** Gemerkte Rezepte und Pläne: ansehen, teilen, löschen. */
@Composable
fun RecipesDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var list by remember { mutableStateOf(HelperStore.recipes(context)) }
    var open by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onClose,
        title = { Text("⭐ Gemerkt") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (list.isEmpty()) Text("Noch nichts gemerkt. In den Ergebnissen der Helfer „⭐ Merken“ tippen.", style = MaterialTheme.typography.bodySmall)
                list.forEach { (t, x) ->
                    Column(Modifier.fillMaxWidth().clickable { open = if (open == t) null else t }) {
                        Text((if (open == t) "▴ " else "▾ ") + t, style = MaterialTheme.typography.titleSmall)
                        if (open == t) {
                            if (x.isNotBlank()) Text(x, style = MaterialTheme.typography.bodyMedium)
                            Row {
                                TextButton(onClick = { shareHelperText(context, t + "\n\n" + x) }) { Text("Teilen") }
                                TextButton(onClick = {
                                    list = list.filterNot { it.first == t }
                                    HelperStore.setRecipes(context, list)
                                }) { Text("Löschen") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } },
    )
}

/** Foto verkleinern (max. 1024 px) und als JPEG in Base64 umwandeln. */
private fun photoBase64(file: java.io.File): String {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val bmp = android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    val scale = 1024f / maxOf(bmp.width, bmp.height)
    val scaled = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    val out = java.io.ByteArrayOutputStream()
    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
    return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
}

private fun shareHelperText(context: Context, text: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Teilen")) }
}

/** Einstellungen → Persönlicher Assistent → Beta-Funktionen: Alltagshelfer ein/aus. */
@Composable
fun SunnyBetaCard() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(de.meinhelfer.tag.update.AppPrefs.helpersBeta(context)) }
    var hub by remember { mutableStateOf(false) }
    val name = de.meinhelfer.tag.update.AppPrefs.assistantName(context)
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🧪 Beta-Funktionen von $name", style = MaterialTheme.typography.titleMedium)
            Text("In Erprobung – kann sich noch ändern. Rückmeldungen helfen sehr (Hilfe und Rückmeldung).", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🧰 Alltagshelfer", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Kochen, Essensplan, Einkaufs- und Apotheken-Liste mit Grundvorrat, Arztfragen, Wochenrückblick und eigene Werkzeuge – als Knöpfe in der Karte des Assistenten. Braucht KI (Stufe 2 oder 3).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                androidx.compose.material3.Switch(checked = on, onCheckedChange = {
                    on = it
                    de.meinhelfer.tag.update.AppPrefs.setHelpersBeta(context, it)
                })
            }
            if (on) TextButton(onClick = { hub = true }) { Text("Alltagshelfer öffnen") }
        }
    }
    if (hub) {
        val vm: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        HelperHub(vm) { hub = false }
    }
}
