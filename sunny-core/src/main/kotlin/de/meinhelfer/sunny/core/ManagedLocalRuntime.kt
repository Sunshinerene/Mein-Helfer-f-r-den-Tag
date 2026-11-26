package de.meinhelfer.sunny.core

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

enum class LocalModelStatus { Available, Missing, Incompatible }

enum class LocalRuntimeFailure {
    Busy, ModelMissing, ModelIncompatible, StatusCheckFailed, InitializationFailed,
    InferenceFailed, DeadlineExceeded, CleanupFailed, ClockFailed,
}

/** Contains only a fixed failure code. Backend exception messages and causes are discarded. */
class LocalRuntimeException internal constructor(val failure: LocalRuntimeFailure) :
    IllegalStateException(failure.name)

/**
 * Local-only backend. It must check compatibility before opening and revalidate it during
 * open if the model can change. Failed opens clean up their own partial allocations.
 * Each suspending method must propagate cancellation and finish all work before returning
 * or throwing. Backends must check the deadline at safe points.
 */
interface LocalModelBackend {
    suspend fun modelStatus(deadline: RuntimeDeadline): LocalModelStatus
    suspend fun open(deadline: RuntimeDeadline): LocalModelSession
}

/** An open session is owned by the runtime. Close must complete cleanup synchronously. */
interface LocalModelSession : AutoCloseable {
    /** Stop all use of the session before returning or throwing, including on cancellation. */
    suspend fun generate(text: String, deadline: RuntimeDeadline): String?

    override fun close()
}

/** Monotonic time source with the same elapsed-time semantics as System.nanoTime(). */
fun interface NanoClock {
    fun nowNanos(): Long
}

class RuntimeDeadline internal constructor(
    private val clock: NanoClock,
    private val timeoutNanos: Long,
) {
    private val startedAt = clock.nowNanos()

    /** Safe-point check. External coroutine cancellation is propagated by backend suspension. */
    fun checkpoint() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (clock.nowNanos() - startedAt >= timeoutNanos) {
            throw LocalRuntimeException(LocalRuntimeFailure.DeadlineExceeded)
        }
    }
}

/** Single-flight model access. Use one manager per backend instance. */
class ManagedLocalRuntime(
    private val backend: LocalModelBackend,
    private val timeoutNanos: Long,
    private val clock: NanoClock = NanoClock(System::nanoTime),
) : LocalRuntime {
    private val active = AtomicBoolean()

    init {
        require(timeoutNanos > 0) { "timeoutNanos must be positive" }
    }

    /** The returned status is a snapshot. Every generation checks it again. */
    suspend fun modelStatus(): LocalModelStatus = exclusively { deadline ->
        checkModel(deadline)
    }

    override suspend fun generate(text: String): String? = exclusively { deadline ->
        when (checkModel(deadline)) {
            LocalModelStatus.Available -> Unit
            LocalModelStatus.Missing -> throw LocalRuntimeException(LocalRuntimeFailure.ModelMissing)
            LocalModelStatus.Incompatible -> throw LocalRuntimeException(LocalRuntimeFailure.ModelIncompatible)
        }
        val session = backendCall(LocalRuntimeFailure.InitializationFailed) { backend.open(deadline) }
        useSession(session) {
            backendCall(LocalRuntimeFailure.InferenceFailed) {
                deadline.checkpoint()
                session.generate(text, deadline).also { deadline.checkpoint() }
            }
        }
    }

    private suspend fun checkModel(deadline: RuntimeDeadline): LocalModelStatus =
        backendCall(LocalRuntimeFailure.StatusCheckFailed) {
            deadline.checkpoint()
            backend.modelStatus(deadline).also { deadline.checkpoint() }
        }

    private inline fun <T> exclusively(block: (RuntimeDeadline) -> T): T {
        if (!active.compareAndSet(false, true)) throw LocalRuntimeException(LocalRuntimeFailure.Busy)
        try {
            val deadline = backendCall(LocalRuntimeFailure.ClockFailed) { RuntimeDeadline(clock, timeoutNanos) }
            return block(deadline)
        } finally {
            active.set(false)
        }
    }

    private inline fun <T> backendCall(failure: LocalRuntimeFailure, block: () -> T): T =
        try {
            block()
        } catch (known: LocalRuntimeException) {
            throw known
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            throw interrupted
        } catch (_: Exception) {
            throw LocalRuntimeException(failure)
        }

    private inline fun <T> useSession(session: LocalModelSession, block: () -> T): T {
        var primary: Throwable? = null
        try {
            return block()
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try {
                backendCall(LocalRuntimeFailure.CleanupFailed) { session.close() }
            } catch (cleanup: Exception) {
                // Preserve the original abort, even if closing also fails. If cleanup alone
                // reports an external abort, it supersedes an ordinary inference failure.
                if (primary == null ||
                    (primary is Exception && !primary.isAbort() && cleanup.isAbort())
                ) {
                    throw cleanup
                }
            }
        }
    }

    private fun Exception.isAbort(): Boolean =
        this is CancellationException || this is InterruptedException
}
