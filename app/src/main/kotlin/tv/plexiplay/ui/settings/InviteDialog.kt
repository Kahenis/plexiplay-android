package tv.plexiplay.ui.settings

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
import tv.plexiplay.plex.PlexInvite
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Panel
import tv.plexiplay.ui.theme.Paper
import tv.plexiplay.ui.theme.themedButton

private val Green = Color(0xFF5DD39E)

/** Invitations reçues : qui demande en ami, quel serveur partage. « Accepter » valide tout. */
@Composable
fun InviteDialog(invites: List<PlexInvite>, busy: Boolean, onAccept: () -> Unit, onLater: () -> Unit) {
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
                        Key.Back, Key.Escape -> if (!busy) onLater()
                        else -> Unit
                    }
                } else if (event.type == KeyEventType.KeyUp && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    if (!busy) {
                        if (choice == 0) onAccept() else onLater()
                    }
                }
                true
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(520.dp)
                .background(Panel, RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(10.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (invites.size > 1) "${invites.size} invitations Plex" else "Invitation Plex",
                color = Paper, fontSize = 19.sp, fontWeight = FontWeight.Bold,
            )
            invites.take(4).forEach { invite ->
                val who = if (invite.email.isNotBlank() && invite.email != invite.name) "${invite.name} (${invite.email})" else invite.name
                Column(Modifier.padding(top = 6.dp)) {
                    if (invite.friend) {
                        Text("$who vous demande en ami.", color = Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (invite.server) {
                        val server = invite.serverName.ifBlank { "son serveur" }
                        val libs = if (invite.libraries > 0) " (${invite.libraries} bibliothèque${if (invite.libraries > 1) "s" else ""})" else ""
                        Text(
                            (if (invite.friend) "" else "$who ") + "propose de partager ses bibliothèques : serveur « $server »$libs.",
                            color = Color(0xFFC5CDD6), fontSize = 13.sp,
                        )
                    }
                    if (!invite.friend && !invite.server) {
                        Text("$who vous a envoyé une invitation.", color = Paper, fontSize = 14.sp)
                    }
                }
            }
            if (invites.size > 4) Text("… et ${invites.size - 4} autre(s)", color = Color(0xFFC5CDD6), fontSize = 12.sp)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (busy) {
                    Text("Acceptation en cours…", color = Green, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    InviteButton("Accepter", choice == 0)
                    InviteButton("Plus tard", choice == 1)
                }
            }
        }
    }
}

@Composable
private fun InviteButton(label: String, selected: Boolean) {
    Text(
        label,
        color = if (selected) Ink else Paper,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .width(120.dp)
            .themedButton(if (selected) Accent else Color(0xFF2A333C), selected)
            .padding(vertical = 8.dp),
        textAlign = TextAlign.Center,
    )
}
