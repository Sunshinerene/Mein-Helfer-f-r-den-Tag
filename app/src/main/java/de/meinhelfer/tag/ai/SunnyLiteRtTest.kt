package de.meinhelfer.tag.ai

import android.content.Context
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SunnyLiteRtTest {
    suspend fun answer(context: Context, prompt: String): String = withContext(Dispatchers.IO) {
        SunnyModelStore.mutex.withLock {
            val model = SunnyModelStore.model(context)
            require(model.isFile && model.length() > 0) { "Bitte zuerst ein kompatibles LiteRT-LM-Modell importieren." }
            require(prompt.isNotBlank() && prompt.length <= 1000)
            Engine(EngineConfig(modelPath = model.absolutePath, backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath, maxNumTokens = 1024)).use { engine ->
                engine.initialize()
                engine.createConversation().use { conversation ->
                    conversation.sendMessage("Antworte kurz auf Deutsch. Du bist Sunny. Keine medizinischen Empfehlungen. Frage: " + prompt).toString()
                }
            }
        }
    }
}
