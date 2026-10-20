package tv.plexiplay.ui.theme

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Toucher au doigt ou à la souris. S'ajoute à côté des touches de télécommande existantes :
 * les deux commandes déclenchent la même action.
 */
fun Modifier.tap(onTap: () -> Unit): Modifier = pointerInput(Unit) {
    detectTapGestures(onTap = { onTap() })
}

/** Toucher court et appui long (par défaut 500 ms, délai du système). */
fun Modifier.tapOrHold(onTap: () -> Unit, onHold: () -> Unit): Modifier = pointerInput(Unit) {
    detectTapGestures(onTap = { onTap() }, onLongPress = { onHold() })
}

/** Écran de type tablette : au moins 600 dp de large. */
@Composable
fun isTablet(): Boolean {
    val width = LocalConfiguration.current.screenWidthDp
    return remember(width) { width >= 600 }
}
