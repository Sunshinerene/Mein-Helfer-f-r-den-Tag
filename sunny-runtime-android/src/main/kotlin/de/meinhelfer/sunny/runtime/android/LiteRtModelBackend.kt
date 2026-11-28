package de.meinhelfer.sunny.runtime.android

import de.meinhelfer.sunny.core.LocalRuntimeException
import de.meinhelfer.sunny.core.LocalModelBackend
import de.meinhelfer.sunny.core.LocalModelSession
import de.meinhelfer.sunny.core.LocalModelStatus
import de.meinhelfer.sunny.core.RuntimeDeadline
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class LiteRtModelBackend internal constructor(
    private val model: File,
    private val cache: File,
    private val native: NativeModel,
    private val access: ModelAccess,
    private val dispatcher: CoroutineDispatcher,
    private val diagnostics: NativeDiagnostics? = null,
) : LocalModelBackend {
    constructor(model: File, cache: File) :
        this(model, cache, LiteRtNativeModel, ModelAccess.process, Dispatchers.IO)

    constructor(model: File, cache: File, diagnostics: NativeDiagnostics) :
        this(model, cache, LiteRtNativeModel, ModelAccess.process, Dispatchers.IO, diagnostics)

    override suspend fun modelStatus(deadline: RuntimeDeadline): LocalModelStatus {
        val lease = acquireForRuntime(DiagnosticPhase.ModelAccess)
        try {
            return inspect(deadline, DiagnosticPhase.ModelCheck)
        } finally {
            lease.close()
        }
    }

    override suspend fun open(deadline: RuntimeDeadline): LocalModelSession {
        val lease = acquireForRuntime(DiagnosticPhase.NativeAccess)
        try {
            when (inspect(deadline, DiagnosticPhase.ModelRecheck)) {
                LocalModelStatus.Missing -> throw ModelAccessException(ModelAccessFailure.Missing)
                LocalModelStatus.Incompatible -> throw ModelAccessException(ModelAccessFailure.Incompatible)
                LocalModelStatus.Available -> return Session(lease)
            }
        } catch (failure: Throwable) {
            lease.close()
            throw failure
        }
    }

    private fun acquireForRuntime(phase: DiagnosticPhase): ModelAccess.Lease = try {
        diagnostics?.measure(phase) { access.acquire() } ?: access.acquire()
    } catch (failure: ModelAccessException) {
        if (failure.code == ModelAccessFailure.Quarantined) diagnostics?.quarantined()
        if (failure.code == ModelAccessFailure.Busy) throw LocalRuntimeException.busy()
        throw failure
    }

    private suspend fun inspect(deadline: RuntimeDeadline, phase: DiagnosticPhase) = withContext(dispatcher) {
        val check = {
            ensureActive()
            deadline.checkpoint()
            nativeCall(ModelAccessFailure.ReadFailed) { modelStatus(model) }.also { deadline.checkpoint() }
        }
        diagnostics?.measure(phase, { status ->
            when (status) {
                LocalModelStatus.Available -> DiagnosticStatus.Available
                LocalModelStatus.Missing -> DiagnosticStatus.Missing
                LocalModelStatus.Incompatible -> DiagnosticStatus.Incompatible
            }
        }, check) ?: check()
    }

    private inner class Session(private val lease: ModelAccess.Lease) : LocalModelSession {
        private var used = false
        private var running = false
        private var closed = false

        override suspend fun generate(text: String, deadline: RuntimeDeadline): String? {
            synchronized(this) {
                if (used || closed) throw ModelAccessException(ModelAccessFailure.InvalidSession)
                used = true
                running = true
            }
            try {
                return withContext(dispatcher) {
                    val prompt = "Classify the request. Reply with exactly ACK or CLARIFY. Request:\n$text"
                    nativeReply(native, lease, model, cache, prompt, legacy = false, diagnostics = diagnostics) {
                        ensureActive()
                        deadline.checkpoint()
                    }
                }
            } finally {
                synchronized(this) { running = false }
            }
        }

        override fun close() = synchronized(this) {
            if (running) throw ModelAccessException(ModelAccessFailure.Busy)
            closed = true
            lease.close()
        }
    }
}

// Fixed header fields accepted by the LiteRT-LM 0.16.1 reader. Native initialization
// still validates model metadata and device/runtime compatibility.
internal fun modelStatus(file: File): LocalModelStatus {
    if (!file.exists()) return LocalModelStatus.Missing
    if (!file.isFile || !file.canRead()) return LocalModelStatus.Incompatible
    return try {
        RandomAccessFile(file, "r").use { input ->
            val magic = ByteArray(8).also(input::readFully)
            val major = Integer.reverseBytes(input.readInt())
            input.seek(24)
            val headerEnd = java.lang.Long.reverseBytes(input.readLong())
            if (magic.contentEquals("LITERTLM".toByteArray(Charsets.US_ASCII)) &&
                major == 1 && headerEnd in 33..16_384 && headerEnd < input.length()
            ) LocalModelStatus.Available else LocalModelStatus.Incompatible
        }
    } catch (_: EOFException) {
        LocalModelStatus.Incompatible
    }
}
