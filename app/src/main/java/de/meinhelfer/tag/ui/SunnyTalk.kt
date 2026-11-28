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
import androidx.compose.ui.graphics.graphicsLayer
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
    val show: Int = 2,
    val place: Int = 0,
    val size: Int = 132,
    val alpha: Float = 1f,
    val frame: Int = 1,
    val back: Int = 0,
) {
    companion object {
        val SHOWS = listOf("Nie", "Nur wenn Sunny selbst spricht", "Bei allem, was vorgelesen wird")
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
    val appContext = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(appContext) { SunnyFrameDesign.load(appContext) }
    val view = androidx.compose.ui.platform.LocalView.current
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shown = remember { androidx.compose.runtime.mutableStateOf(false) }
    val holder = remember { arrayOfNulls<android.view.View>(1) }
    val windowFailed = remember { androidx.compose.runtime.mutableStateOf(false) }
    val wm = remember { view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager }

    fun remove() {
        holder[0]?.let { runCatching { wm.removeViewImmediate(it) } }
        holder[0] = null
    }

    fun add() {
        remove()
        val token = view.windowToken ?: return
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
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
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
                    // Immer gleich groß (Fenster hat von Anfang an seine Größe), nur Deckkraft und Größe werden animiert
                    val a by androidx.compose.animation.core.animateFloatAsState(if (shown.value) 1f else 0f, tween(if (shown.value) 600 else 450), label = "sichtbar")
                    run {
                        val caption by SunnyAvatar.caption.collectAsState()
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.graphicsLayer {
                                alpha = a
                                scaleX = 0.85f + 0.15f * a
                                scaleY = 0.85f + 0.15f * a
                            },
                        ) {
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
        }.onFailure { windowFailed.value = true }
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

    // Rückfall, falls kein eigenes Fenster möglich ist: Einblendung innerhalb der App (wie früher)
    if (windowFailed.value && visible) {
        androidx.compose.ui.window.Popup(
            alignment = when (style.place) {
                1 -> Alignment.BottomStart
                2 -> Alignment.BottomCenter
                3 -> Alignment.TopEnd
                4 -> Alignment.TopStart
                5 -> Alignment.Center
                else -> Alignment.BottomEnd
            },
            offset = androidx.compose.ui.unit.IntOffset(0, with(density) { (if (style.place in 3..4) 84 else if (style.place == 5) 0 else -96).dp.roundToPx() }),
            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
        ) {
            SunnyBubble(style, Modifier.padding(12.dp).clickable { Speech.stop() })
        }
    }
}

/** Sunny im Rahmen mit Hintergrund nach [style]; auch als Vorschau in den Einstellungen. */
@Composable
fun SunnyBubble(style: SunnyPopup, modifier: Modifier = Modifier, video: Boolean = true) {
    val context = LocalContext.current
    val design by SunnyFrameDesign.state.collectAsState()
    val speaking by Speech.speaking.collectAsState()
    val primary = if (design.color == 6 && speaking != null) Color(0xFF43D8DF)
        else SunnyFrameDesign.colors.getOrNull(design.color) ?: MaterialTheme.colorScheme.primary
    val avatarShape = when (design.shape) {
        1 -> RoundedCornerShape(22.dp)
        2 -> RoundedCornerShape(0.dp)
        else -> CircleShape
    }
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface
    val turn = rememberInfiniteTransition(label = "liquid")
    val angle by turn.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "winkel")
    val pulse by turn.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "puls")
    val liquid = Brush.sweepGradient(listOf(primary, tertiary, Color(0xFFFFC857), primary))
    val shape = when (design.backShape) {
        1 -> CircleShape
        2 -> RoundedCornerShape(22.dp)
        3 -> RoundedCornerShape(0.dp)
        else -> if (design.shape == 0) RoundedCornerShape(28.dp) else avatarShape
    }
    val backColor = SunnyFrameDesign.colors.getOrNull(design.backColor) ?: primary
    val backGlow = design.backGlow.coerceIn(0f, 1f)
    val back: Modifier = when (style.back) {
        0 -> Modifier.shadow(8.dp, shape).background(if (design.backColor < 0) surface else backColor.copy(alpha = 0.35f), shape)
        1 -> Modifier
            .background(Brush.verticalGradient(listOf((if (design.backColor < 0) Color.White else backColor).copy(alpha = 0.28f), Color.White.copy(alpha = 0.08f))), shape)
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f))), shape)
        2 -> Modifier
            .background(Brush.radialGradient(listOf(backColor.copy(alpha = 0.35f * backGlow), tertiary.copy(alpha = 0.15f * backGlow), Color.Transparent)), shape)
            .drawBehind {
                rotate(if (design.backPulse) angle else 0f) {
                    drawCircle(Brush.sweepGradient(listOf(primary.copy(alpha = 0.0f), backColor.copy(alpha = 0.35f * backGlow), tertiary.copy(alpha = 0.0f))), radius = size.minDimension * 0.62f)
                }
            }
        else -> Modifier
    }
    Box(modifier.alpha(style.alpha).then(back).padding(if (style.back == 3) 0.dp else 6.dp)) {
        val ring = Modifier
            .size(style.size.dp)
            .then(if (style.frame == 1 || style.frame == 3) Modifier.shadow((12f * design.glow * (if (design.pulse) pulse else 1f)).dp, avatarShape, clip = false, ambientColor = primary, spotColor = primary) else Modifier)
            .then(
                when (style.frame) {
                    1 -> Modifier.border(design.width.dp, primary, avatarShape)
                    2 -> Modifier.border(
                        design.width.dp,
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), primary.copy(alpha = 0.25f), Color.White.copy(alpha = 0.6f))),
                        avatarShape,
                    )
                    3 -> Modifier.border((design.width + 2).dp, liquid, avatarShape)
                    else -> Modifier
                },
            )
            .clip(avatarShape)
        if (video && SunnyPresentation.videosAllowed()) {
            TalkVideo(ring)
        } else {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(SunnyAvatar.drawable(de.meinhelfer.tag.summary.Expression.Friendly)),
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
    val res = remember(context) {
        listOf(R.raw.sunny_talk1, R.raw.sunny_talk2, SunnyAnimations.resource(context, "talk3"), SunnyAnimations.resource(context, "talk4"))
            .filter { it != 0 }.random()
    }
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

data class SunnyFrameOptions(val shape: Int = 0, val color: Int = -1, val width: Int = 3, val glow: Float = 1f, val pulse: Boolean = true, val backShape: Int = 0, val backColor: Int = -1, val backGlow: Float = 1f, val backPulse: Boolean = true)
object SunnyFrameDesign {
    val colors = listOf(Color(0xFF43D8DF), Color(0xFF8C6DE9), Color(0xFFEF77B8), Color(0xFFEAB957), Color(0xFF65CC89), Color.White)
    val state = kotlinx.coroutines.flow.MutableStateFlow(SunnyFrameOptions())
    fun load(context: android.content.Context) {
        val p = context.getSharedPreferences("sunny_frame_design", 0)
        state.value = SunnyFrameOptions(p.getInt("shape",0), p.getInt("color",-1), p.getInt("width",3), p.getFloat("glow",1f), p.getBoolean("pulse",true), p.getInt("backShape",0), p.getInt("backColor",-1), p.getFloat("backGlow",1f), p.getBoolean("backPulse",true))
    }
    fun save(context: android.content.Context, value: SunnyFrameOptions) {
        state.value = value
        context.getSharedPreferences("sunny_frame_design",0).edit().putInt("shape",value.shape).putInt("color",value.color).putInt("width",value.width).putFloat("glow",value.glow).putBoolean("pulse",value.pulse).putInt("backShape",value.backShape).putInt("backColor",value.backColor).putFloat("backGlow",value.backGlow).putBoolean("backPulse",value.backPulse).apply()
    }
}
