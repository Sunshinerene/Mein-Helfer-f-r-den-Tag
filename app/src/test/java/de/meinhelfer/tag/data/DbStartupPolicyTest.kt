package de.meinhelfer.tag.data

import org.junit.Assert.assertEquals
import org.junit.Test
import de.meinhelfer.tag.data.DbStartupPolicy.FileState
import de.meinhelfer.tag.data.DbStartupPolicy.KeyState
import de.meinhelfer.tag.data.DbStartupPolicy.Decision

class DbStartupPolicyTest {
    @Test fun all48InputCombinations() {
        var count = 0
        for (cipher in listOf(false, true))
            for (file in FileState.entries)
                for (key in KeyState.entries)
                    for (artifacts in listOf(false, true)) {
                        val expected = when {
                            !cipher -> Decision.CipherUnavailable
                            artifacts || file == FileState.Uncertain -> Decision.RecoveryRequired
                            key == KeyState.Unavailable -> Decision.KeyUnavailable
                            file == FileState.EncryptedCandidate && key == KeyState.Missing -> Decision.ExistingKeyMissing
                            file == FileState.EncryptedCandidate -> Decision.OpenEncrypted
                            file == FileState.Plaintext -> Decision.MigratePlaintext
                            else -> Decision.CreateEncrypted
                        }
                        assertEquals("$cipher/$file/$key/$artifacts", expected,
                            DbStartupPolicy.decide(cipher, file, key, artifacts))
                        count++
                    }
        assertEquals(48, count)
    }
}
