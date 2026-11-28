package de.meinhelfer.sunny.poc.brain

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ProgressBar
import android.view.View
import de.meinhelfer.sunny.runtime.android.DiagnosticStatus
import de.meinhelfer.sunny.runtime.android.NativeDiagnostics
import kotlinx.coroutines.*

internal class BrainPanel(
    private val activity: Activity,
    private val chooseFile: () -> Unit,
) : LinearLayout(activity) {
    private val store = LocalModelStore(activity)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var operation: Job? = null
    private var closed = false
    private val status = TextView(activity)
    private val progress = ProgressBar(activity)
    private val importButton = Button(activity)
    private val deleteButton = Button(activity)
    private val cancelButton = Button(activity)
    private val testButton = Button(activity)
    private val diagnosisButton = Button(activity)
    private var lastDiagnosis: BrainDiagnosticReport? = null

    init {
        orientation = VERTICAL
        addView(TextView(activity).apply {
            text = "Lokale KI · LiteRT-LM · nur vertrauenswürdige Modelle importieren. Keine echten Pflegedaten verwenden."
        })
        addView(status.apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE })
        addView(progress)
        addView(importButton.apply {
            text = "KI-Modell auswählen"
            isAllCaps = false
            setOnClickListener { if (operation == null && !closed) chooseFile() }
        })
        addView(deleteButton.apply {
            text = "Lokales KI-Modell entfernen"
            isAllCaps = false
            setOnClickListener {
                AlertDialog.Builder(activity)
                    .setTitle("Lokales Modell entfernen?")
                    .setMessage("Nur Sunnys importiertes KI-Modell wird entfernt. MeinHelfer bleibt unverändert.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Entfernen") { _, _ ->
                        runOperation("Modell wird entfernt …") {
                            if (store.delete()) "Modell entfernt." else "Modell konnte nicht entfernt werden."
                        }
                    }.show()
            }
        })
        addView(testButton.apply {
            text = "Native lokale KI testen"
            isAllCaps = false
            setOnClickListener {
                runOperation("Lokale Klassifikation läuft …") {
                    runDiagnostic()
                }
            }
        })
        addView(diagnosisButton.apply {
            text = "Brain-Diagnose anzeigen"
            isAllCaps = false
            setOnClickListener { lastDiagnosis?.let(::showDiagnostic) }
        })
        addView(cancelButton.apply {
            text = "Vorgang abbrechen"
            isAllCaps = false
            setOnClickListener {
                status.text = "Abbruch angefordert. Native Arbeit muss zuerst sicher enden."
                isEnabled = false
                operation?.cancel()
            }
        })
        refresh()
    }

    fun acceptFile(uri: Uri) {
        runOperation("Modell wird importiert und geprüft …") {
            val size = store.importModel(uri)
            "Import abgeschlossen: ${size / (1024 * 1024)} MiB. Nativer Antworttest steht aus."
        }
    }

    private fun refresh(message: String = "") {
        if (closed) return
        val busy = operation != null
        val installed = store.model.isFile
        status.text = listOf(
            if (installed) "Modell vorhanden: ${store.model.length() / (1024 * 1024)} MiB" else "Kein Modell installiert.",
            message,
        ).filter { it.isNotBlank() }.joinToString("\n")
        importButton.isEnabled = !busy
        deleteButton.isEnabled = !busy && installed
        testButton.isEnabled = !busy
        diagnosisButton.isEnabled = !busy && lastDiagnosis != null
        cancelButton.isEnabled = busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private suspend fun runDiagnostic(): String {
        val trace = NativeDiagnostics()
        val start = trace.nowNanos()
        var completion = DiagnosticStatus.Unknown
        try {
            return withContext(Dispatchers.IO) {
                NativeBrainCheck.create(store.model, store.cache, trace).run()
            }.also { completion = DiagnosticStatus.Completed }
        } catch (failure: CancellationException) {
            completion = DiagnosticStatus.Cancelled
            throw failure
        } catch (failure: InterruptedException) {
            completion = DiagnosticStatus.Interrupted
            throw failure
        } catch (failure: Exception) {
            completion = DiagnosticStatus.Failed
            throw failure
        } finally {
            if (!closed) lastDiagnosis = BrainDiagnosticReport(trace.snapshot(), trace.nowNanos() - start, completion)
        }
    }

    private fun showDiagnostic(report: BrainDiagnosticReport) {
        val summary = report.export()
        val preview = report.localPreview?.let { "«$it»" } ?: "Kein sichtbarer Text erfasst."
        AlertDialog.Builder(activity)
            .setTitle("Brain-Diagnose")
            .setMessage("$summary\nSynthetische Modellantwort · nicht vertrauenswürdig · nur hier angezeigt\n$preview")
            .setPositiveButton("Bericht kopieren") { _, _ ->
                activity.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Sunny Brain-Diagnose", summary))
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun runOperation(message: String, work: suspend () -> String) {
        if (closed || operation != null) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var result = "Vorgang nicht abgeschlossen."
            try {
                result = work()
            } catch (failure: CancellationException) {
                result = "Vorgang abgebrochen. Modellstatus erneut geprüft."
                throw failure
            } catch (_: Exception) {
                result = "Vorgang fehlgeschlagen. Bei gesperrtem Modellzugriff Sunny neu starten."
            } finally {
                operation = null
                refresh(result)
            }
        }
        operation = job
        refresh(message)
        job.start()
    }

    fun close() {
        closed = true
        lastDiagnosis = null
        scope.cancel()
    }
}
