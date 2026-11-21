package de.meinhelfer.tag

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import android.view.WindowManager
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import de.meinhelfer.tag.security.AppLock
import de.meinhelfer.tag.ui.LockScreen
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.meinhelfer.tag.ui.HelferTheme
import de.meinhelfer.tag.ui.MainViewModel
import androidx.core.content.ContextCompat
import de.meinhelfer.tag.ui.HelferApp
import de.meinhelfer.tag.update.AppPrefs
import de.meinhelfer.tag.update.UpdateWorker

class MainActivity : FragmentActivity() {
    /** Gesperrt, solange die Abfrage (Fingerabdruck, Gesicht, PIN) nicht bestanden ist. */
    private var locked by mutableStateOf(false)
    private var stoppedAt = 0L

    private fun applyScreenProtection() {
        if (AppPrefs.screenProtected(this)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun askUnlock() {
        AppLock.authenticate(this, "App entsperren") { ok -> if (ok) locked = false }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Beim Start gesperrt, wenn die App-Sperre an ist
        locked = AppPrefs.lockEnabled(this)
        applyScreenProtection()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                stoppedAt = SystemClock.elapsedRealtime()
            }

            override fun onStart(owner: LifecycleOwner) {
                applyScreenProtection()
                if (AppPrefs.lockEnabled(this@MainActivity) && stoppedAt > 0 && !locked) {
                    val away = SystemClock.elapsedRealtime() - stoppedAt
                    if (away >= AppPrefs.lockGraceSeconds(this@MainActivity) * 1000L) locked = true
                }
            }
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Hintergrund-Pruefung auf neue Versionen (kann in den Einstellungen abgeschaltet werden)
        if (AppPrefs.autoUpdateCheck(this)) UpdateWorker.schedule(this) else UpdateWorker.cancel(this)

        setContent {
            val vm: MainViewModel = viewModel()
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val style by vm.designStyle.collectAsStateWithLifecycle()
            val tweaks by vm.designTweaks.collectAsStateWithLifecycle()
            val dark = when (mode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            // Statusleisten-Symbole passend zur Helligkeit der App
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose { }
            }
            HelferTheme(style = style, dark = dark, tweak = tweaks[style]) {
                Box {
                    HelferApp(vm)
                    if (locked) {
                        LockScreen(dark = dark, onUnlock = { askUnlock() })
                        LaunchedEffect(Unit) { askUnlock() }
                    }
                }
            }
        }
    }
}
