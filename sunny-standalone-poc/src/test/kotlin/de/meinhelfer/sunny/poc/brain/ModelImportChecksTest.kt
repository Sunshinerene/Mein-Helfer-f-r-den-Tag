package de.meinhelfer.sunny.poc.brain

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelImportChecksTest {
    @get:Rule val temp = TemporaryFolder()

    private fun header(major: Int = 1, end: Long = 40, magic: String = "LITERTLM"): ByteArray =
        ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(magic.toByteArray(Charsets.US_ASCII))
            putInt(major)
            position(24)
            putLong(end)
        }.array()

    private fun checkHeader(bytes: ByteArray) {
        ModelImportChecks.validateHeader(temp.newFile().apply { writeBytes(bytes) })
    }

    @Test fun acceptsValidStructuralHeader() { checkHeader(header()) }

    @Test fun rejectsShortHeader() {
        assertThrows(IOException::class.java) { checkHeader(ByteArray(32)) }
    }

    @Test fun rejectsWrongMagicAndVersion() {
        assertThrows(IOException::class.java) { checkHeader(header(magic = "INVALID!")) }
        assertThrows(IOException::class.java) { checkHeader(header(major = 2)) }
    }

    @Test fun rejectsOutOfBoundsHeader() {
        for (end in listOf(32L, 64L, 50_000L)) {
            assertThrows(IOException::class.java) { checkHeader(header(end = end)) }
        }
    }

    @Test fun copiesAtLimit() {
        val bytes = byteArrayOf(1, 2, 3)
        val out = ByteArrayOutputStream()
        assertEquals(3L, ModelImportChecks.copy(bytes.inputStream(), out, { 10L }, {}, 3, 7))
        assertArrayEquals(bytes, out.toByteArray())
    }

    @Test fun rejectsOversizeWithoutWritingChunk() {
        val out = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            ModelImportChecks.copy(ByteArray(4).inputStream(), out, { 100L }, {}, 3, 7)
        }
        assertEquals(0, out.size())
    }

    @Test fun rejectsInsufficientReserve() {
        val out = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            ModelImportChecks.copy(ByteArray(3).inputStream(), out, { 9L }, {}, 10, 7)
        }
        assertEquals(0, out.size())
    }

    @Test fun rejectsEmptyInput() {
        assertThrows(IOException::class.java) {
            ModelImportChecks.copy(ByteArray(0).inputStream(), ByteArrayOutputStream(), { Long.MAX_VALUE }, {})
        }
    }

    @Test fun propagatesCancellationBeforeCopy() {
        val out = ByteArrayOutputStream()
        assertThrows(CancellationException::class.java) {
            ModelImportChecks.copy(ByteArray(3).inputStream(), out, { Long.MAX_VALUE }, { throw CancellationException() })
        }
        assertEquals(0, out.size())
    }
}
