package tv.plexiplay.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import tv.plexiplay.ui.theme.tap
import androidx.compose.ui.unit.sp

/** Une piste audio du film : son flux converti est prêt pour le cas où elle ne peut pas être choisie dans le flux en cours. */
data class AudioChoice(val id: Int, val label: String, val url: String, val current: Boolean = false, val language: String = "")

/** Code de langue sur deux lettres, en majuscules, pour le bouton FR/EN… ; vide si la langue est inconnue. */
internal fun langTag(code: String?): String {
    val value = code?.trim()?.lowercase()?.substringBefore('-').orEmpty()
    return if (value.length >= 2 && value != "und" && value != "zxx" && value.take(2).all { it.isLetter() }) value.take(2).uppercase() else ""
}

/** Un sous-titre du film (liste de Plex) : `url` est le fichier à charger quand il est externe et textuel. */
data class SubChoice(
    val id: Int,
    val label: String,
    val language: String,
    val forced: Boolean,
    val codec: String,
    val external: Boolean,
    val url: String = "",
)

/** Liste des pistes audio (appui long sur FR) ou des sous-titres (appui long sur ST). Toucher une ligne pour la choisir. */
@Composable
internal fun AudioMenu(
    labels: List<String>,
    selected: Int,
    current: Int,
    modifier: Modifier = Modifier,
    title: String = "Piste audio",
    onPick: (Int) -> Unit = {},
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(selected) {
        if (selected in labels.indices) listState.animateScrollToItem((selected - 3).coerceAtLeast(0))
    }
    Column(
        modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth(0.92f)
            .background(Color(0xF01C2128), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(10.dp))
            .tap { }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Touchez une ligne pour la choisir", color = Color(0xFFC5CDD6), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.heightIn(max = 300.dp),
        ) {
            items(labels.size) { i ->
                val on = i == selected
                Text(
                    labels[i] + if (i == current) "  ✓" else "",
                    color = if (on) Color(0xFF121418) else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (on) Color(0xFF3DBEBF) else Color(0xFF2A333C), RoundedCornerShape(6.dp))
                        .tap { onPick(i) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                )
            }
        }
    }
}
