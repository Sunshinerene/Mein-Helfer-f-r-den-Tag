package de.meinhelfer.tag.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Vorlesen mit der Sprachausgabe des Handys (Text-to-Speech). Alles bleibt auf dem Handy,
 * sofern die Sprachausgabe des Handys nicht selbst etwas anderes tut (Google-Sprachausgabe arbeitet mit heruntergeladenen Stimmen).
 */
object Speech {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private val main = Handler(Looper.getMainLooper())

    private val _speaking = MutableStateFlow<String?>(null)

    /** Schlüssel des Knopfs, der gerade vorliest (null = still). */
    val speaking: StateFlow<String?> = _speaking

    private var lastId = ""

    /** Symbole, Emojis und Trennzeichen entfernen bzw. in Pausen verwandeln, damit der Text natürlich klingt. */
    internal fun clean(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val type = Character.getType(cp)
            val skip = type == Character.OTHER_SYMBOL.toInt() || type == Character.SURROGATE.toInt() ||
                cp == 0xFE0F || cp == 0x200D || cp in 0x1F000..0x1FAFF
            if (!skip) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString()
            .replace(" · ", ", ")
            .replace("·", ",")
            .replace(" – ", ", ")
            .replace("•", "")
            .replace(Regex("_{2,}"), " ")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()
    }

    fun speak(context: Context, owner: String, text: String) {
        val app = context.applicationContext
        val clean = clean(text)
        if (clean.isBlank()) return
        _speaking.value = owner
        val t = tts
        if (t == null) {
            pending = clean
            tts = TextToSpeech(app) { status ->
                main.post {
                    ready = status == TextToSpeech.SUCCESS
                    val engine = tts
                    if (!ready || engine == null) {
                        _speaking.value = null
                        tts = null
                        Toast.makeText(app, "Vorlesen ist auf diesem Handy nicht verfügbar. Prüfe in den Android-Einstellungen die Sprachausgabe.", Toast.LENGTH_LONG).show()
                        return@post
                    }
                    val lang = engine.setLanguage(Locale.GERMANY)
                    if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(app, "Für Deutsch fehlt die Stimme. Android-Einstellungen → Sprachausgabe → Sprachdaten installieren.", Toast.LENGTH_LONG).show()
                    }
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == lastId) _speaking.value = null
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _speaking.value = null
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            if (utteranceId == lastId) _speaking.value = null
                        }
                    })
                    pending?.let { say(app, it) }
                    pending = null
                }
            }
        } else if (ready) {
            say(app, clean)
        } else {
            pending = clean
        }
    }

    private fun say(context: Context, text: String) {
        val engine = tts ?: return
        engine.setSpeechRate(AppPrefs.speechRate(context))
        // Lange Texte in Stücken an Satzgrenzen
        val max = (TextToSpeech.getMaxSpeechInputLength() - 100).coerceAtLeast(500)
        val parts = mutableListOf<String>()
        var rest = text
        while (rest.length > max) {
            val cut = rest.lastIndexOfAny(charArrayOf('.', '\n', '!', '?'), max).takeIf { it > max / 2 } ?: max
            parts += rest.substring(0, cut + 1)
            rest = rest.substring(cut + 1)
        }
        if (rest.isNotBlank()) parts += rest
        parts.forEachIndexed { i, p ->
            val id = "helfer_${System.nanoTime()}_$i"
            if (i == parts.lastIndex) lastId = id
            engine.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    fun stop() {
        pending = null
        runCatching { tts?.stop() }
        _speaking.value = null
    }
}

/** Knopf „🔊 Vorlesen“ bzw. „⏹ Stopp“. Beim Verlassen der Seite hört das Vorlesen auf. */
@Composable
fun SpeakButton(owner: String, label: String = "🔊 Vorlesen", modifier: Modifier = Modifier, text: () -> String) {
    val context = LocalContext.current
    val speaking by Speech.speaking.collectAsState()
    val mine = speaking == owner
    DisposableEffect(owner) {
        onDispose { if (Speech.speaking.value == owner) Speech.stop() }
    }
    OutlinedButton(
        shape = buttonShape(),
        modifier = modifier,
        onClick = { if (mine) Speech.stop() else Speech.speak(context, owner, text()) },
    ) { Text(if (mine) "⏹ Stopp" else label) }
}

/** Abschnitte des Tagesplans als Vorlese-Text. */
internal fun planSpeech(title: String, sections: List<de.meinhelfer.tag.summary.PlanSection>): String = buildString {
    append(title).append(". ")
    if (sections.isEmpty()) append("Für heute ist nichts eingetragen.")
    sections.forEach { s ->
        append(s.title).append(": ")
        s.lines.forEach { l ->
            append(l.text.replace(Regex("_{2,}.*"), "").trim().trimEnd(':'))
            l.sub?.let { append(", ").append(it) }
            append(". ")
        }
    }
}
