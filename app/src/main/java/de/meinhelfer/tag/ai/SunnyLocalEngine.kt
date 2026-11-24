package de.meinhelfer.tag.ai

/** Contract for a future on-device runtime. Implementations must not use network access. */
interface SunnyLocalEngine {
    fun isReady(): Boolean
    suspend fun generate(system: String, prompt: String, maxTokens: Int): Result<String>
}

object SunnyRouting {
    enum class Route { Rules, OnDevice, Online, Unavailable }
    enum class Request { Routine, Language, FreshInformation }
    data class Policy(
        val localReady: Boolean = false,
        val internetAvailable: Boolean = false,
        val onlineEnabled: Boolean = false,
        val requestApproved: Boolean = false,
        val containsPrivateData: Boolean = true
    )

    /** No automatic cloud fallback. Routing never executes actions. */
    fun choose(request: Request, policy: Policy): Route {
        val onlineAllowed = policy.internetAvailable && policy.onlineEnabled &&
            policy.requestApproved && !policy.containsPrivateData
        return when (request) {
            Request.Routine -> Route.Rules
            Request.Language -> when {
                policy.localReady -> Route.OnDevice
                onlineAllowed -> Route.Online
                else -> Route.Rules
            }
            Request.FreshInformation -> if (onlineAllowed) Route.Online else Route.Unavailable
        }
    }
}
