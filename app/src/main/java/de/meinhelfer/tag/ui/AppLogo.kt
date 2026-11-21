package de.meinhelfer.tag.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.R

/** Name des Urhebers, wird in der App an mehreren Stellen gezeigt. */
const val AUTHOR = "Sunshine@R"

/** App-Logo: Sonne mit Herz auf warmem Verlauf (gleiche Grafik wie das Startbild-Symbol). */
@Composable
fun AppLogo(size: Dp = 72.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFF57C00)))),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Logo von Mein Helfer für den Tag",
            modifier = Modifier.fillMaxSize(),
        )
    }
}
