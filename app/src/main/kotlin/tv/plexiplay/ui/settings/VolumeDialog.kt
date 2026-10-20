package tv.plexiplay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper
import tv.plexiplay.ui.theme.Panel
import tv.plexiplay.ui.theme.themedButton

/** Volumes proposés pour la musique de générique : de 0 à 50 % par paliers de 10 %. */
val ThemeVolumes = listOf(0, 10, 20, 30, 40, 50)

/** Liste déroulante : haut/bas pour choisir, OK pour valider, Retour pour fermer sans changer. */
@Composable
fun VolumeDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var index by remember { mutableIntStateOf(ThemeVolumes.indexOf(current).coerceAtLeast(0)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionUp -> index = (index - 1).coerceAtLeast(0)
                            Key.DirectionDown -> index = (index + 1).coerceAtMost(ThemeVolumes.lastIndex)
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    } else if (event.type == KeyEventType.KeyUp && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                        onPick(ThemeVolumes[index])
                        true
                    } else {
                        false
                    }
                }
                .focusable(),
        ) {
            Column(
                Modifier
                    .width(300.dp)
                    .background(Panel, RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(10.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Volume du générique", color = Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Musique des fiches de saisons et d'épisodes",
                    color = Color(0xFFC5CDD6),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                ThemeVolumes.forEachIndexed { i, value ->
                    val selected = i == index
                    Text(
                        if (value == 0) "0 % (muet)" else "$value %" + if (value == current) "  ✓" else "",
                        color = if (selected) Ink else Paper,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .themedButton(if (selected) Accent else Color(0xFF2A333C), selected)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}
