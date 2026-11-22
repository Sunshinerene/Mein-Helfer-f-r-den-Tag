package de.meinhelfer.tag.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class AiKind { OPENAI_COMPAT, ANTHROPIC, GEMINI }

/** Auswahl an KI-Diensten. Jeder braucht einen eigenen Schluessel (API-Key) des Nutzers, ausser [keyRequired] = false. */
enum class AiProvider(
    val label: String,
    val kind: AiKind,
    val baseUrl: String,
    val defaultModel: String,
    val keyUrl: String,
    val free: Boolean,
    val note: String,
    /** false = geht auch ohne Schluessel (z. B. eigener Server, OVHcloud mit niedrigem Limit) */
    val keyRequired: Boolean = true,
    /** Eigene Adresse fuer die Modell-Liste, null = baseUrl + "/models" */
    val modelsUrl: String? = null,
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
    CEREBRAS(
        "Cerebras", AiKind.OPENAI_COMPAT, "https://api.cerebras.ai/v1",
        "gpt-oss-120b", "https://cloud.cerebras.ai", true,
        "Kostenloses Kontingent mit Begrenzung pro Minute und Tag, sehr schnell. Anbieter aus den USA.",
    ),
    SAMBANOVA(
        "SambaNova", AiKind.OPENAI_COMPAT, "https://api.sambanova.ai/v1",
        "Meta-Llama-3.3-70B-Instruct", "https://cloud.sambanova.ai/apis", true,
        "Kostenloser Zugang mit Begrenzung pro Minute. Anbieter aus den USA.",
    ),
    GITHUB(
        "GitHub Models", AiKind.OPENAI_COMPAT, "https://models.github.ai/inference",
        "openai/gpt-4.1-mini", "https://github.com/settings/personal-access-tokens/new", true,
        "Kostenlos mit einem GitHub-Konto, aber knapp begrenzt. Als Schlüssel ein Token mit der Berechtigung „Models: Read“.",
        modelsUrl = "https://models.github.ai/catalog/models",
    ),
    HUGGINGFACE(
        "Hugging Face", AiKind.OPENAI_COMPAT, "https://router.huggingface.co/v1",
        "openai/gpt-oss-120b", "https://huggingface.co/settings/tokens", true,
        "Viele offene Modelle über einen Zugang. Kleines kostenloses Monatsguthaben, danach kostenpflichtig.",
    ),
    COHERE(
        "Cohere", AiKind.OPENAI_COMPAT, "https://api.cohere.ai/compatibility/v1",
        "command-a-03-2025", "https://dashboard.cohere.com/api-keys", true,
        "Kostenloser Test-Schlüssel mit Begrenzung, gedacht zum Ausprobieren.",
    ),
    OVHCLOUD(
        "OVHcloud (EU)", AiKind.OPENAI_COMPAT, "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1",
        "gpt-oss-120b", "https://endpoints.ai.cloud.ovh.net", true,
        "Server in der EU (Frankreich). Geht ohne Schlüssel kostenlos mit 2 Anfragen pro Minute, mit Schlüssel deutlich mehr.",
        keyRequired = false,
    ),
    SCALEWAY(
        "Scaleway (EU)", AiKind.OPENAI_COMPAT, "https://api.scaleway.ai/v1",
        "mistral-small-3.2-24b-instruct-2506", "https://console.scaleway.com/iam/api-keys", true,
        "Server in der EU (Frankreich). 1 Million Tokens kostenlos, danach nach Verbrauch.",
    ),
    LOCAL(
        "Eigener Server (Ollama, LM Studio)", AiKind.OPENAI_COMPAT, "",
        "llama3.2", "https://ollama.com/download", true,
        "Eine KI auf deinem eigenen PC im Heimnetz, z. B. mit Ollama oder LM Studio. Die Daten verlassen dein Netz nicht. Unten die Adresse des PCs eintragen.",
        keyRequired = false,
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
    XAI(
        "xAI (Grok)", AiKind.OPENAI_COMPAT, "https://api.x.ai/v1",
        "grok-4", "https://console.x.ai", false,
        "Kostenpflichtig, Abrechnung nach Verbrauch. Anbieter aus den USA.",
    ),
    TOGETHER(
        "Together AI", AiKind.OPENAI_COMPAT, "https://api.together.xyz/v1",
        "meta-llama/Llama-3.3-70B-Instruct-Turbo", "https://api.together.ai/settings/api-keys", false,
        "Viele offene Modelle, Abrechnung nach Verbrauch (teils mit Startguthaben).",
    ),
    NEBIUS(
        "Nebius", AiKind.OPENAI_COMPAT, "https://api.tokenfactory.nebius.com/v1",
        "meta-llama/Llama-3.3-70B-Instruct", "https://tokenfactory.nebius.com", false,
        "Offene Modelle, Abrechnung nach Verbrauch. Anbieter mit Sitz in den Niederlanden.",
    ),
    IONOS(
        "IONOS (Deutschland)", AiKind.OPENAI_COMPAT, "https://openai.inference.de-txl.ionos.com/v1",
        "openai/gpt-oss-120b", "https://dcd.ionos.com", false,
        "Server in Deutschland (Berlin). Abrechnung nach Verbrauch; als Schlüssel dient ein IONOS-Cloud-Token.",
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

    /** Adresse des eigenen Servers, z. B. http://192.168.178.20:11434/v1 */
    fun customUrl(context: Context): String = prefs(context)?.getString("eigene_adresse", "").orEmpty()

    fun setCustomUrl(context: Context, url: String) {
        prefs(context)?.edit()?.putString("eigene_adresse", url.trim().trimEnd('/'))?.apply()
    }

    /** Dienst ohne Schluessel bewusst eingeschaltet (z. B. OVHcloud mit niedrigem Limit) */
    fun keylessEnabled(context: Context, provider: AiProvider): Boolean =
        prefs(context)?.getBoolean("ohne_schluessel_${provider.name}", false) ?: false

    fun setKeylessEnabled(context: Context, provider: AiProvider, on: Boolean) {
        prefs(context)?.edit()?.putBoolean("ohne_schluessel_${provider.name}", on)?.apply()
    }

    /** Basis-Adresse fuer Anfragen (beim eigenen Server die eingetragene Adresse). */
    fun baseUrl(context: Context, provider: AiProvider): String =
        if (provider == AiProvider.LOCAL) customUrl(context) else provider.baseUrl

    /** Ist der Dienst nutzbar: Schluessel hinterlegt, eigener Server eingetragen oder ohne Schluessel eingeschaltet. */
    fun isReady(context: Context, provider: AiProvider): Boolean = when {
        provider == AiProvider.LOCAL -> isAllowedCustomUrl(customUrl(context))
        apiKey(context, provider).isNotBlank() -> true
        !provider.keyRequired -> keylessEnabled(context, provider)
        else -> false
    }

    fun isConfigured(context: Context): Boolean = isReady(context, provider(context))

    /**
     * Eigener Server: https immer, http nur im eigenen Netz (localhost, 10.x, 192.168.x, 172.16-31.x, 100.64-127.x,
     * Namen auf .local, .lan, .fritz.box, .home.arpa). So gehen unverschluesselte Anfragen nie ins Internet.
     */
    fun isAllowedCustomUrl(url: String): Boolean {
        val u = runCatching { java.net.URL(url) }.getOrNull() ?: return false
        if (u.host.isNullOrBlank()) return false
        if (u.protocol == "https") return true
        if (u.protocol != "http") return false
        val h = u.host.lowercase()
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".fritz.box") || h == "fritz.box" || h.endsWith(".home.arpa")) return true
        val parts = h.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || h.count { it == '.' } != 3) return false
        val (a, b) = parts[0] to parts[1]
        return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31) || (a == 100 && b in 64..127)
    }
}
