package de.meinhelfer.sunny.runtime.android

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Shared native lifecycle for the host's existing import probe and model test. */
object LegacyLiteRt {
    suspend fun test(lease: ModelAccess.Lease, model: File, cache: File, prompt: String): String =
        legacyReply(LiteRtNativeModel, lease, model, cache, prompt, probe = false)

    suspend fun probe(lease: ModelAccess.Lease, model: File, cache: File) {
        legacyReply(LiteRtNativeModel, lease, model, cache, "", probe = true)
    }
}

internal suspend fun legacyReply(
    native: NativeModel,
    lease: ModelAccess.Lease,
    model: File,
    cache: File,
    prompt: String,
    probe: Boolean,
): String = withContext(Dispatchers.IO) {
    val message = if (probe) "Antworte mit OK." else
        "Antworte kurz auf Deutsch. Du bist Sunny. Keine medizinischen Empfehlungen. Frage: " + prompt
    checkNotNull(nativeReply(native, lease, model, cache, message, legacy = true) {
        ensureActive()
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
    })
}
