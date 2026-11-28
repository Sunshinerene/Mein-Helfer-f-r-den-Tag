package de.meinhelfer.sunny.core.conversation

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEngineTest {
    @Test
    fun syntheticConversationSuppliesWholeTurnsInChronologicalOrder() {
        val requests = mutableListOf<ConversationPrompt>()
        val session = engine { prompt, _ ->
            requests += prompt
            "answer for ${prompt.text}"
        }.openSession()

        assertEquals("answer for demo one", session.send("demo one").answer())
        assertEquals("answer for demo two", session.send("demo two").answer())
        assertEquals("answer for demo three", session.send("demo three").answer())
        assertTrue(requests[0].history.isEmpty())
        assertEquals(listOf("demo one"), requests[1].history.map { it.input })
        assertEquals(listOf("demo one", "demo two"), requests[2].history.map { it.input })
        assertEquals(listOf("answer for demo one", "answer for demo two"), requests[2].history.map { it.reply.value })
        assertEquals("demo three", requests[2].text)
    }

    @Test
    fun countBudgetDropsOldestWholeTurn() {
        val seen = mutableListOf<List<String>>()
        val session = engine { prompt, _ ->
            seen += prompt.history.map { it.input }
            "ack"
        }.openSession(ConversationBudget(maxHistoryTurns = 2))

        listOf("first", "second", "third", "fourth").forEach { session.send(it).answer() }
        assertEquals(listOf(emptyList(), listOf("first"), listOf("first", "second"), listOf("second", "third")), seen)
    }

    @Test
    fun contextBudgetIncludesInputAndBothSidesOfEveryTurnInUtf16() {
        val seen = mutableListOf<List<String>>()
        val session = engine { prompt, _ ->
            seen += prompt.history.map { it.input }
            "😀"
        }.openSession(ConversationBudget(maxContextUtf16Units = 9, maxReplyUtf16Units = 2))

        listOf("a", "bc", "xx", "xxxxx", "123456789").forEach { session.send(it).answer() }
        assertEquals(listOf(emptyList(), listOf("a"), listOf("a", "bc"), listOf("xx"), emptyList()), seen)
        assertEquals(ConversationResult.Rejected.InvalidInput, session.send("1234567890"))
    }

    @Test
    fun turnThatCannotFitIsNotPartiallyRetained() {
        val seen = mutableListOf<List<String>>()
        val session = engine { prompt, _ ->
            seen += prompt.history.map { it.input }
            "xyz"
        }.openSession(ConversationBudget(maxContextUtf16Units = 5))

        session.send("a").answer()
        session.send("long").answer()
        session.send("b").answer()
        assertEquals(listOf(emptyList<List<String>>(), emptyList(), emptyList()), seen)
    }

    @Test
    fun zeroHistoryIsSupportedAndBudgetBoundsAreValidated() {
        val session = engine { prompt, _ ->
            assertTrue(prompt.history.isEmpty())
            "ack"
        }.openSession(ConversationBudget(maxHistoryTurns = 0))

        repeat(2) { session.send("fixture").answer() }
        assertThrows(IllegalArgumentException::class.java) { ConversationBudget(maxContextUtf16Units = 0) }
        assertThrows(IllegalArgumentException::class.java) { ConversationBudget(maxHistoryTurns = 65) }
        assertThrows(IllegalArgumentException::class.java) { ConversationBudget(maxReplyUtf16Units = 0) }
    }

    @Test
    fun invalidInputNeverInvokesTheBackend() {
        val session = engine { _, _ -> error("Invalid text must never reach backend") }
            .openSession(ConversationBudget(maxContextUtf16Units = 8))

        listOf("", " \t\n", "too long!", "bad\u0000", "bad\uD800", "bad\u202E").forEach { input ->
            assertEquals(ConversationResult.Rejected.InvalidInput, session.send(input))
        }
    }

    @Test
    fun rejectedBackendOutputDoesNotEnterHistory() {
        val invalid = listOf(null, "", " ", "x".repeat(9), "bad\u001B", "bad\uDC00", "bad\u202E")
        val replies = (invalid + "ok").iterator()
        val session = engine { prompt, _ ->
            assertTrue(prompt.history.isEmpty())
            replies.next()
        }.openSession(ConversationBudget(maxReplyUtf16Units = 8))

        repeat(invalid.size) {
            assertEquals(ConversationResult.Rejected.InvalidBackendOutput, session.send("fixture"))
        }
        assertEquals("ok", session.send("fixture").answer())
    }

    @Test
    fun backendErrorIsSanitizedAndCannotCommitOrInvokeFallback() {
        var calls = 0
        val session = engine { prompt, _ ->
            calls++
            assertTrue(prompt.history.isEmpty())
            if (calls == 1) throw IllegalStateException("private synthetic error")
            "recovered"
        }.openSession()

        assertEquals(ConversationResult.Rejected.BackendFailure, session.send("first"))
        assertEquals(1, calls)
        assertEquals("recovered", session.send("second").answer())
        assertEquals(2, calls)
    }

    @Test
    fun actionAndNetworkLookingOutputRemainsOpaquePlainText() {
        val fixture = """<action>transfer</action> {"url":"https://example.invalid/demo"}"""
        val session = engine { prompt, _ ->
            if (prompt.history.isNotEmpty()) assertEquals(fixture, prompt.history.single().reply.value)
            fixture
        }.openSession()

        val result = session.send("synthetic prompt") as ConversationResult.Answer
        assertEquals(fixture, result.text.value)
        assertFalse(result.toString().contains(fixture))
        assertFalse(result.text.toString().contains(fixture))
        assertEquals(fixture, session.send("synthetic follow-up").answer())
    }

    @Test
    fun sessionHandlesDoNotMixHistory() {
        val seen = mutableListOf<List<String>>()
        val engine = engine { prompt, _ ->
            seen += prompt.history.map { it.input }
            "ack"
        }
        val first = engine.openSession()
        val second = engine.openSession()

        first.send("A one").answer()
        second.send("B one").answer()
        first.send("A two").answer()
        second.send("B two").answer()

        assertEquals(listOf(emptyList(), emptyList(), listOf("A one"), listOf("B one")), seen)
    }

    @Test
    fun backendCannotMutateItsHistorySnapshot() {
        val session = engine { prompt, _ ->
            if (prompt.history.isNotEmpty()) {
                assertThrows(UnsupportedOperationException::class.java) {
                    (prompt.history as MutableList<ConversationTurn>).clear()
                }
            }
            "ack"
        }.openSession()
        session.send("fixture").answer()
        session.send("follow-up").answer()
    }

    @Test
    fun preCancelledRequestSkipsBackend() {
        val session = engine { _, _ -> error("Cancelled request reached backend") }.openSession()
        val cancellation = ConversationCancellation().apply { cancel() }
        assertEquals(ConversationResult.Rejected.Cancelled, session.send(ConversationRequest("fixture"), cancellation))
    }

    @Test
    fun busySpansSessionsUntilCooperativeCancellationFinishesAndDoesNotLeaveHistory() {
        val entered = CountDownLatch(1)
        val checkpoint = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val cancellation = ConversationCancellation()
        val engine = engine { prompt, signal ->
            if (prompt.text == "blocked") {
                entered.countDown()
                assertTrue(checkpoint.await(5, TimeUnit.SECONDS))
                signal.checkpoint()
            }
            assertTrue(prompt.history.isEmpty())
            "ack"
        }
        val first = engine.openSession()
        val second = engine.openSession()
        try {
            val pending = executor.submit<ConversationResult> {
                first.send(ConversationRequest("blocked"), cancellation)
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(ConversationResult.Rejected.Busy, first.send("same session"))
            assertEquals(ConversationResult.Rejected.Busy, second.send("other session"))
            cancellation.cancel()
            assertEquals(ConversationResult.Rejected.Busy, second.send("still busy"))
            checkpoint.countDown()
            assertEquals(ConversationResult.Rejected.Cancelled, pending.get(5, TimeUnit.SECONDS))
            first.send("after cancellation").answer()
            second.send("now independent").answer()
        } finally {
            checkpoint.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun cancellationBeforeBackendReturnPreventsCommitEvenIfItReturnsText() {
        val session = engine { prompt, signal ->
            assertTrue(prompt.history.isEmpty())
            if (prompt.text == "cancel") signal.cancel()
            "ack"
        }.openSession()

        assertEquals(ConversationResult.Rejected.Cancelled, session.send("cancel"))
        assertEquals("ack", session.send("retry").answer())
    }

    @Test
    fun cancellationTakesPrecedenceOverAnOrdinaryBackendError() {
        val session = engine { prompt, signal ->
            assertTrue(prompt.history.isEmpty())
            if (prompt.text == "cancel") {
                signal.cancel()
                throw IllegalStateException("synthetic error during cancellation")
            }
            "ack"
        }.openSession()

        assertEquals(ConversationResult.Rejected.Cancelled, session.send("cancel"))
        assertEquals("ack", session.send("retry").answer())
    }

    @Test
    fun backendInterruptionIsReportedAndRestoresTheInterruptFlag() {
        val session = engine { _, _ -> throw InterruptedException("fixture") }.openSession()
        try {
            assertEquals(ConversationResult.Rejected.Cancelled, session.send("fixture"))
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    private fun engine(fixture: (ConversationPrompt, ConversationCancellation) -> String?) =
        ConversationEngine(FakeConversationBackend(fixture))

    private fun ConversationSession.send(text: String): ConversationResult = send(ConversationRequest(text))

    private fun ConversationResult.answer(): String = (this as ConversationResult.Answer).text.value
}
