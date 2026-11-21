package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Einzeiliger Text, der sich so weit verkleinert, bis er hineinpasst (statt abgeschnitten zu werden).
 * Gedacht fuer enge Stellen wie die Beschriftung der unteren Leiste.
 */
@Composable
internal fun FitText(
    text: String,
    maxSize: TextUnit,
    modifier: Modifier = Modifier,
    minSize: TextUnit = 7.sp,
    fontWeight: FontWeight? = null,
    /** Gemeinsame Groesse fuer mehrere Texte (alle werden gleich gross, so gross wie der laengste passt). */
    shared: MutableFloatState? = null,
) {
    val own = remember(text, maxSize) { mutableFloatStateOf(maxSize.value) }
    val state = shared ?: own
    var size by state
    Text(
        text,
        modifier = modifier,
        fontSize = size.sp,
        letterSpacing = 0.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        fontWeight = fontWeight,
        onTextLayout = { result ->
            if (result.didOverflowWidth && size > minSize.value) size = (size * 0.92f).coerceAtLeast(minSize.value)
        },
    )
}

/** Zwischenueberschrift in langen Listen (z. B. Einstellungen). */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
    )
}

/** Textknopf-Farben fuer farbige Karten (z. B. rot), damit die Schrift lesbar bleibt. */
@Composable
internal fun textButtonOn(content: Color): ButtonColors = ButtonDefaults.textButtonColors(contentColor = content)
