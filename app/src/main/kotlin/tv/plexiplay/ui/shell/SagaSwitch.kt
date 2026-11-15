package tv.plexiplay.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.FocusRing
import tv.plexiplay.ui.theme.Paper

/**
 * Interrupteur « Sagas », à gauche de l'heure : le bouton rond glisse d'un cran. Allumé, l'affiche de chaque saga remplace ses films ;
 * éteint, tous les films sont affichés un par un. Se manie avec OK, comme les autres boutons.
 */
@Composable
fun SagaSwitch(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val knob by animateDpAsState(if (on) 21.dp else 3.dp, tween(160), label = "saga-knob")
    val track by animateColorAsState(if (on) Accent else Color(0xFF4A5560), tween(160), label = "saga-track")
    val frame = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .background(Color(0xCC121418), frame)
            .border(if (focused) 2.dp else 1.dp, if (focused) FocusRing else Color(0xFF3A4450), frame)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sagas", color = Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier
                .size(width = 42.dp, height = 24.dp)
                .background(track, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(12.dp)),
        ) {
            Box(
                Modifier
                    .padding(vertical = 3.dp)
                    .offset(x = knob)
                    .size(18.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}
