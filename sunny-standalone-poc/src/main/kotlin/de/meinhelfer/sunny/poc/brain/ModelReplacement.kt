package de.meinhelfer.sunny.poc.brain

import java.io.File
import java.io.IOException

/** Caller must hold the process-wide model lease throughout the transaction. */
internal object ModelReplacement {
    suspend fun install(
        directory: File,
        model: File,
        isQuarantined: () -> Boolean,
        prepare: suspend (File) -> Long,
        checkpoint: () -> Unit,
        replace: (File, File) -> Unit,
    ): Long {
        require(model.parentFile == directory)
        val staged = File.createTempFile("import-", ".litertlm", directory)
        try {
            val size = prepare(staged)
            if (size <= 0L || staged.length() != size) throw IOException("Unvollständiges Modell.")
            checkpoint()
            if (isQuarantined()) throw IOException("Modellzugriff gesperrt.")
            replace(staged, model)
            return size
        } finally {
            // Never remove a file potentially still owned by native code.
            if (!isQuarantined()) staged.delete()
        }
    }

    fun delete(model: File): Boolean = !model.exists() || model.delete()
}
