package de.meinhelfer.sunny.poc.voice

import de.meinhelfer.sunny.poc.emotion.EmotionEngine
import de.meinhelfer.sunny.poc.emotion.EmotionGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class VoiceSessionTest {
    @Test
    fun selectsInstalledOfflineGermanDeterministically() {
        val german = VoiceCandidate("z-de", Locale.GERMANY, requiresNetwork = false, installed = true)
        val earlier = german.copy(name = "b-de")
        val austrian = german.copy(name = "a-at", locale = Locale.forLanguageTag("de-AT"))
        val candidates = listOf(
            german.copy(name = "0-online", requiresNetwork = true),
            german.copy(name = "1-remote", installed = false),
            german.copy(name = "2-english", locale = Locale.UK),
            german,
            austrian,
            earlier,
        )
        assertEquals(earlier, chooseOfflineGermanVoice(candidates))
        assertEquals(earlier, chooseOfflineGermanVoice(candidates.reversed()))
        assertEquals(austrian, chooseOfflineGermanVoice(candidates.take(3) + austrian))
    }

    @Test
    fun neverFallsBackToOnlineUninstalledOrOtherLanguage() {
        val unavailable = listOf(
            VoiceCandidate("online", Locale.GERMANY, requiresNetwork = true, installed = true),
            VoiceCandidate("uninstalled", Locale.GERMANY, requiresNetwork = false, installed = false),
            VoiceCandidate("fr", Locale.FRANCE, requiresNetwork = false, installed = true),
        )
        assertNull(chooseOfflineGermanVoice(unavailable))
        assertNull(chooseOfflineGermanVoice(emptyList()))
    }

    @Test
    fun onlyStartCallbackSelectsTalkingAndDoneRestoresExactIdleVariant() {
        val scenario = Scenario()
        val (output, session) = scenario
        session.play(VoiceSample.Explanation)
        assertEquals(listOf(VoiceSample.Explanation), output.samples)
        assertEquals(VoicePhase.Preparing, session.state.phase)
        assertEquals(scenario.idle, scenario.frames.last())
        output.emit(VoiceEvent.Started)
        assertEquals(VoicePhase.Speaking, session.state.phase)
        assertEquals(EmotionGroup.Talking, scenario.frames.last().group)
        assertEquals("su30", scenario.frames.last().id.code)
        output.emit(VoiceEvent.Done)
        assertEquals(VoiceState(VoicePhase.Finished, VoiceSample.Explanation), session.state)
        assertEquals(scenario.idle, scenario.frames.last())
        assertEquals(1, output.cancelCount)
    }

    @Test
    fun errorWhileSpeakingRestoresIdleAndKeepsSyntheticText() {
        val scenario = Scenario()
        val (output, session) = scenario
        session.play(VoiceSample.Greeting)
        output.emit(VoiceEvent.Started)
        output.emit(VoiceEvent.Error)
        assertEquals(VoiceState(VoicePhase.Error, VoiceSample.Greeting), session.state)
        assertEquals(scenario.idle, scenario.frames.last())
        assertEquals(1, output.cancelCount)
    }

    @Test
    fun noOfflineVoiceReleasesEngineAndKeepsText() {
        val scenario = Scenario()
        val (output, session) = scenario
        session.play(VoiceSample.Explanation)
        output.emit(VoiceEvent.Unavailable)
        assertEquals(VoiceState(VoicePhase.Unavailable, VoiceSample.Explanation), session.state)
        assertEquals(listOf(scenario.idle, scenario.idle), scenario.frames)
        assertEquals(1, output.cancelCount)
    }

    @Test
    fun stopWhilePreparingIgnoresStartDuringAndAfterCancellation() {
        val scenario = Scenario()
        val (output, session) = scenario
        output.cancelEvent = VoiceEvent.Started
        session.play(VoiceSample.Greeting)
        session.stop()
        output.emit(VoiceEvent.Started)
        output.emit(VoiceEvent.Done)
        assertEquals(VoicePhase.Stopped, session.state.phase)
        assertEquals(listOf(scenario.idle, scenario.idle), scenario.frames)
        assertEquals(1, output.cancelCount)
    }

    @Test
    fun explicitStopAndPlatformStopReleaseSpeechAndRestoreIdle() {
        for (byPlatform in listOf(false, true)) {
            val scenario = Scenario()
            val (output, session) = scenario
            session.play(VoiceSample.Greeting)
            output.emit(VoiceEvent.Started)
            if (byPlatform) output.emit(VoiceEvent.Stopped) else session.stop()
            assertEquals(VoicePhase.Stopped, session.state.phase)
            assertEquals(scenario.idle, scenario.frames.last())
            session.stop()
            assertEquals(1, output.cancelCount)
        }
    }

    @Test
    fun staleCallbacksCannotChangeOrStopTheNewRequest() {
        val (output, session) = Scenario()
        session.play(VoiceSample.Greeting)
        session.stop()
        session.play(VoiceSample.Explanation)
        for (event in VoiceEvent.entries) output.emit(event, index = 0)
        assertEquals(VoiceState(VoicePhase.Preparing, VoiceSample.Explanation), session.state)
        output.emit(VoiceEvent.Started, index = 1)
        output.emit(VoiceEvent.Done, index = 0)
        assertEquals(VoicePhase.Speaking, session.state.phase)
        assertEquals(1, output.cancelCount)
        output.emit(VoiceEvent.Done, index = 1)
        assertEquals(VoicePhase.Finished, session.state.phase)
        assertEquals(2, output.cancelCount)
    }

    @Test
    fun resetDuringInitializationOrPlaybackClearsSessionAndBlocksLateEvents() {
        for (started in listOf(false, true)) {
            val scenario = Scenario()
            val (output, session) = scenario
            session.play(VoiceSample.Greeting)
            if (started) output.emit(VoiceEvent.Started)
            session.reset()
            for (event in VoiceEvent.entries) output.emit(event)
            assertEquals(VoiceState(VoicePhase.Idle), session.state)
            assertEquals(scenario.idle, scenario.frames.last())
            assertEquals(1, output.cancelCount)
        }
    }

    @Test
    fun repeatedPlayOrStartedDoesNotQueueOrRepublishPlayback() {
        val scenario = Scenario()
        val (output, session) = scenario
        session.play(VoiceSample.Greeting)
        assertTrue(session.state.phase.busy)
        session.play(VoiceSample.Explanation)
        output.emit(VoiceEvent.Started)
        output.emit(VoiceEvent.Started)
        assertEquals(listOf(VoiceSample.Greeting), output.samples)
        assertEquals(2, scenario.frames.size)
        output.emit(VoiceEvent.Done)
        output.emit(VoiceEvent.Done)
        assertFalse(session.state.phase.busy)
        assertEquals(3, scenario.frames.size)
        assertEquals(1, output.cancelCount)
    }

    @Test
    fun immediateEngineExceptionUsesFixedErrorState() {
        val scenario = Scenario()
        val (output, session) = scenario
        output.throwOnPlay = true
        session.play(VoiceSample.Greeting)
        assertEquals(VoiceState(VoicePhase.Error, VoiceSample.Greeting), session.state)
        assertEquals(scenario.idle, scenario.frames.last())
        assertEquals(1, output.cancelCount)
    }

    private class Scenario {
        private val engine = EmotionEngine()
        val idle = engine.select(EmotionGroup.Thinking, 2)
        val frames = mutableListOf<de.meinhelfer.sunny.poc.emotion.EmotionFrame>()
        val output = FakeVoice()
        val session = VoiceSession(output) { frames.add(engine.displayed(idle, it.phase == VoicePhase.Speaking)) }

        operator fun component1() = output
        operator fun component2() = session
    }

    private class FakeVoice : VoiceOutput {
        val samples = mutableListOf<VoiceSample>()
        private val callbacks = mutableListOf<(VoiceEvent) -> Unit>()
        var cancelCount = 0
            private set
        var throwOnPlay = false
        var cancelEvent: VoiceEvent? = null

        override fun play(sample: VoiceSample, callback: (VoiceEvent) -> Unit) {
            samples.add(sample)
            callbacks.add(callback)
            if (throwOnPlay) error("Synthetic engine exception")
        }

        override fun cancel() {
            cancelCount++
            cancelEvent?.let { emit(it) }
        }

        fun emit(event: VoiceEvent, index: Int = callbacks.lastIndex) {
            callbacks[index](event)
        }
    }
}
