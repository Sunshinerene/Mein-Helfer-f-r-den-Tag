package de.meinhelfer.sunny.core.memory

import java.util.UUID

private val opaqueId = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")

/** Opaque host-issued keys. These name a partition and carry no authority by themselves. */
data class MemoryScope(val userId: String, val personId: String) {
    init {
        require(opaqueId.matches(userId) && opaqueId.matches(personId)) { "Invalid memory scope" }
    }

    override fun toString(): String = "MemoryScope([redacted])"
}

sealed interface MemoryTarget {
    val scope: MemoryScope

    data class Session(override val scope: MemoryScope, val sessionId: String) : MemoryTarget {
        init {
            require(opaqueId.matches(sessionId)) { "Invalid session ID" }
        }

        override fun toString(): String = "MemoryTarget.Session([redacted])"
    }

    data class LongTerm(override val scope: MemoryScope) : MemoryTarget
}

@JvmInline
value class MemoryId(val value: UUID)

enum class MemorySource { ExplicitUserInput, UserConfirmedHostInput }

/** Source metadata supplied by the trusted caller for each explicit write. */
data class MemoryOrigin(val source: MemorySource, val reference: String) {
    init {
        require(opaqueId.matches(reference)) { "Invalid origin reference" }
    }

    override fun toString(): String = "MemoryOrigin(source=$source, reference=[redacted])"
}

data class MemoryValue(val text: String, val origin: MemoryOrigin) {
    init {
        require(text.length in 1..4_096 && text.isNotBlank()) { "Invalid memory text length" }
        require(text.codePoints().noneMatch {
            it in 0xD800..0xDFFF || (Character.isISOControl(it) && it != '\n'.code && it != '\t'.code)
        }) { "Invalid memory text" }
    }

    override fun toString(): String = "MemoryValue([redacted])"
}

data class MemoryRecord(
    val id: MemoryId,
    val text: String,
    val origin: MemoryOrigin,
    val correctionOrigin: MemoryOrigin? = null,
) {
    override fun toString(): String = "MemoryRecord(id=$id, content=[redacted])"
}

data class MemoryContextBudget(val maxRecords: Int, val maxUtf16Units: Int) {
    init {
        require(maxRecords > 0 && maxUtf16Units > 0) { "Invalid memory context budget" }
    }
}

enum class MemoryPermission { SetOptIn, Save, Correct, Delete, ClearSession, SelectContext, Export }

/**
 * Trusted host policy. Resolve caller identity outside this API and check the exact target,
 * operation, and current grant. IDs supplied in requests and model text never confer grants.
 * Called within store operations: implementations must be non-blocking and non-reentrant.
 */
fun interface MemoryPolicy {
    fun allows(target: MemoryTarget, permission: MemoryPermission): Boolean

    companion object {
        val DenyAll = MemoryPolicy { _, _ -> false }
    }
}

sealed interface MemoryResult<out T> {
    data class Success<T>(val value: T) : MemoryResult<T>
    data object Denied : MemoryResult<Nothing>
    data object NotFound : MemoryResult<Nothing>
    data object CapacityReached : MemoryResult<Nothing>
}

/** Explicit writes against an exact partition. Instances are intended for trusted host code. */
interface MemoryRecords {
    fun save(target: MemoryTarget, value: MemoryValue): MemoryResult<MemoryRecord>
    fun correct(target: MemoryTarget, id: MemoryId, value: MemoryValue): MemoryResult<MemoryRecord>
    fun delete(target: MemoryTarget, id: MemoryId): MemoryResult<Unit>
}

interface SessionContext {
    fun clearSession(session: MemoryTarget.Session): MemoryResult<Unit>

    /** Return a bounded snapshot; evaluate access separately for session and long-term records. */
    fun selectContext(session: MemoryTarget.Session, budget: MemoryContextBudget): MemoryResult<List<MemoryRecord>>
}

/**
 * Long-term persistence contract. Opt-in is per scope and revocation erases that scope's
 * long-term records. A durable implementation would require separately approved encryption,
 * key management, and atomic authorization and storage operations.
 */
interface LongTermMemory {
    fun setLongTermOptIn(scope: MemoryScope, enabled: Boolean): MemoryResult<Unit>
    fun export(scope: MemoryScope): MemoryResult<List<MemoryRecord>>
}

data class MemoryLimits(
    val sessionTtlNanos: Long = 30L * 60 * 1_000_000_000,
    val maxSessionRecords: Int = 256,
    val maxLongTermRecords: Int = 256,
    val maxOptedInScopes: Int = 64,
) {
    init {
        require(sessionTtlNanos > 0) { "Invalid memory TTL" }
        require(maxSessionRecords > 0 && maxLongTermRecords > 0 && maxOptedInScopes > 0) {
            "Invalid memory capacity"
        }
    }
}
