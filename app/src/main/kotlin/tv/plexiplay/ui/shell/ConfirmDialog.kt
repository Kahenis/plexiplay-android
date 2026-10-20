package tv.plexiplay.ui.shell

import tv.plexiplay.ui.theme.themedButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper
import tv.plexiplay.ui.theme.Panel

/** Question « Oui / Non » plein écran. « Non » est sélectionné au départ ; Retour = Non. */
@Composable
fun ConfirmDialog(question: String, onYes: () -> Unit, onNo: () -> Unit) {
    var choice by remember { mutableIntStateOf(1) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> choice = 0
                        Key.DirectionRight -> choice = 1
                        Key.Back, Key.Escape -> onNo()
                        else -> Unit
                    }
                } else if (event.type == KeyEventType.KeyUp && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    if (choice == 0) onYes() else onNo()
                }
                true
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(380.dp)
                .background(Panel, RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(10.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Êtes-vous sûr ?", color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(question, color = Color(0xFFC5CDD6), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ConfirmButton("Oui", choice == 0)
                ConfirmButton("Non", choice == 1)
            }
        }
    }
}

@Composable
private fun ConfirmButton(label: String, selected: Boolean) {
    Text(
        label,
        color = if (selected) Ink else Paper,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .width(110.dp)
            .themedButton(if (selected) Accent else Color(0xFF2A333C), selected)
            .padding(vertical = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
