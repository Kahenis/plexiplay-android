package tv.plexiplay.ui.player

import tv.plexiplay.ui.theme.tap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Repère de saut posé par le serveur Plex : [kind] « intro » ou « credits », en millisecondes. */
data class SkipMarker(val kind: String, val startMs: Long, val endMs: Long) {
    val label: String get() = if (kind == "intro") "Passer l'intro" else "Passer le générique"
    val doneNotice: String get() = if (kind == "intro") "Intro passée" else "Générique passé"
}

/** Repères demandés au serveur (une fois par film ou épisode) et sauts automatiques déjà faits. */
internal class SkipState {
    val requested = mutableSetOf<String>()
    val markers = mutableStateMapOf<String, List<SkipMarker>>()
    val autoDone = mutableSetOf<String>()
}

/** Le repère en cours à cette position (dernière seconde et demie exclue), s'il y en a un. */
internal fun activeMarker(list: List<SkipMarker>, positionMs: Long): SkipMarker? =
    list.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs - 1500L }

/** Où aller pour passer le repère : sa fin, ou la fin du fichier si le générique va jusqu'au bout. */
internal fun skipTarget(marker: SkipMarker, durationMs: Long): Long =
    if (durationMs > 0L && marker.endMs >= durationMs - 3000L) durationMs else marker.endMs

/** Bouton « Passer l'intro » / « Passer le générique » (OK pour sauter quand la barre est masquée). */
@Composable
internal fun SkipButton(marker: SkipMarker, raised: Boolean, modifier: Modifier = Modifier, onSkip: () -> Unit = {}) {
    // Mobile : le bouton se touche ; au-dessus de la barre de commandes (plus haute sur téléphone) quand elle est affichée.
    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 700
    Box(
        modifier
            .padding(end = if (narrow) 16.dp else 32.dp, bottom = if (raised) (if (narrow) 210.dp else 170.dp) else 40.dp)
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
            .tap { onSkip() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(marker.label, color = Color(0xFF111418), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
