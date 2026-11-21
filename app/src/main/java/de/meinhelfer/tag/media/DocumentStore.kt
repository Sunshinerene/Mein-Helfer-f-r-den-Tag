package de.meinhelfer.tag.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class DocKind { IMAGES, PDF, TEXT, OTHER }

/** Ein neues Dokument vor dem Speichern: Dateien im Zwischenordner plus der erkannte Text. */
data class PendingDoc(
    val files: List<File>,
    val kind: DocKind,
    val mime: String,
    val ext: String,
    val text: String,
    val displayName: String,
)

/**
 * Dokumente liegen verschluesselt (AES-256, Schluessel im Android Keystore) im privaten App-Ordner.
 * Zum Anzeigen oder Teilen entsteht kurz eine entschluesselte Kopie im Cache, die beim App-Start geloescht wird.
 */
object DocumentStore {
    private fun dir(context: Context): File = File(context.filesDir, "docs_enc").apply { mkdirs() }
    private fun tmpDir(context: Context): File = File(context.cacheDir, "docs").apply { mkdirs() }

    fun newTempFile(context: Context, ext: String): File =
        File(tmpDir(context), "tmp_${System.currentTimeMillis()}_${(0..9999).random()}.$ext")

    fun clearTemp(context: Context) {
        tmpDir(context).listFiles()?.forEach { it.delete() }
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun encrypted(context: Context, file: File): EncryptedFile {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedFile.Builder(context, file, master, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
    }

    /** Verschluesselt [source] in den App-Ordner und gibt den Dateinamen zurueck. */
    suspend fun store(context: Context, source: File): String = withContext(Dispatchers.IO) {
        val name = "doc_${System.currentTimeMillis()}_${(0..9999).random()}.enc"
        val target = File(dir(context), name)
        encrypted(context, target).openFileOutput().use { out -> source.inputStream().use { it.copyTo(out) } }
        name
    }

    /** Entschluesselte Kopie im Cache (fuer Oeffnen/Teilen). */
    suspend fun decryptToCache(context: Context, name: String, displayName: String): File = withContext(Dispatchers.IO) {
        val out = File(tmpDir(context), safeName(displayName))
        out.delete()
        encrypted(context, File(dir(context), name)).openFileInput().use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out
    }

    fun delete(context: Context, name: String) {
        File(dir(context), name).delete()
    }

    fun safeName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").trim('_').ifBlank { "Dokument" }.take(60)

    fun extensionFor(mime: String): String = when {
        mime == "application/pdf" -> "pdf"
        mime.startsWith("image/") -> "jpg"
        mime.startsWith("text/") -> "txt"
        mime.contains("wordprocessingml") -> "docx"
        mime.contains("opendocument.text") -> "odt"
        mime == "application/msword" -> "doc"
        else -> "bin"
    }

    /** Speichert ein fertiges Dokument. Mehrere Fotos werden zu einer PDF zusammengefasst. Gibt (Dateiname, Mime) zurueck. */
    suspend fun commit(context: Context, pending: PendingDoc): Pair<String, String> = withContext(Dispatchers.IO) {
        val result = when {
            pending.kind == DocKind.IMAGES && pending.files.size > 1 -> {
                val pdf = newTempFile(context, "pdf")
                imagesToPdf(pending.files, pdf)
                val name = store(context, pdf)
                pdf.delete()
                name to "application/pdf"
            }
            else -> store(context, pending.files.first()) to pending.mime
        }
        discard(pending)
        result
    }

    fun discard(pending: PendingDoc) {
        pending.files.forEach { it.delete() }
    }

    private fun imagesToPdf(images: List<File>, target: File) {
        val doc = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        images.forEachIndexed { index, file ->
            val bitmap = PhotoStore.decodeSampled(file, 1600) ?: return@forEachIndexed
            val width = 595
            val height = (width.toFloat() * bitmap.height / bitmap.width).toInt().coerceAtLeast(1)
            val page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
            page.canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), paint)
            doc.finishPage(page)
            bitmap.recycle()
        }
        target.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}

/** Liest Text aus Fotos, PDFs und Textdateien, alles direkt auf dem Handy. */
object DocumentReader {
    private const val MAX_PDF_PAGES = 12

