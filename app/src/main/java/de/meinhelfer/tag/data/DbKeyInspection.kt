package de.meinhelfer.tag.data

import de.meinhelfer.tag.data.DbStartupPolicy.KeyState

/** Classifies a read-only key lookup. Never generates or returns key material. */
internal object DbKeyInspection {
    fun inspect(readExisting: () -> String?): KeyState = try {
        val key = readExisting()
        when {
            key == null -> KeyState.Missing
            key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> KeyState.Present
            else -> KeyState.Unavailable
        }
    } catch (e: java.util.concurrent.CancellationException) {
        throw e
    } catch (e: InterruptedException) {
        throw e
    } catch (_: Exception) {
        KeyState.Unavailable
    } catch (_: LinkageError) {
        KeyState.Unavailable
    }
}
