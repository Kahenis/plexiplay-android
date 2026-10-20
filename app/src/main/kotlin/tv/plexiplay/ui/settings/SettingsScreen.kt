package tv.plexiplay.ui.settings

import tv.plexiplay.ui.theme.screenBackground
import tv.plexiplay.ui.theme.themedButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.plexiplay.player.Engine
import tv.plexiplay.player.PlaybackSettings
import tv.plexiplay.player.PlayerKind
import tv.plexiplay.plex.model.ServerEndpoint
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.FocusRing
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Panel
import tv.plexiplay.ui.theme.Paper
import tv.plexiplay.ui.theme.Signal
import tv.plexiplay.update.UpdateOffer

@Composable
fun SettingsScreen(
    settings: PlaybackSettings,
    servers: List<ServerEndpoint>,
    serverIndex: Int,
    onChange: (PlaybackSettings) -> Unit,
    onServer: (Int) -> Unit,
    onSignOut: () -> Unit,
    onLibraries: () -> Unit,
    versionName: String,
    updateStatus: String,
    updateProgress: Float = -1f,
    updateDetail: String = "",
    offer: UpdateOffer?,
    onCheckUpdate: () -> Unit,
    onInstall: () -> Unit,
    onCancelUpdate: () -> Unit,
    theme: String = "classic",
    onTheme: (String) -> Unit = {},
    themeVolume: Int = 20,
    onThemeVolume: (Int) -> Unit = {},
    inviteStatus: String = "",
    onCheckInvites: () -> Unit = {},
) {
    var volumeOpen by remember { mutableStateOf(false) }
    // Le focus part sur la première option : sans lui, Retour n'arrive pas à l'écran principal.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().screenBackground()) {
        if (volumeOpen) {
            VolumeDialog(
                current = themeVolume,
                onPick = {
                    onThemeVolume(it)
                    volumeOpen = false
                },
                onDismiss = { volumeOpen = false },
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item { Text("Paramètres", color = Paper, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold) }
            item {
                Hinted("Piste audio choisie automatiquement au lancement d'un film ou d'un épisode.") {
                    Cycle("Langue audio par défaut", settings.audioLanguage, listOf("original", "fr", "en", "de", "es"), firstFocus) {
                        onChange(settings.copy(audioLanguage = it))
                    }
                }
            }
            item {
                Hinted("Sous-titres choisis automatiquement (« off » = aucun).") {
                    Cycle("Sous-titres par défaut", settings.subtitleLanguage, listOf("off", "fr", "en", "de", "es")) {
                        onChange(settings.copy(subtitleLanguage = it))
                    }
                }
            }
            item {
                Hinted("Choisissez les bibliothèques visibles dans le menu de gauche, serveur par serveur.") {
                    Chip("Bibliothèques affichées", onClick = onLibraries)
                }
            }
            item {
                val playerName = if (settings.player == PlayerKind.MPV) "mpv" else "ExoPlayer"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    Cycle("Lecteur", playerName, listOf("ExoPlayer", "mpv")) {
                        onChange(settings.copy(player = if (it == "mpv") PlayerKind.MPV else PlayerKind.EXO))
                    }
                    Text(
                        "ExoPlayer est léger, mais il transcode certains codecs (délai avant la lecture). mpv est plus lourd, mais il les lit directement (lecture immédiate). ExoPlayer est en général suffisant.",
                        color = Color(0xFF9AA3AD),
                        fontSize = 10.5.sp,
                        lineHeight = 12.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                val labels = mapOf("auto" to "Automatique", "on" to "Activé", "off" to "Désactivé")
                Hinted("Si une vidéo bloque (écran noir), l'appli lit l'image en HDR10 et convertit le son. Automatique : elle bascule seule.") {
                    Cycle("Mode compatible", labels[settings.compatMode] ?: "Automatique", labels.values.toList()) {
                        val mode = labels.entries.firstOrNull { e -> e.value == it }?.key ?: "auto"
                        onChange(settings.copy(compatMode = mode, compat = mode == "on"))
                    }
                }
                Text(
                    tv.plexiplay.player.DeviceCodecs.summary(),
                    color = Color(0xFF9AA3AD),
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            if (settings.player != PlayerKind.MPV) {
                item {
                    Hinted("Corrige l'image étirée sur certaines TV. Désactivez-la si l'image est déjà correcte.") {
                        Cycle("Correction du format", if (settings.formatFix) "Activée" else "Désactivée", listOf("Activée", "Désactivée")) {
                            onChange(settings.copy(formatFix = it == "Activée"))
                        }
                    }
                }
            }
            item {
                Hinted("Apparence de l'application : classique, ou « Néon » avec boutons en relief.") {
                    Cycle("Thème", if (theme == "neon") "Néon" else "Classique", listOf("Classique", "Néon")) {
                        onTheme(if (it == "Néon") "neon" else "classic")
                    }
                }
            }
            item {
                Hinted("Musique de générique des fiches de saisons et d'épisodes (0 à 50 %).") {
                    Chip("Volume du générique : " + (if (themeVolume == 0) "muet" else "$themeVolume %")) { volumeOpen = true }
                }
            }
            item {
                Hinted("Qui prépare la vidéo : le serveur (SERVER) ou la TV elle-même (TV).") {
                    Cycle("Puissance", settings.engine.name, listOf(Engine.SERVER.name, Engine.TV.name)) {
                        onChange(settings.copy(engine = Engine.valueOf(it)))
                    }
                }
            }
            item {
                val serverName = servers.getOrNull(serverIndex)?.name?.ifBlank { null } ?: "Aucun"
                Hinted("Serveur Plex utilisé. Appuyez pour passer au suivant.") {
                    Chip("Serveur : $serverName") {
                        if (servers.size > 1) onServer((serverIndex + 1) % servers.size)
                    }
                }
            }
            item {
                Hinted("Vérifie si une nouvelle version de l'appli est disponible.") {
                    Chip(
                        if (updateStatus.isBlank()) "Mise à jour : $versionName" else updateStatus,
                        tint = if (updateStatus.startsWith("Téléchargement")) Color(0xFF5DD39E) else if (updateStatus.isBlank()) Color(0xFFE39A3C) else null,
                        onClick = onCheckUpdate,
                    )
                }
            }
            if (updateProgress >= 0f) {
                item {
                    Column {
                        tv.plexiplay.ui.shell.ThinBar(updateProgress, Modifier.width(220.dp))
                        if (updateDetail.isNotBlank()) Text(updateDetail, color = Paper, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (offer != null) {
                item { Text("La version ${offer.version} est disponible.", color = Paper, fontSize = 11.sp) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Installer", primary = true, onClick = onInstall)
                        Chip("Annuler", onClick = onCancelUpdate)
                    }
                }
            }
            item {
                Hinted("Retire votre compte Plex de cet appareil.") {
                    Chip("Se déconnecter", onClick = onSignOut)
                }
            }
            item {
                Hinted("Cherche une demande d'ami ou un partage de bibliothèques envoyé à votre compte Plex.") {
                    Chip(
                        if (inviteStatus.isBlank()) "Vérifier les invitations" else inviteStatus,
                        tint = if (inviteStatus.startsWith("Recherche") || inviteStatus.startsWith("Acceptation")) Color(0xFF5DD39E) else null,
                        onClick = onCheckInvites,
                    )
                }
            }
        }
        Text(
            "Plexiplay $versionName    Conçu et développé par Jean-Francois Guilard",
            color = Color(0xFF9AA3AD),
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().background(Ink).padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Option suivie d'une explication d'une ligne. */
@Composable
private fun Hinted(hint: String, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        Text(
            hint,
            color = Color(0xFF9AA3AD),
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Cycle(label: String, value: String, options: List<String>, requester: FocusRequester? = null, onPick: (String) -> Unit) {
    Chip("$label : $value", requester = requester) {
        if (options.isEmpty()) return@Chip
        val next = options[(options.indexOf(value) + 1).mod(options.size)]
        onPick(next)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(label: String, primary: Boolean = false, requester: FocusRequester? = null, tint: Color? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val fill = when {
        primary -> Signal
        focused -> Accent
        else -> Panel
    }
    Text(
        label,
        color = if (primary || focused) Ink else (tint ?: Paper),
        fontSize = 11.sp,
        modifier = Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .bringIntoViewRequester(bring)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) scope.launch { bring.bringIntoView() }
            }
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .themedButton(fill, focused)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
