package de.meinhelfer.sunny.poc.brain

import de.meinhelfer.sunny.runtime.android.DiagnosticPhase
import de.meinhelfer.sunny.runtime.android.DiagnosticSnapshot
import de.meinhelfer.sunny.runtime.android.DiagnosticStatus

internal class BrainDiagnosticReport(
    private val snapshot: DiagnosticSnapshot,
    private val elapsedNanos: Long,
    private val completion: DiagnosticStatus,
) {
    val localPreview: String?
        get() = snapshot.untrustedPreview

    // Only fixed labels, enums and elapsed times leave the local display.
    fun export(): String = buildString {
        appendLine("Sunny DevTools · Synthetischer Brain-Test")
        appendLine("Gesamtdauer · ${elapsedNanos / 1_000_000} ms")
        appendLine("Testlauf · ${completion.label}")
        appendLine("Konfiguriertes Backend · ${snapshot.backend.name}")
        appendLine("Quarantäne im Test beobachtet · ${if (snapshot.quarantined) "Ja" else "Unbekannt"}")
        for (phase in DiagnosticPhase.entries) {
            val step = snapshot.steps[phase]
            append(phase.label)
            append(" · ")
            append(step?.status?.label ?: DiagnosticStatus.Unknown.label)
            if (step != null) append(" · ${step.elapsedNanos / 1_000_000} ms")
            appendLine()
        }
    }
}

private val DiagnosticStatus.label: String
    get() = when (this) {
        DiagnosticStatus.Unknown -> "Unbekannt"
        DiagnosticStatus.Completed -> "Abgeschlossen"
        DiagnosticStatus.Failed -> "Fehlgeschlagen"
        DiagnosticStatus.Available -> "Verfügbar"
        DiagnosticStatus.Missing -> "Fehlt"
        DiagnosticStatus.Incompatible -> "Inkompatibel"
        DiagnosticStatus.Busy -> "Belegt"
        DiagnosticStatus.Quarantined -> "Quarantäne"
        DiagnosticStatus.ReadFailed -> "Lesefehler"
        DiagnosticStatus.Cancelled -> "Abgebrochen"
        DiagnosticStatus.Interrupted -> "Unterbrochen"
        DiagnosticStatus.DeadlineExceeded -> "Zeitlimit erreicht"
        DiagnosticStatus.SingleText -> "Ein Textteil"
        DiagnosticStatus.Empty -> "Leer"
        DiagnosticStatus.Structured -> "Strukturierte Antwort"
        DiagnosticStatus.AcceptedAck -> "ACK akzeptiert"
        DiagnosticStatus.AcceptedClarify -> "CLARIFY akzeptiert"
        DiagnosticStatus.Rejected -> "Antwort abgelehnt"
        DiagnosticStatus.OnDevice -> "Lokal"
        DiagnosticStatus.Rules -> "Regeln"
        DiagnosticStatus.Online -> "Online"
        DiagnosticStatus.Unavailable -> "Nicht verfügbar"
    }
