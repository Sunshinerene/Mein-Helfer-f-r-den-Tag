package de.meinhelfer.tag.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.ai.AiClient
import de.meinhelfer.tag.ai.AiConfig
import de.meinhelfer.tag.ai.AiLimitException
import de.meinhelfer.tag.ai.AiProvider
import de.meinhelfer.tag.ai.MedCheck
import de.meinhelfer.tag.ai.MedCheckItem
import de.meinhelfer.tag.ai.MedRating
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.export.CarePdfs
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.info.MedicationInfo
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CHECK_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

private fun normMed(s: String): String =
    MedicationInfo.cleanName(s).ifBlank { s }.lowercase(Locale.GERMANY).replace(Regex("\\s+"), " ").trim()

/** Ordnet einen Eintrag aus dem Gesamt-Check dem Medikament zu (Name, sonst Reihenfolge). */
private fun itemFor(med: Medication, index: Int, items: List<MedCheckItem>): MedCheckItem? {
    val n = normMed(med.name)
    return items.firstOrNull { val i = normMed(it.name); i.isNotEmpty() && (i == n || i in n || n in i) }
        ?: items.getOrNull(index)?.takeIf { normMed(it.name).isEmpty() }
}

private fun comboLevelText(level: Int): String = when (level) {
    3 -> "Wichtig"
    2 -> "Beachten"
    else -> "Gering"
}

