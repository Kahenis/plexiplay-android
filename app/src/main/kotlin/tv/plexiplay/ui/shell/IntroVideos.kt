package tv.plexiplay.ui.shell

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.delay
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.FocusRing
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper
import tv.plexiplay.ui.theme.Signal

private const val INTRO_BASE = "https://guilard.com/intros/"

/** Lit la page du dossier et en extrait les fichiers .mp4, dans l'ordre alphabétique. Vide si le dossier est inaccessible. */
private fun fetchIntroNames(): List<String> = runCatching {
    val conn = (URL(INTRO_BASE).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
    }
    val html = conn.inputStream.bufferedReader().use { it.readText() }
    Regex("""href="([^"?#]+\.mp4)"""", RegexOption.IGNORE_CASE).findAll(html)
        .map { Uri.decode(it.groupValues[1].substringAfterLast('/')) }
        .distinct()
        .sortedBy { it.lowercase() }
        .toList()
}.getOrDefault(emptyList())

@Composable
private fun IntroButton(label: String, requester: FocusRequester? = null, primary: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (primary || focused) Ink else Paper,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .border(2.dp, if (focused) FocusRing else Color.Transparent, RoundedCornerShape(8.dp))
            .background(if (primary) Signal else if (focused) Accent else Color(0xFF2A333C), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
    )
}

/** Proposition faite après l'animation : voir les vidéos d'intros et d'humour. */
@Composable
fun IntroOffer(onYes: () -> Unit, onNo: () -> Unit) {
    val yes = remember { FocusRequester() }
    LaunchedEffect(Unit) { yes.requestFocus() }
    Dialog(onDismissRequest = onNo, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(620.dp)
                .background(Ink, RoundedCornerShape(24.dp))
                .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                "Pour vos efforts, voulez-vous découvrir toutes nos vidéos d'intros et d'humour ?",
                color = Paper, fontSize = 22.sp, textAlign = TextAlign.Center,
            )
            Text("(Arrêtable à tout moment)", color = Color(0xFF9AA3AD), fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                IntroButton("Oui", yes, primary = true, onClick = onYes)
                IntroButton("Non", onClick = onNo)
            }
        }
    }
}

/** Fenêtre à 80 % de l'écran qui enchaîne les vidéos du dossier guilard.com/intros. */
@Composable
fun IntroPlayer(onClose: () -> Unit) {
    val context = LocalContext.current
    var names by remember { mutableStateOf<List<String>?>(null) }
    var index by remember { mutableStateOf(0) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fetchIntroNames() }
        names = list
        if (list.isEmpty()) {
            delay(2500)
            onClose()
        }
    }
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    // Fin d'une vidéo (ou erreur) : décompte puis passage à la suivante.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) countdown = 3
            }
            override fun onPlayerError(error: PlaybackException) {
                countdown = 3
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val list = names.orEmpty()
    fun next() {
        countdown = null
        if (index + 1 >= list.size) onClose() else index += 1
    }
    LaunchedEffect(names, index) {
        if (list.isEmpty()) return@LaunchedEffect
        paused = false
        player.setMediaItem(MediaItem.fromUri(INTRO_BASE + Uri.encode(list[index])))
        player.prepare()
        player.playWhenReady = true
    }
    LaunchedEffect(countdown) {
        var c = countdown ?: return@LaunchedEffect
        while (c > 0) {
            delay(1000)
            c -= 1
            countdown = c
        }
        next()
    }
    val root = remember { FocusRequester() }
    val nextButton = remember { FocusRequester() }
    LaunchedEffect(countdown == null, names) {
        if (countdown == null) runCatching { root.requestFocus() } else runCatching { nextButton.requestFocus() }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .focusRequester(root)
                .focusable()
                .onPreviewKeyEvent {
                    if (countdown == null && it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                        paused = !paused
                        player.playWhenReady = !paused
                        true
                    } else false
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .fillMaxHeight(0.8f)
                    .background(Color.Black, RoundedCornerShape(16.dp))
                    .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (list.isNotEmpty()) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                                isFocusable = false
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        "${list[index].removeSuffix(".mp4")}  (${index + 1}/${list.size})" + if (paused) "  ·  pause" else "",
                        color = Paper, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                    )
                } else {
                    Text(
                        if (names == null) "Chargement…" else "Vidéos indisponibles pour le moment.",
                        color = Paper, fontSize = 20.sp,
                    )
                }
                val c = countdown
                if (c != null) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            if (index + 1 >= list.size) "Fin des vidéos" else "Vidéo suivante dans $c…",
                            color = Paper, fontSize = 22.sp,
                            modifier = Modifier.background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            IntroButton("Suivant", nextButton, primary = true, onClick = { next() })
                            IntroButton("Arrêter", onClick = onClose)
                        }
                    }
                }
            }
        }
    }
}
