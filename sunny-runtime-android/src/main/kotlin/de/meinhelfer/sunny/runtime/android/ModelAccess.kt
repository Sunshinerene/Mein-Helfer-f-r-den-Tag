package de.meinhelfer.sunny.runtime.android

import kotlinx.coroutines.sync.Mutex

enum class ModelAccessFailure {
    Busy, Quarantined, Missing, Incompatible, ReadFailed, InitializationFailed,
    InferenceFailed, CleanupFailed, InvalidSession,
}

class ModelAccessException internal constructor(val code: ModelAccessFailure) :
    IllegalStateException(code.name)

/** Process-wide lease shared by the host and the adapter. Quarantine lasts until restart. */
class ModelAccess internal constructor() {
    private val mutex = Mutex()

    @Volatile
    private var quarantined = false

    internal fun acquire(): Lease {
        if (quarantined) throw ModelAccessException(ModelAccessFailure.Quarantined)
        if (!mutex.tryLock()) throw ModelAccessException(ModelAccessFailure.Busy)
        return checkedLease()
    }

    /** Host operations await exclusive access cancellably, as the legacy model store did. */
    suspend fun <T> withExclusive(block: suspend (Lease) -> T): T {
        mutex.lock()
        val lease = checkedLease()
        try {
            return block(lease)
        } finally {
            lease.close()
        }
    }

    private fun checkedLease(): Lease {
        if (quarantined) {
            mutex.unlock()
            throw ModelAccessException(ModelAccessFailure.Quarantined)
        }
        return Lease(this)
    }

    class Lease internal constructor(private val access: ModelAccess) {
        private var closed = false
        private var nativeActive = false

        val isQuarantined: Boolean get() = access.quarantined

        internal fun quarantine() {
            access.quarantined = true
        }

        internal fun <T> useNative(block: () -> T): T {
            synchronized(this) {
                if (closed) throw ModelAccessException(ModelAccessFailure.InvalidSession)
                if (isQuarantined) throw ModelAccessException(ModelAccessFailure.Quarantined)
                if (nativeActive) throw ModelAccessException(ModelAccessFailure.Busy)
                nativeActive = true
            }
            try {
                return block()
            } finally {
                synchronized(this) {
                    nativeActive = false
                    if (closed) access.mutex.unlock()
                }
            }
        }

        @Synchronized
        internal fun close() {
            if (closed) return
            closed = true
            if (nativeActive) quarantine() else access.mutex.unlock()
        }
    }

    companion object {
        val process = ModelAccess()
    }
}
