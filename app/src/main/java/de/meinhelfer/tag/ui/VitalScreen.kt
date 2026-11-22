package de.meinhelfer.tag.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.data.VitalEntry
import de.meinhelfer.tag.export.CarePdfs
import de.meinhelfer.tag.summary.VitalKind
import de.meinhelfer.tag.summary.Vitals
import de.meinhelfer.tag.update.AppPrefs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
private val DAY_TIME = DateTimeFormatter.ofPattern("EE dd.MM., HH:mm", Locale.GERMANY)
private val DRINKS = listOf(150, 200, 250, 500)

private fun whenText(e: VitalEntry, today: Long): String {
    val t = Instant.ofEpochMilli(e.atMillis).atZone(ZoneId.systemDefault())
    return when (e.day) {
        today -> "heute " + t.format(TIME_ONLY)
        today - 1 -> "gestern " + t.format(TIME_ONLY)
        else -> t.format(DAY_TIME)
    }
}

/** Karte auf der Heute-Seite: Trinkmenge mit Schnelltasten, letzte Messwerte. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VitalTodayCard(vm: MainViewModel, person: Person?, todayDay: Long, onOpen: () -> Unit) {
    val context = LocalContext.current
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    val pid = person?.id ?: return
    val goal = remember(pid) { AppPrefs.drinkGoal(context, pid) }
    val drunk = Vitals.drinkToday(vitals, pid, todayDay)
    var entryKind by remember { mutableStateOf<VitalKind?>(null) }
    var showEntry by remember { mutableStateOf(false) }
    val lastDrink = vitals.firstOrNull { it.kind == VitalKind.Trinken.key && it.day == todayDay }
    val latest = VitalKind.MEASURES.mapNotNull { k -> vitals.firstOrNull { it.kind == k.key } }.sortedByDescending { it.atMillis }.take(3)

    Foldable(
        key = "trinken",
        title = "Trinken und Werte",
        summary = "💧 $drunk ml" + if (goal > 0) " von $goal ml" else "",
    ) {
        HCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Trinken und Werte", style = MaterialTheme.typography.titleMedium)
                Text("💧 Heute getrunken: $drunk ml" + if (goal > 0) " von $goal ml" else "", style = MaterialTheme.typography.bodyLarge)
                if (goal > 0) LinearProgressIndicator(progress = { (drunk.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DRINKS.forEach { ml ->
                        OutlinedButton(shape = buttonShape(), contentPadding = PaddingValues(horizontal = 12.dp), onClick = { vm.addVital(VitalKind.Trinken.key, ml.toDouble(), unit = "ml") }) {
                            Text("+$ml")
                        }
                    }
                    TextButton(onClick = {
                        entryKind = VitalKind.Trinken
                        showEntry = true
                    }) { Text("Andere Menge") }
                }
                lastDrink?.let { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Zuletzt: +${Vitals.number(d.value, 0)} ml um " + Instant.ofEpochMilli(d.atMillis).atZone(ZoneId.systemDefault()).format(TIME_ONLY), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.deleteVital(d) }) { Text("Rückgängig") }
                    }
                }
                if (latest.isNotEmpty()) {
                    Text(
                        "Zuletzt gemessen: " + latest.joinToString(" · ") { e -> (VitalKind.of(e.kind)?.emoji ?: "") + " " + Vitals.valueText(e) + " (" + whenText(e, todayDay) + ")" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(shape = buttonShape(), onClick = {
                        entryKind = null
                        showEntry = true
                    }) { Text("Wert eintragen") }
                    OutlinedButton(shape = buttonShape(), onClick = onOpen) { Text("Verlauf und PDF") }
                }
            }
        }
    }
    if (showEntry) {
        VitalEntryDialog(initialKind = entryKind, vm = vm, onDone = { showEntry = false })
    }
}

/** Wert eintragen: Art waehlen, Wert(e), Zeitpunkt, Notiz. Unplausible Zahlen (Tippfehler) werden abgefangen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VitalEntryDialog(initialKind: VitalKind?, vm: MainViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var kind by remember { mutableStateOf(initialKind ?: VitalKind.Blutdruck) }
    var v1 by remember { mutableStateOf("") }
    var v2 by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var sugarUnit by remember { mutableStateOf(AppPrefs.sugarUnit(context)) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf<LocalTime?>(null) }

    val unit = if (kind == VitalKind.Blutzucker) sugarUnit else kind.unit
    val range = if (kind == VitalKind.Blutzucker && sugarUnit == "mmol/l") VitalKind.MMOL_RANGE else kind.plausible
    val a = Vitals.parse(v1)
    val b = Vitals.parse(v2)
    val p = Vitals.parse(pulse)
    val okA = a != null && a in range
    val okB = kind.second == null || (b != null && b in kind.second!!)
    val okP = pulse.isBlank() || (p != null && p in VitalKind.Puls.plausible)
    val canSave = okA && okB && okP

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (initialKind == VitalKind.Trinken) "Getrunken" else "Wert eintragen") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialKind != VitalKind.Trinken) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VitalKind.entries.forEach { k ->
                            FilterChip(selected = kind == k, onClick = {
                                kind = k
                                v1 = ""
                                v2 = ""
                            }, label = { Text(k.emoji + " " + k.label) })
                        }
                    }
                }
                if (kind == VitalKind.Blutzucker) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("mg/dl", "mmol/l").forEach { u ->
                            FilterChip(selected = sugarUnit == u, onClick = {
                                sugarUnit = u
                                AppPrefs.setSugarUnit(context, u)
                            }, label = { Text(u) })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = v1,
                        onValueChange = { v1 = it },
                        label = { Text(if (kind == VitalKind.Blutdruck) "Oberer Wert" else "${kind.label} ($unit)") },
                        singleLine = true,
                        isError = v1.isNotBlank() && !okA,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    if (kind.second != null) {
                        OutlinedTextField(
                            value = v2,
                            onValueChange = { v2 = it },
                            label = { Text("Unterer Wert") },
                            singleLine = true,
                            isError = v2.isNotBlank() && !okB,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (kind == VitalKind.Blutdruck) {
                    OutlinedTextField(
                        value = pulse,
                        onValueChange = { pulse = it },
                        label = { Text("Puls (optional)") },
                        singleLine = true,
                        isError = !okP,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (kind == VitalKind.Trinken) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(100, 150, 200, 250, 300, 500, 1000).forEach { ml ->
                            FilterChip(selected = a == ml.toDouble(), onClick = { v1 = ml.toString() }, label = { Text("$ml ml") })
                        }
                    }
                }
                if ((v1.isNotBlank() && !okA) || (v2.isNotBlank() && !okB) || !okP) {
                    Text("Bitte prüfen: Diese Zahl ist ungewöhnlich (Tippfehler?).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(label = "Tag", date = date, onChange = { date = it }, modifier = Modifier.weight(1f))
                    TimePickerField(label = "Uhrzeit", time = time, onChange = { time = it }, placeholder = "jetzt", defaultTime = LocalTime.now().withSecond(0).withNano(0), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notiz (optional)") },
                    placeholder = { Text(if (kind == VitalKind.Trinken) "z. B. Tee, Suppe" else "z. B. nach dem Essen, im Sitzen") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Nur zum Festhalten. Die App bewertet keine Werte.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                val zone = ZoneId.systemDefault()
                val at = if (time == null && date == LocalDate.now()) {
                    System.currentTimeMillis()
                } else {
                    LocalDateTime.of(date, time ?: LocalTime.of(12, 0)).atZone(zone).toInstant().toEpochMilli()
                }
                vm.addVital(kind.key, a!!, if (kind.second != null) b else null, unit, note, at)
                if (kind == VitalKind.Blutdruck && p != null) vm.addVital(VitalKind.Puls.key, p, null, VitalKind.Puls.unit, "", at)
                onDone()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Abbrechen") } },
    )
}

/** Einfacher Verlauf als Linie (bzw. zwei Linien beim Blutdruck), mit optionalem Bereich oder Ziel als gestrichelte Linie. */
@Composable
internal fun MiniChart(values: List<Double>, values2: List<Double>?, marks: List<Double>, bars: Boolean = false) {
    if (values.isEmpty()) return
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val markColor = MaterialTheme.colorScheme.outline
    val all = values + values2.orEmpty() + marks
    val min = if (bars) 0.0 else all.min()
    val max = all.max()
    val span = (max - min).takeIf { it > 0 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(110.dp).padding(vertical = 6.dp)) {
        fun y(v: Double) = (size.height - ((v - min) / span * size.height)).toFloat()
        val n = values.size
        fun x(i: Int) = if (n <= 1) size.width / 2 else i * size.width / (n - 1)
        marks.forEach { m ->
            drawLine(markColor, Offset(0f, y(m)), Offset(size.width, y(m)), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        if (bars) {
            val w = size.width / (n * 1.5f)
            values.forEachIndexed { i, v ->
                val cx = if (n <= 1) size.width / 2 else w / 2 + i * (size.width - w) / (n - 1)
                drawRect(primary, Offset(cx - w / 2, y(v)), androidx.compose.ui.geometry.Size(w, size.height - y(v)))
            }
        } else {
            fun line(list: List<Double>, color: Color) {
                val path = Path()
                list.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                drawPath(path, color, style = Stroke(width = 4f))
                list.forEachIndexed { i, v -> drawCircle(color, 5f, Offset(x(i), y(v))) }
            }
            line(values, primary)
            values2?.let { line(it, secondary) }
        }
    }
}

/** Ganze Seite: Trinken mit Ziel, alle Messwerte mit Verlauf, eigener Bereich, PDF. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VitalScreen(vm: MainViewModel, person: Person?, onBack: () -> Unit) {
    val context = LocalContext.current
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    val pid = person?.id ?: 1L
    val today = LocalDate.now().toEpochDay()
    var goal by remember(pid) { mutableIntStateOf(AppPrefs.drinkGoal(context, pid)) }
    var editGoal by remember { mutableStateOf(false) }
    var entryKind by remember { mutableStateOf<VitalKind?>(null) }
    var showEntry by remember { mutableStateOf(false) }
    var rangeFor by remember { mutableStateOf<VitalKind?>(null) }
    var rangeTick by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var pdfDays by remember { mutableIntStateOf(14) }

    val drinkDays = (13 downTo 0).map { today - it }
    val drinkTotals = drinkDays.map { d -> vitals.filter { it.kind == VitalKind.Trinken.key && it.day == d }.sumOf { it.value } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trinken und Werte") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") } },
            )
        },
    ) { inner ->
        val vitalList = androidx.compose.foundation.lazy.rememberLazyListState()
        val vitalJumps = rememberJumpList(vitalList)
        LazyColumn(
            state = vitalList,
            modifier = Modifier.padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            vitalJumps.reset()
            jumpItem(vitalJumps, "💧 Trinken") {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val drunk = drinkTotals.last().toInt()
                        Text("💧 Trinken", style = MaterialTheme.typography.titleMedium)
                        Text("Heute: $drunk ml" + if (goal > 0) " von $goal ml" else "", style = MaterialTheme.typography.bodyLarge)
                        if (goal > 0) LinearProgressIndicator(progress = { (drunk.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DRINKS.forEach { ml ->
                                OutlinedButton(shape = buttonShape(), contentPadding = PaddingValues(horizontal = 12.dp), onClick = { vm.addVital(VitalKind.Trinken.key, ml.toDouble(), unit = "ml") }) { Text("+$ml") }
                            }
                            TextButton(onClick = {
                                entryKind = VitalKind.Trinken
                                showEntry = true
                            }) { Text("Andere Menge") }
                        }
                        Text("Letzte 14 Tage" + if (goal > 0) " (gestrichelt: Ziel)" else "", style = MaterialTheme.typography.labelMedium)
                        MiniChart(drinkTotals, null, if (goal > 0) listOf(goal.toDouble()) else emptyList(), bars = true)
                        val withData = drinkTotals.filter { it > 0 }
                        if (withData.isNotEmpty()) {
                            Text("Im Schnitt ${withData.average().toInt()} ml an ${withData.size} Tag(en) mit Eintrag.", style = MaterialTheme.typography.bodySmall)
                        }
                        vitals.filter { it.kind == VitalKind.Trinken.key && it.day == today }.forEach { d ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Instant.ofEpochMilli(d.atMillis).atZone(ZoneId.systemDefault()).format(TIME_ONLY) + "  " + Vitals.number(d.value, 0) + " ml" +
                                        (if (d.note.isNotBlank()) " · ${d.note}" else ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { vm.deleteVital(d) }) { Text("Löschen") }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (goal > 0) "Trinkziel: $goal ml pro Tag" else "Kein Trinkziel", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editGoal = true }) { Text("Ändern") }
                        }
                        DrinkReminderSetting()
                    }
                }
            }

            jumpItem(vitalJumps, "Messwerte eintragen") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Messwerte", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VitalKind.MEASURES.forEach { k ->
                            OutlinedButton(shape = buttonShape(), contentPadding = PaddingValues(horizontal = 12.dp), onClick = {
                                entryKind = k
                                showEntry = true
                            }) { Text("+ ${k.emoji} ${k.label}") }
                        }
                    }
                }
            }

            jumpItem(vitalJumps, "⌚ Von Uhr oder Messgerät") { HealthImportCard(vm, person, vitals) }

            VitalKind.MEASURES.forEach { k ->
                val list = vitals.filter { it.kind == k.key }
                if (list.isEmpty()) return@forEach
                jumpItem(vitalJumps, "${k.emoji} ${k.label}", key = k.key) {
                    val range = remember(rangeTick, pid) { AppPrefs.vitalRange(context, pid, k.key) }
                    val recent = list.take(30).reversed()
                    val unit = list.first().unit.ifBlank { k.unit }
                    val sameUnit = recent.filter { it.unit.ifBlank { k.unit } == unit }
                    HCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${k.emoji} ${k.label}", style = MaterialTheme.typography.titleMedium)
                            Text("Zuletzt: ${Vitals.valueText(list.first())} (${whenText(list.first(), today)})", style = MaterialTheme.typography.bodyLarge)
                            if (sameUnit.size >= 2) {
                                MiniChart(
                                    sameUnit.map { it.value },
                                    if (k.second != null) sameUnit.map { it.value2 ?: it.value } else null,
                                    listOfNotNull(range?.first, range?.second),
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    range?.let { r -> "Eigener Bereich: " + (r.first?.let { Vitals.number(it, k.decimals) } ?: "…") + " bis " + (r.second?.let { Vitals.number(it, k.decimals) } ?: "…") }
                                        ?: "Kein eigener Bereich eingetragen",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { rangeFor = k }) { Text(if (range == null) "Festlegen" else "Ändern") }
                            }
                            val shown = if (expanded == k.key) list.take(60) else list.take(4)
                            shown.forEach { e ->
                                val out = Vitals.outside(e, range)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        whenText(e, today) + "  " + Vitals.valueText(e) + (if (out) "  ⚠ außerhalb" else "") + (if (e.note.isNotBlank()) " · ${e.note}" else ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (out) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { vm.deleteVital(e) }) { Text("Löschen") }
                                }
                            }
                            if (list.size > 4) {
                                TextButton(onClick = { expanded = if (expanded == k.key) null else k.key }) {
                                    Text(if (expanded == k.key) "Weniger anzeigen" else "Alle anzeigen (${list.size})")
                                }
                            }
                        }
                    }
                }
            }

            jumpItem(vitalJumps, "Protokoll als PDF") {
                HCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Protokoll als PDF", style = MaterialTheme.typography.titleMedium)
                        Text("Tabelle mit Trinkmenge und allen Messwerten je Tag, z. B. für den Arztbesuch oder den Pflegedienst.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 14, 30).forEach { d -> FilterChip(selected = pdfDays == d, onClick = { pdfDays = d }, label = { Text("$d Tage") }) }
                        }
                        PdfButtons(shareLabel = "Als PDF teilen", chooserTitle = "Protokoll teilen") { ctx ->
                            CarePdfs.vitals(
                                ctx, person, vitals.filter { it.day > today - pdfDays }, goal,
                                VitalKind.MEASURES.mapNotNull { k -> AppPrefs.vitalRange(ctx, pid, k.key)?.let { k.key to it } }.toMap(),
                                today - pdfDays + 1, today,
                            )
                        }
                    }
                }
            }
            jumpItem(vitalJumps, null) {
                Text(
                    "Alles bleibt auf dem Handy. Die App bewertet keine Werte; „außerhalb“ bezieht sich nur auf den Bereich, den du selbst eingetragen hast (z. B. wie vom Arzt besprochen). Bei Beschwerden Arzt anrufen, im Notfall 112.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (showEntry) VitalEntryDialog(initialKind = entryKind, vm = vm, onDone = { showEntry = false })

    if (editGoal) {
        var text by remember { mutableStateOf(if (goal > 0) goal.toString() else "") }
        AlertDialog(
            onDismissRequest = { editGoal = false },
            title = { Text("Trinkziel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("ml pro Tag (leer = ohne Ziel)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Richtwert der Deutschen Gesellschaft für Ernährung für Menschen ab 65: etwa 1,3 Liter am Tag über Getränke. Bei Herz- oder Nierenerkrankungen gibt der Arzt oft eine andere Menge vor, dann diese eintragen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    goal = text.toIntOrNull()?.coerceIn(0, 5000) ?: 0
                    AppPrefs.setDrinkGoal(context, pid, goal)
                    editGoal = false
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { editGoal = false }) { Text("Abbrechen") } },
        )
    }

    rangeFor?.let { k ->
        val current = AppPrefs.vitalRange(context, pid, k.key)
        var min by remember(k) { mutableStateOf(current?.first?.let { Vitals.number(it, k.decimals) }.orEmpty()) }
        var max by remember(k) { mutableStateOf(current?.second?.let { Vitals.number(it, k.decimals) }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { rangeFor = null },
            title = { Text("Eigener Bereich: ${k.label}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = min, onValueChange = { min = it }, label = { Text("von") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = max, onValueChange = { max = it }, label = { Text("bis") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                    Text(
                        (if (k == VitalKind.Blutdruck) "Beim Blutdruck gilt der Bereich für den oberen Wert. " else "") +
                            "Trage nur ein, was mit Arzt oder Pflegedienst besprochen ist. Werte außerhalb werden markiert und in der Auswertung genannt. Leer lassen = kein Bereich.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AppPrefs.setVitalRange(context, pid, k.key, Vitals.parse(min), Vitals.parse(max))
                    rangeTick++
                    rangeFor = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { rangeFor = null }) { Text("Abbrechen") } },
        )
    }
}
