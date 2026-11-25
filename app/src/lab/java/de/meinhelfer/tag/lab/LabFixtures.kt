package de.meinhelfer.tag.lab

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest

/** Synthetic fixture only; never reads production paths or user-selected files. */
internal class LabFixtures(context: Context) {
    init { check(context.packageName == "de.meinhelfer.tag.lab") }
    private val root = File(context.filesDir, "synthetic-fixtures")
    private val fixture = AtomicFile(File(root, "fixture.txt"))
    private val expected = "SUNNY-LAB-v1\nTEST_PERSON_001\nTEST_TASK_001\nTEST_DOCUMENT_001\n".toByteArray(Charsets.UTF_8)

    fun create(): String {
        check(root.isDirectory || root.mkdirs())
        val stream = fixture.startWrite()
        try {
            stream.write(expected)
            fixture.finishWrite(stream)
        } catch (e: Exception) {
            fixture.failWrite(stream)
            throw e
        }
        return verify()
    }

    fun verify(): String {
        val actual = fixture.readFully()
        check(actual.contentEquals(expected)) { "Testdaten stimmen nicht mit Sollbestand überein" }
        val digest = MessageDigest.getInstance("SHA-256").digest(actual)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return "OK – synthetischer Sollbestand vollständig\nSHA-256: $digest"
    }
}
