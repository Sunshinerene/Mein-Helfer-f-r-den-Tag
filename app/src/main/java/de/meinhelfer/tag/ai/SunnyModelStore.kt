package de.meinhelfer.tag.ai

import android.content.Context
import android.net.Uri
import de.meinhelfer.sunny.runtime.android.LegacyLiteRt
import de.meinhelfer.sunny.runtime.android.ModelAccess
import de.meinhelfer.sunny.runtime.android.ModelAccessException
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Stores an optional model privately. Import does not imply runtime compatibility. */
object SunnyModelStore {
    private class ImportFailure(message: String) : IOException(message)

    private fun directory(context: Context) = File(context.noBackupFilesDir, "sunny_models")
    fun model(context: Context): File = File(directory(context), "model.litertlm")
    fun installedBytes(context: Context): Long = model(context).takeIf { it.isFile }?.length() ?: 0L

    suspend fun importModel(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        ModelAccess.process.withExclusive { lease ->
            val dir = directory(context)
            if (!dir.isDirectory && !dir.mkdirs()) throw ImportFailure("Modellordner nicht verfügbar.")
            val temp = File(dir, "import.litertlm")
            try {
                val reserve = 256L * 1024 * 1024
                val limit = 4L * 1024 * 1024 * 1024
                var total = 0L
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw ImportFailure("Datei nicht lesbar.")
                input.use { source ->
                    temp.outputStream().use { target ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = source.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > limit || dir.usableSpace < reserve + n)
                                throw ImportFailure("Nicht genügend Speicher oder Modell zu groß.")
                            target.write(buffer, 0, n)
                        }
                    }
                }
                if (total == 0L) throw ImportFailure("Die Datei ist leer.")
                LegacyLiteRt.probe(lease, temp, context.cacheDir)
                currentCoroutineContext().ensureActive()
                android.system.Os.rename(temp.absolutePath, model(context).absolutePath)
                total
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: InterruptedException) {
                throw failure
            } catch (failure: ModelAccessException) {
                throw failure
            } catch (failure: ImportFailure) {
                throw failure
            } catch (_: Exception) {
                throw ImportFailure("Datei konnte nicht importiert werden.")
            } finally {
                if (!lease.isQuarantined) temp.delete()
            }
        }
    }

    suspend fun delete(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            ModelAccess.process.withExclusive {
                val file = model(context)
                !file.exists() || file.delete()
            }
        } catch (_: ModelAccessException) {
            false
        }
    }
}
