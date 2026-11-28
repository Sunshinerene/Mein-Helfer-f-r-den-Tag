package de.meinhelfer.sunny.poc

import de.meinhelfer.sunny.core.LocalRuntime
import de.meinhelfer.sunny.core.RequestType
import de.meinhelfer.sunny.core.RoutingContext
import de.meinhelfer.sunny.core.SunnyOrchestrator
import de.meinhelfer.sunny.core.SunnyRequest
import de.meinhelfer.sunny.core.SunnyResponse

internal class CoreDemo(runtime: LocalRuntime = LocalRuntime { "ACK" }) {
    private val simulated = SunnyOrchestrator(localRuntime = runtime)
    private val missing = SunnyOrchestrator()

    suspend fun respond(text: String, simulateMissingRuntime: Boolean): SunnyResponse {
        val orchestrator = if (simulateMissingRuntime) missing else simulated
        return orchestrator.respond(
            SunnyRequest(
                type = RequestType.Language,
                text = text,
                // Force the local route in both demonstrations so Core exercises its fallback.
                routingContext = RoutingContext(localReady = true),
            ),
        )
    }
}
