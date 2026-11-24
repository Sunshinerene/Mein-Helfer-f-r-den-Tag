package de.meinhelfer.tag.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object SunnyActionGuard {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private fun activity(context: Context): FragmentActivity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is FragmentActivity) return current
            val next = current.baseContext
            if (next === current) break
            current = next
        }
        return current as? FragmentActivity
    }

    /** One authentication per action; no cached approval and no silent fallback. */
    fun authorize(context: Context, action: String, onDenied: (String) -> Unit, onApproved: () -> Unit) {
        val host = activity(context)
        if (host == null || host.isFinishing || host.isDestroyed) {
            onDenied("Freigabe momentan nicht möglich.")
            return
        }
        if (BiometricManager.from(host).canAuthenticate(AUTH) != BiometricManager.BIOMETRIC_SUCCESS) {
            onDenied("Bitte richte eine sichere Gerätesperre ein. Die Aktion wurde nicht ausgeführt.")
            return
        }
        try {
            var completed = false
            val prompt = BiometricPrompt(host, ContextCompat.getMainExecutor(host),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (!completed) {
                            completed = true
                            if (!host.isFinishing && !host.isDestroyed) onApproved()
                        }
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!completed) { completed = true; onDenied("Aktion nicht freigegeben.") }
                    }
                })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle("Sunny – persönliche Freigabe")
                .setSubtitle(action)
                .setAllowedAuthenticators(AUTH)
                .build())
        } catch (_: Exception) {
            onDenied("Freigabe nicht verfügbar. Die Aktion wurde nicht ausgeführt.")
        }
    }
}
