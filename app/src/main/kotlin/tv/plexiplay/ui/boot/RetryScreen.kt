package tv.plexiplay.ui.boot

import tv.plexiplay.ui.theme.tap
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper
import tv.plexiplay.ui.theme.Panel
import tv.plexiplay.ui.theme.themedButton

/** Écran affiché quand l'accueil n'a pas pu être chargé : explication simple, « Réessayer » ou « Se reconnecter ». */
@Composable
fun RetryScreen(message: String, onRetry: () -> Unit, onSignIn: () -> Unit) {
    var choice by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> choice = 0
                        Key.DirectionRight -> choice = 1
                        else -> Unit
                    }
                } else if (event.type == KeyEventType.KeyUp && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    if (choice == 0) onRetry() else onSignIn()
                }
                event.key != Key.Back && event.key != Key.Escape
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .padding(horizontal = 16.dp)
                .background(Panel, RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(10.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Plexiplay", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("Chargement impossible", color = Paper, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Text(
                message,
                color = Color(0xFFC5CDD6),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 22.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RetryButton("Réessayer", choice == 0) { onRetry() }
                RetryButton("Se reconnecter", choice == 1) { onSignIn() }
            }
        }
    }
}

@Composable
private fun RetryButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Ink else Paper,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .width(150.dp)
            .tap { onClick() }
            .themedButton(if (selected) Accent else Color(0xFF2A333C), selected)
            .padding(vertical = 9.dp),
        textAlign = TextAlign.Center,
    )
}
