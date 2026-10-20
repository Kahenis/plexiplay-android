package tv.plexiplay.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yuroyami.libmpvkt.HwdecMode
import io.github.yuroyami.libmpvkt.LoadFileMode
import io.github.yuroyami.libmpvkt.MpvCommands
import io.github.yuroyami.libmpvkt.MpvProperties
import io.github.yuroyami.libmpvkt.MpvTrack
import io.github.yuroyami.libmpvkt.SeekMode
import io.github.yuroyami.libmpvkt.SeekPrecision
import io.github.yuroyami.libmpvkt.SubAddMode
import io.github.yuroyami.libmpvkt.TrackSelection
import io.github.yuroyami.libmpvkt.TrackType
import io.github.yuroyami.libmpvkt.getOrNull
import io.github.yuroyami.libmpvkt.view.MpvOptions
import io.github.yuroyami.libmpvkt.view.MpvView
import io.github.yuroyami.libmpvkt.view.SurfaceType
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.plexiplay.plex.preroll.ClipKind

private fun sameLanguage(track: String?, wanted: String): Boolean {
    if (wanted.isBlank()) return false
    val value = track?.lowercase()?.substringBefore('-').orEmpty()
    if (value.isBlank()) return false
    return value == wanted || value.take(2) == wanted.take(2)
}

private fun applyMpvChoice(
    view: MpvView,
    clip: ClipPlay,
    tracks: List<MpvTrack>,
    position: Int,
    added: MutableSet<Int>,
) {
    if (!clip.direct) return
    val core = view.mpv ?: return
    if (clip.audioLanguage.isNotBlank()) {
        val audio = tracks.filter { it.type == TrackType.Audio }
        val match = audio.firstOrNull { sameLanguage(it.lang, clip.audioLanguage) && it.isDefault }
            ?: audio.firstOrNull { sameLanguage(it.lang, clip.audioLanguage) }
        if (match != null) core.command(MpvCommands.set(MpvProperties.Aid, TrackSelection.Id(match.id)))
    }
    if (clip.subtitleUrl.isNotBlank()) {
        if (added.add(position)) {
            core.command(
                MpvCommands.subAdd(
                    clip.subtitleUrl,
                    SubAddMode.Select,
                    clip.textLanguage.ifBlank { "und" },
                    clip.textLanguage.ifBlank { "und" },
                ),
            )
        }
        return
    }
    if (clip.textMode == "off") {
        core.command(MpvCommands.set(MpvProperties.Sid, TrackSelection.No))
        return
    }
    val subs = tracks.filter { it.type == TrackType.Sub }
    val wantedForced = clip.textMode == "forced"
    val match = subs.firstOrNull {
        sameLanguage(it.lang, clip.textLanguage) &&
            it.isForced == wantedForced &&
            (clip.subtitleCodec.isBlank() || it.codec.equals(clip.subtitleCodec, true))
    } ?: subs.firstOrNull { sameLanguage(it.lang, clip.textLanguage) && it.isForced == wantedForced }
    if (match != null) core.command(MpvCommands.set(MpvProperties.Sid, TrackSelection.Id(match.id)))
    else core.command(MpvCommands.set(MpvProperties.Sid, TrackSelection.No))
}

