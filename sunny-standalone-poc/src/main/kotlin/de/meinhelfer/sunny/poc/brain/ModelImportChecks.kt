package de.meinhelfer.sunny.poc.brain

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

internal object ModelImportChecks {
    const val RESERVE = 256L * 1024 * 1024
    const val LIMIT = 4L * 1024 * 1024 * 1024

    fun copy(
        input: InputStream,
        output: OutputStream,
        usableSpace: () -> Long,
        checkpoint: () -> Unit,
        limit: Long = LIMIT,
        reserve: Long = RESERVE,
    ): Long {
        require(limit > 0 && reserve >= 0 && reserve <= Long.MAX_VALUE - 128 * 1024)
        var total = 0L
        val buffer = ByteArray(128 * 1024)
        while (true) {
            checkpoint()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) throw IOException("Datei nicht vollständig lesbar.")
            if (count.toLong() > limit - total || usableSpace() < reserve + count) {
                throw IOException("Nicht genügend Speicher oder Modell zu groß.")
            }
            output.write(buffer, 0, count)
            total += count
        }
        checkpoint()
        if (total == 0L) throw IOException("Die Datei ist leer.")
        return total
    }

    fun validateHeader(file: File) {
        RandomAccessFile(file, "r").use { input ->
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
    }
}
