package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.CaregiverProfile
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.data.Todo
import de.meinhelfer.tag.letters.SignedCopies
import de.meinhelfer.tag.media.DocumentStore
import kotlinx.coroutines.launch
import java.io.File

/**
 * Oeffnet die E-Mail-App mit Empfaenger, Betreff, Text und beliebig vielen Anhaengen.
 * Angeboten werden nur E-Mail-Apps; gesendet wird erst dort.
 */
internal fun openMailWithFiles(context: Context, to: String?, subject: String, text: String, files: List<File>) {
    val mailto = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
    val intent = when {
        files.isEmpty() -> mailto
        files.size == 1 -> {
            val uri = DocumentStore.uriFor(context, files[0])
            Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selector = mailto
            }
        }
        else -> {
            val uris = ArrayList(files.map { DocumentStore.uriFor(context, it) })
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                clipData = ClipData.newRawUri("", uris[0]).also { c -> uris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selector = mailto
            }
        }
    }
    intent.apply {
        to?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it.trim())) }
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Keine E-Mail-App gefunden.", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Die E-Mail-App konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show()
    }
}

/** Gespeichertes Dokument kurz entschluesselt als Datei mit lesbarem Namen (fuer Anhang oder Druck). */
internal suspend fun documentFile(context: Context, d: Document): File =
    DocumentStore.decryptToCache(context, d.fileName, DocumentStore.safeName(SignedCopies.baseTitle(d)) + "." + DocumentStore.extensionFor(d.mimeType))

/** Betreff und Text aus einem Entwurf aus „Schreiben und Vollmachten“. */
private fun letterParts(doc: Document, sender: String): Pair<String, String> {
    val parts = doc.text.split("\n\n", limit = 2)
    val subject = parts.firstOrNull()?.trim().orEmpty().ifBlank { SignedCopies.baseTitle(doc) }
    val rest = parts.getOrNull(1)?.trim().orEmpty()
    val text = if (rest.contains("Mit freundlichen Grüßen")) rest else letterMailText(subject, "", "", emptyList(), emptyList(), true, sender)
    return subject to text
}

private fun signature(c: CaregiverProfile): String =
    listOfNotNull(c.name.ifBlank { null }, c.phone.ifBlank { null }?.let { "Tel. $it" }).joinToString("\n")

/**
 * E-Mail zu einer Aufgabe: Empfaenger aus dem Kontakt, Text aus dem verknuepften Schreiben (falls vorhanden),
 * Anhaenge frei waehlbar aus allen Dokumenten (verknuepfte sind vorausgewaehlt).
 */
