package de.meinhelfer.tag.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import de.meinhelfer.tag.R

/**
 * Einstellungen für Sunnys Einblendung beim Sprechen.
 * [show]: 0 = nie, 1 = wenn Sunny spricht, 2 = bei jedem Vorlesen.
 * [place]: Index in [PLACES]. [frame]: Index in [FRAMES]. [back]: Index in [BACKS].
 */
data class SunnyPopup(
    val show: Int = 1,
    val place: Int = 0,
    val size: Int = 132,
    val alpha: Float = 1f,
    val frame: Int = 1,
    val back: Int = 0,
) {
    companion object {
        val SHOWS = listOf("Nie", "Wenn Sunny spricht", "Bei jedem Vorlesen")
        val PLACES = listOf("Unten rechts", "Unten links", "Unten Mitte", "Oben rechts", "Oben links", "Bildmitte")
        val FRAMES = listOf("Ohne", "Leuchtrand", "Glas", "Liquid")
        val BACKS = listOf("Karte", "Glas", "Liquid", "Ohne")
        const val MIN_SIZE = 80
        const val MAX_SIZE = 240
    }

    /** Soll beim Sprecher [owner] eingeblendet werden? */
    fun showsFor(owner: String?): Boolean = when {
        owner == null -> false
        owner.startsWith("assistent") -> show >= 1
        else -> show >= 2
    }
}

/**
 * Wenn der Assistent spricht (oder auf Wunsch bei jedem Vorlesen) und das Sunny-Bild an ist,
 * blendet sich Sunny als kleines, stummes Video ein. Tippen stoppt das Vorlesen.
 * Eigenes Fenster, das bei jedem Sprechen neu oben aufgelegt wird – so liegt Sunny auch über Dialogen und Blättern.
 */
@Composable
fun SunnyTalkOverlay() {
    val speaking by Speech.speaking.collectAsState()
    val image by SunnyAvatar.useImage.collectAsState()
    val style by SunnyAvatar.popup.collectAsState()
    val visible = image && style.showsFor(speaking)
    val view = androidx.compose.ui.platform.LocalView.current
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shown = remember { androidx.compose.runtime.mutableStateOf(false) }
    val holder = remember { arrayOfNulls<android.view.View>(1) }
    val wm = remember { view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager }

    fun remove() {
        holder[0]?.let { runCatching { wm.removeViewImmediate(it) } }
        holder[0] = null
    }

    fun add() {
        remove()
        val token = view.applicationWindowToken ?: return
        val (gravity, offsetDp) = when (style.place) {
            1 -> (android.view.Gravity.BOTTOM or android.view.Gravity.START) to 96
            2 -> (android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL) to 96
            3 -> (android.view.Gravity.TOP or android.view.Gravity.END) to 84
            4 -> (android.view.Gravity.TOP or android.view.Gravity.START) to 84
            5 -> android.view.Gravity.CENTER to 0
            else -> (android.view.Gravity.BOTTOM or android.view.Gravity.END) to 96
        }
        val lp = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            this.token = token
            this.gravity = gravity
            y = with(density) { offsetDp.dp.roundToPx() }
            title = "Sunny"
        }
        val compose = androidx.compose.ui.platform.ComposeView(view.context).apply {
            setViewTreeLifecycleOwner(view.findViewTreeLifecycleOwner())
            setViewTreeViewModelStoreOwner(view.findViewTreeViewModelStoreOwner())
            setViewTreeSavedStateRegistryOwner(view.findViewTreeSavedStateRegistryOwner())
            setContent {
                MaterialTheme(colorScheme = colors, typography = typography) {
                    val now by SunnyAvatar.popup.collectAsState()
                    val talking by Speech.speaking.collectAsState()
                    AnimatedVisibility(
                        visible = shown.value,
                        enter = fadeIn(tween(600)) + scaleIn(initialScale = 0.85f),
                        exit = fadeOut(tween(500)) + scaleOut(targetScale = 0.9f),
                    ) {
                        val caption by SunnyAvatar.caption.collectAsState()
                        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
                            caption?.takeIf { talking?.startsWith("assistent-seite") == true }?.let { c ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    tonalElevation = 6.dp,
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.padding(horizontal = 12.dp).widthIn(max = 280.dp).clickable { Speech.stop() },
                                ) { Text(c, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp)) }
                            }
                            SunnyBubble(now, Modifier.padding(12.dp).clickable { Speech.stop() })
                        }
                    }
                }
            }
        }
        runCatching {
            wm.addView(compose, lp)
            holder[0] = compose
        }
    }

    androidx.compose.runtime.LaunchedEffect(visible, style.place, speaking) {
        if (visible) {
            // Bei jedem neuen Sprechen oben auflegen (über inzwischen geöffnete Dialoge)
            add()
            kotlinx.coroutines.delay(30)
            shown.value = true
        } else {
            shown.value = false
            kotlinx.coroutines.delay(600)
            if (!shown.value) remove()
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { remove() } }
}

