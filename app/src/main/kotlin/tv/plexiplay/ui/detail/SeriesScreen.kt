package tv.plexiplay.ui.detail

import tv.plexiplay.ui.theme.screenBackground
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.withContext
import tv.plexiplay.R
import tv.plexiplay.plex.PlexClient
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.plex.model.ServerEndpoint
import tv.plexiplay.ui.shell.PosterStore
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.Signal
import tv.plexiplay.ui.theme.FocusRing
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper

/**
 * Fiche d'une série : jaquette officielle, résumé, puis les saisons.
 * Une saison ouverte affiche ses épisodes, avec le même fond (promo + générique de la série).
 */
@OptIn(UnstableApi::class)
@Composable
fun SeriesScreen(
    seed: Metadata,
    season: Metadata?,
    client: PlexClient,
    server: ServerEndpoint,
    posters: PosterStore,
    onSeason: (Metadata?) -> Unit,
    onEpisode: (Metadata) -> Unit,
    onPlay: (Metadata, Metadata) -> Unit,
    onClose: () -> Unit,
    notice: String = "",
    themeVolume: Float = 0.5f,
) {
    val context = LocalContext.current
    var show by remember(seed.ratingKey) { mutableStateOf(seed) }
    var seasons by remember(seed.ratingKey) { mutableStateOf<List<Metadata>>(emptyList()) }
    var next by remember(seed.ratingKey) { mutableStateOf<Metadata?>(null) }
    var watched by remember(seed.ratingKey) { mutableStateOf(false) }
    var episodes by remember(season?.ratingKey) { mutableStateOf<List<Metadata>>(emptyList()) }
    var trailerUrl by remember(seed.ratingKey) { mutableStateOf<String?>(null) }
    var themeUrl by remember(seed.ratingKey) { mutableStateOf<String?>(null) }
    var art by remember(seed.ratingKey) { mutableStateOf<Bitmap?>(null) }
    var lastSeason by remember(seed.ratingKey) { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    val playFocus = remember { FocusRequester() }
    val seasonFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val mark: (String, Boolean, String) -> Unit = { key, state, message ->
        note = message
        Thread {
            val failed = runCatching { client.setWatched(server, key, state) }.isFailure
            if (failed) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    note = "Le serveur n'a pas enregistré le statut"
                }
            }
        }.start()
    }

    val quiet = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
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
        val full = withContext(Dispatchers.IO) {
            runCatching { client.metadata(server, seed.ratingKey) }.getOrNull()
        } ?: seed
        show = full
        watched = full.leafCount > 0 && full.viewedLeafCount >= full.leafCount
        themeUrl = client.fileUrl(server, full.theme.ifBlank { seed.theme })
        seasons = withContext(Dispatchers.IO) {
            runCatching { client.children(server, full.ratingKey) }.getOrDefault(emptyList())
        }.filter { it.type == "season" }
        next = withContext(Dispatchers.IO) {
            client.showOnDeck(server, full.ratingKey)
                ?: runCatching {
                    val all = client.allEpisodes(server, full.ratingKey)
                    all.firstOrNull { it.viewCount == 0 && it.parentIndex > 0 } ?: all.firstOrNull()
                }.getOrNull()
        }
        val extra = withContext(Dispatchers.IO) {
            runCatching { client.extras(server, full.ratingKey) }.getOrDefault(emptyList())
                .let { backgroundClip(it) }
        }
        val part = extra?.let { item ->
            val detailed = withContext(Dispatchers.IO) {
                runCatching { client.metadata(server, item.ratingKey) }.getOrNull()
            } ?: item
            detailed.Media.firstOrNull()?.Part?.firstOrNull()?.key
        }
        trailerUrl = client.fileUrl(server, part)
    }
    LaunchedEffect(show.art) {
        if (show.art.isBlank()) return@LaunchedEffect
        posters.load("art-" + show.ratingKey, show.art, client.wideSources(server, show.art, 1280, 720)) { loaded ->
            art = loaded
        }
    }
    LaunchedEffect(season?.ratingKey) {
        val current = season ?: return@LaunchedEffect
        lastSeason = current.ratingKey
        episodes = withContext(Dispatchers.IO) {
            runCatching { client.children(server, current.ratingKey) }.getOrDefault(emptyList())
        }.filter { it.type == "episode" }
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
    LaunchedEffect(season?.ratingKey, seasons) {
        if (season != null) return@LaunchedEffect
        delay(60)
        runCatching {
            if (lastSeason != null && seasons.any { it.ratingKey == lastSeason }) seasonFocus.requestFocus()
            else playFocus.requestFocus()
        }
    }
    LaunchedEffect(episodes) {
        if (episodes.isEmpty()) return@LaunchedEffect
        val target = episodes.indexOfFirst { it.viewOffset > 0L }.takeIf { it >= 0 }
            ?: episodes.indexOfFirst { it.viewCount == 0 }.takeIf { it >= 0 }
            ?: 0
        listState.scrollToItem(target)
        delay(80)
        runCatching { episodeFocus.requestFocus() }
    }

    val episodeTarget = remember(episodes) {
        episodes.indexOfFirst { it.viewOffset > 0L }.takeIf { it >= 0 }
            ?: episodes.indexOfFirst { it.viewCount == 0 }.takeIf { it >= 0 }
            ?: 0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .screenBackground()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                    if (season != null) onSeason(null) else onClose()
                    true
                } else false
            },
    ) {
        Backdrop(trailerUrl, trailerPlayer, art)
        if (season == null) {
            val seasonsLabel = when {
                show.childCount > 1 -> "${show.childCount} saisons"
                show.childCount == 1 -> "1 saison"
                else -> ""
            }
            val episodesLabel = if (show.leafCount > 0) "${show.leafCount} épisodes" else ""
            val metaLine = listOf(if (show.year > 0) show.year.toString() else "", seasonsLabel, episodesLabel)
                .filter { it.isNotBlank() }
                .joinToString("   ")
            Column(
                Modifier
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight()
                    .padding(start = 36.dp, top = 28.dp, end = 16.dp, bottom = 20.dp),
            ) {
                Text(show.title, color = Paper, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (show.tagline.isNotBlank()) {
                    Text(show.tagline, color = Color(0xFFC5CDD6), fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 2)
                }
                if (metaLine.isNotBlank()) {
                    Text(metaLine, color = Paper, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                }
                val genres = MovieFacts.genres(show)
                if (genres.isNotBlank()) {
                    Text(genres, color = Paper, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Text(
                    "${MovieFacts.imdb(show)}    ${MovieFacts.audience(show)}    ${MovieFacts.stars(show)}",
                    color = Paper,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    show.summary.ifBlank { "Résumé indisponible." },
                    color = Color(0xFFD5DBE2),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val episode = next
                    val verb = if (episode != null && episode.viewOffset >= 30_000L) "Reprendre" else "Lecture"
                    val label = if (episode != null && episode.parentIndex > 0 && episode.index > 0) {
                        "$verb S${episode.parentIndex} E${episode.index}"
                    } else {
                        verb
                    }
                    Action(label, playFocus, primary = true) {
                        if (episode != null) onPlay(show, episode) else note = "Aucun épisode lisible"
                    }
                    Action(if (watched) "Série marquée comme vue" else "Marquer la série comme vue") {
                        val state = !watched
                        watched = state
                        Thread {
                            val failed = runCatching { client.setWatched(server, show.ratingKey, state) }.isFailure
                            if (failed) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    note = "Le serveur n'a pas enregistré le statut"
                                }
                            }
                        }.start()
                    }
                }
                if (notice.isNotBlank() || note.isNotBlank()) {
                    Text(notice.ifBlank { note }, color = Color(0xFF9AA3AD), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("Saisons", color = Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    items(seasons, key = { it.ratingKey }) { item ->
                        SeasonCard(
                            season = item,
                            client = client,
                            server = server,
                            posters = posters,
                            requester = if (item.ratingKey == lastSeason) seasonFocus else null,
                            onClick = { onSeason(item) },
                            onToggle = { state ->
                                mark(item.ratingKey, state, if (state) "${item.title} marquée comme vue" else "${item.title} marquée comme non vue")
                            },
                        )
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight()
                    .padding(start = 36.dp, top = 24.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Text(show.title, color = Color(0xFF9AA3AD), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(season.title, color = Paper, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    note.ifBlank { "Appui long sur OK : marquer vu / non vu" },
                    color = Color(0xFF9AA3AD),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (season.summary.isNotBlank()) {
                    Text(season.summary, color = Color(0xFFD5DBE2), fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp).fillMaxSize(),
                ) {
                    itemsIndexed(episodes, key = { _, item -> item.ratingKey }) { index, item ->
                        EpisodeRow(
                            episode = item,
                            client = client,
                            server = server,
                            posters = posters,
                            requester = if (index == episodeTarget) episodeFocus else null,
                            onClick = { onEpisode(item) },
                            onToggle = { state ->
                                mark(item.ratingKey, state, if (state) "Épisode marqué comme vu" else "Épisode marqué comme non vu")
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Backdrop(trailerUrl: String?, trailerPlayer: ExoPlayer, art: Bitmap?) {
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
        } else if (art != null) {
            Image(
                bitmap = art.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.62f).fillMaxHeight(),
            )
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
    }
}

@Composable
private fun SeasonCard(
    season: Metadata,
    client: PlexClient,
    server: ServerEndpoint,
    posters: PosterStore,
    requester: FocusRequester?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var viewed by remember(season.ratingKey) { mutableStateOf(season.viewedLeafCount) }
    var held by remember { mutableStateOf(false) }
    val unseen = (season.leafCount - viewed).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .width(100.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                val ok = it.key == Key.DirectionCenter || it.key == Key.Enter
                if (ok && it.type == KeyEventType.KeyDown && it.nativeKeyEvent.isLongPress && !held) {
                    held = true
                    val state = unseen > 0
                    viewed = if (state) season.leafCount else 0
                    onToggle(state)
                    true
                } else if (ok && it.type == KeyEventType.KeyUp) {
                    val wasHeld = held
                    held = false
                    if (!wasHeld) onClick()
                    true
                } else false
            }
            .focusable(),
    ) {
        Box(Modifier.size(100.dp, 150.dp).clip(RoundedCornerShape(4.dp))) {
            RemoteImage(
                posters = posters,
                key = season.ratingKey,
                path = season.thumb,
                urls = client.thumbSources(server, season.thumb),
                modifier = Modifier.fillMaxSize(),
            )
            if (unseen > 0 && viewed > 0) {
                Text(
                    "Reste $unseen",
                    color = Ink,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Signal.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 0.dp),
                )
            }
            if (season.leafCount > 0 && viewed >= season.leafCount) {
                Text(
                    "✓",
                    color = Ink,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Accent.copy(alpha = 0.9f), RoundedCornerShape(50))
                        .padding(horizontal = 6.dp),
                )
            }
            if (focused) {
                Box(Modifier.fillMaxSize().border(3.dp, FocusRing, RoundedCornerShape(4.dp)))
            }
        }
        Text(
            if (season.index > 0) "Saison ${season.index}" else season.title.ifBlank { "Spéciaux" },
            color = if (focused) Accent else Paper,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (season.leafCount > 0) {
            Text(
                if (season.leafCount == 1) "1 épisode" else "${season.leafCount} épisodes",
                color = Color(0xFF9AA3AD),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Metadata,
    client: PlexClient,
    server: ServerEndpoint,
    posters: PosterStore,
    requester: FocusRequester?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var seen by remember(episode.ratingKey) { mutableStateOf(episode.viewCount > 0) }
    var held by remember { mutableStateOf(false) }
    val progress = if (!seen && episode.viewOffset > 0L && episode.duration > 0L) {
        (episode.viewOffset.toFloat() / episode.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val number = if (episode.index > 0) "E${episode.index}  ·  " else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                val ok = it.key == Key.DirectionCenter || it.key == Key.Enter
                if (ok && it.type == KeyEventType.KeyDown && it.nativeKeyEvent.isLongPress && !held) {
                    held = true
                    seen = !seen
                    onToggle(seen)
                    true
                } else if (ok && it.type == KeyEventType.KeyUp) {
                    val wasHeld = held
                    held = false
                    if (!wasHeld) onClick()
                    true
                } else false
            }
            .focusable()
            .border(2.dp, if (focused) FocusRing else Color.Transparent, RoundedCornerShape(6.dp))
            .background(Color(0xCC1C2128), RoundedCornerShape(6.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(150.dp, 84.dp).clip(RoundedCornerShape(4.dp))) {
            RemoteImage(
                posters = posters,
                key = episode.ratingKey,
                path = episode.thumb,
                urls = client.wideSources(server, episode.thumb),
                modifier = Modifier.fillMaxSize(),
            )
            if (progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x99000000)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(4.dp).background(Accent))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                "$number${episode.title}",
                color = if (focused) Accent else Paper,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val length = MovieFacts.duration(episode.duration)
            if (length.isNotBlank()) {
                Text(length, color = Color(0xFF9AA3AD), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            if (episode.summary.isNotBlank()) {
                Text(
                    episode.summary,
                    color = Color(0xFFD5DBE2),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (seen) {
            Text("Vu", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 6.dp))
        }
    }
}

@Composable
internal fun RemoteImage(
    posters: PosterStore,
    key: String,
    path: String,
    urls: List<String>,
    modifier: Modifier,
) {
    var bitmap by remember(key, path) {
        mutableStateOf(if (path.isBlank()) null else posters.peek(key, path))
    }
    LaunchedEffect(key, path) {
        if (path.isBlank() || bitmap != null) return@LaunchedEffect
        posters.load(key, path, urls) { loaded -> bitmap = loaded }
    }
    Box(modifier.background(Color(0xFF2A333C))) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
