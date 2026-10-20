package tv.plexiplay.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import tv.plexiplay.R
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Panel
import tv.plexiplay.ui.theme.Signal

private const val NAME = "Plexiplay"

private fun clamp01(v: Float) = v.coerceIn(0f, 1f)
private fun easeInOut(t: Float) = t * t * (3f - 2f * t)
private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)
private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

/** Petite animation cachée : le nom tourne, se resserre et devient le logo. Appelle [onDone] à la fin. */
@Composable
fun LogoEgg(onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(5200, easing = LinearEasing))
        onDone()
    }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .size(520.dp, 330.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Ink)
                .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .graphicsLayer { alpha = 1f - clamp01((progress.value - 0.94f) / 0.06f) },
            contentAlignment = Alignment.Center,
        ) {
            val paint = remember {
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = progress.value
                val cx = size.width / 2f
                val cy = size.height / 2f
                val textPx = 46.dp.toPx()
                paint.textSize = textPx
                val widths = NAME.map { paint.measureText(it.toString()) }
                val total = widths.sum()
                val n = NAME.length
                val baseRadius = min(size.width, size.height) * 0.30f
                val move = easeInOut(clamp01((t - 0.30f) / 0.25f))
                val spinT = clamp01((t - 0.30f) / 0.50f)
                val spin = (spinT * spinT * 3.5f * 2f * PI).toFloat()
                val shrink = easeInOut(clamp01((t - 0.58f) / 0.20f))
                val fade = clamp01((t - 0.70f) / 0.10f)
                var cursor = cx - total / 2f
                for (i in 0 until n) {
                    val w = widths[i]
                    val lineX = cursor + w / 2f
                    cursor += w
                    val lineY = cy
                    val angle = (-PI / 2.0).toFloat() + i * (2f * PI.toFloat() / n) + spin
                    val radius = baseRadius * (1f - shrink)
                    val circleX = cx + cos(angle) * radius
                    val circleY = cy + sin(angle) * radius
                    val x = lineX + (circleX - lineX) * move
                    val y = lineY + (circleY - lineY) * move
                    val appear = clamp01((t - i * 0.014f) / 0.05f)
                    val grow = easeOutBack(appear)
                    val alpha = appear * (1f - fade)
                    if (alpha <= 0.01f) continue
                    val rotation = move * (angle * 180f / PI.toFloat() + 90f)
                    paint.color = (if (i == 0) Signal else Accent).copy(alpha = alpha).toArgb()
                    withTransform({
                        translate(x, y)
                        rotate(rotation, pivot = androidx.compose.ui.geometry.Offset.Zero)
                        scale(grow, grow, pivot = androidx.compose.ui.geometry.Offset.Zero)
                    }) {
                        drawContext.canvas.nativeCanvas.drawText(NAME[i].toString(), 0f, textPx * 0.35f, paint)
                    }
                }
                // éclair orange au moment où les lettres se fondent dans le logo
                val flash = clamp01(1f - kotlin.math.abs(t - 0.76f) / 0.08f)
                if (flash > 0f) {
                    drawCircle(Signal.copy(alpha = 0.55f * flash), radius = 40.dp.toPx() + 90.dp.toPx() * flash, center = androidx.compose.ui.geometry.Offset(cx, cy))
                }
            }
            Image(
                painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        val k = clamp01((progress.value - 0.72f) / 0.20f)
                        val s = 0.2f + 0.8f * easeOutBack(k)
                        scaleX = s
                        scaleY = s
                        rotationZ = (1f - easeOut(k)) * 360f
                        alpha = clamp01(k * 2f)
                    }
                    .clip(RoundedCornerShape(30.dp)),
            )
        }
    }
}
