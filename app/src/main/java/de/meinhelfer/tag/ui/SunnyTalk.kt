package de.meinhelfer.tag.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import de.meinhelfer.tag.R

/**
 * Wenn der Assistent spricht und das Sunny-Bild an ist, blendet sich Sunny dezent als kleines,
 * stummes Video unten rechts ein. Tippen stoppt das Vorlesen.
 */
@Composable
fun SunnyTalkOverlay() {
    val speaking by Speech.speaking.collectAsState()
    val image by SunnyAvatar.useImage.collectAsState()
    val visible = image && speaking?.startsWith("assistent") == true
    val density = androidx.compose.ui.platform.LocalDensity.current
    val lift = with(density) { 96.dp.roundToPx() }
    Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(0, -lift),
        properties = PopupProperties(focusable = false),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(androidx.compose.animation.core.tween(600)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(androidx.compose.animation.core.tween(500)) + scaleOut(targetScale = 0.9f),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(12.dp).clickable { Speech.stop() },
            ) {
                Box(Modifier.padding(6.dp)) {
                    TalkVideo(
                        Modifier
                            .size(132.dp)
                            .clip(CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) { Text("⏹", modifier = Modifier.padding(6.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TalkVideo(modifier: Modifier) {
    val context = LocalContext.current
    val res = remember { if (Math.random() < 0.5) R.raw.sunny_talk1 else R.raw.sunny_talk2 }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(Uri.parse("android.resource://${context.packageName}/$res"))
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    start()
                }
                setOnErrorListener { _, _, _ -> true }
            }
        },
        onRelease = { it.stopPlayback() },
    )
}
