package de.meinhelfer.tag.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class AiKind { OPENAI_COMPAT, ANTHROPIC, GEMINI }

/** Auswahl an KI-Diensten. Jeder braucht einen eigenen Schluessel (API-Key) des Nutzers. */
enum class AiProvider(
    val label: String,
    val kind: AiKind,
    val baseUrl: String,
    val defaultModel: String,
    val keyUrl: String,
    val free: Boolean,
    val note: String,
) {
    GEMINI(
        "Google Gemini", AiKind.GEMINI, "https://generativelanguage.googleapis.com/v1beta",
        "gemini-2.5-flash", "https://aistudio.google.com/apikey", true,
        "Kostenloses Kontingent mit einem Google-Konto.",
    ),
    GROQ(
        "Groq (Llama u. a.)", AiKind.OPENAI_COMPAT, "https://api.groq.com/openai/v1",
        "llama-3.3-70b-versatile", "https://console.groq.com/keys", true,
        "Kostenloses Kontingent mit Begrenzung pro Minute und Tag.",
    ),
    MISTRAL(
        "Mistral", AiKind.OPENAI_COMPAT, "https://api.mistral.ai/v1",
        "mistral-small-latest", "https://console.mistral.ai/api-keys", true,
        "Kostenloser Einstieg („Experiment“), Anbieter aus der EU.",
    ),
    OPENROUTER(
        "OpenRouter (viele Modelle)", AiKind.OPENAI_COMPAT, "https://openrouter.ai/api/v1",
        "meta-llama/llama-3.3-70b-instruct:free", "https://openrouter.ai/keys", true,
        "Modelle mit „:free“ im Namen sind kostenlos, andere kosten Geld. Ein Zugang für viele Anbieter.",
    ),
    OPENAI(
        "OpenAI (ChatGPT-Modelle)", AiKind.OPENAI_COMPAT, "https://api.openai.com/v1",
        "gpt-4o-mini", "https://platform.openai.com/api-keys", false,
        "Kostenpflichtig, Abrechnung nach Verbrauch (meist wenige Cent).",
    ),
    ANTHROPIC(
        "Anthropic (Claude)", AiKind.ANTHROPIC, "https://api.anthropic.com/v1",
        "claude-haiku-5-5", "https://console.anthropic.com/settings/keys", false,
        "Kostenpflichtig, Abrechnung nach Verbrauch (meist wenige Cent).",
    ),
    DEEPSEEK(
        "DeepSeek", AiKind.OPENAI_COMPAT, "https://api.deepseek.com/v1",
        "deepseek-chat", "https://platform.deepseek.com/api_keys", false,
        "Kostenpflichtig, sehr günstig. Server in China.",
    ),
}

/** Gewaehlter KI-Dienst, Schluessel (verschluesselt auf dem Handy) und Modell. */
object AiConfig {
    private const val FILE = "ki_einstellungen"
    private const val KEY_PROVIDER = "anbieter"

    private fun prefs(context: Context): SharedPreferences? = try {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, FILE, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        null
    }

    /** false, wenn der verschluesselte Speicher auf diesem Geraet nicht verfuegbar ist. */
    fun storageAvailable(context: Context): Boolean = prefs(context) != null

    fun provider(context: Context): AiProvider {
        val name = prefs(context)?.getString(KEY_PROVIDER, null)
        return AiProvider.entries.firstOrNull { it.name == name } ?: AiProvider.GEMINI
    }

    fun setProvider(context: Context, provider: AiProvider) {
        prefs(context)?.edit()?.putString(KEY_PROVIDER, provider.name)?.apply()
    }

    fun apiKey(context: Context, provider: AiProvider): String =
        prefs(context)?.getString("key_${provider.name}", "").orEmpty()

    fun setApiKey(context: Context, provider: AiProvider, key: String) {
        prefs(context)?.edit()?.putString("key_${provider.name}", key.trim())?.apply()
    }

    fun model(context: Context, provider: AiProvider): String =
        prefs(context)?.getString("model_${provider.name}", null)?.takeIf { it.isNotBlank() } ?: provider.defaultModel

    fun setModel(context: Context, provider: AiProvider, model: String) {
        prefs(context)?.edit()?.putString("model_${provider.name}", model.trim())?.apply()
    }

    fun isConfigured(context: Context): Boolean = apiKey(context, provider(context)).isNotBlank()
}
