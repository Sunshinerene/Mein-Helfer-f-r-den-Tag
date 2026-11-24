package de.meinhelfer.tag.ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

/** Stores an optional model privately. Import does not imply runtime compatibility. */
object SunnyModelStore {
    private val mutex = Mutex()
    private fun directory(context: Context) = File(context.noBackupFilesDir, "sunny_models")
    fun model(context: Context): File = File(directory(context), "model.task")
    fun installedBytes(context: Context): Long = model(context).takeIf { it.isFile }?.length() ?: 0L

    suspend fun importModel(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dir = directory(context)
            check(dir.isDirectory || dir.mkdirs()) { "Modellordner nicht verfügbar." }
            val temp = File(dir, "import.part")
            try {
                val reserve = 256L * 1024 * 1024
                val limit = 4L * 1024 * 1024 * 1024
                var total = 0L
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Datei nicht lesbar.")
                input.use { source ->
                    temp.outputStream().use { target ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val n = source.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > limit || dir.usableSpace < reserve + n)
                                throw IOException("Nicht genügend Speicher oder Modell zu groß.")
                            target.write(buffer, 0, n)
                        }
                    }
                }
                require(total > 0) { "Die Datei ist leer." }
                android.system.Os.rename(temp.absolutePath, model(context).absolutePath)
                total
            } finally {
                temp.delete()
            }
        }
    }

    suspend fun delete(context: Context): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { val file = model(context); !file.exists() || file.delete() }
    }
}
