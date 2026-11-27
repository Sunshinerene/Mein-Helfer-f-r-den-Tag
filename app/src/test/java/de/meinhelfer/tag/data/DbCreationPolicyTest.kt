package de.meinhelfer.tag.data

import org.junit.Assert.*
import org.junit.Test

class DbCreationPolicyTest {
    @Test fun confirmedFirstRun() {
        assertTrue(DbCreationPolicy.allowsCreation(true, false, false, false, false))
    }
    @Test fun missingExistingDatabase() {
        assertFalse(DbCreationPolicy.allowsCreation(true, false, true, false, false))
        assertFalse(DbCreationPolicy.allowsCreation(false, false, true, false, false))
    }
    @Test fun completedReset() {
        assertTrue(DbCreationPolicy.allowsCreation(false, true, true, false, false))
    }
    @Test fun unconfirmedFirstRun() {
        assertFalse(DbCreationPolicy.allowsCreation(false, false, false, false, false))
    }
    @Test fun incompleteResetOrMigrationAlwaysBlocks() {
        for (first in listOf(false, true))
            for (reset in listOf(false, true))
                for (initialized in listOf(false, true)) {
                    assertFalse(DbCreationPolicy.allowsCreation(first, reset, initialized, true, false))
                    assertFalse(DbCreationPolicy.allowsCreation(first, reset, initialized, false, true))
                    assertFalse(DbCreationPolicy.allowsCreation(first, reset, initialized, true, true))
                }
    }
}
