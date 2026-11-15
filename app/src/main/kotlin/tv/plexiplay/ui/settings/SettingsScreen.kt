package tv.plexiplay.ui.settings
import tv.plexiplay.ui.theme.tap
import tv.plexiplay.ui.theme.tapOrHold

import tv.plexiplay.ui.theme.screenBackground
import tv.plexiplay.ui.theme.themedButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    prerollVolume: Int = 75,
    accountName: String = "",
    prerollPauseUntil: Long = 0L,
    onPrerollPause: () -> Unit = {},
    onPrerollResume: () -> Unit = {},
    onPrerollVolume: (Int) -> Unit = {},
    inviteStatus: String = "",
    onCheckInvites: () -> Unit = {},
    prerollTexture: Boolean = false,
    onPrerollTexture: (Boolean) -> Unit = {},
) {
    var volumeOpen by remember { mutableStateOf(false) }
    var prerollOpen by remember { mutableStateOf(false) }
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
        if (prerollOpen) {
            VolumeDialog(
                current = prerollVolume,
                onPick = {
                    onPrerollVolume(it)
                    prerollOpen = false
                },
                onDismiss = { prerollOpen = false },
                values = PrerollVolumes,
                title = "Volume des prerolls",
                subtitle = "Annonces avant le film, par rapport au film",
                zeroLabel = false,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // À droite du titre : les manuels (en retrait, pour laisser la place à l'heure en haut à droite).
                Row(Modifier.fillMaxWidth().padding(end = 100.dp), verticalAlignment = Alignment.Bottom) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Text("Paramètres", color = Paper, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                        if (accountName.isNotBlank()) {
                            Text(
                                " - $accountName",
                                color = Color(0xFF9AA3AD),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 1.dp),
                            )
                        }
                    }
                }
            }
            item {
                Group("RÉGLAGES PRINCIPAUX") {
                    Hinted("Piste audio choisie automatiquement au lancement d'un film ou d'un épisode.") {
                        Cycle("Langue audio par défaut", settings.audioLanguage, listOf("original", "fr", "en", "de", "es"), firstFocus) {
                            onChange(settings.copy(audioLanguage = it))
                        }
                    }
                    Hinted("Sous-titres choisis automatiquement (« off » = aucun).") {
                        Cycle("Sous-titres par défaut", settings.subtitleLanguage, listOf("off", "fr", "en", "de", "es")) {
                            onChange(settings.copy(subtitleLanguage = it))
                        }
                    }
                    val playerName = if (settings.player == PlayerKind.MPV) "mpv" else "ExoPlayer"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                    val serverName = servers.getOrNull(serverIndex)?.name?.ifBlank { null } ?: "Aucun"
                    Hinted("Serveur Plex utilisé. Appuyez pour passer au suivant.") {
                        Chip("Serveur : $serverName") {
                            if (servers.size > 1) onServer((serverIndex + 1) % servers.size)
                        }
                    }
                }
            }
            item {
                Group("AMÉLIORATIONS") {
                    Hinted("Choisissez les bibliothèques visibles dans le menu, serveur par serveur.") {
                        Chip("Bibliothèques affichées", onClick = onLibraries)
                    }
                    val labels = mapOf("auto" to "Automatique", "on" to "Activé", "off" to "Désactivé")
                    Hinted("Si une vidéo bloque (écran noir), lecture en HDR10 avec conversion du son. Automatique : bascule seule. Évite aussi, sur certaines TV, les prérolls abîmés après un film Dolby Vision.") {
                        Cycle("Mode compatible", labels[settings.compatMode] ?: "Automatique", labels.values.toList()) {
                            val mode = labels.entries.firstOrNull { e -> e.value == it }?.key ?: "auto"
                            onChange(settings.copy(compatMode = mode, compat = mode == "on"))
                        }
                    }
                    val boosts = mapOf(100 to "Normal", 150 to "Renforcé", 200 to "Fort")
                    Hinted("Gain demandé au serveur quand il convertit le son. À augmenter si le volume est trop faible en conversion.") {
                        Cycle("Volume du son converti", boosts[settings.audioBoost] ?: "Normal", boosts.values.toList()) {
                            val value = boosts.entries.firstOrNull { e -> e.value == it }?.key ?: 100
                            onChange(settings.copy(audioBoost = value))
                        }
                    }
                    Text(
                        "Codecs intégrés à l'appareil : " + tv.plexiplay.player.DeviceCodecs.summary(),
                        color = Color(0xFF9AA3AD),
                        fontSize = 10.5.sp,
                    )
                    if (settings.player != PlayerKind.MPV) {
                        Hinted("Corrige l'image étirée sur certains écrans. Désactivez-la si l'image est déjà correcte.") {
                            Cycle("Correction du format", if (settings.formatFix) "Activée" else "Désactivée", listOf("Activée", "Désactivée")) {
                                onChange(settings.copy(formatFix = it == "Activée"))
                            }
                        }
                    }
                }
            }
            item {
                Group("CONFORT") {
                    // Mobile : pas de thème « Futuriste » (effets pensés pour la télécommande).
                    Hinted("Apparence de l'application : classique, ou « Néon » avec boutons en relief.") {
                        Cycle(
                            "Thème",
                            if (theme == "neon") "Néon" else "Classique",
                            listOf("Classique", "Néon"),
                        ) {
                            onTheme(if (it == "Néon") "neon" else "classic")
                        }
                    }
                    Hinted("Musique de générique des fiches de saisons et d'épisodes (0 à 60 %).") {
                        Chip("Volume du générique : " + (if (themeVolume == 0) "muet" else "$themeVolume %")) { volumeOpen = true }
                    }
                    Hinted(
                        if (settings.prerollLocked) {
                            "Dossier des prérolls introuvable : les prérolls du serveur Plex sont utilisés."
                        } else {
                            "Séquentiel externe : un préroll différent à chaque film, dans l'ordre, puis retour au premier. Aléatoire serveur : choisis par le serveur Plex."
                        },
                    ) {
                        Cycle(
                            "Prérolls",
                            if (settings.prerollMode == "random") "Aléatoire serveur" else "Séquentiel externe",
                            listOf("Séquentiel externe", "Aléatoire serveur"),
                            dimmed = settings.prerollLocked,
                            // Appui long sur le réglage grisé : le réactive (fonction volontairement non documentée).
                            onLongPress = if (settings.prerollLocked) {
                                { onChange(settings.copy(prerollMode = "sequential", prerollLocked = false)) }
                            } else null,
                        ) {
                            onChange(settings.copy(prerollMode = if (it == "Aléatoire serveur") "random" else "sequential"))
                        }
                    }
                    Hinted("Volume des annonces avant le film (20 à 100 %). Le film garde son volume normal.") {
                        Chip("Volume des prerolls : $prerollVolume %") { prerollOpen = true }
                    }
                    val paused = prerollPauseUntil > System.currentTimeMillis()
                    val startFocus = remember { FocusRequester() }
                    val cancelFocus = remember { FocusRequester() }
                    var first by remember { mutableStateOf(true) }
                    LaunchedEffect(paused) {
                        if (first) { first = false; return@LaunchedEffect }
                        delay(60)
                        runCatching { if (paused) cancelFocus.requestFocus() else startFocus.requestFocus() }
                    }
                    if (paused) {
                        val until = java.text.SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", java.util.Locale.FRANCE).format(java.util.Date(prerollPauseUntil))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Prerolls désactivés, remis en place le $until", color = Paper, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
                            Chip("Annuler", requester = cancelFocus) { onPrerollResume() }
                        }
                    } else {
                        Hinted("Plus aucune annonce avant les films pendant 7 jours. Les bandes-annonces et le film ne changent pas.") {
                            Chip("Désactiver les prerolls pendant 7 jours", requester = startFocus) { onPrerollPause() }
                        }
                    }
                }
            }
            item {
                Group("PRATIQUE") {
                    val current = "V" + versionName.trimStart('v', 'V')
                    Hinted("Vérifie si une nouvelle version de l'appli est disponible.") {
                        Chip("Recherche mise à jour", onClick = onCheckUpdate)
                    }
                    // Téléphone : la ligne d'état passe à la ligne si elle ne tient pas.
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (updateProgress >= 0f) {
                            tv.plexiplay.ui.shell.ThinBar(updateProgress, Modifier.width(220.dp).align(Alignment.CenterVertically))
                            if (updateDetail.isNotBlank()) Text(updateDetail, color = Paper, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
                            if (offer != null) Chip("Annuler", onClick = onCancelUpdate)
                        } else if (offer != null) {
                            Text("Version proposée : V" + offer.version.trimStart('v', 'V') + " ($current actuelle)", color = Color(0xFF5DD39E), fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
                            Chip("Mise à jour", primary = true, onClick = onInstall)
                            Chip("Annuler", onClick = onCancelUpdate)
                        } else if (updateStatus.isNotBlank()) {
                            Text(updateStatus, color = Color(0xFFE39A3C), fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
                        } else {
                            Text("Version actuelle $current", color = Color(0xFF9AA3AD), fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
                        }
                    }
                    Hinted("Cherche une demande d'ami ou un partage de bibliothèques envoyé à votre compte Plex.") {
                        Chip(
                            if (inviteStatus.isBlank()) "Vérifier les invitations" else inviteStatus,
                            tint = if (inviteStatus.startsWith("Recherche") || inviteStatus.startsWith("Acceptation")) Color(0xFF5DD39E) else null,
                            onClick = onCheckInvites,
                        )
                    }
                    Hinted("Retire votre compte Plex de cet appareil.") {
                        Chip("Se déconnecter", onClick = onSignOut)
                    }
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

/** Couleur des boutons au repos : plus claire que le fond des cadres pour qu'ils restent visibles sans focus. */
private val IdleButton: Color
    get() = if (tv.plexiplay.ui.theme.ThemeMode.neon) Color(0xFF2F3A78) else Color(0xFF3A4552)

/** Cadre d'un groupe de réglages : titre en capitales avec une barre de couleur, fond léger et bordure arrondie. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
            Box(Modifier.width(3.dp).height(13.dp).background(Signal, RoundedCornerShape(2.dp)))
            Text(title, color = Color(0xFFC9D1DA), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 8.dp))
        }
        content()
    }
}

/** Option suivie d'une explication d'une ligne. */
@Composable
private fun Hinted(hint: String, content: @Composable () -> Unit) {
    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 700
    if (narrow) {
        Column(Modifier.padding(vertical = 4.dp)) {
            content()
            Text(
                hint,
                color = Color(0xFF9AA3AD),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            // Jusqu'à deux lignes, puis « … ».
            Text(
                hint,
                color = Color(0xFF9AA3AD),
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Cycle(
    label: String,
    value: String,
    options: List<String>,
    requester: FocusRequester? = null,
    dimmed: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onPick: (String) -> Unit,
) {
    Chip("$label : $value", requester = requester, dimmed = dimmed, onLongPress = onLongPress) {
        if (options.isEmpty() || dimmed) return@Chip
        val next = options[(options.indexOf(value) + 1).mod(options.size)]
        onPick(next)
    }
}

@OptIn(ExperimentalFoundationApi::class)
/** Petit bouton discret (manuels) : sobre au repos, aux couleurs du thème quand il a le focus. */
@Composable
private fun Chip(label: String, primary: Boolean = false, requester: FocusRequester? = null, tint: Color? = null, dimmed: Boolean = false, onLongPress: (() -> Unit)? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var longDone by remember { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val fill = when {
        dimmed -> IdleButton
        primary -> Signal
        focused -> Accent
        else -> IdleButton
    }
    Text(
        label,
        color = if (dimmed) Color(0xFF6B7480) else if (primary || focused) Ink else (tint ?: Paper),
        fontSize = 14.sp,
        modifier = Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .bringIntoViewRequester(bring)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) scope.launch { bring.bringIntoView() }
            }
            .onKeyEvent {
                val ok = it.key == Key.DirectionCenter || it.key == Key.Enter
                if (!ok) {
                    false
                } else if (it.type == KeyEventType.KeyDown) {
                    // Appui long (si le bouton en prévoit un) : déclenché une seule fois, le relâchement ne valide rien.
                    if (onLongPress != null) {
                        if (it.nativeKeyEvent.isLongPress && !longDone) {
                            longDone = true
                            onLongPress()
                        }
                        true
                    } else false
                } else if (it.type == KeyEventType.KeyUp) {
                    if (longDone) {
                        longDone = false
                    } else {
                        onClick()
                    }
                    true
                } else false
            }
            .then(if (onLongPress != null) Modifier.tapOrHold(onTap = onClick, onHold = onLongPress) else Modifier.tap { onClick() })
            .focusable()
            .themedButton(fill, focused)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}
