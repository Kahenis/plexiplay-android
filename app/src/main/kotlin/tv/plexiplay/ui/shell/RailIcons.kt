package tv.plexiplay.ui.shell

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import java.text.Normalizer

internal enum class RailIconKind { HOME, SEARCH, MOVIE, TV, MIC, FOLDER }

internal data class RailLook(val icon: RailIconKind, val tag: String)

private val keywords = listOf(
    "film" to RailIconKind.MOVIE,
    "serie" to RailIconKind.TV,
    "spectacle" to RailIconKind.MIC,
)

private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

private fun tagOf(words: List<String>): String =
    words.joinToString("").filter { it.isLetterOrDigit() }.take(3).uppercase()

/**
 * Icône d'après le mot clé (film, série, spectacle, au singulier ou au pluriel, sans tenir
 * compte des accents). L'étiquette reprend les 3 premiers caractères de ce qui suit le mot clé.
 * Sans mot clé : dossier et 3 premiers caractères du nom.
 */
internal fun railLook(entry: RailEntry): RailLook {
    if (entry.id == "home" || entry.type == "home") return RailLook(RailIconKind.HOME, "")
    if (entry.id == "search") return RailLook(RailIconKind.SEARCH, "")
    val words = entry.title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    words.forEachIndexed { index, word ->
        val letters = fold(word).filter { it.isLetter() }
        val match = keywords.firstOrNull { (key, _) -> letters == key || letters == key + "s" }
        if (match != null) return RailLook(match.second, tagOf(words.drop(index + 1)))
    }
    return RailLook(RailIconKind.FOLDER, tagOf(words))
}

internal fun DrawScope.drawRailIcon(kind: RailIconKind, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = size.minDimension * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * w, y * h)
    when (kind) {
        RailIconKind.HOME -> {
            val roof = Path().apply {
                moveTo(0.12f * w, 0.50f * h); lineTo(0.50f * w, 0.16f * h); lineTo(0.88f * w, 0.50f * h)
            }
            drawPath(roof, color, style = stroke)
            val body = Path().apply {
                moveTo(0.24f * w, 0.44f * h); lineTo(0.24f * w, 0.84f * h)
                lineTo(0.76f * w, 0.84f * h); lineTo(0.76f * w, 0.44f * h)
            }
            drawPath(body, color, style = stroke)
            drawLine(color, p(0.50f, 0.84f), p(0.50f, 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        RailIconKind.MOVIE -> {
            drawRoundRect(color, p(0.14f, 0.40f), Size(0.72f * w, 0.44f * h), CornerRadius(0.05f * w), style = stroke)
            drawRoundRect(color, p(0.14f, 0.18f), Size(0.72f * w, 0.18f * h), CornerRadius(0.05f * w), style = stroke)
            for (x in listOf(0.38f, 0.58f, 0.78f)) {
                drawLine(color, p(x, 0.18f), p(x - 0.10f, 0.36f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
        RailIconKind.TV -> {
            drawRoundRect(color, p(0.12f, 0.30f), Size(0.76f * w, 0.54f * h), CornerRadius(0.08f * w), style = stroke)
            drawLine(color, p(0.50f, 0.30f), p(0.34f, 0.14f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, p(0.50f, 0.30f), p(0.66f, 0.14f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        RailIconKind.MIC -> {
            drawRoundRect(color, p(0.38f, 0.10f), Size(0.24f * w, 0.40f * h), CornerRadius(0.12f * w), style = stroke)
            drawArc(
                color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = p(0.25f, 0.28f), size = Size(0.50f * w, 0.50f * h), style = stroke,
            )
            drawLine(color, p(0.50f, 0.78f), p(0.50f, 0.90f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, p(0.36f, 0.90f), p(0.64f, 0.90f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        RailIconKind.SEARCH -> {
            drawCircle(color, radius = 0.26f * w, center = p(0.44f, 0.44f), style = stroke)
            drawLine(color, p(0.64f, 0.64f), p(0.86f, 0.86f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        RailIconKind.FOLDER -> {
            val folder = Path().apply {
                moveTo(0.12f * w, 0.24f * h); lineTo(0.40f * w, 0.24f * h); lineTo(0.48f * w, 0.36f * h)
                lineTo(0.88f * w, 0.36f * h); lineTo(0.88f * w, 0.80f * h); lineTo(0.12f * w, 0.80f * h); close()
            }
            drawPath(folder, color, style = stroke)
        }
    }
}
