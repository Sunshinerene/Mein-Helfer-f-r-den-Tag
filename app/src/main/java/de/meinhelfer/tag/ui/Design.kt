package de.meinhelfer.tag.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Die waehlbaren Designs. Alle gibt es in Hell und Dunkel. */
enum class DesignStyle(val key: String, val label: String, val description: String) {
    Schlicht("schlicht", "Schlicht", "Klar und ruhig, so wie bisher"),
    Minimal("minimal", "Minimalistisch", "Reduziert und flach, mit feinen Linien"),
    Glas("glas", "Glas", "Durchscheinende Karten auf sanftem Farbverlauf"),
    Liquid("liquid", "Liquid", "Weiche Formen und fließende Farben"),
    Sonnig("sonnig", "Sonnig", "Warm und freundlich in Gelb und Orange"),
    Natur("natur", "Natur", "Beruhigendes, sanftes Grün"),
    Aurora("aurora", "Aurora (Beta)", "Leuchtende Farbverläufe mit Glas und Leuchtrand"),
    ;

    companion object {
        fun fromKey(key: String?): DesignStyle = entries.firstOrNull { it.key == key } ?: Schlicht
    }
}

/** Ein weicher Farbfleck im Hintergrund (Mitte und Radius als Anteil der Bildschirmbreite/-hoehe). */
class Blob(val color: Color, val x: Float, val y: Float, val radius: Float)

/** Hintergrund hinter der ganzen App (nur bei Glas und Liquid). */
class Backdrop(val base: Color, val gradient: List<Color>?, val blobs: List<Blob>)

/** Alles, was ein Design ausmacht. */
data class DesignSpec(
    val scheme: ColorScheme,
    val shapes: Shapes,
    /** null = Standardfarbe der Karten aus dem Farbschema */
    val cardColor: Color?,
    val cardBorder: BorderStroke?,
    val cardElevation: Dp,
    val backdrop: Backdrop?,
    /** Drei Farbtupfer fuer die Vorschau in den Einstellungen */
    val preview: List<Color>,
    /** Leuchtrand der Karten (0 = aus, 1 = kraeftig) */
    val glow: Float = 0f,
    /** Form der Knoepfe; null = ganz rund (Pille), wie bei Material ueblich */
    val buttonShape: Shape? = null,
)

/** Ecken der ganzen App: einheitlich fuer Karten, Knoepfe, Felder, Chips und Fenster. */
enum class CornerStyle(val key: String, val label: String, val radius: Int?) {
    Design("design", "Wie im Design", null),
    Eckig("eckig", "Eckig", 0),
    DezentEckig("dezent_eckig", "Dezent eckig", 4),
    DezentRund("dezent_rund", "Dezent rund", 10),
    Rund("rund", "Rund", 18),
    ;

    companion object {
        fun fromKey(key: String?): CornerStyle = entries.firstOrNull { it.key == key } ?: Design
    }
}

/** Einstellbare Effekte (Beta). -1 = wie im Design. */
data class Effects(
    /** Deckkraft der Karten 0..1 */
    val cardAlpha: Float = -1f,
    /** Leuchtrand 0..1 */
    val glow: Float = -1f,
    /** Staerke der Farbflecken im Hintergrund 0..2 */
    val backdrop: Float = -1f,
    /** Hintergrund bewegt sich sanft */
    val motion: Boolean = false,
    /** Animationen beim Antippen, Auf- und Zuklappen und beim Wechsel der Reiter */
    val animations: Boolean = false,
    /** Glanz auf Skalen und Balken */
    val gloss: Boolean = false,
)

val LocalEffects = staticCompositionLocalOf { Effects() }

/** Knopf-Form passend zur gewaehlten Ecken-Einstellung. */
@Composable
fun buttonShape(): Shape = LocalDesign.current.buttonShape ?: CircleShape

