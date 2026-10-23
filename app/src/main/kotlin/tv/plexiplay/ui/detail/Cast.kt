package tv.plexiplay.ui.detail

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tv.plexiplay.plex.PersonInfo
import tv.plexiplay.plex.PersonWork
import tv.plexiplay.plex.model.RoleItem
import tv.plexiplay.ui.shell.PosterStore
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.tap
import androidx.compose.ui.platform.LocalConfiguration
import tv.plexiplay.ui.theme.FocusRing
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Panel
import tv.plexiplay.ui.theme.Paper
import tv.plexiplay.ui.theme.Signal

/** Un acteur de la distribution ; [guest] vrai pour les invités de l'épisode (les principaux viennent de la série). */
data class CastEntry(val role: RoleItem, val guest: Boolean)

/** Octogone régulier (photos des acteurs). */
object OctagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val c = 0.29f
        return Outline.Generic(
            Path().apply {
                moveTo(w * c, 0f)
                lineTo(w * (1 - c), 0f)
                lineTo(w, h * c)
                lineTo(w, h * (1 - c))
                lineTo(w * (1 - c), h)
                lineTo(w * c, h)
                lineTo(0f, h * (1 - c))
                lineTo(0f, h * c)
                close()
            },
        )
    }
}

private fun initials(name: String): String =
    name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

@Composable
private fun loadedBitmap(posters: PosterStore, key: String, path: String, urls: List<String>): Bitmap? {
    var bitmap by remember(key, path) { mutableStateOf(if (path.isBlank()) null else posters.peek(key, path)) }
    LaunchedEffect(key, path) {
        if (path.isBlank() || urls.isEmpty()) return@LaunchedEffect
        posters.load(key, path, urls) { loaded -> bitmap = loaded }
    }
    return bitmap
}

@Composable
private fun Octagon(role: RoleItem, size: Int, posters: PosterStore, thumbs: (String) -> List<String>, focused: Boolean) {
    val bitmap = loadedBitmap(posters, "role-" + role.id + "-" + role.tag.hashCode(), role.thumb, thumbs(role.thumb))
    Box(
        Modifier
            .size(size.dp)
            .clip(OctagonShape)
            .background(Panel),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(initials(role.tag), color = Accent, fontSize = (size / 3).sp, fontWeight = FontWeight.Bold)
        }
        if (focused) {
            Box(Modifier.fillMaxSize().border(4.dp, FocusRing, OctagonShape))
        }
    }
}

/** Rangée « Distribution » : principaux, un trait, puis invités. OK ouvre la fiche de l'acteur. */
@Composable
fun CastRow(
    cast: List<CastEntry>,
    posters: PosterStore,
    thumbs: (String) -> List<String>,
    requester: FocusRequester,
    onOpen: (CastEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var last by remember(cast) { mutableIntStateOf(0) }
    Column(modifier) {
        Text("DISTRIBUTION", color = Color(0xFF9AA3AD), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(cast, key = { index, entry -> "$index-${entry.role.id}-${entry.role.tag}" }) { index, entry ->
                if (index > 0 && entry.guest && !cast[index - 1].guest) {
                    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(70.dp).background(Color(0xFF3A4450)))
                }
                var focused by remember { mutableStateOf(false) }
                Column(
                    Modifier
                        .width(84.dp)
                        .then(if (index == last) Modifier.focusRequester(requester) else Modifier)
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) last = index
                        }
                        .tap { onOpen(entry) }
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                                onOpen(entry)
                                true
                            } else false
                        }
                        .focusable(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Octagon(entry.role, 60, posters, thumbs, focused)
                    Text(
                        entry.role.tag,
                        color = Paper, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (entry.role.role.isNotBlank()) {
                        Text(
                            entry.role.role,
                            color = Signal, fontSize = 10.sp, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkTile(work: PersonWork, posters: PosterStore) {
    val path = work.images.firstOrNull().orEmpty()
    val bitmap = loadedBitmap(posters, "work-" + path.hashCode(), path, work.images)
    Column(Modifier.width(92.dp)) {
        Box(Modifier.size(92.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A333C))) {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xCC000000)),
                ),
            )
            Text(
                work.title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }
        if (work.year > 0) Text(work.year.toString(), color = Color(0xFF9AA3AD), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 3.dp))
    }
}

