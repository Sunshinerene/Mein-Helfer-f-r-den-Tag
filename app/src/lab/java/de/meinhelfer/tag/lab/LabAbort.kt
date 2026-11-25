package de.meinhelfer.tag.lab

import android.content.Context
import android.os.Process

internal class LabAbort(context: Context, private val database: LabDatabase) {
    init { check(context.packageName == "de.meinhelfer.tag.lab") }
    private val prefs = context.getSharedPreferences("lab_abort", Context.MODE_PRIVATE)

    fun start(): String {
        check(!prefs.getBoolean("pending", false)) { "Vorherigen Test zuerst auswerten" }
        database.prepare()
        check(prefs.edit().putBoolean("pending", true)
            .putString("checkpoint", "A_PREPARED").putInt("pid", Process.myPid()).commit())
        Process.killProcess(Process.myPid())
        error("Prozessabbruch nicht erfolgt")
    }

    fun evaluate(): String {
        if (!prefs.getBoolean("pending", false)) return prefs.getString("result", "Noch kein Abbruchtest")!!
        check(prefs.getInt("pid", -1) != Process.myPid()) { "Testprozess läuft noch; App erneut öffnen" }
        val result = runCatching {
            check(prefs.getString("checkpoint", "") == "A_PREPARED")
            val actual = database.verify()
            check(actual.endsWith("Zustand A")) { "Ausgangszustand A erwartet" }
            "BESTANDEN – Neustart nach Prozessabbruch; $actual"
        }.getOrElse { "FEHLER – ${it.message}" }
        check(prefs.edit().putBoolean("pending", false).putString("result", result).commit())
        return result
    }
}