@Composable
internal fun MpvPlayerScreen(
    clips: List<ClipPlay>,
    mode: String,
    onProgress: (ratingKey: String, state: String, position: Long, duration: Long) -> Unit,
    onExit: () -> Unit,
    onRegisterBack: ((() -> Boolean)?) -> Unit,
    onAudioChosen: (ClipPlay) -> Unit = {},
    onSubtitleChosen: suspend (ClipPlay, Int) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf("") }
    var audioMenu by remember { mutableStateOf(false) }
    var subMenu by remember { mutableStateOf(false) }
    var menuIndex by remember { mutableIntStateOf(0) }
    val pickedAudio = remember { mutableStateMapOf<Int, Int>() }
    val pickedSub = remember { mutableMapOf<Int, Int>() }
    LaunchedEffect(notice) {
        if (notice.isNotEmpty()) {
            delay(4000)
            notice = ""
        }
    }
    var infoVisible by remember { mutableStateOf(false) }
    val shown = remember { mutableStateOf(false) }
    var pulse by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var pictureWidth by remember { mutableIntStateOf(0) }
    var pictureHeight by remember { mutableIntStateOf(0) }
    var bitsPerSecond by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var mpvStats by remember { mutableStateOf("") }
    var index by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    var swallowOkUp by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf<MpvView?>(null) }
    DisposableEffect(Unit) {
        onRegisterBack {
            if (shown.value) {
                shown.value = false
                true
            } else false
        }
        onDispose { onRegisterBack(null) }
    }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(screen) {
        val view = screen ?: return@LaunchedEffect
        val added = mutableSetOf<Int>()
        val resumed = mutableSetOf<Int>()
        var signature = ""
        while (true) {
            val core = view.mpv
            val pos = core?.get(MpvProperties.PlaylistPos)?.getOrNull()?.toInt() ?: 0
            val tracks = core?.get(MpvProperties.TrackList)?.getOrNull().orEmpty()
            val clip = clips.getOrNull(pos)
            val sign = "$pos:${tracks.size}:${tracks.firstOrNull()?.id}:${clip?.subtitleUrl}"
            if (clip != null && tracks.isNotEmpty() && sign != signature) {
                applyMpvChoice(view, clip, tracks, pos, added)
                if (clip.startMs > 0L && resumed.add(pos)) {
                    view.seek(clip.startMs / 1000.0, SeekMode.Absolute, SeekPrecision.Keyframes)
                }
                signature = sign
            }
            delay(250)
        }
    }
    // Rapport régulier au serveur, même quand les commandes sont masquées.
    var lastKey by remember { mutableStateOf("") }
    var lastPosition by remember { mutableStateOf(0L) }
    var lastDuration by remember { mutableStateOf(0L) }
    LaunchedEffect(screen) {
        val view = screen ?: return@LaunchedEffect
        while (true) {
            val core = view.mpv
            if (core != null) {
                val pos = core[MpvProperties.PlaylistPos].getOrNull()?.toInt() ?: 0
                val clip = clips.getOrNull(pos)
                // Fin de lecture complète d'un film : retour à l'écran d'où il a été lancé.
                val ended = core.getString("eof-reached") == "yes" || core.getString("idle-active") == "yes"
                if (ended && lastKey.isNotEmpty() && lastDuration > 0L && lastPosition >= lastDuration * 9 / 10 &&
                    clips.any { it.kind == ClipKind.FEATURE && it.closeOnEnd && it.ratingKey == lastKey }
                ) {
                    onExit()
                    return@LaunchedEffect
                }
                if (clip != null && clip.kind == ClipKind.FEATURE) {
                    val timePos = ((core[MpvProperties.TimePos].getOrNull() ?: 0.0) * 1000).toLong()
                    val total = ((core[MpvProperties.Duration].getOrNull() ?: 0.0) * 1000).toLong()
                    val paused = core[MpvProperties.Pause].getOrNull() == true
                    if (total > 0L) {
                        lastKey = clip.ratingKey
                        lastPosition = timePos
                        lastDuration = total
                        onProgress(clip.ratingKey, if (paused) "paused" else "playing", timePos, total)
                    }
                }
            }
            delay(1000)
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (lastKey.isNotEmpty()) onProgress(lastKey, "stopped", lastPosition, lastDuration)
        }
    }
    LaunchedEffect(shown.value) {
        if (shown.value) runCatching { playFocus.requestFocus() } else runCatching { rootFocus.requestFocus() }
    }
    LaunchedEffect(shown.value, pulse) {
        if (!shown.value) return@LaunchedEffect
        delay(6000)
        shown.value = false
    }
    LaunchedEffect(shown.value, infoVisible) {
        while (shown.value || infoVisible) {
            val core = screen?.mpv
            if (core != null) {
                position = ((core[MpvProperties.TimePos].getOrNull() ?: 0.0) * 1000).toLong()
                duration = ((core[MpvProperties.Duration].getOrNull() ?: 0.0) * 1000).toLong()
                pictureWidth = core[MpvProperties.Width].getOrNull()?.toInt() ?: pictureWidth
                pictureHeight = core[MpvProperties.Height].getOrNull()?.toInt() ?: pictureHeight
                val bytes = core.getString("cache-speed")?.toDoubleOrNull() ?: 0.0
                if (bytes > 0) bitsPerSecond = (bytes * 8).toLong()
                playing = core[MpvProperties.Pause].getOrNull() != true
                mpvStats = "Mode ${mode} · décodage : ${core.getString("hwdec-current").orEmpty().ifBlank { "logiciel" }} · " +
                    "cadence ${core.getString("estimated-vf-fps").orEmpty().take(5)} i/s · " +
                    "perdues décodeur ${core.getString("decoder-frame-drop-count").orEmpty()} · " +
                    "perdues affichage ${core.getString("frame-drop-count").orEmpty()} · " +
                    "en retard ${core.getString("vo-delayed-frame-count").orEmpty()} · " +
                    "écran ${core.getString("display-fps").orEmpty().take(5)} Hz"
                index = core[MpvProperties.PlaylistPos].getOrNull()?.toInt() ?: index
                val clip = clips.getOrNull(index)
                if (clip?.kind == ClipKind.FEATURE) {
                    onProgress(clip.ratingKey, if (playing) "playing" else "paused", position, duration)
                }
            }
            delay(400)
        }
    }

    fun reveal() {
        shown.value = true
        pulse += 1
    }

    // Bouton FR : bascule immédiate sur la piste française du fichier, sans interrompre la lecture.
    fun forceFrench() {
        val core = screen?.mpv ?: return
        val pos = core[MpvProperties.PlaylistPos].getOrNull()?.toInt() ?: 0
        val clip = clips.getOrNull(pos) ?: return
        if (clip.kind != ClipKind.FEATURE) {
            notice = "Le bouton FR agit pendant le film"
            return
        }
        val audio = core[MpvProperties.TrackList].getOrNull().orEmpty().filter { it.type == TrackType.Audio }
        val french = audio.firstOrNull { sameLanguage(it.lang, "fr") && it.isDefault }
            ?: audio.firstOrNull { sameLanguage(it.lang, "fr") }
        if (french != null) {
            core.command(MpvCommands.set(MpvProperties.Aid, TrackSelection.Id(french.id)))
            if (clip.frStreamId > 0) pickedAudio[pos] = clip.frStreamId
            notice = "Audio : français"
            onAudioChosen(clip)
        } else {
            notice = "Aucune piste française dans ce flux"
        }
    }

    fun openAudioMenu() {
        val pos = screen?.mpv?.get(MpvProperties.PlaylistPos)?.getOrNull()?.toInt() ?: index
        val clip = clips.getOrNull(pos) ?: return
        if (clip.kind != ClipKind.FEATURE || clip.audios.isEmpty()) {
            notice = "Aucune piste audio à choisir"
            return
        }
        val current = pickedAudio[pos] ?: clip.audios.firstOrNull { it.current }?.id
        menuIndex = clip.audios.indexOfFirst { it.id == current }.coerceAtLeast(0)
        shown.value = false
        swallowOkUp = true
        audioMenu = true
    }

    // Choix d'une piste dans la liste : mpv a toutes les pistes du fichier, la n-ième piste Plex est la n-ième piste audio de mpv.
    fun chooseAudio(position: Int) {
        audioMenu = false
        val core = screen?.mpv ?: return
        val pos = core[MpvProperties.PlaylistPos].getOrNull()?.toInt() ?: 0
        val clip = clips.getOrNull(pos) ?: return
        val choice = clip.audios.getOrNull(position) ?: return
        val tracks = core[MpvProperties.TrackList].getOrNull().orEmpty().filter { it.type == TrackType.Audio }
        if (tracks.size == clip.audios.size) {
            core.command(MpvCommands.set(MpvProperties.Aid, TrackSelection.Id(tracks[position].id)))
            pickedAudio[pos] = choice.id
            notice = "Audio : ${choice.label}"
            onAudioChosen(clip.copy(frStreamId = choice.id))
        } else {
            notice = "Choix de piste impossible dans ce flux"
        }
    }

    fun subPosition(pos: Int, clip: ClipPlay): Int {
        val id = pickedSub[pos] ?: clip.subStart
        return if (id <= 0) 0 else (clip.subs.indexOfFirst { it.id == id } + 1)
    }
    fun subLabel(clip: ClipPlay, position: Int): String =
        if (position <= 0) "désactivés" else clip.subs.getOrNull(position - 1)?.label.orEmpty()

    // Choix d'un sous-titre : fichier externe ajouté à mpv, sinon piste du fichier (la n-ième piste Plex est la n-ième piste de mpv
    // quand les nombres concordent, à défaut on cherche par langue et « forcé »).
    fun chooseSub(position: Int) {
        val core = screen?.mpv ?: return
        val pos = core[MpvProperties.PlaylistPos].getOrNull()?.toInt() ?: 0
        val clip = clips.getOrNull(pos) ?: return
        if (clip.kind != ClipKind.FEATURE || clip.subs.isEmpty()) {
            notice = "Aucun sous-titre à choisir"
            return
        }
        if (position <= 0) {
            core.command(MpvCommands.set(MpvProperties.Sid, TrackSelection.No))
            pickedSub[pos] = 0
            scope.launch { onSubtitleChosen(clip, 0) }
            notice = "Sous-titres : désactivés"
            return
        }
        if (!clip.direct) {
            notice = "Choix de sous-titres impossible dans ce flux"
            return
        }
        val sub = clip.subs.getOrNull(position - 1) ?: return
        if (sub.url.isNotBlank()) {
            core.command(
                MpvCommands.subAdd(sub.url, SubAddMode.Select, sub.language.ifBlank { "und" }, sub.language.ifBlank { "und" }),
            )
            pickedSub[pos] = sub.id
            scope.launch { onSubtitleChosen(clip, sub.id) }
            notice = "Sous-titres : ${sub.label}"
            return
        }
        val tracks = core[MpvProperties.TrackList].getOrNull().orEmpty().filter { it.type == TrackType.Sub }
        val match = if (tracks.size == clip.subs.size) {
            tracks[position - 1]
        } else {
            tracks.firstOrNull { sameLanguage(it.lang, sub.language) && it.isForced == sub.forced && (sub.codec.isBlank() || it.codec.equals(sub.codec, true)) }
                ?: tracks.firstOrNull { sameLanguage(it.lang, sub.language) && it.isForced == sub.forced }
        }
        if (match == null) {
            notice = "Ce sous-titre n'est pas lisible dans ce flux"
            return
        }
        core.command(MpvCommands.set(MpvProperties.Sid, TrackSelection.Id(match.id)))
        pickedSub[pos] = sub.id
        scope.launch { onSubtitleChosen(clip, sub.id) }
        notice = "Sous-titres : ${sub.label}"
    }
    fun cycleSub() {
        val pos = screen?.mpv?.get(MpvProperties.PlaylistPos)?.getOrNull()?.toInt() ?: index
        val clip = clips.getOrNull(pos) ?: return
        if (clip.kind != ClipKind.FEATURE || clip.subs.isEmpty()) {
            notice = "Aucun sous-titre disponible"
            return
        }
        chooseSub((subPosition(pos, clip) + 1) % (clip.subs.size + 1))
    }
    fun openSubMenu() {
        val pos = screen?.mpv?.get(MpvProperties.PlaylistPos)?.getOrNull()?.toInt() ?: index
        val clip = clips.getOrNull(pos) ?: return
        if (clip.kind != ClipKind.FEATURE || clip.subs.isEmpty()) {
            notice = "Aucun sous-titre à choisir"
            return
        }
        menuIndex = subPosition(pos, clip)
        shown.value = false
        swallowOkUp = true
        subMenu = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (event.type == KeyEventType.KeyUp) {
                    if (ok && swallowOkUp) {
                        swallowOkUp = false
                        return@onPreviewKeyEvent true
                    }
                    if (audioMenu) {
                        if (ok) chooseAudio(menuIndex)
                        return@onPreviewKeyEvent true
                    }
                    if (subMenu) {
                        if (ok) {
                            subMenu = false
                            chooseSub(menuIndex)
                        }
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (audioMenu || subMenu) {
                    val count = if (audioMenu) clips.getOrNull(index)?.audios?.size ?: 0 else (clips.getOrNull(index)?.subs?.size ?: 0) + 1
                    when (event.key) {
                        Key.DirectionUp -> menuIndex = (menuIndex - 1).coerceAtLeast(0)
                        Key.DirectionDown -> menuIndex = (menuIndex + 1).coerceAtMost((count - 1).coerceAtLeast(0))
                        Key.Back, Key.Escape -> {
                            audioMenu = false
                            subMenu = false
                        }
                        else -> Unit
                    }
                    return@onPreviewKeyEvent true
                }
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        if (shown.value) shown.value = false else onExit()
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (!shown.value) {
                            reveal()
                            swallowOkUp = true
                            true
                        } else {
                            pulse += 1
                            false
                        }
                    }
                    Key.DirectionLeft -> {
                        if (shown.value) {
                            pulse += 1
                            false
                        } else {
                            screen?.seek(-30.0, SeekMode.Relative, SeekPrecision.Keyframes)
                            true
                        }
                    }
                    Key.DirectionRight -> {
                        if (shown.value) {
                            pulse += 1
                            false
                        } else {
                            screen?.seek(30.0, SeekMode.Relative, SeekPrecision.Keyframes)
                            true
                        }
                    }
                    else -> {
                        if (shown.value) pulse += 1
                        false
                    }
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        AndroidView(
            factory = { context ->
                MpvView(context).also { created ->
                    created.initialize(
                        MpvOptions(
                            surfaceType = if (mode == "normal") SurfaceType.Texture else SurfaceType.Surface,
                            hwdec = HwdecMode.MediacodecCopy,
                            profile = "fast",
                        ),
                    )
                    // Modes : normal = réglage d'origine ; direct = images du décodeur envoyées à l'écran sans rendu
                    // (fluide en 4K, pas de sous-titres) ; light = rendu simplifié.
                    fun opt(name: String, value: String) {
                        runCatching { created.mpv?.setString(name, value) }
                    }
                    // Sous-titres : texte blanc sur un bandeau noir semi-transparent, léger contour (aussi pour les styles intégrés).
                    opt("sub-color", "#FFFFFFFF")
                    opt("sub-back-color", "#A6000000")
                    opt("sub-border-style", "background-box")
                    opt("sub-border-color", "#FF000000")
                    opt("sub-border-size", "1")
                    opt("sub-ass-override", "force")
                    when (mode) {
                        "direct" -> {
                            opt("hwdec", "mediacodec")
                            opt("vo", "mediacodec_embed")
                        }
                        "light" -> {
                            opt("hwdec", "mediacodec,mediacodec-copy")
                            opt("scale", "bilinear")
                            opt("dscale", "bilinear")
                            opt("cscale", "bilinear")
                            opt("tone-mapping", "clip")
                            opt("hdr-compute-peak", "no")
                            opt("interpolation", "no")
                            opt("deband", "no")
                            opt("sigmoid-upscaling", "no")
                            opt("correct-downscaling", "no")
                        }
                    }
                    val token = clips.firstNotNullOfOrNull { clip ->
                        clip.url.substringAfter("X-Plex-Token=", "").substringBefore("&").takeIf { it.isNotBlank() }
                    }.orEmpty()
                    if (token.isNotBlank()) {
                        created.mpv?.set(
                            MpvProperties.HttpHeaderFields,
                            listOf("X-Plex-Token: " + java.net.URLDecoder.decode(token, Charsets.UTF_8.name())),
                        )
                    }
                    clips.forEachIndexed { item, clip ->
                        created.playFile(clip.url, if (item == 0) LoadFileMode.Replace else LoadFileMode.Append)
                    }
                    created.paused = false
                    created.isFocusable = false
                    created.isFocusableInTouchMode = false
                    screen = created
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                screen = null
                it.destroy()
            },
        )
        if (notice.isNotEmpty()) {
            Text(
                notice,
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            )
        }
        if (audioMenu) {
            val menuClip = clips.getOrNull(index)
            val menuCurrent = pickedAudio[index] ?: menuClip?.audios?.firstOrNull { it.current }?.id
            AudioMenu(
                labels = menuClip?.audios?.map { it.label }.orEmpty(),
                selected = menuIndex,
                current = menuClip?.audios?.indexOfFirst { it.id == menuCurrent } ?: -1,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (subMenu) {
            val menuClip = clips.getOrNull(index)
            AudioMenu(
                labels = listOf("Sans") + menuClip?.subs?.map { it.label }.orEmpty(),
                selected = menuIndex,
                current = menuClip?.let { subPosition(index, it) } ?: -1,
                modifier = Modifier.align(Alignment.Center),
                title = "Sous-titres",
            )
        }
        if (infoVisible && mpvStats.isNotEmpty()) {
            Text(
                mpvStats,
                color = Color(0xFF9AA3AD),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 20.dp),
            )
        }
        if (infoVisible) {
            PlaybackFacts(
                clips.getOrNull(index),
                pictureWidth,
                pictureHeight,
                bitsPerSecond,
                Modifier.align(Alignment.TopStart),
            )
        }
        if (infoVisible) {
            val clip = clips.getOrNull(index)
            val text = if (clip == null) "" else listOf(
                "Appli ${tv.plexiplay.plex.PlexHeaders.VERSION} · mpv · " + (if (clip.direct) "direct" else "converti"),
                "ST appli : départ=${clip.subStart} choisi=${pickedSub[index]} liste=${clip.subs.size} message=\"$notice\"",
                clip.diag,
            ).joinToString("\n")
            SubDiagnostic(text, Modifier.align(Alignment.TopEnd))
        }
        if (shown.value) {
            val fraction = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                    .padding(start = 48.dp, end = 48.dp, top = 36.dp, bottom = 22.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(16.dp)) {
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(fraction).height(3.dp).clip(CircleShape).background(Color.White))
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(position), color = Color.White, fontSize = 12.sp)
                    Text(clock(duration), color = Color.White, fontSize = 12.sp)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerIcon(Glyph.Rew30) {
                        screen?.seek(-30.0, SeekMode.Relative, SeekPrecision.Keyframes)
                        reveal()
                    }
                    PlayerIcon(Glyph.Rew10) {
                        screen?.seek(-10.0, SeekMode.Relative, SeekPrecision.Keyframes)
                        reveal()
                    }
                    PlayerIcon(if (playing) Glyph.Pause else Glyph.Play, playFocus) {
                        screen?.paused = playing
                        reveal()
                    }
                    PlayerIcon(Glyph.Fwd10) {
                        screen?.seek(10.0, SeekMode.Relative, SeekPrecision.Keyframes)
                        reveal()
                    }
                    PlayerIcon(Glyph.Fwd30) {
                        screen?.seek(30.0, SeekMode.Relative, SeekPrecision.Keyframes)
                        reveal()
                    }
                    PlayerIcon(Glyph.Subs, onLongPress = { openSubMenu() }) {
                        cycleSub()
                        reveal()
                    }
                    PlayerIcon(
                        Glyph.Fr,
                        onLongPress = { openAudioMenu() },
                        label = run {
                            val tagClip = clips.getOrNull(index)
                            val tagPick = pickedAudio[index]
                            val code = when {
                                tagPick == null -> tagClip?.audioLanguage
                                tagPick == tagClip?.frStreamId && (tagPick ?: 0) > 0 -> "fr"
                                else -> tagClip?.audios?.firstOrNull { it.id == tagPick }?.language
                            }
                            langTag(code).ifBlank { "FR" }
                        },
                    ) {
                        forceFrench()
                        reveal()
                    }
                    PlayerIcon(Glyph.Info) {
                        infoVisible = !infoVisible
                        reveal()
                    }
                    PlayerIcon(Glyph.Skip) {
                        screen?.mpv?.command(MpvCommands.playlistNext())
                        reveal()
                    }
                    PlayerIcon(Glyph.Stop) { onExit() }
                }
            }
        }
    }
}

