package de.meinhelfer.tag.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.R

/** Name des Urhebers, wird in der App an mehreren Stellen gezeigt. */
const val AUTHOR = "Sunshine@R"

/** App-Logo als Zeichen ohne Schrift (Hand, Herz, Sonne) – gleiche Grafik wie das App-Symbol. */
@Composable
fun AppLogo(size: Dp = 72.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo_mark),
        contentDescription = "Logo von Mein Helfer für den Tag",
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.22f)),
    )
}

/** Vollständiges Logo mit Schriftzug „Mein Helfer für den Tag · by Sunshine@R“. */
@Composable
fun AppLogoFull(width: Dp = 220.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo_full),
        contentDescription = "Logo: Mein Helfer für den Tag, by $AUTHOR",
        contentScale = ContentScale.Fit,
        modifier = modifier.width(width),
    )
}
