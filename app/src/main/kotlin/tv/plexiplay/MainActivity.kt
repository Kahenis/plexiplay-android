package tv.plexiplay

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.plexiplay.data.SeriesChoice
import tv.plexiplay.data.SessionStore
import tv.plexiplay.player.Delivery
import tv.plexiplay.player.DeliveryPlanner
import tv.plexiplay.player.DeviceCodecs
import tv.plexiplay.player.PlaybackSettings
import tv.plexiplay.player.PlayerKind
import tv.plexiplay.player.StreamUrlFactory
import tv.plexiplay.plex.PlexClient
import tv.plexiplay.plex.auth.PinAuthClient
import tv.plexiplay.plex.model.LibrarySection
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.plex.model.ServerEndpoint
import tv.plexiplay.plex.preroll.ClipKind
import tv.plexiplay.ui.auth.LoginScreen
import tv.plexiplay.ui.boot.BootScreen
import tv.plexiplay.ui.detail.MovieDetailScreen
import tv.plexiplay.ui.detail.MovieFacts
import tv.plexiplay.ui.detail.SeriesScreen
import tv.plexiplay.ui.player.AudioChoice
import tv.plexiplay.ui.player.SubChoice
import tv.plexiplay.ui.player.ClipPlay
import tv.plexiplay.ui.player.PlayerScreen
import tv.plexiplay.ui.settings.SettingsScreen
import tv.plexiplay.ui.shell.LibraryPane
import tv.plexiplay.ui.shell.PosterStore
import tv.plexiplay.ui.shell.RailEntry
import tv.plexiplay.ui.shell.Shelf
import tv.plexiplay.ui.shell.ShellScreen
import tv.plexiplay.ui.shell.sectionGlyph
import tv.plexiplay.ui.settings.LibraryPicker
import tv.plexiplay.ui.shell.ConfirmDialog
import tv.plexiplay.ui.shell.SearchScreen
import tv.plexiplay.ui.theme.PlexiplayTheme
import tv.plexiplay.update.AppUpdate
import tv.plexiplay.update.UpdateOffer
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Dernier rapport de lecture envoyé au serveur (pour n'envoyer qu'un point toutes les 10 s). */
private class ReportState {
    var tag: String = ""
    var at: Long = 0L
}

