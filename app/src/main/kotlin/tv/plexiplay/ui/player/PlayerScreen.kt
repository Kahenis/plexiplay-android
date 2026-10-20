package tv.plexiplay.ui.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.plexiplay.player.AspectMode
import tv.plexiplay.player.Engine
import tv.plexiplay.player.PlayerKind
import tv.plexiplay.plex.preroll.ClipKind

data class ClipPlay(
    val url: String,
    val label: String,
    val kind: ClipKind,
    val ratingKey: String,
    val info: String,
    val audioLanguage: String = "",
    val textLanguage: String = "",
    val textMode: String = "off",
    val direct: Boolean = false,
    val fileResolution: String = "",
    val subtitleCodec: String = "",
    val subtitleUrl: String = "",
    val audioCodec: String = "",
    val startMs: Long = 0L,
    val sourceAspect: Float = 0f,
    /** Film ou vidéo personnelle (pas un épisode) : l'écran de lecture se ferme à la fin. */
    val closeOnEnd: Boolean = false,
    /** Flux converti par le serveur avec la piste française (bouton FR), piste et partie à enregistrer. */
    val frUrl: String = "",
    val audios: List<AudioChoice> = emptyList(),
    val frStreamId: Int = 0,
    val frCodec: String = "",
    val partId: Long = 0L,
    /** Sous-titres du film (bouton ST) : liste de Plex, choix de départ (0 = aucun), piste audio de départ, et fabrique de flux converti (piste audio, sous-titre incrusté). */
    val subs: List<SubChoice> = emptyList(),
    val subStart: Int = 0,
    val audioStart: Int = 0,
    val streamFor: ((Int, Int) -> String)? = null,
    /** Texte de diagnostic (panneau « i ») : versions du film, pistes de sous-titres vues par Plex, choix de départ. */
    val diag: String = "",
    /** Langue audio demandée après un changement de piste (relance complète) : le lecteur vérifie qu'elle est bien jouée. */
    val expectAudio: String = "",
)