/**
 * Grande fiche d'un acteur : photo, nom, personnage, biographie et œuvres principales. Haut/bas font défiler ;
 * Retour (géré par l'écran) ferme la fiche.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ActorDialog(
    entry: CastEntry,
    info: PersonInfo?,
    posters: PosterStore,
    thumbs: (String) -> List<String>,
    onClose: () -> Unit = {},
) {
    val narrow = LocalConfiguration.current.screenWidthDp < 600
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Fiche Wikipédia : le sélecteur « Wikipédia | Plex » bascule vers la biographie officielle Plex (et inversement).
    var showPlex by remember(info) { mutableStateOf(false) }
    val canSwitch = info != null && info.fromWikipedia && info.altBio.isNotBlank()
    val pick: (Boolean) -> Unit = { plex ->
        if (plex != showPlex) {
            showPlex = plex
            scope.launch { scroll.scrollTo(0) }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9000000))
            .tap { }
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                val dpad = event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                    event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                    event.key == Key.DirectionCenter || event.key == Key.Enter
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionDown -> scope.launch { scroll.animateScrollBy(160f) }
                        Key.DirectionUp -> scope.launch { scroll.animateScrollBy(-160f) }
                        Key.DirectionCenter, Key.Enter -> if (canSwitch) pick(!showPlex)
                        else -> Unit
                    }
                }
                dpad
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (narrow) 8.dp else 80.dp, vertical = if (narrow) 16.dp else 36.dp)
                .background(Panel, RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(14.dp))
                .padding(if (narrow) 12.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 24.dp),
        ) {
            Column(Modifier.width(if (narrow) 104.dp else 180.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Octagon(entry.role, if (narrow) 90 else 150, posters, thumbs, false)
                Text(
                    entry.role.tag, color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (entry.role.role.isNotBlank()) {
                    Text(entry.role.role, color = Signal, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
                Text(
                    if (entry.guest) "Invité de l'épisode" else "Distribution principale",
                    color = Color(0xFF9AA3AD), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                )
            }
            Column(Modifier.weight(1f).fillMaxSize().verticalScroll(scroll)) {
                if (canSwitch) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "BIOGRAPHIE", color = Color(0xFF9AA3AD), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                        BioSwitch(showPlex, pick)
                    }
                } else {
                    Text("BIOGRAPHIE", color = Color(0xFF9AA3AD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    when {
                        info == null -> "Chargement…"
                        info.bio.isBlank() -> "Biographie indisponible pour cet acteur."
                        showPlex && canSwitch -> info.altBio
                        else -> info.bio
                    },
                    color = Color(0xFFD5DBE2), fontSize = 14.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 6.dp, end = 12.dp),
                )
                if (info != null && info.works.isNotEmpty()) {
                    Text(
                        if (info.worksInLibrary) "DANS VOTRE BIBLIOTHÈQUE" else "ŒUVRES PRINCIPALES",
                        color = Color(0xFF9AA3AD), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                    )
                    // Vignettes à la suite, qui passent à la ligne selon la largeur de l'écran.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 10.dp, end = 12.dp),
                    ) {
                        info.works.forEach { work -> WorkTile(work, posters) }
                    }
                }
                Text(
                    "Fermer",
                    color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.tap { onClose() }.padding(top = 10.dp, bottom = 10.dp, end = 16.dp),
                )
            }
        }
    }
}

/** Sélecteur à deux choix « Wikipédia | Plex » : le choix actif est surligné, chaque moitié se touche (≥ 40 dp de haut). */
@Composable
private fun BioSwitch(showPlex: Boolean, onPick: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF26303B))
            .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(20.dp)),
    ) {
        listOf(false to "Wikipédia", true to "Plex").forEach { (plex, label) ->
            val active = plex == showPlex
            Text(
                label,
                color = if (active) Ink else Paper,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (active) Accent else Color.Transparent)
                    .tap { onPick(plex) }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
    }
}


/** Grand popup du résumé complet : défile avec haut/bas, Retour referme (géré par l'écran). */
@Composable
fun SummaryDialog(title: String, subtitle: String, summary: String, onClose: () -> Unit = {}) {
    val narrow = LocalConfiguration.current.screenWidthDp < 600
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9000000))
            .tap { }
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                val dpad = event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                    event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                    event.key == Key.DirectionCenter || event.key == Key.Enter
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionDown -> scope.launch { scroll.animateScrollBy(160f) }
                        Key.DirectionUp -> scope.launch { scroll.animateScrollBy(-160f) }
                        else -> Unit
                    }
                }
                dpad
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (narrow) 8.dp else 110.dp, vertical = if (narrow) 16.dp else 44.dp)
                .background(Panel, RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(14.dp))
                .padding(if (narrow) 14.dp else 28.dp),
        ) {
            Text(title, color = Paper, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Signal, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("RÉSUMÉ", color = Color(0xFF9AA3AD), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
                Text(
                    summary.ifBlank { "Résumé indisponible." },
                    color = Color(0xFFD5DBE2), fontSize = 17.sp, lineHeight = 25.sp,
                    modifier = Modifier.padding(top = 6.dp, end = 12.dp),
                )
            }
            Text(
                "Fermer",
                color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.tap { onClose() }.padding(top = 10.dp, bottom = 10.dp, end = 16.dp),
            )
        }
    }
}
