package de.meinhelfer.sunny.poc.brain

import de.meinhelfer.sunny.core.*
import de.meinhelfer.sunny.runtime.android.LiteRtModelBackend
import java.io.File
import java.io.IOException

internal class NativeBrainCheck(private val runtime: LocalRuntime) {
    suspend fun run(): String {
        val response = SunnyOrchestrator(localRuntime = runtime).respond(
            SunnyRequest(
                type = RequestType.Language,
                text = "Dies ist eine synthetische Testnachricht. Bitte bestätige sie.",
                routingContext = RoutingContext(localReady = true),
            ),
        )
        return when {
            response is SunnyResponse.Completed && response.selectedRoute == Route.OnDevice &&
                response.fallback == null && response.reply == Reply.LocalAcknowledgement ->
                "Native lokale Klassifikation erfolgreich: ACK."
            response is SunnyResponse.Completed && response.selectedRoute == Route.OnDevice &&
                response.fallback == null && response.reply == Reply.LocalClarification ->
                "Native lokale Klassifikation erfolgreich: CLARIFY."
            else -> "Nativer Test nicht bestanden. Keine gültige lokale Klassifikation."
        }
    }

    companion object {
        fun create(model: File, cache: File): NativeBrainCheck {
            if (!cache.isDirectory && !cache.mkdirs()) throw IOException("Modellcache nicht verfügbar.")
            return NativeBrainCheck(ManagedLocalRuntime(LiteRtModelBackend(model, cache), 120_000_000_000L))
        }
    }
}
