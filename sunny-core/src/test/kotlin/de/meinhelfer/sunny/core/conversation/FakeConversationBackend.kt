package de.meinhelfer.sunny.core.conversation

internal class FakeConversationBackend(
    private val fixture: (ConversationPrompt, ConversationCancellation) -> String?,
) : ConversationBackend {
    override fun reply(prompt: ConversationPrompt, cancellation: ConversationCancellation): String? {
        cancellation.checkpoint()
        return fixture(prompt, cancellation)
    }
}
