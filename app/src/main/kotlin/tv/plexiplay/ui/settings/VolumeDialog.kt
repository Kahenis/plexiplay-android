package tv.plexiplay.ui.settings

import tv.plexiplay.ui.theme.tap

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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

/** Volumes proposés pour la musique de générique : de 0 à 60 % par paliers de 10 %. */
val ThemeVolumes = listOf(0, 10, 20, 30, 40, 50, 60)

/** Volumes proposés pour les prerolls : de 20 à 100 %, par paliers de 10 %, plus 75 % (valeur par défaut). */
val PrerollVolumes = listOf(20, 30, 40, 50, 60, 70, 75, 80, 90, 100)

/** Liste déroulante : haut/bas pour choisir, OK pour valider, Retour pour fermer sans changer. */
@Composable
fun VolumeDialog(
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    values: List<Int> = ThemeVolumes,
    title: String = "Volume du générique",
    subtitle: String = "Musique des fiches de saisons et d'épisodes",
    zeroLabel: Boolean = true,
) {
    var index by remember { mutableIntStateOf(values.indexOf(current).coerceAtLeast(0)) }
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
                            Key.DirectionDown -> index = (index + 1).coerceAtMost(values.lastIndex)
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    } else if (event.type == KeyEventType.KeyUp && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                        onPick(values[index])
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
                    // Téléphone en paysage : la liste des prerolls (10 valeurs) défile si elle ne tient pas.
                    .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - 32).dp)
                    .background(Panel, RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(10.dp))
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, color = Paper, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    color = Color(0xFFC5CDD6),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                values.forEachIndexed { i, value ->
                    val selected = i == index
                    Text(
                        if (value == 0 && zeroLabel) "0 % (muet)" else "$value %" + if (value == current) "  ✓" else "",
                        color = if (selected) Ink else Paper,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .themedButton(if (selected) Accent else Color(0xFF2A333C), selected)
                            .tap { onPick(value) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
