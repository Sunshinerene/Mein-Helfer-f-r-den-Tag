package de.meinhelfer.sunny.poc

import de.meinhelfer.sunny.core.ActionDecision
import de.meinhelfer.sunny.core.FallbackReason
import de.meinhelfer.sunny.core.LocalRuntime
import de.meinhelfer.sunny.core.Reply
import de.meinhelfer.sunny.core.Route
import de.meinhelfer.sunny.core.SunnyResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class CoreDemoTest {
    @Test
    fun simulatedRuntimeUsesAcceptedCoreToken() {
        val response = respond(CoreDemo(), "Testtext")
        assertEquals(
            SunnyResponse.Completed(Route.OnDevice, Reply.LocalAcknowledgement),
            response,
        )
    }

    @Test
    fun missingRuntimeUsesDeterministicCoreFallback() {
        var calls = 0
        val core = CoreDemo(LocalRuntime {
            calls++
            "ACK"
        })
        val first = respond(core, "Testtext A", simulateMissingRuntime = true)
        val second = respond(core, "Testtext B", simulateMissingRuntime = true)

        assertEquals(SunnyResponse.Completed(Route.OnDevice, Reply.RuleResponse, FallbackReason.ModelMissing), first)
        assertEquals(first, second)
        assertEquals(0, calls)
    }

    @Test
    fun invalidInputDoesNotInvokeRuntime() {
        var calls = 0
        val core = CoreDemo(LocalRuntime {
            calls++
            "ACK"
        })

        assertEquals(SunnyResponse.InvalidInput, respond(core, " \n "))
        assertEquals(SunnyResponse.InvalidInput, respond(core, "x".repeat(4097)))
        assertEquals(SunnyResponse.InvalidInput, respond(core, "Test\u0000"))
        assertEquals(0, calls)
    }

    @Test
    fun modelSuggestionCannotReadEvenWithAnActiveTestGrant() {
        var adapterCalls = 0
        val connect = MockConnect(adapter = TodayAdapter { schema, query ->
            adapterCalls++
            SyntheticMeinHelfer.read(schema, query)
        })
        val tasks = connect.capabilities().first()
        connect.grantForTest(tasks.id, tasks.version)
        val core = CoreDemo(LocalRuntime { "READ " + tasks.id })

        val response = respond(core, "Synthetischer Auftrag")

        assertEquals(
            SunnyResponse.Completed(
                Route.OnDevice,
                Reply.RuleResponse,
                FallbackReason.InvalidModelOutput,
                ActionDecision.NotRequested,
            ),
            response,
        )
        assertEquals(0, adapterCalls)
        assertEquals(
            ConnectResult.Read(TodayPayload.Tasks(listOf(SampleTask.TestList))),
            connect.request(ConnectRequest(tasks.id, tasks.version, mapOf("day" to "today", "limit" to "1"))),
        )
        assertEquals(1, adapterCalls)
    }

    private fun respond(core: CoreDemo, text: String, simulateMissingRuntime: Boolean = false): SunnyResponse {
        var completed: Result<SunnyResponse>? = null
        suspend { core.respond(text, simulateMissingRuntime) }
            .startCoroutine(Continuation(EmptyCoroutineContext) { completed = it })
        return checkNotNull(completed) { "POC simulation must finish inline" }.getOrThrow()
    }
}
