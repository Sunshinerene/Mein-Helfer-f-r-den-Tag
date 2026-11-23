package de.meinhelfer.tag.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    private val waiting = mutableListOf<(TextToSpeech?) -> Unit>()

    /** Sprachausgabe starten (gewählte Engine und Stimme) und [then] aufrufen, sobald sie bereit ist (null = nicht verfügbar). */
    private fun ensure(app: Context, then: (TextToSpeech?) -> Unit) {
        val t = tts
        if (t != null && ready) {
            then(t)
            return
        }
        waiting += then
        if (t != null) return
        val enginePkg = AppPrefs.speechEngine(app).takeIf { it.isNotBlank() }
        tts = TextToSpeech(app, { status ->
            main.post {
                ready = status == TextToSpeech.SUCCESS
                val engine = tts
                if (!ready || engine == null) {
                    tts = null
                    val list = waiting.toList()
                    waiting.clear()
                    list.forEach { it(null) }
                    return@post
                }
                val voiceName = AppPrefs.speechVoice(app)
                val voice = voiceName.takeIf { it.isNotBlank() }?.let { n -> runCatching { engine.voices?.firstOrNull { it.name == n } }.getOrNull() }
                if (voice != null) {
                    engine.voice = voice
                } else {
                    val lang = engine.setLanguage(Locale.GERMANY)
                    if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(app, "Für Deutsch fehlt die Stimme. Android-Einstellungen → Sprachausgabe → Sprachdaten installieren.", Toast.LENGTH_LONG).show()
                    }
                }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        partIds[utteranceId]?.let { _part.value = it }
                    }
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
                val list = waiting.toList()
                waiting.clear()
                list.forEach { it(engine) }
            }
        }, enginePkg)
    }

    /** Nach Wechsel von Engine oder Stimme: beim nächsten Vorlesen neu starten. */
    fun reset() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }

    /** Installierte Sprachausgaben (Paketname to Name), z. B. Google-Sprachausgabe oder SherpaTTS. */
    fun engines(context: Context): List<Pair<String, String>> = runCatching {
        val probe = TextToSpeech(context.applicationContext, null)
        val list = probe.engines.map { it.name to it.label }
        probe.shutdown()
        list
    }.getOrDefault(emptyList())

    /**
     * Deutsche Stimmen der gewählten Sprachausgabe, die ohne Internet arbeiten (Name to Beschreibung).
     * Stimmen, die Internet brauchen, fehlen absichtlich: Der vorgelesene Text würde sonst das Handy verlassen.
     */
    fun germanVoices(context: Context, result: (List<Pair<String, String>>) -> Unit) {
        ensure(context.applicationContext) { engine ->
            val voices = runCatching { engine?.voices.orEmpty() }.getOrDefault(emptySet())
                .filter { it.locale.language == "de" && !it.isNetworkConnectionRequired }
                .sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }.thenBy { it.name })
                .map { v ->
                    val quality = when {
                        v.quality >= android.speech.tts.Voice.QUALITY_VERY_HIGH -> "sehr gut"
                        v.quality >= android.speech.tts.Voice.QUALITY_HIGH -> "gut"
                        else -> "normal"
                    }
                    v.name to (v.name.replace('_', ' ').replace('-', ' ') + " · " + v.locale.displayCountry + " · Qualität " + quality)
                }
            result(voices)
        }
    }

    private val partIds = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val _part = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** Welcher Abschnitt von [speakParts] gerade gesprochen wird (zum Weiterlesen nach einer Pause). */
    val part: kotlinx.coroutines.flow.StateFlow<Int> = _part

    /** Abschnitte (z. B. Sätze) ab [start] vorlesen; [part] zeigt, wo gerade gelesen wird. */
    fun speakParts(context: Context, owner: String, parts: List<String>, start: Int = 0) {
        val app = context.applicationContext
        val list = parts.map { clean(it) }
        if (list.drop(start).all { it.isBlank() }) return
        _speaking.value = owner
        _part.value = start
        ensure(app) { engine ->
            if (engine == null) {
                _speaking.value = null
            } else if (_speaking.value == owner) {
                engine.setSpeechRate(AppPrefs.speechRate(app))
                partIds.clear()
                var first = true
                for (i in start until list.size) {
                    if (list[i].isBlank()) continue
                    val id = "teil_${System.nanoTime()}_$i"
                    partIds[id] = i
                    lastId = id
                    engine.speak(list[i], if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
                    first = false
                }
            }
        }
    }

    fun speak(context: Context, owner: String, text: String) {
        val app = context.applicationContext
        val clean = clean(text)
        if (clean.isBlank()) return
        // Was Sunny sagt, für heute merken (für den Sprachschatz am Abend)
        if (owner.startsWith("assistent") && !owner.contains("rundgang") && !owner.contains("probe") && !owner.contains("vorschau")) {
            runCatching { de.meinhelfer.tag.ai.SunnyMemory.log(app, owner, clean) }
        }
        _speaking.value = owner
        ensure(app) { engine ->
            if (engine == null) {
                _speaking.value = null
                Toast.makeText(app, "Vorlesen ist auf diesem Handy nicht verfügbar. Prüfe in den Android-Einstellungen die Sprachausgabe.", Toast.LENGTH_LONG).show()
            } else if (_speaking.value == owner) {
                say(app, clean)
            }
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

    /** Zeitpunkt des letzten Anhaltens (z. B. „⏹ Stopp“), damit automatische Abläufe nicht weiterlaufen. */
    @Volatile
    var stoppedAt = 0L
        private set

    fun stop() {
        stoppedAt = System.currentTimeMillis()
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

/** Auswahl von Sprachausgabe und Stimme (nur deutsche Stimmen ohne Internet). */
@Composable
fun VoiceChooser() {
    val context = LocalContext.current
    val engines = remember { Speech.engines(context) }
    var engine by remember { mutableStateOf(AppPrefs.speechEngine(context)) }
    var voice by remember { mutableStateOf(AppPrefs.speechVoice(context)) }
    var voices by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(engine) {
        voices = null
        Speech.germanVoices(context) { voices = it }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DropdownField(
            label = "Sprachausgabe",
            value = engines.firstOrNull { it.first == engine }?.second ?: "Standard des Handys",
            options = listOf("Standard des Handys") + engines.map { it.second },
            editable = false,
            onValueChange = { chosen ->
                val pkg = engines.firstOrNull { it.second == chosen }?.first.orEmpty()
                engine = pkg
                voice = ""
                AppPrefs.setSpeech(context, pkg, "")
                Speech.reset()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        val list = voices
        when {
            list == null -> Text("Stimmen werden geladen …", style = MaterialTheme.typography.bodySmall)
            list.isEmpty() -> Text(
                "Diese Sprachausgabe hat keine deutsche Stimme, die ohne Internet arbeitet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            else -> DropdownField(
                label = "Stimme",
                value = list.firstOrNull { it.first == voice }?.second ?: "Standardstimme Deutsch",
                options = listOf("Standardstimme Deutsch") + list.map { it.second },
                editable = false,
                onValueChange = { chosen ->
                    val name = list.firstOrNull { it.second == chosen }?.first.orEmpty()
                    voice = name
                    AppPrefs.setSpeech(context, engine, name)
                    Speech.reset()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            "Stimmen, die Internet brauchen, zeige ich nicht an: Der vorgelesene Text (z. B. Medikamente) würde sonst das Handy verlassen. " +
                "Natürlicher klingt eine KI-Stimme ohne Internet: die freie App „SherpaTTS“ (F-Droid) installieren, darin eine deutsche Piper-Stimme laden " +
                "(z. B. Thorsten oder Kerstin), dann hier als Sprachausgabe wählen.",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://f-droid.org/packages/org.woheller69.ttsengine/"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }) { Text("SherpaTTS bei F-Droid ansehen") }
    }
}

/**
 * Vorlesen für Empfehlungen und Hinweise: Knopf „🔊 Vorlesen“ und, wenn eingestellt
 * (Einstellungen → Persönlicher Assistent → „Empfehlungen gleich vorlesen“), liest Sunny beim Erscheinen gleich vor.
 * Sprecher beginnt mit „assistent“, damit Sunny sich dazu einblendet.
 */
@Composable
fun HintReader(key: String, auto: Boolean = true, modifier: Modifier = Modifier, text: () -> String) {
    val context = LocalContext.current
    val owner = "assistent-hinweis-$key"
    androidx.compose.runtime.LaunchedEffect(key) {
        if (auto && AppPrefs.assistantEnabled(context) && AppPrefs.assistantReadHints(context)) {
            kotlinx.coroutines.delay(500)
            Speech.speak(context, owner, text())
        }
    }
    SpeakButton(owner = owner, label = "🔊 Vorlesen", modifier = modifier, text = text)
}
