package de.meinhelfer.tag.ui

import android.app.Activity
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.meinhelfer.tag.R
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.delay

/** Hintergrund passend zum unteren Rand des Einspielers (dunkles Nachtblau). */
private val IntroBackground = Color(0xFF0A0F1C)

/**
 * Einspieler beim Start: kurzes Video mit Logo. Endet von selbst, beim Tippen,
 * mit „Überspringen“, mit der Zurück-Taste oder wenn die App verlassen wird.
 * Spielt das Video nicht (z. B. fehlender Decoder), geht es nach kurzer Zeit weiter.
 */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    var finished by remember { mutableStateOf(false) }
    fun finish() {
        if (!finished) {
            finished = true
            done()
        }
    }
    var sound by remember { mutableStateOf(AppPrefs.introSound(context)) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var started by remember { mutableStateOf(false) }

    BackHandler { finish() }
    HideSystemBars()

    // App verlassen: Einspieler nicht später fortsetzen, sondern beenden
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) finish() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Sicherheitsnetz: startet das Video nicht, geht es weiter; spätestens nach 20 Sekunden ist Schluss
    LaunchedEffect(Unit) {
        delay(4000)
        if (!started) finish()
        delay(16000)
        finish()
    }
    LaunchedEffect(sound, player) {
        val v = if (sound) 1f else 0f
        runCatching { player?.setVolume(v, v) }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(IntroBackground),
    ) {
        val tall = maxWidth / maxHeight < 9f / 16f
        val videoModifier = if (tall) {
            // Hochformat-Handy: volle Breite, oben anliegend; unten bleibt Platz für die Knöpfe
            Modifier.fillMaxWidth().aspectRatio(9f / 16f).align(Alignment.TopCenter)
        } else {
            Modifier.fillMaxHeight().aspectRatio(9f / 16f, matchHeightConstraintsFirst = true).align(Alignment.Center)
        }
        AndroidView(
            modifier = videoModifier,
            factory = { ctx ->
                VideoView(ctx).apply {
                    // Ohne Ton keine Musik anderer Apps unterbrechen, mit Ton nur kurz leiser stellen
                    setAudioFocusRequest(
                        if (AppPrefs.introSound(ctx)) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK else AudioManager.AUDIOFOCUS_NONE,
                    )
                    setOnPreparedListener { mp ->
                        player = mp
                        val v = if (AppPrefs.introSound(ctx)) 1f else 0f
                        runCatching { mp.setVolume(v, v) }
                        started = true
                        start()
                    }
                    setOnCompletionListener { finish() }
                    setOnErrorListener { _, _, _ ->
                        finish()
                        true
                    }
                    setVideoURI(Uri.parse("android.resource://${ctx.packageName}/${R.raw.intro}"))
                }
            },
            onRelease = { view ->
                player = null
                runCatching { view.stopPlayback() }
            },
        )
        // Tippen irgendwo beendet den Einspieler
        Box(
            Modifier
                .matchParentSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    shape = buttonShape(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    onClick = {
                        sound = !sound
                        AppPrefs.setIntroSound(context, sound)
                    },
                ) { Text(if (sound) "🔇 Ton aus" else "🔊 Ton an") }
                Button(
                    shape = buttonShape(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2B84B), contentColor = Color(0xFF1B1300)),
                    onClick = { finish() },
                ) { Text("Überspringen ›") }
            }
            Text(
                "Abschalten: Einstellungen › Darstellung › Einspieler",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Einspieler zum Ansehen aus den Einstellungen (Vollbild-Fenster). */
@Composable
fun IntroPreviewDialog(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize()) { IntroScreen(onDone = onClose) }
    }
}

/** Status- und Navigationsleiste während des Einspielers ausblenden (Wischen holt sie kurz zurück). */
@Composable
private fun HideSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view as? DialogWindowProvider)?.window
            ?: (view.parent as? DialogWindowProvider)?.window
            ?: (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
