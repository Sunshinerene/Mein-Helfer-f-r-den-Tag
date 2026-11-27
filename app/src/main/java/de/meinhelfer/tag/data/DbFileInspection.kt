package de.meinhelfer.tag.data

import java.io.File
import java.io.DataInputStream
import de.meinhelfer.tag.data.DbStartupPolicy.FileState

/** Read-only classification; an encrypted candidate is not proof of encryption. */
internal object DbFileInspection {
    private val header = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
    private val suffixes = listOf("", "-wal", "-shm", "-journal")

    data class Result(val state: FileState, val migrationArtifacts: Boolean)

    fun inspect(file: File): Result {
        return try {
            val artifacts = suffixes.any { suffix ->
                File(file.path + suffix + ".klartext").exists() ||
                    File(file.path + ".neu" + suffix).exists()
            }
            val state = when {
                !file.exists() -> if (suffixes.drop(1).any { File(file.path + it).exists() }) {
                    FileState.Uncertain
                } else FileState.Missing
                !file.isFile || file.length() < header.size -> FileState.Uncertain
                else -> {
                    val bytes = ByteArray(header.size)
                    DataInputStream(file.inputStream()).use { it.readFully(bytes) }
                    if (bytes.contentEquals(header)) FileState.Plaintext
                    else FileState.EncryptedCandidate
                }
            }
            Result(state, artifacts)
        } catch (_: Exception) {
            Result(FileState.Uncertain, true)
        }
    }
}
