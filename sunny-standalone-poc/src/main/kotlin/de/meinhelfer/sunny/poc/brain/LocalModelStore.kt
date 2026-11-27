package de.meinhelfer.sunny.poc.brain

import android.content.Context
import android.net.Uri
import android.system.Os
import de.meinhelfer.sunny.runtime.android.LegacyLiteRt
import de.meinhelfer.sunny.runtime.android.ModelAccess
import de.meinhelfer.sunny.runtime.android.ModelAccessException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Standalone-only model storage. Never uses the MeinHelfer data directory. */
internal class LocalModelStore(context: Context) {
    private val app = context.applicationContext
    private val directory = File(app.noBackupFilesDir, "sunny_models")
    val model = File(directory, "model.litertlm")
    val cache = File(app.cacheDir, "sunny_model_runtime")

    suspend fun importModel(uri: Uri): Long = withContext(Dispatchers.IO) {
        ModelAccess.process.withExclusive { lease ->
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Modellordner nicht verfügbar.")
            if (!cache.isDirectory && !cache.mkdirs()) throw IOException("Modellcache nicht verfügbar.")
            val staged = File.createTempFile("import-", ".litertlm", directory)
            try {
                var total = 0L
                val source = app.contentResolver.openInputStream(uri)
                    ?: throw IOException("Datei nicht lesbar.")
                source.use { input ->
                    FileOutputStream(staged).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) throw IOException("Datei konnte nicht vollständig gelesen werden.")
                            total += count
                            if (total > LIMIT || directory.usableSpace < RESERVE + count) {
                                throw IOException("Nicht genügend Speicher oder Modell zu groß.")
                            }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                if (total == 0L) throw IOException("Die Datei ist leer.")
                // Reject malformed headers before invoking native code.
                java.io.RandomAccessFile(staged, "r").use { input ->
                    if (input.length() < 33) throw IOException("Ungültiges Modellformat.")
                    val magic = ByteArray(8).also(input::readFully)
                    val major = Integer.reverseBytes(input.readInt())
                    input.seek(24)
                    val headerEnd = java.lang.Long.reverseBytes(input.readLong())
                    if (!magic.contentEquals("LITERTLM".toByteArray(Charsets.US_ASCII)) ||
                        major != 1 || headerEnd !in 33..16_384 || headerEnd >= input.length()) {
                        throw IOException("Ungültiges Modellformat.")
                    }
                }
                LegacyLiteRt.probe(lease, staged, cache)
                currentCoroutineContext().ensureActive()
                Os.rename(staged.absolutePath, model.absolutePath)
                total
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: InterruptedException) {
                throw failure
            } catch (failure: ModelAccessException) {
                throw failure
            } catch (_: Exception) {
                throw IOException("Modellimport fehlgeschlagen. Vorhandenes Modell bleibt erhalten.")
            } finally {
                if (!lease.isQuarantined) staged.delete()
            }
        }
    }

    suspend fun delete(): Boolean = withContext(Dispatchers.IO) {
        ModelAccess.process.withExclusive {
            !model.exists() || model.delete()
        }
    }

    private companion object {
        const val RESERVE = 256L * 1024 * 1024
        const val LIMIT = 4L * 1024 * 1024 * 1024
    }
}
