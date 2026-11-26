package de.meinhelfer.sunny.core

internal class FakeLocalRuntime(private val answer: suspend (String) -> String?) : LocalRuntime {
    val inputs = mutableListOf<String>()

    override suspend fun generate(text: String): String? {
        inputs += text
        return answer(text)
    }
}
