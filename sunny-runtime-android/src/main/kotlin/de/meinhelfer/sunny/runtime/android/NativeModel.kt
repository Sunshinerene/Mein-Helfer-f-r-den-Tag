package de.meinhelfer.sunny.runtime.android

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import java.io.File
import java.util.concurrent.CancellationException

internal fun interface NativeModel {
    fun create(model: File, cache: File): NativeEngine
}

internal interface NativeEngine : AutoCloseable {
    fun initialize()
    fun conversation(): NativeConversation
}

internal interface NativeConversation : AutoCloseable {
    fun send(prompt: String, legacy: Boolean): String?
}

internal object LiteRtNativeModel : NativeModel {
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
                    override fun send(prompt: String, legacy: Boolean): String? {
                        val message = conversation.sendMessage(prompt)
                        if (legacy) return message.toString()
                        if (message.toolCalls.isNotEmpty() || message.channels.isNotEmpty()) return null
                        return (message.contents.contents.singleOrNull() as? Content.Text)?.text
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

internal fun nativeReply(
    native: NativeModel,
    lease: ModelAccess.Lease,
    model: File,
    cache: File,
    prompt: String,
    legacy: Boolean,
    checkpoint: () -> Unit,
): String? = lease.useNative {
    checkpoint()
    val engine = try {
        nativeCall(ModelAccessFailure.InitializationFailed) { native.create(model, cache) }
    } catch (failure: Throwable) {
        // Construction may have allocated native resources without returning a handle.
        // No cleanup can be verified; prevent further model access in this process.
        lease.quarantine()
        throw failure
    }
    var conversation: NativeConversation? = null
    var primary: Throwable? = null
    try {
        nativeCall(ModelAccessFailure.InitializationFailed) { engine.initialize() }
        checkpoint()
        conversation = nativeCall(ModelAccessFailure.InitializationFailed) { engine.conversation() }
        checkpoint()
        nativeCall(ModelAccessFailure.InferenceFailed) { conversation.send(prompt, legacy) }
            .also { checkpoint() }
    } catch (failure: Throwable) {
        primary = failure
        throw failure
    } finally {
        try {
            // A failed child close can leave it using the engine. Preserve both
            // handles and quarantine instead of closing the engine underneath it.
            conversation?.close()
            engine.close()
        } catch (cleanup: Throwable) {
            lease.quarantine()
            when {
                primary is CancellationException || primary is InterruptedException -> Unit
                cleanup is CancellationException || cleanup is InterruptedException -> throw cleanup
                cleanup is Error && cleanup !is LinkageError -> throw cleanup
                else -> throw ModelAccessException(ModelAccessFailure.CleanupFailed)
            }
        }
    }
}

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