/** Wendet Ecken und Effekte auf ein Design an. */
fun DesignSpec.adjusted(corners: CornerStyle, effects: Effects): DesignSpec {
    var spec = this
    corners.radius?.let { r ->
        val shape = RoundedCornerShape(r.dp)
        spec = spec.copy(shapes = Shapes(shape, shape, shape, shape, shape), buttonShape = shape)
    }
    if (effects.cardAlpha >= 0f) {
        val base = spec.cardColor ?: spec.scheme.surfaceContainerHighest
        spec = spec.copy(cardColor = base.copy(alpha = effects.cardAlpha.coerceIn(0.03f, 1f)))
    }
    if (effects.glow >= 0f) spec = spec.copy(glow = effects.glow.coerceIn(0f, 1f))
    val bd = spec.backdrop
    if (effects.backdrop >= 0f && bd != null) {
        val k = effects.backdrop.coerceIn(0f, 2f)
        spec = spec.copy(
            backdrop = Backdrop(bd.base, bd.gradient, bd.blobs.map { Blob(it.color.copy(alpha = (it.color.alpha * k).coerceIn(0f, 1f)), it.x, it.y, it.radius) }),
        )
    }
    return spec
}

val LocalDesign = staticCompositionLocalOf { designSpec(DesignStyle.Schlicht, false) }

/** Wendet Farben, Formen und Hintergrund des gewaehlten Designs auf die ganze App an. */
@Composable
fun HelferTheme(
    style: DesignStyle,
    dark: Boolean,
    tweak: DesignTweak? = null,
    corners: CornerStyle = CornerStyle.Design,
    effects: Effects = Effects(),
    content: @Composable () -> Unit,
) {
    val spec = remember(style, dark, tweak, corners, effects) { tweakedSpec(style, dark, tweak).adjusted(corners, effects) }
    // Sanft bewegter Hintergrund (nur wenn eingeschaltet und das Design Farbflecken hat)
    val phase = if (effects.motion && spec.backdrop != null) {
        val transition = rememberInfiniteTransition(label = "hintergrund")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(durationMillis = 45_000, easing = LinearEasing)),
            label = "phase",
        )
    } else null
    CompositionLocalProvider(LocalDesign provides spec, LocalEffects provides effects) {
        MaterialTheme(colorScheme = spec.scheme, shapes = spec.shapes) {
            Box(Modifier.fillMaxSize().drawBackdrop(spec.backdrop, phase)) { content() }
        }
    }
}

private fun Modifier.drawBackdrop(backdrop: Backdrop?, phase: State<Float>?): Modifier {
    if (backdrop == null) return this
    return drawBehind {
        val gradient = backdrop.gradient
        if (gradient != null) {
            drawRect(Brush.linearGradient(gradient, start = Offset(0f, 0f), end = Offset(size.width, size.height)))
        } else {
            drawRect(backdrop.base)
        }
        val p = phase?.value ?: 0f
        backdrop.blobs.forEachIndexed { i, blob ->
            val radius = size.width * blob.radius
            val dx = if (phase != null) sin(p + i * 1.7f) * 0.10f * size.width else 0f
            val dy = if (phase != null) cos(p * 0.8f + i * 1.1f) * 0.07f * size.height else 0f
            val center = Offset(size.width * blob.x + dx, size.height * blob.y + dy)
            drawCircle(
                brush = Brush.radialGradient(listOf(blob.color, Color.Transparent), center = center, radius = radius),
                radius = radius,
                center = center,
            )
        }
    }
}

/** Karte, die sich nach dem gewaehlten Design richtet (Farbe, Rand, Form). */
@Composable
fun HCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    colors: CardColors? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val design = LocalDesign.current
    val effects = LocalEffects.current
    val cardColors = colors ?: design.cardColor?.let { CardDefaults.cardColors(containerColor = it) }
        ?: CardDefaults.cardColors()
    val elevation = CardDefaults.cardElevation(defaultElevation = design.cardElevation)
    val shape = MaterialTheme.shapes.medium
    // Leuchtrand: Farbverlauf aus Haupt- und Akzentfarbe
    val border = if (design.glow > 0f) {
        val a = 0.25f + 0.75f * design.glow
        val sc = MaterialTheme.colorScheme
        BorderStroke(
            (1f + design.glow).dp,
            Brush.linearGradient(listOf(sc.primary.copy(alpha = a), sc.tertiary.copy(alpha = a), sc.primary.copy(alpha = a * 0.6f))),
        )
    } else design.cardBorder
    if (onClick != null) {
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val scale by animateFloatAsState(if (effects.animations && pressed) 0.97f else 1f, label = "druck")
        Card(
            onClick = onClick,
            modifier = modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            shape = shape,
            colors = cardColors,
            elevation = elevation,
            border = border,
            interactionSource = source,
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = cardColors,
            elevation = elevation,
            border = border,
            content = content,
        )
    }
}

