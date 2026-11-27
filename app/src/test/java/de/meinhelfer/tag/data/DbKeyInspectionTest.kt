package de.meinhelfer.tag.data

import org.junit.Assert.*
import org.junit.Test
import de.meinhelfer.tag.data.DbStartupPolicy.KeyState
import java.util.concurrent.CancellationException

class DbKeyInspectionTest {
    @Test fun missingKey() {
        assertEquals(KeyState.Missing, DbKeyInspection.inspect { null })
    }
    @Test fun validKey() {
        assertEquals(KeyState.Present, DbKeyInspection.inspect { "aB09".repeat(16) })
    }
    @Test fun invalidKey() {
        for (key in listOf("", "a".repeat(63), "a".repeat(65), "z".repeat(64))) {
            assertEquals(KeyState.Unavailable, DbKeyInspection.inspect { key })
        }
    }
    @Test fun inaccessibleKey() {
        assertEquals(KeyState.Unavailable, DbKeyInspection.inspect { throw SecurityException("synthetic") })
    }
    @Test fun nativeFailure() {
        assertEquals(KeyState.Unavailable, DbKeyInspection.inspect { throw UnsatisfiedLinkError("synthetic") })
    }
    @Test(expected = CancellationException::class)
    fun cancellationPropagates() {
        DbKeyInspection.inspect { throw CancellationException() }
    }
    @Test(expected = InterruptedException::class)
    fun interruptionPropagates() {
        DbKeyInspection.inspect { throw InterruptedException() }
    }
    @Test fun lookupOnlyOnce() {
        var calls = 0
        DbKeyInspection.inspect { calls++; null }
        assertEquals(1, calls)
    }
}
