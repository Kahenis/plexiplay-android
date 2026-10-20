package tv.plexiplay.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.roundToInt

private fun Context.activity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Pendant la lecture : écran en paysage (selon le capteur) et barres du système masquées.
 * À la sortie, l'orientation redevient libre.
 */
@Composable
internal fun ImmersiveLandscape() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.activity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            controller?.show(WindowInsetsCompat.Type.navigationBars())
        }
    }
}

/**
 * Gestes sur l'image : un toucher affiche ou masque les commandes, un double toucher saute de 10 s
 * (moitié gauche : en arrière, moitié droite : en avant), un glissement vertical règle la luminosité
 * (moitié gauche) ou le volume (moitié droite).
 */
@Composable
internal fun Modifier.playerGestures(
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onFeedback: (String) -> Unit,
): Modifier {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    return this
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { onToggle() },
                onDoubleTap = { point -> onSeek(if (point.x < size.width / 2f) -1 else 1) },
            )
        }
        .pointerInput(Unit) {
            var left = false
            var accumulated = 0f
            var startVolume = 0
            var startBrightness = 0.5f
            detectVerticalDragGestures(
                onDragStart = { point ->
                    left = point.x < size.width / 2f
                    accumulated = 0f
                    startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val window = context.activity()?.window
                    val set = window?.attributes?.screenBrightness ?: -1f
                    startBrightness = if (set >= 0f) set else {
                        runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }
                            .getOrDefault(0.5f)
                    }
                },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    accumulated -= dy
                    val fraction = accumulated / (size.height * 0.7f)
                    if (left) {
                        val window = context.activity()?.window ?: return@detectVerticalDragGestures
                        val value = (startBrightness + fraction).coerceIn(0.02f, 1f)
                        window.attributes = window.attributes.also { it.screenBrightness = value }
                        onFeedback("Luminosité " + (value * 100).roundToInt() + " %")
                    } else {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val level = (startVolume + fraction * max).roundToInt().coerceIn(0, max)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
                        onFeedback("Volume " + (level * 100 / max) + " %")
                    }
                },
            )
        }
}

/** Barre de progression tactile : toucher pour aller à cet endroit, glisser pour parcourir (le saut se fait au relâchement). */
@Composable
internal fun SeekBar(
    fraction: Float,
    onSeek: (Float) -> Unit,
    onTouch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val shown = scrub ?: fraction
    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { point ->
                    onTouch()
                    onSeek((point.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { point ->
                        onTouch()
                        scrub = (point.x / size.width).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onTouch()
                        scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        scrub?.let { onSeek(it) }
                        scrub = null
                    },
                    onDragCancel = { scrub = null },
                )
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            val middle = size.height / 2f
            val track = 3.dp.toPx()
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(0f, middle - track / 2f), Size(size.width, track), CornerRadius(track, track))
            drawRoundRect(Color.White, Offset(0f, middle - track / 2f), Size(size.width * shown, track), CornerRadius(track, track))
            drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset((size.width * shown).coerceIn(7.dp.toPx(), size.width - 7.dp.toPx()), middle))
        }
    }
}

/** Pause automatique quand l'appli passe en arrière-plan (la fenêtre de lecture est alors masquée). */
object BackgroundPause {
    @Volatile
    var action: (() -> Unit)? = null
}

@Composable
internal fun PauseWhenBackgrounded(pause: () -> Unit) {
    val current by androidx.compose.runtime.rememberUpdatedState(pause)
    DisposableEffect(Unit) {
        val hook: () -> Unit = { current() }
        BackgroundPause.action = hook
        onDispose { if (BackgroundPause.action === hook) BackgroundPause.action = null }
    }
}
