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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
import de.meinhelfer.tag.ui.IntroScreen
import de.meinhelfer.tag.update.AppPrefs
import de.meinhelfer.tag.update.UpdateWorker

class MainActivity : FragmentActivity() {
    companion object {
        /** Person, die beim Oeffnen aus einer Benachrichtigung ausgewaehlt werden soll. */
        const val EXTRA_PERSON = "person"

        /** Seite, die aus einer Benachrichtigung geoeffnet werden soll, z. B. "trinken" oder "tagebuch". */
        const val EXTRA_SCREEN = "seite"

        /** Geöffnet über das Startbildschirm-Widget (dann ohne Einspieler). */
        const val EXTRA_FROM_WIDGET = "widget"
    }

    /** Aus einer Benachrichtigung gewuenschte Person (null = nichts zu tun). */
    private var pendingPerson by mutableStateOf<Long?>(null)

    /** Von einer anderen App geteilte Dateien (z. B. aus „Dateien“, Drive oder Dropbox) fuer den Import unter Dokumente. */
    private var pendingShare by mutableStateOf<List<android.net.Uri>?>(null)

    private fun sharedUris(intent: android.content.Intent?): List<android.net.Uri>? {
        if (intent == null) return null
        val streams = when (intent.action) {
            android.content.Intent.ACTION_SEND ->
                listOfNotNull(androidx.core.content.IntentCompat.getParcelableExtra(intent, android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java))
            android.content.Intent.ACTION_SEND_MULTIPLE ->
                androidx.core.content.IntentCompat.getParcelableArrayListExtra(intent, android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java).orEmpty()
            else -> return null
        }
        val clip = intent.clipData?.let { c -> (0 until c.itemCount).mapNotNull { c.getItemAt(it).uri } }.orEmpty()
        return (streams + clip).distinct().takeIf { it.isNotEmpty() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPerson = intent.getLongExtra(EXTRA_PERSON, -1L).takeIf { it > 0 }
        pendingScreen = intent.getStringExtra(EXTRA_SCREEN)
        sharedUris(intent)?.let { pendingShare = it }
    }

    /** Aus einer Benachrichtigung gewuenschte Seite (null = nichts zu tun). */
    private var pendingScreen by mutableStateOf<String?>(null)

    /** Einspieler (Video) beim Start zeigen; endet von selbst oder per Tippen. */
    private var showIntro by mutableStateOf(false)

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
        if (savedInstanceState == null) {
            intent?.getLongExtra(EXTRA_PERSON, -1L)?.takeIf { it > 0 }?.let { pendingPerson = it }
            pendingScreen = intent?.getStringExtra(EXTRA_SCREEN)
            pendingShare = sharedUris(intent)
            // Nur beim normalen Öffnen, nicht aus einer Erinnerung, vom Widget oder beim Teilen von Dateien
            showIntro = AppPrefs.introEnabled(this) && pendingPerson == null && pendingScreen == null && pendingShare == null &&
                intent?.getBooleanExtra(EXTRA_FROM_WIDGET, false) != true
        }

        // Beim Start gesperrt, wenn die App-Sperre an ist
        locked = AppPrefs.lockEnabled(this)
        applyScreenProtection()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                stoppedAt = SystemClock.elapsedRealtime()
                de.meinhelfer.tag.widget.HelperWidget.refresh(this@MainActivity)
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

        de.meinhelfer.tag.ui.SunnyAvatar.useImage.value = AppPrefs.assistantImage(this)
        setContent {
            val vm: MainViewModel = viewModel()
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            val style by vm.designStyle.collectAsStateWithLifecycle()
            val tweaks by vm.designTweaks.collectAsStateWithLifecycle()
            val fontFactor by vm.fontFactor.collectAsStateWithLifecycle()
            val uiScale by vm.uiScale.collectAsStateWithLifecycle()
            val corners by vm.cornerStyle.collectAsStateWithLifecycle()
            val effects by vm.effects.collectAsStateWithLifecycle()
            val baseDensity = LocalDensity.current
            val wanted = pendingPerson
            LaunchedEffect(wanted) {
                if (wanted != null) {
                    vm.selectPersonIfExists(wanted)
                    pendingPerson = null
                }
            }
            val wantedScreen = pendingScreen
            LaunchedEffect(wantedScreen) {
                if (wantedScreen != null) {
                    vm.requestScreen(wantedScreen)
                    pendingScreen = null
                }
            }
            // Geteilte Dateien erst nach dem Entsperren übernehmen (sonst stünde der Dialog über der Sperre)
            val share = pendingShare
            LaunchedEffect(share, locked, showIntro) {
                if (share != null && !locked && !showIntro) {
                    vm.receiveShared(share)
                    pendingShare = null
                }
            }
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
            // Schriftgröße der App und (Barrierefreiheit) Größe der ganzen Oberfläche
            val scaled = Density(baseDensity.density * uiScale, baseDensity.fontScale * fontFactor)
            CompositionLocalProvider(LocalDensity provides scaled) {
                HelferTheme(style = style, dark = dark, tweak = tweaks[style], corners = corners, effects = effects) {
                    Box {
                        if (showIntro) {
                            // Erst nach dem Einspieler die App (und ggf. die Sperre) zeigen
                            IntroScreen(onDone = { showIntro = false })
                        } else {
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
    }
}
