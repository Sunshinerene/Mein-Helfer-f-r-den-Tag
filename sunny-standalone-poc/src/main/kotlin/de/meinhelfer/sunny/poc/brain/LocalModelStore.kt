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
                val coroutine = currentCoroutineContext()
                val source = app.contentResolver.openInputStream(uri)
                    ?: throw IOException("Datei nicht lesbar.")
                val total = source.use { input ->
                    FileOutputStream(staged).use { output ->
                        ModelImportChecks.copy(input, output, { directory.usableSpace }, { coroutine.ensureActive() })
                            .also { output.fd.sync() }
                    }
                }
                ModelImportChecks.validateHeader(staged)
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

}
