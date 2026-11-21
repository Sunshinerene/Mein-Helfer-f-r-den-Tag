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
            onDismiss = { showEdit = false },
            onSave = {
                vm.updatePerson(it)
                showEdit = false
            },
        )
    }
    if (showDelete && current != null) {
        AlertDialog(
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
fun PersonDialog(initial: Person?, onDismiss: () -> Unit, onSave: (Person) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var birth by remember { mutableStateOf(initial?.birthDate?.let { LocalDate.ofEpochDay(it) }) }
    var level by remember { mutableStateOf(initial?.careLevel ?: 0) }
    var important by remember { mutableStateOf(initial?.important ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neue Person" else "Profil bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DatePickerField(label = "Geburtsdatum (optional)", date = birth, onChange = { birth = it })
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
                OutlinedTextField(
                    value = important,
                    onValueChange = { important = it },
                    label = { Text("Wichtig zu wissen") },
                    placeholder = { Text("z. B. Allergien, Hörgerät, Hausarzt-Vertretung …") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Diese Angaben bleiben nur in der App auf diesem Handy. Sie werden nie automatisch gesendet.",
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
