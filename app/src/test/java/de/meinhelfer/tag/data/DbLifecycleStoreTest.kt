package de.meinhelfer.tag.data

import org.junit.Assert.*
import org.junit.Test
import de.meinhelfer.tag.data.DbLifecycleStore.State

class DbLifecycleStoreTest {
    @Test fun allKnownStatesRoundTrip() {
        for (state in State.entries) {
            assertEquals(state, DbLifecycleStore.decode(state.name.toByteArray(Charsets.UTF_8)))
        }
    }

    @Test fun unknownRecordsFailClosed() {
        for (value in listOf("", "RESET", "RESET_COMPLETED\n", "initialized", "UNKNOWN")) {
            assertEquals(State.RECOVERY_REQUIRED, DbLifecycleStore.decode(value.toByteArray(Charsets.UTF_8)))
        }
    }

    @Test fun truncatedResetNeverAuthorizesCreation() {
        val value = State.RESET_COMPLETED.name
        for (length in 0 until value.length) {
            assertEquals(State.RECOVERY_REQUIRED, DbLifecycleStore.decode(value.take(length).toByteArray()))
        }
    }
}
