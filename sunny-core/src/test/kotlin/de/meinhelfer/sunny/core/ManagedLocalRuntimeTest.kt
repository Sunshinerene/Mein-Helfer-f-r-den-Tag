package de.meinhelfer.sunny.core

import java.util.concurrent.CancellationException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ManagedLocalRuntimeTest {
    @Test
    fun availableModelPreservesAckAndClarifyThroughTheOrchestrator() {
        val fixture = Fixture()
        assertEquals(LocalModelStatus.Available, runRequest { fixture.runtime.modelStatus() })
        for ((token, reply) in mapOf("ACK" to Reply.LocalAcknowledgement, "CLARIFY" to Reply.LocalClarification)) {
            fixture.infer = { token }
            assertEquals(SunnyResponse.Completed(Route.OnDevice, reply), fixture.respond())
        }
        assertEquals(listOf("status", "status", "open", "infer", "close", "status", "open", "infer", "close"), fixture.events)
    }

    @Test
    fun unavailableModelsAreRejectedBeforeOpeningOrInference() {
        for ((status, code) in mapOf(
            LocalModelStatus.Missing to LocalRuntimeFailure.ModelMissing,
            LocalModelStatus.Incompatible to LocalRuntimeFailure.ModelIncompatible,
        )) {
            val fixture = Fixture()
            assertEquals(LocalModelStatus.Available, runRequest { fixture.runtime.modelStatus() })
            fixture.status = { status }
            assertEquals(status, runRequest { fixture.runtime.modelStatus() })
            assertFailure(code) { fixture.runtime.generate(input) }
            assertEquals(listOf("status", "status", "status"), fixture.events)
        }
    }

    @Test
    fun invalidModelOutputUsesExistingValidationAndLocalFallback() {
        for (output in listOf(null, "", "ack", "ACK\n", "RUN fixture.preview")) {
            val fixture = Fixture(infer = { output })
            assertEquals(fallback(FallbackReason.InvalidModelOutput), fixture.respond())
            assertEquals(fullLifecycle, fixture.events)
        }
    }

    @Test
    fun statusCheckFailureIsSafeAndReleasesTheSlot() {
        val fixture = Fixture(status = { error(input) })
        assertFailure(LocalRuntimeFailure.StatusCheckFailed) { fixture.runtime.modelStatus() }
        assertEquals(listOf("status"), fixture.events)
        fixture.status = { LocalModelStatus.Available }
        assertEquals("ACK", runRequest { fixture.runtime.generate(input) })
        assertEquals(listOf("status") + fullLifecycle, fixture.events)
    }

    @Test
    fun initializationFailureIsSafeAndReleasesTheSlot() {
        val fixture = Fixture(initialize = { error(input) })
        assertFailure(LocalRuntimeFailure.InitializationFailed) { fixture.runtime.generate(input) }
        assertEquals(listOf("status", "open"), fixture.events)
        fixture.initialize = {}
        assertEquals("ACK", runRequest { fixture.runtime.generate(input) })
        assertEquals(listOf("status", "open") + fullLifecycle, fixture.events)
    }

    @Test
    fun inferenceFailureClosesOnceAndLeavesTheRuntimeReusable() {
        val fixture = Fixture(infer = { error(input) })
        assertFailure(LocalRuntimeFailure.InferenceFailed) { fixture.runtime.generate(input) }
        assertEquals(fullLifecycle, fixture.events)
        fixture.infer = { "CLARIFY" }
        assertEquals("CLARIFY", runRequest { fixture.runtime.generate(input) })
        assertEquals(fullLifecycle + fullLifecycle, fixture.events)
    }

    @Test
    fun cleanupFailureIsSafeAndDoesNotRetryClose() {
        val fixture = Fixture()
        fixture.release = { error(input) }
        assertFailure(LocalRuntimeFailure.CleanupFailed) { fixture.runtime.generate(input) }
        assertEquals(fullLifecycle, fixture.events)
        fixture.release = {}
        assertEquals("ACK", runRequest { fixture.runtime.generate(input) })
        assertEquals(fullLifecycle + fullLifecycle, fixture.events)
    }

    @Test
    fun simultaneousInferenceAndCleanupFailuresPreserveSafePrimaryFailure() {
        val fixture = Fixture(infer = { error(input) })
        fixture.release = { error(input) }
        val failure = assertFailure(LocalRuntimeFailure.InferenceFailed) { fixture.runtime.generate(input) }
        assertEquals(0, failure.suppressed.size)
        assertEquals(fullLifecycle, fixture.events)
    }

    @Test
    fun cooperativeDeadlineStopsInferenceAtCheckpoint() {
        val fixture = Fixture()
        fixture.infer = { deadline ->
            fixture.clock.time += timeout
            deadline.checkpoint()
            throw AssertionError("Passed an expired checkpoint")
        }
        assertFailure(LocalRuntimeFailure.DeadlineExceeded) { fixture.runtime.generate(input) }
        assertEquals(fullLifecycle, fixture.events)
    }

    @Test
    fun resultAtDeadlineIsDiscardedAndSessionIsClosed() {
        val fixture = Fixture()
        fixture.infer = {
            fixture.clock.time += timeout
            "ACK"
        }
        assertFailure(LocalRuntimeFailure.DeadlineExceeded) { fixture.runtime.generate(input) }
        assertEquals(fullLifecycle, fixture.events)
    }

    @Test
    fun deadlineCoversStatusAndInitializationAndClosesAJustOpenedSession() {
        val fixture = Fixture()
        fixture.status = {
            fixture.clock.time += 4
            LocalModelStatus.Available
        }
        fixture.initialize = { fixture.clock.time += 6 }
        assertFailure(LocalRuntimeFailure.DeadlineExceeded) { fixture.runtime.generate(input) }
        assertEquals(listOf("status", "open", "close"), fixture.events)
        fixture.events.clear()
        fixture.status = {
            fixture.clock.time += timeout
            LocalModelStatus.Available
        }
        assertFailure(LocalRuntimeFailure.DeadlineExceeded) { fixture.runtime.generate(input) }
        assertEquals(listOf("status"), fixture.events)
    }

    @Test
    fun deadlineAllowsAResultJustBeforeExpiryAcrossNanoTimeWraparound() {
        val fixture = Fixture()
        fixture.clock.time = Long.MAX_VALUE - 3
        fixture.infer = {
            fixture.clock.time += timeout - 1
            "ACK"
        }
        assertEquals("ACK", runRequest { fixture.runtime.generate(input) })
        assertEquals(fullLifecycle, fixture.events)
    }

    @Test
    fun overlapDuringSuspendedInferenceReturnsBusyUntilTheSessionStops() {
        lateinit var pending: Continuation<String?>
        val fixture = Fixture(infer = { suspendCoroutine { pending = it } })
        var first: Result<String?>? = null
        val operation: suspend () -> String? = { fixture.runtime.generate(input) }
        operation.startCoroutine(continuation { first = it })
        assertNull(first)
        val contender = FutureTask {
            assertFailure(LocalRuntimeFailure.Busy) { fixture.runtime.generate(input) }
            assertFailure(LocalRuntimeFailure.Busy) { fixture.runtime.modelStatus() }
        }
        Thread(contender, "synthetic-runtime-contender").apply { isDaemon = true }.start()
        contender.get(5, TimeUnit.SECONDS)
        assertEquals(listOf("status", "open", "infer"), fixture.events)
        pending.resumeWith(Result.success("ACK"))
        assertEquals("ACK", checkNotNull(first).getOrThrow())
        assertEquals(fullLifecycle, fixture.events)

        fixture.infer = { "CLARIFY" }
        assertEquals("CLARIFY", runRequest { fixture.runtime.generate(input) })
        assertEquals(fullLifecycle + fullLifecycle, fixture.events)
    }

    @Test
    fun slotRemainsBusyUntilCloseHasReturned() {
        val fixture = Fixture()
        fixture.release = {
            assertFailure(LocalRuntimeFailure.Busy) { fixture.runtime.generate(input) }
            assertFailure(LocalRuntimeFailure.Busy) { fixture.runtime.modelStatus() }
        }
        assertEquals("ACK", runRequest { fixture.runtime.generate(input) })
        assertEquals(fullLifecycle, fixture.events)
    }

    @Test
    fun externalCancellationPropagatesThroughOrchestratorAndClosesAfterBackendUnwinds() {
        val abort = CancellationException("Synthetic stop")
        lateinit var pending: Continuation<String?>
        val fixture = Fixture()
        fixture.infer = {
            try {
                suspendCoroutine { pending = it }
            } finally {
                fixture.events += "inference ended"
            }
        }
        fixture.release = { error(input) }
        var result: Result<SunnyResponse>? = null
        val operation: suspend () -> SunnyResponse = { fixture.core.respond(request) }
        operation.startCoroutine(continuation { result = it })
        assertNull(result)
        assertEquals(listOf("status", "open", "infer"), fixture.events)
        pending.resumeWith(Result.failure(abort))
        assertSame(abort, checkNotNull(result).exceptionOrNull())
        assertEquals(0, abort.suppressed.size)
        assertEquals(listOf("status", "open", "infer", "inference ended", "close"), fixture.events)
        fixture.infer = { "ACK" }
        fixture.release = {}
        assertEquals("ACK", runRequest { fixture.runtime.generate(input) })
    }

    @Test
    fun interruptedInferencePropagatesThroughOrchestratorAndClosesExactlyOnce() {
        val abort = InterruptedException("Synthetic interruption")
        val fixture = Fixture(infer = { throw abort })
        assertSame(abort, assertThrows(InterruptedException::class.java) { fixture.respond() })
        assertEquals(fullLifecycle, fixture.events)
    }

    @Test
    fun initializationCancellationPropagatesAndReleasesTheSlot() {
        val abort = CancellationException("Synthetic stop")
        val fixture = Fixture(initialize = { throw abort })
        assertSame(abort, assertThrows(CancellationException::class.java) { fixture.respond() })
        assertEquals(listOf("status", "open"), fixture.events)
        assertEquals(LocalModelStatus.Available, runRequest { fixture.runtime.modelStatus() })
    }

    @Test
    fun interruptionDuringCleanupPropagatesEvenAfterOrdinaryInferenceFailure() {
        val abort = InterruptedException("Synthetic interruption")
        val fixture = Fixture(infer = { error(input) })
        fixture.release = { throw abort }
        assertSame(abort, assertThrows(InterruptedException::class.java) { fixture.respond() })
        assertEquals(fullLifecycle, fixture.events)
    }

    @Test
    fun clockInitializationFailureIsSafeAndLeavesTheSlotReusable() {
        val fixture = Fixture()
        var clockFails = true
        val runtime = ManagedLocalRuntime(fixture, timeout, NanoClock {
            if (clockFails) error(input)
            fixture.clock.nowNanos()
        })
        assertFailure(LocalRuntimeFailure.ClockFailed) { runtime.generate(input) }
        assertEquals(emptyList<String>(), fixture.events)
        clockFails = false
        assertEquals("ACK", runRequest { runtime.generate(input) })
    }

    @Test
    fun interruptedThreadDoesNotEnterTheBackend() {
        val fixture = Fixture()
        val requestOnInterruptedThread = FutureTask {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {
                runRequest { fixture.runtime.generate(input) }
            }
        }
        Thread(requestOnInterruptedThread, "synthetic-interrupted-request").apply { isDaemon = true }.start()
        requestOnInterruptedThread.get(5, TimeUnit.SECONDS)
        assertEquals(emptyList<String>(), fixture.events)
        assertEquals("ACK", runRequest { fixture.runtime.generate(input) })
    }

    @Test
    fun localFailuresUseRulesEvenWhenOnlineIsAllowed() {
        val fixtures = listOf(
            Fixture(status = { LocalModelStatus.Missing }),
            Fixture(status = { LocalModelStatus.Incompatible }),
            Fixture(initialize = { error(input) }),
            Fixture(infer = { error(input) }),
            Fixture().apply { infer = {
                clock.time += timeout
                "ACK"
            } },
        )
        for (fixture in fixtures) {
            var routeCalls = 0
            val core = SunnyOrchestrator(
                routing = RoutingPolicy { type, context ->
                    routeCalls++
                    SunnyRouting.choose(type, context)
                },
                localRuntime = fixture.runtime,
            )
            assertEquals(fallback(FallbackReason.ModelFailed), runRequest { core.respond(request) })
            assertEquals(1, routeCalls)
        }
    }

    private class FakeClock(var time: Long = 0) : NanoClock {
        override fun nowNanos(): Long = time
    }

    private class Fixture(
        var status: (RuntimeDeadline) -> LocalModelStatus = { LocalModelStatus.Available },
        var initialize: (RuntimeDeadline) -> Unit = {},
        var infer: suspend (RuntimeDeadline) -> String? = { "ACK" },
    ) : LocalModelBackend {
        val clock = FakeClock()
        val events = mutableListOf<String>()
        var release: () -> Unit = {}
        val runtime = ManagedLocalRuntime(this, timeout, clock)
        val core = SunnyOrchestrator(localRuntime = runtime)

        override suspend fun modelStatus(deadline: RuntimeDeadline): LocalModelStatus {
            events += "status"
            return status(deadline)
        }

        override suspend fun open(deadline: RuntimeDeadline): LocalModelSession {
            events += "open"
            initialize(deadline)
            return object : LocalModelSession {
                override suspend fun generate(text: String, deadline: RuntimeDeadline): String? {
                    assertEquals(input, text)
                    events += "infer"
                    return infer(deadline)
                }

                override fun close() {
                    events += "close"
                    release()
                }
            }
        }

        fun respond(): SunnyResponse = runRequest { core.respond(request) }
    }

    private companion object {
        const val input = "Synthetic input"
        const val timeout = 10L
        val fullLifecycle = listOf("status", "open", "infer", "close")
        val request = SunnyRequest(
            RequestType.Language,
            input,
            RoutingContext(
                localReady = true,
                internetAvailable = true,
                onlineEnabled = true,
                requestApproved = true,
                containsPrivateData = false,
            ),
        )

        fun fallback(reason: FallbackReason) = SunnyResponse.Completed(Route.OnDevice, Reply.RuleResponse, reason)

        fun assertFailure(code: LocalRuntimeFailure, operation: suspend () -> Any?): LocalRuntimeException {
            val failure = assertThrows(LocalRuntimeException::class.java) { runRequest(operation) }
            assertEquals(code, failure.failure)
            assertEquals(code.name, failure.message)
            assertNull(failure.cause)
            return failure
        }

        fun <T> runRequest(operation: suspend () -> T): T {
            var result: Result<T>? = null
            operation.startCoroutine(continuation { result = it })
            return checkNotNull(result).getOrThrow()
        }

        fun <T> continuation(complete: (Result<T>) -> Unit) = object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) = complete(result)
        }
    }
}
