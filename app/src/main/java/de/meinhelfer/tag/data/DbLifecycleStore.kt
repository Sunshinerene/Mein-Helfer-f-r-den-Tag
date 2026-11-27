package de.meinhelfer.tag.data

import android.util.AtomicFile
import android.content.Context
import java.io.File
import java.io.FileNotFoundException

/** Store outside the database and backup payload. Missing is not authorization. */
internal class DbLifecycleStore(file: File) {
    enum class State {
        FIRST_RUN, INITIALIZED, RESET_PENDING, RESET_COMPLETED, RECOVERY_REQUIRED
    }

    private val atomic = AtomicFile(file)

    @Synchronized
    fun read(): State? = try {
        if (File(atomic.baseFile.path + ".bak").exists() ||
            File(atomic.baseFile.path + ".new").exists()) {
            State.RECOVERY_REQUIRED
        } else {
            // Do not let AtomicFile.openRead silently recover an older authorization.
            atomic.baseFile.inputStream().use { input ->
                val bytes = ByteArray(64)
                var count = 0
                while (count < bytes.size) {
                    val n = input.read(bytes, count, bytes.size - count)
                    if (n < 0) break
                    count += n
                }
                if (count == bytes.size) State.RECOVERY_REQUIRED
                else decode(bytes.copyOf(count))
            }
        }
    } catch (_: FileNotFoundException) {
        if (atomic.baseFile.exists() ||
            File(atomic.baseFile.path + ".bak").exists() ||
            File(atomic.baseFile.path + ".new").exists()) State.RECOVERY_REQUIRED else null
    } catch (_: Exception) {
        State.RECOVERY_REQUIRED
    }

    /** Caller must verify authorization and serialize lifecycle operations process-wide. */
    @Synchronized
    fun write(state: State) {
        val stream = atomic.startWrite()
        try {
            stream.write(state.name.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (e: Throwable) {
            atomic.failWrite(stream)
            throw e
        }
        check(read() == state) { "Database lifecycle status not persisted" }
    }

    companion object {
        internal fun forApp(context: Context): DbLifecycleStore =
            DbLifecycleStore(File(context.noBackupFilesDir, "db_lifecycle_v1"))

        internal fun decode(bytes: ByteArray): State =
            State.entries.firstOrNull { it.name.toByteArray(Charsets.UTF_8).contentEquals(bytes) }
                ?: State.RECOVERY_REQUIRED
    }
}
