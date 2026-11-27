package de.meinhelfer.sunny.runtime.android

import de.meinhelfer.sunny.core.FallbackReason
import de.meinhelfer.sunny.core.LocalModelBackend
import de.meinhelfer.sunny.core.LocalModelStatus
import de.meinhelfer.sunny.core.LocalRuntimeException
import de.meinhelfer.sunny.core.LocalRuntimeFailure
import de.meinhelfer.sunny.core.ManagedLocalRuntime
import de.meinhelfer.sunny.core.NanoClock
import de.meinhelfer.sunny.core.Reply
import de.meinhelfer.sunny.core.RequestType
import de.meinhelfer.sunny.core.Route
import de.meinhelfer.sunny.core.RoutingContext
import de.meinhelfer.sunny.core.RuntimeDeadline
import de.meinhelfer.sunny.core.SunnyOrchestrator
import de.meinhelfer.sunny.core.SunnyRequest
import de.meinhelfer.sunny.core.SunnyResponse
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LiteRtModelBackendTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun rejectsMissingAndIncompatibleModelsBeforeNativeInitialization() = runBlocking {
        val f = Fixture()
        f.model.delete()
        assertEquals(LocalModelStatus.Missing, f.runtime.modelStatus())
        assertEquals(LocalRuntimeFailure.ModelMissing, failure<LocalRuntimeException> { f.runtime.generate("fixture") }.failure)
        f.model.writeText("invalid")
        assertEquals(LocalModelStatus.Incompatible, f.runtime.modelStatus())
        assertEquals(LocalRuntimeFailure.ModelIncompatible, failure<LocalRuntimeException> { f.runtime.generate("fixture") }.failure)
        f.writeModel(major = 2)
        assertEquals(LocalModelStatus.Incompatible, f.runtime.modelStatus())
        f.writeModel(headerEnd = 50_000)
        assertEquals(LocalModelStatus.Incompatible, f.runtime.modelStatus())
        assertTrue(f.native.events.isEmpty())
    }

    @Test
    fun initializesGeneratesAndClosesInOrderOutsideCallerThread() = runBlocking {
        val f = Fixture()
        val caller = Thread.currentThread()
        f.native.infer = {
            assertNotSame(caller, Thread.currentThread())
            "ACK"
        }
        assertEquals(LocalModelStatus.Available, f.runtime.modelStatus())
        assertEquals("ACK", f.runtime.generate("fixture"))
        assertEquals(lifecycle, f.native.events)
        f.native.infer = { "CLARIFY" }
        assertEquals("CLARIFY", f.runtime.generate("fixture"))
        assertEquals(lifecycle + lifecycle, f.native.events)
    }

    @Test
    fun revalidatesChangedModelUnderLeaseBeforeNativeOpen() = runBlocking {
        val f = Fixture()
        val backend = object : LocalModelBackend by f.backend {
            override suspend fun modelStatus(deadline: RuntimeDeadline): LocalModelStatus =
                f.backend.modelStatus(deadline).also {
                    assertEquals(LocalModelStatus.Available, it)
                    f.access.withExclusive { f.model.delete() }
                }

            override suspend fun open(deadline: RuntimeDeadline) = f.backend.open(deadline)
        }
        val runtime = ManagedLocalRuntime(backend, 10, NanoClock { 0 })
        assertEquals(LocalRuntimeFailure.InitializationFailed, failure<LocalRuntimeException> { runtime.generate("fixture") }.failure)
        assertTrue(f.native.events.isEmpty())
        f.access.withExclusive { f.writeModel() }
    }

    @Test
    fun failedEngineConstructionQuarantinesWithoutRetryOrModelMutation() = runBlocking {
        val f = Fixture()
        f.native.construct = { error("private synthetic detail") }
        f.access.withExclusive { lease ->
            val error = failure<ModelAccessException> {
                legacyReply(f.native, lease, f.model, temp.root, "", probe = true)
            }
            assertEquals(ModelAccessFailure.InitializationFailed, error.code)
            assertEquals("InitializationFailed", error.message)
            assertNull(error.cause)
            assertTrue(lease.isQuarantined)
        }
        assertEquals(listOf("create"), f.native.events)
        assertEquals(ModelAccessFailure.Quarantined, failure<ModelAccessException> {
            f.access.withExclusive { fail("Unexpected mutation") }
        }.code)
        f.assertFallback()
        assertEquals(listOf("create"), f.native.events)
    }

    @Test
    fun cleansUpAfterPartialInitializationAndConversationCreationFailure() = runBlocking {
        val f = Fixture()
        f.native.initialize = { error("private synthetic detail") }
        f.assertFallback()
        assertEquals(listOf("create", "initialize", "engine close"), f.native.events)
        f.native.events.clear()
        f.native.initialize = {}
        f.native.openConversation = { error("private synthetic detail") }
        f.assertFallback()
        assertEquals(listOf("create", "initialize", "conversation", "engine close"), f.native.events)
    }

    @Test
    fun inferenceFailureIsSanitizedAndFallsBackLocally() = runBlocking {
        val f = Fixture()
        f.native.infer = { error("private synthetic detail") }
        f.access.withExclusive { lease ->
            val error = failure<ModelAccessException> {
                legacyReply(f.native, lease, f.model, temp.root, "fixture", probe = false)
            }
            assertEquals(ModelAccessFailure.InferenceFailed, error.code)
            assertEquals("InferenceFailed", error.message)
            assertNull(error.cause)
            assertTrue(error.suppressed.isEmpty())
        }
        assertEquals(lifecycle, f.native.events)
        f.assertFallback()
    }

    @Test
    fun uncertainConversationCleanupQuarantinesAllOperationsAndPreservesEngine() = runBlocking {
        val f = Fixture()
        f.native.closeConversation = { error("private synthetic detail") }
        f.assertFallback()
        assertEquals(lifecycle.dropLast(1), f.native.events)
        assertEquals(ModelAccessFailure.Quarantined, failure<ModelAccessException> { f.access.withExclusive { fail("Unexpected mutation") } }.code)
        assertEquals(LocalRuntimeFailure.StatusCheckFailed, failure<LocalRuntimeException> { f.runtime.modelStatus() }.failure)
        f.assertFallback()
        assertEquals(lifecycle.dropLast(1), f.native.events)
    }

    @Test
    fun uncertainEngineCleanupAfterFailedInitializationQuarantines() = runBlocking {
        val f = Fixture()
        f.native.initialize = { error("private synthetic detail") }
        f.native.closeEngine = { error("private synthetic cleanup detail") }
        f.access.withExclusive { lease ->
            val error = failure<ModelAccessException> {
                legacyReply(f.native, lease, f.model, temp.root, "", probe = true)
            }
            assertEquals(ModelAccessFailure.CleanupFailed, error.code)
            assertEquals("CleanupFailed", error.message)
            assertNull(error.cause)
            assertTrue(lease.isQuarantined)
        }
        assertEquals(listOf("create", "initialize", "engine close"), f.native.events)
        assertEquals(ModelAccessFailure.Quarantined, failure<ModelAccessException> { f.access.withExclusive { fail("Unexpected deletion") } }.code)
    }

    @Test(timeout = 20_000)
    fun importAndDeletionWaitForInferenceEvenAfterExternalCancellation() = runBlocking {
        val f = Fixture()
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        f.native.infer = {
            entered.countDown()
            check(unblock.await(10, TimeUnit.SECONDS))
            "ACK"
        }
        val request = async(start = CoroutineStart.UNDISPATCHED) { f.runtime.generate("fixture") }
        try {
            val firstCallEntered = kotlinx.coroutines.withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) }
            assertTrue(firstCallEntered)
            val competing = f.makeRuntime()
            assertEquals(LocalRuntimeFailure.Busy, failure<LocalRuntimeException> { competing.generate("fixture") }.failure)
            val import = async(start = CoroutineStart.UNDISPATCHED) {
                f.access.withExclusive { f.native.events += "import" }
            }
            val delete = async(start = CoroutineStart.UNDISPATCHED) {
                f.access.withExclusive { f.native.events += "delete" }
            }

            request.cancel()
            assertFalse(request.isCompleted)
            assertFalse(import.isCompleted)
            assertFalse(delete.isCompleted)
            assertEquals(lifecycle.dropLast(2), f.native.events.toList())
            unblock.countDown()
            failure<CancellationException> { request.await() }
            import.await()
            delete.await()
            assertEquals(lifecycle + listOf("import", "delete"), f.native.events)
        } finally {
            unblock.countDown()
        }
    }

    @Test(timeout = 20_000)
    fun runtimeRejectsAccessDuringModelImport() = runBlocking {
        val f = Fixture()
        f.access.withExclusive {
            assertEquals(LocalRuntimeFailure.Busy, failure<LocalRuntimeException> { f.runtime.generate("fixture") }.failure)
            assertTrue(f.native.events.isEmpty())
        }
        assertEquals("ACK", f.runtime.generate("fixture"))
    }

    @Test
    fun propagatesInterruptedExceptionAndCleansUp() = runBlocking {
        val f = Fixture()
        val abort = InterruptedException()
        f.native.infer = { throw abort }
        val propagated = failure<InterruptedException> { f.runtime.generate("fixture") }
        assertTrue(propagated === abort || propagated.cause === abort)
        assertEquals(lifecycle, f.native.events)
    }

    @Test
    fun checksDeadlineAfterNativeReturnAndCleansUp() = runBlocking {
        val f = Fixture()
        f.native.infer = {
            f.now = 10
            "ACK"
        }
        assertEquals(LocalRuntimeFailure.DeadlineExceeded, failure<LocalRuntimeException> { f.runtime.generate("fixture") }.failure)
        assertEquals(lifecycle, f.native.events)
        f.now = 0
        f.native.infer = { "ACK" }
        assertEquals("ACK", f.runtime.generate("fixture"))
    }

    @Test
    fun legacyModelTestAndImportProbePreservePromptAndReturnText() = runBlocking {
        val f = Fixture()
        f.native.infer = { "Synthetische Antwort" }
        f.access.withExclusive { lease ->
            assertEquals("Synthetische Antwort", legacyReply(f.native, lease, f.model, temp.root, "Testfrage", probe = false))
            legacyReply(f.native, lease, f.model, temp.root, "", probe = true)
        }
        assertEquals(
            listOf(
                true to "Antworte kurz auf Deutsch. Du bist Sunny. Keine medizinischen Empfehlungen. Frage: Testfrage",
                true to "Antworte mit OK.",
            ),
            f.native.prompts,
        )
        assertEquals(lifecycle + lifecycle, f.native.events)
    }

    @Test
    fun coreRejectsUnexpectedNativeOutputAndAcceptsOnlyKnownTokens() = runBlocking {
        val f = Fixture()
        f.native.infer = { "RUN fixture.preview" }
        assertEquals(SunnyResponse.Completed(Route.OnDevice, Reply.RuleResponse, FallbackReason.InvalidModelOutput), f.respond())
        f.native.infer = { "CLARIFY" }
        assertEquals(SunnyResponse.Completed(Route.OnDevice, Reply.LocalClarification), f.respond())
    }

    private inner class Fixture {
        val model = temp.newFile()
        val native = FakeNative()
        val access = ModelAccess()
        val backend = LiteRtModelBackend(model, temp.root, native, access, Dispatchers.IO)
        var now = 0L
        val runtime = makeRuntime()

        init {
            writeModel()
        }

        fun makeRuntime() = ManagedLocalRuntime(backend, 10, NanoClock { now })

        fun writeModel(major: Int = 1, headerEnd: Long = 40) {
            val bytes = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("LITERTLM".toByteArray())
            bytes.putInt(major)
            bytes.position(24)
            bytes.putLong(headerEnd)
            model.writeBytes(bytes.array())
        }

        suspend fun respond() = SunnyOrchestrator(localRuntime = runtime).respond(
            SunnyRequest(RequestType.Language, "fixture", RoutingContext(true, true, true, true, false)),
        )

        suspend fun assertFallback() {
            assertEquals(SunnyResponse.Completed(Route.OnDevice, Reply.RuleResponse, FallbackReason.ModelFailed), respond())
        }
    }

    private class FakeNative : NativeModel {
        val events = mutableListOf<String>()
        val prompts = mutableListOf<Pair<Boolean, String>>()
        var construct: () -> Unit = {}
        var initialize: () -> Unit = {}
        var openConversation: () -> Unit = {}
        var infer: () -> String? = { "ACK" }
        var closeConversation: () -> Unit = {}
        var closeEngine: () -> Unit = {}

        override fun create(model: File, cache: File): NativeEngine {
            events += "create"
            construct()
            return object : NativeEngine {
                override fun initialize() {
                    events += "initialize"
                    this@FakeNative.initialize()
                }

                override fun conversation(): NativeConversation {
                    events += "conversation"
                    openConversation()
                    return object : NativeConversation {
                        override fun send(prompt: String, legacy: Boolean): String? {
                            events += "send"
                            prompts += legacy to prompt
                            return infer()
                        }

                        override fun close() {
                            events += "conversation close"
                            closeConversation()
                        }
                    }
                }

                override fun close() {
                    events += "engine close"
                    closeEngine()
                }
            }
        }
    }

    private companion object {
        val lifecycle = listOf("create", "initialize", "conversation", "send", "conversation close", "engine close")

        suspend inline fun <reified T : Throwable> failure(block: () -> Unit): T {
            try {
                block()
            } catch (failure: Throwable) {
                if (failure is T) return failure
                throw failure
            }
            throw AssertionError("Expected ${T::class.simpleName}")
        }
    }
}
