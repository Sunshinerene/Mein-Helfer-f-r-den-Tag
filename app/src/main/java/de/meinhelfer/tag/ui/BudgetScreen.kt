package de.meinhelfer.tag.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.data.BudgetEntry
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.summary.BudgetKind
import de.meinhelfer.tag.summary.BudgetStatus
import de.meinhelfer.tag.summary.Budgets
import de.meinhelfer.tag.summary.Vitals
import de.meinhelfer.tag.update.AppPrefs
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BUDGET_DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

@Composable
private fun Bar(used: Double, total: Double) {
    if (total > 0) LinearProgressIndicator(progress = { (used / total).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
}

/** Budgets der Pflegekasse: Entlastungsbetrag und Gemeinsamer Jahresbetrag, mit Ausgaben, Rest und Fristen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetScreen(vm: MainViewModel, person: Person?, onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by vm.budget.collectAsStateWithLifecycle()
    val pid = person?.id ?: 1L
    val today = LocalDate.now()
    var year by remember { mutableIntStateOf(today.year) }
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<BudgetEntry?>(null) }
    var addKind by remember { mutableStateOf<BudgetKind?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val since = remember(tick, pid) { AppPrefs.budgetSince(context, pid) }
    val careLevel = person?.careLevel ?: 0

    fun months(y: Int): Int = when {
        since == null -> 12
        y < since.year -> 0
        y == since.year -> 13 - since.monthValue
        else -> 12
    }
    val ratesTick by vm.ratesTick.collectAsStateWithLifecycle()
    val ratesText by vm.ratesText.collectAsStateWithLifecycle()
    val dismissed by vm.assistantDismissed.collectAsStateWithLifecycle()
    val rates = remember(ratesText, ratesTick, tick) { vm.rateChanges() }
    val relief = remember(entries, year, tick, since, ratesTick) {
        Budgets.relief(entries, year, today, { y -> months(y) }, { y -> AppPrefs.budgetCarryMonth(context, y) }) { y -> AppPrefs.budgetMonthly(context, y) }
    }
    val yearly = remember(entries, year, tick, ratesTick) { Budgets.yearly(entries, year, if (careLevel == 1) 0.0 else AppPrefs.budgetYearly(context, year)) }
    val inYear = entries.filter { LocalDate.ofEpochDay(it.day).year == year }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets der Pflegekasse") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") } },
            )
        },
    ) { inner ->
        val budgetList = androidx.compose.foundation.lazy.rememberLazyListState()
        val budgetJumps = rememberJumpList(budgetList)
        LazyColumn(Modifier.padding(inner), state = budgetList, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            budgetJumps.reset()
            jumpItem(budgetJumps, null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(shape = buttonShape(), onClick = { year-- }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("◀") }
                    Text("Jahr $year" + (person?.name?.let { " – $it" } ?: ""), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    OutlinedButton(shape = buttonShape(), enabled = year < today.year + 1, onClick = { year++ }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("▶") }
                }
            }
            if (careLevel == 0) {
                jumpItem(budgetJumps, null) {
                    Text(
                        "Im Profil ist noch kein Pflegegrad eingetragen. Diese Budgets gibt es erst mit Pflegegrad (Antrag bei der Pflegekasse, siehe Wegweiser).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            // ---- Beträge der Pflegekasse: Abgleich mit der Datei im Repo (nur nach Bestätigung)
            jumpItem(budgetJumps, "Beträge aktuell?") {
                HCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Beträge aktuell?", style = MaterialTheme.typography.titleSmall)
                        rates.first.forEach { c ->
                            Text(de.meinhelfer.tag.summary.BenefitRates.changeText(c), style = MaterialTheme.typography.bodyMedium)
                            Button(shape = buttonShape(), onClick = {
                                vm.applyProposal(
                                    de.meinhelfer.tag.summary.Proposal(
                                        c.key, "", "", de.meinhelfer.tag.summary.ProposalAction.SetBenefits(c.year, c.monthlyNew, c.yearlyNew, c.carryNew),
                                    ),
                                )
                                tick++
                            }) { Text("Für ${c.year} übernehmen") }
                        }
                        rates.second.filter { it.key !in dismissed }.forEach { n ->
                            Text("Hinweis (" + n.status + ", Stand " + n.date + "): " + n.text, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            if (ratesText.isBlank()) "Die App vergleicht die Beträge mit einer Liste, die mit der App gepflegt wird (nach den Leistungsübersichten des Bundesgesundheitsministeriums). Noch nicht geladen."
                            else if (rates.first.isEmpty()) "Die eingestellten Beträge entsprechen dem Stand " + de.meinhelfer.tag.summary.BenefitRates.parse(ratesText).stand + ". Ändert sich etwas, fragt die App (bzw. dein Assistent) vor dem Übernehmen."
                            else "Erst nach deinem Tipp werden die Beträge geändert. Im Zweifel bei der Pflegekasse nachfragen.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { vm.refreshRates(force = true) }) { Text("Jetzt prüfen") }
                    }
                }
            }
            // ---- Hinweis zur geplanten Reform (Stand 22.11.2026), nur solange keine Liste geladen ist
            if (ratesText.isBlank() && year >= 2026 && today.year <= 2027) {
                jumpItem(budgetJumps, "Hinweis: Reform 2027") {
                    HCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Geplante Pflegereform ab 2027", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Stand November 2026: Die Bundesregierung plant mit dem Pflegeneuordnungsgesetz ab 1. Januar 2027 unter anderem, den Entlastungsbetrag durch ein „Sozialraumbudget“ (175 € ab Pflegegrad 2) zu ersetzen; Pflegegrad 1 bekäme nichts mehr. Ob nicht genutzte Beträge aus 2026 noch bis 30. Juni 2027 nutzbar bleiben, ist unklar. Noch ist das kein Gesetz. Tipp: Budgets für 2026 möglichst bis Jahresende nutzen und Belege einreichen. Ändert sich etwas, kannst du unten unter „Beträge“ die Werte anpassen.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            // ---- Entlastungsbetrag
            jumpItem(budgetJumps, "Entlastungsbetrag") {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Entlastungsbetrag", style = MaterialTheme.typography.titleMedium)
                        Text(
                            Budgets.euro(AppPrefs.budgetMonthly(context, year)) + " im Monat (ab Pflegegrad 1, häusliche Pflege), z. B. für Alltagshilfe, Betreuung, Haushaltshilfe, Tages- oder Kurzzeitpflege. Ausgezahlt wird gegen Rechnung.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Bar(relief.usedCurrent, relief.yearTotal)
                        Text("Genutzt $year: ${Budgets.euro(relief.usedCurrent)} von ${Budgets.euro(relief.yearTotal)}", style = MaterialTheme.typography.bodyMedium)
                        if (year == today.year) {
                            Text("Bis jetzt angesammelt: ${Budgets.euro(relief.accruedSoFar)} · jetzt verfügbar: ${Budgets.euro(relief.availableNow)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (relief.carryIn > 0) {
                            Text(
                                "Übertrag aus ${year - 1}: ${Budgets.euro(relief.carryIn)}, davon genutzt ${Budgets.euro(relief.usedFromCarry)}. " +
                                    if (relief.carryLeft > 0) "Rest ${Budgets.euro(relief.carryLeft)} " + (if (year == today.year && today.monthValue <= 6) "verfällt am 30.06.$year." else "ist am 30.06.$year verfallen.") else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (relief.left > 0 && year == today.year) {
                            Text("Nicht genutzt bleiben am Jahresende voraussichtlich ${Budgets.euro(relief.left)}; nach heutigem Recht nutzbar bis 30.06.${year + 1}.", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(shape = buttonShape(), onClick = { addKind = BudgetKind.Entlastung }) { Text("+ Ausgabe") }
                    }
                }
            }
            // ---- Gemeinsamer Jahresbetrag
            jumpItem(budgetJumps, "Verhinderungs- und Kurzzeitpflege") {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Verhinderungs- und Kurzzeitpflege", style = MaterialTheme.typography.titleMedium)
                        if (careLevel == 1) {
                            Text("Den Gemeinsamen Jahresbetrag gibt es erst ab Pflegegrad 2.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(
                                "Gemeinsamer Jahresbetrag ${Budgets.euro(yearly.total)} je Kalenderjahr (ab Pflegegrad 2) für beide zusammen, jeweils bis zu 8 Wochen im Jahr. Verhinderungspflege: wenn die Pflegeperson verhindert ist (Urlaub, Krankheit, stundenweise). Kurzzeitpflege: vorübergehend in einer Einrichtung.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Bar(yearly.used, yearly.total)
                            Text("Genutzt: ${Budgets.euro(yearly.used)} · übrig: ${Budgets.euro(yearly.left)}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Verhinderungspflege ${Budgets.euro(yearly.usedRespite)} (${yearly.daysRespite} von ${Budgets.MAX_DAYS} Tagen) · Kurzzeitpflege ${Budgets.euro(yearly.usedShort)} (${yearly.daysShort} von ${Budgets.MAX_DAYS} Tagen)",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Bei Verhinderungspflege durch nahe Angehörige (bis 2. Grad verwandt oder verschwägert, im Haushalt) erstattet die Kasse meist weniger. Während Verhinderungs- und Kurzzeitpflege wird das Pflegegeld bis zu 8 Wochen zur Hälfte weitergezahlt. Bitte bei der Pflegekasse bestätigen lassen.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(shape = buttonShape(), onClick = { addKind = BudgetKind.Verhinderung }) { Text("+ Verhinderungspflege") }
                                OutlinedButton(shape = buttonShape(), onClick = { addKind = BudgetKind.Kurzzeit }) { Text("+ Kurzzeitpflege") }
                            }
                        }
                    }
                }
            }
            // ---- Ausgaben
            jumpItem(budgetJumps, "Ausgaben") { Text("Ausgaben $year", style = MaterialTheme.typography.titleMedium) }
            if (inYear.isEmpty()) {
                jumpItem(budgetJumps, null) { Text("Noch keine Ausgaben eingetragen.", style = MaterialTheme.typography.bodyMedium) }
            }
            inYear.forEach { e ->
                jumpItem(budgetJumps, null, key = e.id) {
                    val st = BudgetStatus.of(e.status)
                    HCard(Modifier.fillMaxWidth().clickable { editing = e }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                LocalDate.ofEpochDay(e.day).format(BUDGET_DAY) + " · " + (BudgetKind.of(e.kind)?.label ?: e.kind) + " · " + Budgets.euro(e.amount),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                listOfNotNull(
                                    e.provider.ifBlank { null },
                                    e.days.takeIf { it > 0 }?.let { "$it Tag(e)" },
                                    st.label,
                                    e.note.ifBlank { null },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = when (st) {
                                    BudgetStatus.Offen -> MaterialTheme.colorScheme.tertiary
                                    BudgetStatus.Abgelehnt -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
            inYear.count { it.status == BudgetStatus.Offen.key }.takeIf { it > 0 }?.let { open ->
                jumpItem(budgetJumps, null) {
                    Text("$open Ausgabe(n) noch nicht bei der Pflegekasse eingereicht. Belege sammeln und einreichen (viele Kassen haben dafür eine App).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            jumpItem(budgetJumps, "Beträge und Beginn") {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Beträge und Beginn", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Anspruch seit: " + (since?.let { it.monthValue.toString().padStart(2, '0') + "/" + it.year } ?: "schon länger (ganzes Jahr)") +
                                " · Entlastungsbetrag ${Budgets.euro(AppPrefs.budgetMonthly(context, year))}/Monat · Jahresbetrag ${Budgets.euro(AppPrefs.budgetYearly(context, year))} ($year)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { showSettings = true }) { Text("Ändern") }
                    }
                }
            }
            jumpItem(budgetJumps, null) {
                Text(
                    "Nur zum Mitverfolgen, alles bleibt auf dem Handy. Maßgeblich sind die Bescheide und Abrechnungen der Pflegekasse. Beträge Stand November 2026, ohne Gewähr.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    val kindForDialog = addKind
    if (kindForDialog != null || editing != null) {
        BudgetDialog(
            initial = editing,
            kind = editing?.let { BudgetKind.of(it.kind) } ?: kindForDialog ?: BudgetKind.Entlastung,
            onDismiss = {
                addKind = null
                editing = null
            },
            onSave = {
                vm.saveBudget(it)
                addKind = null
                editing = null
            },
            onDelete = editing?.let { e -> { vm.deleteBudget(e); editing = null } },
        )
    }

    if (showSettings) {
        var sinceText by remember { mutableStateOf(since?.let { it.monthValue.toString().padStart(2, '0') + "/" + it.year } ?: "") }
        var monthly by remember { mutableStateOf(Vitals.number(AppPrefs.budgetMonthly(context, year), 2)) }
        var yearlyText by remember { mutableStateOf(Vitals.number(AppPrefs.budgetYearly(context, year), 2)) }
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { showSettings = false },
            title = { Text("Beträge und Beginn") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sinceText,
                        onValueChange = { sinceText = it.take(7) },
                        label = { Text("Pflegegrad seit (MM/JJJJ, leer = schon länger)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = monthly,
                        onValueChange = { monthly = it },
                        label = { Text("Entlastungsbetrag pro Monat in $year (€)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = yearlyText,
                        onValueChange = { yearlyText = it },
                        label = { Text("Gemeinsamer Jahresbetrag $year (€)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Voreingestellt sind die Beträge nach heutigem Recht (131 € und 3.539 €). Nur ändern, wenn die Pflegekasse andere Beträge nennt.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = Regex("""(\d{1,2})\D+(\d{4})""").find(sinceText.trim())?.let { m ->
                        runCatching { YearMonth.of(m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
                    }
                    AppPrefs.setBudgetSince(context, pid, if (sinceText.isBlank()) null else parsed ?: since)
                    Vitals.parse(monthly)?.takeIf { it in 0.0..5000.0 }?.let { AppPrefs.setBudgetMonthly(context, year, it) }
                    Vitals.parse(yearlyText)?.takeIf { it in 0.0..50000.0 }?.let { AppPrefs.setBudgetYearly(context, year, it) }
                    tick++
                    showSettings = false
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Abbrechen") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetDialog(initial: BudgetEntry?, kind: BudgetKind, onDismiss: () -> Unit, onSave: (BudgetEntry) -> Unit, onDelete: (() -> Unit)?) {
    var k by remember { mutableStateOf(kind) }
    var date by remember { mutableStateOf(initial?.let { LocalDate.ofEpochDay(it.day) } ?: LocalDate.now()) }
    var amount by remember { mutableStateOf(initial?.amount?.let { Vitals.number(it, 2) } ?: "") }
    var days by remember { mutableStateOf(initial?.days?.takeIf { it > 0 }?.toString() ?: "") }
    var provider by remember { mutableStateOf(initial?.provider ?: "") }
    var status by remember { mutableStateOf(initial?.status ?: BudgetStatus.Offen.key) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val value = Vitals.parse(amount)
    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Ausgabe eintragen" else "Ausgabe ändern") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BudgetKind.entries.forEach { b -> FilterChip(selected = k == b, onClick = { k = b }, label = { Text(b.label) }) }
                }
                DatePickerField(label = "Datum der Leistung", date = date, onChange = { date = it })
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Betrag (€)") },
                    singleLine = true,
                    isError = amount.isNotBlank() && (value == null || value <= 0),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (k != BudgetKind.Entlastung) {
                    OutlinedTextField(
                        value = days,
                        onValueChange = { days = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Anzahl Tage") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(value = provider, onValueChange = { provider = it }, label = { Text("Anbieter (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Stand", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BudgetStatus.entries.forEach { s -> FilterChip(selected = status == s.key, onClick = { status = s.key }, label = { Text(s.label) }) }
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Ausgabe löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value > 0, onClick = {
                onSave(
                    (initial ?: BudgetEntry(personId = 0, kind = k.key, day = date.toEpochDay(), amount = 0.0)).copy(
                        kind = k.key,
                        day = date.toEpochDay(),
                        amount = value ?: 0.0,
                        days = if (k == BudgetKind.Entlastung) 0 else days.toIntOrNull() ?: 0,
                        provider = provider.trim(),
                        status = status,
                        note = note.trim(),
                    ),
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
