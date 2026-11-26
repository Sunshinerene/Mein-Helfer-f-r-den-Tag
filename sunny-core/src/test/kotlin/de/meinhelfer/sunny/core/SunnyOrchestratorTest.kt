package de.meinhelfer.sunny.core

import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class SunnyOrchestratorTest {
    private val routine = SunnyRequest(RequestType.Routine, "Synthetic input")
    private val onlineContext = RoutingContext(
        internetAvailable = true,
        onlineEnabled = true,
        requestApproved = true,
        containsPrivateData = false,
    )
    private val localRequest = routine.copy(
        type = RequestType.Language,
        routingContext = onlineContext.copy(localReady = true),
    )
    private val action = ActionRequest("fixture.preview")

    @Test
    fun rejectsInvalidInputBeforeCallingDependencies() {
        val core = SunnyOrchestrator(
            routing = RoutingPolicy { _, _ -> error("Unexpected routing") },
            actionPolicy = ActionPolicy { error("Unexpected policy call") },
            localRuntime = FakeLocalRuntime { error("Unexpected runtime call") },
        )
        for (text in listOf("", " \t\n", "synthetic\u0000input", "x".repeat(4097))) {
            assertEquals(SunnyResponse.InvalidInput, runRequest { core.respond(routine.copy(text = text)) })
        }
        for (id in listOf("", " ", "bad/id", "x".repeat(65))) {
            val request = routine.copy(requestedAction = ActionRequest(id))
            assertEquals(SunnyResponse.InvalidInput, runRequest { core.respond(request) })
        }
    }

    @Test
    fun acceptsBoundaryLengthAndStandardWhitespace() {
        val core = SunnyOrchestrator()
        val request = routine.copy(text = "x".repeat(4093) + "\t\r\n")
        assertEquals(SunnyResponse.Completed(Route.Rules, Reply.RuleResponse), runRequest { core.respond(request) })
    }

    @Test
    fun routesNonLocalRequestsWithoutInvokingRuntime() {
        val runtime = FakeLocalRuntime { error("Unexpected runtime call") }
        val core = SunnyOrchestrator(localRuntime = runtime)
        val language = routine.copy(type = RequestType.Language)
        val fresh = routine.copy(type = RequestType.FreshInformation)

        val cases = listOf(
            routine.copy(routingContext = localRequest.routingContext) to
                SunnyResponse.Completed(Route.Rules, Reply.RuleResponse),
            language to SunnyResponse.Completed(Route.Rules, Reply.RuleResponse),
            language.copy(routingContext = onlineContext) to
                SunnyResponse.Completed(Route.Online, Reply.OnlineSelected),
            fresh.copy(routingContext = localRequest.routingContext) to
                SunnyResponse.Completed(Route.Online, Reply.OnlineSelected),
            fresh to SunnyResponse.Completed(Route.Unavailable, Reply.FreshInformationUnavailable),
        )
        for ((request, expected) in cases) {
            assertEquals(expected, runRequest { core.respond(request) })
        }
        assertEquals(emptyList<String>(), runtime.inputs)
    }

    @Test
    fun missingRuntimeProducesRulesEvenWhenOnlineIsAllowed() {
        assertEquals(
            fallback(FallbackReason.ModelMissing),
            runRequest { SunnyOrchestrator().respond(localRequest) },
        )
    }

    @Test
    fun runtimeExceptionProducesRulesWithoutRerouting() {
        val runtime = FakeLocalRuntime { throw IllegalStateException("Synthetic failure") }
        var routeCalls = 0
        val core = SunnyOrchestrator(
            routing = RoutingPolicy { type, context ->
                routeCalls++
                SunnyRouting.choose(type, context)
            },
            localRuntime = runtime,
        )
        assertEquals(fallback(FallbackReason.ModelFailed), runRequest { core.respond(localRequest) })
        assertEquals(1, routeCalls)
        assertEquals(listOf(localRequest.text), runtime.inputs)
    }

    @Test
    fun malformedAndUntrustedOutputFallsBackWithoutSurfacingModelText() {
        for (output in listOf(null, "", " ", "ACK\n", "ack", "RUN fixture.preview", """{"action":"fixture.preview"}""", "x".repeat(4097))) {
            val core = SunnyOrchestrator(localRuntime = FakeLocalRuntime { output })
            assertEquals(fallback(FallbackReason.InvalidModelOutput), runRequest { core.respond(localRequest) })
        }
    }

    @Test
    fun validTokensBecomeFixedReplies() {
        for ((token, reply) in mapOf("ACK" to Reply.LocalAcknowledgement, "CLARIFY" to Reply.LocalClarification)) {
            val runtime = FakeLocalRuntime { token }
            val core = SunnyOrchestrator(localRuntime = runtime)
            assertEquals(SunnyResponse.Completed(Route.OnDevice, reply), runRequest { core.respond(localRequest) })
            assertEquals(listOf(localRequest.text), runtime.inputs)
        }
    }

    @Test
    fun cancellationDuringSuspendedGenerationPropagatesUnchanged() {
        lateinit var pending: Continuation<String?>
        val runtime = FakeLocalRuntime { suspendCoroutine { pending = it } }
        val core = SunnyOrchestrator(localRuntime = runtime)
        val abort = CancellationException("Synthetic cancellation")
        var result: Result<SunnyResponse>? = null
        val operation: suspend () -> SunnyResponse = { core.respond(localRequest) }
        operation.startCoroutine(resultContinuation { result = it })
        assertNull(result)
        pending.resumeWith(Result.failure(abort))
        assertSame(abort, checkNotNull(result).exceptionOrNull())
    }

    @Test
    fun successfulResumeReturnsLocalReply() {
        lateinit var pending: Continuation<String?>
        val core = SunnyOrchestrator(localRuntime = FakeLocalRuntime { suspendCoroutine { pending = it } })
        var result: Result<SunnyResponse>? = null
        val operation: suspend () -> SunnyResponse = { core.respond(localRequest) }
        operation.startCoroutine(resultContinuation { result = it })
        assertNull(result)
        pending.resume("ACK")
        assertEquals(SunnyResponse.Completed(Route.OnDevice, Reply.LocalAcknowledgement), result?.getOrThrow())
    }

    @Test
    fun interruptedRuntimePropagatesUnchanged() {
        val abort = InterruptedException("Synthetic interruption")
        val core = SunnyOrchestrator(localRuntime = FakeLocalRuntime { throw abort })
        assertSame(abort, assertThrows(InterruptedException::class.java) { runRequest { core.respond(localRequest) } })
    }

    @Test
    fun policyCancellationPropagatesBeforeGeneration() {
        val abort = CancellationException("Synthetic cancellation")
        val runtime = FakeLocalRuntime { "ACK" }
        val core = SunnyOrchestrator(actionPolicy = ActionPolicy { throw abort }, localRuntime = runtime)
        val request = localRequest.copy(requestedAction = action)
        assertSame(abort, assertThrows(CancellationException::class.java) { runRequest { core.respond(request) } })
        assertEquals(emptyList<String>(), runtime.inputs)
    }

    @Test
    fun policyInterruptionPropagatesUnchanged() {
        val abort = InterruptedException("Synthetic interruption")
        val core = SunnyOrchestrator(actionPolicy = ActionPolicy { throw abort })
        val request = routine.copy(requestedAction = action)
        assertSame(abort, assertThrows(InterruptedException::class.java) { runRequest { core.respond(request) } })
    }

    @Test
    fun deniedActionsNeverReachTheSyntheticExecutor() {
        val request = localRequest.copy(requestedAction = action)
        val runtime = FakeLocalRuntime { "ACK" }
        val executorCalls = mutableListOf<ActionRequest>()
        // Review consumer confined to the test. Its positive control verifies the spy can record calls.
        fun simulateConsumer(response: SunnyResponse.Completed) {
            if (response.actionDecision == ActionDecision.AllowedForReview) {
                executorCalls += action
            }
        }

        val denyingCores = listOf(
            SunnyOrchestrator(localRuntime = runtime),
            SunnyOrchestrator(actionPolicy = ActionPolicy { false }, localRuntime = runtime),
            SunnyOrchestrator(actionPolicy = ActionPolicy { error("Synthetic policy failure") }, localRuntime = runtime),
        )
        for (core in denyingCores) {
            val response = runRequest { core.respond(request) } as SunnyResponse.Completed
            assertEquals(ActionDecision.Denied, response.actionDecision)
            simulateConsumer(response)
        }
        assertEquals(emptyList<ActionRequest>(), executorCalls)

        val approvingCore = SunnyOrchestrator(actionPolicy = ActionPolicy { it == action }, localRuntime = runtime)
        val response = runRequest { approvingCore.respond(request) } as SunnyResponse.Completed
        assertEquals(ActionDecision.AllowedForReview, response.actionDecision)
        simulateConsumer(response)
        assertEquals(listOf(action), executorCalls)
    }

    @Test
    fun modelOutputCannotInvokeActionPolicyWithoutCallerAction() {
        val core = SunnyOrchestrator(
            actionPolicy = ActionPolicy { throw AssertionError("Unexpected policy call") },
            localRuntime = FakeLocalRuntime { "RUN fixture.preview" },
        )
        assertEquals(fallback(FallbackReason.InvalidModelOutput), runRequest { core.respond(localRequest) })
    }

    private fun fallback(reason: FallbackReason) = SunnyResponse.Completed(Route.OnDevice, Reply.RuleResponse, reason)

    private fun runRequest(operation: suspend () -> SunnyResponse): SunnyResponse {
        var result: Result<SunnyResponse>? = null
        operation.startCoroutine(resultContinuation { result = it })
        return checkNotNull(result).getOrThrow()
    }

    private fun resultContinuation(onResult: (Result<SunnyResponse>) -> Unit) =
        object : Continuation<SunnyResponse> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<SunnyResponse>) = onResult(result)
        }
}
