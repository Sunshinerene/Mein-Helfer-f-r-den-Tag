package de.meinhelfer.tag.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.summary.Expression

private val SUN = Color(0xFFF6C453)
private val TEAL = Color(0xFF1FA3A8)
private val PETROL = Color(0xFF0B4A5E)
private val EYE = Color(0xFFF7FFFF)

/**
 * Digitales Gesicht des Assistenten: eine leuchtende, weiche Form in den Logo-Farben mit zwei Augen und einer Mundlinie.
 * Blinzelt und „atmet“ leicht; ohne Animationen (Einstellungen → Effekte) bleibt es still.
 */
@Composable
fun AssistantFace(
    expression: Expression,
    size: Dp = 48.dp,
    speaking: Boolean = false,
    modifier: Modifier = Modifier,
    description: String = "Gesicht des Assistenten",
) {
    val animate = LocalEffects.current.animations
    val transition = rememberInfiniteTransition(label = "gesicht")
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                1f at 0
                1f at 3900
                0.1f at 4020
                1f at 4200
            },
        ),
        label = "blinzeln",
    )
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "atmen",
    )
    val talk by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(180, easing = LinearEasing), RepeatMode.Reverse),
        label = "sprechen",
    )
    val eyeOpen = if (animate) blink else 1f
    val grow = if (animate) breath else 1f
    val mouthOpen = if (speaking && animate) talk else if (speaking) 0.6f else 0f
    // Bild von Sunny (Einstellung), je nach Lage ein anderer Ausdruck
    val useImage by SunnyAvatar.useImage.collectAsState()
    if (useImage) {
        val ring = if (speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(SunnyAvatar.drawable(expression)),
            contentDescription = description,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier
                .size(size)
                .graphicsLayer {
                    scaleX = if (speaking && animate) 1f + (talk - 0.6f) * 0.04f else grow
                    scaleY = scaleX
                }
                .clip(androidx.compose.foundation.shape.CircleShape)
                .border(if (speaking) 2.dp else 1.dp, ring, androidx.compose.foundation.shape.CircleShape),
        )
        return
    }

    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        scale(grow) { drawFace(expression, eyeOpen, mouthOpen) }
    }
}

