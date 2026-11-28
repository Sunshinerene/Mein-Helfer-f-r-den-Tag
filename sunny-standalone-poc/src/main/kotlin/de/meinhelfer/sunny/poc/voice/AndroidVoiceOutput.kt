package de.meinhelfer.sunny.poc.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice

internal class AndroidVoiceOutput(context: Context) : VoiceOutput {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var sequence = 0L
    private var current: Playback? = null

    private class Playback(
        val id: String,
        val sample: VoiceSample,
        val notify: (VoiceEvent) -> Unit,
    ) {
        var engine: TextToSpeech? = null
        var timeout: Runnable? = null
    }

    override fun play(sample: VoiceSample, callback: (VoiceEvent) -> Unit) {
        val playback = Playback("sunny-s2-" + ++sequence, sample, callback)
        current = playback
        scheduleTimeout(playback, 8_000L, VoiceEvent.Unavailable)
        try {
            playback.engine = TextToSpeech(appContext) { status ->
                // Initialization can complete before the constructor returns.
                main.post { onInitialized(playback, status) }
            }
        } catch (_: RuntimeException) {
            callback(VoiceEvent.Unavailable)
        }
    }

    private fun onInitialized(playback: Playback, status: Int) {
        if (current !== playback) return
        val engine = playback.engine
        if (status != TextToSpeech.SUCCESS || engine == null) {
            playback.notify(VoiceEvent.Unavailable)
            return
        }

        val selected = try {
            setInstalledOfflineVoice(engine)
        } catch (_: RuntimeException) {
            false
        }
        if (!selected) {
            playback.notify(VoiceEvent.Unavailable)
            return
        }

        scheduleTimeout(playback, 30_000L, VoiceEvent.Error)
        try {
            if (engine.setOnUtteranceProgressListener(listener(playback)) != TextToSpeech.SUCCESS ||
                engine.speak(playback.sample.text, TextToSpeech.QUEUE_FLUSH, Bundle(), playback.id) != TextToSpeech.SUCCESS
            ) {
                playback.notify(VoiceEvent.Error)
            }
        } catch (_: RuntimeException) {
            playback.notify(VoiceEvent.Error)
        }
    }

    private fun setInstalledOfflineVoice(engine: TextToSpeech): Boolean {
        val voices = engine.voices.orEmpty()
        val candidate = chooseOfflineGermanVoice(voices.map(::describe)) ?: return false
        val voice = voices.first { describe(it) == candidate }
        if (engine.setVoice(voice) != TextToSpeech.SUCCESS) return false
        return engine.voice?.let(::describe) == candidate
    }

    private fun describe(voice: Voice) = VoiceCandidate(
        name = voice.name,
        locale = voice.locale,
        requiresNetwork = voice.isNetworkConnectionRequired,
        installed = voice.features?.let { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it } == true,
    )

    private fun listener(playback: Playback) = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = post(utteranceId, VoiceEvent.Started)
        override fun onDone(utteranceId: String?) = post(utteranceId, VoiceEvent.Done)

        @Deprecated("Required by UtteranceProgressListener on Android")
        override fun onError(utteranceId: String?) = post(utteranceId, VoiceEvent.Error)

        override fun onError(utteranceId: String?, errorCode: Int) = post(utteranceId, VoiceEvent.Error)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = post(utteranceId, VoiceEvent.Stopped)

        private fun post(utteranceId: String?, event: VoiceEvent) {
            main.post {
                if (current === playback && utteranceId == playback.id) playback.notify(event)
            }
        }
    }

    private fun scheduleTimeout(playback: Playback, delay: Long, event: VoiceEvent) {
        playback.timeout?.let(main::removeCallbacks)
        val timeout = Runnable { if (current === playback) playback.notify(event) }
        playback.timeout = timeout
        main.postDelayed(timeout, delay)
    }

    override fun cancel() {
        val playback = current ?: return
        current = null
        playback.timeout?.let(main::removeCallbacks)
        playback.engine?.let { engine ->
            runCatching { engine.stop() }
            runCatching { engine.shutdown() }
        }
    }
}