@Composable
internal fun TaskMailDialog(
    task: Todo,
    doctor: Doctor?,
    documents: List<Document>,
    caregiver: CaregiverProfile,
    onLink: (List<Long>) -> Unit,
    onTopicMail: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attachedIds = task.attachedIds()
    val linked = documents.filter { it.id in attachedIds }
    val letter = linked.firstOrNull { isLetterDraft(it) }
    val initial = letter?.let { letterParts(it, caregiver.name) }
    var to by remember { mutableStateOf(doctor?.email.orEmpty()) }
    var subject by remember { mutableStateOf(initial?.first ?: task.text.lines().first().take(120)) }
    var body by remember {
        mutableStateOf(
            initial?.second ?: ("Sehr geehrte Damen und Herren,\n\n" + task.text.trim() + "\n\nMit freundlichen Grüßen\n" + signature(caregiver)).trimEnd(),
        )
    }
    val selected = remember { mutableStateListOf<Long>().apply { addAll(linked.map { it.id }) } }
    var showOthers by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var link by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val others = documents.filter { it.id !in attachedIds }
        .filter { query.isBlank() || (it.title + " " + it.category).contains(query.trim(), ignoreCase = true) }
        .sortedByDescending { it.docDate ?: 0L }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("E-Mail zur Aufgabe") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text("An (E-Mail-Adresse)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (to.isBlank()) {
                    Text("Keine Adresse hinterlegt. Du kannst sie hier oder später in der E-Mail-App eintragen.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Betreff") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Text – bitte prüfen") }, minLines = 6, modifier = Modifier.fillMaxWidth())
                if (onTopicMail != null && letter == null) {
                    TextButton(onClick = onTopicMail) { Text("Stattdessen Vorlage nach Thema (Rezept, Termin …)") }
                }
                Text("Anhänge (${selected.size})", style = MaterialTheme.typography.titleSmall)
                if (linked.isNotEmpty()) {
                    Text("Mit der Aufgabe verknüpft", style = MaterialTheme.typography.labelLarge)
                    linked.forEach { d -> DocCheck(d, d.id in selected) { if (d.id in selected) selected.remove(d.id) else selected.add(d.id) } }
                }
                if (documents.size > linked.size) {
                    TextButton(onClick = { showOthers = !showOthers }) {
                        Text(if (showOthers) "Weitere Dokumente ausblenden" else "Weitere Dokumente anhängen (${documents.size - linked.size})")
                    }
                    if (showOthers) {
                        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Suchen") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        others.take(60).forEach { d -> DocCheck(d, d.id in selected) { if (d.id in selected) selected.remove(d.id) else selected.add(d.id) } }
                    }
                }
                if (documents.isEmpty()) Text("Noch keine Dokumente gespeichert.", style = MaterialTheme.typography.bodySmall)
                if (selected.any { it !in attachedIds }) {
                    Row(Modifier.fillMaxWidth().clickable { link = !link }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = link, onCheckedChange = { link = it })
                        Text("Neu gewählte Anhänge auch mit der Aufgabe verknüpfen", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (letter != null && (letter.text.contains("Unterschrift") || letter.category.contains("Vollmacht"))) {
                    Text(
                        "Wo eine Unterschrift nötig ist, lieber die unterschriebene, eingescannte Fassung anhängen oder per Post schicken.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "Normale E-Mails sind nicht verschlüsselt. Nur das Nötige anhängen und nur an vertrauenswürdige Adressen. Gesendet wird erst in deiner E-Mail-App.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val chosen = documents.filter { it.id in selected }
                    val files = chosen.mapNotNull { d -> runCatching { documentFile(context, d) }.getOrNull() }
                    if (files.size < chosen.size) Toast.makeText(context, "Nicht alle Anhänge konnten vorbereitet werden.", Toast.LENGTH_LONG).show()
                    if (link) {
                        val newIds = (attachedIds + chosen.map { it.id }).distinct()
                        if (newIds.size != attachedIds.size) onLink(newIds)
                    }
                    openMailWithFiles(context, to, subject, body, files)
                    busy = false
                    onClose()
                }
            }) { Text(if (busy) "Bereite vor …" else "In E-Mail-App öffnen") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Abbrechen") } },
    )
}

@Composable
private fun DocCheck(d: Document, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(d.title, style = MaterialTheme.typography.bodyMedium)
            Text(listOfNotNull(d.category, d.dateText(), DocumentStore.extensionFor(d.mimeType).uppercase()).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Dokument mit passender Art an eine andere App geben (Messenger, Drucker-App, Speichern). */
internal fun shareDocumentFile(context: Context, file: File, mime: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, DocumentStore.uriFor(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Teilen"))
    } catch (_: Exception) {
        Toast.makeText(context, "Teilen ist nicht möglich.", Toast.LENGTH_SHORT).show()
    }
}

/** Verknuepfte Unterlagen einer Aufgabe drucken, teilen oder ansehen. */
@Composable
internal fun TaskPrintDialog(task: Todo, documents: List<Document>, onView: (Document) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val linked = documents.filter { it.id in task.attachedIds() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Unterlagen drucken") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (linked.isEmpty()) Text("Mit dieser Aufgabe ist noch nichts verknüpft. Über „Unterlagen“ kannst du Dokumente auswählen.", style = MaterialTheme.typography.bodyMedium)
                linked.forEach { d ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(d.title, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (canPrint(d)) {
                                OutlinedButton(shape = buttonShape(), onClick = {
                                    scope.launch {
                                        val ok = runCatching { printDocument(context, d, documentFile(context, d)) }.getOrDefault(false)
                                        if (!ok) Toast.makeText(context, "Drucken ist bei diesem Dokument nicht möglich.", Toast.LENGTH_LONG).show()
                                    }
                                }) { Text("Drucken") }
                            }
                            OutlinedButton(shape = buttonShape(), onClick = {
                                scope.launch {
                                    runCatching { documentFile(context, d) }.getOrNull()?.let { f -> shareDocumentFile(context, f, d.mimeType) }
                                }
                            }) { Text("Teilen") }
                            TextButton(onClick = { onView(d) }) { Text("Ansehen") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } },
    )
}
