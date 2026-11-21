package de.meinhelfer.tag.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** App-Sperre: Fingerabdruck, Gesicht oder die PIN/das Muster des Handys. Die App speichert selbst keine PIN. */
object AppLock {
    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** true, wenn auf dem Handy eine Bildschirmsperre eingerichtet ist. */
    fun available(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
        }

    /** Zeigt die Abfrage des Handys. [onResult] bekommt true bei Erfolg. */
    @Suppress("DEPRECATION")
    fun authenticate(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Mein Helfer für den Tag")
            .apply {
                if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(AUTHENTICATORS) else setDeviceCredentialAllowed(true)
            }
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: Exception) {
            onResult(false)
        }
    }
}
