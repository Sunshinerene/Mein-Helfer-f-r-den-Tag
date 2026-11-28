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
        check(!File(root, "previous.db").exists()) { "Offene Wiederherstellung zuerst auswerten" }
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

    private val active get() = File(root, "active.db")
    private val candidate get() = File(root, "candidate.db")
    private val previous get() = File(root, "previous.db")

    fun moveAside() {
        check(!previous.exists())
        check(inspect(active) == "A")
        check(active.renameTo(previous))
    }

    fun placeCandidate() {
        check(!active.exists())
        check(inspect(candidate) == "B")
        check(candidate.renameTo(active))
    }

    fun cleanup() {
        check(inspect(active) == "B")
        check(!previous.exists() || previous.delete())
    }

    fun recover(committed: Boolean): String {
        if (committed) {
            cleanup()
            check(inspect(active) == "B")
        } else {
            if (previous.exists()) {
                check(inspect(previous) == "A")
                // Atomic replacement preserves the rollback source until rename succeeds.
                android.system.Os.rename(previous.path, active.path)
            }
            check(inspect(active) == "A")
        }
        return verify()
    }

    fun verify(): String = "OK – aktive Testdatenbank entspricht vollständig Zustand ${inspect(File(root, "active.db"))}"
}
