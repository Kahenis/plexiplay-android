package tv.plexiplay.ui.shell

import tv.plexiplay.ui.theme.themedButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.plexiplay.R
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper
import tv.plexiplay.ui.theme.Panel

private val Green = Color(0xFF5DD39E)

/**
 * Proposition de mise à jour au démarrage : logo à gauche, texte à droite, « Installer » / « Plus tard ».
 * « Installer » est sélectionné au départ ; Retour = Plus tard.
 */
@Composable
fun UpdateDialog(current: String, offered: String, downloading: Boolean, progress: Float = -1f, detail: String = "", onInstall: () -> Unit, onLater: () -> Unit) {
    var choice by remember { mutableIntStateOf(0) }
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
                        Key.Back, Key.Escape -> if (!downloading) onLater()
                        else -> Unit
                    }
                } else if (event.type == KeyEventType.KeyUp && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    if (!downloading) {
                        if (choice == 0) onInstall() else onLater()
                    }
                }
                true
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .width(470.dp)
                .background(Panel, RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(10.dp))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)),
            )
            Column(Modifier.padding(start = 18.dp)) {
                Text("Nouvelle version disponible", color = Paper, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Plexiplay $offered est disponible (vous avez $current).",
                    color = Color(0xFFC5CDD6),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                )
                if (downloading) {
                    Text("Téléchargement…", color = Green, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    ThinBar(progress, Modifier.width(250.dp).padding(top = 8.dp))
                    if (detail.isNotBlank()) Text(detail, color = Color(0xFFC5CDD6), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        UpdateButton("Installer", choice == 0)
                        UpdateButton("Plus tard", choice == 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateButton(label: String, selected: Boolean) {
    Text(
        label,
        color = if (selected) Ink else Paper,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .width(110.dp)
            .themedButton(if (selected) Accent else Color(0xFF2A333C), selected)
            .padding(vertical = 8.dp),
        textAlign = TextAlign.Center,
    )
}

/** Barre de progression fine et discrète (vert), masquée si la progression est inconnue (< 0). */
@Composable
fun ThinBar(progress: Float, modifier: Modifier = Modifier) {
    if (progress < 0f) return
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2A333C))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Green))
    }
}