// ---------------------------------------------------------------- Paletten

private class Palette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val variant: Color,
    val onVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    /** surfaceContainerLowest .. Highest */
    val c0: Color,
    val c1: Color,
    val c2: Color,
    val c3: Color,
    val c4: Color,
)

private fun scheme(p: Palette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.primaryContainer,
        onPrimaryContainer = p.onPrimaryContainer,
        secondary = p.primary,
        onSecondary = p.onPrimary,
        secondaryContainer = p.secondaryContainer,
        onSecondaryContainer = p.onSecondaryContainer,
        background = p.background,
        onBackground = p.onBackground,
        surface = p.background,
        onSurface = p.onBackground,
        surfaceVariant = p.variant,
        onSurfaceVariant = p.onVariant,
        outline = p.outline,
        outlineVariant = p.outlineVariant,
        surfaceContainerLowest = p.c0,
        surfaceContainerLow = p.c1,
        surfaceContainer = p.c2,
        surfaceContainerHigh = p.c3,
        surfaceContainerHighest = p.c4,
    )
}

private fun shapes(xs: Int, s: Int, m: Int, l: Int, xl: Int) = Shapes(
    extraSmall = RoundedCornerShape(xs.dp),
    small = RoundedCornerShape(s.dp),
    medium = RoundedCornerShape(m.dp),
    large = RoundedCornerShape(l.dp),
    extraLarge = RoundedCornerShape(xl.dp),
)

private fun c(hex: Long) = Color(hex)