/**
 * Alle Medikamente der Person mit ihrer Einschaetzung auf einen Blick, dazu ein freiwilliger KI-Gesamt-Check:
 * wie die Medikamente zusammenpassen, Einnahmezeiten, Gesamtbild und Fragen an Arzt oder Apotheke.
 * Nur ein Anhaltspunkt, um den Plan pruefen zu lassen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedOverviewScreen(
    meds: List<Medication>,
    doctors: List<Doctor>,
    person: Person?,
    vm: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val personId = person?.id ?: 0L
    var stored by remember(personId) { mutableStateOf(AppPrefs.medCheck(context, personId)) }
    val check = remember(stored) { MedCheck.fromStored(stored) }
    val planKey = MedCheck.planKey(meds)
    val changed = check != null && check.planKey != planKey
    val age = person?.birthDate?.let { Period.between(LocalDate.ofEpochDay(it), LocalDate.now()).years }?.takeIf { it in 0..120 }

    var confirm by remember { mutableStateOf(false) }
    var withAge by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var limitHit by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(AiConfig.provider(context)) }
    val added = remember { mutableStateListOf<String>() }
    val configured = !de.meinhelfer.tag.ai.AiMode.isOff(context) && AiConfig.isConfigured(context)
    val otherReady = !de.meinhelfer.tag.ai.AiMode.isOff(context) && AiProvider.entries.any { it != provider && AiConfig.isReady(context, it) }
    val perDay = meds.sumOf { it.timeList().distinct().size }
    val duties by vm.duties.collectAsStateWithLifecycle()
    val hausarzt = de.meinhelfer.tag.data.Duties.resolve(duties, null, personId, de.meinhelfer.tag.data.Duties.REZEPT).id ?: matchDoctor("hausarzt", doctors)
    val apotheke = de.meinhelfer.tag.data.Duties.resolve(duties, null, personId, de.meinhelfer.tag.data.Duties.APOTHEKE).id ?: matchDoctor("apotheke", doctors)

    fun addTask(text: String, doctorId: Long?) {
        if (text in added) return
        vm.addTasks(listOf(TaskDraft(text, doctorId, 1, LocalDate.now().toEpochDay() + 14, doctorId != null)))
        added += text
        Toast.makeText(context, "Als Aufgabe gemerkt", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medikamente im Überblick") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
            )
        },
    ) { inner ->
        val overviewList = androidx.compose.foundation.lazy.rememberLazyListState()
        val overviewJumps = rememberJumpList(overviewList)
        LazyColumn(
            state = overviewList,
            modifier = Modifier.padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            overviewJumps.reset()
            // ---- Gesamtbild
            jumpItem(overviewJumps, "Gesamtbild") {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Gesamtbild" + (person?.name?.let { " – $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${meds.size} Medikament" + (if (meds.size == 1) "" else "e") + ", $perDay Einnahme" + (if (perDay == 1) "" else "n") + " pro Tag",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (check != null && check.overall > 0) {
                            ScaleRow("Gesamt (wie komplex bzw. belastend)", check.overall)
                        }
                        if (check != null && check.summary.isNotBlank()) {
                            Text(check.summary, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (check != null) {
                            Text(
                                "KI-Check vom ${LocalDate.ofEpochDay(check.day).format(CHECK_DATE)}" +
                                    (if (check.provider.isNotBlank()) ", ${check.provider}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (changed) {
                            Text(
                                "Der Medikamentenplan hat sich seit dem Check geändert. Bitte neu prüfen lassen.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        // Ansprueche (Stand Nov. 2026): Medikationsplan ab 3, Medikationsberatung in der Apotheke ab 5
                        if (meds.size >= 3) {
                            Text(
                                "Tipp: Wer mindestens 3 verordnete Medikamente dauerhaft (28 Tage oder länger) nimmt, hat Anspruch auf einen Medikationsplan vom Hausarzt.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (meds.size >= 5) {
                            Text(
                                "Bei 5 oder mehr dauerhaft verordneten Medikamenten gibt es in der Apotheke die kostenlose „Erweiterte Medikationsberatung bei Polymedikation“ (gesetzlich Versicherte, alle 12 Monate, bei größeren Umstellungen früher).",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(shape = buttonShape(), onClick = {
                                addTask("Medikamentenplan beim Hausarzt prüfen lassen (Wechselwirkungen, Zeiten, noch alles nötig?)", hausarzt)
                            }) { Text("Aufgabe: beim Hausarzt prüfen") }
                            if (meds.size >= 5) {
                                OutlinedButton(shape = buttonShape(), onClick = {
                                    addTask("Erweiterte Medikationsberatung in der Apotheke anfragen (alle Medikamente und Packungen mitnehmen)", apotheke)
                                }) { Text("Aufgabe: Apotheke") }
                            }
                        }
                    }
                }
            }

            // ---- Plan zum Ausdrucken
            jumpItem(overviewJumps, "Medikamentenplan als PDF") {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Medikamentenplan als PDF", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tabelle im Querformat mit morgens, mittags, abends und zur Nacht – angelehnt an den Medikationsplan vom Arzt. Gut für Kühlschrank, Pflegedienst oder Vertretung.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        PdfButtons(shareLabel = "Als PDF teilen", chooserTitle = "Medikamentenplan teilen") { ctx ->
                            CarePdfs.medPlan(ctx, person, meds, doctors, AppPrefs.caregiver(ctx), duties)
                        }
                        Text(
                            "Kein offizieller bundeseinheitlicher Medikationsplan. Maßgeblich sind die Angaben von Arztpraxis und Apotheke.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // ---- KI-Gesamt-Check
            jumpItem(overviewJumps, "KI-Gesamt-Check") {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Wie passen die Medikamente zusammen?", style = MaterialTheme.typography.titleMedium)
                        if (check == null) {
                            Text(
                                "Mit dem KI-Gesamt-Check bekommst du eine grobe Einschätzung aller Medikamente zusammen: mögliche Wechselwirkungen, Hinweise zu Einnahmezeiten und Fragen für Arzt oder Apotheke.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else if (check.combos.isEmpty()) {
                            Text("Die KI hat keine besonderen Wechselwirkungen genannt.", style = MaterialTheme.typography.bodyMedium)
                        }
                        check?.combos?.forEach { c ->
                            val color = when (c.level) {
                                3 -> MaterialTheme.colorScheme.error
                                2 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    comboLevelText(c.level),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when (c.level) {
                                        3 -> MaterialTheme.colorScheme.onError
                                        2 -> MaterialTheme.colorScheme.onTertiary
                                        else -> MaterialTheme.colorScheme.onPrimary
                                    },
                                    modifier = Modifier.background(color, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("${c.a} + ${c.b}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(c.note, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (check != null && check.timing.isNotEmpty()) {
                            Text("Einnahmezeiten", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                            check.timing.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        }
                        if (check != null && check.questions.isNotEmpty()) {
                            Text("Fragen an Arzt oder Apotheke", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                            check.questions.forEach { q ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("• $q", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    TextButton(
                                        enabled = ("Arzt/Apotheke fragen: $q") !in added,
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        onClick = { addTask("Arzt/Apotheke fragen: $q", hausarzt) },
                                    ) { Text(if (("Arzt/Apotheke fragen: $q") in added) "Gemerkt" else "Merken") }
                                }
                            }
                        }

                        if (meds.size < 1) {
                            Text("Noch keine Medikamente angelegt.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            if (!configured && !otherReady) {
                                Text(
                                    if (de.meinhelfer.tag.ai.AiMode.isOff(context)) de.meinhelfer.tag.ai.AiMode.OFF_TEXT else "KI nicht eingerichtet. Du kannst sie unter Einstellungen → KI und Internet einrichten (z. B. kostenlos mit Google Gemini).",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if ((configured || otherReady) && !loading) {
                                AiProviderSwitch(selected = provider) {
                                    provider = it
                                    AiConfig.setProvider(context, it)
                                    error = null
                                    limitHit = false
                                }
                            }
                            if (loading) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("${provider.label} prüft den Plan …", style = MaterialTheme.typography.bodyMedium)
                                }
                            } else if (configured) {
                                Button(shape = buttonShape(), onClick = { confirm = true }) {
                                    Text(if (check == null) "Gesamt-Check per KI" else "Gesamt-Check erneuern")
                                }
                            }
                            if (!loading) AiShareButton(MedCheck.SYSTEM_PROMPT) { MedCheck.prompt(meds, if (withAge) age else null) }
                            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            if (limitHit) {
                                Text(
                                    if (otherReady) "Wähle oben einen anderen KI-Dienst und sende erneut."
                                    else "Du kannst unter Einstellungen → KI und Internet einen weiteren Dienst einrichten und dann hier umschalten.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            // ---- Einzelne Medikamente
            jumpItem(overviewJumps, "Die Medikamente einzeln") {
                Text("Die Medikamente einzeln", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
            meds.forEachIndexed { index, m ->
                jumpItem(overviewJumps, m.name, key = m.id) {
                    val r = MedRating.fromStored(m.rating)
                    val fromCheck = check?.let { itemFor(m, index, it.items) }
                    HCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(m.name + if (m.dosage.isNotBlank()) " – ${m.dosage}" else "", style = MaterialTheme.typography.titleSmall)
                            m.timeList().takeIf { it.isNotEmpty() }?.let {
                                Text("Einnahme: ${it.joinToString(", ")} Uhr", style = MaterialTheme.typography.bodySmall)
                            }
                            val strength = r?.strength ?: fromCheck?.strength ?: 0
                            val side = r?.sideEffects ?: fromCheck?.sideEffects ?: 0
                            val inter = r?.interactions ?: fromCheck?.interactions ?: 0
                            if (r == null && fromCheck == null) {
                                Text("Noch nicht eingeschätzt. Der Gesamt-Check oben schätzt alle auf einmal ein.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                ScaleRow("Wirkstärke", strength)
                                ScaleRow("Nebenwirkungen", side)
                                ScaleRow("Wechselwirkungen mit den übrigen", inter, emptyText = "keine genannt")
                                (r?.notes ?: fromCheck?.notes).orEmpty().forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }

            // ---- Teilen und Hinweis
            jumpItem(overviewJumps, "Teilen") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = buttonShape(), onClick = { shareText(context, overviewText(meds, check, person)) }) { Text("Teilen") }
                    OutlinedButton(shape = buttonShape(), onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Medikamente", overviewText(meds, check, person)))
                        Toast.makeText(context, "Text kopiert", Toast.LENGTH_SHORT).show()
                    }) { Text("Kopieren") }
                }
            }
            jumpItem(overviewJumps, null) {
                Text(
                    "Nur ein Anhaltspunkt aus einer KI, kein ärztlicher Rat. Dosis, Vorerkrankungen, Nierenwerte und das genaue Präparat sind nicht berücksichtigt. " +
                        "Nichts eigenmächtig absetzen oder ändern. Lass den Plan von Hausarzt oder Apotheke prüfen. Bei akuten Beschwerden 116 117, im Notfall 112.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (confirm) {
        val promptText = MedCheck.prompt(meds, if (withAge) age else null)
        val send0: () -> Unit = {
                    confirm = false
                    loading = true
                    error = null
                    limitHit = false
                    val used = provider
                    scope.launch {
                        AiClient.ask(context, MedCheck.SYSTEM_PROMPT, promptText, 3000)
                            .onSuccess { answer ->
                                val today = LocalDate.now().toEpochDay()
                                val label = used.label.substringBefore(" (")
                                val parsed = MedCheck.parse(answer, today, label, planKey)
                                if (parsed == null) {
                                    error = "Die Antwort der KI konnte nicht ausgewertet werden. Versuche es nochmal oder wähle ein anderes Modell."
                                } else {
                                    val json = parsed.toJson()
                                    AppPrefs.setMedCheck(context, personId, json)
                                    stored = json
                                    // Einschaetzung auch bei den einzelnen Medikamenten ablegen
                                    meds.forEachIndexed { i, m ->
                                        val item = itemFor(m, i, parsed.items) ?: return@forEachIndexed
                                        if (item.strength > 0 || item.sideEffects > 0) {
                                            val rating = MedRating(item.strength, item.sideEffects, item.interactions, item.notes, today, label)
                                            vm.update(m.copy(rating = rating.toJson()))
                                        }
                                    }
                                }
                            }
                            .onFailure {
                                error = it.message ?: "Das hat nicht geklappt."
                                limitHit = it is AiLimitException
                            }
                        loading = false
                    }
                }
        if (de.meinhelfer.tag.ai.AiMode.skipConfirm(context)) {
            androidx.compose.runtime.LaunchedEffect(Unit) { send0() }
        } else AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { confirm = false },
            title = { Text("An ${provider.label} senden?") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Genau dieser Text wird über das Internet an den Dienst gesendet. Der Name der Person ist nicht enthalten, aber die Medikamente mit Dosierung und Zeiten.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (age != null) {
                        Row(Modifier.fillMaxWidth().clickable { withAge = !withAge }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = withAge, onCheckedChange = { withAge = it })
                            Text("Alter mitsenden (etwa $age Jahre, macht die Einschätzung genauer)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(de.meinhelfer.tag.summary.Anonymizer.scrub(promptText), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = send0) { Text("Senden") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
        )
    }
}

private fun shareText(context: Context, text: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Medikamenten-Überblick teilen"))
    } catch (_: Exception) {
    }
}

/** Text fuer Arzt oder Apotheke: Plan, Gesamtbild, Kombinationen, Fragen. */
private fun overviewText(meds: List<Medication>, check: MedCheck?, person: Person?): String = buildString {
    appendLine("Medikamente" + (person?.name?.let { " – $it" } ?: "") + " (Stand ${LocalDate.now().format(CHECK_DATE)})")
    meds.forEach { m ->
        append("- ${m.name}")
        if (m.dosage.isNotBlank()) append(", ${m.dosage}")
        if (m.timeList().isNotEmpty()) append(", ${m.timeList().joinToString(", ")} Uhr")
        appendLine()
    }
    if (check != null) {
        appendLine()
        appendLine("KI-Einschätzung vom ${LocalDate.ofEpochDay(check.day).format(CHECK_DATE)} (nur Anhaltspunkt, bitte prüfen):")
        if (check.summary.isNotBlank()) appendLine(check.summary)
        check.combos.forEach { appendLine("- ${comboLevelText(it.level)}: ${it.a} + ${it.b}: ${it.note}") }
        check.timing.forEach { appendLine("- Einnahme: $it") }
        if (check.questions.isNotEmpty()) {
            appendLine("Fragen:")
            check.questions.forEach { appendLine("- $it") }
        }
    }
}
