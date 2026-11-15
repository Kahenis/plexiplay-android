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
import tv.plexiplay.ui.theme.tap
import tv.plexiplay.ui.theme.tapOrHold
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun MpvPlayerScreen(
    clips: List<ClipPlay>,
    mode: String,
    onProgress: (ratingKey: String, state: String, position: Long, duration: Long) -> Unit,
    onExit: () -> Unit,
    onRegisterBack: ((() -> Boolean)?) -> Unit,
    onAudioChosen: (ClipPlay) -> Unit = {},
    onSubtitleChosen: suspend (ClipPlay, Int) -> Unit = { _, _ -> },
    prerollVolume: Float = 0.75f,
    onCompat: () -> Unit = {},
    onPrerollStarted: (ClipPlay) -> Unit = {},
    onPrerollError: (ClipPlay, Boolean) -> Unit = { _, _ -> },
    titleInfo: suspend (String) -> TitleInfo? = { null },
    skipMarkers: suspend (String) -> List<SkipMarker> = { emptyList() },
    autoSkipIntro: Boolean = false,
    autoSkipCredits: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var userSkipped by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var audioMenu by remember { mutableStateOf(false) }
    var subMenu by remember { mutableStateOf(false) }
    var menuIndex by remember { mutableIntStateOf(0) }
    val pickedAudio = remember { mutableStateMapOf<Int, Int>() }
    val pickedSub = remember { mutableMapOf<Int, Int>() }
    LaunchedEffect(notice) {
        if (notice.isNotEmpty()) {
            delay(if (notice.startsWith("Erreur preroll")) 9000L else 4000L)
            notice = ""
        }
    }
    var infoVisible by remember { mutableStateOf(false) }
    // Page du panneau « i » : 0 = lecture, 1 = diagnostic (un nouvel appui sur « i » passe à la page suivante, puis ferme).
    var infoPage by remember { mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val shown = remember { mutableStateOf(false) }
    var pulse by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var pictureWidth by remember { mutableIntStateOf(0) }
    var pictureHeight by remember { mutableIntStateOf(0) }
    var bitsPerSecond by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var mpvStats by remember { mutableStateOf("") }
    var mpvLive by remember { mutableStateOf<List<InfoLine>>(emptyList()) }
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
    // Message d'attente d'un flux converti : tant qu'il n'y a ni image ni temps de lecture.
    var waitText by remember { mutableStateOf<String?>(null) }
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
            val waiting = clip != null && clip.kind == ClipKind.FEATURE && !clip.direct && core != null &&
                ((core.get(MpvProperties.Width).getOrNull()?.toInt() ?: 0) <= 0 || (core.get(MpvProperties.TimePos).getOrNull() ?: 0.0) <= 0.0)
            waitText = if (waiting) waitMessage(clip) else null
            val sign = "$pos:${tracks.size}:${tracks.firstOrNull()?.id}:${clip?.subtitleUrl}"
            if (clip != null) {
                // Volume d'un preroll : réduit ; le film et les bandes-annonces gardent 100 %.
                val wanted = if (clip.kind == ClipKind.PREROLL) (prerollVolume * 100).toInt() else 100
                if (core?.getString("volume")?.toDoubleOrNull()?.toInt() != wanted) runCatching { core?.setString("volume", wanted.toString()) }
            }
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
        // Surveillance : pas d'image 12 s après le début du film (ou rien de lu), l'application relance le film en conversion complète.
        var noPicture = 0
        var nothingYet = 0
        var fallbackDone = false
        // Préroll séquentiel : compté comme lu dès qu'il démarre ; si mpv passe au suivant sans l'avoir lu, il est en erreur.
        var watchedIndex = -1
        var watchedPlayed = 0L
        val startedPrerolls = mutableSetOf<String>()
        while (true) {
            val core = view.mpv
            if (core != null) {
                val pos = core[MpvProperties.PlaylistPos].getOrNull()?.toInt() ?: 0
                val clip = clips.getOrNull(pos)
                if (pos != watchedIndex) {
                    val before = clips.getOrNull(watchedIndex)
                    if (before != null && before.kind == ClipKind.PREROLL && before.prerollFile.isNotBlank() && watchedPlayed <= 0L && !userSkipped) {
                        onPrerollError(before, true)
                        notice = "Erreur preroll, merci de signaler ce message au créateur du logiciel\n${before.prerollFile}"
                    }
                    userSkipped = false
                    watchedIndex = pos
                    watchedPlayed = 0L
                }
                if (clip != null && clip.kind == ClipKind.PREROLL) {
                    val playedNow = ((core[MpvProperties.TimePos].getOrNull() ?: 0.0) * 1000).toLong()
                    if (playedNow > watchedPlayed) watchedPlayed = playedNow
                    if (playedNow > 0L && clip.prerollFile.isNotBlank() && startedPrerolls.add(clip.prerollKey)) onPrerollStarted(clip)
                }
                if (clip != null && clip.kind == ClipKind.FEATURE && !fallbackDone) {
                    val shownWidth = core[MpvProperties.Width].getOrNull()?.toInt() ?: 0
                    val playedMs = ((core[MpvProperties.TimePos].getOrNull() ?: 0.0) * 1000).toLong()
                    val paused = core[MpvProperties.Pause].getOrNull() == true
                    // Le temps avance sans image : 12 s ; rien de lu du tout : 25 s (un démarrage lent ne doit pas déclencher la conversion).
                    if (!paused && playedMs > 0L && shownWidth <= 0) noPicture += 1 else noPicture = 0
                    if (!paused && playedMs <= 0L) nothingYet += 1 else nothingYet = 0
                    if ((noPicture >= 12 || nothingYet >= 25) && clips.none { it.expectAudio.isNotBlank() }) {
                        fallbackDone = true
                        onCompat()
                    }
                }
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
                if (infoVisible) {
                    fun prop(name: String) = core.getString(name).orEmpty().trim()
                    val live = mutableListOf<InfoLine>()
                    live += "Image" to ""
                    val format = prop("video-format")
                    val fps = prop("estimated-vf-fps").take(5)
                    val gamma = prop("video-params/gamma")
                    val hdr = when {
                        gamma.contains("pq", true) -> " · HDR10"
                        gamma.contains("hlg", true) -> " · HLG"
                        else -> ""
                    }
                    live += "Flux reçu" to "${codecLabel(format)} ${pictureWidth}×${pictureHeight}" + (if (fps.isNotBlank()) " · $fps i/s" else "") + hdr
                    val hwdec = prop("hwdec-current")
                    live += "Décodeur" to if (hwdec.isBlank() || hwdec == "no") "FFmpeg (logiciel)" else "$hwdec (matériel TV)"
                    live += "Images" to "perdues décodeur ${prop("decoder-frame-drop-count").ifBlank { "0" }} · perdues affichage ${prop("frame-drop-count").ifBlank { "0" }}"
                    live += "Réseau" to "${rateLabel(bitsPerSecond)} · tampon ${prop("demuxer-cache-duration").take(5).ifBlank { "0" }} s"
                    live += "Son" to ""
                    val audioName = prop("audio-codec-name")
                    val channels = prop("audio-params/channel-count").toIntOrNull() ?: 0
                    live += "Flux reçu" to listOf(codecLabel(audioName), channelsLabel(channels)).filter { it.isNotBlank() }.joinToString(" ")
                    live += "Décodeur" to "FFmpeg (mpv)"
                    mpvLive = live
                }
                index = core[MpvProperties.PlaylistPos].getOrNull()?.toInt() ?: index
                val clip = clips.getOrNull(index)
                if (clip?.kind == ClipKind.FEATURE) {
                    onProgress(clip.ratingKey, if (playing) "playing" else "paused", position, duration)
                }
            }
            delay(400)
        }
    }

    // Fiche du titre (appui court sur « i ») : demandée au serveur, gardée en mémoire pour la suite.
    var titleCard by remember { mutableStateOf<TitleInfo?>(null) }
    val cardCache = remember { mutableMapOf<String, TitleInfo>() }
    var nowPlaying by remember { mutableStateOf("") }
    fun nowPlayingText(): String {
        val core = screen?.mpv ?: return ""
        fun prop(name: String) = core.getString(name).orEmpty().trim()
        val subLang = prop("current-tracks/sub/lang")
        val subOn = prop("current-tracks/sub/id").isNotBlank() && prop("sid") != "no"
        val subtitles = if (!subOn) "" else listOf(languageName(subLang).ifBlank { prop("current-tracks/sub/title") }, if (prop("current-tracks/sub/forced") == "yes") "forcés" else "").filter { it.isNotBlank() }.joinToString(" ")
        return nowPlayingLine(
            core[MpvProperties.Width].getOrNull()?.toInt() ?: 0,
            core[MpvProperties.Height].getOrNull()?.toInt() ?: 0,
            prop("audio-codec-name"),
            prop("audio-params/channel-count").toIntOrNull() ?: 0,
            prop("current-tracks/audio/lang"),
            subtitles,
        )
    }
    LaunchedEffect(titleCard != null) {
        while (titleCard != null) {
            nowPlaying = nowPlayingText()
            // Le film ou l'épisode a changé depuis l'ouverture de la fiche : elle n'est plus la bonne.
            val shownKey = titleCard?.key
            val now = index
            val expected = clips.drop(now).firstOrNull { it.kind == ClipKind.FEATURE }?.ratingKey ?: clips.getOrNull(now)?.ratingKey
            if (shownKey != null && expected != null && shownKey != expected) titleCard = null
            delay(700)
        }
    }
    // Intro et générique : repères du serveur Plex, bouton « Passer… » et saut automatique (réglages).
    val skipState = remember { SkipState() }
    var activeSkip by remember { mutableStateOf<SkipMarker?>(null) }
    fun skipNow(marker: SkipMarker) {
        val duration = ((screen?.mpv?.get(MpvProperties.Duration)?.getOrNull() ?: 0.0) * 1000).toLong()
        screen?.seek(skipTarget(marker, duration) / 1000.0, SeekMode.Absolute, SeekPrecision.Keyframes)
        notice = marker.doneNotice
        activeSkip = null
    }
    LaunchedEffect(Unit) {
        while (true) {
            val core = screen?.mpv
            val at = if (core != null) core[MpvProperties.PlaylistPos].getOrNull()?.toInt() ?: index else index
            val clip = clips.getOrNull(at)
            if (core != null && clip != null && clip.kind == ClipKind.FEATURE) {
                val key = clip.ratingKey
                if (skipState.requested.add(key)) {
                    scope.launch { skipState.markers[key] = runCatching { skipMarkers(key) }.getOrDefault(emptyList()) }
                }
                val position = ((core[MpvProperties.TimePos].getOrNull() ?: 0.0) * 1000).toLong()
                val found = activeMarker(skipState.markers[key].orEmpty(), position)
                val auto = found != null && ((found.kind == "intro" && autoSkipIntro) || (found.kind == "credits" && autoSkipCredits && !clip.closeOnEnd))
                if (found != null && auto && skipState.autoDone.add("$key:${found.kind}:${found.startMs}")) {
                    skipNow(found)
                } else {
                    activeSkip = found
                }
            } else {
                activeSkip = null
            }
            delay(500)
        }
    }
    fun showTitleCard(current: Int) {
        val target = clips.drop(current).firstOrNull { it.kind == ClipKind.FEATURE } ?: clips.getOrNull(current) ?: return
        infoVisible = false
        val cached = cardCache[target.ratingKey]
        if (cached != null) {
            titleCard = cached
            return
        }
        val key = target.ratingKey
        titleCard = TitleInfo(key, target.label, summary = "Chargement…")
        scope.launch {
            val loaded = runCatching { titleInfo(key) }.getOrNull()
            if (loaded != null) cardCache[key] = loaded
            if (titleCard?.key == key) titleCard = loaded ?: TitleInfo(key, target.label)
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

    ImmersiveLandscape()
    PauseWhenBackgrounded { screen?.paused = true }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .playerGestures(
                onToggle = {
                    if (audioMenu || subMenu) {
                        audioMenu = false
                        subMenu = false
                    } else if (shown.value) shown.value = false else reveal()
                },
                onSeek = { direction ->
                    screen?.seek(direction * 10.0, SeekMode.Relative, SeekPrecision.Keyframes)
                    notice = if (direction < 0) "− 10 s" else "+ 10 s"
                },
                onFeedback = { notice = it },
            )
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
                            val skip = activeSkip
                            if (skip != null) {
                                skipNow(skip)
                            } else {
                                reveal()
                            }
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
        waitText?.let { WaitOverlay(it, Modifier.align(Alignment.Center)) }
        if (notice.isNotEmpty()) {
            Text(
                notice,
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            )
        }
        if (audioMenu || subMenu) {
            Box(Modifier.fillMaxSize().tap { audioMenu = false; subMenu = false })
        }
        if (audioMenu) {
            val menuClip = clips.getOrNull(index)
            val menuCurrent = pickedAudio[index] ?: menuClip?.audios?.firstOrNull { it.current }?.id
            AudioMenu(
                labels = menuClip?.audios?.map { it.label }.orEmpty(),
                selected = menuIndex,
                current = menuClip?.audios?.indexOfFirst { it.id == menuCurrent } ?: -1,
                modifier = Modifier.align(Alignment.Center),
                onPick = { chooseAudio(it) },
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
                onPick = {
                    subMenu = false
                    chooseSub(it)
                },
            )
        }
        titleCard?.let { TitleInfoPanel(it, nowPlaying, Modifier.align(Alignment.TopStart)) }
        activeSkip?.takeIf { !audioMenu && !subMenu }?.let { SkipButton(it, shown.value, Modifier.align(Alignment.BottomEnd)) { skipNow(it) } }
        if (infoVisible) {
            if (infoPage == 0) {
                InfoPanel(playbackLines(clips.getOrNull(index), "mpv") + mpvLive, Modifier.align(Alignment.TopStart))
            } else {
                InfoPanel(diagnosticLines(context, clips.getOrNull(index), mpvLive + ("Mesures mpv" to mpvStats)), Modifier.align(Alignment.TopStart), twoColumns = true)
            }
        }
        if (shown.value) {
            val fraction = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
            val barPad = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 700) 16.dp else 48.dp
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                    .padding(start = barPad, end = barPad, top = 36.dp, bottom = 22.dp),
            ) {
                SeekBar(
                    fraction = fraction,
                    onSeek = { f -> if (duration > 0L) screen?.seek(f * duration / 1000.0, SeekMode.Absolute, SeekPrecision.Keyframes) },
                    onTouch = { reveal() },
                )
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(position), color = Color.White, fontSize = 12.sp)
                    Text(clock(duration), color = Color.White, fontSize = 12.sp)
                }
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
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
                    PlayerIcon(
                        Glyph.Info,
                        onLongPress = {
                            // Appui de 3 secondes : données techniques (lecture, puis diagnostic, puis fermeture).
                            titleCard = null
                            if (!infoVisible) {
                                infoVisible = true
                                infoPage = 0
                            } else if (infoPage == 0) {
                                infoPage = 1
                            } else {
                                infoVisible = false
                            }
                            reveal()
                        },
                        longPressMs = 3000L,
                    ) {
                        // Appui court : fiche du film ou de l'épisode.
                        if (titleCard != null) titleCard = null else showTitleCard(index)
                        reveal()
                    }
                    PlayerIcon(Glyph.Skip) {
                        userSkipped = true
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
