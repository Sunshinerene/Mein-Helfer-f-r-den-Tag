package de.meinhelfer.tag.data

/** Creation authorization only; cipher, key and artifact checks remain mandatory. */
internal object DbCreationPolicy {
    fun allowsCreation(
        firstRunConfirmed: Boolean,
        resetCompleted: Boolean,
        previouslyInitialized: Boolean,
        resetPending: Boolean,
        migrationArtifacts: Boolean,
    ): Boolean {
        if (resetPending || migrationArtifacts) return false
        return resetCompleted || (firstRunConfirmed && !previouslyInitialized)
    }
}
