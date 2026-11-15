package tv.plexiplay.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper

/** Clavier virtuel partagé (Recherche, Écrire un avis) : lettres, chiffres, et la ponctuation courante d'une phrase. */
val KEYBOARD_ROWS = listOf("ABCDEFGHI", "JKLMNOPQR", "STUVWXYZ", "0123456789", ".,!?'-")

/** Largeur de touche adaptée à la longueur de la rangée, pour que toutes restent dans la même largeur de colonne. */
fun keyWidth(row: String): Dp = if (row.length > 9) 30.dp else 34.dp

/** Touche d'un clavier virtuel, focusable au D-pad (navigation automatique de Compose selon la disposition). */
@Composable
fun KeyButton(label: String, width: Dp, requester: FocusRequester? = null, onPress: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (focused) Ink else Paper,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onPress()
                    true
                } else false
            }
            .focusable()
            .size(width, 34.dp)
            .border(2.dp, if (focused) tv.plexiplay.ui.theme.FocusRing else Color.Transparent, RoundedCornerShape(6.dp))
            .background(if (focused) Accent else Color(0xFF2A333C), RoundedCornerShape(6.dp))
            .padding(top = 6.dp),
    )
}
