package de.meinhelfer.tag.data

import org.junit.Assert.*
import org.junit.Test
import de.meinhelfer.tag.data.DbStartupPolicy.*
import de.meinhelfer.tag.data.DbLifecycleStore.State

class DbStartupGateTest {
    private fun decide(file: FileState, key: KeyState, state: State?,
        first: Boolean = false, cipher: Boolean = true, artifacts: Boolean = false) =
        DbStartupGate.decide(cipher, DbFileInspection.Result(file, artifacts), key, state, first)

    @Test fun firstRunRequiresConfirmationAndNoExistingKey() {
        assertEquals(Decision.CreateEncrypted, decide(FileState.Missing, KeyState.Missing, null, true))
        assertEquals(Decision.RecoveryRequired, decide(FileState.Missing, KeyState.Missing, null))
        assertEquals(Decision.RecoveryRequired, decide(FileState.Missing, KeyState.Present, null, true))
    }
    @Test fun initializedMissingDatabaseNeverRecreated() {
        for (key in KeyState.entries) {
            assertNotEquals(Decision.CreateEncrypted, decide(FileState.Missing, key, State.INITIALIZED, true))
        }
    }
    @Test fun resetRequiresHealthyPrerequisites() {
        assertEquals(Decision.CreateEncrypted, decide(FileState.Missing, KeyState.Present, State.RESET_COMPLETED))
        assertEquals(Decision.CipherUnavailable, decide(FileState.Missing, KeyState.Present, State.RESET_COMPLETED, cipher = false))
        assertEquals(Decision.KeyUnavailable, decide(FileState.Missing, KeyState.Unavailable, State.RESET_COMPLETED))
        assertEquals(Decision.RecoveryRequired, decide(FileState.Missing, KeyState.Present, State.RESET_COMPLETED, artifacts = true))
    }
    @Test fun blockedLifecycleNeverOpensOrCreates() {
        for (state in listOf(State.RESET_PENDING, State.RECOVERY_REQUIRED))
            for (file in FileState.entries)
                for (key in KeyState.entries)
                    assertEquals(Decision.RecoveryRequired, decide(file, key, state, true))
    }
    @Test fun legacyExistingDatabaseRemainsSupported() {
        assertEquals(Decision.OpenEncrypted, decide(FileState.EncryptedCandidate, KeyState.Present, null))
        assertEquals(Decision.ExistingKeyMissing, decide(FileState.EncryptedCandidate, KeyState.Missing, null))
        assertEquals(Decision.MigratePlaintext, decide(FileState.Plaintext, KeyState.Missing, null))
    }
}
