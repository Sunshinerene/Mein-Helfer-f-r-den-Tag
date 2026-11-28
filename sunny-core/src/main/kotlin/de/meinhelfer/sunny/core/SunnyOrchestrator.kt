package de.meinhelfer.sunny.core

import java.util.concurrent.CancellationException

class SunnyOrchestrator(
    private val routing: RoutingPolicy = SunnyRouting,
    private val actionPolicy: ActionPolicy = DenyByDefault,
    private val localRuntime: LocalRuntime? = null,
) {
    suspend fun respond(request: SunnyRequest): SunnyResponse {
        if (request.text.isBlank() || request.text.length > MAX_REQUEST_LENGTH ||
            request.text.any { it.isISOControl() && it !in "\t\r\n" } ||
            request.requestedAction?.let { !ACTION_ID.matches(it.id) } == true
        ) {
            return SunnyResponse.InvalidInput
        }

        val route = routing.choose(request.type, request.routingContext)
        val actionDecision = request.requestedAction?.let(::evaluateAction) ?: ActionDecision.NotRequested
        val response = when (route) {
            Route.Rules -> SunnyResponse.Completed(route, Reply.RuleResponse)
            Route.OnDevice -> respondLocally(request.text)
            Route.Online -> SunnyResponse.Completed(route, Reply.OnlineSelected)
            Route.Unavailable -> SunnyResponse.Completed(route, Reply.FreshInformationUnavailable)
        }
        return response.copy(actionDecision = actionDecision)
    }

    private fun evaluateAction(action: ActionRequest): ActionDecision {
        val allowed = try {
            actionPolicy.allows(action)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            throw interrupted
        } catch (_: Exception) {
            false
        }
        return if (allowed) ActionDecision.AllowedForReview else ActionDecision.Denied
    }

    private suspend fun respondLocally(text: String): SunnyResponse.Completed {
        val runtime = localRuntime ?: return fallback(FallbackReason.ModelMissing)
        val output = try {
            runtime.generate(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            throw interrupted
        } catch (_: Exception) {
            return fallback(FallbackReason.ModelFailed)
        }

        val reply = when (output) {
            "ACK" -> Reply.LocalAcknowledgement
            "CLARIFY" -> Reply.LocalClarification
            else -> return fallback(FallbackReason.InvalidModelOutput)
        }
        return SunnyResponse.Completed(Route.OnDevice, reply)
    }

    private fun fallback(reason: FallbackReason) =
        SunnyResponse.Completed(Route.OnDevice, Reply.RuleResponse, fallback = reason)

    private companion object {
        const val MAX_REQUEST_LENGTH = 4_096
        val ACTION_ID = Regex("[a-z][a-z0-9_.-]{0,63}")
    }
}
