package de.meinhelfer.tag.ai

import android.content.Context
import de.meinhelfer.sunny.runtime.android.LegacyLiteRt
import de.meinhelfer.sunny.runtime.android.ModelAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SunnyLiteRtTest {
    suspend fun answer(context: Context, prompt: String): String = withContext(Dispatchers.IO) {
        ModelAccess.process.withExclusive { lease ->
            val model = SunnyModelStore.model(context)
            require(model.isFile && model.length() > 0) { "Bitte zuerst ein kompatibles LiteRT-LM-Modell importieren." }
            require(prompt.isNotBlank() && prompt.length <= 1000)
            LegacyLiteRt.test(lease, model, context.cacheDir, prompt)
        }
    }
}
