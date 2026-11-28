package de.meinhelfer.sunny.core.memory

import de.meinhelfer.sunny.core.NanoClock
import java.util.UUID

/**
 * Synthetic, process-local implementation. Operations are serialized per instance and their
 * snapshots are detached. Expired session records are inaccessible immediately and reclaimed
 * on the next authorized session write. A correction preserves age and insertion order.
 */
class InMemoryStore(
    private val policy: MemoryPolicy = MemoryPolicy.DenyAll,
    private val limits: MemoryLimits = MemoryLimits(),
    private val clock: NanoClock = NanoClock(System::nanoTime),
) : MemoryRecords, SessionContext, LongTermMemory {
    private data class StoredRecord(
        val target: MemoryTarget,
        val record: MemoryRecord,
        val sessionStartNanos: Long?,
    ) {
        fun isExpired(now: Long, ttl: Long): Boolean =
            sessionStartNanos != null && now - sessionStartNanos >= ttl
    }

    private val records = linkedMapOf<MemoryId, StoredRecord>()
    private val optedIn = mutableSetOf<MemoryScope>()

    @Synchronized
    override fun setLongTermOptIn(scope: MemoryScope, enabled: Boolean): MemoryResult<Unit> {
        val target = MemoryTarget.LongTerm(scope)
        if (!policy.allows(target, MemoryPermission.SetOptIn)) return MemoryResult.Denied
        if (enabled) {
            if (scope !in optedIn && optedIn.size >= limits.maxOptedInScopes) {
                return MemoryResult.CapacityReached
            }
            optedIn.add(scope)
        } else {
            optedIn.remove(scope)
            records.values.removeAll { it.target == target }
        }
        return MemoryResult.Success(Unit)
    }

    @Synchronized
    override fun save(target: MemoryTarget, value: MemoryValue): MemoryResult<MemoryRecord> {
        if (!allows(target, MemoryPermission.Save)) return MemoryResult.Denied
        val isSession = target is MemoryTarget.Session
        val now = if (isSession) clock.nowNanos() else null
        if (now != null) records.values.removeAll { it.isExpired(now, limits.sessionTtlNanos) }
        val count = records.values.count { (it.target is MemoryTarget.Session) == isSession }
        val capacity = if (isSession) limits.maxSessionRecords else limits.maxLongTermRecords
        if (count >= capacity) return MemoryResult.CapacityReached

        val record = MemoryRecord(MemoryId(UUID.randomUUID()), value.text, value.origin)
        records[record.id] = StoredRecord(target, record, now)
        return MemoryResult.Success(record)
    }

    @Synchronized
    override fun correct(target: MemoryTarget, id: MemoryId, value: MemoryValue): MemoryResult<MemoryRecord> {
        if (!allows(target, MemoryPermission.Correct)) return MemoryResult.Denied
        val entry = find(target, id) ?: return MemoryResult.NotFound
        val record = entry.record.copy(text = value.text, correctionOrigin = value.origin)
        records[id] = entry.copy(record = record)
        return MemoryResult.Success(record)
    }

    @Synchronized
    override fun delete(target: MemoryTarget, id: MemoryId): MemoryResult<Unit> {
        if (!allows(target, MemoryPermission.Delete)) return MemoryResult.Denied
        find(target, id) ?: return MemoryResult.NotFound
        records.remove(id)
        return MemoryResult.Success(Unit)
    }

    @Synchronized
    override fun clearSession(session: MemoryTarget.Session): MemoryResult<Unit> {
        if (!allows(session, MemoryPermission.ClearSession)) return MemoryResult.Denied
        records.values.removeAll { it.target == session }
        return MemoryResult.Success(Unit)
    }

    @Synchronized
    override fun selectContext(
        session: MemoryTarget.Session,
        budget: MemoryContextBudget,
    ): MemoryResult<List<MemoryRecord>> {
        val longTerm = MemoryTarget.LongTerm(session.scope)
        val sessionAllowed = allows(session, MemoryPermission.SelectContext)
        val longTermAllowed = allows(longTerm, MemoryPermission.SelectContext)
        if (!sessionAllowed && !longTermAllowed) return MemoryResult.Denied

        val now = if (sessionAllowed) clock.nowNanos() else 0L
        var remaining = budget.maxUtf16Units
        val selected = mutableListOf<MemoryRecord>()
        // Newest explicit writes first. Skip entries that do not fit, without truncating text.
        for (entry in records.values.toList().asReversed()) {
            val visible = when (entry.target) {
                session -> sessionAllowed && !entry.isExpired(now, limits.sessionTtlNanos)
                longTerm -> longTermAllowed
                else -> false
            }
            if (visible && entry.record.text.length <= remaining) {
                selected.add(entry.record)
                remaining -= entry.record.text.length
                if (selected.size == budget.maxRecords || remaining == 0) break
            }
        }
        return MemoryResult.Success(selected.toList())
    }

    @Synchronized
    override fun export(scope: MemoryScope): MemoryResult<List<MemoryRecord>> {
        val target = MemoryTarget.LongTerm(scope)
        if (!allows(target, MemoryPermission.Export)) return MemoryResult.Denied
        return MemoryResult.Success(records.values.filter { it.target == target }.map { it.record })
    }

    private fun allows(target: MemoryTarget, permission: MemoryPermission): Boolean =
        policy.allows(target, permission) &&
            (target !is MemoryTarget.LongTerm || target.scope in optedIn)

    private fun find(target: MemoryTarget, id: MemoryId): StoredRecord? {
        val entry = records[id]?.takeIf { it.target == target } ?: return null
        if (target is MemoryTarget.Session && entry.isExpired(clock.nowNanos(), limits.sessionTtlNanos)) {
            return null
        }
        return entry
    }
}
