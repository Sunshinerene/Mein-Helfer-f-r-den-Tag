package de.meinhelfer.sunny.poc.brain

import de.meinhelfer.sunny.core.LocalRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NativeBrainCheckTest {
    @Test fun acknowledgement() = runBlocking {
        assertEquals("Native lokale Klassifikation erfolgreich: ACK.", NativeBrainCheck(LocalRuntime { "ACK" }).run())
    }
    @Test fun clarification() = runBlocking {
        assertEquals("Native lokale Klassifikation erfolgreich: CLARIFY.", NativeBrainCheck(LocalRuntime { "CLARIFY" }).run())
    }
    @Test fun invalidOutput() = runBlocking {
        assertTrue(NativeBrainCheck(LocalRuntime { "ACK extra" }).run().contains("nicht bestanden"))
    }
    @Test fun runtimeFailure() = runBlocking {
        assertTrue(NativeBrainCheck(LocalRuntime { error("private detail") }).run().contains("nicht bestanden"))
    }
}
