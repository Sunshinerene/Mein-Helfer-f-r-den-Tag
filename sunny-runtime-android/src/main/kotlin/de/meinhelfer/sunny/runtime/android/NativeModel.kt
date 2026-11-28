package de.meinhelfer.sunny.runtime.android

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import java.io.File
import java.util.concurrent.CancellationException

internal fun interface NativeModel {
    fun create(model: File, cache: File): NativeEngine
    val backend: DiagnosticBackend get() = DiagnosticBackend.Unknown
}

internal interface NativeEngine : AutoCloseable {
    fun initialize()
    fun conversation(): NativeConversation
}

internal interface NativeConversation : AutoCloseable {
    fun send(prompt: String, legacy: Boolean): String?
    fun sendWithDiagnostics(prompt: String, diagnostics: NativeDiagnostics): String? {
        // A facade exposing only the extracted string cannot establish the native response format.
        return diagnostics.measure(DiagnosticPhase.Generate) { send(prompt, legacy = false) }
            .also(diagnostics::capture)
    }
}

internal object LiteRtNativeModel : NativeModel {
    override val backend = DiagnosticBackend.Cpu

    override fun create(model: File, cache: File): NativeEngine {
        Engine.setNativeMinLogSeverity(LogSeverity.INFINITY)
        val engine = Engine(EngineConfig(
            modelPath = model.absolutePath,
            backend = Backend.CPU(),
            cacheDir = cache.absolutePath,
            maxNumTokens = 1024,
        ))
        return object : NativeEngine {
            override fun initialize() = engine.initialize()

            override fun conversation(): NativeConversation {
                val conversation = engine.createConversation()
                return object : NativeConversation {
                    override fun send(prompt: String, legacy: Boolean): String? =
                        send(prompt, legacy, diagnostics = null)

                    override fun sendWithDiagnostics(prompt: String, diagnostics: NativeDiagnostics): String? =
                        send(prompt, legacy = false, diagnostics = diagnostics)

                    private fun send(prompt: String, legacy: Boolean, diagnostics: NativeDiagnostics?): String? {
                        val message = measured(diagnostics, DiagnosticPhase.Generate) { conversation.sendMessage(prompt) }
                        if (legacy) return message.toString()
                        return classificationText(message, diagnostics)
                    }

                    override fun close() = conversation.close()
                }
            }

            // LiteRT throws when no initialized handle is available. In this case
            // native cleanup cannot be confirmed and the caller quarantines access.
            override fun close() = engine.close()
        }
    }
}

private fun classificationText(message: Message, diagnostics: NativeDiagnostics?): String? {
    val structured = message.toolCalls.isNotEmpty() || message.channels.isNotEmpty()
    val contents = message.contents.contents
    val singleText = (contents.singleOrNull() as? Content.Text)?.text
    if (diagnostics == null) return ClassificationResponse(contents.size, singleText, structured).text
    return diagnostics.measure(DiagnosticPhase.ResponseFormat, { it.format }) {
        val response = ClassificationResponse(contents.size, singleText, structured)
        // For a structured message only preview its first non-empty visible text part.
        diagnostics.capture(contents.asSequence().filterIsInstance<Content.Text>()
            .firstOrNull { it.text.isNotEmpty() }?.text)
        response
    }.text
}

/** Describes the already parsed native message without retaining tool calls or channel payloads. */
internal class ClassificationResponse(contentCount: Int, singleText: String?, structured: Boolean) {
    val text = if (structured) null else singleText
    val format = when {
        structured -> DiagnosticStatus.Structured
        contentCount == 0 -> DiagnosticStatus.Empty
        singleText == null -> DiagnosticStatus.Structured
        singleText.isEmpty() -> DiagnosticStatus.Empty
        else -> DiagnosticStatus.SingleText
    }
}

internal fun nativeReply(
    native: NativeModel,
    lease: ModelAccess.Lease,
    model: File,
    cache: File,
    prompt: String,
    legacy: Boolean,
    diagnostics: NativeDiagnostics? = null,
    checkpoint: () -> Unit,
): String? = lease.useNative {
    checkpoint()
    diagnostics?.backend(native.backend)
    val engine = try {
        measured(diagnostics, DiagnosticPhase.EngineCreate) {
            nativeCall(ModelAccessFailure.InitializationFailed) { native.create(model, cache) }
        }
    } catch (failure: Throwable) {
        // Construction may have allocated native resources without returning a handle.
        // No cleanup can be verified; prevent further model access in this process.
        lease.quarantine()
        diagnostics?.quarantined()
        throw failure
    }
    var conversation: NativeConversation? = null
    var primary: Throwable? = null
    try {
        measured(diagnostics, DiagnosticPhase.EngineInit) {
            nativeCall(ModelAccessFailure.InitializationFailed) { engine.initialize() }
        }
        checkpoint()
        conversation = measured(diagnostics, DiagnosticPhase.Session) {
            nativeCall(ModelAccessFailure.InitializationFailed) { engine.conversation() }
        }
        checkpoint()
        nativeCall(ModelAccessFailure.InferenceFailed) {
            if (diagnostics != null && !legacy) conversation.sendWithDiagnostics(prompt, diagnostics)
            else conversation.send(prompt, legacy)
        }.also { checkpoint() }
    } catch (failure: Throwable) {
        primary = failure
        throw failure
    } finally {
        try {
            // A failed child close can leave it using the engine. Preserve both
            // handles and quarantine instead of closing the engine underneath it.
            measured(diagnostics, DiagnosticPhase.Cleanup) {
                conversation?.close()
                engine.close()
            }
        } catch (cleanup: Throwable) {
            lease.quarantine()
            diagnostics?.quarantined()
            when {
                primary is CancellationException || primary is InterruptedException -> Unit
                cleanup is CancellationException || cleanup is InterruptedException -> throw cleanup
                cleanup is Error && cleanup !is LinkageError -> throw cleanup
                else -> throw ModelAccessException(ModelAccessFailure.CleanupFailed)
            }
        }
    }
}

private inline fun <T> measured(
    diagnostics: NativeDiagnostics?,
    phase: DiagnosticPhase,
    block: () -> T,
): T = if (diagnostics == null) block() else diagnostics.measure(phase, block = block)

internal inline fun <T> nativeCall(code: ModelAccessFailure, block: () -> T): T = try {
    block()
} catch (abort: CancellationException) {
    throw abort
} catch (abort: InterruptedException) {
    throw abort
} catch (_: Exception) {
    throw ModelAccessException(code)
} catch (_: LinkageError) {
    throw ModelAccessException(code)
}
