package tv.plexiplay.ui.shell

import tv.plexiplay.ui.theme.ThemeMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tv.plexiplay.ui.theme.Paper

private fun now(): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE).format(java.util.Date())

/** Heure du système (24 h), en haut à droite. Ne prend pas le focus. */
@Composable
fun ClockChip(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) {
        while (true) {
            text = now()
            delay(10_000)
        }
    }
    val neon = ThemeMode.neon
    Text(
        text,
        color = if (neon) Color(0xFF9FF8F9) else Paper,
        fontSize = if (neon) 17.sp else 15.sp,
        fontWeight = if (neon) FontWeight.ExtraBold else FontWeight.SemiBold,
        modifier = modifier
            .background(if (neon) Color(0xE60A0F26) else Color(0xCC121418), RoundedCornerShape(if (neon) 12.dp else 8.dp))
            .border(1.dp, if (neon) Color(0x8C7FF3F4) else Color(0xFF3A4450), RoundedCornerShape(if (neon) 12.dp else 8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
