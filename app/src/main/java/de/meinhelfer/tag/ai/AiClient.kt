package de.meinhelfer.tag.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Limit des Dienstes erreicht oder Dienst ueberlastet. Der Nutzer kann dann einen anderen Dienst waehlen. */
class AiLimitException(message: String) : IllegalStateException(message)

/** Schickt eine Anfrage an den gewaehlten KI-Dienst. Nur auf ausdruecklichen Tipp des Nutzers. */
object AiClient {
    const val DISCLAIMER =
        "KI-Hinweis, nur ein Anhaltspunkt. Kann Fehler enthalten und ist keine ärztliche Empfehlung. Sie ersetzt weder Arzt noch Apotheke."

    const val SYSTEM_PROMPT =
        "Du unterstützt pflegende Angehörige im Alltag. Du bist kein Arzt und kein Apotheker und ersetzt beide nie. " +
            "Stelle keine Diagnosen, empfiehl keine Dosierungen und rate nie, Medikamente abzusetzen oder zu ändern. " +
            "Gib nur Anhaltspunkte, was man mit dem Hausarzt oder der Apotheke besprechen könnte " +
            "(z. B. den Medikamentenplan prüfen lassen, wenn sich Medikamente überschneiden oder nicht mehr passen könnten). " +
            "Antworte auf Deutsch in höchstens 5 kurzen Stichpunkten. Jede Zeile beginnt mit „- “. " +
            "Wenn nichts auffällt, sage das in einem Stichpunkt. Keine Einleitung, kein Schlusssatz."

    /** Zerlegt die Antwort in Stichpunkte (Zeilen mit "- " oder "* "). */
    fun bullets(answer: String): List<String> {
        val lines = answer.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val bullets = lines.filter { it.startsWith("- ") || it.startsWith("* ") || it.startsWith("• ") }
            .map { it.drop(2).trim() }
        return bullets.ifEmpty { lines }
    }

