package de.meinhelfer.tag.lab

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Standalone synthetic SQLite fixture, not the production Room database. */
internal class LabDatabase(context: Context) {
    init { check(context.packageName == "de.meinhelfer.tag.lab") }
    private val root = File(context.filesDir, "synthetic-db")
    private fun expected(state: String) = listOf("DOCUMENT_$state", "PERSON_$state", "TASK_$state")

    private fun create(file: File, state: String) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS fixture (value TEXT PRIMARY KEY NOT NULL)")
                db.execSQL("DELETE FROM fixture")
                expected(state).forEach { db.execSQL("INSERT INTO fixture(value) VALUES (?)", arrayOf<Any>(it)) }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }

    fun prepare(): String {
        check(root.isDirectory || root.mkdirs())
        create(File(root, "active.db"), "A")
        create(File(root, "candidate.db"), "B")
        check(inspect(File(root, "active.db")) == "A")
        check(inspect(File(root, "candidate.db")) == "B")
        return "OK – Ausgangszustand A und Sicherungszustand B vollständig"
    }

    private fun inspect(file: File): String {
        check(file.isFile) { "Testdatenbank fehlt: ${file.name}" }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use {
                check(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext()) { "SQLite-Integritätsprüfung fehlgeschlagen" }
            }
            val rows = mutableListOf<String>()
            db.rawQuery("SELECT value FROM fixture ORDER BY value", null).use {
                while (it.moveToNext()) rows.add(it.getString(0))
            }
            return listOf("A", "B").singleOrNull { expected(it) == rows }
                ?: error("Unvollständiger oder vermischter Datenbestand")
        }
    }

    fun verify(): String = "OK – aktive Testdatenbank entspricht vollständig Zustand ${inspect(File(root, "active.db"))}"
}
