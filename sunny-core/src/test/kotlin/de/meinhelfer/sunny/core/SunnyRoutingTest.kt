package de.meinhelfer.sunny.core

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SunnyRoutingTest(
    private val type: RequestType,
    private val mask: Int,
    private val expected: Route,
) {
    @Test
    fun routesAccordingToTruthTable() {
        val context = RoutingContext(
            localReady = mask and 16 != 0,
            internetAvailable = mask and 8 != 0,
            onlineEnabled = mask and 4 != 0,
            requestApproved = mask and 2 != 0,
            containsPrivateData = mask and 1 != 0,
        )
        assertEquals(expected, SunnyRouting.choose(type, context))
    }

    companion object {
        // Explicit exceptional row indices from the 6A truth table, independent of the router.
        private val expectedByType = mapOf(
            RequestType.Routine to List(32) { Route.Rules },
            RequestType.Language to List(16) { Route.Rules }.toMutableList().apply {
                this[14] = Route.Online
            } + List(16) { Route.OnDevice },
            RequestType.FreshInformation to MutableList(32) { Route.Unavailable }.apply {
                this[14] = Route.Online
                this[30] = Route.Online
            },
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}, mask={1}, expected={2}")
        fun cases(): List<Array<Any>> = RequestType.entries.flatMap { type ->
            expectedByType.getValue(type).mapIndexed { mask, expected -> arrayOf(type, mask, expected) }
        }
    }
}
