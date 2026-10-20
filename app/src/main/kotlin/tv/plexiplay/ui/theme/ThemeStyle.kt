package tv.plexiplay.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val NeonIdle = Color(0xFF262F5E)
private val GreyIdle = setOf(Color(0xFF2A333C), Color(0xFF232A32), Color(0xFF1C2128))

/**
 * Bouton : en thème classique, anneau de focus et aplat comme avant ; en thème « Néon », bouton 3D
 * (socle foncé, dégradé, reflet) avec un contour doré quand il a le focus (sans halo qui déborde).
 */
fun Modifier.themedButton(fill: Color, focused: Boolean, radius: Dp = 6.dp): Modifier {
    if (!ThemeMode.neon) {
        val shape = RoundedCornerShape(radius)
        return this.border(2.dp, if (focused) FocusRing else Color.Transparent, shape).background(fill, shape)
    }
    val base = if (fill in GreyIdle || fill == Panel) NeonIdle else fill
    val ring = FocusRing
    return this.drawBehind {
        val r = CornerRadius(radius.toPx() * 1.8f)
        val lift = 3.dp.toPx()
        drawRoundRect(lerp(base, Color.Black, 0.55f), topLeft = Offset(0f, lift), size = size, cornerRadius = r)
        drawRoundRect(
            Brush.verticalGradient(listOf(lerp(base, Color.White, 0.30f), base, lerp(base, Color.Black, 0.18f))),
            size = size,
            cornerRadius = r,
        )
        drawRoundRect(Color.White.copy(alpha = 0.30f), size = size, cornerRadius = r, style = Stroke(1.dp.toPx()))
        if (focused) {
            drawRoundRect(ring, size = Size(size.width, size.height + lift), cornerRadius = r, style = Stroke(2.dp.toPx()))
        }
    }
}

/** Fond d'écran : uni en classique, dégradé nuit avec halos violet et turquoise en « Néon ». */
fun Modifier.screenBackground(): Modifier {
    if (!ThemeMode.neon) return this.background(Ink)
    return this.drawBehind {
        drawRect(Color(0xFF070A1C))
        drawRect(
            Brush.radialGradient(
                listOf(Color(0x805B2A86), Color.Transparent),
                center = Offset(size.width * 0.9f, 0f),
                radius = size.width * 0.6f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color(0x660B6E7A), Color.Transparent),
                center = Offset(0f, size.height),
                radius = size.width * 0.55f,
            ),
        )
    }
}

/** Panneau latéral : aplat en classique, dégradé translucide en « Néon ». */
fun Modifier.panelBackground(): Modifier {
    if (!ThemeMode.neon) return this.background(Panel)
    return this.background(Brush.verticalGradient(listOf(Color(0xFF1A2145), Color(0xFF0E1230))), RectangleShape)
}