fun designSpec(style: DesignStyle, dark: Boolean): DesignSpec = when (style) {
    DesignStyle.Schlicht -> DesignSpec(
        scheme = if (dark) darkColorScheme() else lightColorScheme(),
        shapes = Shapes(),
        cardColor = null,
        cardBorder = null,
        cardElevation = 0.dp,
        backdrop = null,
        preview = if (dark) listOf(c(0xFFD0BCFF), c(0xFF4F378B), c(0xFF1C1B1F)) else listOf(c(0xFF6650A4), c(0xFFEADDFF), c(0xFFFFFBFE)),
    )

    DesignStyle.Minimal -> {
        val p = if (dark) {
            Palette(
                c(0xFFF0F0F0), c(0xFF111111), c(0xFF2A2A2A), c(0xFFF0F0F0), c(0xFF262626), c(0xFFF0F0F0),
                c(0xFF0F0F0F), c(0xFFEDEDED), c(0xFF1E1E1E), c(0xFFB5B5B5), c(0xFF777777), c(0xFF333333),
                c(0xFF0A0A0A), c(0xFF151515), c(0xFF1A1A1A), c(0xFF202020), c(0xFF262626),
            )
        } else {
            Palette(
                c(0xFF111111), c(0xFFFFFFFF), c(0xFFE9E9E9), c(0xFF111111), c(0xFFEDEDED), c(0xFF111111),
                c(0xFFFFFFFF), c(0xFF111111), c(0xFFF2F2F2), c(0xFF444444), c(0xFF8C8C8C), c(0xFFD6D6D6),
                c(0xFFFFFFFF), c(0xFFF8F8F8), c(0xFFF3F3F3), c(0xFFEFEFEF), c(0xFFEAEAEA),
            )
        }
        DesignSpec(
            scheme = scheme(p, dark),
            shapes = shapes(2, 4, 8, 10, 14),
            cardColor = p.background,
            cardBorder = BorderStroke(1.dp, p.outlineVariant),
            cardElevation = 0.dp,
            backdrop = null,
            preview = listOf(p.primary, p.outlineVariant, p.background),
        )
    }

    DesignStyle.Glas -> {
        val p = if (dark) {
            Palette(
                c(0xFF9DB4FF), c(0xFF0B1A4A), c(0xFF2B3F8F), c(0xFFDCE4FF), c(0x33FFFFFF), c(0xFFE8ECFA),
                Color.Transparent, c(0xFFEEF1FA), c(0xFF262C47), c(0xFFB8C0DE), c(0xFF7F8AB0), c(0xFF3A4266),
                c(0xFF101530), c(0xFF161B38), c(0xF0181D3E), c(0xFF20264A), c(0xFF2A3158),
            )
        } else {
            Palette(
                c(0xFF3B5BDB), c(0xFFFFFFFF), c(0xFFD3E0FF), c(0xFF0F2457), c(0xB3FFFFFF), c(0xFF1B1F2A),
                Color.Transparent, c(0xFF1B1F2A), c(0xFFE6EBFA), c(0xFF3F4660), c(0xFF7C86A8), c(0xFFA9B4D6),
                c(0xFFFFFFFF), c(0xFFF5F7FF), c(0xF2F2F5FF), c(0xFFF3F5FF), c(0xFFE6ECFA),
            )
        }
        val backdrop = if (dark) {
            Backdrop(
                base = c(0xFF0E1736),
                gradient = listOf(c(0xFF0E1736), c(0xFF261A4A), c(0xFF3A1B3C)),
                blobs = listOf(
                    Blob(c(0x593B4FD9), 0.10f, 0.10f, 0.85f),
                    Blob(c(0x4D9B3FBF), 0.95f, 0.50f, 0.75f),
                    Blob(c(0x40E0507A), 0.20f, 0.95f, 0.75f),
                ),
            )
        } else {
            Backdrop(
                base = c(0xFFDDEBFF),
                gradient = listOf(c(0xFFDDEBFF), c(0xFFEADFFF), c(0xFFFFE4F1)),
                blobs = listOf(
                    Blob(c(0xCCB7D2FF), 0.10f, 0.10f, 0.85f),
                    Blob(c(0xB3F2C6FF), 0.95f, 0.50f, 0.75f),
                    Blob(c(0xB3FFD6C9), 0.20f, 0.95f, 0.75f),
                ),
            )
        }
        DesignSpec(
            scheme = scheme(p, dark),
            shapes = shapes(10, 14, 20, 24, 28),
            cardColor = if (dark) c(0x17FFFFFF) else c(0x8CFFFFFF),
            cardBorder = BorderStroke(1.dp, if (dark) c(0x2EFFFFFF) else c(0xBBFFFFFF)),
            cardElevation = 0.dp,
            backdrop = backdrop,
            preview = listOf(p.primary, backdrop.gradient!![1], backdrop.gradient[0]),
        )
    }

    DesignStyle.Liquid -> {
        val p = if (dark) {
            Palette(
                c(0xFF4FD8C8), c(0xFF003732), c(0xFF0F5750), c(0xFFB9F5EE), c(0xFF3A3170), c(0xFFE3DCFF),
                Color.Transparent, c(0xFFE6F4F2), c(0xFF143330), c(0xFFA9CFCA), c(0xFF5E8C86), c(0xFF25504B),
                c(0xFF07141A), c(0xFF0C1E24), c(0xF2102A2D), c(0xFF14323A), c(0xFF1B3F46),
            )
        } else {
            Palette(
                c(0xFF00897B), c(0xFFFFFFFF), c(0xFFB9F0E8), c(0xFF00332E), c(0xFFD7CCFF), c(0xFF20164F),
                Color.Transparent, c(0xFF10211F), c(0xFFDDF1EE), c(0xFF32514D), c(0xFF6E9490), c(0xFFB5D4D0),
                c(0xFFFFFFFF), c(0xFFF2FBF9), c(0xF2F0FAF8), c(0xFFEAF7F5), c(0xFFE0F2EF),
            )
        }
        val backdrop = if (dark) {
            Backdrop(
                base = c(0xFF07141A),
                gradient = null,
                blobs = listOf(
                    Blob(c(0x8C0E7C74), 0.10f, 0.08f, 0.95f),
                    Blob(c(0x804B3AA8), 0.95f, 0.35f, 0.85f),
                    Blob(c(0x598A2F6B), 0.30f, 0.98f, 0.95f),
                    Blob(c(0x660B5C8F), 0.90f, 0.85f, 0.65f),
                ),
            )
        } else {
            Backdrop(
                base = c(0xFFEAF8F6),
                gradient = null,
                blobs = listOf(
                    Blob(c(0xBF7FE3D8), 0.10f, 0.08f, 0.95f),
                    Blob(c(0x99BBA9FF), 0.95f, 0.35f, 0.85f),
                    Blob(c(0x99FFC8AE), 0.30f, 0.98f, 0.95f),
                    Blob(c(0x80A6E8FF), 0.90f, 0.85f, 0.65f),
                ),
            )
        }
        DesignSpec(
            scheme = scheme(p, dark),
            shapes = shapes(14, 20, 32, 36, 40),
            cardColor = if (dark) c(0x14FFFFFF) else c(0xB8FFFFFF),
            cardBorder = null,
            cardElevation = 0.dp,
            backdrop = backdrop,
            preview = listOf(p.primary, p.secondaryContainer, backdrop.base),
        )
    }

    DesignStyle.Sonnig -> {
        val p = if (dark) {
            Palette(
                c(0xFFFFB74D), c(0xFF3B2100), c(0xFF6B3A00), c(0xFFFFDDB3), c(0xFF4A3A22), c(0xFFFFE9C7),
                c(0xFF1B1610), c(0xFFF2E8DA), c(0xFF2C241A), c(0xFFD3C2AA), c(0xFF8C7C66), c(0xFF4A4030),
                c(0xFF140F0A), c(0xFF221B13), c(0xFF2A2218), c(0xFF322A1E), c(0xFF3A3124),
            )
        } else {
            Palette(
                c(0xFFC75A00), c(0xFFFFFFFF), c(0xFFFFDDB3), c(0xFF3B1F00), c(0xFFFFE9C7), c(0xFF3B2A10),
                c(0xFFFFF8EC), c(0xFF2B2118), c(0xFFF7E7CF), c(0xFF5A4630), c(0xFF9C8468), c(0xFFE3D1B5),
                c(0xFFFFFFFF), c(0xFFFFF3DF), c(0xFFFCEBD0), c(0xFFF8E4C6), c(0xFFF4DEBC),
            )
        }
        DesignSpec(
            scheme = scheme(p, dark),
            shapes = shapes(6, 10, 16, 20, 28),
            cardColor = if (dark) c(0xFF2A2218) else c(0xFFFFFFFF),
            cardBorder = if (dark) null else BorderStroke(1.dp, p.outlineVariant),
            cardElevation = 1.dp,
            backdrop = null,
            preview = listOf(p.primary, p.primaryContainer, p.background),
        )
    }

    DesignStyle.Aurora -> {
        val p = if (dark) {
            Palette(
                c(0xFFD7A8FF), c(0xFF2A0B4A), c(0xFF5B2A86), c(0xFFF3E4FF), c(0x33FFFFFF), c(0xFFF4ECFF),
                Color.Transparent, c(0xFFF4F0FF), c(0xFF2A2245), c(0xFFC9C0E6), c(0xFF8E85B3), c(0xFF463C6E),
                c(0xFF0B0A1F), c(0xFF120F2B), c(0xF0161333), c(0xFF1E1A3E), c(0xFF27224D),
            )
        } else {
            Palette(
                c(0xFF7A2FD0), c(0xFFFFFFFF), c(0xFFEBD9FF), c(0xFF2C0B55), c(0xB3FFFFFF), c(0xFF2A1A40),
                Color.Transparent, c(0xFF22183A), c(0xFFF0E8FF), c(0xFF4C4168), c(0xFF8A80A8), c(0xFFC9BDE6),
                c(0xFFFFFFFF), c(0xFFFAF6FF), c(0xF2F6F0FF), c(0xFFF2EAFF), c(0xFFEADFFF),
            )
        }
        val backdrop = if (dark) {
            Backdrop(
                base = c(0xFF0B0A1F),
                gradient = listOf(c(0xFF0B0A1F), c(0xFF1B0F3A), c(0xFF0A1E2E)),
                blobs = listOf(
                    Blob(c(0x8C2EC4B6), 0.85f, 0.05f, 0.80f),
                    Blob(c(0x807B2FF7), 0.10f, 0.35f, 0.85f),
                    Blob(c(0x66F72585), 0.80f, 0.70f, 0.75f),
                    Blob(c(0x664361EE), 0.25f, 0.95f, 0.70f),
                ),
            )
        } else {
            Backdrop(
                base = c(0xFFF4EEFF),
                gradient = listOf(c(0xFFF4EEFF), c(0xFFEAF6FF), c(0xFFFFEEF6)),
                blobs = listOf(
                    Blob(c(0x9988E6DC), 0.85f, 0.05f, 0.80f),
                    Blob(c(0x88C8A2FF), 0.10f, 0.35f, 0.85f),
                    Blob(c(0x77FF9CCB), 0.80f, 0.70f, 0.75f),
                    Blob(c(0x6699B4FF), 0.25f, 0.95f, 0.70f),
                ),
            )
        }
        DesignSpec(
            scheme = scheme(p, dark).copy(
                tertiary = if (dark) c(0xFFFF6FB5) else c(0xFFD1307A),
                onTertiary = if (dark) c(0xFF3D0024) else c(0xFFFFFFFF),
            ),
            shapes = shapes(12, 16, 24, 28, 32),
            cardColor = if (dark) c(0x1AFFFFFF) else c(0x99FFFFFF),
            cardBorder = null,
            cardElevation = 0.dp,
            backdrop = backdrop,
            preview = listOf(p.primary, c(0xFF2EC4B6), c(0xFFF72585)),
            glow = 0.7f,
        )
    }

    DesignStyle.Natur -> {
        val p = if (dark) {
            Palette(
                c(0xFF8FD0A0), c(0xFF0B2514), c(0xFF1F5238), c(0xFFCDEBD7), c(0xFF2B3D2F), c(0xFFDDEBDD),
                c(0xFF0F1912), c(0xFFE1EDE2), c(0xFF1B2A1F), c(0xFFB5CDB9), c(0xFF6E8A74), c(0xFF32483A),
                c(0xFF0A120C), c(0xFF15201A), c(0xFF1A261D), c(0xFF202E24), c(0xFF26362B),
            )
        } else {
            Palette(
                c(0xFF2F6F4F), c(0xFFFFFFFF), c(0xFFCDEBD7), c(0xFF0C2A1B), c(0xFFDDEBDD), c(0xFF1B2A1F),
                c(0xFFF2F6F0), c(0xFF1B2A1F), c(0xFFE1EBDF), c(0xFF3E5244), c(0xFF7F9684), c(0xFFC5D6C6),
                c(0xFFFFFFFF), c(0xFFEDF3EB), c(0xFFE8F0E6), c(0xFFE2EBDF), c(0xFFDCE6D9),
            )
        }
        DesignSpec(
            scheme = scheme(p, dark),
            shapes = shapes(6, 10, 14, 18, 24),
            cardColor = null,
            cardBorder = null,
            cardElevation = 0.dp,
            backdrop = null,
            preview = listOf(p.primary, p.primaryContainer, p.background),
        )
    }
}

