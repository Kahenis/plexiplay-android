package tv.plexiplay.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.Signal
import tv.plexiplay.plex.preroll.ClipKind

/** Message d'attente pendant que le serveur prépare un flux converti ; null pour la lecture directe, les prérolls et les bandes-annonces. */
internal fun waitMessage(clip: ClipPlay?): String? {
    if (clip == null || clip.kind != ClipKind.FEATURE || clip.direct) return null
    val uri = runCatching { android.net.Uri.parse(clip.url) }.getOrNull()
    return when {
        clip.info.contains("conversion complète") -> "Création du cache et du transcodage, veuillez patienter…"
        !uri?.getQueryParameter("videoResolution").isNullOrBlank() -> "Veuillez patienter pendant la mise en place de la nouvelle résolution…"
        else -> "Préparation du son, veuillez patienter…"
    }
}

/** Texte d'attente avec un indicateur qui tourne, au centre de l'image. */
@Composable
internal fun WaitOverlay(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color(0xCC000000), RoundedCornerShape(12.dp))
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(color = Signal, trackColor = Accent.copy(alpha = 0.25f), modifier = Modifier.size(44.dp), strokeWidth = 4.dp)
        Text(text, color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
    }
}
