package de.meinhelfer.tag.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import de.meinhelfer.tag.summary.Expression

object SunnyPresentation {
    private val changes = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun mode(context: android.content.Context): Int =
        context.getSharedPreferences("sunny_presentation", 0).getInt("mode", 0).coerceIn(0, 2)
    fun setMode(context: android.content.Context, mode: Int) {
        context.getSharedPreferences("sunny_presentation", 0).edit().putInt("mode", mode.coerceIn(0, 2)).apply()
        changes.value = changes.value + 1
    }
    @Composable
    fun videosAllowed(): Boolean {
        val context = LocalContext.current
        val revision by changes.collectAsState()
        val selected = remember(context, revision) { mode(context) }
        return selected != 2 && (selected == 1 || LocalEffects.current.animations)
    }
}

object SunnyAnimations {
    val names = setOf("idle", "happy", "soft_smile", "concerned", "gentle_nod",
        "listening", "thinking", "calming", "proud", "celebrate", "welcome",
        "goodbye", "reminder", "idea", "question", "warning", "success",
        "error", "sleep", "wakeup", "talk1", "talk2", "talk3", "talk4", "slow_blink")

    fun resource(context: android.content.Context, name: String): Int {
        if (name !in names) return 0
        val actual = if (name == "slow_blink") "idle" else name
        return context.resources.getIdentifier("sunny_" + actual, "raw", context.packageName)
    }
}

/** Plays a short silent reaction once, then restores the existing portrait. */
@Composable
fun SunnyReactionAnimation(name: String, expression: Expression, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by SunnyAvatar.useImage.collectAsState()
    val enabled = SunnyPresentation.videosAllowed()
    val res = remember(context, name) { SunnyAnimations.resource(context, name) }
    var finished by remember(name, res, image, enabled) { mutableStateOf(false) }
    if (!image || !enabled || res == 0 || finished) {
        AssistantFace(expression, size = size, modifier = modifier)
        return
    }
    val holder = remember { arrayOfNulls<VideoView>(1) }
    DisposableEffect(res) {
        onDispose { holder[0]?.stopPlayback(); holder[0] = null }
    }
    LaunchedEffect(res) {
        kotlinx.coroutines.delay(4000)
        finished = true
    }
    Box(modifier.size(size).clip(CircleShape)) {
        AndroidView(
            modifier = Modifier.size(size),
            factory = { ctx ->
                VideoView(ctx).apply {
                    holder[0] = this
                    setOnPreparedListener { player ->
                        player.setVolume(0f, 0f)
                        player.isLooping = false
                        start()
                    }
                    setOnCompletionListener { finished = true }
                    setOnErrorListener { _, _, _ -> finished = true; true }
                    setVideoURI(Uri.parse("android.resource://" + ctx.packageName + "/" + res))
                }
            }
        )
    }
}