private val timelineExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SessionStore(this)
        applyLanguage(store.settings().uiLanguage)
        val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
        val client = PlexClient(store.clientId(), http = http)
        val auth = PinAuthClient(http, store.clientId())
        val posters = PosterStore(applicationContext.cacheDir, http)
        val updates = AppUpdate(http)
        setContent {
            PlexiplayTheme {
                App(store, client, auth, posters, updates, ::applyLanguage)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        tv.plexiplay.update.KeepOpen.on = false
    }

    /** Touche Home (ou toute sortie de l'appli) : fermeture complète, sans message. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing && !tv.plexiplay.update.KeepOpen.on) {
            finishAndRemoveTask()
        }
    }

    private fun applyLanguage(code: String) {
        val locales = if (code == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(code)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

@Composable
private fun App(
    store: SessionStore,
    client: PlexClient,
    auth: PinAuthClient,
    posters: PosterStore,
    updates: AppUpdate,
    applyLanguage: (String) -> Unit,
) {
    remember { tv.plexiplay.ui.theme.ThemeMode.neon = store.theme() == "neon"; true }
    var themeVolume by remember { mutableIntStateOf(store.themeVolume()) }
    var token by remember { mutableStateOf(store.token()) }
    var servers by remember { mutableStateOf(store.servers()) }
    var serverIndex by remember { mutableStateOf(store.activeServerIndex()) }
    var settings by remember { mutableStateOf(store.settings()) }
    var entries by remember { mutableStateOf(railEntries(emptyList())) }
    var sections by remember { mutableStateOf<List<LibrarySection>>(emptyList()) }
    var picker by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf<List<Metadata>?>(null) }
    var selected by remember { mutableStateOf("home") }
    var pane by remember { mutableStateOf(LibraryPane.NEWS) }
    var shelves by remember { mutableStateOf<List<Shelf>>(emptyList()) }
    var items by remember { mutableStateOf<List<Metadata>>(emptyList()) }
    var stack by remember { mutableStateOf<List<Metadata>>(emptyList()) }
    var collection by remember { mutableStateOf<Metadata?>(null) }
    var updateStatus by remember { mutableStateOf("") }
    var updateProgress by remember { mutableFloatStateOf(-1f) }
    var updateDetail by remember { mutableStateOf("") }
    var offer by remember { mutableStateOf<UpdateOffer?>(null) }
    var startupOffer by remember { mutableStateOf<UpdateOffer?>(null) }
    var startupChecked by remember { mutableStateOf(false) }
    var inviteStatus by remember { mutableStateOf("") }
    var pendingInvites by remember { mutableStateOf<List<tv.plexiplay.plex.PlexInvite>?>(null) }
    var inviteBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Choisissez une catégorie") }
    var clips by remember { mutableStateOf<List<ClipPlay>?>(null) }
    // Dernier lancement depuis la page d'un film : de quoi relancer le film (changement de piste en flux converti).
    var lastPlay by remember { mutableStateOf<Triple<Metadata, String, Boolean>?>(null) }
    var movie by remember { mutableStateOf<Metadata?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var series by remember { mutableStateOf<Metadata?>(null) }
    var seriesFrom by remember { mutableStateOf<String?>(null) }
    var seasonOpen by remember { mutableStateOf<Metadata?>(null) }
    var episodeOpen by remember { mutableStateOf<Metadata?>(null) }
    var restoreKey by remember { mutableStateOf<String?>(null) }
    var restoreToken by remember { mutableIntStateOf(0) }
    var playError by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(token.isNullOrBlank()) }
    var progress by remember { mutableFloatStateOf(if (token.isNullOrBlank()) 1f else 0.04f) }
    var loadedUri by remember { mutableStateOf<String?>(null) }
    val server = servers.getOrNull(serverIndex)
    // Bibliothèques de vidéos personnelles : ni résumé ni description, on lance la lecture sans passer par la fiche.
    fun openMovie(item: Metadata) {
        val sectionKey = if (item.librarySectionID > 0L) item.librarySectionID.toString() else selected
        val personal = sections.any { it.key == sectionKey && it.personal }
        val active = server
        if (!personal || active == null) {
            movie = item
            return
        }
        Thread {
            val built = runCatching {
                buildQueue(client, active, item, settings, store.clientId(), resume = true, bonus = false)
            }.getOrDefault(emptyList())
            runOnUi {
                if (built.isEmpty()) status = "Lecture impossible" else clips = built
            }
        }.start()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        tv.plexiplay.update.UpdateBus.listener = { message ->
            updateStatus = message
            updateProgress = -1f
            updateDetail = ""
            startupOffer = null
        }
        onDispose { tv.plexiplay.update.UpdateBus.listener = null }
    }
    val backAction = remember { mutableStateOf<() -> Unit>({}) }
    val playerBack = remember { mutableStateOf<(() -> Boolean)?>(null) }
    backAction.value = {
        when {
            startupOffer != null && clips == null && updateStatus != "Téléchargement…" -> startupOffer = null
            pendingInvites != null -> if (!inviteBusy) pendingInvites = null
            confirm != null -> confirm = null
            picker -> picker = false
            clips != null -> if (playerBack.value?.invoke() != true) clips = null
            episodeOpen != null -> episodeOpen = null
            seasonOpen != null -> seasonOpen = null
            series != null -> {
                restoreKey = seriesFrom ?: series?.ratingKey
                restoreToken += 1
                series = null
                seriesFrom = null
            }
            movie != null -> {
                restoreKey = movie?.ratingKey
                restoreToken += 1
                movie = null
            }
            // Retour système (le focus n'est pas dans l'écran principal) : même logique que les touches.
            selected == "settings" || selected == "search" -> {
                selected = "home"
                stack = emptyList()
                collection = null
                pane = LibraryPane.NEWS
            }
            stack.isNotEmpty() -> stack = stack.dropLast(1)
            collection != null -> collection = null
            selected != "home" && pane != LibraryPane.NEWS -> pane = LibraryPane.NEWS
            else -> confirm = "exit"
        }
    }
    val backCallback = remember {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = backAction.value.invoke()
        }
    }
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(dispatcher) {
        if (dispatcher != null) dispatcher.addCallback(backCallback)
        onDispose { backCallback.remove() }
    }
    DisposableEffect(clips, movie, series, confirm) {
        backCallback.isEnabled = true
        onDispose { }
    }
    // Pendant une lecture, la box ne reçoit aucun appui : on empêche la mise en veille.
    val playing = clips != null
    DisposableEffect(playing) {
        val window = (context as? android.app.Activity)?.window
        if (playing) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val main = remember { Handler(Looper.getMainLooper()) }
    LaunchedEffect(token) {
        val current = token
        if (current.isNullOrBlank()) {
            ready = true
            return@LaunchedEffect
        }
        ready = false
        progress = 0.04f
        fun push(value: Float) {
            val next = value.coerceIn(0f, 1f)
            val apply = {
                if (next > progress) progress = next
            }
            if (Looper.myLooper() == Looper.getMainLooper()) apply() else main.post(apply)
        }
        try {
            val found = stage(client, 0.04f, 0.28f, ::push) {
                val listed = client.chooseServers(client.resources(current))
                // Chaque serveur est joint sur la première de ses adresses qui répond.
                val pool = java.util.concurrent.Executors.newFixedThreadPool(listed.size.coerceIn(1, 6))
                val reached = try {
                    pool.invokeAll(listed.map { entry -> java.util.concurrent.Callable { client.reach(entry) } })
                        .map { future -> runCatching { future.get() }.getOrNull() }
                } finally {
                    pool.shutdown()
                }
                val all = listed.mapIndexed { index, entry -> reached[index] ?: entry }
                // Le premier serveur qui a des bibliothèques passe devant : un serveur vide
                // (le sien, par exemple) ne doit pas masquer celui d'un ami.
                val live = reached.filterNotNull()
                val first = live.firstOrNull { candidate ->
                    runCatching { client.sections(candidate).isNotEmpty() }.getOrDefault(false)
                }
                if (first != null) listOf(first) + all.filter { it.clientIdentifier != first.clientIdentifier } else all
            }
            store.saveServers(found, 0)
            servers = found
            serverIndex = 0
            val active = found.firstOrNull()
            if (active == null) {
                status = "Aucun serveur"
                return@LaunchedEffect
            }
            val loaded = stage(client, 0.28f, 0.42f, ::push) { client.sections(active) }
            val shown = visibleSections(store, active, loaded)
            sections = shown
            entries = railEntries(shown)
            loadedUri = active.uri
            shelves = loadHome(client, active, shown, 0.42f, 0.98f, ::push)
            items = emptyList()
            selected = "home"
            pane = LibraryPane.NEWS
            status = if (loaded.isEmpty()) "Aucune bibliothèque disponible sur ${active.name}" else "Accueil"
            push(1f)
        } catch (error: Exception) {
            status = error.message ?: "Serveur inaccessible"
        } finally {
            client.onBytes = null
            progress = 1f
            ready = true
        }
    }

    // Au démarrage : une seule vérification discrète de la dernière version publiée ; rien ne s'affiche s'il n'y a rien de nouveau.
    LaunchedEffect(ready, token) {
        if (!ready || startupChecked || token.isNullOrBlank()) return@LaunchedEffect
        startupChecked = true
        Thread {
            val found = runCatching { updates.latest(BuildConfig.VERSION_NAME) }.getOrNull()
            if (found != null) runOnUi { startupOffer = found }
        }.start()
    }

    if (!ready) {
        BootScreen(progress, BuildConfig.VERSION_NAME)
        return
    }

    if (token.isNullOrBlank()) {
        LoginScreen(auth) { received ->
            store.saveToken(received)
            progress = 0.04f
            ready = false
            token = received
        }
        return
    }

    LaunchedEffect(server?.uri) {
        val active = server ?: return@LaunchedEffect
        if (loadedUri == active.uri && sections.isNotEmpty()) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) { client.sections(active) }
        }.onSuccess { loaded ->
            val shown = visibleSections(store, active, loaded)
            sections = shown
            entries = railEntries(shown)
            selected = "home"
            pane = LibraryPane.NEWS
            loadedUri = active.uri
        }.onFailure {
            sections = emptyList()
            entries = railEntries(emptyList())
            shelves = emptyList()
            items = emptyList()
            selected = "home"
            pane = LibraryPane.NEWS
            loadedUri = active.uri
            status = "${active.name} : serveur injoignable"
        }
    }

    // Catalogue de recherche : titres des bibliothèques affichées du serveur actif, chargés une fois.
    LaunchedEffect(server?.uri, sections) { catalog = null }
    LaunchedEffect(selected, server?.uri, sections, catalog == null) {
        val active = server ?: return@LaunchedEffect
        if (selected != "search" || catalog != null) return@LaunchedEffect
        catalog = withContext(Dispatchers.IO) {
            sections.flatMap { section -> runCatching { client.items(active, section.key) }.getOrDefault(emptyList()) }
                .filter { it.type == "movie" || it.type == "show" }
        }
    }

    // Fond d'écran d'une collection : celui que Plex prévoit pour elle, à défaut le fond d'un des films de la collection.
    var collectionArt by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(collection?.ratingKey, items) {
        val current = collection
        val active = server
        if (current == null || active == null) {
            collectionArt = null
            return@LaunchedEffect
        }
        var path = current.art
        if (path.isBlank()) {
            path = withContext(Dispatchers.IO) { runCatching { client.metadata(active, current.ratingKey)?.art }.getOrNull() }.orEmpty()
        }
        if (path.isBlank()) {
            path = items.firstOrNull { (it.type == "movie" || it.type == "show") && it.art.isNotBlank() }?.art.orEmpty()
        }
        if (path.isBlank()) return@LaunchedEffect
        posters.load("art-" + current.ratingKey, path, client.wideSources(active, path, 1280, 720)) { loaded ->
            if (collection?.ratingKey == current.ratingKey) collectionArt = loaded
        }
    }

    val inside = stack.isNotEmpty() || collection != null
    val useShelves = selected != "settings" && selected != "search" && !inside && (selected == "home" || pane == LibraryPane.NEWS)
    val showTabs = selected != "home" && selected != "settings" && selected != "search" && !inside

    LaunchedEffect(selected, pane, stack, collection?.ratingKey, server?.uri, sections) {
        val active = server ?: return@LaunchedEffect
        if (selected == "settings" || selected == "search") return@LaunchedEffect
        if (shelves.isEmpty() && items.isEmpty()) status = "Chargement…"
        val parent = stack.lastOrNull()
        val currentCollection = collection
        runCatching {
            withContext(Dispatchers.IO) {
                when {
                    selected == "home" -> Loaded.Rows(homeRows(client, active, sections))
                    parent != null -> Loaded.Grid(client.children(active, parent.ratingKey), parent.title)
                    currentCollection != null -> Loaded.Grid(client.children(active, currentCollection.ratingKey), currentCollection.title)
                    pane == LibraryPane.LIBRARY -> Loaded.Grid(
                        client.items(active, selected),
                        entries.firstOrNull { it.id == selected }?.title ?: "Bibliothèque",
                        fullSection = selected,
                    )
                    pane == LibraryPane.COLLECTIONS -> Loaded.Grid(client.collections(active, selected), "Collections")
                    else -> {
                        val show = entries.firstOrNull { it.id == selected }?.type == "show"
                        val rows = newsRows(client, active, selected, show)
                        Loaded.Rows(if (show) withSeriesPosters(rows) else rows)
                    }
                }
            }
        }.onSuccess { loaded ->
            when (loaded) {
                is Loaded.Rows -> {
                    shelves = loaded.rows
                    items = emptyList()
                    status = when {
                        selected == "home" && sections.isEmpty() ->
                            "Aucune bibliothèque disponible sur ${active.name} (serveur injoignable ou vide)"
                        selected == "home" -> "Accueil"
                        else -> "Quoi de neuf"
                    }
                }
                is Loaded.Grid -> {
                    items = loaded.items
                    shelves = emptyList()
                    status = loaded.title
                    loaded.fullSection?.let { section -> posters.syncSection(section, loaded.items.map { it.ratingKey }.toSet()) }
                }
            }
        }.onFailure { status = it.message ?: "Liste indisponible" }
    }

    LaunchedEffect(shelves, items, server?.uri) {
        val active = server ?: return@LaunchedEffect
        val queued = (shelves.flatMap { it.items } + items).map { item ->
            Triple(item.ratingKey, item.thumb, active.let { client.thumbSources(it, item.thumb) })
        }
        posters.prefetch(queued)
    }

    val pageKey = selected + "|" + pane.name + "|" +
        stack.joinToString(",") { it.ratingKey } + "|" + (collection?.ratingKey ?: "")
    val closeDetail = {
        val key = movie?.ratingKey
        if (key != null) {
            restoreKey = key
            restoreToken += 1
        }
        movie = null
    }
    val closeSeries = {
        restoreKey = seriesFrom ?: series?.ratingKey
        restoreToken += 1
        series = null
        seriesFrom = null
        seasonOpen = null
        episodeOpen = null
    }
    val playEpisode: (String, Metadata, EpisodeChoice?, Boolean) -> Unit = { showKey, episode, picked, resume ->
        val active = server
        if (active != null) {
            playError = ""
            Thread {
                val built = runCatching {
                    buildEpisodeQueue(
                        client, active, showKey, episode, settings, store.clientId(),
                        picked, store.seriesChoice(showKey),
                        { choice -> store.saveSeriesChoice(showKey, choice) },
                        resume,
                    )
                }.getOrElse { error ->
                    runOnUi { playError = error.message ?: "Lecture impossible" }
                    return@Thread
                }
                runOnUi {
                    if (built.isEmpty()) playError = "Aucun fichier lisible" else clips = built
                }
            }.start()
        }
    }
    Box(Modifier.fillMaxSize()) {
    ShellScreen(
        serverName = server?.name ?: "Aucun serveur",
        entries = entries,
        selectedId = selected,
        pane = pane,
        pageKey = pageKey,
        covered = movie != null || series != null || clips != null || confirm != null || picker || startupOffer != null || pendingInvites != null,
        restoreKey = restoreKey,
        restoreToken = restoreToken,
        shelves = shelves,
        items = items,
        useShelves = useShelves,
        showTabs = showTabs,
        thumb = { item -> server?.let { client.thumbSources(it, item.thumb) }.orEmpty() },
        posters = posters,
        backdrop = if (collection != null) collectionArt else null,
        status = status,
        count = if (pane == LibraryPane.LIBRARY && items.isNotEmpty() && entries.firstOrNull { it.id == selected }?.type == "show") items.size else -1,
        onSelect = {
            selected = it.id
            stack = emptyList()
            collection = null
            pane = LibraryPane.NEWS
        },
        onOpen = { item ->
            when {
                showTabs && pane == LibraryPane.COLLECTIONS && item.type == "collection" ->
                    collection = item
                item.type == "show" -> {
                    seriesFrom = item.ratingKey
                    seasonOpen = null
                    series = item
                }
                item.type == "episode" && item.grandparentRatingKey.isNotBlank() -> {
                    seriesFrom = item.ratingKey
                    seasonOpen = null
                    series = Metadata(
                        ratingKey = item.grandparentRatingKey,
                        type = "show",
                        title = item.grandparentTitle,
                        thumb = item.grandparentThumb,
                        art = item.grandparentArt,
                        theme = item.grandparentTheme,
                    )
                }
                item.type == "season" && item.added > 0 && item.grandparentRatingKey.isNotBlank() -> {
                    seriesFrom = item.ratingKey
                    seasonOpen = Metadata(
                        ratingKey = item.ratingKey,
                        type = "season",
                        title = item.title,
                        index = item.index,
                    )
                    series = Metadata(
                        ratingKey = item.grandparentRatingKey,
                        type = "show",
                        title = item.grandparentTitle,
                        thumb = item.grandparentThumb,
                        art = item.grandparentArt,
                        theme = item.grandparentTheme,
                    )
                }
                item.type == "season" -> stack = stack + item
                item.type == "movie" -> openMovie(item)
                else -> {
                    val active = server ?: return@ShellScreen
                    Thread {
                        val built = buildQueue(client, active, item, settings, store.clientId())
                        runOnUi { clips = built }
                    }.start()
                }
            }
        },
        onPane = {
            pane = it
            collection = null
            stack = emptyList()
        },
        onForget = { item, _ ->
            val active = server
            if (active != null) {
                Thread {
                    val done = runCatching { client.setWatched(active, item.ratingKey, false) }.isSuccess
                    runOnUi {
                        if (done) {
                            shelves = shelves.map { shelf ->
                                if (shelf.title == "Continuer à regarder" || shelf.title == "Regarder à nouveau" || shelf.title == "Les plus regardés") {
                                    shelf.copy(items = shelf.items.filter { it.ratingKey != item.ratingKey })
                                } else {
                                    shelf
                                }
                            }
                        } else {
                            status = "Le serveur n'a pas pu mettre à jour ce titre"
                        }
                    }
                }.start()
            }
        },
        onContentBack = {
            if (stack.isNotEmpty()) {
                stack = stack.dropLast(1)
                true
            } else if (collection != null) {
                collection = null
                true
            } else false
        },
        search = {
            SearchScreen(
                catalog = catalog,
                thumb = { item -> server?.let { client.thumbSources(it, item.thumb) }.orEmpty() },
                posters = posters,
                onOpen = { item -> if (item.type == "show") { seriesFrom = item.ratingKey; seasonOpen = null; series = item } else openMovie(item) },
            )
        },
        settings = {
            if (selected == "settings") {
                SettingsScreen(
                    settings = settings,
                    servers = servers,
                    serverIndex = serverIndex,
                    onChange = {
                        store.saveSettings(it)
                        settings = store.settings()
                        applyLanguage(it.uiLanguage)
                    },
                    onServer = {
                        serverIndex = it
                        store.saveServers(servers, it)
                        stack = emptyList()
                    },
                    onSignOut = { confirm = "signout" },
                    inviteStatus = inviteStatus,
                    onCheckInvites = {
                        val current = token
                        if (!current.isNullOrBlank() && !inviteBusy) {
                            inviteStatus = "Recherche des invitations…"
                            Thread {
                                val found = runCatching { client.invites(current) }
                                runOnUi {
                                    val list = found.getOrNull()
                                    when {
                                        list == null -> inviteStatus = "Vérification impossible, réessayez"
                                        list.isEmpty() -> inviteStatus = "Aucune invitation en attente"
                                        else -> {
                                            inviteStatus = "${list.size} invitation(s) en attente"
                                            pendingInvites = list
                                        }
                                    }
                                }
                            }.start()
                        }
                    },
                    onLibraries = { picker = true },
                    versionName = BuildConfig.VERSION_NAME,
                    updateStatus = updateStatus,
                    updateProgress = updateProgress,
                    updateDetail = updateDetail,
                    offer = offer,
                    onCheckUpdate = {
                        updateStatus = "Vérification…"
                        offer = null
                        Thread {
                            val found = runCatching { updates.latest(BuildConfig.VERSION_NAME) }.getOrNull()
                            runOnUi {
                                if (found == null) {
                                    offer = null
                                    updateStatus = "Déjà à jour (${BuildConfig.VERSION_NAME})"
                                } else {
                                    offer = found
                                    updateStatus = "Version ${found.version} proposée"
                                }
                            }
                        }.start()
                    },
                    onInstall = {
                        val current = offer
                        if (current != null) {
                            updateStatus = "Téléchargement…"
                            updateProgress = 0f
                            tv.plexiplay.update.KeepOpen.on = true
                            Thread {
                                val message = updates.fetchAndInstall(
                                    context,
                                    current,
                                    { text -> runOnUi { updateDetail = text } },
                                    { value -> runOnUi { updateProgress = value } },
                                )
                                runOnUi {
                                    updateProgress = -1f
                                    updateDetail = ""
                                    updateStatus = message ?: "Confirmez l'installation à l'écran"
                                }
                            }.start()
                        }
                    },
                    onCancelUpdate = {
                        offer = null
                        updateStatus = "Mise à jour annulée"
                    },
                    theme = if (tv.plexiplay.ui.theme.ThemeMode.neon) "neon" else "classic",
                    themeVolume = themeVolume,
                    onThemeVolume = {
                        store.saveThemeVolume(it)
                        themeVolume = it
                    },
                    onTheme = {
                        store.saveTheme(it)
                        tv.plexiplay.ui.theme.ThemeMode.neon = it == "neon"
                    },
                )
            }
        },
    )
    val opened = movie
    val activeServer = server
    if (opened != null && activeServer != null && clips == null) {
        MovieDetailScreen(
            seed = opened,
            client = client,
            server = activeServer,
            settings = settings,
            onPlay = { full, resolution, audio, subtitles, resume ->
                playError = ""
                lastPlay = Triple(full, resolution, resume)
                Thread {
                    val built = runCatching {
                        buildQueue(client, activeServer, full, settings, store.clientId(), resolution, audio, subtitles, resume)
                    }.getOrElse { error ->
                        runOnUi { playError = error.message ?: "Lecture impossible" }
                        return@Thread
                    }
                    runOnUi {
                        if (built.isEmpty()) playError = "Aucun fichier lisible"
                        else clips = built
                    }
                }.start()
            },
            onTrailers = { full ->
                Thread {
                    val built = trailerQueue(client, activeServer, full, settings, store.clientId())
                    runOnUi { if (built.isNotEmpty()) clips = built }
                }.start()
            },
            onClose = closeDetail,
            notice = playError,
            themeVolume = themeVolume / 100f,
            posters = posters,
            accountToken = token.orEmpty(),
        )
    }
    val openedSeries = series
    if (openedSeries != null && activeServer != null && clips == null && episodeOpen == null) {
        SeriesScreen(
            seed = openedSeries,
            season = seasonOpen,
            client = client,
            server = activeServer,
            posters = posters,
            themeVolume = themeVolume / 100f,
            onSeason = { seasonOpen = it },
            onEpisode = { episodeOpen = it },
            onPlay = { show, episode -> playEpisode(show.ratingKey, episode, null, episode.viewOffset >= 30_000L) },
            onClose = closeSeries,
            notice = playError,
        )
    }
    val openedEpisode = episodeOpen
    if (openedEpisode != null && activeServer != null && clips == null) {
        val showKey = openedEpisode.grandparentRatingKey.ifBlank { openedSeries?.ratingKey.orEmpty() }
        MovieDetailScreen(
            seed = openedEpisode,
            client = client,
            server = activeServer,
            settings = settings,
            onPlay = { full, resolution, audio, subtitles, resume ->
                playEpisode(showKey, full, EpisodeChoice(resolution, audio, subtitles), resume)
            },
            onTrailers = { },
            onClose = { episodeOpen = null },
            notice = playError,
            themeVolume = themeVolume / 100f,
            posters = posters,
            accountToken = token.orEmpty(),
            promoKey = showKey,
            themePath = openedEpisode.grandparentTheme.ifBlank { openedSeries?.theme.orEmpty() },
            remembered = store.seriesChoice(showKey),
        )
    }
    if (clips != null) {
      androidx.compose.runtime.key(clips!!.firstOrNull()?.url) {
        val reportState = androidx.compose.runtime.remember { ReportState() }
        val reportSession = androidx.compose.runtime.remember { java.util.UUID.randomUUID().toString() }
        PlayerScreen(
            clips = clips!!,
            aspect = settings.aspect,
            engine = settings.engine,
            playerKind = settings.player,
            onProgress = { key, state, position, duration ->
                val active = server ?: return@PlayerScreen
                // Rapport au serveur (« Lecture en cours », Tautulli) : tout de suite quand l'état change,
                // sinon toutes les 10 s, dans l'ordre, avec un identifiant de session propre à cette lecture.
                val now = android.os.SystemClock.elapsedRealtime()
                val tag = "$key:$state"
                val send = synchronized(reportState) {
                    if (tag == reportState.tag && state != "stopped" && now - reportState.at < 10_000L) {
                        false
                    } else {
                        reportState.tag = tag
                        reportState.at = now
                        true
                    }
                }
                if (send) {
                    val session = reportSession
                    timelineExecutor.execute { client.timeline(active, key, state, position, duration, session) }
                }
            },
            onExit = { clips = null },
            onSubtitleChosen = { clip, id ->
                val active = server
                if (active != null) withContext(Dispatchers.IO) { runCatching { client.setSubtitleStream(active, clip.partId, id) } }
            },
            onTrackSync = { clip, audioId, oldUrl ->
                val active = server
                if (active != null) withContext(Dispatchers.IO) {
                    runCatching { client.stopTranscode(active, android.net.Uri.parse(oldUrl).getQueryParameter("session").orEmpty()) }
                    if (audioId > 0) runCatching { client.setAudioStream(active, clip.partId, audioId) }
                    // Laisse au serveur le temps d'arrêter l'ancien transcodage.
                    kotlinx.coroutines.delay(700)
                }
            },
            onReload = { clip, audioId, subId, positionMs, oldUrl ->
                val active = server
                val play = lastPlay
                if (active == null || play == null || play.first.ratingKey != clip.ratingKey || clip.partId <= 0L) {
                    false
                } else {
                    val built = withContext(Dispatchers.IO) {
                        runCatching {
                            var note = ""
                            runCatching { client.stopTranscode(active, android.net.Uri.parse(oldUrl).getQueryParameter("session").orEmpty()) }
                            val audioOk = if (audioId > 0) runCatching { client.setAudioStream(active, clip.partId, audioId) }.isSuccess else true
                            val subOk = runCatching { client.setSubtitleStream(active, clip.partId, subId.coerceAtLeast(0)) }.getOrDefault(false)
                            note = "demandé : " + (if (play.second.isBlank()) "résolution native" else "${play.second}p") + ", audio #$audioId, ST #${subId.coerceAtLeast(0)}, reprise à ${positionMs / 1000} s · enregistrement serveur : audio #$audioId " + (if (audioOk) "OK" else "ÉCHEC") + " · ST #${subId.coerceAtLeast(0)} " + (if (subOk) "OK" else "ÉCHEC")
                            // Pas de rapport « arrêté » : Plex pourrait couper la nouvelle session (la position est transmise par l'appli).
                            kotlinx.coroutines.delay(5000)
                            val fresh = client.metadata(active, clip.ratingKey) ?: play.first
                            val language = clip.audios.firstOrNull { it.id == audioId }?.language.orEmpty()
                            val queue = buildQueue(
                                client, active, fresh, settings, store.clientId(), play.second,
                                language.ifBlank { settings.audioLanguage },
                                if (subId > 0) subId.toString() else "off",
                                resume = false, bonus = false,
                            )
                            queue.map {
                                if (it.kind == tv.plexiplay.plex.preroll.ClipKind.FEATURE) {
                                    it.copy(startMs = positionMs, expectAudio = language, diag = it.diag + "\nDernier changement : relance complète · " + note)
                                } else {
                                    it
                                }
                            }
                        }.getOrNull().orEmpty()
                    }
                    if (built.isEmpty()) {
                        false
                    } else {
                        clips = built
                        true
                    }
                }
            },
            onAudioChosen = { clip ->
                val active = server
                if (active != null) Thread { runCatching { client.setAudioStream(active, clip.partId, clip.frStreamId) } }.start()
            },
            onRegisterBack = { playerBack.value = it },
            mpvMode = settings.mpvMode,
            formatFix = settings.formatFix,
            compat = settings.compat || (clips?.firstOrNull { it.kind == tv.plexiplay.plex.preroll.ClipKind.FEATURE }?.ratingKey in settings.compatTitles),
            onCompat = {
                val active = server
                val featureClip = clips?.firstOrNull { it.kind == tv.plexiplay.plex.preroll.ClipKind.FEATURE }
                val key = featureClip?.ratingKey
                val wasResumed = (featureClip?.startMs ?: 0L) > 0L
                if (active != null && key != null && !settings.compat && settings.compatMode == "auto" && key !in settings.compatTitles) {
                    store.learnCompat(key)
                    settings = store.settings()
                    val next = settings.copy(trailerCount = 0)
                    Thread {
                        val built = runCatching {
                            client.metadata(active, key)?.let { buildQueue(client, active, it, next, store.clientId(), resume = wasResumed, bonus = false) }
                        }.getOrNull().orEmpty()
                        runOnUi { if (built.isNotEmpty()) clips = built }
                    }.start()
                }
            },
        )
      }
    }
    if (picker) {
        LibraryPicker(
            servers = servers,
            startIndex = serverIndex,
            fetch = { target -> client.sections(target) },
            hiddenOf = { target -> store.hiddenLibraries(target.clientIdentifier) },
            onSave = { target, hidden ->
                store.saveHiddenLibraries(target.clientIdentifier, hidden)
                if (target.clientIdentifier == server?.clientIdentifier) {
                    Thread {
                        val all = runCatching { client.sections(target) }.getOrNull()
                        runOnUi {
                            if (all != null) {
                                val shown = visibleSections(store, target, all)
                                sections = shown
                                entries = railEntries(shown)
                                if (entries.none { it.id == selected }) selected = "home"
                            }
                        }
                    }.start()
                }
            },
            onClose = { picker = false },
        )
    }
    // Horloge en haut à droite, partout sauf pendant la lecture.
    if (clips == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopEnd) {
            tv.plexiplay.ui.shell.ClockChip(Modifier.padding(top = 10.dp, end = 18.dp))
        }
    }
    val invitesShown = pendingInvites
    if (invitesShown != null && clips == null) {
        tv.plexiplay.ui.settings.InviteDialog(
            invites = invitesShown,
            busy = inviteBusy,
            onLater = { pendingInvites = null; inviteStatus = "Invitations laissées en attente" },
            onAccept = {
                val current = token
                if (!current.isNullOrBlank() && !inviteBusy) {
                    inviteBusy = true
                    val before = servers.map { it.clientIdentifier }.toSet()
                    val activeId = servers.getOrNull(serverIndex)?.clientIdentifier
                    val wantsServer = invitesShown.any { it.server }
                    Thread {
                        val outcome = runCatching {
                            invitesShown.forEach { client.acceptInvite(current, it) }
                            var found: List<tv.plexiplay.plex.model.ServerEndpoint> = emptyList()
                            for (attempt in 0 until 6) {
                                val listed = client.chooseServers(client.resources(current))
                                found = listed.map { client.reach(it) ?: it }
                                if (!wantsServer || found.any { it.clientIdentifier !in before }) break
                                Thread.sleep(2500)
                            }
                            found
                        }
                        runOnUi {
                            inviteBusy = false
                            pendingInvites = null
                            val found = outcome.getOrNull()
                            if (found == null) {
                                inviteStatus = "Acceptation impossible, réessayez"
                            } else {
                                val fresh = found.indexOfFirst { it.clientIdentifier !in before }
                                val keep = found.indexOfFirst { it.clientIdentifier == activeId }.coerceAtLeast(0)
                                val target = if (fresh >= 0) fresh else keep
                                store.saveServers(found, target)
                                servers = found
                                stack = emptyList()
                                serverIndex = target
                                if (fresh >= 0) {
                                    inviteStatus = "Accepté : serveur « ${found[fresh].name} » ajouté"
                                    selected = "home"
                                    picker = true
                                } else {
                                    inviteStatus = if (wantsServer) "Accepté : le serveur partagé n'est pas encore visible, revérifiez dans un instant" else "Demande d'ami acceptée"
                                }
                            }
                        }
                    }.start()
                }
            },
        )
    }
    val proposed = startupOffer
    if (proposed != null && clips == null) {
        tv.plexiplay.ui.shell.UpdateDialog(
            current = BuildConfig.VERSION_NAME,
            offered = proposed.version,
            downloading = updateStatus == "Téléchargement…",
            progress = updateProgress,
            detail = updateDetail,
            onInstall = {
                updateStatus = "Téléchargement…"
                updateProgress = 0f
                tv.plexiplay.update.KeepOpen.on = true
                Thread {
                    val message = updates.fetchAndInstall(
                        context,
                        proposed,
                        { text -> runOnUi { updateDetail = text } },
                        { value -> runOnUi { updateProgress = value } },
                    )
                    runOnUi {
                        updateProgress = -1f
                        updateDetail = ""
                        updateStatus = message ?: "Confirmez l'installation à l'écran"
                        startupOffer = null
                    }
                }.start()
            },
            onLater = { startupOffer = null },
        )
    }
    when (confirm) {
        "signout" -> ConfirmDialog(
            question = "Vous allez être déconnecté de Plex.",
            onYes = {
                confirm = null
                store.clearSession()
                token = null
                servers = emptyList()
            },
            onNo = { confirm = null },
        )
        "exit" -> ConfirmDialog(
            question = "Quitter Plexiplay ?",
            onYes = {
                confirm = null
                (context as? android.app.Activity)?.finish()
            },
            onNo = { confirm = null },
        )
    }
    }
}

private sealed class Loaded {
    data class Rows(val rows: List<Shelf>) : Loaded()
    data class Grid(val items: List<Metadata>, val title: String, val fullSection: String? = null) : Loaded()
}

private suspend fun <T> stage(
    client: PlexClient,
    start: Float,
    end: Float,
    publish: (Float) -> Unit,
    block: () -> T,
): T {
    return try {
        client.onBytes = { read, total ->
            val part = if (total > 0) read.toFloat() / total.toFloat()
            else read.toFloat() / (read.toFloat() + 24_000f)
            publish(start + (end - start) * part.coerceIn(0f, 0.98f))
        }
        withContext(Dispatchers.IO) { block() }
    } finally {
        client.onBytes = null
        publish(end)
    }
}

private suspend fun loadHome(
    client: PlexClient,
    server: ServerEndpoint,
    sections: List<LibrarySection>,
    start: Float,
    end: Float,
    publish: (Float) -> Unit,
): List<Shelf> {
    val movies = sections.filter { it.type == "movie" }
    val shows = sections.filter { it.type == "show" }
    val calls = buildList {
        movies.forEach { section ->
            add { runCatching { client.onDeck(server, section.key) }.getOrDefault(emptyList()) }
            add { runCatching { client.recentlyAdded(server, section.key) }.getOrDefault(emptyList()) }
        }
        shows.forEach { section ->
            add { runCatching { client.onDeck(server, section.key) }.getOrDefault(emptyList()) }
            add { runCatching { groupAdditions(client.recentlyAdded(server, section.key, 80)) }.getOrDefault(emptyList()) }
        }
    }
    if (calls.isEmpty()) {
        publish(end)
        return listOf(
            Shelf("Films - Continuer à voir", emptyList()),
            Shelf("Films - Derniers ajouts", emptyList()),
            Shelf("Séries - Continuer à voir", emptyList()),
            Shelf("Séries - Derniers ajouts", emptyList()),
        )
    }
    val slice = (end - start) / calls.size
    val loaded = calls.mapIndexed { index, call ->
        stage(client, start + index * slice, start + (index + 1) * slice, publish, call)
    }
    val movieCalls = movies.size * 2
    return listOf(
        Shelf("Films - Continuer à voir", loaded.filterIndexed { index, _ -> index < movieCalls && index % 2 == 0 }.flatten()),
        Shelf("Films - Derniers ajouts", loaded.filterIndexed { index, _ -> index < movieCalls && index % 2 == 1 }.flatten()),
        Shelf("Séries - Continuer à voir", loaded.filterIndexed { index, _ -> index >= movieCalls && index % 2 == 0 }.flatten().map { seriesPoster(it) }),
        Shelf("Séries - Derniers ajouts", loaded.filterIndexed { index, _ -> index >= movieCalls && index % 2 == 1 }.flatten().map { seriesPoster(it) }),
    )
}

private fun homeRows(client: PlexClient, server: ServerEndpoint, sections: List<LibrarySection>): List<Shelf> {
    val movies = sections.filter { it.type == "movie" }
    val shows = sections.filter { it.type == "show" }
    return listOf(
        Shelf("Films - Continuer à voir", movies.flatMap { runCatching { client.onDeck(server, it.key) }.getOrDefault(emptyList()) }),
        Shelf("Films - Derniers ajouts", movies.flatMap { runCatching { client.recentlyAdded(server, it.key) }.getOrDefault(emptyList()) }),
        Shelf("Séries - Continuer à voir", shows.flatMap { runCatching { client.onDeck(server, it.key) }.getOrDefault(emptyList()) }.map { seriesPoster(it) }),
        Shelf("Séries - Derniers ajouts", shows.flatMap { runCatching { groupAdditions(client.recentlyAdded(server, it.key, 80)) }.getOrDefault(emptyList()) }.map { seriesPoster(it) }),
    )
}

private fun withSeriesPosters(rows: List<Shelf>): List<Shelf> =
    rows.map { shelf -> shelf.copy(items = shelf.items.map { seriesPoster(it) }) }

/**
 * Plus de 4 épisodes de la même saison qui se suivent : une seule vignette (la plus récente),
 * de type saison, avec le nombre d'ajouts. Le résultat est limité à 24 vignettes.
 */
private fun groupAdditions(items: List<Metadata>): List<Metadata> {
    val out = mutableListOf<Metadata>()
    var i = 0
    while (i < items.size) {
        val first = items[i]
        var j = i + 1
        if (first.type == "episode" && first.parentRatingKey.isNotBlank()) {
            while (j < items.size && items[j].type == "episode" &&
                items[j].parentRatingKey == first.parentRatingKey &&
                items[j].grandparentRatingKey == first.grandparentRatingKey
            ) j++
        }
        val count = j - i
        if (count > 4) {
            out += first.copy(
                ratingKey = first.parentRatingKey,
                type = "season",
                title = first.parentTitle,
                thumb = first.grandparentThumb.ifBlank { first.parentThumb },
                index = first.parentIndex,
                added = count,
            )
        } else {
            out += items.subList(i, j)
        }
        i = j
    }
    return out.take(24)
}

/** Bibliothèques choisies pour ce serveur (toutes par défaut, au moins une). */
private fun railEntries(shown: List<LibrarySection>): List<RailEntry> =
    listOf(RailEntry("home", "Accueil", "A", "home"), RailEntry("search", "Recherche", "R", "search")) +
        shown.map { RailEntry(it.key, it.title, sectionGlyph(it), it.type) }

private fun visibleSections(store: SessionStore, server: ServerEndpoint, all: List<LibrarySection>): List<LibrarySection> {
    val hidden = store.hiddenLibraries(server.clientIdentifier)
    val shown = all.filter { it.key !in hidden }
    return shown.ifEmpty { all }
}

private fun seriesPoster(item: Metadata): Metadata {
    val poster = when (item.type) {
        "episode" -> item.grandparentThumb.ifBlank { item.parentThumb }
        "season" -> item.parentThumb.ifBlank { item.grandparentThumb }
        else -> ""
    }
    return if (poster.isBlank()) item else item.copy(thumb = poster)
}

private fun newsRows(client: PlexClient, server: ServerEndpoint, sectionKey: String, isShow: Boolean = false): List<Shelf> {
    val deck = runCatching { client.onDeck(server, sectionKey) }.getOrDefault(emptyList())
    val seen = deck.map { it.ratingKey }.toSet()
    val again = runCatching { client.watched(server, sectionKey) }.getOrDefault(emptyList())
        .filter { it.ratingKey !in seen }
    return listOf(
        Shelf("Continuer à regarder", deck),
        Shelf(
            "Nouveautés",
            runCatching {
                if (isShow) groupAdditions(client.recentlyAdded(server, sectionKey, 80)) else client.recentlyAdded(server, sectionKey)
            }.getOrDefault(emptyList()),
        ),
        Shelf("Les plus regardés", runCatching { client.mostWatched(server, sectionKey) }.getOrDefault(emptyList())),
        Shelf("Regarder à nouveau", again),
    )
}

private fun bitrateFor(height: Int?): Int? = when (height) {
    2160 -> 20000
    1080 -> 8000
    720 -> 4000
    480 -> 2000
    else -> null
}

private fun buildQueue(
    client: PlexClient,
    server: ServerEndpoint,
    feature: Metadata,
    settings: PlaybackSettings,
    clientId: String,
    resolution: String = "",
    audioLanguage: String = settings.audioLanguage,
    subtitleMode: String = if (settings.subtitleLanguage == "off") "off" else "full",
    resume: Boolean = false,
    bonus: Boolean = true,
): List<ClipPlay> {
    // Bonus du serveur (« lire la piste 1 avant le film ») : demandé comme le client officiel, avec 0 bande-annonce
    // de cinéma ; le serveur choisit seul (au hasard ou à la suite). Pas de bonus quand on reprend un film en cours.
    val resuming = resume && feature.viewOffset > 0L
    val asked = if (feature.type == "movie" && bonus && !resuming) {
        runCatching {
            client.cinemaQueue(server, feature.ratingKey, 0)
        }.getOrDefault(emptyList())
    } else {
        emptyList()
    }
    val items = if (asked.any { it.ratingKey == feature.ratingKey }) asked else listOf(feature)
    return items.mapNotNull { item ->
        runCatching {
        val kind = when {
            item.ratingKey == feature.ratingKey -> ClipKind.FEATURE
            item.type.equals("trailer", true) || item.extraType == 1 -> ClipKind.TRAILER
            else -> ClipKind.PREROLL
        }
        val meta = if (item.Media.isEmpty() && item.ratingKey.isNotBlank()) {
            client.metadata(server, item.ratingKey) ?: item
        } else {
            item
        }
        val label = when (kind) {
            ClipKind.FEATURE -> "Film"
            ClipKind.TRAILER -> "Bande-annonce"
            ClipKind.PREROLL -> "Bonus"
        }
        val numeric = meta.ratingKey.isNotEmpty() && meta.ratingKey.all { it.isDigit() }
        val featureItem = kind == ClipKind.FEATURE
        val picked = MovieFacts.bestMedia(meta) ?: meta.Media.firstOrNull()
        val part = picked?.Part?.firstOrNull()?.key
        val video = picked?.videoCodec
        val audio = picked?.audioCodec
        val chosenAudio = if (featureItem) audioLanguage else ""
        val chosenSub = if (featureItem) MovieFacts.subtitleOption(meta, subtitleMode) else null
        val chosenAudioCodec = if (chosenAudio.isNotBlank()) MovieFacts.audioCodec(meta, chosenAudio) else null
        val plannedAudio = when ((chosenAudioCodec ?: audio).orEmpty().lowercase()) {
            "dca", "dtshd", "dts-hd" -> "dts"
            else -> (chosenAudioCodec ?: audio)?.ifBlank { null }
        }
        val choice = DeliveryPlanner.choose(
            settings.engine,
            settings.player,
            video,
            plannedAudio,
            DeviceCodecs::decodes,
            DeviceCodecs.ffmpegPresent(),
        )
        val audioId = if (chosenAudio.isNotBlank()) MovieFacts.audioStreamId(meta, chosenAudio) else null
        val target = if (featureItem) MovieFacts.targetHeight(resolution) else null
        val nativeHeight = MovieFacts.targetHeight(picked?.videoResolution.orEmpty())
        val downscale = target != null && (nativeHeight == null || target < nativeHeight)
        // Baisse de résolution d'un film HEVC : la TV décode le HEVC, on demande donc du HEVC au serveur (sinon il doit convertir le HDR
        // en SDR et encoder en H.264 sur le processeur, trop lent pour un 4K).
        val hevcOut = downscale && settings.player != PlayerKind.MPV && video.orEmpty().lowercase() in setOf("hevc", "h265") && DeviceCodecs.decodes("hevc")
        val heavyAudio = (settings.compat || feature.ratingKey in settings.compatTitles) && featureItem && settings.player != PlayerKind.MPV &&
            (chosenAudioCodec ?: audio).orEmpty().lowercase().let {
                it in setOf("dca", "dts", "dtshd", "dts-hd", "truehd")
            }
        val delivery = if (heavyAudio) Delivery.TRANSCODE else choice.delivery
        val videoCopy = video.isNullOrBlank() || DeviceCodecs.decodes(video)
        val directPlay = delivery == Delivery.DIRECT && numeric && !downscale
        val exo = settings.player != PlayerKind.MPV
        val url = when {
            !numeric -> {
                if (part.isNullOrBlank() || !(part.startsWith("/library/") || part.startsWith("http"))) {
                    return@runCatching null
                }
                StreamUrlFactory.direct(server.uri, part, server.accessToken)
            }
            delivery == Delivery.DIRECT && !part.isNullOrBlank() && !downscale ->
                StreamUrlFactory.direct(server.uri, part, server.accessToken)
            else -> StreamUrlFactory.transcode(
                server.uri,
                meta.ratingKey,
                server.accessToken,
                clientId,
                audioStreamId = audioId,
                subtitleStreamId = chosenSub?.id?.toIntOrNull() ?: 0,
                mediaIndex = meta.Media.indexOf(picked).let { if (it < 0) 0 else it },
                videoResolution = if (downscale) target.toString() else null,
                maxVideoBitrate = if (downscale) bitrateFor(target) else null,
                videoCodec = if (exo && downscale) (if (hevcOut) "hevc" else "h264") else null,
                audioCodec = if (exo && !directPlay) (if (heavyAudio) DeviceCodecs.convertedAudio() else "aac") else null,
                // Seul le son est converti : l'image est copiée telle quelle quand la TV sait la décoder.
                directStream = !downscale && videoCopy,
                hevcOutput = hevcOut,
                maxAudioChannels = if (exo) 6 else null,
            )
        }
        // Version « français » du même flux, pour le bouton FR du lecteur (relance à la même position).
        val frStream = if (featureItem && numeric) MovieFacts.frenchStream(meta) else null
        fun streamFor(audioId: Int, subId: Int = chosenSub?.id?.toIntOrNull() ?: 0, burn: Boolean = false) = StreamUrlFactory.transcode(
            server.uri,
            meta.ratingKey,
            server.accessToken,
            clientId,
            audioStreamId = audioId,
            subtitleStreamId = subId,
            mediaIndex = meta.Media.indexOf(picked).let { if (it < 0) 0 else it },
            videoResolution = if (downscale) target.toString() else null,
            maxVideoBitrate = if (downscale) bitrateFor(target) else null,
            videoCodec = if (exo && downscale) (if (hevcOut) "hevc" else "h264") else null,
            audioCodec = if (exo) (if (heavyAudio) DeviceCodecs.convertedAudio() else "aac") else null,
            directStream = !downscale && videoCopy,
            burnSubtitles = burn,
            hevcOutput = hevcOut,
            maxAudioChannels = if (exo) 6 else null,
        )
        // Sous-titres pour le bouton ST : la liste de Plex, avec le fichier à charger pour les sous-titres externes textuels.
        val textCodecs = setOf("srt", "ass", "ssa", "vtt", "subrip")
        val subChoices = if (featureItem && numeric) {
            // Seulement les pistes de la version réellement lue (un film en plusieurs versions a des identifiants différents).
            val ownIds = picked?.Part?.flatMap { it.Stream }?.filter { it.streamType == 3 }?.map { it.id.toString() }?.toSet().orEmpty()
            MovieFacts.subtitleOptions(meta).filter { it.id != "off" && (ownIds.isEmpty() || it.id in ownIds) }.mapNotNull { option ->
                val id = option.id.toIntOrNull() ?: return@mapNotNull null
                SubChoice(
                    id, option.label, option.language, option.forced, option.codec, option.external,
                    if (option.external && option.codec.lowercase() in textCodecs) client.fileUrl(server, option.key).orEmpty() else "",
                )
            }
        } else {
            emptyList()
        }
        val frUrl = if (frStream != null) streamFor(frStream.id) else ""
        // Liste des pistes audio pour l'appui long sur FR : chaque piste a son flux converti prêt.
        val audioChoices = if (featureItem && numeric) {
            MovieFacts.audioStreams(meta).map { stream ->
                AudioChoice(stream.id, MovieFacts.audioLabel(stream), streamFor(stream.id), stream.id == audioId, MovieFacts.code(stream))
            }
        } else {
            emptyList()
        }
        // Le choix de sous-titres de départ est enregistré sur le serveur avant le lancement : sinon la piste « sélectionnée » du compte
        // (choisie un jour avec une autre appli Plex) est ajoutée au flux converti, même quand l'appli demande « aucun ».
        val serverSub = picked?.Part?.flatMap { it.Stream }?.firstOrNull { it.streamType == 3 && it.selected }?.id ?: 0
        val wantedSub = chosenSub?.id?.toIntOrNull() ?: 0
        val syncNote = if (featureItem && numeric && serverSub != wantedSub) {
            val done = runCatching { client.setSubtitleStream(server, MovieFacts.partId(meta), wantedSub) }.getOrDefault(false)
            "sélection serveur : #$serverSub → #$wantedSub (" + (if (done) "enregistrée" else "ÉCHEC") + ")"
        } else {
            "sélection serveur : #$serverSub (inchangée)"
        }
        // Même chose pour la piste audio d'un flux converti : le serveur transcode la piste « sélectionnée » du fichier, pas celle de l'adresse.
        val serverAudio = picked?.Part?.flatMap { it.Stream }?.firstOrNull { it.streamType == 2 && it.selected }?.id ?: 0
        val wantedAudio = audioId ?: 0
        val audioNote = if (featureItem && numeric && !directPlay && wantedAudio > 0 && serverAudio != wantedAudio) {
            val done = runCatching { client.setAudioStream(server, MovieFacts.partId(meta), wantedAudio) }.isSuccess
            "sélection serveur audio : #$serverAudio → #$wantedAudio (" + (if (done) "enregistrée" else "ÉCHEC") + ")"
        } else {
            "sélection serveur audio : #$serverAudio (inchangée)"
        }
        // Diagnostic (panneau « i ») : versions du film et pistes de sous-titres telles que Plex les décrit.
        val diagText = if (featureItem) {
            val rows = mutableListOf("Versions : ${meta.Media.size} · lue : #${meta.Media.indexOf(picked)} · réglage ST départ : \"$subtitleMode\" · choisi : ${chosenSub?.id ?: "aucun"}")
            rows += syncNote
            rows += audioNote
            rows += "Plan : ${delivery.name} · baisse de résolution : ${if (downscale) "oui (cible $target, source ${nativeHeight ?: "?"}, sortie ${if (hevcOut) "HEVC" else "H.264"})" else "non"}"
            meta.Media.forEachIndexed { number, media ->
                val subs = media.Part.flatMap { it.Stream }.filter { it.streamType == 3 }
                rows += "V$number ${media.videoResolution} ${media.videoCodec} ${media.Part.firstOrNull()?.container.orEmpty()} · ${subs.size} ST"
                subs.take(8).forEach { st ->
                    rows += "  #${st.id} ${st.codec} ${st.languageCode.ifBlank { st.language }}" +
                        (if (st.forced) " forcé" else "") + (if (st.selected) " SÉLECTIONNÉ" else "") + (if (st.key.isNotBlank()) " externe" else "")
                }
            }
            rows.joinToString("\n")
        } else {
            ""
        }
        val info = listOf(if (heavyAudio) "mode compatible : son converti" else choice.reason, video, audio, picked?.videoResolution)
            .filter { !it.isNullOrBlank() }
            .joinToString(" · ")
        val externalSub = chosenSub != null && chosenSub.external && directPlay
        ClipPlay(
            url,
            label,
            kind,
            meta.ratingKey.ifBlank { feature.ratingKey },
            info,
            chosenAudio,
            chosenSub?.language.orEmpty(),
            when {
                chosenSub == null -> "off"
                chosenSub.forced -> "forced"
                else -> "full"
            },
            direct = directPlay,
            fileResolution = picked?.videoResolution.orEmpty(),
            subtitleCodec = chosenSub?.codec.orEmpty(),
            subtitleUrl = if (externalSub) client.fileUrl(server, chosenSub.key).orEmpty() else "",
            audioCodec = chosenAudioCodec.orEmpty(),
            frUrl = frUrl,
            audios = audioChoices,
            frStreamId = frStream?.id ?: 0,
            frCodec = frStream?.codec.orEmpty(),
            partId = if (featureItem) MovieFacts.partId(meta) else 0L,
            subs = subChoices,
            subStart = chosenSub?.id?.toIntOrNull() ?: 0,
            audioStart = audioId ?: 0,
            streamFor = if (featureItem && numeric) { a, sub -> streamFor(a, sub, sub > 0) } else null,
            diag = diagText,
            sourceAspect = if (picked != null && picked.width > 0 && picked.height > 0) picked.width.toFloat() / picked.height else 0f,
            closeOnEnd = featureItem && feature.type == "movie",
            startMs = if (resume && featureItem) {
                (feature.viewOffset.takeIf { it > 0L } ?: meta.viewOffset).let { (it - 5000L).coerceAtLeast(0L) }
            } else {
                0L
            },
        )
        }.getOrNull()
    }
}

private fun trailerQueue(
    client: PlexClient,
    server: ServerEndpoint,
    feature: Metadata,
    settings: PlaybackSettings,
    clientId: String,
): List<ClipPlay> {
    return client.extras(server, feature.ratingKey)
        .filter { it.extraType == 1 || it.type == "trailer" }
        .mapNotNull { extra ->
            val meta = client.metadata(server, extra.ratingKey) ?: extra
            val part = meta.Media.firstOrNull()?.Part?.firstOrNull()?.key ?: return@mapNotNull null
            val video = meta.Media.firstOrNull()?.videoCodec
            val audio = meta.Media.firstOrNull()?.audioCodec
            val choice = DeliveryPlanner.choose(
                settings.engine,
                settings.player,
                video,
                audio,
                DeviceCodecs::decodes,
                DeviceCodecs.ffmpegPresent(),
            )
            val direct = choice.delivery == Delivery.DIRECT
            val url = if (direct) {
                StreamUrlFactory.direct(server.uri, part, server.accessToken)
            } else {
                StreamUrlFactory.transcode(server.uri, extra.ratingKey, server.accessToken, clientId)
            }
            ClipPlay(
                url,
                meta.title.ifBlank { "Bande-annonce" },
                tv.plexiplay.plex.preroll.ClipKind.TRAILER,
                extra.ratingKey,
                "Bande-annonce",
                direct = direct,
            )
        }
}

private fun runOnUi(block: () -> Unit) {
    android.os.Handler(android.os.Looper.getMainLooper()).post(block)
}


private data class EpisodeChoice(val resolution: String, val audio: String, val subtitleId: String)

private fun episodeLabel(clip: ClipPlay, episode: Metadata): ClipPlay {
    val number = if (episode.parentIndex > 0 && episode.index > 0) "S${episode.parentIndex} · E${episode.index}" else ""
    val label = listOf(number, episode.title).filter { it.isNotBlank() }.joinToString(" — ")
    return clip.copy(label = label.ifBlank { "Épisode" })
}

/**
 * File de lecture d'une série : l'épisode choisi puis les suivants (jusqu'à 5), avec le même
 * audio et le même type de sous-titres, sans toucher à la file des films.
 */
private fun buildEpisodeQueue(
    client: PlexClient,
    server: ServerEndpoint,
    showKey: String,
    first: Metadata,
    settings: PlaybackSettings,
    clientId: String,
    picked: EpisodeChoice?,
    remembered: SeriesChoice?,
    onChoice: (SeriesChoice) -> Unit,
    resume: Boolean = false,
): List<ClipPlay> {
    val firstFull = client.metadata(server, first.ratingKey) ?: first
    val resolution = picked?.resolution ?: "native"
    val audioCodes = MovieFacts.audioChoicesLabeled(firstFull).map { it.first }
    val audio = picked?.audio
        ?: remembered?.audio?.takeIf { it in audioCodes }
        ?: MovieFacts.defaultAudio(firstFull, settings.audioLanguage)
    val firstSub = picked?.subtitleId
        ?: if (remembered != null) {
            MovieFacts.subtitleMatch(firstFull, remembered.subtitleLanguage, remembered.subtitleForced)
        } else {
            MovieFacts.defaultSubtitle(firstFull, settings.subtitleLanguage)
        }
    val option = MovieFacts.subtitleOption(firstFull, firstSub)
    val language = option?.language.orEmpty()
    val forced = option?.forced ?: false
    onChoice(SeriesChoice(audio, language, forced))
    val queue = mutableListOf<ClipPlay>()
    queue += buildQueue(client, server, firstFull, settings, clientId, resolution, audio, firstSub, resume)
        .map { episodeLabel(it, firstFull) }
    if (queue.isEmpty()) return queue
    val key = showKey.ifBlank { first.grandparentRatingKey }
    val all = if (key.isBlank()) emptyList() else runCatching { client.allEpisodes(server, key) }.getOrDefault(emptyList())
    val position = all.indexOfFirst { it.ratingKey == first.ratingKey }
    if (position >= 0) {
        all.drop(position + 1)
            .filter { it.parentIndex > 0 || first.parentIndex == 0 }
            .take(5)
            .forEach { episode ->
                val meta = runCatching { client.metadata(server, episode.ratingKey) }.getOrNull() ?: return@forEach
                val subtitle = MovieFacts.subtitleMatch(meta, language, forced)
                val built = runCatching {
                    buildQueue(client, server, meta, settings, clientId, resolution, audio, subtitle)
                }.getOrDefault(emptyList())
                queue += built.map { episodeLabel(it, meta) }
            }
    }
    return queue
}
