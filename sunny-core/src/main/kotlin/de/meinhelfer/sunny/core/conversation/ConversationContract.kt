package de.meinhelfer.sunny.core.conversation

import java.util.concurrent.CancellationException

/** Limits are measured in UTF-16 code units, not model tokens. */
data class ConversationBudget(
    val maxContextUtf16Units: Int = 4_096,
    val maxHistoryTurns: Int = 8,
    val maxReplyUtf16Units: Int = 1_024,
) {
    init {
        require(maxContextUtf16Units in 1..65_536) { "Invalid conversation context budget" }
        require(maxHistoryTurns in 0..64) { "Invalid conversation history budget" }
        require(maxReplyUtf16Units in 1..65_536) { "Invalid conversation reply budget" }
    }
}

class ConversationRequest(val text: String)

/** Display as plain text. Even valid text can contain misleading or instruction-like content. */
class UntrustedConversationText internal constructor(val value: String)

class ConversationTurn internal constructor(val input: String, val reply: UntrustedConversationText)

class ConversationPrompt internal constructor(val text: String, val history: List<ConversationTurn>)

sealed interface ConversationResult {
    class Answer(val text: UntrustedConversationText) : ConversationResult

    enum class Rejected : ConversationResult {
        InvalidInput, Busy, Cancelled, BackendFailure, InvalidBackendOutput,
    }
}

/** A one-way cancellation signal, normally created for each request. */
class ConversationCancellation {
    private var cancelled = false

    @Synchronized
    fun cancel() {
        cancelled = true
    }

    @Synchronized
    fun checkpoint() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (cancelled) throw CancellationException()
    }

    @Synchronized
    internal fun isCancelled(): Boolean = cancelled || Thread.currentThread().isInterrupted

    // Serialize the final check and history update with explicit cancel().
    @Synchronized
    internal fun <T> complete(block: () -> T): T {
        checkpoint()
        return block()
    }
}

/**
 * Local-only text backend contract. A backend cooperates at safe points, keeps no session
 * history of its own, and completes all work before returning or throwing. No network or
 * interpretation of the prompt as actions. Return plain, untrusted text.
 */
fun interface ConversationBackend {
    fun reply(prompt: ConversationPrompt, cancellation: ConversationCancellation): String?
}

/** The caller owns the handle for one session and discards it when the session ends. */
interface ConversationSession {
    fun send(
        request: ConversationRequest,
        cancellation: ConversationCancellation = ConversationCancellation(),
    ): ConversationResult
}
