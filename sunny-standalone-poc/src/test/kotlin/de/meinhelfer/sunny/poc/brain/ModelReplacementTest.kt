package de.meinhelfer.sunny.poc.brain

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelReplacementTest {
    @get:Rule val temp = TemporaryFolder()

    private fun replace(staged: File, target: File) {
        Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun fixture(): Pair<File, File> {
        val dir = temp.newFolder()
        return dir to File(dir, "model.litertlm").apply { writeText("old") }
    }

    @Test fun successfulReplacement() = runBlocking {
        val (dir, model) = fixture()
        val size = ModelReplacement.install(dir, model, { false },
            { it.writeText("new"); 3L }, {}, ::replace)
        assertEquals(3L, size)
        assertEquals("new", model.readText())
        assertEquals(listOf(model.name), dir.list()!!.toList())
    }

    @Test fun failedPreparationPreservesOldModel() {
        val (dir, model) = fixture()
        assertThrows(IOException::class.java) {
            runBlocking {
                ModelReplacement.install(dir, model, { false },
                    { it.writeText("partial"); throw IOException("probe") }, {}, ::replace)
            }
        }
        assertEquals("old", model.readText())
        assertEquals(listOf(model.name), dir.list()!!.toList())
    }

    @Test fun failedRenamePreservesOldModel() {
        val (dir, model) = fixture()
        assertThrows(IOException::class.java) {
            runBlocking {
                ModelReplacement.install(dir, model, { false },
                    { it.writeText("new"); 3L }, {}, { _, _ -> throw IOException("rename") })
            }
        }
        assertEquals("old", model.readText())
        assertEquals(listOf(model.name), dir.list()!!.toList())
    }

    @Test fun cancellationBeforeReplacementPreservesOldModel() {
        val (dir, model) = fixture()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                ModelReplacement.install(dir, model, { false },
                    { it.writeText("new"); 3L }, { throw CancellationException() }, ::replace)
            }
        }
        assertEquals("old", model.readText())
        assertEquals(listOf(model.name), dir.list()!!.toList())
    }

    @Test fun quarantineRetainsStagedFileAndOldModel() {
        val (dir, model) = fixture()
        var quarantined = false
        assertThrows(IOException::class.java) {
            runBlocking {
                ModelReplacement.install(dir, model, { quarantined },
                    { it.writeText("new"); quarantined = true; 3L }, {}, ::replace)
            }
        }
        assertEquals("old", model.readText())
        assertEquals(2, dir.list()!!.size)
    }

    @Test fun incompletePreparationRejected() {
        val (dir, model) = fixture()
        assertThrows(IOException::class.java) {
            runBlocking {
                ModelReplacement.install(dir, model, { false },
                    { it.writeText("new"); 4L }, {}, ::replace)
            }
        }
        assertEquals("old", model.readText())
        assertEquals(listOf(model.name), dir.list()!!.toList())
    }

    @Test fun deletionIsIdempotent() {
        val (_, model) = fixture()
        assertTrue(ModelReplacement.delete(model))
        assertFalse(model.exists())
        assertTrue(ModelReplacement.delete(model))
    }

    @Test fun deletionFailureReported() {
        val dir = temp.newFolder()
        File(dir, "child").writeText("keep")
        assertFalse(ModelReplacement.delete(dir))
        assertTrue(File(dir, "child").exists())
    }
}
