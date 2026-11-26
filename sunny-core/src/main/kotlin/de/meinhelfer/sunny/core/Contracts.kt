package de.meinhelfer.sunny.core

enum class RequestType { Routine, Language, FreshInformation }

enum class Route { Rules, OnDevice, Online, Unavailable }

data class RoutingContext(
    val localReady: Boolean = false,
    val internetAvailable: Boolean = false,
    val onlineEnabled: Boolean = false,
    val requestApproved: Boolean = false,
    val containsPrivateData: Boolean = true,
)

data class SunnyRequest(
    val type: RequestType,
    val text: String,
    val routingContext: RoutingContext = RoutingContext(),
    val requestedAction: ActionRequest? = null,
)

/** A candidate supplied by the caller. Runtime output cannot supply a candidate. */
data class ActionRequest(val id: String)

enum class ActionDecision { NotRequested, Denied, AllowedForReview }

enum class Reply {
    RuleResponse,
    LocalAcknowledgement,
    LocalClarification,
    OnlineSelected,
    FreshInformationUnavailable,
}

enum class FallbackReason { ModelMissing, ModelFailed, InvalidModelOutput }

sealed interface SunnyResponse {
    data object InvalidInput : SunnyResponse

    data class Completed(
        val selectedRoute: Route,
        val reply: Reply,
        val fallback: FallbackReason? = null,
        val actionDecision: ActionDecision = ActionDecision.NotRequested,
    ) : SunnyResponse
}

fun interface ActionPolicy {
    fun allows(action: ActionRequest): Boolean
}

object DenyByDefault : ActionPolicy {
    override fun allows(action: ActionRequest): Boolean = false
}

/**
 * On-device runtime contract. Return ACK or CLARIFY. The response is untrusted.
 * Implementations must propagate cancellation and must not access the network.
 */
fun interface LocalRuntime {
    suspend fun generate(text: String): String?
}
