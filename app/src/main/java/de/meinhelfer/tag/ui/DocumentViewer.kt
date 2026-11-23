package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.export.PdfPages
import de.meinhelfer.tag.export.PdfPrintAdapter
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import de.meinhelfer.tag.media.DocumentStore
import de.meinhelfer.tag.media.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Kann der eingebaute Betrachter dieses Dokument anzeigen? */
internal fun canViewInside(doc: Document): Boolean =
    doc.mimeType == "application/pdf" || doc.mimeType.startsWith("image/") || doc.mimeType.startsWith("text/")

/** Kann dieses Dokument gedruckt werden (PDF, Fotos und Text)? */
internal fun canPrint(doc: Document): Boolean =
    doc.mimeType == "application/pdf" || doc.mimeType.startsWith("image/") || doc.mimeType.startsWith("text/")

/** Text-Dokument als A4-PDF (Titel oben, Zeilenumbruch, Seitenwechsel). */
private fun textToPdf(title: String, text: String, target: File): File {
    val pdf = PdfPages(595, 842, margin = 50f, footer = title.take(80))
    pdf.newPage()
    pdf.text(title, 14f, bold = true)
    pdf.gap(10f)
    text.take(400_000).replace("\r\n", "\n").split('\n').forEach { line ->
        if (line.isBlank()) {
            pdf.gap(6f)
        } else {
            // Sehr lange Zeilen in Stuecke teilen, damit nichts ueber den Seitenrand laeuft
            line.chunked(1500).forEach { pdf.text(it, 10.5f) }
        }
    }
    return pdf.save(target)
}

/** Gibt das Dokument an den Druckdienst des Handys. Fotos werden dafuer kurz in eine PDF umgewandelt. */
internal suspend fun printDocument(context: Context, doc: Document, file: File): Boolean {
    val pdf = when {
        doc.mimeType == "application/pdf" -> file
        doc.mimeType.startsWith("image/") -> withContext(Dispatchers.IO) {
            DocumentStore.newTempFile(context, "pdf").also { DocumentStore.imagesToPdf(listOf(file), it) }
        }
        doc.mimeType.startsWith("text/") -> withContext(Dispatchers.IO) {
            textToPdf(doc.title, file.readText(), DocumentStore.newTempFile(context, "pdf"))
        }
        else -> return false
    }
    val name = DocumentStore.safeName(doc.title)
    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    pm.print(name, PdfPrintAdapter(pdf, name), PrintAttributes.Builder().build())
    return true
}

private const val MAX_ZOOM = 4f

/**
 * Zoom mit zwei Fingern. Reagiert nur, wenn zwei Finger aufliegen; mit einem Finger wird wie gewohnt geblaettert.
 * Laeuft im ersten Durchgang, damit das Blaettern die Zoom-Bewegung nicht uebernimmt.
 */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val z = event.calculateZoom()
                if (z != 1f) onZoom(z)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