// ---------------------------------------------------------------- Eigene Farben

/**
 * Eigene Farben fuer ein Design (null = Standard des Designs).
 * Bei Glas und Liquid: a = Hauptfarbe, b = Hintergrund oben/links, c = Hintergrund unten/rechts.
 * Bei den anderen: a = Hauptfarbe, b = Zweitfarbe (Flaechen von Auswahl und Knoepfen), c = Hintergrund.
 * Gespeichert als ARGB-Zahl.
 */
data class DesignTweak(val a: Long? = null, val b: Long? = null, val c: Long? = null) {
    val isEmpty: Boolean get() = a == null && b == null && c == null
}

/** Ob das Design einen farbigen Hintergrund mit Farbflecken hat (Glas, Liquid). */
fun DesignStyle.hasBackdrop(): Boolean = this == DesignStyle.Glas || this == DesignStyle.Liquid || this == DesignStyle.Aurora

/** Hellt eine Farbe im dunklen Modus auf bzw. dunkelt sie im hellen Modus ab, bis Text in dieser Farbe gut lesbar ist. */
private fun readableOn(color: Color, dark: Boolean): Color {
    var c = color
    var step = 0
    if (dark) {
        while (c.luminance() < 0.30f && step < 12) {
            c = lerp(c, Color.White, 0.15f)
            step++
        }
    } else {
        while (c.luminance() > 0.25f && step < 12) {
            c = lerp(c, Color.Black, 0.15f)
            step++
        }
    }
    return c
}

