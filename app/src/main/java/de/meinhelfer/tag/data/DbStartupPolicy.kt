package de.meinhelfer.tag.data

/** Pure startup policy. No file writes, key generation or database opening. */
internal object DbStartupPolicy {
    enum class FileState { Missing, Plaintext, EncryptedCandidate, Uncertain }
    enum class KeyState { Present, Missing, Unavailable }
    enum class Decision {
        CreateEncrypted, OpenEncrypted, MigratePlaintext,
        RecoveryRequired, CipherUnavailable, KeyUnavailable, ExistingKeyMissing
    }

    fun decide(
        cipherAvailable: Boolean,
        file: FileState,
        key: KeyState,
        migrationArtifacts: Boolean,
    ): Decision {
        if (!cipherAvailable) return Decision.CipherUnavailable
        if (migrationArtifacts || file == FileState.Uncertain) return Decision.RecoveryRequired
        if (key == KeyState.Unavailable) return Decision.KeyUnavailable
        return when (file) {
            FileState.Missing -> Decision.CreateEncrypted
            FileState.Plaintext -> Decision.MigratePlaintext
            FileState.EncryptedCandidate -> if (key == KeyState.Present) {
                Decision.OpenEncrypted
            } else {
                Decision.ExistingKeyMissing
            }
            FileState.Uncertain -> Decision.RecoveryRequired
        }
    }
}
