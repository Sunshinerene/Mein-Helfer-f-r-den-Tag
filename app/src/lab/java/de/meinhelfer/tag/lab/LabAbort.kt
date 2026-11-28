package de.meinhelfer.tag.lab

import android.content.Context
import android.os.Process

internal class LabAbort(context: Context, private val database: LabDatabase) {
    init { check(context.packageName == "de.meinhelfer.tag.lab") }
    private val prefs = context.getSharedPreferences("lab_abort", Context.MODE_PRIVATE)
    private val points = listOf("A_PREPARED", "ASIDE", "PLACED", "COMMITTED", "CLEANED")
    fun report(): String = points.joinToString("\n") { point ->
        "$point: ${prefs.getString("report_$point", "Nicht getestet")}"
    }

    fun pending() = prefs.getBoolean("pending", false)

    fun start(): String = startAt("A_PREPARED")

    fun startAt(target: String): String {
        check(target in listOf("A_PREPARED", "ASIDE", "PLACED", "COMMITTED", "CLEANED"))
        check(!pending()) { "Vorherigen Test zuerst auswerten" }
        database.prepare()
        fun checkpoint(name: String) {
            check(prefs.edit().putBoolean("pending", true)
                .putString("checkpoint", name).putInt("pid", Process.myPid()).commit())
            if (target == name) {
                Process.killProcess(Process.myPid())
                error("Prozessabbruch nicht erfolgt")
            }
        }
        checkpoint("A_PREPARED")
        database.moveAside()
        checkpoint("ASIDE")
        database.placeCandidate()
        checkpoint("PLACED")
        check(database.verify().endsWith("Zustand B"))
        checkpoint("COMMITTED")
        database.cleanup()
        checkpoint("CLEANED")
        error("Abbruchpunkt nicht erreicht")
    }

    fun evaluate(): String {
        if (!pending()) return prefs.getString("result", "Noch kein Abbruchtest")!!
        check(prefs.getInt("pid", -1) != Process.myPid()) { "Testprozess läuft noch; App erneut öffnen" }
        val point = prefs.getString("checkpoint", "")!!
        val result = runCatching {
            check(point in listOf("A_PREPARED", "ASIDE", "PLACED", "COMMITTED", "CLEANED"))
            val committed = point in listOf("COMMITTED", "CLEANED")
            val actual = database.recover(committed)
            check(actual.endsWith(if (committed) "Zustand B" else "Zustand A"))
            "BESTANDEN – $point; $actual"
        }.getOrElse { "FEHLER – $point; ${it.message}" }
        check(prefs.edit().putBoolean("pending", !result.startsWith("BESTANDEN")).putString("result", result)
            .putString("report_$point", "${java.util.Date()} – $result").commit())
        return result
    }
}