internal enum class Glyph { Play, Pause, Rew10, Fwd10, Rew30, Fwd30, Subs, Fr, Info, Skip, Hide, Stop }

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    clips: List<ClipPlay>,
    aspect: AspectMode,
    engine: Engine,
    playerKind: PlayerKind = PlayerKind.EXO,
    onProgress: (ratingKey: String, state: String, position: Long, duration: Long) -> Unit,
    onExit: () -> Unit,
    onRegisterBack: ((() -> Boolean)?) -> Unit = {},
    compat: Boolean = false,
    onCompat: () -> Unit = {},
    mpvMode: String = "light",
    formatFix: Boolean = true,
    onAudioChosen: (ClipPlay) -> Unit = {},
    onSubtitleChosen: suspend (ClipPlay, Int) -> Unit = { _, _ -> },
    // Avant un changement de piste en flux converti : arrête l'ancienne session de transcodage et enregistre la piste audio (-1 = aucune) sur le serveur.
    onTrackSync: suspend (ClipPlay, Int, String) -> Unit = { _, _, _ -> },
    // Changement de piste sur un flux converti : relance complète du film comme depuis la page de présentation
    // (clip, piste audio, sous-titre, position, adresse du flux en cours). Renvoie vrai si l'appli a relancé le film.
    onReload: suspend (ClipPlay, Int, Int, Long, String) -> Boolean = { _, _, _, _, _ -> false },
) {
    if (playerKind == PlayerKind.MPV) {
        MpvPlayerScreen(clips, mpvMode, onProgress, onExit, onRegisterBack, onAudioChosen, onSubtitleChosen)
        return
    }
    var infoVisible by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(clips.firstOrNull()?.label ?: "") }
    var notice by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<PendingChange?>(null) }
    var lastChange by remember { mutableStateOf("") }
    var lastError by remember { mutableStateOf("") }
    var probe by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var retries by remember { mutableIntStateOf(0) }
    // Message « Basculement… » : pendant un changement de piste en flux converti, jusqu'à l'apparition de l'image.
    var switching by remember { mutableStateOf(clips.any { it.expectAudio.isNotBlank() }) }
    val shown = remember { mutableStateOf(false) }
    var pulse by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var pictureWidth by remember { mutableIntStateOf(0) }
    var pictureHeight by remember { mutableIntStateOf(0) }
    var aspectNote by remember { mutableStateOf("") }
    val firstFrame = remember { mutableStateOf(false) }
    var nudge by remember { mutableIntStateOf(0) }
    var bitsPerSecond by remember { mutableLongStateOf(0L) }
    // Diagnostic vidéo (panneau « i ») : décodeur choisi par la TV et nombre de coupures de lecture (tampon vide en cours de film).
    var decoderName by remember { mutableStateOf("") }
    var rebuffers by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    var swallowOkUp by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val selection = remember { intArrayOf(-1, 0) }
    // Bouton FR et liste des pistes : flux relancé (url) ou piste française choisie dans le flux, par numéro de lecture.
    val reloadedUrl = remember { mutableMapOf<Int, String>() }
    val frInStream = remember { mutableSetOf<Int>() }
    val pickedAudio = remember { mutableMapOf<Int, Int>() }
    // Bouton ST : sous-titre choisi (0 = aucun), incrusté par un flux relancé, ou fichier externe chargé à part, par numéro de lecture.
    val pickedSub = remember { mutableMapOf<Int, Int>() }
    val burnedSub = remember { mutableMapOf<Int, Int>() }
    val sidecarSub = remember { mutableMapOf<Int, Int>() }
    var subMenu by remember { mutableStateOf(false) }
    var pendingSub by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    var audioMenu by remember { mutableStateOf(false) }
    var audioTag by remember { mutableStateOf("FR") }
    var menuIndex by remember { mutableIntStateOf(0) }
    fun effective(index: Int): ClipPlay? {
        val base = clips.getOrNull(index) ?: return null
        val url = reloadedUrl[index]
        val current = when {
            url != null -> base.copy(audioLanguage = if (url == base.frUrl) "fr" else "", audioCodec = "", direct = false, url = url)
            index in frInStream -> base.copy(audioLanguage = "fr", audioCodec = base.frCodec)
            else -> base
        }
        val sub = pickedSub[index] ?: return current
        val choice = base.subs.firstOrNull { it.id == sub }
        return if (sub == 0 || choice == null || (burnedSub[index] ?: 0) > 0) {
            current.copy(textMode = "off", subtitleUrl = "")
        } else {
            current.copy(
                textMode = if (choice.forced) "forced" else "full",
                textLanguage = choice.language,
                subtitleCodec = choice.codec,
                subtitleUrl = if (sidecarSub[index] == sub) choice.url else "",
            )
        }
    }
    val player = remember {
        // Mode compatible : le décodeur Dolby Vision est écarté, la TV lit la couche HDR10 de base.
        val selector = androidx.media3.exoplayer.mediacodec.MediaCodecSelector { mime, secure, tunneling ->
            val wanted = if (compat && mime == MimeTypes.VIDEO_DOLBY_VISION) MimeTypes.VIDEO_H265 else mime
            androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT.getDecoderInfos(wanted, secure, tunneling)
        }
        val renderers = DefaultRenderersFactory(context)
            .setMediaCodecSelector(selector)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                if (engine == Engine.TV) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
            )
        val token = clips.firstNotNullOfOrNull { clip ->
            clip.url.substringAfter("X-Plex-Token=", "").substringBefore("&").takeIf { it.isNotBlank() }
        }.orEmpty()
        val headers = buildMap {
            if (token.isNotBlank()) put("X-Plex-Token", java.net.URLDecoder.decode(token, Charsets.UTF_8.name()))
            put("X-Plex-Product", "Plexiplay")
            put("X-Plex-Platform", "Android")
        }
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)))
            .build().apply {
            val startIndex = clips.indexOfFirst { it.startMs > 0L }.coerceAtLeast(0)
            val startAt = clips.getOrNull(startIndex)?.startMs ?: 0L
            val items = clips.map { clip ->
                val builder = MediaItem.Builder().setUri(clip.url).setMediaId(clip.ratingKey)
                if (clip.url.contains(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                if (clip.subtitleUrl.isNotBlank()) {
                    builder.setSubtitleConfigurations(
                        listOf(
                            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(clip.subtitleUrl))
                                .setMimeType(subtitleMime(clip.subtitleCodec))
                                .setLanguage(clip.textLanguage.ifBlank { "fr" })
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build(),
                        ),
                    )
                }
                builder.build()
            }
            if (startAt > 0L) setMediaItems(items, startIndex, startAt) else setMediaItems(items)
            prepare()
            playWhenReady = true
            clips.firstOrNull()?.let { applyTracks(this, it) }
        }
    }
    // Langue lue, pour le bouton FR : choix fait (liste ou FR), sinon piste réellement sélectionnée, sinon réglage de départ ; FR par défaut.
    fun refreshAudioTag() {
        val index = player.currentMediaItemIndex
        val clip = clips.getOrNull(index)
        val picked = pickedAudio[index]
        val fromPick = if (picked != null) {
            if (picked == clip?.frStreamId && picked > 0) "fr" else clip?.audios?.firstOrNull { it.id == picked }?.language
        } else {
            null
        }
        val fromFr = if (clip != null && (index in frInStream || (clip.frUrl.isNotBlank() && reloadedUrl[index] == clip.frUrl))) "fr" else null
        val fromTrack = player.currentTracks.groups.firstNotNullOfOrNull { group ->
            if (group.type != C.TRACK_TYPE_AUDIO) null
            else (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it).language }
        }
        audioTag = langTag(fromPick).ifBlank { langTag(fromFr) }.ifBlank { langTag(fromTrack) }.ifBlank { langTag(clip?.audioLanguage) }.ifBlank { "FR" }
    }
    fun relaunch(index: Int, url: String, message: String, position: Long = player.currentPosition) {
        val ratingKey = clips.getOrNull(index)?.ratingKey.orEmpty()
        reloadedUrl[index] = url
        selection[0] = -1
        selection[1] = 0
        notice = message
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaId(ratingKey)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        player.replaceMediaItem(index, item)
        player.seekTo(index, position)
        player.prepare()
        player.playWhenReady = true
    }
    // URL d'un flux relancé pour une autre piste audio : garde le sous-titre choisi avec ST (incrusté), sinon l'URL préparée.
    fun audioUrl(index: Int, clip: ClipPlay, audioId: Int, fallback: String): String {
        val sub = pickedSub[index]
        val factory = clip.streamFor
        if (sub == null || factory == null) return fallback
        burnedSub[index] = sub
        return factory(audioId, sub)
    }
    fun selectedAudioLanguage(): String = player.currentTracks.groups.firstNotNullOfOrNull { group ->
        if (group.type != C.TRACK_TYPE_AUDIO) null
        else (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it).language }
    }.orEmpty()
    // Échec d'un changement de piste en flux converti : message pour l'utilisateur, retour au flux précédent à la même position.
    fun failChange(reason: String) {
        val change = pending ?: return
        pending = null
        switching = false
        val message = "Impossible en transcodant. Veuillez choisir vos préférences sur la page de présentation."
        lastChange = "${change.what} · ÉCHEC ($reason) après ${(android.os.SystemClock.elapsedRealtime() - change.startedAt) / 1000} s"
        val clip = clips.getOrNull(change.index) ?: return
        if (change.prevAudio != null) pickedAudio[change.index] = change.prevAudio else pickedAudio.remove(change.index)
        if (change.prevSub != null) pickedSub[change.index] = change.prevSub else pickedSub.remove(change.index)
        if (change.prevBurn != null) burnedSub[change.index] = change.prevBurn else burnedSub.remove(change.index)
        refreshAudioTag()
        notice = message
        val current = reloadedUrl[change.index] ?: clip.url
        scope.launch {
            runCatching { onTrackSync(clip, change.prevAudio ?: -1, current) }
            runCatching { onSubtitleChosen(clip, change.prevSub ?: clip.subStart) }
            relaunch(change.index, change.prevUrl, message, change.position)
        }
    }
    // Changement de piste sur un flux converti, comme depuis la page du film : arrêt du flux, enregistrement des pistes sur le serveur,
    // puis nouveau flux (session neuve) à la même position ; une vérification suit (voir plus bas).
    fun syncedRelaunch(index: Int, clip: ClipPlay, url: String, message: String, audioId: Int, wantLang: String, what: String, subId: Int? = null) {
        val position = player.currentPosition
        val prevUrl = reloadedUrl[index] ?: clip.url
        val change = PendingChange(
            index = index, what = what, wantLang = lang2(wantLang), position = position, prevUrl = prevUrl,
            prevAudio = pickedAudio[index], prevSub = pickedSub[index], prevBurn = burnedSub[index],
            startedAt = android.os.SystemClock.elapsedRealtime(),
        )
        notice = message
        lastChange = "$what · en cours…"
        player.pause()
        switching = true
        val reloadAudio = if (audioId > 0) audioId else (pickedAudio[index] ?: clip.audioStart)
        val reloadSub = subId ?: (pickedSub[index] ?: clip.subStart)
        scope.launch {
            // Plex ne change de piste dans un flux converti qu'à un nouveau lancement du film : relance complète (fermeture, reprise à la même position).
            val reloaded = runCatching { onReload(clip, reloadAudio, reloadSub, position, prevUrl) }.getOrDefault(false)
            if (reloaded) return@launch
            runCatching { onTrackSync(clip, audioId, prevUrl) }
            if (subId != null) runCatching { onSubtitleChosen(clip, subId) }
            relaunch(index, url, message, position)
            pending = change
        }
    }
    fun forceFrench() {
        val index = player.currentMediaItemIndex
        val clip = clips.getOrNull(index) ?: return
        if (clip.kind != ClipKind.FEATURE) {
            notice = "Le bouton FR agit pendant le film"
            return
        }
        val reloaded = reloadedUrl[index] != null
        val wanted = pickTrack(player, C.TRACK_TYPE_AUDIO) { sameLanguage(it.language, "fr") }
        if (wanted != null) {
            val active = player.currentTracks.groups.any { group ->
                group.type == C.TRACK_TYPE_AUDIO && (0 until group.length).any { i ->
                    group.isTrackSelected(i) && sameLanguage(group.getTrackFormat(i).language, "fr")
                }
            }
            if (!active) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setOverrideForType(TrackSelectionOverride(wanted.group, listOf(wanted.index)))
                    .build()
            }
            if (!reloaded) frInStream += index
            pickedAudio[index] = clip.frStreamId
            refreshAudioTag()
            notice = if (active) "Audio déjà en français" else "Audio : français"
            if (!active) onAudioChosen(clip)
            return
        }
        if (clip.frUrl.isBlank() || clip.frStreamId <= 0) {
            notice = "Aucune piste française dans ce film"
            return
        }
        if (reloadedUrl[index] == clip.frUrl || (!reloaded && clip.url.contains("audioStreamID=${clip.frStreamId}"))) {
            notice = "Audio déjà en français"
            return
        }
        // La piste française n'est pas dans le flux en cours (flux converti, ou piste que la TV ne décode pas) :
        // on relance le flux avec cette piste, à la même position, sans quitter le lecteur.
        syncedRelaunch(index, clip, audioUrl(index, clip, clip.frStreamId, clip.frUrl), "Passage en français…", clip.frStreamId, "fr", "audio → FR (#${clip.frStreamId})")
        pickedAudio[index] = clip.frStreamId
        refreshAudioTag()
    }
    fun openAudioMenu() {
        val index = player.currentMediaItemIndex
        val clip = clips.getOrNull(index) ?: return
        if (clip.kind != ClipKind.FEATURE || clip.audios.isEmpty()) {
            notice = "Aucune piste audio à choisir"
            return
        }
        val current = pickedAudio[index] ?: clip.audios.firstOrNull { it.current }?.id
        menuIndex = clip.audios.indexOfFirst { it.id == current }.coerceAtLeast(0)
        shown.value = false
        swallowOkUp = true
        audioMenu = true
    }
    fun chooseAudio(position: Int) {
        val index = player.currentMediaItemIndex
        val clip = clips.getOrNull(index) ?: return
        val choice = clip.audios.getOrNull(position) ?: return
        audioMenu = false
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val inStream = reloadedUrl[index] == null && clip.direct && groups.size == clip.audios.size
        if (inStream && groups[position].isTrackSupported(0)) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(TrackSelectionOverride(groups[position].mediaTrackGroup, listOf(0)))
                .build()
            frInStream.remove(index)
            pickedAudio[index] = choice.id
            refreshAudioTag()
            notice = "Audio : ${choice.label}"
            onAudioChosen(clip.copy(frStreamId = choice.id))
            return
        }
        val same = if (reloadedUrl[index] != null) reloadedUrl[index] == choice.url else clip.url.contains("audioStreamID=${choice.id}")
        if (same) {
            notice = "Déjà sur cette piste"
            return
        }
        frInStream.remove(index)
        syncedRelaunch(index, clip, audioUrl(index, clip, choice.id, choice.url), "Audio : ${choice.label}…", choice.id, choice.language, "audio → ${choice.label} (#${choice.id})")
        pickedAudio[index] = choice.id
        refreshAudioTag()
    }
    fun subId(index: Int, clip: ClipPlay): Int = pickedSub[index] ?: clip.subStart
    fun subPosition(index: Int, clip: ClipPlay): Int {
        val id = subId(index, clip)
        return if (id <= 0) 0 else (clip.subs.indexOfFirst { it.id == id } + 1)
    }
    fun subLabel(clip: ClipPlay, position: Int): String =
        if (position <= 0) "désactivés" else clip.subs.getOrNull(position - 1)?.label.orEmpty()
    // Fichier de sous-titres externe : l'élément est rechargé avec le fichier, à la même position, sans quitter le lecteur.
    fun reloadWithSidecar(index: Int, clip: ClipPlay, sub: SubChoice) {
        val position = player.currentPosition
        selection[0] = -1
        selection[1] = 0
        val builder = MediaItem.Builder().setUri(clip.url).setMediaId(clip.ratingKey)
        if (clip.url.contains(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        builder.setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                    .setMimeType(subtitleMime(sub.codec))
                    .setLanguage(sub.language.ifBlank { "fr" })
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build(),
            ),
        )
        player.replaceMediaItem(index, builder.build())
        player.seekTo(index, position)
        player.prepare()
        player.playWhenReady = true
    }
    fun chooseSub(position: Int) {
        val index = player.currentMediaItemIndex
        val clip = clips.getOrNull(index) ?: return
        if (clip.kind != ClipKind.FEATURE || clip.subs.isEmpty()) {
            notice = "Aucun sous-titre à choisir"
            return
        }
        val sub = if (position <= 0) null else clip.subs.getOrNull(position - 1)
        val wanted = sub?.id ?: 0
        if (wanted == subId(index, clip)) {
            notice = "Sous-titres déjà sur : " + subLabel(clip, position)
            return
        }
        val label = subLabel(clip, position)
        val inStream = clip.direct && reloadedUrl[index] == null
        if (inStream && sub == null) {
            pickedSub[index] = 0
            sidecarSub.remove(index)
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            scope.launch { onSubtitleChosen(clip, 0) }
            notice = "Sous-titres : désactivés"
            return
        }
        if (inStream && sub != null && sub.url.isNotBlank()) {
            pickedSub[index] = wanted
            sidecarSub[index] = wanted
            burnedSub[index] = 0
            notice = "Sous-titres : $label…"
            scope.launch { onSubtitleChosen(clip, wanted) }
            reloadWithSidecar(index, clip, sub)
            return
        }
        if (inStream && sub != null) {
            val track = pickTrack(player, C.TRACK_TYPE_TEXT) { format ->
                val forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
                sameLanguage(format.language, sub.language) && forced == sub.forced && sameCodec(format, sub.codec)
            } ?: pickTrack(player, C.TRACK_TYPE_TEXT) { format ->
                val forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
                sameLanguage(format.language, sub.language) && forced == sub.forced
            }
            if (track != null) {
                pickedSub[index] = wanted
                sidecarSub.remove(index)
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(track.group, listOf(track.index)))
                    .build()
                scope.launch { onSubtitleChosen(clip, wanted) }
                notice = "Sous-titres : $label"
                return
            }
        }
        // Flux converti, ou sous-titre que la TV ne lit pas (images PGS…) : le serveur le grave dans l'image, à la même position.
        val factory = clip.streamFor
        if (factory == null) {
            notice = "Choix de sous-titres impossible dans ce flux"
            return
        }
        val audio = pickedAudio[index] ?: clip.audioStart
        val message = if (sub == null) "Sous-titres : désactivés…" else "Sous-titres : $label…"
        val url = factory(audio, wanted)
        // Arrêt du flux, choix enregistré sur le serveur (sinon sa piste « sélectionnée » revient dans le flux), puis nouveau flux à la même position.
        syncedRelaunch(index, clip, url, message, -1, "", "sous-titres → ${if (sub == null) "aucun" else label} (#$wanted)", wanted)
        pickedSub[index] = wanted
        burnedSub[index] = wanted
        sidecarSub.remove(index)
    }
    // Diagnostic des sous-titres pour le panneau « i » : ce qui est demandé à Plex, ce que reçoit ExoPlayer, l'état du bouton ST.
    fun subDiag(): String {
        val index = player.currentMediaItemIndex
        val clip = clips.getOrNull(index) ?: return ""
        val uri = player.currentMediaItem?.localConfiguration?.uri
        val wantedKeys = setOf("subtitleStreamID", "subtitles", "videoResolution", "maxVideoBitrate", "directStream", "mediaIndex", "audioStreamID", "videoCodec", "audioCodec", "maxAudioChannels")
        val asked = uri?.query.orEmpty().split("&").filter { it.substringBefore("=") in wantedKeys }.joinToString(" ")
        val lines = mutableListOf<String>()
        lines += "Appli ${tv.plexiplay.plex.PlexHeaders.VERSION} · ExoPlayer · " + (if (clip.direct && reloadedUrl[index] == null) "direct" else "converti") +
            (if (reloadedUrl[index] != null) " (relancé)" else "")
        lines += "Demande Plex : " + asked.ifBlank { "aucun paramètre (lecture directe)" }
        val eff = effective(index)
        lines += "ST appli : départ=${clip.subStart} choisi=${pickedSub[index]} incrusté=${burnedSub[index]} annexe=${sidecarSub[index]} " +
            "mode=${eff?.textMode} liste=${clip.subs.size} message=\"$notice\""
        val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val texts = mutableListOf<String>()
        for (group in player.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                texts += "  ${format.language ?: "?"} ${format.sampleMimeType ?: format.codecs ?: "?"} f=${format.selectionFlags}" +
                    (if (group.isTrackSelected(i)) " SEL" else "") + (if (!group.isTrackSupported(i)) " NON-LU" else "")
            }
        }
        val video = player.videoFormat
        if (video != null) {
            val color = video.colorInfo?.let { " transfert=${it.colorTransfer} bits=${it.lumaBitdepth}" }.orEmpty()
            lines += "Vidéo reçue : ${video.sampleMimeType} ${video.codecs.orEmpty()} ${video.width}×${video.height} " +
                "${if (video.frameRate > 0f) "%.2f i/s".format(video.frameRate) else ""} ${rateLabel(video.bitrate.toLong().coerceAtLeast(0L))}$color"
        }
        val software = decoderName.contains("google", true) || decoderName.contains("c2.android", true) || decoderName.contains("sw", true)
        val counters = player.videoDecoderCounters?.also { it.ensureUpdated() }
        lines += "Décodeur : ${decoderName.ifBlank { "?" }}" + (if (decoderName.isNotBlank()) (if (software) " (logiciel ?)" else " (matériel)") else "")
        lines += "Images : affichées=${counters?.renderedOutputBufferCount ?: 0} perdues=${counters?.droppedBufferCount ?: 0} " +
            "ignorées=${counters?.skippedOutputBufferCount ?: 0} · coupures=$rebuffers · tampon=${player.totalBufferedDuration / 1000} s"
        lines += "ExoPlayer texte : ${if (disabled) "désactivé" else "actif"} · ${texts.size} piste(s)"
        lines += texts.take(6)
        lines += "Audio : " + clip.audios.joinToString(" ") { "#${it.id}=${it.language.ifBlank { "?" }}" } +
            " · demandé=#${pickedAudio[index]} · lu=${selectedAudioLanguage().ifBlank { "?" }}"
        if (lastChange.isNotBlank()) lines += "Dernier changement : $lastChange"
        val stateName = when (player.playbackState) {
            Player.STATE_IDLE -> "inactif"
            Player.STATE_BUFFERING -> "préparation"
            Player.STATE_READY -> "prêt"
            else -> "terminé"
        }
        lines += "Lecteur : $stateName · lecture voulue=${player.playWhenReady} · image reçue=${firstFrame.value}" +
            (if (lastError.isNotBlank()) " · erreur : $lastError" else "")
        if (probe.isNotBlank()) lines += "Test du flux : $probe"
        lines += clip.diag
        return lines.joinToString("\n")
    }
    // Un clic sur ST : sous-titre suivant (Sans → chaque piste → Sans). Le changement est appliqué quand les clics s'arrêtent.
    fun cycleSub() {
        val index = player.currentMediaItemIndex
        val clip = clips.getOrNull(index) ?: return
        if (clip.kind != ClipKind.FEATURE || clip.subs.isEmpty()) {
            notice = cycleText(player)
            return
        }
        val next = ((pendingSub ?: subPosition(index, clip)) + 1) % (clip.subs.size + 1)
        pendingSub = next
        notice = "Sous-titres : " + subLabel(clip, next)
    }
    fun openSubMenu(fromLongPress: Boolean = true) {
        val index = player.currentMediaItemIndex
        val clip = clips.getOrNull(index) ?: return
        if (clip.kind != ClipKind.FEATURE || clip.subs.isEmpty()) {
            notice = "Aucun sous-titre à choisir"
            return
        }
        menuIndex = subPosition(index, clip)
        shown.value = false
        // Appui long : le relâchement de la touche ne doit pas valider le choix. Appui bref : la touche est déjà relâchée.
        swallowOkUp = fromLongPress
        subMenu = true
    }
    LaunchedEffect(pendingSub) {
        val target = pendingSub ?: return@LaunchedEffect
        delay(900)
        pendingSub = null
        chooseSub(target)
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshAudioTag()
                val clip = clips.getOrNull(player.currentMediaItemIndex)
                status = clip?.label ?: ""
                selection[0] = -1
                selection[1] = 0
            }
            override fun onTracksChanged(tracks: Tracks) {
                refreshAudioTag()
                val index = player.currentMediaItemIndex
                val clip = effective(index) ?: return
                if (selection[0] == index) return
                selection[1] += 1
                if (!exoTracksReady(tracks, clip) && selection[1] < 4) return
                runCatching { applyTracks(player, clip) }
                selection[0] = index
                selection[1] = 0
            }
            override fun onPlayerError(error: PlaybackException) {
                val refused = generateSequence<Throwable>(error) { it.cause }
                    .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                val where = refused?.let { " · refus HTTP ${it.responseCode} sur ${it.dataSpec.uri.path}" }.orEmpty()
                lastError = "${error.errorCodeName} ${error.message.orEmpty().take(60)}$where"
                // Flux converti pas encore prêt à la position demandée (Plex répond 404 au segment) : nouvelle tentative après 3 s.
                val current = clips.getOrNull(player.currentMediaItemIndex)
                if (refused?.responseCode == 404 && current?.kind == ClipKind.FEATURE && !firstFrame.value && retries < 5) {
                    retries += 1
                    lastChange = "nouvelle tentative $retries/5 après refus 404 (le serveur n'a pas encore ce segment)"
                    scope.launch {
                        delay(3000)
                        player.prepare()
                        player.playWhenReady = true
                    }
                    return
                }
                if (pending != null) {
                    failChange("erreur ${error.errorCodeName}")
                    return
                }
                val clip = clips.getOrNull(player.currentMediaItemIndex) ?: return
                val decoderTrouble = error.errorCode in setOf(
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                )
                if (clip.kind == ClipKind.FEATURE && !compat && decoderTrouble) {
                    onCompat()
                    return
                }
                if (clip.kind != ClipKind.FEATURE && player.hasNextMediaItem()) {
                    notice = "Bonus non lu, le film continue"
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.playWhenReady = true
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_ENDED) return
                val clip = clips.getOrNull(player.currentMediaItemIndex) ?: return
                // Lecture complète d'un film : retour à l'écran d'où il a été lancé (le rapport « arrêté » part à la sortie).
                if (clip.kind == ClipKind.FEATURE && clip.closeOnEnd && !player.hasNextMediaItem()) onExit()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                val clip = clips.getOrNull(player.currentMediaItemIndex) ?: return
                if (clip.kind != ClipKind.FEATURE) return
                onProgress(clip.ratingKey, if (isPlaying) "playing" else "paused", player.currentPosition, player.duration)
            }
        }
        player.addListener(listener)
        var wasReady = false
        fun diagDecoder(name: String) { decoderName = name }
        val stats = object : AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long,
            ) {
                bitsPerSecond = bitrateEstimate
            }

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                diagDecoder(decoderName)
            }

            override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
                if (state == Player.STATE_READY) wasReady = true
                if (state == Player.STATE_BUFFERING && wasReady) {
                    wasReady = false
                    rebuffers += 1
                }
            }
        }
        player.addAnalyticsListener(stats)
        onDispose {
            val clip = clips.getOrNull(player.currentMediaItemIndex)
            if (clip?.kind == ClipKind.FEATURE) {
                onProgress(clip.ratingKey, "stopped", player.currentPosition, player.duration)
            }
            player.removeListener(listener)
            player.removeAnalyticsListener(stats)
            player.release()
        }
    }
    DisposableEffect(Unit) {
        onRegisterBack {
            if (shown.value) {
                shown.value = false
                true
            } else {
                false
            }
        }
        onDispose { onRegisterBack(null) }
    }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
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
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            player.videoFormat?.let { format ->
                if (format.width > 0) pictureWidth = format.width
                if (format.height > 0) pictureHeight = format.height
            }
            delay(400)
        }
    }

    // « OK invisible » : certaines TV n'actualisent la taille de la fenêtre vidéo qu'au prochain changement
    // d'interface. On décale la vidéo d'un pixel puis on la remet, après la première image.
    LaunchedEffect(firstFrame.value, player.currentMediaItemIndex) {
        if (!firstFrame.value || !formatFix) return@LaunchedEffect
        for (wait in longArrayOf(800, 1200, 2000)) {
            delay(wait)
            nudge += 1
            delay(150)
            nudge += 1
        }
    }

    // Rapport régulier au serveur pendant le film (le serveur en affiche la session tant qu'il en reçoit).
    LaunchedEffect(player) {
        while (true) {
            val clip = clips.getOrNull(player.currentMediaItemIndex)
            if (clip != null && clip.kind == ClipKind.FEATURE && player.playbackState == Player.STATE_READY) {
                onProgress(clip.ratingKey, if (player.isPlaying) "playing" else "paused", player.currentPosition, player.duration)
            }
            delay(1000)
        }
    }

    // Surveillance : si le film n'avance pas après 20 s, on bascule seul en mode compatible.
    LaunchedEffect(player) {
        if (compat) return@LaunchedEffect
        // Film relancé après un changement de piste : pas de retour automatique au mode compatible (le « i » doit montrer le vrai flux).
        if (clips.any { it.expectAudio.isNotBlank() }) return@LaunchedEffect
        delay(25000)
        val clip = clips.getOrNull(player.currentMediaItemIndex)
        if (clip?.kind == ClipKind.FEATURE && player.playWhenReady && !firstFrame.value) onCompat()
    }

    // Vérification d'un changement de piste en flux converti : le nouveau flux doit démarrer avec la langue demandée.
    LaunchedEffect(pending) {
        val change = pending ?: return@LaunchedEffect
        var waited = 0
        while (waited < 30_000) {
            delay(500)
            waited += 500
            if (pending !== change) return@LaunchedEffect
            if (player.playbackState != Player.STATE_READY) continue
            val played = selectedAudioLanguage()
            val elapsed = (android.os.SystemClock.elapsedRealtime() - change.startedAt) / 1000
            if (change.wantLang.isBlank() || played.isBlank() || lang2(played) == change.wantLang) {
                pending = null
                lastChange = "${change.what} · OK après $elapsed s · piste lue ${played.ifBlank { "?" }}"
            } else {
                failChange("piste lue ${lang2(played)} au lieu de ${change.wantLang}")
            }
            return@LaunchedEffect
        }
        failChange("flux non prêt après 30 s")
    }
    // Film relancé après un changement de piste : la langue jouée doit être celle demandée.
    LaunchedEffect(Unit) {
        val expected = lang2(clips.firstOrNull { it.kind == ClipKind.FEATURE }?.expectAudio)
        if (expected.isBlank()) return@LaunchedEffect
        var waited = 0
        while (waited < 30_000) {
            delay(500)
            waited += 500
            if (player.playbackState != Player.STATE_READY) continue
            val played = selectedAudioLanguage()
            if (played.isBlank() || lang2(played) == expected) {
                lastChange = "relance complète · OK · piste lue ${played.ifBlank { "?" }}"
            } else {
                lastChange = "relance complète · ÉCHEC (piste lue ${lang2(played)} au lieu de $expected)"
                notice = "Impossible en transcodant. Veuillez choisir vos préférences sur la page de présentation."
            }
            return@LaunchedEffect
        }
        lastChange = "relance complète · flux non prêt après 30 s"
        notice = "Impossible en transcodant. Veuillez choisir vos préférences sur la page de présentation."
    }
    // Aucune image au bout de 8 s : test direct de l'adresse du flux (réponse de Plex), affiché dans le « i ».
    LaunchedEffect(player) {
        delay(8000)
        if (firstFrame.value) return@LaunchedEffect
        val index = player.currentMediaItemIndex
        val uri = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: return@LaunchedEffect
        if (!uri.startsWith("http")) return@LaunchedEffect
        probe = "en cours…"
        probe = withContext(kotlinx.coroutines.Dispatchers.IO) { probeStream(uri) }
    }
    LaunchedEffect(switching) {
        if (!switching) return@LaunchedEffect
        var waited = 0
        delay(1500)
        while (waited < 90_000) {
            delay(300)
            waited += 300
            val failed = retries >= 5 && player.playbackState == Player.STATE_IDLE
            if (player.isPlaying || failed) {
                switching = false
                return@LaunchedEffect
            }
        }
        switching = false
    }
    LaunchedEffect(infoVisible) {
        while (infoVisible) {
            tick += 1
            delay(1000)
        }
    }
    LaunchedEffect(notice) {
        if (notice.isEmpty()) return@LaunchedEffect
        delay(if (notice.startsWith("Impossible")) 9000L else 4000L)
        notice = ""
    }

    fun reveal() {
        shown.value = true
        pulse += 1
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
                    val menuClip = clips.getOrNull(player.currentMediaItemIndex)
                    val count = if (audioMenu) menuClip?.audios?.size ?: 0 else (menuClip?.subs?.size ?: 0) + 1
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
                            player.seekTo((player.currentPosition - 30_000).coerceAtLeast(0))
                            true
                        }
                    }
                    Key.DirectionRight -> {
                        if (shown.value) {
                            pulse += 1
                            false
                        } else {
                            player.seekTo(player.currentPosition + 30_000)
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
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    isFocusable = false
                    // Sous-titres : texte blanc sur un bandeau noir semi-transparent, léger contour ; styles intégrés ignorés.
                    subtitleView?.apply {
                        setApplyEmbeddedStyles(false)
                        setStyle(
                            androidx.media3.ui.CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                0xA6000000.toInt(),
                                android.graphics.Color.TRANSPARENT,
                                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null,
                            ),
                        )
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    resizeMode = when (aspect) {
                        AspectMode.FULL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectMode.R4_3, AspectMode.R16_9, AspectMode.AUTO -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    // Certains MKV déclarent une « taille d'affichage » erronée (ex. 16:9 pour une image 2,4:1) :
                    // en HD/UHD les pixels sont carrés, on affiche donc l'image avec ses vraies proportions.
                    val view = this
                    // Rapport imposé : réappliqué en continu (le composant vidéo peut remettre l'ancien) et
                    // avec une mise en page forcée, sinon la TV garde l'ancien affichage d'une image immobile.
                    val locked = floatArrayOf(0f)
                    var ticks = 0
                    val enforce = object : Runnable {
                        override fun run() {
                            if (!view.isAttachedToWindow) return
                            val target = locked[0]
                            if (target > 0f) {
                                val frame = view.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
                                frame?.setAspectRatio(target)
                                if (ticks < 8) {
                                    frame?.requestLayout()
                                    view.requestLayout()
                                    view.invalidate()
                                }
                            }
                            ticks += 1
                            view.postDelayed(this, if (ticks < 8) 400L else 1500L)
                        }
                    }
                    view.post(enforce)
                    player.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            firstFrame.value = true
                            ticks = 0
                        }

                        override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                            if (size.width <= 0 || size.height <= 0) return
                            val ratio = size.pixelWidthHeightRatio
                            val clip = effective(player.currentMediaItemIndex)
                            val frame = size.width.toFloat() / size.height
                            val source = clip?.sourceAspect ?: 0f
                            // Flux converti par le serveur dont les proportions ne sont pas celles du film : on impose celles du film.
                            val squeezed = clip != null && !clip.direct && source > 0f &&
                                kotlin.math.abs(frame - source) / source > 0.05f
                            val wrong = size.width >= 1920 && kotlin.math.abs(ratio - 1f) > 0.1f
                            aspectNote = (if (clip?.direct == false) "Flux converti par le serveur · " else "Lecture directe · ") +
                                "image ${size.width}×${size.height}, pixels ${"%.2f".format(ratio)}" +
                                (if (source > 0f) ", film ${"%.2f".format(source)}:1" else "") +
                                (if (!formatFix) " · correction désactivée" else if (squeezed || wrong) " (proportions corrigées)" else " · correction activée")
                            locked[0] = if (!formatFix) 0f else if (squeezed) source else if (wrong) frame else frame * ratio
                            ticks = 0
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize().padding(bottom = if (nudge % 2 == 1) 1.dp else 0.dp),
        )
        if (notice.isNotEmpty()) {
            Text(
                notice,
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            )
        }
        if (switching) {
            Text(
                "Basculement, veuillez patienter quelques secondes…",
                color = Color.White,
                fontSize = 26.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 28.dp, vertical = 16.dp),
            )
        }
        if (audioMenu) {
            val menuClip = clips.getOrNull(player.currentMediaItemIndex)
            val menuCurrent = pickedAudio[player.currentMediaItemIndex] ?: menuClip?.audios?.firstOrNull { it.current }?.id
            AudioMenu(
                labels = menuClip?.audios?.map { it.label }.orEmpty(),
                selected = menuIndex,
                current = menuClip?.audios?.indexOfFirst { it.id == menuCurrent } ?: -1,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (subMenu) {
            val menuClip = clips.getOrNull(player.currentMediaItemIndex)
            AudioMenu(
                labels = listOf("Sans") + menuClip?.subs?.map { it.label }.orEmpty(),
                selected = menuIndex,
                current = menuClip?.let { subPosition(player.currentMediaItemIndex, it) } ?: -1,
                modifier = Modifier.align(Alignment.Center),
                title = "Sous-titres",
            )
        }
        if (infoVisible && aspectNote.isNotEmpty()) {
            Text(
                aspectNote,
                color = Color(0xFF9AA3AD),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 20.dp),
            )
        }
        if (infoVisible) {
            PlaybackFacts(
                effective(player.currentMediaItemIndex),
                pictureWidth,
                pictureHeight,
                bitsPerSecond,
                Modifier.align(Alignment.TopStart),
            )
        }
        if (infoVisible) {
            // Relu à chaque affichage de la barre (position mise à jour toutes les 400 ms) : le texte suit l'état réel.
            val diagnostic = remember(position, notice, lastChange, lastError, probe, tick, pictureWidth, player.currentMediaItemIndex) { subDiag() }
            SubDiagnostic(diagnostic, Modifier.align(Alignment.TopEnd))
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
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f)),
                    )
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(fraction.coerceAtLeast(0.02f)),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White))
                    }
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
                    PlayerIcon(Glyph.Rew30) { player.seekTo((player.currentPosition - 30_000).coerceAtLeast(0)); reveal() }
                    PlayerIcon(Glyph.Rew10) { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); reveal() }
                    PlayerIcon(if (playing) Glyph.Pause else Glyph.Play, playFocus) {
                        if (player.isPlaying) player.pause() else player.play()
                        reveal()
                    }
                    PlayerIcon(Glyph.Fwd10) { player.seekTo(player.currentPosition + 10_000); reveal() }
                    PlayerIcon(Glyph.Fwd30) { player.seekTo(player.currentPosition + 30_000); reveal() }
                    PlayerIcon(Glyph.Subs, onLongPress = { openSubMenu() }) {
                        // Appui simple : liste des sous-titres du film (plus lisible que le défilement, surtout en flux converti).
                        val subClip = clips.getOrNull(player.currentMediaItemIndex)
                        if (subClip != null && subClip.kind == ClipKind.FEATURE && subClip.subs.isNotEmpty()) {
                            openSubMenu(fromLongPress = false)
                        } else {
                            cycleSub()
                            reveal()
                        }
                    }
                    PlayerIcon(Glyph.Fr, onLongPress = { openAudioMenu() }, label = audioTag) { forceFrench(); reveal() }
                    PlayerIcon(Glyph.Info) { infoVisible = !infoVisible; reveal() }
                    PlayerIcon(Glyph.Skip) {
                        if (player.currentMediaItemIndex < clips.lastIndex) player.seekToNextMediaItem()
                        reveal()
                    }
                    PlayerIcon(Glyph.Stop) { onExit() }
                }
            }
        }
    }
}

