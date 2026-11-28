package de.meinhelfer.sunny.runtime.android

import de.meinhelfer.sunny.core.LocalRuntimeException
import de.meinhelfer.sunny.core.LocalRuntimeFailure
import de.meinhelfer.sunny.core.NanoClock
import java.util.concurrent.CancellationException

enum class DiagnosticPhase(val label: String) {
    Preparation("Vorbereitung"), Routing("Routing"), ModelAccess("Modellzugriff"),
    ModelCheck("Modellprüfung"), NativeAccess("Exklusiver Zugriff"),
    ModelRecheck("Modellprüfung unter Lease"), Runtime("Laufzeit und Zeitlimit"), EngineCreate("Engine-Erstellung"),
    EngineInit("Engine-Initialisierung"), Session("Native Sitzung"), Generate("Generierung"),
    ResponseFormat("Antwortformat"), Validation("Core-Validierung"), Cleanup("Native Bereinigung"),
}

enum class DiagnosticStatus {
    Unknown, Completed, Failed, Available, Missing, Incompatible, Busy, Quarantined,
    ReadFailed, Cancelled, Interrupted, DeadlineExceeded, SingleText, Empty, Structured,
    AcceptedAck, AcceptedClarify, Rejected, OnDevice, Rules, Online, Unavailable,
}

enum class DiagnosticBackend { Unknown, Cpu }

data class DiagnosticStep(val status: DiagnosticStatus, val elapsedNanos: Long)

/** One in-memory trace for one synthetic run. Reports are read after its structured completion. */
class NativeDiagnostics(private val clock: NanoClock = NanoClock(System::nanoTime)) {
    private val steps = mutableMapOf<DiagnosticPhase, DiagnosticStep>()
    private var backend = DiagnosticBackend.Unknown
    private var quarantined = false
    private var preview: String? = null

    fun nowNanos(): Long = clock.nowNanos()

    @Synchronized
    fun record(phase: DiagnosticPhase, status: DiagnosticStatus, startedAt: Long) {
        steps[phase] = DiagnosticStep(status, nowNanos() - startedAt)
    }

    inline fun <T> measure(
        phase: DiagnosticPhase,
        status: (T) -> DiagnosticStatus = { DiagnosticStatus.Completed },
        block: () -> T,
    ): T {
        val start = nowNanos()
        try {
            return block().also { record(phase, status(it), start) }
        } catch (failure: Throwable) {
            record(phase, failure.diagnosticStatus(), start)
            throw failure
        }
    }

    @Synchronized
    internal fun backend(value: DiagnosticBackend) {
        backend = value
    }

    @Synchronized
    internal fun quarantined() {
        quarantined = true
    }

    @Synchronized
    internal fun capture(text: String?) {
        preview = text?.takeIf { it.isNotEmpty() }?.let { output ->
            buildString {
                output.take(256).forEach { char ->
                    when {
                        char == '\n' -> append("\\n")
                        char == '\r' -> append("\\r")
                        char == '\t' -> append("\\t")
                        char.isISOControl() || char.category == CharCategory.FORMAT ||
                            char.category == CharCategory.SURROGATE ||
                            char.category == CharCategory.LINE_SEPARATOR ||
                            char.category == CharCategory.PARAGRAPH_SEPARATOR -> append('\uFFFD')
                        else -> append(char)
                    }
                }
                if (output.length > 256) append('…')
            }
        }
    }

    @Synchronized
    fun snapshot() = DiagnosticSnapshot(steps.toMap(), backend, quarantined, preview)
}

class DiagnosticSnapshot internal constructor(
    val steps: Map<DiagnosticPhase, DiagnosticStep>,
    val backend: DiagnosticBackend,
    val quarantined: Boolean,
    val untrustedPreview: String?,
)

@PublishedApi
internal fun Throwable.diagnosticStatus() = when (this) {
    is CancellationException -> DiagnosticStatus.Cancelled
    is InterruptedException -> DiagnosticStatus.Interrupted
    is LocalRuntimeException -> when (failure) {
        LocalRuntimeFailure.DeadlineExceeded -> DiagnosticStatus.DeadlineExceeded
        LocalRuntimeFailure.Busy -> DiagnosticStatus.Busy
        else -> DiagnosticStatus.Failed
    }
    is ModelAccessException -> when (code) {
        ModelAccessFailure.Busy -> DiagnosticStatus.Busy
        ModelAccessFailure.Quarantined -> DiagnosticStatus.Quarantined
        ModelAccessFailure.ReadFailed -> DiagnosticStatus.ReadFailed
        else -> DiagnosticStatus.Failed
    }
    else -> DiagnosticStatus.Failed
}
