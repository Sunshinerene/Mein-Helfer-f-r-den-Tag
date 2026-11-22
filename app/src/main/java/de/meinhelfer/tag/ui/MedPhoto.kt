package de.meinhelfer.tag.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.info.FreeImage
import de.meinhelfer.tag.info.FreeImages
import de.meinhelfer.tag.media.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

/** Platzhalter, wenn ein Medikament noch kein Foto hat: Antippen fuegt eins hinzu. */
@Composable
fun AddPhotoThumb(size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📷", fontSize = 20.sp)
            Text("Foto", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Foto fuer ein Medikament: fotografieren, aus der Galerie, freie Bilder (Wikimedia Commons) oder im Internet suchen.
 * [queueText] z. B. "2 von 7" beim Ergaenzen mehrerer Fotos; [onSkip] springt zum naechsten.
 */
@Composable
fun MedPhotoDialog(
    med: Medication,
    vm: MainViewModel,
    onDone: () -> Unit,
    queueText: String? = null,
    onSkip: (() -> Unit)? = null,
    onSaved: () -> Unit = onDone,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showFree by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun save(file: File, credit: String) {
        scope.launch {
            busy = true
            val ok = PhotoStore.normalize(file) != null
            busy = false
            if (!ok) {
                file.delete()
                message = "Das Bild konnte nicht gelesen werden."
                return@launch
            }
            val old = med.photoPath
            vm.update(med.copy(photoPath = file.name, photoCredit = credit))
            if (old != null && old != file.name) PhotoStore.delete(context, old)
            onSaved()
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPath?.let { File(it) }
        pendingPath = null
        if (file == null) return@rememberLauncherForActivityResult
        if (ok) save(file, "") else file.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val file = PhotoStore.newFile(context)
            scope.launch {
                val copied = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                        file.length() > 0
                    }.getOrDefault(false)
                }
                if (copied) save(file, "") else {
                    file.delete()
                    message = "Das Bild konnte nicht übernommen werden."
                }
            }
        }
    }

    if (showFree) {
        FreeImageDialog(
            term = FreeImages.searchTerm(med.name),
            onPick = { img ->
                showFree = false
                val file = PhotoStore.newFile(context)
                scope.launch {
                    busy = true
                    val ok = FreeImages.download(img, file)
                    busy = false
                    if (ok) save(file, img.credit) else message = "Das Bild konnte nicht geladen werden."
                }
            },
            onClose = { showFree = false },
        )
        return
    }

    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onDone,
        title = { Text("Foto für " + med.name) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                queueText?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                med.photoPath?.let { p ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhotoThumb(p, 72.dp)
                        Column {
                            if (med.photoCredit.isNotBlank()) Text("Bild: " + med.photoCredit, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                vm.update(med.copy(photoPath = null, photoCredit = ""))
                                PhotoStore.delete(context, p)
                                onDone()
                            }) { Text("Foto entfernen") }
                        }
                    }
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Bild wird gespeichert …", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(shape = buttonShape(), enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    val file = PhotoStore.newFile(context)
                    pendingPath = file.path
                    try {
                        camera.launch(PhotoStore.uriForCamera(context, file))
                    } catch (_: Exception) {
                        file.delete()
                        pendingPath = null
                    }
                }) { Text("📷 Packung fotografieren") }
                OutlinedButton(shape = buttonShape(), enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("🖼 Aus der Galerie wählen") }
                OutlinedButton(shape = buttonShape(), enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { showFree = true }) {
                    Text("🌐 Freie Bilder suchen (Wikimedia)")
                }
                TextButton(onClick = {
                    val q = URLEncoder.encode(FreeImages.searchTerm(med.name) + " " + med.dosage.substringBefore("·").trim() + " Packung", "UTF-8")
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?tbm=isch&q=$q")))
                    } catch (_: Exception) {
                    }
                }) { Text("Im Internet nach Packungsbildern suchen") }
                Text(
                    "Fotos bleiben nur in der App. Freie Bilder kommen von Wikimedia Commons, Urheber und Lizenz werden gespeichert; dabei wird nur der Medikamentenname gesendet. " +
                        "Bei der Internetsuche öffnet sich der Browser: Bild dort für dich speichern und dann „Aus der Galerie wählen“. Packungsfotos aus dem Internet sind meist urheberrechtlich geschützt, also bitte nur für dich verwenden.",
                    style = MaterialTheme.typography.bodySmall,
                )
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Row {
                if (onSkip != null) {
                    TextButton(onClick = onDone) { Text("Fertig") }
                    TextButton(onClick = onSkip) { Text("Überspringen") }
                } else {
                    TextButton(onClick = onDone) { Text("Schließen") }
                }
            }
        },
    )
}

/** Auswahl freier Bilder von Wikimedia Commons mit Urheber und Lizenz. */
@Composable
private fun FreeImageDialog(term: String, onPick: (FreeImage) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf(term) }
    var searched by remember { mutableStateOf(term) }
    val results by produceState<List<FreeImage>?>(initialValue = null, searched) {
        value = null
        value = runCatching { FreeImages.search(searched) }.getOrDefault(emptyList())
    }
    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = onClose,
        title = { Text("Freie Bilder") },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = { query = it.take(60) }, singleLine = true, label = { Text("Suchbegriff") }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { searched = query.trim() }, enabled = query.isNotBlank()) { Text("Suchen") }
                }
                Text("Von Wikimedia Commons. Oft gibt es nur Bilder zum Wirkstoff; dann hilft ein kürzerer Suchbegriff.", style = MaterialTheme.typography.bodySmall)
                when (val list = results) {
                    null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Suche …", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> if (list.isEmpty()) {
                        Text("Keine freien Fotos gefunden. Versuche den Wirkstoff (z. B. „Atorvastatin“) oder fotografiere die Packung.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        list.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { img -> FreeImageTile(img, Modifier.weight(1f)) { onPick(img) } }
                                if (pair.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Zurück") } },
    )
}

@Composable
private fun FreeImageTile(img: FreeImage, modifier: Modifier, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, img.thumbUrl) {
        value = FreeImages.thumbnail(img.thumbUrl)?.asImageBitmap()
    }
    Column(modifier.clickable { onClick() }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 110.dp, max = 110.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val b = bitmap
            if (b != null) {
                Image(b, contentDescription = img.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 110.dp))
            } else {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        Text(img.credit, style = MaterialTheme.typography.labelSmall, maxLines = 2, modifier = Modifier.padding(horizontal = 2.dp))
    }
}