@Composable
internal fun PlayerIcon(glyph: Glyph, requester: FocusRequester? = null, onLongPress: (() -> Unit)? = null, label: String? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var pressing by remember { mutableStateOf(false) }
    var fired by remember { mutableStateOf(false) }
    if (onLongPress != null) {
        // Appui maintenu 2 secondes : action longue ; appui bref : action normale au relâchement.
        LaunchedEffect(pressing) {
            if (pressing) {
                delay(2000)
                fired = true
                pressing = false
                onLongPress()
            }
        }
    }
    val ink = Color(0xFF121418)
    Box(
        Modifier
            .size(if (glyph == Glyph.Play || glyph == Glyph.Pause) 54.dp else 44.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                val ok = it.key == Key.DirectionCenter || it.key == Key.Enter
                if (onLongPress != null && ok) {
                    if (it.type == KeyEventType.KeyDown) {
                        if (it.nativeKeyEvent.repeatCount == 0) {
                            fired = false
                            pressing = true
                        }
                        true
                    } else if (it.type == KeyEventType.KeyUp) {
                        val short = pressing
                        pressing = false
                        fired = false
                        if (short) onClick()
                        true
                    } else false
                } else if (it.type == KeyEventType.KeyUp && ok) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(if (glyph == Glyph.Play || glyph == Glyph.Pause) 26.dp else 22.dp)) {
            val color = if (focused) ink else Color.White
            val stroke = size.minDimension * 0.09f
            when (glyph) {
                Glyph.Play -> drawPath(
                    Path().apply {
                        moveTo(size.width * 0.32f, size.height * 0.18f)
                        lineTo(size.width * 0.82f, size.height * 0.5f)
                        lineTo(size.width * 0.32f, size.height * 0.82f)
                        close()
                    },
                    color,
                )
                Glyph.Pause -> {
                    drawRoundRect(color, Offset(size.width * 0.22f, size.height * 0.16f), Size(size.width * 0.16f, size.height * 0.68f), CornerRadius(2f, 2f))
                    drawRoundRect(color, Offset(size.width * 0.60f, size.height * 0.16f), Size(size.width * 0.16f, size.height * 0.68f), CornerRadius(2f, 2f))
                }
                Glyph.Stop -> drawRoundRect(
                    color,
                    topLeft = Offset(size.width * 0.22f, size.height * 0.22f),
                    size = Size(size.width * 0.56f, size.height * 0.56f),
                    cornerRadius = CornerRadius(2f, 2f),
                )
                Glyph.Skip -> {
                    drawPath(
                        Path().apply {
                            moveTo(size.width * 0.14f, size.height * 0.18f)
                            lineTo(size.width * 0.62f, size.height * 0.5f)
                            lineTo(size.width * 0.14f, size.height * 0.82f)
                            close()
                        },
                        color,
                    )
                    drawRoundRect(color, Offset(size.width * 0.72f, size.height * 0.18f), Size(size.width * 0.14f, size.height * 0.64f), CornerRadius(1.5f, 1.5f))
                }
                Glyph.Hide -> {
                    drawPath(
                        Path().apply {
                            moveTo(size.width * 0.06f, size.height * 0.5f)
                            quadraticBezierTo(size.width * 0.5f, size.height * 0.16f, size.width * 0.94f, size.height * 0.5f)
                            quadraticBezierTo(size.width * 0.5f, size.height * 0.84f, size.width * 0.06f, size.height * 0.5f)
                            close()
                        },
                        color,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawCircle(color, radius = size.minDimension * 0.11f)
                    drawLine(
                        color,
                        Offset(size.width * 0.16f, size.height * 0.8f),
                        Offset(size.width * 0.84f, size.height * 0.2f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
                Glyph.Info -> {
                    drawCircle(color, radius = size.minDimension * 0.42f, style = Stroke(width = stroke))
                    drawCircle(color, radius = size.minDimension * 0.07f, center = Offset(center.x, size.height * 0.32f))
                    drawLine(
                        color,
                        Offset(center.x, size.height * 0.46f),
                        Offset(center.x, size.height * 0.74f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
                Glyph.Subs, Glyph.Fr -> Unit
                Glyph.Rew10, Glyph.Fwd10, Glyph.Rew30, Glyph.Fwd30 -> {
                    val forward = glyph == Glyph.Fwd10 || glyph == Glyph.Fwd30
                    val pad = stroke
                    val arc = Size(size.width - pad * 2, size.height - pad * 2)
                    val start = if (forward) 55f else 125f
                    val sweep = if (forward) 250f else -250f
                    drawArc(
                        color,
                        start,
                        sweep,
                        false,
                        Offset(pad, pad),
                        arc,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    val end = Math.toRadians((start + sweep).toDouble())
                    val radius = arc.minDimension / 2f
                    val x = center.x + radius * cos(end).toFloat()
                    val y = center.y + radius * sin(end).toFloat()
                    val direction = if (forward) 1f else -1f
                    val tx = -sin(end).toFloat() * direction
                    val ty = cos(end).toFloat() * direction
                    val head = size.minDimension * 0.2f
                    val px = -ty
                    val py = tx
                    drawPath(
                        Path().apply {
                            moveTo(x + tx * head * 0.15f, y + ty * head * 0.15f)
                            lineTo(x - tx * head + px * head * 0.62f, y - ty * head + py * head * 0.62f)
                            lineTo(x - tx * head - px * head * 0.62f, y - ty * head - py * head * 0.62f)
                            close()
                        },
                        color,
                    )
                }
            }
        }
        if (glyph == Glyph.Rew10 || glyph == Glyph.Fwd10 || glyph == Glyph.Rew30 || glyph == Glyph.Fwd30) {
            Text(
                if (glyph == Glyph.Rew30 || glyph == Glyph.Fwd30) "30" else "10",
                color = if (focused) ink else Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (glyph == Glyph.Subs || glyph == Glyph.Fr) {
            Text(
                label ?: if (glyph == Glyph.Fr) "FR" else "ST",
                color = if (focused) ink else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        // Bulle d'aide au-dessus du bouton quand il a le focus et qu'un appui long ouvre une liste (FR, ST).
        if (focused && onLongPress != null) {
            val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
            androidx.compose.ui.window.Popup(
                popupPositionProvider = object : androidx.compose.ui.window.PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: androidx.compose.ui.unit.IntRect,
                        windowSize: androidx.compose.ui.unit.IntSize,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                        popupContentSize: androidx.compose.ui.unit.IntSize,
                    ) = androidx.compose.ui.unit.IntOffset(
                        (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                        anchorBounds.top - popupContentSize.height - gap,
                    )
                },
            ) {
                Text(
                    "Maintenir pour lister",
                    color = Color(0xFF121418),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Color(0xFFF4F1EA), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

internal fun clock(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private fun subtitleMime(codec: String): String = when (codec.lowercase()) {
    "ass", "ssa" -> MimeTypes.TEXT_SSA
    "vtt" -> MimeTypes.TEXT_VTT
    else -> MimeTypes.APPLICATION_SUBRIP
}

private fun exoTracksReady(tracks: Tracks, clip: ClipPlay): Boolean {
    val audio = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
    val text = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
    if (clip.audioLanguage.isNotBlank() && !audio) return false
    if (clip.textMode != "off" && !text) return false
    return tracks.groups.isNotEmpty()
}

/** Changement de piste en attente de vérification (flux converti) : de quoi vérifier la langue lue et revenir au flux précédent. */
private class PendingChange(
    val index: Int,
    val what: String,
    val wantLang: String,
    val position: Long,
    val prevUrl: String,
    val prevAudio: Int?,
    val prevSub: Int?,
    val prevBurn: Int?,
    val startedAt: Long,
)

/** Code de langue sur deux lettres, codes à trois lettres (fra/fre, deu/ger…) ramenés à la même valeur ; vide si inconnu. */
private fun lang2(code: String?): String {
    val value = code?.trim()?.lowercase()?.substringBefore('-').orEmpty()
    if (value.isBlank() || value == "und") return ""
    return when (value) {
        "fra", "fre" -> "fr"
        "eng" -> "en"
        "deu", "ger" -> "de"
        "spa" -> "es"
        "ita" -> "it"
        "por" -> "pt"
        "nld", "dut" -> "nl"
        "jpn" -> "ja"
        "zho", "chi" -> "zh"
        "kor" -> "ko"
        "rus" -> "ru"
        "ara" -> "ar"
        else -> value.take(2)
    }
}

private fun sameLanguage(track: String?, wanted: String): Boolean {
    if (wanted.isBlank()) return false
    val value = track?.lowercase()?.substringBefore('-').orEmpty()
    if (value.isBlank() || value == "und") return false
    return value == wanted || value.take(2) == wanted.take(2)
}

private fun sameCodec(format: Format, codec: String): Boolean {
    if (codec.isBlank()) return true
    val mime = format.sampleMimeType.orEmpty().lowercase()
    val codecs = format.codecs.orEmpty().lowercase()
    val key = codec.lowercase()
    return mime.contains(key) || codecs.contains(key) ||
        (key == "eac3" && (mime.contains("eac3") || codecs.contains("ec-3") || mime.contains("ec-3"))) ||
        (key == "ac3" && (mime.contains("ac3") || codecs.contains("ac-3"))) ||
        ((key == "dca" || key == "dts") && (mime.contains("dts") || codecs.contains("dts"))) ||
        (key == "truehd" && (mime.contains("truehd") || mime.contains("true-hd"))) ||
        (key == "srt" && (mime.contains("subrip") || mime.contains("srt"))) ||
        (key == "pgs" && (mime.contains("pgs") || codecs.contains("pgs")))
}

private data class TrackPick(val group: androidx.media3.common.TrackGroup, val index: Int)

private fun pickTrack(player: ExoPlayer, type: Int, predicate: (Format) -> Boolean): TrackPick? {
    for (group in player.currentTracks.groups) {
        if (group.type != type) continue
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            if (predicate(group.getTrackFormat(index))) return TrackPick(group.mediaTrackGroup, index)
        }
    }
    return null
}

private fun applyTracks(player: ExoPlayer, clip: ClipPlay) {
    val builder = player.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
    if (clip.audioLanguage.isNotBlank()) {
        val audio = pickTrack(player, C.TRACK_TYPE_AUDIO) { format ->
            sameLanguage(format.language, clip.audioLanguage) && sameCodec(format, clip.audioCodec)
        } ?: pickTrack(player, C.TRACK_TYPE_AUDIO) { format ->
            sameLanguage(format.language, clip.audioLanguage)
        }
        if (audio != null) {
            builder.setOverrideForType(TrackSelectionOverride(audio.group, listOf(audio.index)))
        } else {
            builder.setPreferredAudioLanguage(clip.audioLanguage)
        }
    }
    if (clip.textMode == "off") {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        val wantedForced = clip.textMode == "forced"
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        val text = pickTrack(player, C.TRACK_TYPE_TEXT) { format ->
            val forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
            sameLanguage(format.language, clip.textLanguage) && forced == wantedForced && sameCodec(format, clip.subtitleCodec)
        } ?: pickTrack(player, C.TRACK_TYPE_TEXT) { format ->
            val forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
            sameLanguage(format.language, clip.textLanguage) && forced == wantedForced
        }
        if (text != null) {
            builder.setOverrideForType(TrackSelectionOverride(text.group, listOf(text.index)))
        } else {
            builder.setPreferredTextLanguage(clip.textLanguage.ifBlank { "fr" })
            if (!wantedForced) builder.setIgnoredTextSelectionFlags(C.SELECTION_FLAG_FORCED)
        }
    }
    player.trackSelectionParameters = builder.build()
}

private fun selectForced(player: ExoPlayer, language: String) {
    val wanted = language.ifBlank { "fr" }
    for (group in player.currentTracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (index in 0 until group.length) {
            val format = group.getTrackFormat(index)
            val forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
            val code = format.language?.take(2)?.lowercase().orEmpty()
            if (forced && (code.isBlank() || code == wanted)) {
                val already = player.trackSelectionParameters.overrides.values.any { override ->
                    override.mediaTrackGroup == group.mediaTrackGroup && override.trackIndices.contains(index)
                }
                if (already) return
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
                return
            }
        }
    }
}

private fun textLabel(format: Format): String {
    val lang = format.language?.substringBefore('-').orEmpty()
    val name = if (lang.isBlank() || lang == "und") {
        format.label.orEmpty().ifBlank { "Sous-titre" }
    } else {
        java.util.Locale(lang).getDisplayLanguage(java.util.Locale.FRENCH).replaceFirstChar { it.uppercase() }.ifBlank { lang.uppercase() }
    }
    return if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0) "$name (forcés)" else name
}

/** Bouton ST : Sans → chaque piste de sous-titres → Sans. Renvoie le message à afficher. */
private fun cycleText(player: ExoPlayer): String {
    val tracks = mutableListOf<Pair<Tracks.Group, Int>>()
    for (group in player.currentTracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (index in 0 until group.length) {
            if (group.isTrackSupported(index)) tracks += group to index
        }
    }
    if (tracks.isEmpty()) return "Aucun sous-titre disponible dans ce flux"
    val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    // Seul le choix de sous-titres compte : la liste des réglages contient aussi celui de la piste audio.
    val override = player.trackSelectionParameters.overrides.values.firstOrNull { it.mediaTrackGroup.type == C.TRACK_TYPE_TEXT }
    val currentIndex = if (disabled) -1 else tracks.indexOfFirst { (group, index) ->
        if (override != null) override.mediaTrackGroup == group.mediaTrackGroup && override.trackIndices.contains(index)
        else group.isTrackSelected(index)
    }
    val next = if (currentIndex < 0) 0 else currentIndex + 1
    val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
    val message: String
    if (next >= tracks.size) {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        message = "Sous-titres : désactivés"
    } else {
        val (group, index) = tracks[next]
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
        message = "Sous-titres : " + textLabel(group.getTrackFormat(index))
    }
    player.trackSelectionParameters = builder.build()
    return message
}


/**
 * Test du flux converti (panneau « i ») : liste maître, liste des segments qu'elle annonce, premier segment.
 * Montre le code de réponse de Plex à chaque étape (et son message en cas de refus), sans afficher d'adresse ni de jeton.
 */
private fun probeStream(uri: String): String {
    fun fetch(url: String): Triple<Int, Long, String> {
        val started = android.os.SystemClock.elapsedRealtime()
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        val code = connection.responseCode
        val stream = if (code in 200..399) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { reader ->
            val buffer = CharArray(4000)
            val count = reader.read(buffer)
            if (count > 0) String(buffer, 0, count) else ""
        }.orEmpty()
        return Triple(code, android.os.SystemClock.elapsedRealtime() - started, text)
    }
    fun firstEntry(text: String): String? = text.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
    fun reason(text: String) = " « " + text.take(120).replace("\n", " ") + " »"
    return try {
        val (code, ms, text) = fetch(uri)
        val out = StringBuilder("maître HTTP $code en $ms ms")
        if (code !in 200..399) return out.append(reason(text)).toString()
        val variant = firstEntry(text) ?: return out.append(" · aucune liste annoncée").toString()
        val variantUrl = java.net.URI(uri).resolve(variant).toString()
        val (variantCode, variantMs, variantText) = fetch(variantUrl)
        out.append(" · liste HTTP $variantCode en $variantMs ms")
        if (variantCode !in 200..399) return out.append(reason(variantText)).toString()
        val segment = firstEntry(variantText) ?: return out.append(" · aucun segment annoncé").toString()
        val (segmentCode, segmentMs, segmentText) = fetch(java.net.URI(variantUrl).resolve(segment).toString())
        out.append(" · segment HTTP $segmentCode en $segmentMs ms")
        if (segmentCode !in 200..399) out.append(reason(segmentText))
        out.toString()
    } catch (e: Exception) {
        "ÉCHEC ${e.javaClass.simpleName} ${e.message.orEmpty().take(80)}"
    }
}
