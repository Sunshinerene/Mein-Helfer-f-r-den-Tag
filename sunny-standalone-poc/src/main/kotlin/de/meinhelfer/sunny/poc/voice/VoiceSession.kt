package de.meinhelfer.sunny.poc.voice

import java.util.Locale

internal enum class VoiceSample(val title: String, val text: String) {
    Greeting("Begrüßung", "Hallo. Sunny spielt einen festen Testtext ab."),
    Explanation("Testhinweis", "Dies ist eine synthetische Sprachausgabe. Der Test kann jederzeit gestoppt werden."),
}

internal data class VoiceCandidate(
    val name: String,
    val locale: Locale,
    val requiresNetwork: Boolean,
    val installed: Boolean,
)

internal fun chooseOfflineGermanVoice(candidates: Iterable<VoiceCandidate>): VoiceCandidate? =
    candidates.asSequence()
        .filter { it.locale.language == Locale.GERMAN.language && it.installed && !it.requiresNetwork }
        .minWithOrNull(compareBy<VoiceCandidate> { it.locale != Locale.GERMANY }.thenBy { it.name }
            .thenBy { it.locale.toLanguageTag() })

internal enum class VoiceEvent { Started, Done, Error, Unavailable, Stopped }

internal interface VoiceOutput {
    fun play(sample: VoiceSample, callback: (VoiceEvent) -> Unit)
    fun cancel()
}

internal enum class VoicePhase {
    Idle, Preparing, Speaking, Finished, Stopped, Unavailable, Error;

    val busy: Boolean get() = this == Preparing || this == Speaking
}

internal data class VoiceState(val phase: VoicePhase, val sample: VoiceSample? = null)

/** All calls and callbacks are confined to the same thread. */
internal class VoiceSession(
    private val output: VoiceOutput,
    private val onChanged: (VoiceState) -> Unit,
) {
    private var generation = 0L
    private var active: Long? = null
    var state = VoiceState(VoicePhase.Idle)
        private set

    fun play(sample: VoiceSample) {
        if (active != null) return
        val attempt = ++generation
        active = attempt
        update(VoiceState(VoicePhase.Preparing, sample))
        try {
            output.play(sample) { onEvent(attempt, it) }
        } catch (_: RuntimeException) {
            onEvent(attempt, VoiceEvent.Error)
        }
    }

    fun stop() {
        if (active != null) finish(VoicePhase.Stopped)
    }

    fun reset() {
        cancelActive()
        update(VoiceState(VoicePhase.Idle))
    }

    private fun onEvent(attempt: Long, event: VoiceEvent) {
        if (active != attempt) return
        when (event) {
            VoiceEvent.Started -> if (state.phase == VoicePhase.Preparing) {
                update(state.copy(phase = VoicePhase.Speaking))
            }
            VoiceEvent.Done -> finish(VoicePhase.Finished)
            VoiceEvent.Error -> finish(VoicePhase.Error)
            VoiceEvent.Unavailable -> finish(VoicePhase.Unavailable)
            VoiceEvent.Stopped -> finish(VoicePhase.Stopped)
        }
    }

    private fun finish(phase: VoicePhase) {
        cancelActive()
        update(state.copy(phase = phase))
    }

    private fun cancelActive() {
        if (active == null) return
        // Invalidate before stop/shutdown because cancellation can itself trigger a callback.
        active = null
        output.cancel()
    }

    private fun update(next: VoiceState) {
        state = next
        onChanged(next)
    }
}
