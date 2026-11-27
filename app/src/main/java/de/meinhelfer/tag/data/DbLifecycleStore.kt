package de.meinhelfer.tag.data

import android.util.AtomicFile
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
        decode(atomic.openRead().use { it.readBytes() })
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
        internal fun decode(bytes: ByteArray): State =
            State.entries.firstOrNull { it.name.toByteArray(Charsets.UTF_8).contentEquals(bytes) }
                ?: State.RECOVERY_REQUIRED
    }
}
