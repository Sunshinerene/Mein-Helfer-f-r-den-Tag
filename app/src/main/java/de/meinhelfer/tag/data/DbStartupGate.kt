package de.meinhelfer.tag.data

/** Pure composition of startup checks; no database or filesystem mutation. */
internal object DbStartupGate {
    fun decide(
        cipherAvailable: Boolean,
        files: DbFileInspection.Result,
        key: DbStartupPolicy.KeyState,
        lifecycle: DbLifecycleStore.State?,
        firstRunConfirmed: Boolean,
    ): DbStartupPolicy.Decision {
        if (lifecycle == DbLifecycleStore.State.RESET_PENDING ||
            lifecycle == DbLifecycleStore.State.RECOVERY_REQUIRED) {
            return DbStartupPolicy.Decision.RecoveryRequired
        }
        val decision = DbStartupPolicy.decide(cipherAvailable, files.state, key, files.migrationArtifacts)
        if (decision != DbStartupPolicy.Decision.CreateEncrypted) return decision
        val allowed = DbCreationPolicy.allowsCreation(
            firstRunConfirmed = firstRunConfirmed &&
                (lifecycle == DbLifecycleStore.State.FIRST_RUN || lifecycle == null) &&
                key == DbStartupPolicy.KeyState.Missing,
            resetCompleted = lifecycle == DbLifecycleStore.State.RESET_COMPLETED,
            previouslyInitialized = lifecycle == DbLifecycleStore.State.INITIALIZED,
            resetPending = false,
            migrationArtifacts = files.migrationArtifacts,
        )
        return if (allowed) decision else DbStartupPolicy.Decision.RecoveryRequired
    }
}
