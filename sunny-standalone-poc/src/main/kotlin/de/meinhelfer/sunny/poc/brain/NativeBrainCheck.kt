package de.meinhelfer.sunny.poc.brain

import de.meinhelfer.sunny.core.*
import de.meinhelfer.sunny.runtime.android.DiagnosticPhase
import de.meinhelfer.sunny.runtime.android.DiagnosticStatus
import de.meinhelfer.sunny.runtime.android.LiteRtModelBackend
import de.meinhelfer.sunny.runtime.android.NativeDiagnostics
import java.io.File
import java.io.IOException

internal class NativeBrainCheck(
    private val runtime: LocalRuntime,
    private val diagnostics: NativeDiagnostics? = null,
) {
    suspend fun run(): String {
        var validationStarted: Long? = null
        val observedRuntime = if (diagnostics == null) runtime else LocalRuntime { text ->
            diagnostics.measure(DiagnosticPhase.Runtime) { runtime.generate(text) }
                .also { validationStarted = diagnostics.nowNanos() }
        }
        val routing = RoutingPolicy { type, context ->
            diagnostics?.measure(DiagnosticPhase.Routing, { route ->
                when (route) {
                    Route.OnDevice -> DiagnosticStatus.OnDevice
                    Route.Rules -> DiagnosticStatus.Rules
                    Route.Online -> DiagnosticStatus.Online
                    Route.Unavailable -> DiagnosticStatus.Unavailable
                }
            }) { SunnyRouting.choose(type, context) } ?: SunnyRouting.choose(type, context)
        }
        val response = SunnyOrchestrator(routing = routing, localRuntime = observedRuntime).respond(
            SunnyRequest(
                type = RequestType.Language,
                text = "Dies ist eine synthetische Testnachricht. Bitte bestätige sie.",
                routingContext = RoutingContext(localReady = true),
            ),
        )
        val status = when {
            response is SunnyResponse.Completed && response.selectedRoute == Route.OnDevice &&
                response.fallback == null && response.reply == Reply.LocalAcknowledgement ->
                DiagnosticStatus.AcceptedAck
            response is SunnyResponse.Completed && response.selectedRoute == Route.OnDevice &&
                response.fallback == null && response.reply == Reply.LocalClarification ->
                DiagnosticStatus.AcceptedClarify
            response is SunnyResponse.Completed && response.fallback == FallbackReason.InvalidModelOutput ->
                DiagnosticStatus.Rejected
            else -> DiagnosticStatus.Unknown
        }
        validationStarted?.let { diagnostics?.record(DiagnosticPhase.Validation, status, it) }
        return when (status) {
            DiagnosticStatus.AcceptedAck -> "Native lokale Klassifikation erfolgreich: ACK."
            DiagnosticStatus.AcceptedClarify -> "Native lokale Klassifikation erfolgreich: CLARIFY."
            else -> "Nativer Test nicht bestanden. Keine gültige lokale Klassifikation."
        }
    }

    companion object {
        fun create(model: File, cache: File, diagnostics: NativeDiagnostics): NativeBrainCheck {
            val runtime = diagnostics.measure(DiagnosticPhase.Preparation) {
                if (!cache.isDirectory && !cache.mkdirs()) throw IOException("Modellcache nicht verfügbar.")
                val backend = LiteRtModelBackend(model, cache, diagnostics)
                ManagedLocalRuntime(backend, 120_000_000_000L)
            }
            return NativeBrainCheck(runtime, diagnostics)
        }
    }
}
