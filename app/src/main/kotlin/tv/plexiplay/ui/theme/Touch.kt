package tv.plexiplay.ui.theme

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Toucher au doigt ou à la souris. S'ajoute à côté des touches de télécommande existantes :
 * les deux commandes déclenchent la même action.
 */
fun Modifier.tap(onTap: () -> Unit): Modifier = composed {
    // Toujours la dernière action reçue : sans cela, le geste garde celle de la première composition
    // (un réglage à choix ne passait plus à la valeur suivante après le premier toucher).
    val action = rememberUpdatedState(onTap)
    pointerInput(Unit) { detectTapGestures(onTap = { action.value() }) }
}

/** Toucher court et appui long (par défaut 500 ms, délai du système). */
fun Modifier.tapOrHold(onTap: () -> Unit, onHold: () -> Unit): Modifier = composed {
    val tapAction = rememberUpdatedState(onTap)
    val holdAction = rememberUpdatedState(onHold)
    pointerInput(Unit) { detectTapGestures(onTap = { tapAction.value() }, onLongPress = { holdAction.value() }) }
}

/** Écran de type tablette : au moins 600 dp de large. */
@Composable
fun isTablet(): Boolean {
    val width = LocalConfiguration.current.screenWidthDp
    return remember(width) { width >= 600 }
}
