package de.meinhelfer.sunny.core

fun interface RoutingPolicy {
    fun choose(type: RequestType, context: RoutingContext): Route
}

object SunnyRouting : RoutingPolicy {
    override fun choose(type: RequestType, context: RoutingContext): Route {
        val onlineAllowed = context.internetAvailable && context.onlineEnabled &&
            context.requestApproved && !context.containsPrivateData

        return when (type) {
            RequestType.Routine -> Route.Rules
            RequestType.Language -> when {
                context.localReady -> Route.OnDevice
                onlineAllowed -> Route.Online
                else -> Route.Rules
            }
            RequestType.FreshInformation -> if (onlineAllowed) Route.Online else Route.Unavailable
        }
    }
}
