package tv.plexiplay.ui.detail

import tv.plexiplay.ui.theme.screenBackground
import tv.plexiplay.ui.theme.themedButton
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.plexiplay.R
import tv.plexiplay.data.SeriesChoice
import tv.plexiplay.player.PlaybackSettings
import tv.plexiplay.plex.PlexClient
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.plex.model.ServerEndpoint
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.FocusRing
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper
import tv.plexiplay.ui.theme.Signal

@OptIn(UnstableApi::class)
@Composable
fun MovieDetailScreen(
    seed: Metadata,
    client: PlexClient,
    server: ServerEndpoint,
    settings: PlaybackSettings,
    onPlay: (Metadata, String, String, String, Boolean) -> Unit,
    onTrailers: (Metadata) -> Unit,
    onClose: () -> Unit,
    notice: String = "",
    promoKey: String? = null,
    themePath: String? = null,
    remembered: SeriesChoice? = null,
    themeVolume: Float = 0.5f,
    posters: tv.plexiplay.ui.shell.PosterStore? = null,
    accountToken: String = "",
) {
    val context = LocalContext.current
    // Distribution : épisodes = principaux de la série puis invités ; films = acteurs du film. Fiche d'un acteur au clic.
    var cast by remember(seed.ratingKey) { mutableStateOf<List<CastEntry>>(emptyList()) }
    var actor by remember(seed.ratingKey) { mutableStateOf<CastEntry?>(null) }
    var person by remember(seed.ratingKey) { mutableStateOf<tv.plexiplay.plex.PersonInfo?>(null) }
    val castFocus = remember { FocusRequester() }
    // Résumé complet : OK sur le texte du résumé ouvre un grand popup ; Retour le referme et rend le focus au résumé.
    var summaryOpen by remember(seed.ratingKey) { mutableStateOf(false) }
    var summaryFocused by remember { mutableStateOf(false) }
    val summaryFocus = remember { FocusRequester() }
    var summaryWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(summaryOpen) {
        if (summaryOpen) {
            summaryWasOpen = true
        } else if (summaryWasOpen) {
            summaryWasOpen = false
            delay(60)
            runCatching { summaryFocus.requestFocus() }
        }
    }
    val roleImages: (String) -> List<String> = { path ->
        when {
            path.isBlank() -> emptyList()
            path.startsWith("http") -> listOf(path)
            else -> client.thumbSources(server, path)
        }
    }
    LaunchedEffect(actor) {
        val open = actor
        person = null
        if (open == null) return@LaunchedEffect
        person = withContext(Dispatchers.IO) {
            runCatching { client.person(server, open.role, accountToken) }.getOrNull() ?: tv.plexiplay.plex.PersonInfo()
        }
    }
    var actorWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(actor) {
        if (actor != null) {
            actorWasOpen = true
        } else if (actorWasOpen) {
            actorWasOpen = false
            delay(60)
            runCatching { castFocus.requestFocus() }
        }
    }
    var movie by remember(seed.ratingKey) { mutableStateOf(seed) }
    var watched by remember(seed.ratingKey) { mutableStateOf(seed.viewCount > 0) }
    var trailerUrl by remember(seed.ratingKey) { mutableStateOf<String?>(null) }
    var themeUrl by remember(seed.ratingKey) { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    val resolutionChoices = MovieFacts.resolutionChoices(movie)
    val audioChoices = MovieFacts.audioChoicesLabeled(movie)
    val subtitleChoices = MovieFacts.subtitleOptions(movie)
    var resolution by remember(seed.ratingKey) { mutableStateOf("native") }
    var audio by remember(seed.ratingKey, audioChoices) {
        val wanted = remembered?.audio?.takeIf { code -> audioChoices.any { it.first == code } }
        mutableStateOf(wanted ?: MovieFacts.defaultAudio(movie, settings.audioLanguage))
    }
    var subtitles by remember(seed.ratingKey, subtitleChoices) {
        mutableStateOf(
            if (remembered != null) {
                MovieFacts.subtitleMatch(movie, remembered.subtitleLanguage, remembered.subtitleForced)
            } else {
                MovieFacts.defaultSubtitle(movie, settings.subtitleLanguage)
            },
        )
    }
    var menu by remember(seed.ratingKey) { mutableStateOf<String?>(null) }
    val playFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    val quiet = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()
    val trailerPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setAudioAttributes(quiet, false)
        }
    }
    val themePlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = themeVolume
            repeatMode = Player.REPEAT_MODE_OFF
            setAudioAttributes(quiet, false)
        }
    }
    LaunchedEffect(themeVolume) { themePlayer.volume = themeVolume }
    DisposableEffect(trailerPlayer, themePlayer) {
        onDispose {
            trailerPlayer.release()
            themePlayer.release()
        }
    }

    LaunchedEffect(seed.ratingKey) {
        val full = withContext(Dispatchers.IO) { runCatching { client.metadata(server, seed.ratingKey) }.getOrNull() } ?: seed
        movie = full
        if (full.type == "episode") {
            val show = full.grandparentRatingKey.takeIf { it.isNotBlank() }?.let { key ->
                withContext(Dispatchers.IO) { runCatching { client.metadata(server, key) }.getOrNull() }
            }
            val main = show?.Role.orEmpty().take(15)
            val guests = full.Role.filter { guest -> main.none { it.tag == guest.tag } }.take(15)
            cast = main.map { CastEntry(it, false) } + guests.map { CastEntry(it, true) }
        } else {
            // Film : les acteurs du film, tous « principaux » (pas d'invités).
            cast = full.Role.take(15).map { CastEntry(it, false) }
        }
        watched = full.viewCount > 0
        themeUrl = client.fileUrl(server, themePath?.takeIf { it.isNotBlank() } ?: full.theme)
        val extra = withContext(Dispatchers.IO) {
            runCatching { client.extras(server, promoKey?.takeIf { it.isNotBlank() } ?: full.ratingKey) }.getOrDefault(emptyList())
                .let { backgroundClip(it) }
        }
        val part = extra?.let { item ->
            val detailed = withContext(Dispatchers.IO) { runCatching { client.metadata(server, item.ratingKey) }.getOrNull() } ?: item
            detailed.Media.firstOrNull()?.Part?.firstOrNull()?.key
        }
        trailerUrl = client.fileUrl(server, part)
    }
    LaunchedEffect(trailerUrl) {
        val url = trailerUrl ?: return@LaunchedEffect
        trailerPlayer.setMediaItem(MediaItem.fromUri(url))
        trailerPlayer.prepare()
        trailerPlayer.playWhenReady = true
    }
    LaunchedEffect(themeUrl) {
        val url = themeUrl ?: return@LaunchedEffect
        themePlayer.setMediaItem(MediaItem.fromUri(url))
        themePlayer.prepare()
        themePlayer.playWhenReady = true
    }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
    LaunchedEffect(menu) {
        if (menu != null) runCatching { menuFocus.requestFocus() } else runCatching { playFocus.requestFocus() }
    }

    val year = if (movie.year > 0) movie.year.toString() else ""
    val length = MovieFacts.duration(movie.duration)
    val metaLine = listOf(year, length).filter { it.isNotBlank() }.joinToString("   ")
    val languages = MovieFacts.languageLabels(movie)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                    if (summaryOpen) summaryOpen = false else if (actor != null) actor = null else if (menu != null) menu = null else onClose()
                    true
                } else false
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (trailerUrl != null) {
                Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.62f).fillMaxHeight()) {
                    AndroidView(
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                val view = LayoutInflater.from(ctx).inflate(R.layout.trailer_view, null) as PlayerView
                                view.player = trailerPlayer
                                view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                addView(
                                    view,
                                    FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Ink,
                        0.46f to Ink,
                        0.66f to Ink.copy(alpha = 0.88f),
                        0.84f to Ink.copy(alpha = 0.28f),
                        1f to Ink.copy(alpha = 0.42f),
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Ink.copy(alpha = 0.72f),
                        0.18f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Ink,
                    ),
                ),
            )
            Column(Modifier.fillMaxWidth(0.56f).fillMaxHeight().padding(start = 36.dp, top = 28.dp, end = 16.dp, bottom = 88.dp)) {
                Text(movie.title, color = Paper, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val episodeLine = if (movie.type == "episode") {
                    listOf(movie.grandparentTitle, if (movie.parentIndex > 0 && movie.index > 0) "S${movie.parentIndex} E${movie.index}" else "")
                        .filter { it.isNotBlank() }
                        .joinToString("  ·  ")
                } else {
                    movie.tagline
                }
                if (episodeLine.isNotBlank()) {
                    Text(episodeLine, color = Color(0xFFC5CDD6), fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
                }
                if (metaLine.isNotBlank()) {
                    Text(metaLine, color = Paper, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
                }
                if (languages.isNotEmpty()) {
                    Text(languages.joinToString("    "), color = Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                val genres = MovieFacts.genres(movie)
                if (genres.isNotBlank()) {
                    Text(genres, color = Paper, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Text(
                    "${MovieFacts.imdb(movie)}    ${MovieFacts.audience(movie)}    ${MovieFacts.stars(movie)}",
                    color = Paper,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    movie.summary.ifBlank { "Résumé indisponible." },
                    color = Color(0xFFD5DBE2),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = if (cast.isNotEmpty() && posters != null) 3 else 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .focusRequester(summaryFocus)
                        .onFocusChanged { summaryFocused = it.isFocused }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                                summaryOpen = true
                                true
                            } else false
                        }
                        .then(
                            if (summaryFocused) Modifier.border(2.dp, FocusRing, RoundedCornerShape(6.dp)) else Modifier,
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .focusable(),
                )
                if (cast.isNotEmpty() && posters != null) {
                    CastRow(
                        cast = cast,
                        posters = posters,
                        thumbs = roleImages,
                        requester = castFocus,
                        onOpen = { actor = it },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (notice.isNotBlank() || note.isNotBlank()) {
                    Text(notice.ifBlank { note }, color = Color(0xFF9AA3AD), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 36.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (movie.viewOffset >= 30_000L) {
                Action("Reprendre à ${resumeLabel(movie.viewOffset)}", playFocus, primary = true) {
                    onPlay(movie, resolution, audio, subtitles, true)
                }
                RestartAction { onPlay(movie, resolution, audio, subtitles, false) }
            } else {
                Action("Lecture", playFocus, primary = true) { onPlay(movie, resolution, audio, subtitles, false) }
            }
            if (movie.type != "episode") Action("Bandes-annonces") { onTrailers(movie) }
            Action(if (watched) "Marqué comme lu" else "Marquer comme lu") {
                val next = !watched
                watched = next
                Thread {
                    val failed = runCatching { client.setWatched(server, movie.ratingKey, next) }.isFailure
                    if (failed) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            note = "Le serveur n'a pas enregistré le statut"
                        }
                    }
                }.start()
            }
            Spacer(Modifier.weight(1f))
            Cycle("Résolution", resolutionChoices.firstOrNull { it.first == resolution }?.second ?: "Native") {
                menu = if (menu == "resolution") null else "resolution"
            }
            Cycle("Audio", audioChoices.firstOrNull { it.first == audio }?.second ?: MovieFacts.languageName(audio)) {
                menu = if (menu == "audio") null else "audio"
            }
            Cycle("Sous-titres", subtitleChoices.firstOrNull { it.id == subtitles }?.label ?: "Sans") {
                menu = if (menu == "subs") null else "subs"
            }
        }
        if (menu != null) {
            val picks = when (menu) {
                "audio" -> audioChoices
                "subs" -> subtitleChoices.map { it.id to it.label }
                else -> resolutionChoices
            }
            ChoiceList(
                picks = picks,
                firstFocus = menuFocus,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 96.dp),
            ) { id ->
                when (menu) {
                    "audio" -> audio = id
                    "subs" -> subtitles = id
                    else -> resolution = id
                }
                menu = null
            }
        }
        if (summaryOpen) {
            SummaryDialog(
                title = movie.title,
                subtitle = if (movie.type == "episode") {
                    listOf(movie.grandparentTitle, if (movie.parentIndex > 0 && movie.index > 0) "S${movie.parentIndex} E${movie.index}" else "")
                        .filter { it.isNotBlank() }
                        .joinToString("  ·  ")
                } else {
                    movie.tagline
                },
                summary = movie.summary,
            )
        }
        val openActor = actor
        if (openActor != null && posters != null) {
            ActorDialog(entry = openActor, info = person, posters = posters, thumbs = roleImages)
        }
    }
}

internal fun backgroundClip(extras: List<Metadata>): Metadata? {
    val promo = extras.filter { it.extraType == 11 || it.extraType == 10 }
    if (promo.isNotEmpty()) return promo.minByOrNull { if (it.duration > 0) it.duration else Long.MAX_VALUE }
    return extras.firstOrNull { it.extraType == 1 || it.type.equals("trailer", true) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChoiceList(
    picks: List<Pair<String, String>>,
    firstFocus: FocusRequester,
    modifier: Modifier,
    onPick: (String) -> Unit,
) {
    Column(
        modifier
            .width(280.dp)
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState())
            .background(Color(0xF01C2128), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp),
    ) {
        picks.forEachIndexed { index, (id, label) ->
            ChoiceRow(label, if (index == 0) firstFocus else null, onClick = { onPick(id) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChoiceRow(label: String, requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Text(
        label,
        color = if (focused) Ink else Paper,
        fontSize = 15.sp,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .bringIntoViewRequester(bring)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) scope.launch { bring.bringIntoView() }
            }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .fillMaxWidth()
            .background(if (focused) Accent else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

internal fun resumeLabel(ms: Long): String {
    val minutes = (ms / 60_000L).toInt()
    return if (minutes >= 60) "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}" else "$minutes min"
}

@Composable
internal fun Action(label: String, requester: FocusRequester? = null, primary: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        primary -> Signal
        focused -> Accent
        else -> Color(0xFF2A333C)
    }
    Text(
        label,
        color = if (primary || focused) Ink else Paper,
        fontSize = 15.sp,
        modifier = Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .themedButton(fill, focused)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Icône « retour au début » ; une bulle « Recommencer » apparaît au-dessus quand elle a le focus. */
@Composable
private fun RestartAction(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill = if (focused) Accent else Color(0xFF2A333C)
    val tint = if (focused) Ink else Paper
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .themedButton(fill, focused)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
            val stroke = 2.4.dp.toPx()
            val c = center
            val r = size.minDimension / 2f - stroke * 1.4f
            withTransform({ scale(-1f, 1f, pivot = c) }) {
                drawArc(
                    color = tint,
                    startAngle = -40f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(c.x - r, c.y - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
                val theta = Math.toRadians(240.0)
                val px = c.x + r * Math.cos(theta).toFloat()
                val py = c.y + r * Math.sin(theta).toFloat()
                val tx = -Math.sin(theta).toFloat()
                val ty = Math.cos(theta).toFloat()
                val nx = Math.cos(theta).toFloat()
                val ny = Math.sin(theta).toFloat()
                val a = stroke * 2.4f
                val head = androidx.compose.ui.graphics.Path().apply {
                    moveTo(px + tx * a, py + ty * a)
                    lineTo(px + nx * a * 0.8f, py + ny * a * 0.8f)
                    lineTo(px - nx * a * 0.8f, py - ny * a * 0.8f)
                    close()
                }
                drawPath(head, tint)
            }
        }
        if (focused) {
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
                    "Recommencer",
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Paper, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun Cycle(title: String, value: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .border(2.dp, if (focused) FocusRing else Color.Transparent, RoundedCornerShape(6.dp))
            .background(Color(0xCC1C2128), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(title, color = Color(0xFF9AA3AD), fontSize = 11.sp)
        Text(value, color = if (focused) Accent else Paper, fontSize = 15.sp)
    }
}