private fun onColor(color: Color): Color = if (color.luminance() > 0.5f) Color(0xFF111111) else Color.White

fun tweakedSpec(style: DesignStyle, dark: Boolean, tweak: DesignTweak?): DesignSpec {
    val base = designSpec(style, dark)
    if (tweak == null || tweak.isEmpty) return base
    var scheme = base.scheme
    var card = base.cardColor
    var backdrop = base.backdrop
    val deep = if (dark) Color(0xFF14141A) else Color.White

    tweak.a?.let { raw ->
        // Hauptfarbe wird auch fuer Schrift (Knoepfe, Links) genutzt: so weit aufhellen bzw. abdunkeln, dass sie lesbar bleibt.
        val pc = readableOn(Color(raw), dark)
        val container = lerp(deep, pc, if (dark) 0.40f else 0.25f)
        scheme = scheme.copy(
            primary = pc, onPrimary = onColor(pc), secondary = pc, onSecondary = onColor(pc),
            primaryContainer = container, onPrimaryContainer = onColor(container), surfaceTint = pc,
        )
    }

    if (backdrop != null) {
        if (tweak.b != null || tweak.c != null) {
            val old = backdrop
            val t1 = tweak.b?.let { Color(it) } ?: old.blobs[0].color.copy(alpha = 1f)
            val t2 = tweak.c?.let { Color(it) } ?: old.blobs[1].color.copy(alpha = 1f)
            val k = if (dark) 0.35f else 0.30f
            val c1 = lerp(deep, t1, k)
            val c2 = lerp(deep, t2, k)
            val mid = lerp(c1, c2, 0.5f)
            backdrop = Backdrop(
                base = if (old.gradient != null) c1 else lerp(deep, lerp(t1, t2, 0.5f), if (dark) 0.18f else 0.20f),
                gradient = if (old.gradient != null) listOf(c1, mid, c2) else null,
                blobs = old.blobs.mapIndexed { i, b -> Blob((if (i % 2 == 0) t1 else t2).copy(alpha = b.color.alpha), b.x, b.y, b.radius) },
            )
        }
    } else {
        tweak.b?.let { raw ->
            val sc = Color(raw)
            scheme = scheme.copy(secondaryContainer = sc, onSecondaryContainer = onColor(sc))
        }
        tweak.c?.let { raw ->
            val bg = Color(raw)
            val on = onColor(bg)
            fun tone(k: Float) = lerp(bg, on, k)
            scheme = scheme.copy(
                background = bg, onBackground = on, surface = bg, onSurface = on,
                surfaceContainerLowest = bg, surfaceContainerLow = tone(0.03f), surfaceContainer = tone(0.06f),
                surfaceContainerHigh = tone(0.09f), surfaceContainerHighest = tone(0.12f),
                surfaceVariant = tone(0.08f), onSurfaceVariant = lerp(on, bg, 0.25f),
            )
            if (card != null && card.alpha >= 0.99f) {
                card = if (dark) lerp(bg, Color.White, 0.06f) else lerp(bg, Color.White, 0.55f)
            }
        }
    }

    val preview = if (backdrop != null && (tweak.b != null || tweak.c != null)) {
        listOf(scheme.primary, backdrop.blobs.getOrNull(1)?.color?.copy(alpha = 1f) ?: base.preview[1], backdrop.blobs[0].color.copy(alpha = 1f))
    } else {
        listOf(scheme.primary, tweak.b?.let { Color(it) } ?: base.preview[1], tweak.c?.let { Color(it) } ?: base.preview[2])
    }
    return DesignSpec(
        scheme = scheme,
        shapes = base.shapes,
        cardColor = card,
        cardBorder = base.cardBorder,
        cardElevation = base.cardElevation,
        backdrop = backdrop,
        preview = preview,
        glow = base.glow,
    )
}
