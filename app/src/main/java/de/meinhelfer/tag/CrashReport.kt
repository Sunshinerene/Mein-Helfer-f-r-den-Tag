package de.meinhelfer.tag

import android.content.Context
import java.io.File

/**
 * Merkt sich einen Absturz (nur Fehlerart und Programmstellen, keine Einträge) und bietet ihn beim nächsten Start zum Teilen an.
 * So lässt sich ein Fehler finden, ohne dass jemand einen Computer anschließen muss.
 */
object CrashReport {
    private const val FILE = "absturz.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { File(app.filesDir, FILE).writeText(describe(app, e)) }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun describe(context: Context, e: Throwable): String = buildString {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        appendLine("Mein Helfer $version · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}")
        appendLine("Zeit: " + java.time.LocalDateTime.now().withNano(0))
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 4) {
            // Nur die Fehlerart, nicht die Meldung (kann Text aus Einträgen enthalten)
            appendLine((if (depth == 0) "" else "Ursache: ") + t.javaClass.name)
            t.stackTrace.take(if (t is StackOverflowError) 12 else 25).forEach { appendLine("  at $it") }
            t = t.cause
            depth++
        }
    }

    fun pending(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
