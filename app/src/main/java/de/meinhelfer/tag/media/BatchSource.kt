package de.meinhelfer.tag.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import de.meinhelfer.tag.summary.DocGrouping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Eine ausgewählte Datei für den Sammel-Import: noch nicht gelesen, nur Name, Art und Zeit. */
data class PickedFile(val uri: Uri, val name: String, val mime: String, val time: Long?)

/** Mehrere Dateien oder einen ganzen Ordner für den Sammel-Import vorbereiten. Liest nur, ändert nichts am Speicher. */
object BatchSource {
    private val readableExt = setOf("pdf", "jpg", "jpeg", "png", "webp", "heic", "heif", "txt", "docx", "odt")

    /** Dateien, deren Text die App lesen kann (PDF, Foto, Text, Word, LibreOffice). */
    fun readable(name: String, mime: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return mime == "application/pdf" || mime.startsWith("image/") || mime == "text/plain" ||
            mime.contains("wordprocessingml") || mime.contains("opendocument.text") || ext in readableExt
    }

    /** Name, Art und (bei Fotos aus der Fotoauswahl) Aufnahmezeit der ausgewählten Dateien. */
    suspend fun describe(context: Context, uris: List<Uri>): List<PickedFile> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        uris.map { uri ->
            var name: String? = null
            var time: Long? = null
            runCatching {
                resolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (n >= 0) name = c.getString(n)
                        // Nur die Aufnahmezeit aus der Fotoauswahl (Millisekunden). „Zuletzt geändert“ taugt nicht:
                        // kopierte Dateien haben oft alle fast dieselbe Zeit. Kamera-Namen wie PXL_2026… wertet DocGrouping aus.
                        val i = c.getColumnIndex("datetaken")
                        if (i >= 0 && !c.isNull(i)) time = c.getLong(i).takeIf { it > 0 }
                    }
                }
            }
            PickedFile(uri, name ?: "Dokument", runCatching { resolver.getType(uri) }.getOrNull() ?: "", time)
        }
    }

    /** Höchstens so tief in Unterordner und so viele Dateien sammeln (danach nimmt der Import die ersten 30). */
    private const val MAX_DEPTH = 3
    private const val MAX_LISTED = 300

    /**
     * Dateien eines Ordners samt Unterordnern (bis 3 Ebenen), die die App lesen kann. Dateien in Unterordnern heißen
     * „Unterordner/Datei.pdf“. Gibt die Dateien in natürlicher Reihenfolge und die Zahl der übersprungenen Dateien zurück.
     * Wirft eine Ausnahme, wenn der Speicherort (z. B. manche Cloud-Dienste) keine Ordner-Liste liefert.
     */
    suspend fun listFolder(context: Context, tree: Uri): Pair<List<PickedFile>, Int> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val out = mutableListOf<PickedFile>()
        var skipped = 0
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        fun walk(docId: String, prefix: String, depth: Int) {
            val subdirs = mutableListOf<Pair<String, String>>()
            val cursor = resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId), cols, null, null, null)
                ?: if (depth == 0) throw IllegalStateException("Keine Ordner-Liste") else return
            cursor.use { c ->
                while (c.moveToNext() && out.size < MAX_LISTED) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    if (name.startsWith(".")) continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < MAX_DEPTH) subdirs += id to name
                        continue
                    }
                    if (!readable(name, mime)) {
                        skipped++
                        continue
                    }
                    out.add(PickedFile(DocumentsContract.buildDocumentUriUsingTree(tree, id), prefix + name, mime, null))
                }
            }
            subdirs.sortedWith { a, b -> DocGrouping.naturalOrder.compare(a.second, b.second) }.forEach { (id, name) ->
                if (out.size < MAX_LISTED) walk(id, "$prefix$name/", depth + 1)
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), "", 0)
        out.sortedWith { a, b -> DocGrouping.naturalOrder.compare(a.name, b.name) } to skipped
    }
}
