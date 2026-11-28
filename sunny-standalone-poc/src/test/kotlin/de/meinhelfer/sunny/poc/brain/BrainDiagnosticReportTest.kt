package de.meinhelfer.sunny.poc.brain

import de.meinhelfer.sunny.core.LocalRuntime
import de.meinhelfer.sunny.core.NanoClock
import de.meinhelfer.sunny.runtime.android.DiagnosticPhase
import de.meinhelfer.sunny.runtime.android.DiagnosticStatus
import de.meinhelfer.sunny.runtime.android.NativeDiagnostics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BrainDiagnosticReportTest {
    @Test
    fun recordsCoreVerdictWithoutChangingValidationOrReplayingInput() = runBlocking {
        for ((output, expected) in listOf(
            "ACK" to DiagnosticStatus.AcceptedAck,
            "CLARIFY" to DiagnosticStatus.AcceptedClarify,
            "ACK " to DiagnosticStatus.Rejected,
            "ACK\n" to DiagnosticStatus.Rejected,
            "ack" to DiagnosticStatus.Rejected,
            "" to DiagnosticStatus.Rejected,
            null to DiagnosticStatus.Rejected,
        )) {
            var calls = 0
            val trace = NativeDiagnostics()
            val summary = NativeBrainCheck(LocalRuntime { input ->
                assertEquals("Dies ist eine synthetische Testnachricht. Bitte bestätige sie.", input)
                calls++
                output
            }, trace).run()
            val snapshot = trace.snapshot()
            assertEquals(1, calls)
            assertEquals(DiagnosticStatus.OnDevice, snapshot.steps.getValue(DiagnosticPhase.Routing).status)
            assertEquals(expected, snapshot.steps.getValue(DiagnosticPhase.Validation).status)
            assertEquals(expected != DiagnosticStatus.Rejected, summary.contains("erfolgreich"))
        }
    }

    @Test
    fun runtimeFailureDoesNotReportValidationOrLeakDetails() = runBlocking {
        var time = 0L
        val trace = NativeDiagnostics(NanoClock { time })
        val run = NativeBrainCheck(LocalRuntime {
            time += 9_000_000L
            error("secret detail /local/file")
        }, trace).run()
        val snapshot = trace.snapshot()
        val report = BrainDiagnosticReport(snapshot, time, DiagnosticStatus.Completed)
        assertTrue(run.contains("nicht bestanden"))
        assertNull(snapshot.steps[DiagnosticPhase.Validation])
        assertNull(report.localPreview)
        val text = report.export()
        assertTrue(text.contains("Gesamtdauer · 9 ms"))
        assertTrue(text.contains("Laufzeit und Zeitlimit · Fehlgeschlagen · 9 ms"))
        assertTrue(text.contains("Core-Validierung · Unbekannt"))
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("/local/file"))
        assertTrue(text.length < 2_000)
    }
}
