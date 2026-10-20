package tv.plexiplay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Thème actif : classique (par défaut) ou « Néon » (réglage dans les paramètres). */
object ThemeMode {
    var neon by mutableStateOf(false)
}

val Ink: Color get() = if (ThemeMode.neon) Color(0xFF070A1C) else Color(0xFF121418)
val Panel: Color get() = if (ThemeMode.neon) Color(0xFF141935) else Color(0xFF1C2128)
val Accent: Color get() = if (ThemeMode.neon) Color(0xFF3DD6D8) else Color(0xFF3DBEBF)
val Signal: Color get() = if (ThemeMode.neon) Color(0xFFFF9F43) else Color(0xFFE39A3C)
val Paper = Color(0xFFF4F1EA)
val Muted = Color(0xFF9AA3AD)
val FocusRing: Color get() = if (ThemeMode.neon) Color(0xFFFFE3B8) else Color(0xFFE8C07A)

@Composable
fun PlexiplayTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        background = Ink,
        surface = Panel,
        primary = Accent,
        onPrimary = Ink,
        onBackground = Paper,
        onSurface = Paper,
    )
    MaterialTheme(colorScheme = colors, content = content)
}