@Composable
internal fun PlaybackFacts(
    clip: ClipPlay?,
    width: Int,
    height: Int,
    bitsPerSecond: Long,
    modifier: Modifier = Modifier,
) {
    val decoded = if (width > 0 && height > 0) "${width}×${height}" else ""
    val resolution = listOf(decoded, clip?.fileResolution.orEmpty())
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
        .ifBlank { "—" }
    Column(
        modifier
            .padding(28.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(if (clip?.direct == true) "Lecture directe" else "Transcodage", color = Color.White, fontSize = 16.sp)
        Text("Résolution  $resolution", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Text("Réseau  ${rateLabel(bitsPerSecond)}", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Bloc de diagnostic des sous-titres, affiché avec le panneau « i » (en haut à droite). */
@Composable
internal fun SubDiagnostic(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Column(
        modifier
            .padding(28.dp)
            .width(560.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("Diagnostic sous-titres", color = Color(0xFF5DD39E), fontSize = 13.sp)
        Text(text, color = Color(0xFFE6EAEE), fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

internal fun rateLabel(bits: Long): String {
    if (bits <= 0L) return "—"
    val mega = bits / 1_000_000.0
    return if (mega >= 1.0) "%.1f Mbit/s".format(mega) else "%.0f kbit/s".format(bits / 1_000.0)
}
