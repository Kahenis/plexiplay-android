package tv.plexiplay.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.ui.res.painterResource
import tv.plexiplay.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.plexiplay.plex.model.LibrarySection
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.ThemeMode
import tv.plexiplay.ui.theme.themedButton
import tv.plexiplay.ui.theme.screenBackground
import tv.plexiplay.ui.theme.panelBackground
import tv.plexiplay.ui.theme.Signal
import tv.plexiplay.ui.theme.FocusRing
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Panel
import tv.plexiplay.ui.theme.Paper

enum class Zone { Rail, Content }

enum class LibraryPane { NEWS, LIBRARY, COLLECTIONS }

data class RailEntry(val id: String, val title: String, val glyph: String, val type: String = "")

data class Shelf(val title: String, val items: List<Metadata>)

enum class ForgetKind { CONTINUE, AGAIN }

private data class ForgetMenu(val item: Metadata, val kind: ForgetKind, val anchor: Rect)

private fun forgetKind(title: String): ForgetKind? = when (title) {
    "Continuer à regarder" -> ForgetKind.CONTINUE
    "Regarder à nouveau" -> ForgetKind.AGAIN
    else -> null
}

@Composable
fun ShellScreen(
    serverName: String,
    entries: List<RailEntry>,
    selectedId: String,
    pane: LibraryPane,
    pageKey: String,
    covered: Boolean = false,
    restoreKey: String? = null,
    restoreToken: Int = 0,
    shelves: List<Shelf>,
    items: List<Metadata>,
    useShelves: Boolean,
    showTabs: Boolean,
    thumb: (Metadata) -> List<String>,
    posters: PosterStore,
    backdrop: android.graphics.Bitmap? = null,
    status: String,
    count: Int = -1,
    onSelect: (RailEntry) -> Unit,
    onOpen: (Metadata) -> Unit,
    onPane: (LibraryPane) -> Unit,
    onContentBack: () -> Boolean = { false },
    onForget: (Metadata, ForgetKind) -> Unit = { _, _ -> },
    settings: @Composable () -> Unit = {},
    search: @Composable () -> Unit = {},
) {
    var zone by remember { mutableStateOf(Zone.Rail) }
    // Onglet où l'on était avant le dernier changement : Retour y revient.
    var paneBefore by remember { mutableStateOf<LibraryPane?>(null) }
    LaunchedEffect(selectedId) { paneBefore = null }
    val changePane: (LibraryPane) -> Unit = { next ->
        if (next != pane) paneBefore = pane
        onPane(next)
    }
    val isShowLibrary = entries.firstOrNull { it.id == selectedId }?.type == "show"
    var target by remember { mutableStateOf<String?>(null) }
    val crumbs = remember { mutableStateListOf<String>() }
    val railFocus = remember { FocusRequester() }
    var loadingLibrary by remember { mutableStateOf(false) }
    var loadFrom by remember { mutableStateOf("") }
    // Au retour dans le menu de gauche, le focus revient sur la ligne de la bibliothèque ouverte.
    val focusIndex = entries.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0
    val contentFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    var menu by remember { mutableStateOf<ForgetMenu?>(null) }
    var menuChoice by remember { mutableStateOf(1) }
    var swallowUp by remember { mutableStateOf(false) }
    var pendingShelf by remember { mutableStateOf<String?>(null) }
    var pendingKey by remember { mutableStateOf<String?>(null) }
    var pendingNeighbor by remember { mutableStateOf<String?>(null) }
    val neighborFocus = remember { FocusRequester() }
    val columnStates = remember { mutableMapOf<String, LazyListState>() }
    val rowStates = remember { mutableMapOf<String, LazyListState>() }
    val gridStates = remember { mutableMapOf<String, LazyGridState>() }
    val columnState = columnStates.getOrPut(pageKey) { LazyListState() }
    val gridState = gridStates.getOrPut(pageKey) { LazyGridState() }
    val expanded = zone == Zone.Rail && !covered
    val isSettings = selectedId == "settings"
    val railActive = zone == Zone.Rail && !covered
    val contentActive = zone == Zone.Content && !covered
    fun openItem(item: Metadata) {
        if (item.type == "season" || item.type == "collection") crumbs.add(item.ratingKey)
        onOpen(item)
    }

    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().screenBackground())
    if (backdrop != null) {
        // Fond d'une collection : l'image du fond, assombrie pour que les affiches restent lisibles.
        Image(
            bitmap = backdrop.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to Ink.copy(alpha = 0.94f),
                    0.3f to Ink.copy(alpha = 0.78f),
                    1f to Ink.copy(alpha = 0.7f),
                ),
            ),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val open = menu
                if (open != null) {
                    val center = event.key == Key.DirectionCenter || event.key == Key.Enter
                    when {
                        event.type == KeyEventType.KeyUp && center -> {
                            if (swallowUp) {
                                swallowUp = false
                            } else {
                                val confirm = menuChoice == 0
                                menu = null
                                if (confirm) {
                                    val shelf = shelves.firstOrNull { row ->
                                        forgetKind(row.title) == open.kind && row.items.any { it.ratingKey == open.item.ratingKey }
                                    }
                                    val index = shelf?.items?.indexOfFirst { it.ratingKey == open.item.ratingKey } ?: -1
                                    val neighbor = if (shelf != null && index >= 0) {
                                        shelf.items.getOrNull(index + 1) ?: shelf.items.getOrNull(index - 1)
                                    } else null
                                    pendingShelf = shelf?.title
                                    pendingKey = open.item.ratingKey
                                    pendingNeighbor = neighbor?.ratingKey
                                    onForget(open.item, open.kind)
                                }
                            }
                        }
                        event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape) -> menu = null
                        event.type == KeyEventType.KeyDown && (event.key == Key.DirectionUp || event.key == Key.DirectionDown) ->
                            menuChoice = 1 - menuChoice
                    }
                    return@onPreviewKeyEvent true
                }
                if (covered || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if ((event.key == Key.Back || event.key == Key.Escape) && (selectedId == "settings" || selectedId == "search")) {
                    onSelect(entries.firstOrNull { it.id == "home" } ?: RailEntry("home", "Accueil", "A", "home"))
                    zone = Zone.Rail
                    true
                } else if ((event.key == Key.Back || event.key == Key.Escape) && zone == Zone.Content) {
                    if (onContentBack()) {
                        target = crumbs.removeLastOrNull()
                    } else {
                        val before = paneBefore
                        if (showTabs && before != null && before != pane) {
                            paneBefore = null
                            onPane(before)
                        } else {
                            zone = Zone.Rail
                        }
                    }
                    true
                } else {
                    false
                }
            },
    ) {
        LazyColumn(
            modifier = Modifier
                .width(if (expanded) 196.dp else 52.dp)
                .fillMaxHeight()
                .panelBackground()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        Image(
                            painterResource(R.drawable.logo),
                            contentDescription = "Plexiplay",
                            modifier = Modifier
                                .size(if (expanded) 28.dp else 36.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                        if (expanded) {
                            Text(
                                "Plexiplay",
                                color = Accent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (expanded) {
                            Text(
                                serverName,
                                color = Color(0xFF9AA3AD),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                            )
                        }
                        GearButton(
                            selected = selectedId == "settings",
                            active = railActive,
                            onClick = {
                                onSelect(RailEntry("settings", "Paramètres", "", "settings"))
                                zone = Zone.Content
                            },
                        )
                    }
                }
            }
            items(entries.size) { index ->
                val entry = entries[index]
                RailRow(
                    entry = entry,
                    expanded = expanded,
                    selected = entry.id == selectedId,
                    requester = if (index == focusIndex) railFocus else null,
                    active = railActive,
                    onClick = {
                        if (entry.id != selectedId && entry.id != "search" && entry.id != "settings") {
                            loadFrom = pageKey
                            loadingLibrary = true
                        }
                        onSelect(entry)
                        zone = Zone.Content
                    },
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            if (isSettings) {
                settings()
            } else if (selectedId == "search") {
                search()
            } else {
                if (showTabs) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        PaneChip("Bibliothèque", pane == LibraryPane.LIBRARY, contentActive) { changePane(LibraryPane.LIBRARY) }
                        if (!isShowLibrary) {
                            PaneChip("Collections", pane == LibraryPane.COLLECTIONS, contentActive) { changePane(LibraryPane.COLLECTIONS) }
                        }
                        PaneChip("Quoi de neuf", pane == LibraryPane.NEWS, contentActive) { changePane(LibraryPane.NEWS) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status, color = Color(0xFF9AA3AD), fontSize = 13.sp)
                    // Nombre d'éléments de la bibliothèque, dans une autre couleur, à côté du titre.
                    if (count >= 0) {
                        Text("$count", color = Signal, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (useShelves) {
                    LazyColumn(
                        state = columnState,
                        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shelves, key = { it.title }) { shelf ->
                            val rowState = rowStates.getOrPut("$pageKey|${shelf.title}") { LazyListState() }
                            ShelfRow(
                                shelf = shelf,
                                thumb = thumb,
                                posters = posters,
                                onOpen = ::openItem,
                                rowState = rowState,
                                requester = if (shelf.items.any { it.ratingKey == target }) {
                                    null
                                } else if (shelf == shelves.firstOrNull { row -> row.items.isNotEmpty() } && shelf.items.isNotEmpty()) {
                                    contentFocus
                                } else {
                                    null
                                },
                                restoreKey = target,
                                restoreFocus = restoreFocus,
                                active = contentActive,
                                neighborKey = if (shelf.title == pendingShelf) pendingNeighbor else null,
                                neighborFocus = neighborFocus,
                                onLongPress = forgetKind(shelf.title)?.let { kind ->
                                    { item: Metadata, anchor: Rect ->
                                        pendingShelf = null
                                        pendingKey = null
                                        pendingNeighbor = null
                                        menuChoice = 1
                                        swallowUp = true
                                        menu = ForgetMenu(item, kind, anchor)
                                    }
                                },
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(128.dp),
                        contentPadding = if (ThemeMode.neon) PaddingValues(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 6.dp) else PaddingValues(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items, key = { it.ratingKey + it.title }) { item ->
                            Poster(
                                item = item,
                                thumb = thumb,
                                posters = posters,
                                requester = when {
                                    item.ratingKey == target -> restoreFocus
                                    item == items.firstOrNull() -> contentFocus
                                    else -> null
                                },
                                active = contentActive,
                                onOpen = { openItem(item) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (loadingLibrary) {
        Text(
            "Chargement…",
            color = Color(0xFF5DD39E),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(Ink.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
    menu?.let { ForgetPopup(it, menuChoice) }
    }
    // Indicateur « Chargement » : visible jusqu'à ce que la page de la bibliothèque soit prête.
    LaunchedEffect(loadingLibrary, pageKey, shelves, items, useShelves) {
        if (!loadingLibrary || pageKey == loadFrom) return@LaunchedEffect
        val ready = if (useShelves) shelves.any { it.items.isNotEmpty() } else items.isNotEmpty()
        if (ready) {
            // Reste affiché jusqu'à ce que toutes les affiches visibles soient dessinées.
            delay(300)
            var calm = 0
            while (calm < 3) {
                delay(150)
                if (posters.pendingVisible() == 0) calm += 1 else calm = 0
            }
            delay(150)
            loadingLibrary = false
        }
    }
    LaunchedEffect(loadingLibrary) {
        if (loadingLibrary) {
            delay(25000)
            loadingLibrary = false
        }
    }
    LaunchedEffect(restoreToken) {
        if (!restoreKey.isNullOrBlank()) target = restoreKey
    }
    LaunchedEffect(shelves) {
        val removed = pendingKey ?: return@LaunchedEffect
        if (shelves.any { row -> forgetKind(row.title) != null && row.items.any { it.ratingKey == removed } }) return@LaunchedEffect
        val neighbor = pendingNeighbor
        delay(80)
        runCatching { if (neighbor != null) neighborFocus.requestFocus() else contentFocus.requestFocus() }
        pendingKey = null
        pendingShelf = null
        pendingNeighbor = null
    }
    LaunchedEffect(zone, selectedId, pane, useShelves, pageKey, covered) {
        if (covered || target != null) return@LaunchedEffect
        runCatching {
            if (zone == Zone.Rail) railFocus.requestFocus() else contentFocus.requestFocus()
        }
    }
    LaunchedEffect(target, items, shelves, covered, pageKey, useShelves) {
        val key = target ?: return@LaunchedEffect
        if (covered) return@LaunchedEffect
        if (useShelves) {
            val shelfIndex = shelves.indexOfFirst { shelf -> shelf.items.any { it.ratingKey == key } }
            if (shelfIndex < 0) return@LaunchedEffect
            val itemIndex = shelves[shelfIndex].items.indexOfFirst { it.ratingKey == key }
            zone = Zone.Content
            columnState.scrollToItem(shelfIndex)
            rowStates.getOrPut("$pageKey|${shelves[shelfIndex].title}") { LazyListState() }.scrollToItem(itemIndex)
        } else {
            val gridIndex = items.indexOfFirst { it.ratingKey == key }
            if (gridIndex < 0) return@LaunchedEffect
            zone = Zone.Content
            gridState.scrollToItem(gridIndex)
        }
        delay(48)
        runCatching { restoreFocus.requestFocus() }
        target = null
    }
}

@Composable
private fun ShelfRow(
    shelf: Shelf,
    thumb: (Metadata) -> List<String>,
    posters: PosterStore,
    onOpen: (Metadata) -> Unit,
    rowState: LazyListState,
    requester: FocusRequester?,
    restoreKey: String?,
    restoreFocus: FocusRequester,
    active: Boolean,
    neighborKey: String? = null,
    neighborFocus: FocusRequester? = null,
    onLongPress: ((Metadata, Rect) -> Unit)? = null,
) {
    Column {
        Text(shelf.title, color = Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (shelf.items.isEmpty()) {
            Text("Aucun titre", color = Color(0xFF9AA3AD), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        } else {
            LazyRow(
                state = rowState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                // Thème Néon : petite marge pour que le contour de l'affiche agrandie ne soit pas coupé.
                contentPadding = if (ThemeMode.neon) PaddingValues(horizontal = 6.dp, vertical = 6.dp) else PaddingValues(0.dp),
                modifier = Modifier.padding(top = if (ThemeMode.neon) 0.dp else 6.dp),
            ) {
                items(shelf.items, key = { it.ratingKey + it.title }) { item ->
                    Poster(
                        item = item,
                        thumb = thumb,
                        posters = posters,
                        requester = if (item.ratingKey == restoreKey) {
                            restoreFocus
                        } else if (neighborKey != null && neighborFocus != null && item.ratingKey == neighborKey) {
                            neighborFocus
                        } else if (requester != null && item == shelf.items.first()) {
                            requester
                        } else {
                            null
                        },
                        active = active,
                        onOpen = { onOpen(item) },
                        onLongPress = onLongPress?.let { handler -> { anchor: Rect -> handler(item, anchor) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun PaneChip(label: String, selected: Boolean, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (focused || (selected && ThemeMode.neon)) Ink else Paper,
        fontSize = 13.sp,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusProperties { canFocus = active }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .themedButton(if (selected || focused) Accent else Color(0xFF2A333C), focused)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun RailRow(
    entry: RailEntry,
    expanded: Boolean,
    selected: Boolean,
    requester: FocusRequester?,
    active: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val look = remember(entry.id, entry.title, entry.type) { railLook(entry) }
    val tint = if (ThemeMode.neon && expanded && selected) Ink else if (focused || selected) Accent else Paper
    val ring = if (focused) FocusRing else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusProperties { canFocus = active }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .then(
                if (expanded && ThemeMode.neon) {
                    Modifier
                        .padding(bottom = 3.dp)
                        .themedButton(if (selected) Accent else Color(0xFF2A333C), focused, 6.dp)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                } else if (expanded) {
                    Modifier
                        .border(1.dp, ring, RoundedCornerShape(6.dp))
                        .background(if (selected) Color(0xFF2A333C) else Color.Transparent, RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (expanded) 28.dp else 36.dp)
                .then(
                    if (expanded) {
                        Modifier
                    } else {
                        Modifier
                            .border(2.dp, ring, RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFF2A333C) else Color(0xFF232A32), RoundedCornerShape(8.dp))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(if (expanded) 20.dp else 22.dp)) { drawRailIcon(look.icon, tint) }
            if (!expanded && look.tag.isNotEmpty()) {
                Text(
                    look.tag,
                    color = Ink,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(2.dp, 2.dp)
                        .background(Accent.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 2.dp, vertical = 0.dp),
                )
            }
        }
        if (expanded) {
            Text(
                entry.title,
                color = if (ThemeMode.neon && selected) Ink else if (focused) Accent else Paper,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private val PosterWidth = 112.dp
private val PosterHeight = 168.dp

@Composable
internal fun Poster(
    item: Metadata,
    thumb: (Metadata) -> List<String>,
    posters: PosterStore,
    requester: FocusRequester?,
    active: Boolean,
    onOpen: () -> Unit,
    onLongPress: ((Rect) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val pressScope = rememberCoroutineScope()
    var pressJob by remember { mutableStateOf<Job?>(null) }
    var pressFired by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var bitmap by remember(item.ratingKey, item.thumb) {
        mutableStateOf(if (item.thumb.isBlank()) null else posters.peek(item.ratingKey, item.thumb))
    }
    LaunchedEffect(item.ratingKey, item.thumb) {
        if (item.thumb.isBlank()) return@LaunchedEffect
        val key = item.ratingKey
        val path = item.thumb
        posters.load(key, path, thumb(item)) { loaded ->
            if (key == item.ratingKey && path == item.thumb) bitmap = loaded
        }
    }
    Column(
        modifier = Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .onFocusChanged { focused = it.isFocused }
            .focusProperties { canFocus = active }
            .onPreviewKeyEvent {
                val center = it.key == Key.DirectionCenter || it.key == Key.Enter
                if (!center) return@onPreviewKeyEvent false
                if (onLongPress == null) {
                    if (it.type == KeyEventType.KeyUp) {
                        onOpen()
                        true
                    } else false
                } else when (it.type) {
                    KeyEventType.KeyDown -> {
                        if (it.nativeKeyEvent.repeatCount == 0) {
                            pressFired = false
                            pressJob?.cancel()
                            pressJob = pressScope.launch {
                                delay(3000)
                                pressFired = true
                                onLongPress(bounds)
                            }
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        pressJob?.cancel()
                        pressJob = null
                        val wasLong = pressFired
                        pressFired = false
                        if (!wasLong) onOpen()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .graphicsLayer {
                if (ThemeMode.neon && focused) {
                    scaleX = 1.07f
                    scaleY = 1.07f
                }
            },
    ) {
        Box(Modifier.size(PosterWidth, PosterHeight).clip(RoundedCornerShape(4.dp))) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF2A333C)), contentAlignment = Alignment.Center) {
                    Text(item.title.take(1), color = Paper, fontSize = 18.sp)
                }
            }
            if (item.type == "episode" && item.index > 0 && item.parentIndex > 0) {
                Box(Modifier.align(Alignment.TopEnd).size(60.dp).clipToBounds()) {
                    Text(
                        "S${item.parentIndex} · E${item.index}",
                        color = Ink,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .width(80.dp)
                            .offset(0.dp, 13.dp)
                            .graphicsLayer { rotationZ = 45f }
                            .background(Signal.copy(alpha = 0.92f))
                            .padding(vertical = 1.dp),
                    )
                }
            }
            if (item.added > 0) {
                Text(
                    "${item.added} ajouts",
                    color = Ink,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Signal.copy(alpha = 0.92f))
                        .padding(vertical = 1.dp),
                )
            }
            if (focused) {
                Box(Modifier.fillMaxSize().border(3.dp, FocusRing, RoundedCornerShape(4.dp)))
            }
        }
        Text(
            if ((item.type == "episode" || item.added > 0) && item.grandparentTitle.isNotBlank()) item.grandparentTitle else item.title,
            color = Paper,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(PosterWidth).padding(top = 2.dp),
        )
    }
}

@Composable
private fun ForgetPopup(menu: ForgetMenu, choice: Int) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidth = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { config.screenHeightDp.dp.toPx() }
    val width = 280.dp
    val widthPx = with(density) { width.toPx() }
    val heightPx = with(density) { 150.dp.toPx() }
    val gap = with(density) { 10.dp.toPx() }
    val onRight = menu.anchor.right + gap + widthPx <= screenWidth
    val x = if (onRight) menu.anchor.right + gap else (menu.anchor.left - gap - widthPx).coerceAtLeast(0f)
    val y = menu.anchor.top.coerceIn(0f, (screenHeight - heightPx).coerceAtLeast(0f))
    val remove = when (menu.kind) {
        ForgetKind.CONTINUE -> "Supprimer de \u00ab Continuer à regarder \u00bb"
        ForgetKind.AGAIN -> "Retirer de \u00ab Regarder à nouveau \u00bb"
    }
    Column(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .width(width)
            .background(Color(0xF01C2128), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Text(
            menu.item.title,
            color = Color(0xFF9AA3AD),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        PopupChoice(remove, choice == 0)
        PopupChoice("Annuler", choice == 1)
    }
}

@Composable
private fun PopupChoice(label: String, selected: Boolean) {
    Text(
        label,
        color = if (selected) Ink else Paper,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(if (selected) Accent else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

fun sectionGlyph(section: LibrarySection): String = if (section.type == "show") "S" else "F"

@Composable
private fun GearButton(selected: Boolean, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var pressing by remember { mutableStateOf(false) }
    var fired by remember { mutableStateOf(false) }
    var egg by remember { mutableStateOf(false) }
    // Appui prolongé (5 s) : animation cachée, puis les paramètres s'ouvrent normalement.
    LaunchedEffect(pressing) {
        if (pressing) {
            delay(5000)
            fired = true
            egg = true
        }
    }
    if (egg) {
        LogoEgg(onDone = {
            if (egg) {
                egg = false
                pressing = false
                fired = false
                onClick()
            }
        })
    }
    val color = if (focused || selected) Accent else Paper
    Canvas(
        modifier = Modifier
            .size(30.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusProperties { canFocus = active }
            .onPreviewKeyEvent {
                val ok = it.key == Key.DirectionCenter || it.key == Key.Enter
                if (!ok) {
                    false
                } else if (it.type == KeyEventType.KeyDown) {
                    if (!pressing && !fired) pressing = true
                    true
                } else if (it.type == KeyEventType.KeyUp) {
                    val long = fired
                    pressing = false
                    if (!long) onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .border(1.dp, if (focused) FocusRing else Color.Transparent, RoundedCornerShape(4.dp))
            .padding(3.dp),
    ) {
        val teeth = 8
        val tip = size.minDimension * 0.48f
        val root = size.minDimension * 0.30f
        val hole = size.minDimension * 0.11f
        val flank = Math.toRadians(15.0)
        for (index in 0 until teeth) {
            val mid = Math.toRadians(index * (360.0 / teeth) - 90.0)
            fun point(radius: Float, angle: Double) = Offset(
                center.x + kotlin.math.cos(angle).toFloat() * radius,
                center.y + kotlin.math.sin(angle).toFloat() * radius,
            )
            val tooth = Path().apply {
                val leftRoot = point(root, mid - flank * 1.2)
                val leftTip = point(tip, mid - flank * 0.55)
                val rightTip = point(tip, mid + flank * 0.55)
                val rightRoot = point(root, mid + flank * 1.2)
                moveTo(leftRoot.x, leftRoot.y)
                lineTo(leftTip.x, leftTip.y)
                lineTo(rightTip.x, rightTip.y)
                lineTo(rightRoot.x, rightRoot.y)
                close()
            }
            drawPath(tooth, color)
        }
        drawCircle(color, radius = root)
        drawCircle(Panel, radius = hole)
    }
}