    /** [maxTokens]: Platz fuer die Antwort (laengere Listen wie ein Medikamentenplan brauchen mehr). */
    suspend fun ask(context: Context, system: String, user: String, maxTokens: Int = 1500): Result<String> = withContext(Dispatchers.IO) {
        val provider = AiConfig.provider(context)
        val key = AiConfig.apiKey(context, provider)
        if (!AiConfig.isReady(context, provider)) {
            return@withContext Result.failure(
                IllegalStateException(if (provider == AiProvider.LOCAL) "Keine gültige Adresse für den eigenen Server eingetragen." else "Kein Schlüssel hinterlegt."),
            )
        }
        val model = AiConfig.model(context, provider)
        try {
            Result.success(call(AiConfig.baseUrl(context, provider), provider, key, model, system, user, maxTokens))
        } catch (e: java.net.ConnectException) {
            Result.failure(IllegalStateException(if (provider == AiProvider.LOCAL) "Der eigene Server antwortet nicht. Läuft er, und ist das Handy im selben WLAN?" else "Keine Verbindung zum Dienst."))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(IllegalStateException("Der Dienst hat zu lange gebraucht. Bitte nochmal versuchen oder ein kleineres Modell wählen."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val NO_CHAT = listOf(
        "embed", "whisper", "tts", "dall-e", "moderation", "davinci", "babbage", "audio", "realtime",
        "image", "transcribe", "imagen", "veo", "aqa", "guard", "rerank", "search", "vision-preview",
    )

    /** Fragt beim Dienst die verfuegbaren Chat-Modelle ab (nur mit dem eigenen Schluessel, ohne Personendaten). */
    suspend fun listModels(context: Context, provider: AiProvider, key: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = AiConfig.baseUrl(context, provider)
            val url = provider.modelsUrl ?: when (provider.kind) {
                AiKind.GEMINI -> "$base/models?pageSize=200"
                AiKind.ANTHROPIC -> "$base/models?limit=100"
                AiKind.OPENAI_COMPAT -> "$base/models"
            }
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "MeinHelfer-App")
            when (provider.kind) {
                AiKind.GEMINI -> connection.setRequestProperty("x-goog-api-key", key)
                AiKind.ANTHROPIC -> {
                    connection.setRequestProperty("x-api-key", key)
                    connection.setRequestProperty("anthropic-version", "2023-06-01")
                }
                AiKind.OPENAI_COMPAT -> if (key.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $key")
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException(errorMessage(code, text))
            val names = mutableListOf<String>()
            // GitHub liefert eine Liste ohne "data"-Huelle
            val json = if (text.trimStart().startsWith("[")) JSONObject().put("data", JSONArray(text)) else JSONObject(text)
            if (provider.kind == AiKind.GEMINI) {
                val arr = json.optJSONArray("models") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val methods = item.optJSONArray("supportedGenerationMethods")
                    var chat = false
                    if (methods != null) for (j in 0 until methods.length()) if (methods.getString(j) == "generateContent") chat = true
                    if (chat) names.add(item.optString("name").removePrefix("models/"))
                }
            } else {
                val arr = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until arr.length()) names.add(arr.getJSONObject(i).optString("id"))
            }
            val result = names.filter { n -> n.isNotBlank() && NO_CHAT.none { n.lowercase().contains(it) } }.distinct().sorted()
            // Bei OpenRouter zuerst die kostenlosen Modelle
            if (provider == AiProvider.OPENROUTER) result.sortedBy { if (it.endsWith(":free")) 0 else 1 } else result
        }
    }

    private fun call(base: String, provider: AiProvider, key: String, model: String, system: String, user: String, maxTokens: Int): String {
        val (url, headers, body) = when (provider.kind) {
            AiKind.OPENAI_COMPAT -> Triple(
                "$base/chat/completions",
                if (key.isNotBlank()) mapOf("Authorization" to "Bearer $key") else emptyMap(),
                JSONObject()
                    .put("model", model)
                    // Neuere OpenAI-Modelle kennen nur noch max_completion_tokens
                    .put(if (provider == AiProvider.OPENAI) "max_completion_tokens" else "max_tokens", maxTokens)
                    .put(
                        "messages",
                        JSONArray()
                            .put(JSONObject().put("role", "system").put("content", system))
                            .put(JSONObject().put("role", "user").put("content", user)),
                    ),
            )
            AiKind.ANTHROPIC -> Triple(
                "$base/messages",
                mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"),
                JSONObject()
                    .put("model", model)
                    .put("max_tokens", maxTokens)
                    .put("system", system)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user))),
            )
            AiKind.GEMINI -> Triple(
                "$base/models/$model:generateContent",
                mapOf("x-goog-api-key" to key),
                JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", user))),
                        ),
                    ),
            )
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        // Eigene Server auf dem PC brauchen teils laenger
        connection.readTimeout = if (provider == AiProvider.LOCAL) 180000 else 90000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("User-Agent", "MeinHelfer-App")
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = errorMessage(code, text)
            if (code == 429 || code == 402 || code == 503) throw AiLimitException(message)
            throw IllegalStateException(message)
        }

        val json = JSONObject(text)
        val answer = when (provider.kind) {
            AiKind.OPENAI_COMPAT -> json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").let { m ->
                if (m.isNull("content")) "" else m.optString("content")
            }
            AiKind.ANTHROPIC -> json.getJSONArray("content").getJSONObject(0).optString("text")
            AiKind.GEMINI -> json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).optString("text")
        }
        // Manche Modelle schreiben ihre Ueberlegungen in <think> ... </think> davor
        val clean = answer.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        if (clean.isBlank()) throw IllegalStateException("Die KI hat nichts geantwortet. Bei Denk-Modellen hilft oft ein anderes Modell.")
        return clean
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            val json = JSONObject(body)
            val err = json.opt("error")
            when (err) {
                is JSONObject -> err.optString("message")
                is String -> err
                else -> json.optString("message")
            }
        }.getOrNull().orEmpty().take(200)
        val hint = when (code) {
            401, 403 -> "Schlüssel ungültig oder ohne Berechtigung."
            404 -> "Modell nicht gefunden. Prüfe den Modellnamen."
            429 -> "Das Limit dieses Dienstes ist erreicht (Kontingent aufgebraucht oder zu viele Anfragen)."
            402 -> "Das Guthaben bei diesem Dienst ist aufgebraucht."
            503 -> "Der Dienst ist gerade überlastet."
            else -> ""
        }
        return listOf("Fehler $code", hint, detail).filter { it.isNotBlank() }.joinToString(". ")
    }
}
