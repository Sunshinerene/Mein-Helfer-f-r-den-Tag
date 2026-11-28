package de.meinhelfer.sunny.runtime.android

import de.meinhelfer.sunny.core.LocalRuntimeException
import de.meinhelfer.sunny.core.LocalRuntimeFailure
import de.meinhelfer.sunny.core.ManagedLocalRuntime
import de.meinhelfer.sunny.core.NanoClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeDiagnosticsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun measuresOneNativeGenerationAndEachReachedPhase() = runBlocking {
        val f = Fixture()
        assertEquals("ACK", f.run())
        val snapshot = f.trace.snapshot()
        assertEquals("ACK", snapshot.untrustedPreview)
        assertEquals(DiagnosticStatus.SingleText, snapshot.steps.getValue(DiagnosticPhase.ResponseFormat).status)
        assertEquals(DiagnosticStatus.Available, snapshot.steps.getValue(DiagnosticPhase.ModelRecheck).status)
        assertEquals(DiagnosticStatus.Completed, snapshot.steps.getValue(DiagnosticPhase.Cleanup).status)
        assertEquals(7L, snapshot.steps.getValue(DiagnosticPhase.Generate).elapsedNanos)
        assertEquals(1, f.sends)
        assertArrayEquals(f.header, f.model.readBytes())
        assertEquals(listOf(f.model), temp.root.listFiles()?.toList())
    }

    @Test
    fun recordsFormatWithoutAlteringExtraction() {
        val cases = listOf(
            Triple(ClassificationResponse(1, "CLARIFY", false), DiagnosticStatus.SingleText, "CLARIFY"),
            Triple(ClassificationResponse(1, "", false), DiagnosticStatus.Empty, ""),
            Triple(ClassificationResponse(0, null, false), DiagnosticStatus.Empty, null),
            Triple(ClassificationResponse(2, null, false), DiagnosticStatus.Structured, null),
            Triple(ClassificationResponse(1, "ACK", true), DiagnosticStatus.Structured, null),
            Triple(ClassificationResponse(1, null, false), DiagnosticStatus.Structured, null),
        )
        for ((response, format, extracted) in cases) {
            assertEquals(extracted, response.text)
            assertEquals(format, response.format)
        }
    }

    @Test
    fun boundsAndEscapesPreviewWhileLeavingExactOutputIntact() {
        val trace = NativeDiagnostics()
        val output = "ACK\n\r\t\u0000\u202E" + "x".repeat(1_000)
        trace.capture(output)
        assertEquals(output, ClassificationResponse(1, output, false).text)
        val preview = checkNotNull(trace.snapshot().untrustedPreview)
        assertTrue(preview.startsWith("ACK\\n\\r\\t��"))
        assertTrue(preview.endsWith("…"))
        assertTrue(preview.length <= 513)
        assertFalse(preview.any { it.isISOControl() || it == '\u202E' })
    }

    @Test
    fun identifiesConstructionInitSessionGenerationAndCleanupFailures() = runBlocking {
        val phases = listOf(
            DiagnosticPhase.EngineCreate, DiagnosticPhase.EngineInit, DiagnosticPhase.Session,
            DiagnosticPhase.Generate, DiagnosticPhase.Cleanup,
        )
        for (phase in phases) {
            val f = Fixture()
            f.failAt = phase
            expectFailure<LocalRuntimeException> { f.run() }
            val snapshot = f.trace.snapshot()
            assertEquals(DiagnosticStatus.Failed, snapshot.steps.getValue(phase).status)
            val quarantined = phase in listOf(DiagnosticPhase.EngineCreate, DiagnosticPhase.Cleanup)
            assertEquals(quarantined, snapshot.quarantined)
            if (phase == DiagnosticPhase.EngineCreate) {
                assertNull(snapshot.steps[DiagnosticPhase.Cleanup])
            } else if (phase != DiagnosticPhase.Cleanup) {
                assertEquals(DiagnosticStatus.Completed, snapshot.steps.getValue(DiagnosticPhase.Cleanup).status)
            }
            if (quarantined) {
                expectFailure<LocalRuntimeException> { f.run() }
                assertEquals(DiagnosticStatus.Quarantined, f.trace.snapshot().steps.getValue(DiagnosticPhase.ModelAccess).status)
            }
        }
    }

    @Test
    fun distinguishesMissingIncompatibleAndBusyWithoutEnteringNativeCode() = runBlocking {
        val f = Fixture()
        f.access.withExclusive {
            assertEquals(LocalRuntimeFailure.Busy, expectFailure<LocalRuntimeException> { f.run() }.failure)
            assertEquals(DiagnosticStatus.Busy, f.trace.snapshot().steps.getValue(DiagnosticPhase.ModelAccess).status)
        }
        for (status in listOf(DiagnosticStatus.Missing, DiagnosticStatus.Incompatible)) {
            if (status == DiagnosticStatus.Missing) f.model.delete() else f.model.writeText("invalid")
            expectFailure<LocalRuntimeException> { f.run() }
            assertEquals(status, f.trace.snapshot().steps.getValue(DiagnosticPhase.ModelCheck).status)
        }
        assertNull(f.trace.snapshot().steps[DiagnosticPhase.EngineCreate])
        assertEquals(0, f.sends)
    }

    @Test
    fun deadlineAfterGenerationStillCleansUpAndKeepsObservedResponse() = runBlocking {
        val f = Fixture()
        f.onSend = { f.now = 100 }
        val failure = expectFailure<LocalRuntimeException> { f.run() }
        assertEquals(LocalRuntimeFailure.DeadlineExceeded, failure.failure)
        assertEquals(DiagnosticStatus.DeadlineExceeded, f.trace.snapshot().steps.getValue(DiagnosticPhase.Runtime).status)
        assertEquals(DiagnosticStatus.Completed, f.trace.snapshot().steps.getValue(DiagnosticPhase.Cleanup).status)
        assertEquals("ACK", f.trace.snapshot().untrustedPreview)
    }

    @Test(timeout = 20_000)
    fun cancellationKeepsLeaseUntilNativeCleanup() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val f = Fixture()
        f.onSend = {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        val test = async { f.run() }
        try {
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            test.cancel()
            assertFalse(test.isCompleted)
            val busy = expectFailure<ModelAccessException> { f.access.acquire() }
            assertEquals(ModelAccessFailure.Busy, busy.code)
            assertNull(f.trace.snapshot().steps[DiagnosticPhase.Cleanup])
        } finally {
            release.countDown()
        }
        expectFailure<CancellationException> { test.await() }
        assertEquals(DiagnosticStatus.Cancelled, f.trace.snapshot().steps.getValue(DiagnosticPhase.Runtime).status)
        assertEquals(DiagnosticStatus.Completed, f.trace.snapshot().steps.getValue(DiagnosticPhase.Cleanup).status)
        f.access.withExclusive { assertFalse(it.isQuarantined) }
    }

    private inner class Fixture {
        val model = temp.newFile()
        val access = ModelAccess()
        @Volatile var now = 0L
        val trace = NativeDiagnostics(NanoClock { now })
        var sends = 0
        var failAt: DiagnosticPhase? = null
        var onSend: () -> Unit = {}
        val header = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("LITERTLM".toByteArray())
            putInt(1)
            position(24)
            putLong(40)
        }.array().also(model::writeBytes)

        private fun reached(phase: DiagnosticPhase) {
            if (phase == failAt) error("/fake/path with private detail")
        }

        private val native = NativeModel { _, _ ->
            reached(DiagnosticPhase.EngineCreate)
            object : NativeEngine {
                override fun initialize() = reached(DiagnosticPhase.EngineInit)
                override fun conversation(): NativeConversation {
                    reached(DiagnosticPhase.Session)
                    return object : NativeConversation {
                        override fun send(prompt: String, legacy: Boolean): String? = error("Wrong facade path")

                        override fun sendWithDiagnostics(prompt: String, diagnostics: NativeDiagnostics): String? {
                            val text = diagnostics.measure(DiagnosticPhase.Generate) {
                                sends++
                                reached(DiagnosticPhase.Generate)
                                now += 7
                                onSend()
                                "ACK"
                            }
                            return diagnostics.measure(DiagnosticPhase.ResponseFormat, { it.format }) {
                                diagnostics.capture(text)
                                ClassificationResponse(1, text, structured = false)
                            }.text
                        }

                        override fun close() = reached(DiagnosticPhase.Cleanup)
                    }
                }

                override fun close() = Unit
            }
        }

        private val backend = LiteRtModelBackend(model, temp.root, native, access, Dispatchers.IO, trace)
        private val runtime = ManagedLocalRuntime(backend, 100, NanoClock { now })

        suspend fun run() = trace.measure(DiagnosticPhase.Runtime) { runtime.generate("synthetic fixture") }
    }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
        try {
            block()
        } catch (failure: Throwable) {
            if (failure is T) return failure
            throw failure
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }
}