private fun DrawScope.drawFace(expression: Expression, eyeOpen: Float, mouthOpen: Float) {
    val s = size.minDimension
    val inset = s * 0.06f
    val body = Size(s - 2 * inset, s - 2 * inset)
    // Leichter Schein um die Form
    drawRoundRect(
        brush = Brush.radialGradient(listOf(SUN.copy(alpha = 0.35f), Color.Transparent), center = center, radius = s * 0.62f),
        topLeft = Offset.Zero,
        size = Size(s, s),
        cornerRadius = CornerRadius(s * 0.45f),
    )
    // Körper: Sonnenlicht oben links, Petrol unten
    drawRoundRect(
        brush = Brush.radialGradient(
            0f to SUN,
            0.38f to TEAL,
            1f to PETROL,
            center = Offset(s * 0.32f, s * 0.26f),
            radius = s * 0.95f,
        ),
        topLeft = Offset(inset, inset),
        size = body,
        cornerRadius = CornerRadius(s * 0.36f),
    )
    // Glanz oben
    drawOval(
        color = Color.White.copy(alpha = 0.18f),
        topLeft = Offset(s * 0.22f, s * 0.12f),
        size = Size(s * 0.42f, s * 0.14f),
    )

    val eyeY = s * 0.45f
    val leftX = s * 0.36f
    val rightX = s * 0.64f
    val eyeW = s * 0.085f
    val stroke = s * 0.055f

    fun capsule(x: Float, h: Float, dx: Float = 0f, dy: Float = 0f) {
        val hh = (h * eyeOpen).coerceAtLeast(s * 0.02f)
        drawRoundRect(
            color = EYE,
            topLeft = Offset(x - eyeW / 2 + dx, eyeY - hh / 2 + dy),
            size = Size(eyeW, hh),
            cornerRadius = CornerRadius(eyeW / 2),
        )
    }

    fun arcEye(x: Float, up: Boolean) {
        val p = Path()
        val w = eyeW * 1.5f
        val h = s * 0.05f
        if (up) {
            p.moveTo(x - w / 2, eyeY + h / 2)
            p.quadraticTo(x, eyeY - h * 1.4f, x + w / 2, eyeY + h / 2)
        } else {
            p.moveTo(x - w / 2, eyeY)
            p.quadraticTo(x, eyeY + h, x + w / 2, eyeY)
        }
        drawPath(p, EYE, style = Stroke(width = stroke * 0.8f, cap = StrokeCap.Round))
    }

    fun mouth(widthFactor: Float, curve: Float, y: Float = s * 0.66f) {
        val w = s * widthFactor
        if (mouthOpen > 0f) {
            val h = s * 0.03f + s * 0.09f * mouthOpen
            drawOval(EYE, topLeft = Offset(center.x - w * 0.3f, y - h / 2), size = Size(w * 0.6f, h))
            return
        }
        val p = Path()
        p.moveTo(center.x - w / 2, y)
        p.quadraticTo(center.x, y + s * curve, center.x + w / 2, y)
        drawPath(p, EYE, style = Stroke(width = stroke * 0.75f, cap = StrokeCap.Round))
    }

    when (expression) {
        Expression.Friendly -> {
            capsule(leftX, s * 0.15f)
            capsule(rightX, s * 0.15f)
            mouth(0.24f, 0.07f)
        }
        Expression.Happy -> {
            arcEye(leftX, up = true)
            arcEye(rightX, up = true)
            mouth(0.30f, 0.11f)
        }
        Expression.Attentive -> {
            capsule(leftX, s * 0.18f)
            capsule(rightX, s * 0.18f)
            mouth(0.14f, 0.02f)
            // kleines Leuchten oben rechts: „da ist etwas“
            drawCircle(SUN, radius = s * 0.05f, center = Offset(s * 0.80f, s * 0.22f))
        }
        Expression.Concerned -> {
            capsule(leftX, s * 0.13f, dy = s * 0.01f)
            capsule(rightX, s * 0.13f, dy = s * 0.01f)
            // sanft gehobene innere Brauen
            val b = Stroke(width = stroke * 0.6f, cap = StrokeCap.Round)
            drawLine(EYE, Offset(leftX - eyeW, eyeY - s * 0.12f), Offset(leftX + eyeW, eyeY - s * 0.15f), strokeWidth = b.width, cap = StrokeCap.Round)
            drawLine(EYE, Offset(rightX - eyeW, eyeY - s * 0.15f), Offset(rightX + eyeW, eyeY - s * 0.12f), strokeWidth = b.width, cap = StrokeCap.Round)
            mouth(0.18f, 0.025f, y = s * 0.68f)
        }
        Expression.Thinking -> {
            capsule(leftX, s * 0.14f, dx = s * 0.025f, dy = -s * 0.025f)
            capsule(rightX, s * 0.14f, dx = s * 0.025f, dy = -s * 0.025f)
            val p = Path()
            p.moveTo(center.x - s * 0.08f, s * 0.68f)
            p.lineTo(center.x + s * 0.08f, s * 0.655f)
            drawPath(p, EYE, style = Stroke(width = stroke * 0.7f, cap = StrokeCap.Round))
        }
        Expression.Calm -> {
            arcEye(leftX, up = false)
            arcEye(rightX, up = false)
            mouth(0.20f, 0.05f)
        }
    }
}


/** Bild von Sunny statt des gezeichneten Gesichts (Einstellungen → Persönlicher Assistent). */
object SunnyAvatar {
    val useImage = kotlinx.coroutines.flow.MutableStateFlow(true)
    val popup = kotlinx.coroutines.flow.MutableStateFlow(SunnyPopup())

    fun drawable(e: Expression): Int = when (e) {
        Expression.Friendly -> de.meinhelfer.tag.R.drawable.sunny_freundlich
        Expression.Happy -> de.meinhelfer.tag.R.drawable.sunny_froh
        Expression.Attentive -> de.meinhelfer.tag.R.drawable.sunny_ernst
        Expression.Concerned -> de.meinhelfer.tag.R.drawable.sunny_besorgt
        Expression.Thinking -> de.meinhelfer.tag.R.drawable.sunny_ernst
        Expression.Calm -> de.meinhelfer.tag.R.drawable.sunny_portrait
    }
}