/** Sunny im Rahmen mit Hintergrund nach [style]; auch als Vorschau in den Einstellungen. */
@Composable
fun SunnyBubble(style: SunnyPopup, modifier: Modifier = Modifier, video: Boolean = true) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface
    val turn = rememberInfiniteTransition(label = "liquid")
    val angle by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "winkel")
    val pulse by turn.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "puls")
    val liquid = Brush.sweepGradient(listOf(primary, tertiary, Color(0xFFFFC857), primary))
    val shape = RoundedCornerShape(28.dp)
    val back: Modifier = when (style.back) {
        0 -> Modifier.shadow(8.dp, shape).background(surface, shape)
        1 -> Modifier
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.08f))), shape)
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f))), shape)
        2 -> Modifier
            .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.35f), tertiary.copy(alpha = 0.15f), Color.Transparent)), shape)
            .drawBehind {
                rotate(angle) {
                    drawCircle(Brush.sweepGradient(listOf(primary.copy(alpha = 0.0f), primary.copy(alpha = 0.35f), tertiary.copy(alpha = 0.0f))), radius = size.minDimension * 0.62f)
                }
            }
        else -> Modifier
    }
    Box(modifier.alpha(style.alpha).then(back).padding(if (style.back == 3) 0.dp else 6.dp)) {
        val ring = Modifier
            .size(style.size.dp)
            .then(
                when (style.frame) {
                    1 -> Modifier.drawBehind {
                        drawCircle(primary.copy(alpha = 0.35f * pulse), radius = size.minDimension / 2 + 6.dp.toPx())
                    }.border(3.dp, primary, CircleShape)
                    2 -> Modifier.border(
                        3.dp,
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.6f))),
                        CircleShape,
                    )
                    3 -> Modifier.drawBehind {
                        rotate(angle) {
                            drawCircle(liquid, radius = size.minDimension / 2 + 1.dp.toPx(), style = Stroke(width = 5.dp.toPx()))
                        }
                        drawCircle(tertiary.copy(alpha = 0.18f * pulse), radius = size.minDimension / 2 + 8.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
                    }
                    else -> Modifier
                },
            )
            .clip(CircleShape)
        if (video) {
            TalkVideo(ring)
        } else {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.sunny_portrait),
                contentDescription = "Vorschau Sunny",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = ring,
            )
        }
        Surface(
            shape = CircleShape,
            color = surface.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) { Text("⏹", modifier = Modifier.padding(6.dp)) }
    }
}

/** Stummes Video in einer TextureView, damit runder Ausschnitt und Deckkraft wirken (VideoView ignoriert beides). */
@Composable
private fun TalkVideo(modifier: Modifier) {
    val context = LocalContext.current
    val res = remember { if (Math.random() < 0.5) R.raw.sunny_talk1 else R.raw.sunny_talk2 }
    val player = remember { arrayOfNulls<android.media.MediaPlayer>(1) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            runCatching { player[0]?.release() }
            player[0] = null
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.view.TextureView(ctx).apply {
                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                        runCatching {
                            val mp = android.media.MediaPlayer()
                            mp.setDataSource(context, Uri.parse("android.resource://${context.packageName}/$res"))
                            mp.setSurface(android.view.Surface(st))
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            mp.setOnPreparedListener { it.start() }
                            mp.prepareAsync()
                            player[0] = mp
                        }
                    }
                    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                        runCatching { player[0]?.release() }
                        player[0] = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                }
            }
        },
    )
}
