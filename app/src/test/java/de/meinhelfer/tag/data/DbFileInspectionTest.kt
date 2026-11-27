package de.meinhelfer.tag.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import de.meinhelfer.tag.data.DbStartupPolicy.FileState

class DbFileInspectionTest {
    @get:Rule val folder = TemporaryFolder()
    private fun db() = File(folder.root, "mein_helfer.db")

    @Test fun missing() {
        assertEquals(FileState.Missing, DbFileInspection.inspect(db()).state)
        assertFalse(db().exists())
    }
    @Test fun emptyAndShort() {
        val file = db()
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2))) {
            file.writeBytes(bytes)
            assertEquals(FileState.Uncertain, DbFileInspection.inspect(file).state)
            assertArrayEquals(bytes, file.readBytes())
        }
    }
    @Test fun plaintextAndEncryptedCandidate() {
        val file = db()
        val plain = "SQLite format 3\u0000".toByteArray() + ByteArray(64)
        file.writeBytes(plain)
        assertEquals(FileState.Plaintext, DbFileInspection.inspect(file).state)
        assertArrayEquals(plain, file.readBytes())
        val candidate = ByteArray(80) { 42 }
        file.writeBytes(candidate)
        assertEquals(FileState.EncryptedCandidate, DbFileInspection.inspect(file).state)
        assertArrayEquals(candidate, file.readBytes())
    }
    @Test fun orphanSidecars() {
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val sidecar = File(db().path + suffix)
            sidecar.writeText("synthetic")
            assertEquals(FileState.Uncertain, DbFileInspection.inspect(db()).state)
            assertEquals("synthetic", sidecar.readText())
            assertTrue(sidecar.delete())
        }
    }
    @Test fun migrationArtifacts() {
        for (suffix in listOf(".klartext", "-wal.klartext", "-shm.klartext", "-journal.klartext",
            ".neu", ".neu-wal", ".neu-shm", ".neu-journal")) {
            val artifact = File(db().path + suffix)
            artifact.writeText("synthetic")
            assertTrue(DbFileInspection.inspect(db()).migrationArtifacts)
            assertEquals("synthetic", artifact.readText())
            assertTrue(artifact.delete())
        }
    }
    @Test fun directoryIsUncertain() {
        assertTrue(db().mkdir())
        assertEquals(FileState.Uncertain, DbFileInspection.inspect(db()).state)
    }
}
