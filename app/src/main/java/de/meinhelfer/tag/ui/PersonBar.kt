package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import java.time.LocalDate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Person

/** Leiste oben auf jeder Seite: fuer welche betreute Person die Daten gelten (wechseln, anlegen, umbenennen, loeschen). */
@Composable
fun PersonBar(persons: List<Person>, currentId: Long, vm: MainViewModel) {
    val current = persons.firstOrNull { it.id == currentId }
    var menu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Für:", style = MaterialTheme.typography.labelLarge)
        Box {
            TextButton(onClick = { menu = true }) {
                Text((current?.name ?: "…") + "  ▾", style = MaterialTheme.typography.titleSmall)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                persons.forEach { p ->
                    DropdownMenuItem(
                        text = { Text((if (p.id == currentId) "✓ " else "") + p.name) },
                        onClick = {
                            vm.selectPerson(p.id)
                            menu = false
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Neue Person anlegen …") },
                    onClick = {
                        menu = false
                        showAdd = true
                    },
                )
                if (current != null) {
                    DropdownMenuItem(
                        text = { Text("Profil von „${current.name}“ bearbeiten …") },
                        onClick = {
                            menu = false
                            showEdit = true
                        },
                    )
                }
                if (current != null && persons.size > 1) {
                    DropdownMenuItem(
                        text = { Text("„${current.name}“ löschen …") },
                        onClick = {
                            menu = false
                            showDelete = true
                        },
                    )
                }
            }
        }
    }

    if (showAdd) {
        PersonDialog(
            initial = null,
            others = persons,
            onDismiss = { showAdd = false },
            onSave = {
                vm.addPerson(it)
                showAdd = false
            },
        )
    }
    if (showEdit && current != null) {
        PersonDialog(
            initial = current,
            others = persons.filter { it.id != current.id },
            onDismiss = { showEdit = false },
            onSave = {
                vm.updatePerson(it)
                showEdit = false
            },
        )
    }
    if (showDelete && current != null) {
        AlertDialog(
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = { showDelete = false },
            title = { Text("Person löschen?") },
            text = {
                Text("„${current.name}“ mit allen Medikamenten, Terminen und Bemerkungen wirklich löschen? Das lässt sich nicht rückgängig machen. Die Kontakte bleiben erhalten.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePerson(current)
                    showDelete = false
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Abbrechen") } },
        )
    }
}

/** Profil einer betreuten Person: Name, Geburtsdatum, Pflegegrad, Wichtiges. Alles bleibt nur in der App. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonDialog(initial: Person?, onDismiss: () -> Unit, onSave: (Person) -> Unit, others: List<Person> = emptyList()) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var birth by remember { mutableStateOf(initial?.birthDate?.let { LocalDate.ofEpochDay(it) }) }
    var level by remember { mutableStateOf(initial?.careLevel ?: 0) }
    var important by remember { mutableStateOf(initial?.important ?: "") }
    var street by remember { mutableStateOf(initial?.street ?: "") }
    var zip by remember { mutableStateOf(initial?.zip ?: "") }
    var city by remember { mutableStateOf(initial?.city ?: "") }
    var insurer by remember { mutableStateOf(initial?.insurer ?: "") }
    var insuranceNumber by remember { mutableStateOf(initial?.insuranceNumber ?: "") }
    var floor by remember { mutableStateOf(initial?.floor ?: "") }
    var elevator by remember { mutableStateOf(initial?.elevator ?: 0) }
    var access by remember { mutableStateOf(initial?.access ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var disability by remember { mutableStateOf(de.meinhelfer.tag.summary.Disability.decode(initial?.disability ?: "")) }
    var household by remember { mutableStateOf(de.meinhelfer.tag.summary.Household.decode(initial?.household ?: "")) }
    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neue Person" else "Profil bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CardScanButtons(
                    kinds = listOf(de.meinhelfer.tag.media.CardKind.Ausweis, de.meinhelfer.tag.media.CardKind.Gesundheitskarte),
                    otherPerson = true,
                ) { d ->
                    d.givenNames?.let { name = it }
                    d.birthDate?.let { birth = it }
                    d.street?.let { street = it }
                    d.zip?.let { zip = it }
                    d.city?.let { city = it }
                    d.insurer?.let { insurer = it }
                    d.insuranceNumber?.let { insuranceNumber = it }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DatePickerField(label = "Geburtsdatum (optional)", date = birth, onChange = { birth = it })
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.take(40) },
                    label = { Text("Telefon (optional)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (birth != null) {
                    TextButton(onClick = { birth = null }) { Text("Geburtsdatum entfernen") }
                }
                Text("Pflegegrad", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = level == 0, onClick = { level = 0 }, label = { Text("Keiner / unbekannt") })
                    (1..5).forEach { l ->
                        FilterChip(selected = level == l, onClick = { level = l }, label = { Text("$l") })
                    }
                }
                Text("Wohnort und Versicherung", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = street,
                    onValueChange = { street = it },
                    label = { Text("Straße und Hausnummer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = zip,
                        onValueChange = { v -> zip = v.filter { it.isDigit() }.take(5) },
                        label = { Text("PLZ") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Ort") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                }
                Text("Zugang zur Wohnung", style = MaterialTheme.typography.labelLarge)
                AccessFields(
                    floor = floor, onFloor = { floor = it },
                    elevator = elevator, onElevator = { elevator = it },
                    access = access, onAccess = { access = it },
                    hint = "Das wird bei Fahrdienst-Anfragen und in der Notfallkarte als Abholort mit angegeben.",
                )
                DropdownField(
                    label = "Krankenkasse / Pflegekasse",
                    value = insurer,
                    options = INSURERS,
                    onValueChange = { insurer = it },
                )
                OutlinedTextField(
                    value = insuranceNumber,
                    onValueChange = { insuranceNumber = it },
                    label = { Text("Versichertennummer (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DisabilityFields(disability) { disability = it }
                HouseholdFields(household, others) { household = it }
                OutlinedTextField(
                    value = important,
                    onValueChange = { important = it },
                    label = { Text("Wichtig zu wissen") },
                    placeholder = { Text("z. B. Allergien, Hörgerät, Hausarzt-Vertretung …") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Diese Angaben bleiben nur in der App auf diesem Handy. Sie werden nie automatisch gesendet. Für die Suche nach Stellen in deiner Nähe werden nur Krankenkasse und PLZ/Ort verwendet.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: Person(name = name)).copy(
                            name = name.trim(),
                            birthDate = birth?.toEpochDay(),
                            careLevel = level,
                            important = important.trim(),
                            street = street.trim(),
                            zip = zip.trim(),
                            city = city.trim(),
                            insurer = insurer.trim(),
                            insuranceNumber = insuranceNumber.trim(),
                            floor = floor.trim(),
                            elevator = elevator,
                            access = access.trim(),
                            phone = phone.trim(),
                            disability = disability.encode(),
                            household = household.copy(withPersons = household.withPersons.filter { id -> others.any { it.id == id } }.toSet()).encode(),
                        ),
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Einfacher Dialog mit einem Textfeld (Name, To-do usw.). */
@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Schwerbehindertenausweis: GdB, Merkzeichen, Gültigkeit, Amt, mögliche Nachteilsausgleiche. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DisabilityFields(d: de.meinhelfer.tag.summary.Disability, onChange: (de.meinhelfer.tag.summary.Disability) -> Unit) {
    val D = de.meinhelfer.tag.summary.Disability
    Text("Schwerbehindertenausweis", style = MaterialTheme.typography.labelLarge)
    Text("Grad der Behinderung (GdB)", style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = d.gdb == 0, onClick = { onChange(d.copy(gdb = 0)) }, label = { Text("keiner") })
        (20..100 step 10).forEach { g -> FilterChip(selected = d.gdb == g, onClick = { onChange(d.copy(gdb = g)) }, label = { Text("$g") }) }
    }
    if (d.gdb == 0 && d.marks.isEmpty()) {
        Text(
            "Tipp: Fotografiere den Ausweis unter Dokumente – die App liest GdB, Merkzeichen und Gültigkeit und trägt sie hier ein.",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    Text("Merkzeichen", style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        D.MARKS.forEach { (k, v) ->
            FilterChip(
                selected = k in d.marks,
                onClick = { onChange(d.copy(marks = if (k in d.marks) d.marks - k else d.marks + k)) },
                label = { Text("$k · $v") },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked = d.unlimited, onCheckedChange = { onChange(d.copy(unlimited = it)) })
        Text("unbefristet", style = MaterialTheme.typography.bodyMedium)
    }
    if (!d.unlimited) {
        DatePickerField(label = "Gültig bis", date = d.validUntil?.let { LocalDate.ofEpochDay(it) }, onChange = { onChange(d.copy(validUntil = it?.toEpochDay())) })
    }
    OutlinedTextField(
        value = d.office,
        onValueChange = { onChange(d.copy(office = it.take(80))) },
        label = { Text("Ausgestellt von (z. B. Versorgungsamt)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = d.reference,
        onValueChange = { onChange(d.copy(reference = it.take(40))) },
        label = { Text("Aktenzeichen (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val benefits = D.benefits(d)
    if (benefits.isNotEmpty()) {
        Text("Mögliche Nachteilsausgleiche (Anhaltspunkt, keine Beratung):", style = MaterialTheme.typography.bodyMedium)
        benefits.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
}

/** Wohnsituation und Tagesstruktur: Wohnform, wer noch im Haushalt lebt, ob du mit wohnst, tagsüber Werkstatt usw. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HouseholdFields(h: de.meinhelfer.tag.summary.Household, others: List<Person>, onChange: (de.meinhelfer.tag.summary.Household) -> Unit) {
    val H = de.meinhelfer.tag.summary.Household
    Text("Wohnsituation und Tagesstruktur", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        H.LIVING.forEach { (k, v) -> FilterChip(selected = h.living == k, onClick = { onChange(h.copy(living = if (h.living == k) "" else k)) }, label = { Text(v) }) }
    }
    Text("Weitere Menschen im Haushalt", style = MaterialTheme.typography.bodyMedium)
    @Composable
    fun counter(label: String, value: Int, set: (Int) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(enabled = value > 0, onClick = { set(value - 1) }) { Text("−") }
            Text("$value", style = MaterialTheme.typography.titleMedium)
            TextButton(enabled = value < 15, onClick = { set(value + 1) }) { Text("+") }
        }
    }
    counter("Kinder unter 6", h.under6) { onChange(h.copy(under6 = it)) }
    counter("Kinder/Jugendliche 6–17", h.age6to17) { onChange(h.copy(age6to17 = it)) }
    counter("Erwachsene 18–64", h.age18to64) { onChange(h.copy(age18to64 = it)) }
    counter("Ältere ab 65", h.age65plus) { onChange(h.copy(age65plus = it)) }
    if (others.isNotEmpty()) {
        Text("Lebt zusammen mit (in der App angelegt)", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            others.forEach { o ->
                FilterChip(
                    selected = o.id in h.withPersons,
                    onClick = { onChange(h.copy(withPersons = if (o.id in h.withPersons) h.withPersons - o.id else h.withPersons + o.id)) },
                    label = { Text(o.name) },
                )
            }
        }
        Text("So gleicht der Assistent Termine aller im Haushalt ab.", style = MaterialTheme.typography.bodySmall)
    }
    Text("Wohnst du (pflegende Person) mit im Haushalt?", style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = h.caregiverHere == true, onClick = { onChange(h.copy(caregiverHere = true)) }, label = { Text("Ja") })
        FilterChip(selected = h.caregiverHere == false, onClick = { onChange(h.copy(caregiverHere = false)) }, label = { Text("Nein") })
        FilterChip(selected = h.caregiverHere == null, onClick = { onChange(h.copy(caregiverHere = null)) }, label = { Text("keine Angabe") })
    }
    Text("Tagsüber", style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        H.DAYTIME.forEach { (k, v) -> FilterChip(selected = h.daytime == k, onClick = { onChange(h.copy(daytime = if (h.daytime == k) "" else k)) }, label = { Text(v) }) }
    }
    if (h.daytime.isNotBlank() && h.daytime != "zuhause") {
        OutlinedTextField(
            value = h.place,
            onValueChange = { onChange(h.copy(place = it.take(60))) },
            label = { Text("Name der Einrichtung (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEachIndexed { i, n ->
                val d = i + 1
                FilterChip(selected = d in h.days, onClick = { onChange(h.copy(days = if (d in h.days) h.days - d else h.days + d)) }, label = { Text(n) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = h.from,
                onValueChange = { onChange(h.copy(from = it.filter { c -> c.isDigit() || c == ':' }.take(5))) },
                label = { Text("von (08:00)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = h.to,
                onValueChange = { onChange(h.copy(to = it.filter { c -> c.isDigit() || c == ':' }.take(5))) },
                label = { Text("bis (15:30)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(checked = h.transport, onCheckedChange = { onChange(h.copy(transport = it)) })
            Text("wird mit Fahrdienst gebracht und abgeholt", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Fällt ein Termin in diese Zeit, erinnert der Assistent, der Einrichtung Bescheid zu geben.", style = MaterialTheme.typography.bodySmall)
    }
}
