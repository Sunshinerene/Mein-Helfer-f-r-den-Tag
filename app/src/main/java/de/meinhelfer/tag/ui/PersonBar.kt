package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    var showRename by remember { mutableStateOf(false) }
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
                        text = { Text("„${current.name}“ umbenennen …") },
                        onClick = {
                            menu = false
                            showRename = true
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
        NameDialog(
            title = "Neue Person",
            label = "Name",
            initial = "",
            onDismiss = { showAdd = false },
            onSave = {
                vm.addPerson(it)
                showAdd = false
            },
        )
    }
    if (showRename && current != null) {
        NameDialog(
            title = "Person umbenennen",
            label = "Name",
            initial = current.name,
            onDismiss = { showRename = false },
            onSave = {
                vm.renamePerson(current, it)
                showRename = false
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