    /** Fotos (bereits im Zwischenordner): verkleinern, richtig drehen und Text erkennen. */
    suspend fun fromImages(files: List<File>, previousText: String = ""): PendingDoc {
        val texts = mutableListOf<String>()
        if (previousText.isNotBlank()) texts.add(previousText)
        files.forEach { file ->
            val bitmap = withContext(Dispatchers.IO) {
                PhotoStore.decodeSampled(file, 1800)?.also { bmp ->
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                }
            }
            if (bitmap != null) {
                runCatching { recognize(bitmap) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            }
        }
        return PendingDoc(files, DocKind.IMAGES, "image/jpeg", "jpg", texts.joinToString("\n\n"), "Dokument")
    }

    /** Eine neue Seite zu einem Foto-Dokument hinzufuegen. */
    suspend fun appendImage(pending: PendingDoc, file: File): PendingDoc {
        val added = fromImages(listOf(file))
        return pending.copy(files = pending.files + file, text = listOf(pending.text, added.text).filter { it.isNotBlank() }.joinToString("\n\n"))
    }

    /** Datei aus dem Dateiauswahl-Dialog (PDF, Bild, Text, sonstiges). */
    suspend fun fromUri(context: Context, uri: Uri): PendingDoc {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = withContext(Dispatchers.IO) {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } ?: "Dokument"
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 } ?: DocumentStore.extensionFor(mime)
        val temp = DocumentStore.newTempFile(context, ext)
        withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
        }
        val base = name.substringBeforeLast('.')
        return when {
            mime.startsWith("image/") -> fromImages(listOf(temp)).copy(displayName = base)
            mime == "application/pdf" || ext == "pdf" -> {
                PendingDoc(listOf(temp), DocKind.PDF, "application/pdf", "pdf", readPdf(temp), base)
            }
            mime.startsWith("text/") || ext == "txt" -> {
                val text = withContext(Dispatchers.IO) { temp.readText(Charsets.UTF_8).take(200_000) }
                PendingDoc(listOf(temp), DocKind.TEXT, "text/plain", "txt", text, base)
            }
            ext == "docx" || mime.contains("wordprocessingml") -> PendingDoc(
                listOf(temp), DocKind.OTHER, DOCX_MIME, "docx", readOffice(temp, "word/document.xml"), base,
            )
            ext == "odt" || mime.contains("opendocument.text") -> PendingDoc(
                listOf(temp), DocKind.OTHER, ODT_MIME, "odt", readOffice(temp, "content.xml"), base,
            )
            else -> PendingDoc(listOf(temp), DocKind.OTHER, mime, ext, "", base)
        }
    }

    private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val ODT_MIME = "application/vnd.oasis.opendocument.text"

    /** Text aus Word- (docx) oder LibreOffice-Dateien (odt): das sind ZIP-Dateien mit XML darin. */
    private suspend fun readOffice(file: File, entry: String): String = withContext(Dispatchers.IO) {
        try {
            java.util.zip.ZipFile(file).use { zip ->
                val item = zip.getEntry(entry) ?: return@use ""
                val xml = zip.getInputStream(item).bufferedReader(Charsets.UTF_8).use { it.readText() }
                xml
                    .replace(Regex("</w:p>|</text:p>|</text:h>|<w:br/>|<w:tab/>|<text:line-break/>"), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&quot;", "\"").replace("&apos;", "'")
                    .lines().joinToString("\n") { it.trim() }
                    .replace(Regex("\n{3,}"), "\n\n")
                    .trim()
                    .take(200_000)
            }
        } catch (_: Exception) {
            ""
        }
    }

    /** Text einer bereits gespeicherten (entschluesselten) Datei noch einmal auslesen. */
    suspend fun extractText(file: File, mime: String): String = when {
        mime == "application/pdf" -> readPdf(file)
        mime.startsWith("image/") -> {
            val bitmap = withContext(Dispatchers.IO) { PhotoStore.decodeSampled(file, 1800) }
            if (bitmap != null) runCatching { recognize(bitmap) }.getOrDefault("") else ""
        }
        mime.startsWith("text/") -> withContext(Dispatchers.IO) { file.readText(Charsets.UTF_8).take(200_000) }
        mime.contains("wordprocessingml") -> readOffice(file, "word/document.xml")
        mime.contains("opendocument.text") -> readOffice(file, "content.xml")
        else -> ""
    }

    fun canExtract(mime: String): Boolean =
        mime == "application/pdf" || mime.startsWith("image/") || mime.startsWith("text/") ||
            mime.contains("wordprocessingml") || mime.contains("opendocument.text")

    private suspend fun readPdf(file: File): String = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val texts = mutableListOf<String>()
                    val pages = minOf(renderer.pageCount, MAX_PDF_PAGES)
                    for (i in 0 until pages) {
                        renderer.openPage(i).use { page ->
                            val width = 1500
                            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            runCatching { recognize(bitmap) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
                            bitmap.recycle()
                        }
                    }
                    if (renderer.pageCount > pages) texts.add("[Es wurden nur die ersten $pages Seiten gelesen.]")
                    texts.joinToString("\n\n")
                }
            }
        } catch (_: Exception) {
            // z. B. passwortgeschuetzte PDF
            ""
        }
    }

    private suspend fun recognize(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return suspendCancellableCoroutine { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { continuation.resume(it.text) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
        } finally {
            recognizer.close()
        }
    }
}
