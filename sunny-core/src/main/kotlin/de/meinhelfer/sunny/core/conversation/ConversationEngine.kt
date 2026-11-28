package de.meinhelfer.sunny.core.conversation

import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Use one engine per backend. Each session owns its own bounded history. */
class ConversationEngine(private val backend: ConversationBackend) {
    private val active = AtomicBoolean()

    fun openSession(budget: ConversationBudget = ConversationBudget()): ConversationSession = Session(budget)

    private inner class Session(private val budget: ConversationBudget) : ConversationSession {
        private var history = emptyList<ConversationTurn>()

        override fun send(
            request: ConversationRequest,
            cancellation: ConversationCancellation,
        ): ConversationResult {
            if (!request.text.isValidText(budget.maxContextUtf16Units)) {
                return ConversationResult.Rejected.InvalidInput
            }
            if (!active.compareAndSet(false, true)) return ConversationResult.Rejected.Busy
            try {
                cancellation.checkpoint()
                val historyForRequest = recentTurns(history, budget.maxContextUtf16Units - request.text.length)
                val prompt = ConversationPrompt(request.text, Collections.unmodifiableList(historyForRequest))
                val raw = backend.reply(prompt, cancellation)
                return cancellation.complete {
                    if (raw == null || !raw.isValidText(budget.maxReplyUtf16Units)) {
                        ConversationResult.Rejected.InvalidBackendOutput
                    } else {
                        val reply = UntrustedConversationText(raw)
                        val turn = ConversationTurn(request.text, reply)
                        history = recentTurns(history + turn, budget.maxContextUtf16Units)
                        ConversationResult.Answer(reply)
                    }
                }
            } catch (_: CancellationException) {
                return ConversationResult.Rejected.Cancelled
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return ConversationResult.Rejected.Cancelled
            } catch (_: Exception) {
                return if (cancellation.isCancelled()) ConversationResult.Rejected.Cancelled
                else ConversationResult.Rejected.BackendFailure
            } finally {
                active.set(false)
            }
        }

        /** Retain a contiguous suffix of whole turns, in chronological order. */
        private fun recentTurns(turns: List<ConversationTurn>, maxUnits: Int): List<ConversationTurn> {
            var remaining = maxUnits
            var count = 0
            for (turn in turns.asReversed()) {
                val units = turn.input.length + turn.reply.value.length
                if (count >= budget.maxHistoryTurns || units > remaining) break
                remaining -= units
                count++
            }
            return turns.takeLast(count)
        }
    }
}

private fun String.isValidText(limit: Int): Boolean =
    length in 1..limit && isNotBlank() && codePoints().noneMatch { codePoint ->
        val control = Character.isISOControl(codePoint) && codePoint != '\n'.code && codePoint != '\t'.code
        val format = Character.getType(codePoint) == Character.FORMAT.toInt()
        val joiner = codePoint == 0x200C || codePoint == 0x200D // Unicode non-joiner and joiner
        control || (format && !joiner) || codePoint in 0xD800..0xDFFF
    }
