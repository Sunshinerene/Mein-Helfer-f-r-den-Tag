package de.meinhelfer.sunny.core.memory

import de.meinhelfer.sunny.core.NanoClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryStoreTest {
    private val scope = MemoryScope("fixture-user-a", "fixture-person-a")
    private val session = MemoryTarget.Session(scope, "fixture-session-a")
    private val longTerm = MemoryTarget.LongTerm(scope)
    private val origin = MemoryOrigin(MemorySource.ExplicitUserInput, "fixture-1")
    private val budget = MemoryContextBudget(20, 200)
    private val allowFixtures = MemoryPolicy { _, _ -> true }

    @Test
    fun defaultPolicyDeniesAllOperations() {
        val store = InMemoryStore(clock = NanoClock { error("Denied call read the clock") })
        val id = InMemoryStore(allowFixtures).save(session, value("demo")).success().id

        assertEquals(MemoryResult.Denied, store.setLongTermOptIn(scope, true))
        assertEquals(MemoryResult.Denied, store.save(session, value("demo")))
        assertEquals(MemoryResult.Denied, store.correct(session, id, value("edit")))
        assertEquals(MemoryResult.Denied, store.delete(session, id))
        assertEquals(MemoryResult.Denied, store.clearSession(session))
        assertEquals(MemoryResult.Denied, store.selectContext(session, budget))
        assertEquals(MemoryResult.Denied, store.export(scope))
    }

    @Test
    fun sessionWritesDoNotOptInOrCopyToLongTermMemory() {
        val store = InMemoryStore(allowFixtures)
        assertEquals(MemoryResult.Denied, store.save(longTerm, value("long-term fixture")))
        assertEquals(MemoryResult.Denied, store.export(scope))
        val temporary = store.save(session, value("session fixture")).success()

        store.setLongTermOptIn(scope, true).success()
        assertTrue(store.export(scope).success().isEmpty())
        assertEquals(listOf(temporary), store.selectContext(session, budget).success())
    }

    @Test
    fun rejectedOperationsLeaveExistingStateAndConsentIntact() {
        var permitted = setOf(MemoryPermission.Save, MemoryPermission.SetOptIn)
        val store = InMemoryStore(MemoryPolicy { _, permission -> permission in permitted })
        val temporary = store.save(session, value("temporary")).success()
        store.setLongTermOptIn(scope, true).success()
        val saved = store.save(longTerm, value("original")).success()

        permitted = emptySet()
        assertEquals(MemoryResult.Denied, store.correct(longTerm, saved.id, value("denied edit")))
        assertEquals(MemoryResult.Denied, store.delete(longTerm, saved.id))
        assertEquals(MemoryResult.Denied, store.clearSession(session))
        assertEquals(MemoryResult.Denied, store.setLongTermOptIn(scope, false))
        assertEquals(MemoryResult.Denied, store.save(longTerm, value("denied write")))

        permitted = setOf(MemoryPermission.Export, MemoryPermission.SelectContext)
        assertEquals(listOf(saved), store.export(scope).success())
        assertEquals(listOf(saved, temporary), store.selectContext(session, budget).success())
    }

    @Test
    fun partitionsIsolateBothUsersBothPersonsAndSessionsEvenWithFixtureGrants() {
        val store = InMemoryStore(allowFixtures)
        val scopes = listOf(scope, scope.copy(userId = "fixture-user-b"), scope.copy(personId = "fixture-person-b"))
        val saved = scopes.mapIndexed { index, owner ->
            store.setLongTermOptIn(owner, true).success()
            store.save(MemoryTarget.LongTerm(owner), value("long-$index")).success()
        }
        val temporary = store.save(session, value("session A")).success()
        val secondSession = session.copy(sessionId = "fixture-session-b")
        val secondTemporary = store.save(secondSession, value("session B")).success()

        assertEquals(listOf(temporary, saved[0]), store.selectContext(session, budget).success())
        assertEquals(listOf(secondTemporary, saved[0]), store.selectContext(secondSession, budget).success())
        for (index in 1..2) {
            val owner = scopes[index]
            val other = MemoryTarget.LongTerm(owner)
            assertEquals(listOf(saved[index]), store.export(owner).success())
            assertEquals(listOf(saved[index]), store.selectContext(session.copy(scope = owner), budget).success())
            assertEquals(MemoryResult.NotFound, store.correct(other, saved[0].id, value("cross-scope")))
            assertEquals(MemoryResult.NotFound, store.delete(other, saved[0].id))
        }
        assertEquals(MemoryResult.NotFound, store.delete(secondSession, temporary.id))
        assertEquals(listOf(saved[0]), store.export(scope).success())
    }

    @Test
    fun policyChecksExactTargetAndIndependentExportPermission() {
        val store = InMemoryStore(MemoryPolicy { target, permission ->
            target.scope == scope && permission != MemoryPermission.Export
        })
        store.setLongTermOptIn(scope, true).success()
        val saved = store.save(longTerm, value("one fixture")).success()

        assertEquals(listOf(saved), store.selectContext(session, budget).success())
        assertEquals(MemoryResult.Denied, store.export(scope))
        val wrongScope = scope.copy(personId = "fixture-person-b")
        assertEquals(MemoryResult.Denied, store.selectContext(session.copy(scope = wrongScope), budget))
        assertEquals(MemoryResult.Denied, store.setLongTermOptIn(wrongScope, true))
    }

    @Test
    fun selectsOnlyIndividuallyAuthorizedContextPartitions() {
        var contextTarget: MemoryTarget = session
        val store = InMemoryStore(MemoryPolicy { target, permission ->
            permission != MemoryPermission.SelectContext || target == contextTarget
        })
        store.setLongTermOptIn(scope, true).success()
        val permanent = store.save(longTerm, value("long")).success()
        val temporary = store.save(session, value("session")).success()

        assertEquals(listOf(temporary), store.selectContext(session, budget).success())
        contextTarget = longTerm
        assertEquals(listOf(permanent), store.selectContext(session, budget).success())
    }

    @Test
    fun correctionKeepsIdAndOriginAndDeletionRemovesAllFutureContextAndExport() {
        val store = InMemoryStore(allowFixtures)
        store.setLongTermOptIn(scope, true).success()
        val saved = store.save(longTerm, value("original")).success()
        val editOrigin = MemoryOrigin(MemorySource.UserConfirmedHostInput, "fixture-2")
        val corrected = store.correct(longTerm, saved.id, MemoryValue("corrected", editOrigin)).success()

        assertEquals(saved.id, corrected.id)
        assertEquals(origin, corrected.origin)
        assertEquals(editOrigin, corrected.correctionOrigin)
        assertEquals("corrected", corrected.text)
        assertEquals(listOf(corrected), store.export(scope).success())
        assertEquals(listOf(corrected), store.selectContext(session, budget).success())

        store.delete(longTerm, saved.id).success()
        assertEquals(emptyList<MemoryRecord>(), store.selectContext(session, budget).success())
        assertEquals(emptyList<MemoryRecord>(), store.export(scope).success())
        assertEquals(MemoryResult.NotFound, store.delete(longTerm, saved.id))
        assertNotEquals(saved.id, store.save(longTerm, value("fresh entry")).success().id)
    }

    @Test
    fun revocationErasesOnlyItsOwnLongTermRecordsAndReenrollmentStartsEmpty() {
        val store = InMemoryStore(allowFixtures)
        val otherScope = scope.copy(userId = "fixture-user-b")
        store.setLongTermOptIn(scope, true).success()
        store.setLongTermOptIn(otherScope, true).success()
        store.save(longTerm, value("erase")).success()
        val other = store.save(MemoryTarget.LongTerm(otherScope), value("keep other scope")).success()
        val temporary = store.save(session, value("keep session")).success()

        store.setLongTermOptIn(scope, false).success()
        assertEquals(MemoryResult.Denied, store.save(longTerm, value("denied")))
        assertEquals(MemoryResult.Denied, store.export(scope))
        assertEquals(listOf(temporary), store.selectContext(session, budget).success())
        assertEquals(listOf(other), store.export(otherScope).success())
        store.setLongTermOptIn(scope, true).success()
        assertTrue(store.export(scope).success().isEmpty())
    }

    @Test
    fun ttlIsInclusiveAndCorrectionDoesNotExtendIt() {
        var now = 0L
        val store = InMemoryStore(
            allowFixtures, MemoryLimits(sessionTtlNanos = 10, maxSessionRecords = 1), NanoClock { now },
        )
        val original = store.save(session, value("temporary")).success()
        now = 9
        val corrected = store.correct(session, original.id, value("edit")).success()
        assertEquals(listOf(corrected), store.selectContext(session, budget).success())
        assertEquals(MemoryResult.CapacityReached, store.save(session, value("over capacity")))

        now = 10
        assertEquals(MemoryResult.NotFound, store.correct(session, original.id, value("expired edit")))
        assertEquals(MemoryResult.NotFound, store.delete(session, original.id))
        assertTrue(store.selectContext(session, budget).success().isEmpty())
        val fresh = store.save(session, value("fresh")).success()
        assertEquals(listOf(fresh), store.selectContext(session, budget).success())
        store.clearSession(session).success()
        assertTrue(store.selectContext(session, budget).success().isEmpty())
    }

    @Test
    fun longTermAndOptInCapacityAreIndependentOfSessionCapacity() {
        val store = InMemoryStore(
            allowFixtures, MemoryLimits(maxSessionRecords = 1, maxLongTermRecords = 1, maxOptedInScopes = 1),
        )
        store.setLongTermOptIn(scope, true).success()
        store.setLongTermOptIn(scope, true).success()
        val other = scope.copy(personId = "fixture-person-b")
        assertEquals(MemoryResult.CapacityReached, store.setLongTermOptIn(other, true))
        store.save(session, value("temporary")).success()
        val saved = store.save(longTerm, value("first")).success()
        assertEquals(MemoryResult.CapacityReached, store.save(longTerm, value("second")))
        assertEquals(listOf(saved), store.export(scope).success())

        store.setLongTermOptIn(scope, false).success()
        store.setLongTermOptIn(other, true).success()
        store.save(MemoryTarget.LongTerm(other), value("new scope")).success()
    }

    @Test
    fun contextSelectionIsDeterministicAndFitsBothLimitsWithoutTruncation() {
        val store = InMemoryStore(allowFixtures)
        store.setLongTermOptIn(scope, true).success()
        val oldest = store.save(session, value("a")).success()
        val middle = store.save(longTerm, value("bc")).success()
        store.save(session, value("too long")).success()
        val latest = store.save(session, value("😀")).success()

        repeat(3) {
            assertEquals(listOf(latest, middle), store.selectContext(session, MemoryContextBudget(2, 5)).success())
            assertEquals(listOf(latest, oldest), store.selectContext(session, MemoryContextBudget(5, 3)).success())
        }
    }

    @Test
    fun invalidInputsHaveFixedErrorsAndDoNotExposePayload() {
        assertThrows(IllegalArgumentException::class.java) { MemoryScope("invalid key", "fixture") }
        assertThrows(IllegalArgumentException::class.java) { session.copy(sessionId = "") }
        assertThrows(IllegalArgumentException::class.java) { origin.copy(reference = "/invalid") }
        assertThrows(IllegalArgumentException::class.java) { value("   ") }
        assertThrows(IllegalArgumentException::class.java) { value("x".repeat(4_097)) }
        assertThrows(IllegalArgumentException::class.java) { value("fixture\u0000secret") }
        assertThrows(IllegalArgumentException::class.java) { value("fixture\uD800") }
        assertThrows(IllegalArgumentException::class.java) { MemoryContextBudget(0, 100) }
        assertThrows(IllegalArgumentException::class.java) { MemoryLimits(sessionTtlNanos = 0) }
        val store = InMemoryStore(allowFixtures)
        val saved = store.save(session, value("fixture-secret")).success()
        assertFalse(saved.toString().contains(saved.text))
        assertFalse(value("fixture-secret").toString().contains("fixture-secret"))
    }

    @Test
    fun simultaneousWritesRespectCapacityAndSnapshotsCannotChangeTheStore() {
        val store = InMemoryStore(allowFixtures, MemoryLimits(maxLongTermRecords = 1))
        store.setLongTermOptIn(scope, true).success()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val writes = (1..2).map { index ->
                workers.submit<MemoryResult<MemoryRecord>> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    store.save(longTerm, value("fixture-$index"))
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val results = writes.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it == MemoryResult.CapacityReached })
            val record = results.filterIsInstance<MemoryResult.Success<MemoryRecord>>().single().value
            val exported = store.export(scope).success()
            store.delete(longTerm, record.id).success()
            assertEquals(listOf(record), exported)
            assertTrue(store.export(scope).success().isEmpty())
        } finally {
            start.countDown()
            workers.shutdownNow()
        }
    }

    private fun value(text: String) = MemoryValue(text, origin)

    private fun <T> MemoryResult<T>.success(): T = (this as MemoryResult.Success).value
}