/** Vollbild-Betrachter fuer PDF, Fotos und Text. Mit Zoom, Drucken, Teilen und "in anderer App oeffnen". */
@Composable
internal fun DocumentViewer(doc: Document, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val ext = DocumentStore.extensionFor(doc.mimeType)

    LaunchedEffect(doc.fileName) {
        try {
            file = DocumentStore.decryptToCache(context, doc.fileName, DocumentStore.safeName(doc.title) + "." + ext)
        } catch (_: Exception) {
            failed = true
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DesignSurface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Schließen") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().pinchToZoom { zoom = (zoom * it).coerceIn(1f, MAX_ZOOM) }) {
                    val f = file
                    when {
                        failed -> Text("Die Datei konnte nicht geöffnet werden.", Modifier.padding(16.dp))
                        f == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        doc.mimeType == "application/pdf" -> PdfBody(f, zoom)
                        doc.mimeType.startsWith("image/") -> ImageBody(f, zoom)
                        doc.mimeType.startsWith("text/") -> {
                            val text = remember(f) { runCatching { f.readText().take(200_000) }.getOrDefault("") }
                            val base = MaterialTheme.typography.bodyMedium
                            Text(
                                text,
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                                style = base.copy(
                                    fontSize = if (base.fontSize.isSpecified) base.fontSize * zoom else base.fontSize,
                                    lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * zoom else base.lineHeight,
                                ),
                            )
                        }
                        else -> Text("Dieses Format kann die App nicht selbst anzeigen. Öffne es mit „Andere App“.", Modifier.padding(16.dp))
                    }
                }
                val ready = file
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (canViewInside(doc)) {
                        OutlinedButton(shape = buttonShape(), enabled = zoom > 1f, onClick = { zoom = (zoom / 1.5f).coerceAtLeast(1f) }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("−") }
                        TextButton(onClick = { zoom = 1f }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text("${(zoom * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(shape = buttonShape(), enabled = zoom < MAX_ZOOM, onClick = { zoom = (zoom * 1.5f).coerceAtMost(MAX_ZOOM) }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("+") }
                    }
                    if (canPrint(doc)) {
                        OutlinedButton(shape = buttonShape(), enabled = ready != null, onClick = {
                            val src = ready ?: return@OutlinedButton
                            scope.launch { if (!runCatching { printDocument(context, doc, src) }.getOrDefault(false)) toast("Drucken ist nicht möglich.") }
                        }) { Text("Drucken") }
                    }
                    OutlinedButton(shape = buttonShape(), enabled = ready != null, onClick = {
                        val src = ready ?: return@OutlinedButton
                        try {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = doc.mimeType
                                putExtra(Intent.EXTRA_STREAM, DocumentStore.uriFor(context, src))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Dokument teilen"))
                        } catch (_: Exception) {
                            toast("Teilen ist nicht möglich.")
                        }
                    }) { Text("Teilen") }
                    OutlinedButton(shape = buttonShape(), enabled = ready != null, onClick = {
                        val src = ready ?: return@OutlinedButton
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(DocumentStore.uriFor(context, src), doc.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                            )
                        } catch (_: ActivityNotFoundException) {
                            toast("Dafür ist keine passende App installiert.")
                        }
                    }) { Text("Andere App") }
                }
            }
        }
    }
}

@Composable
private fun PdfBody(file: File, zoom: Float) {
    val renderer = remember(file) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
    }
    DisposableEffect(renderer) { onDispose { runCatching { renderer?.close() } } }
    if (renderer == null) {
        Text("Die PDF-Datei konnte nicht gelesen werden.", Modifier.padding(16.dp))
        return
    }
    val mutex = remember(renderer) { Mutex() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // Neu gerendert wird nur in Stufen (halbe Schritte), dazwischen wird das Bild nur skaliert
        val renderZoom = kotlin.math.ceil(zoom * 2f) / 2f
        val widthPx = with(density) { (maxWidth * renderZoom).toPx() }.toInt().coerceIn(300, 2200)
        val pageWidth = maxWidth * zoom
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(
                Modifier.width(pageWidth).fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items((0 until renderer.pageCount).toList()) { index ->
                    val bitmap by produceState<Bitmap?>(null, index, widthPx) {
                        value = withContext(Dispatchers.IO) {
                            mutex.withLock {
                                runCatching {
                                    renderer.openPage(index).use { page ->
                                        val h = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                                        val b = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                                        b.eraseColor(android.graphics.Color.WHITE)
                                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        b
                                    }
                                }.getOrNull()
                            }
                        }
                    }
                    val b = bitmap
                    if (b == null) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.414f).background(Color.White), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Image(
                            bitmap = b.asImageBitmap(),
                            contentDescription = "Seite ${index + 1}",
                            modifier = Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height).background(Color.White),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageBody(file: File, zoom: Float) {
    val bitmap by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { PhotoStore.decodeSampled(file, 2400) }.getOrNull() }
    }
    val b = bitmap
    if (b == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val imageWidth = maxWidth * zoom
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Dokument",
                modifier = Modifier.width(imageWidth).aspectRatio(b.width.toFloat() / b.height),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}
