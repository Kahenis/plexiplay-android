package tv.plexiplay

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.background
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
import tv.plexiplay.plex.PlexHttpException
import tv.plexiplay.plex.auth.PinAuthClient
import tv.plexiplay.plex.model.HubEntry
import tv.plexiplay.plex.model.LibrarySection
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.plex.model.ServerEndpoint
import tv.plexiplay.plex.preroll.ClipKind
import tv.plexiplay.ui.auth.LoginScreen
import tv.plexiplay.ui.boot.BootScreen
import tv.plexiplay.ui.detail.MovieDetailScreen
import tv.plexiplay.ui.detail.MovieFacts
import tv.plexiplay.ui.detail.SeriesScreen
import tv.plexiplay.ui.detail.TrailerOption
import tv.plexiplay.ui.detail.TrailerStrip
import tv.plexiplay.ui.detail.trailerLanguage
import tv.plexiplay.ui.player.AudioChoice
import tv.plexiplay.ui.player.SubChoice
import tv.plexiplay.ui.player.ClipPlay
import tv.plexiplay.ui.player.PlayerScreen
import tv.plexiplay.ui.player.SkipMarker
import tv.plexiplay.ui.player.titleInfoOf
import tv.plexiplay.ui.player.fileFrameRate
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

/** Suivi d'une lecture d'épisodes : dernier épisode de la file en cours et suite préparée en arrière-plan. */
private class EpisodeRun(val showKey: String, val resolution: String?, val lastKey: String) {
    @Volatile var loading = false
    @Volatile var done = false
    @Volatile var next: List<ClipPlay>? = null
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
                // Téléphone / tablette : l'appli laisse la place à la barre de navigation d'Android (◁ ○ □),
                // en bas en portrait, sur le côté en paysage. Pendant la lecture les barres sont masquées : marge nulle.
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black)
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars),
                ) {
                    App(store, client, auth, posters, updates, ::applyLanguage)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        tv.plexiplay.update.KeepOpen.on = false
    }

    /** Application mise en arrière-plan (appel, autre appli, Home) : la lecture se met en pause, l'appli reste ouverte. */
    override fun onStop() {
        super.onStop()
        tv.plexiplay.ui.player.BackgroundPause.action?.invoke()
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
    remember {
        tv.plexiplay.ui.theme.ThemeMode.neon = store.theme() == "neon" || store.theme() == "future"
        tv.plexiplay.ui.theme.ThemeMode.future = store.theme() == "future"
        true
    }
    var themeVolume by remember { mutableIntStateOf(store.themeVolume()) }
    var prerollVolume by remember { mutableIntStateOf(store.prerollVolume()) }
    var sagas by remember { mutableStateOf(store.sagaMode()) }
    var movieTotal by remember { mutableIntStateOf(-1) }
    var prerollPauseUntil by remember { mutableStateOf(store.prerollPauseUntil()) }
    prerollPauseUntilMs = prerollPauseUntil
    PrerollFolder.store = store
    var token by remember { mutableStateOf(store.token()) }
    var accountName by remember { mutableStateOf(store.accountName()) }
    // Identifiant plex.tv du compte connecté, haché pour servir de votant à la note Plexiplay (VotesClient) — jamais son nom ni son jeton.
    var accountId by remember { mutableStateOf(store.accountId()) }
    val voter = remember(accountId) { if (accountId > 0L) tv.plexiplay.plex.votes.VotesClient.voterHash(accountId) else "" }
    LaunchedEffect(token) {
        val current = token
        if (current.isNullOrBlank()) {
            accountName = ""
            accountId = 0L
            return@LaunchedEffect
        }
        val account = withContext(Dispatchers.IO) { client.account(current) }
        if (account != null) {
            if (account.name.isNotBlank()) { store.saveAccountName(account.name); accountName = account.name }
            store.saveAccountId(account.id)
            accountId = account.id
        }
    }
    var servers by remember { mutableStateOf(store.servers()) }
    var serverIndex by remember { mutableStateOf(store.activeServerIndex()) }
    var settings by remember { mutableStateOf(store.settings()) }
    // Dossier des prérolls reconnu absent : le réglage se grise, l'écran relit les réglages.
    PrerollFolder.onLocked = { runOnUi { settings = store.settings() } }
    var entries by remember { mutableStateOf(railEntries(emptyList())) }
    var sections by remember { mutableStateOf<List<LibrarySection>>(emptyList()) }
    var picker by remember { mutableStateOf(false) }
    var stylePicker by remember { mutableStateOf(false) }
    // « Idée TV » : popup de 5 films tirés au hasard ; l'état reste ici pour retrouver les 5 films après une bande-annonce ou une fiche.
    var ideaOpen by remember { mutableStateOf(false) }
    var ideaChoice by remember { mutableStateOf<String?>(null) }
    var ideaCard by remember { mutableStateOf<tv.plexiplay.ui.shell.StyleCard?>(null) }
    var ideaFilms by remember { mutableStateOf<List<Metadata>>(emptyList()) }
    var ideaBusy by remember { mutableStateOf(false) }
    var ideaNote by remember { mutableStateOf("") }
    var style by remember { mutableStateOf<tv.plexiplay.ui.shell.StyleCard?>(null) }
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
    // Chargement de l'accueil impossible : message simple + « Réessayer » ; session refusée par Plex : retour à la connexion.
    var loadFailure by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var sessionNotice by remember { mutableStateOf("") }
    var inviteStatus by remember { mutableStateOf("") }
    var pendingInvites by remember { mutableStateOf<List<tv.plexiplay.plex.PlexInvite>?>(null) }
    var inviteBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Choisissez une catégorie") }
    var clips by remember { mutableStateOf<List<ClipPlay>?>(null) }
    // Bande de choix des bandes-annonces (plusieurs disponibles), et celle à rouvrir quand la bande-annonce lancée depuis elle se termine.
    var trailerPick by remember { mutableStateOf<List<TrailerOption>?>(null) }
    var trailerReturn by remember { mutableStateOf<List<TrailerOption>?>(null) }
    var episodeRun by remember { mutableStateOf<EpisodeRun?>(null) }
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
            trailerPick != null && clips == null -> trailerPick = null
            ideaOpen && clips == null && movie == null && series == null && confirm == null -> ideaOpen = false
            stylePicker -> stylePicker = false
            picker -> picker = false
            clips != null -> if (playerBack.value?.invoke() != true) {
                clips = null
                // Retour pendant une bande-annonce choisie dans la bande : la bande se rouvre.
                val back = trailerReturn
                if (back != null) {
                    trailerReturn = null
                    trailerPick = back
                }
            }
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
    LaunchedEffect(token, reloadTick) {
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
                loadFailure = "Aucun serveur Plex n'a été trouvé sur votre compte. Vérifiez qu'il est allumé et connecté à Internet, puis réessayez."
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
            if (error is PlexHttpException && (error.code == 401 || error.code == 403)) {
                // Plex ne reconnaît plus cette session : on repart de l'écran de connexion.
                store.clearSession()
                servers = emptyList()
                sessionNotice = "Votre session Plex a expiré. Veuillez vous reconnecter."
                token = null
            } else {
                status = error.message ?: "Serveur inaccessible"
                loadFailure = friendlyLoadError(error)
            }
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

    val failure = loadFailure
    if (!token.isNullOrBlank() && failure != null) {
        tv.plexiplay.ui.boot.RetryScreen(
            message = failure,
            onRetry = {
                loadFailure = null
                progress = 0.04f
                ready = false
                reloadTick += 1
            },
            onSignIn = {
                loadFailure = null
                store.clearSession()
                servers = emptyList()
                token = null
            },
        )
        return
    }

    if (token.isNullOrBlank()) {
        LoginScreen(auth, sessionNotice) { received ->
            sessionNotice = ""
            loadFailure = null
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

    // Nombre total de films de la bibliothèque (sagas comprises), indépendant de l'interrupteur « Sagas ».
    LaunchedEffect(selected, server?.uri, sections) {
        val active = server
        movieTotal = -1
        if (active == null || entries.firstOrNull { it.id == selected }?.type != "movie") return@LaunchedEffect
        movieTotal = withContext(Dispatchers.IO) { client.totalSize(active, selected) }
    }

    LaunchedEffect(selected, pane, stack, collection?.ratingKey, server?.uri, sections, style, sagas) {
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
                    pane == LibraryPane.LIBRARY && style != null -> Loaded.Grid(
                        client.itemsByGenre(active, selected, style!!.keys),
                        "Style : " + style!!.label,
                    )
                    pane == LibraryPane.LIBRARY && sagas && entries.firstOrNull { it.id == selected }?.type == "movie" -> Loaded.Grid(
                        client.itemsWithCollections(active, selected),
                        entries.firstOrNull { it.id == selected }?.title ?: "Bibliothèque",
                    )
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
        stack.joinToString(",") { it.ratingKey } + "|" + (collection?.ratingKey ?: "") + "|" + (style?.label ?: "")
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
                    if (built.isEmpty()) {
                        playError = "Aucun fichier lisible"
                    } else {
                        episodeRun = EpisodeRun(showKey, picked?.resolution, built.last().ratingKey)
                        clips = built
                    }
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
        covered = stylePicker || ideaOpen || movie != null || series != null || clips != null || confirm != null || picker || startupOffer != null || pendingInvites != null,
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
        count = if (pane == LibraryPane.LIBRARY && items.isNotEmpty() && (style != null || entries.firstOrNull { it.id == selected }?.type == "show")) {
            items.size
        } else if (pane == LibraryPane.LIBRARY && items.isNotEmpty() && !inside && movieTotal >= 0 && entries.firstOrNull { it.id == selected }?.type == "movie") {
            movieTotal
        } else {
            -1
        },
        styleActive = style != null && pane == LibraryPane.LIBRARY,
        onStyle = { stylePicker = true },
        onIdea = {
            ideaChoice = null
            ideaCard = null
            ideaFilms = emptyList()
            ideaNote = ""
            ideaOpen = true
        },
        onSelect = {
            selected = it.id
            stack = emptyList()
            collection = null
            style = null
            pane = LibraryPane.NEWS
        },
        onOpen = { item ->
            when {
                showTabs && (pane == LibraryPane.COLLECTIONS || pane == LibraryPane.LIBRARY) && item.type == "collection" ->
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
            style = null
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
            } else if (style != null) {
                style = null
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
                    theme = if (tv.plexiplay.ui.theme.ThemeMode.future) "future" else if (tv.plexiplay.ui.theme.ThemeMode.neon) "neon" else "classic",
                    themeVolume = themeVolume,
                    onThemeVolume = {
                        store.saveThemeVolume(it)
                        themeVolume = it
                    },
                    accountName = accountName,
                    prerollPauseUntil = prerollPauseUntil,
                    onPrerollPause = {
                        prerollPauseUntil = store.startPrerollPause()
                        prerollPauseUntilMs = prerollPauseUntil
                    },
                    onPrerollResume = {
                        store.cancelPrerollPause()
                        prerollPauseUntil = 0L
                        prerollPauseUntilMs = 0L
                    },
                    prerollVolume = prerollVolume,
                    onPrerollVolume = {
                        store.savePrerollVolume(it)
                        prerollVolume = it
                    },
                    onTheme = {
                        if (it == "future") {
                            // Le thème Futuriste demande une TV puissante : on prévient avant de l'appliquer.
                            confirm = "future-theme"
                        } else {
                            store.saveTheme(it)
                            tv.plexiplay.ui.theme.ThemeMode.neon = it == "neon"
                            tv.plexiplay.ui.theme.ThemeMode.future = false
                        }
                    },
                )
            }
        },
    )
    val opened = movie
    val activeServer = server
    // Bouton « Trailer » (film, série, Idée TV) : aucune → message ; une seule → lue tout de suite ; plusieurs → bande de choix.
    val openTrailers: (Metadata, () -> Unit) -> Unit = { feature, onNone ->
        val active = server
        if (active != null) {
            Thread {
                val options = runCatching { trailerQueue(client, active, feature, settings, store.clientId()) }.getOrDefault(emptyList())
                runOnUi {
                    when {
                        options.isEmpty() -> onNone()
                        options.size == 1 -> { trailerReturn = null; clips = listOf(options.first().clip) }
                        else -> trailerPick = options
                    }
                }
            }.start()
        }
    }
    if (opened != null && activeServer != null && clips == null) {
        MovieDetailScreen(
            seed = opened,
            client = client,
            server = activeServer,
            settings = settings,
            onForceConvert = { key, on ->
                store.setConvert(key, on)
                settings = store.settings()
            },
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
                playError = ""
                openTrailers(full) { playError = "Aucune bande-annonce pour ce film." }
            },
            onClose = closeDetail,
            notice = playError,
            themeVolume = themeVolume / 100f,
            posters = posters,
            accountToken = token.orEmpty(),
            voter = voter,
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
            onTrailers = { full ->
                playError = ""
                openTrailers(full) { playError = "Aucune bande-annonce pour cette série." }
            },
            voter = voter,
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
            onForceConvert = { key, on ->
                store.setConvert(key, on)
                settings = store.settings()
            },
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
    val pick = trailerPick
    val pickServer = server
    if (pick != null && pickServer != null && clips == null) {
        TrailerStrip(
            options = pick,
            wide = { path -> client.wideSources(pickServer, path, 640, 360) },
            posters = posters,
            onPick = { option ->
                trailerReturn = pick
                trailerPick = null
                clips = listOf(option.clip)
            },
            onClose = { trailerPick = null },
        )
    }
    if (clips != null) {
      androidx.compose.runtime.key(clips!!.firstOrNull()?.url) {
        val reportState = androidx.compose.runtime.remember { ReportState() }
        // Identifiant de session des rapports : celui du flux converti du film (paramètre « session » de l'adresse), sinon un identifiant neuf.
        val reportSession = androidx.compose.runtime.remember {
            clips!!.firstOrNull { it.kind == tv.plexiplay.plex.preroll.ClipKind.FEATURE && !it.direct }
                ?.let { runCatching { android.net.Uri.parse(it.url).getQueryParameter("session") }.getOrNull() }
                ?.takeIf { it.isNotBlank() }
                ?: java.util.UUID.randomUUID().toString()
        }
        PlayerScreen(
            clips = clips!!,
            aspect = settings.aspect,
            engine = settings.engine,
            playerKind = settings.player,
            prerollVolume = prerollVolume / 100f,
            onProgress = { key, state, position, duration ->
                // Enchaînement sans fin des épisodes : pendant le dernier de la file, la suite est préparée ;
                // à la fin de cet épisode, une nouvelle file prend le relais.
                val run = episodeRun
                if (run != null && key == run.lastKey && !run.done) {
                    if (!run.loading) {
                        run.loading = true
                        val wanted = server
                        val currentSettings = settings
                        val remembered = store.seriesChoice(run.showKey)
                        if (wanted != null) {
                            Thread {
                                val result = runCatching { buildFollowOn(client, wanted, run, currentSettings, store.clientId(), remembered) }.getOrNull()
                                if (result == null) run.loading = false else run.next = result
                            }.start()
                        }
                    }
                    val queued = run.next
                    if (state == "playing" && duration > 0L && position >= duration - 1500L && !queued.isNullOrEmpty()) {
                        run.done = true
                        runOnUi {
                            episodeRun = EpisodeRun(run.showKey, run.resolution, queued.last().ratingKey)
                            clips = queued
                        }
                    }
                }
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
            onExit = {
                clips = null
                // Bande-annonce lancée depuis la bande de choix : la bande se rouvre pour en choisir une autre.
                val back = trailerReturn
                if (back != null) {
                    trailerReturn = null
                    trailerPick = back
                }
            },
            autoSkipIntro = settings.skipIntro,
            autoSkipCredits = settings.skipCredits,
            skipMarkers = { key ->
                val active = server
                if (active == null) emptyList() else withContext(Dispatchers.IO) {
                    runCatching {
                        client.metadata(active, key, markers = true)?.Marker.orEmpty()
                            .filter { (it.type == "intro" || it.type == "credits") && it.endTimeOffset > it.startTimeOffset }
                            .map { SkipMarker(it.type, it.startTimeOffset, it.endTimeOffset) }
                    }.getOrDefault(emptyList())
                }
            },
            titleInfo = { key ->
                val active = server
                if (active == null) null else withContext(Dispatchers.IO) { runCatching { titleInfoOf(client, active, key) }.getOrNull() }
            },
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
            compat = settings.compat || (clips?.firstOrNull { it.kind == tv.plexiplay.plex.preroll.ClipKind.FEATURE }?.ratingKey in settings.compatTitles) ||
                (settings.dvNoSkip && clips?.any { it.kind == tv.plexiplay.plex.preroll.ClipKind.FEATURE && it.dolbyVision } == true),
            onPrerollStarted = { clip -> if (clip.prerollFile.isNotBlank()) store.setPrerollLast(clip.prerollFile) },
            onPrerollError = { clip, fileFault -> if (fileFault && clip.prerollKey.isNotBlank()) store.addPrerollBad(clip.prerollKey) },
            onCompat = {
                val active = server
                val featureClip = clips?.firstOrNull { it.kind == tv.plexiplay.plex.preroll.ClipKind.FEATURE }
                val key = featureClip?.ratingKey
                val wasResumed = (featureClip?.startMs ?: 0L) > 0L
                if (active != null && key != null && settings.compatMode != "off" && key !in settings.convertTitles) {
                    // Première étape : mode compatible, seulement s'il peut aider (son DTS / TrueHD, ou image 4K). Sinon, ou s'il a déjà été tenté :
                    // conversion complète par le serveur, mémorisée pour ce film.
                    val infoText = featureClip.info.lowercase()
                    val compatCanHelp = listOf("dca", "dts", "truehd").any { it in infoText } ||
                        featureClip.fileResolution.lowercase() in setOf("4k", "2160")
                    val compatFirst = settings.player != PlayerKind.MPV && !settings.compat && settings.compatMode == "auto" &&
                        key !in settings.compatTitles && compatCanHelp
                    if (compatFirst) store.learnCompat(key) else store.setConvert(key, true)
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
    if (ideaOpen && clips == null && movie == null && series == null) {
        val active = server
        if (active != null) {
            val draw: (tv.plexiplay.ui.shell.StyleCard?) -> Unit = { card ->
                if (!ideaBusy) {
                    ideaBusy = true
                    ideaNote = ""
                    val section = selected
                    val before = ideaFilms.map { it.ratingKey }.toSet()
                    Thread {
                        val picked = runCatching {
                            tv.plexiplay.ui.shell.pickIdeas(client, active, section, card, before)
                        }.getOrDefault(emptyList())
                        runOnUi {
                            ideaFilms = picked
                            ideaBusy = false
                            ideaNote = if (picked.isEmpty()) "Aucun film non vu pour ce choix." else ""
                        }
                    }.start()
                }
            }
            tv.plexiplay.ui.shell.IdeaTvDialog(
                loadStyles = { client.genres(active, selected) },
                choice = ideaChoice,
                films = ideaFilms,
                busy = ideaBusy,
                note = ideaNote,
                thumb = { item -> client.thumbSources(active, item.thumb) },
                posters = posters,
                onChoose = { card, label ->
                    ideaCard = card
                    ideaChoice = label
                    ideaFilms = emptyList()
                    draw(card)
                },
                onRedraw = { draw(ideaCard) },
                onOpen = { item -> openMovie(item) },
                onTrailer = { item -> openTrailers(item) { ideaNote = "Pas de bande-annonce pour ce film." } },
                onClose = { ideaOpen = false },
            )
        }
    }
    if (stylePicker) {
        val active = server
        if (active != null) {
            tv.plexiplay.ui.shell.StylePicker(
                cacheKey = active.uri + "|" + selected,
                loadStyles = { client.genres(active, selected) },
                pickFilms = { card -> client.itemsByGenre(active, selected, card.keys, 12) },
                wide = { path -> client.wideSources(active, path, 640, 360) },
                posters = posters,
                onPick = { card ->
                    stylePicker = false
                    collection = null
                    stack = emptyList()
                    pane = LibraryPane.LIBRARY
                    style = card
                },
                onClose = { stylePicker = false },
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
        val sagaSwitchShown = movie == null && series == null && !stylePicker && !ideaOpen && confirm == null && !picker &&
            startupOffer == null && pendingInvites == null && pane == LibraryPane.LIBRARY && style == null && !inside &&
            entries.firstOrNull { it.id == selected }?.type == "movie"
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopEnd) {
            androidx.compose.foundation.layout.Row(Modifier.padding(top = 10.dp, end = 18.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (sagaSwitchShown) {
                    tv.plexiplay.ui.shell.SagaSwitch(
                        on = sagas,
                        onToggle = {
                            sagas = !sagas
                            store.saveSagaMode(sagas)
                        },
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 10.dp))
                }
                tv.plexiplay.ui.shell.ClockChip()
            }
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
        "future-theme" -> ConfirmDialog(
            question = "Le thème Futuriste utilise beaucoup d'effets animés (faisceaux, zooms, fond animé) : il demande une TV ou un boîtier puissant et récent. Sur un appareil modeste, l'affichage peut ralentir. Activer quand même ? (Vous pourrez revenir au thème Classique ou Néon dans les paramètres.)",
            onYes = {
                confirm = null
                store.saveTheme("future")
                tv.plexiplay.ui.theme.ThemeMode.neon = true
                tv.plexiplay.ui.theme.ThemeMode.future = true
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
    // Mobile (choix de Jeff) : les bibliothèques de vidéos personnelles ne vont pas sur l'accueil.
    val sections = sections.filter { !it.personal }
    if (sections.isEmpty()) {
        publish(end)
        HomeDebug.set(emptyList())
        return emptyList()
    }
    val slice = (end - start) / sections.size
    val homes = sections.mapIndexed { index, section ->
        stage(client, start + index * slice, start + (index + 1) * slice, publish) { homeOf(client, server, section) }
    }
    return finishHome(homes)
}

private fun homeRows(client: PlexClient, server: ServerEndpoint, sections: List<LibrarySection>): List<Shelf> =
    finishHome(sections.filter { !it.personal }.map { homeOf(client, server, it) })

/** Une bibliothèque sur l'accueil : ses deux rayons, dans l'ordre du serveur. */
private class HomeOf(val section: LibrarySection, val allowed: Boolean, val note: String, val shelves: List<Shelf>)

private fun finishHome(homes: List<HomeOf>): List<Shelf> {
    HomeDebug.set(homes.map { it.section.title to it.note })
    val shelves = homes.flatMap { it.shelves }.filter { it.items.isNotEmpty() }
    // Les titres servent de clé : deux rayons ne doivent jamais porter le même.
    val seen = mutableMapOf<String, Int>()
    return shelves.map { shelf ->
        val n = (seen[shelf.title] ?: 0) + 1
        seen[shelf.title] = n
        if (n == 1) shelf else shelf.copy(title = "${shelf.title} (${n})")
    }
}

private fun homeOf(client: PlexClient, server: ServerEndpoint, section: LibrarySection): HomeOf {
    val manage = runCatching { client.homeHubs(server, section.key) }.getOrNull()
    // Réglage du serveur illisible ou vide : on ne filtre pas, seule la liste de gauche compte.
    val allowed = manage.isNullOrEmpty() || manage.any { it.onHome }
    val status = when {
        manage == null -> "réglage du serveur illisible"
        manage.isEmpty() -> "réglage du serveur vide"
        allowed -> "autorisée (${manage.count { it.onHome }}/${manage.size} rayons)"
        else -> "non autorisée sur l'accueil"
    }
    if (!allowed) return HomeOf(section, false, status, emptyList())
    val isShow = section.type == "show"
    val hubs = runCatching { client.sectionHubs(server, section.key) }.getOrNull().orEmpty()
    fun hub(vararg words: String): HubEntry? =
        words.firstNotNullOfOrNull { word -> hubs.firstOrNull { it.hubIdentifier.contains(word, ignoreCase = true) } }
    val continueHub = hub("continue", "ondeck", "inprogress")
    val addedHub = hub("recentlyadded")
    val keep = continueHub?.Metadata
        ?: runCatching { client.onDeck(server, section.key) }.getOrDefault(emptyList())
    val added = addedHub?.Metadata
        ?: runCatching { client.recentlyAdded(server, section.key, 80) }.getOrDefault(emptyList())
    val shaped = { list: List<Metadata> -> if (isShow) list.map { seriesPoster(it) } else list }
    val note = "$status · suite : ${continueHub?.hubIdentifier ?: "standard"} · ajouts : ${addedHub?.hubIdentifier ?: "standard"}"
    return HomeOf(
        section, true, note,
        listOf(
            Shelf("${section.title} - Continuer à voir", shaped(keep)),
            Shelf("${section.title} - Derniers ajouts", shaped(if (isShow) groupAdditions(added) else added.take(HOME_LIMIT))),
        ),
    )
}

private const val HOME_LIMIT = 24

/** Message simple, sans code technique, quand l'accueil ne peut pas être chargé. */
private fun friendlyLoadError(error: Exception): String = when {
    error is PlexHttpException && error.code >= 500 ->
        "Le serveur Plex rencontre un problème (erreur ${error.code}). Réessayez dans quelques instants."
    error is java.net.UnknownHostException || error is java.net.ConnectException || error is java.net.SocketTimeoutException || error is java.io.IOException && error !is PlexHttpException ->
        "Le serveur Plex est injoignable. Vérifiez que le serveur est allumé et que la TV est bien connectée à Internet, puis réessayez."
    else -> "Une erreur est survenue en chargeant vos bibliothèques. Réessayez, ou reconnectez-vous à votre compte."
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

/** Films d'abord, séries ensuite, puis le reste ; chaque groupe par ordre alphabétique (sans accents ni majuscules). */
private fun sortedLibraries(list: List<LibrarySection>): List<LibrarySection> {
    fun words(title: String): List<String> =
        java.text.Normalizer.normalize(title.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
    fun group(section: LibrarySection): Int {
        val w = words(section.title)
        return when {
            w.any { it == "film" || it == "films" || it == "movie" || it == "movies" } -> 0
            w.any { it == "serie" || it == "series" } -> 1
            else -> 2
        }
    }
    return list.sortedWith(compareBy<LibrarySection>({ group(it) }, { words(it.title).joinToString(" ") }))
}

private fun visibleSections(store: SessionStore, server: ServerEndpoint, all: List<LibrarySection>): List<LibrarySection> {
    val hidden = store.hiddenLibraries(server.clientIdentifier)
    val shown = all.filter { it.key !in hidden }
    return sortedLibraries(shown.ifEmpty { all })
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

/** Fin de la pause des prerolls (ms) ; tant que l'heure est antérieure, le bonus du serveur n'est pas demandé. */
@Volatile
private var prerollPauseUntilMs: Long = 0L

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
    val wantPreroll = feature.type == "movie" && bonus && !resuming && System.currentTimeMillis() >= prerollPauseUntilMs
    val sequential = settings.prerollMode == "sequential" && !settings.prerollLocked
    // Séquentiel externe : un fichier du dossier guilard.com/preroll, le suivant dans l'ordre. Dossier absent, injoignable ou sans fichier utilisable :
    // prérolls du serveur Plex (le dossier absent grise en plus le réglage, voir PrerollFolder.choose).
    val chosen = if (wantPreroll && sequential) PrerollFolder.choose() else null
    val remoteClip = (chosen as? PrerollFolder.Choice.Remote)?.file?.let {
        ClipPlay(
            url = it.url,
            label = "Bonus",
            kind = ClipKind.PREROLL,
            ratingKey = "",
            info = "préroll séquentiel · ${it.name}",
            direct = true,
            prerollFile = it.name,
            prerollKey = it.key,
        )
    }
    val asked = if (wantPreroll && remoteClip == null) {
        runCatching {
            client.cinemaQueue(server, feature.ratingKey, 0)
        }.getOrDefault(emptyList())
    } else {
        emptyList()
    }
    val items = if (asked.any { it.ratingKey == feature.ratingKey }) asked else listOf(feature)
    return listOfNotNull(remoteClip) + items.mapNotNull { item ->
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
        // Conversion complète demandée (case « Forcer la conversion » ou repli automatique) : image H.264 et son AAC par le serveur.
        val boxWhy = if (featureItem && numeric) boxReason(settings, picked) else null
        val userForced = featureItem && numeric && feature.ratingKey in settings.convertTitles
        val forced = userForced || boxWhy != null
        // « Box Player sur TV FullHD » : toute conversion complète sort en 1080p maximum, avec le débit du 1080p.
        val boxCap = settings.boxFullHd && forced && featureItem
        val delivery = if (heavyAudio || forced) Delivery.TRANSCODE else choice.delivery
        val videoCopy = video.isNullOrBlank() || DeviceCodecs.decodes(video)
        val directPlay = delivery == Delivery.DIRECT && numeric && !downscale
        val exo = settings.player != PlayerKind.MPV
        // Un seul identifiant de session pour la conversion et les rapports de lecture de ce film.
        val sessionId = java.util.UUID.randomUUID().toString()
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
                session = sessionId,
                audioStreamId = audioId,
                subtitleStreamId = chosenSub?.id?.toIntOrNull() ?: 0,
                mediaIndex = meta.Media.indexOf(picked).let { if (it < 0) 0 else it },
                videoResolution = if (downscale) target.toString() else if (boxCap) "1080" else null,
                maxVideoBitrate = if (downscale) bitrateFor(target) else if (boxCap) bitrateFor(1080) else null,
                videoCodec = if (forced) "h264" else if (exo && downscale) (if (hevcOut) "hevc" else "h264") else null,
                audioCodec = if (forced) "aac" else if (exo && !directPlay) (if (heavyAudio) DeviceCodecs.convertedAudio() else "aac") else null,
                // Seul le son est converti : l'image est copiée telle quelle quand la TV sait la décoder (sauf conversion complète).
                directStream = !forced && !downscale && videoCopy,
                hevcOutput = hevcOut && !forced,
                maxAudioChannels = if (exo || forced) 6 else null,
                audioBoost = settings.audioBoost,
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
            videoResolution = if (downscale) target.toString() else if (boxCap) "1080" else null,
            maxVideoBitrate = if (downscale) bitrateFor(target) else if (boxCap) bitrateFor(1080) else null,
            videoCodec = if (forced) "h264" else if (exo && downscale) (if (hevcOut) "hevc" else "h264") else null,
            audioCodec = if (forced) "aac" else if (exo) (if (heavyAudio) DeviceCodecs.convertedAudio() else "aac") else null,
            directStream = !forced && !downscale && videoCopy,
            burnSubtitles = burn,
            hevcOutput = hevcOut && !forced,
            maxAudioChannels = if (exo || forced) 6 else null,
            audioBoost = settings.audioBoost,
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
        val info = listOf(if (boxWhy != null && !userForced) "conversion complète ($boxWhy : H.264 8 bits SDR 1080p)" else if (forced) "conversion complète (forcée ou repli automatique)" else if (heavyAudio) "mode compatible : son converti" else choice.reason, video, audio, picked?.videoResolution)
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
            fileLines = if (featureItem) sourceLines(settings, picked, boxWhy) else emptyList(),
            sourceAspect = if (picked != null && picked.width > 0 && picked.height > 0) picked.width.toFloat() / picked.height else 0f,
            frameRate = if (featureItem) fileFrameRate(picked) else 0f,
            dolbyVision = featureItem && isDolbyVision(picked),
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

/** Bandes-annonces d'un titre, prêtes à lire, avec leur langue : la bande de choix en montre une vignette chacune. */
private fun trailerQueue(
    client: PlexClient,
    server: ServerEndpoint,
    feature: Metadata,
    settings: PlaybackSettings,
    clientId: String,
): List<TrailerOption> {
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
            val clip = ClipPlay(
                url,
                meta.title.ifBlank { "Bande-annonce" },
                tv.plexiplay.plex.preroll.ClipKind.TRAILER,
                extra.ratingKey,
                "Bande-annonce",
                direct = direct,
            )
            TrailerOption(meta, clip, trailerLanguage(meta))
        }
}

private fun runOnUi(block: () -> Unit) {
    android.os.Handler(android.os.Looper.getMainLooper()).post(block)
}


/** Suite d'une série : l'épisode après le dernier de la file, puis les suivants, avec les mêmes choix audio / sous-titres. */
private fun buildFollowOn(
    client: PlexClient,
    server: ServerEndpoint,
    run: EpisodeRun,
    settings: PlaybackSettings,
    clientId: String,
    remembered: SeriesChoice?,
): List<ClipPlay> {
    val all = client.allEpisodes(server, run.showKey)
    val position = all.indexOfFirst { it.ratingKey == run.lastKey }
    if (position < 0) return emptyList()
    val next = all.drop(position + 1).firstOrNull { it.parentIndex > 0 } ?: return emptyList()
    return buildEpisodeQueue(
        client, server, run.showKey, next, settings, clientId,
        picked = null, remembered = remembered, onChoice = { }, resume = false, resolutionOverride = run.resolution,
    )
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
    resolutionOverride: String? = null,
): List<ClipPlay> {
    val firstFull = client.metadata(server, first.ratingKey) ?: first
    val resolution = picked?.resolution ?: resolutionOverride ?: "native"
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
