package tv.plexiplay.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * Thème « Futuriste » : uniquement de l'apparence. Tout est dessiné en arrière-plan ou en
 * transformation graphique (échelle, transparence) ; aucune fonction de l'application n'en dépend.
 */

/** Petit zoom « au clic » : on le déclenche juste avant d'ouvrir l'élément. Sans effet hors thème Futuriste. */
@Stable
class ClickPulse(private val scope: CoroutineScope) {
    val scale = Animatable(1f)

    fun fire(amount: Float = 1.18f) {
        if (!ThemeMode.future) return
        scope.launch {
            scale.snapTo(1f)
            scale.animateTo(amount, tween(90))
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 350f))
        }
    }
}

@Composable
fun rememberClickPulse(): ClickPulse {
    val scope = rememberCoroutineScope()
    return remember { ClickPulse(scope) }
}

/**
 * Faisceau lumineux (deux comètes de couleurs différentes) qui tourne autour du contour de l'élément qui a le focus.
 * À poser sur un élément de la taille du contour à éclairer.
 */
@Composable
fun Modifier.futureBeam(focused: Boolean, corner: Dp = 6.dp): Modifier {
    if (!ThemeMode.future || !focused) return this
    val transition = rememberInfiniteTransition(label = "beam")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "beam-angle",
    )
    val head = Accent
    val tail = Signal
    return this.drawWithContent {
        drawContent()
        val deg = angle
        val brush = object : ShaderBrush() {
            override fun createShader(size: Size): android.graphics.Shader {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val shader = SweepGradientShader(
                    Offset(cx, cy),
                    listOf(
                        Color.Transparent, Color.Transparent, head.copy(alpha = 0.85f), Color.White, Color.Transparent,
                        Color.Transparent, tail.copy(alpha = 0.85f), FocusRing, Color.Transparent,
                    ),
                    listOf(0f, 0.12f, 0.32f, 0.45f, 0.5f, 0.62f, 0.82f, 0.95f, 1f),
                )
                shader.setLocalMatrix(android.graphics.Matrix().apply { setRotate(deg, cx, cy) })
                return shader
            }
        }
        val radius = CornerRadius(corner.toPx())
        val line = 3.dp.toPx()
        // Halo large et doux, puis trait net par-dessus.
        drawRoundRect(brush, topLeft = Offset(line, line), size = Size(size.width - 2 * line, size.height - 2 * line), cornerRadius = radius, alpha = 0.28f, style = Stroke(line * 3f))
        drawRoundRect(brush, topLeft = Offset(line / 2f, line / 2f), size = Size(size.width - line, size.height - line), cornerRadius = radius, style = Stroke(line))
    }
}

/** Décor animé : halos qui dérivent lentement et grille en perspective qui avance vers l'écran. */
@Composable
fun FutureAmbience(modifier: Modifier = Modifier) {
    if (!ThemeMode.future) return
    val transition = rememberInfiniteTransition(label = "ambience")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "flow",
    )
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val a = drift * 2f * PI.toFloat()
        drawRect(
            Brush.radialGradient(
                listOf(Color(0x665B2A86), Color.Transparent),
                center = Offset(w * (0.78f + 0.14f * cos(a)), h * (0.18f + 0.14f * sin(a))),
                radius = w * 0.5f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color(0x550B8E9A), Color.Transparent),
                center = Offset(w * (0.18f + 0.12f * sin(a)), h * (0.82f + 0.1f * cos(a))),
                radius = w * 0.5f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color(0x33305CDE), Color.Transparent),
                center = Offset(w * (0.5f + 0.25f * cos(2f * a)), h * (0.5f + 0.2f * sin(2f * a))),
                radius = w * 0.4f,
            ),
        )
        // Grille en perspective, en bas de l'écran.
        val horizon = h * 0.62f
        val vx = w * 0.5f
        val line = 1.dp.toPx()
        for (i in -16..16) {
            val xTop = vx + i * w * 0.012f
            val xBottom = vx + i * w * 0.12f
            drawLine(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Accent.copy(alpha = 0.16f)),
                    startY = horizon,
                    endY = h,
                ),
                start = Offset(xTop, horizon),
                end = Offset(xBottom, h),
                strokeWidth = line,
            )
        }
        val rows = 9
        for (k in 0 until rows) {
            val p = (k + flow) / rows
            val y = horizon + (h - horizon) * p * p
            drawLine(
                Accent.copy(alpha = (0.05f + 0.17f * p).coerceIn(0f, 0.3f)),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = line,
            )
        }
    }
}

/** Trait lumineux qui balaie l'écran de haut en bas à chaque changement de [key] (page, onglet, style…). */
@Composable
fun FutureSweep(key: Any) {
    if (!ThemeMode.future) return
    val progress = remember { Animatable(1f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        if (p <= 0.001f || p >= 0.999f) return@Canvas
        val y = size.height * p
        val fade = (1f - p * 0.6f).coerceIn(0f, 1f)
        val trail = 170.dp.toPx()
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Accent.copy(alpha = 0.16f * fade)),
                startY = y - trail,
                endY = y,
            ),
            topLeft = Offset(0f, y - trail),
            size = Size(size.width, trail),
        )
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Accent.copy(alpha = fade), Color.White.copy(alpha = fade), Accent.copy(alpha = fade), Color.Transparent),
            ),
            topLeft = Offset(0f, y - 1.5.dp.toPx()),
            size = Size(size.width, 3.dp.toPx()),
        )
    }
}

/** Entrée d'un écran (fiche film, fiche série) : léger zoom depuis le centre avec fondu. */
@Composable
fun Modifier.futureEntrance(): Modifier {
    if (!ThemeMode.future) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(460, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer {
        val p = progress.value
        val s = 0.9f + 0.1f * p
        scaleX = s
        scaleY = s
        alpha = (0.2f + 0.8f * p).coerceIn(0f, 1f)
    }
}

/** Panneau de verre derrière le texte d'une fiche : fond translucide, contour clair et reflet qui glisse. */
@Composable
fun FutureGlass(modifier: Modifier = Modifier) {
    if (!ThemeMode.future) return
    val transition = rememberInfiniteTransition(label = "glass")
    val sheen by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "sheen",
    )
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x2AFFFFFF), Color(0x0AFFFFFF))), shape)
            .border(1.dp, Accent.copy(alpha = 0.4f), shape)
            .drawWithContent {
                drawContent()
                val x = size.width * sheen
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                        startX = x - size.width * 0.18f,
                        endX = x + size.width * 0.18f,
                    ),
                )
            },
    )
}
