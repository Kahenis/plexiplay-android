package tv.plexiplay.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.plexiplay.plex.model.GenreEntry
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.FocusRing
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper

/** Une case du popup : un style, avec la ou les clés Plex qui le désignent (« Sci-Fi » et « Science Fiction » ne font qu'une case). */
data class StyleCard(val label: String, val keys: List<String>)

private val FRENCH = mapOf(
    "action" to "Action",
    "adventure" to "Aventure",
    "animation" to "Animation",
    "anime" to "Animé",
    "biography" to "Biographie",
    "children" to "Enfants",
    "comedy" to "Humour",
    "crime" to "Policier",
    "documentary" to "Documentaire",
    "drama" to "Drame",
    "family" to "Famille",
    "fantasy" to "Fantastique",
    "film-noir" to "Film noir",
    "film noir" to "Film noir",
    "history" to "Histoire",
    "horror" to "Horreur",
    "music" to "Musique",
    "musical" to "Comédie musicale",
    "mystery" to "Mystère",
    "romance" to "Romance",
    "science fiction" to "Science-fiction",
    "sci-fi" to "Science-fiction",
    "science-fiction" to "Science-fiction",
    "short" to "Court métrage",
    "sport" to "Sport",
    "sports" to "Sport",
    "suspense" to "Suspense",
    "thriller" to "Thriller",
    "tv movie" to "Téléfilm",
    "war" to "Guerre",
    "western" to "Western",
    "comédie" to "Humour",
    "action & adventure" to "Action et aventure",
    "sci-fi & fantasy" to "SF et fantastique",
    "war & politics" to "Guerre et politique",
    "politics" to "Politique",
    "reality" to "Téléréalité",
    "talk show" to "Talk-show",
    "talk" to "Talk-show",
    "kids" to "Jeunesse",
    "news" to "Actualités",
    "soap" to "Feuilleton",
    "mini-series" to "Mini-série",
    "miniseries" to "Mini-série",
    "game show" to "Jeu télévisé",
    "food" to "Cuisine",
    "travel" to "Voyage",
    "home and garden" to "Maison et jardin",
    "nature" to "Nature",
    "disaster" to "Catastrophe",
    "holiday" to "Fêtes",
    "indie" to "Indépendant",
    "supernatural" to "Surnaturel",
    "super hero" to "Super-héros",
    "superhero" to "Super-héros",
    "foreign" to "Étranger",
    "teen drama" to "Drame ado",
    "medical" to "Médical",
    "legal" to "Judiciaire",
    "lifestyle" to "Art de vivre",
    "mini series" to "Mini-série",
    "dark comedy" to "Humour noir",
    "stand-up" to "Stand-up",
    "stand up" to "Stand-up",
    "comedie" to "Humour",
)

/** Nom français d'un style Plex ; à défaut, le nom tel qu'il est dans la bibliothèque. */
fun styleLabel(raw: String): String {
    val name = raw.trim()
    return FRENCH[name.lowercase()] ?: name.replaceFirstChar { it.uppercase() }
}

/** Regroupe les styles Plex par nom affiché : une seule case par style, triées par ordre alphabétique. */
fun styleCards(entries: List<GenreEntry>): List<StyleCard> =
    entries.groupBy { styleLabel(it.title).lowercase() }
        .values
        .map { group -> StyleCard(styleLabel(group.first().title), group.map { it.key }.distinct()) }
        .sortedBy { java.text.Normalizer.normalize(it.label.lowercase(), java.text.Normalizer.Form.NFD) }

private val cardCache = HashMap<String, List<StyleCard>>()
private val filmCache = HashMap<String, Pair<String, String>>()

private fun fallbackBrush(label: String): Brush {
    val hue = ((label.hashCode() and 0x7fffffff) % 360).toFloat()
    return Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.42f), Color.hsv((hue + 50f) % 360f, 0.6f, 0.2f)))
}

/**
 * Popup des styles d'une bibliothèque de films : une case par style, fond = image assombrie d'un film de la bibliothèque,
 * nom du style en bouton. [cacheKey] identifie serveur + bibliothèque pour ne charger qu'une fois.
 */
@Composable
fun StylePicker(
    cacheKey: String,
    loadStyles: () -> List<GenreEntry>,
    pickFilms: (StyleCard) -> List<Metadata>,
    wide: (String) -> List<String>,
    posters: PosterStore,
    onPick: (StyleCard) -> Unit,
    onClose: () -> Unit,
) {
    var cards by remember { mutableStateOf(cardCache[cacheKey]) }
    var failed by remember { mutableStateOf(false) }
    val backgrounds = remember { mutableStateMapOf<String, android.graphics.Bitmap>() }
    val first = remember { FocusRequester() }

    LaunchedEffect(cacheKey) {
        if (cards == null) {
            val loaded = withContext(Dispatchers.IO) { runCatching { styleCards(loadStyles()) }.getOrNull() }
            if (loaded == null) failed = true else {
                cardCache[cacheKey] = loaded
                cards = loaded
            }
        }
    }
    LaunchedEffect(cards) {
        val list = cards ?: return@LaunchedEffect
        if (list.isNotEmpty()) runCatching { first.requestFocus() }
        // Le fond de chaque case : un film du style, de préférence pas déjà pris par une autre case.
        val used = HashSet<String>()
        for (card in list) {
            val id = cacheKey + "|" + card.label
            var pick = filmCache[id]
            if (pick == null) {
                val films = withContext(Dispatchers.IO) { runCatching { pickFilms(card) }.getOrDefault(emptyList()) }
                    .filter { it.art.isNotBlank() }
                val film = films.firstOrNull { it.ratingKey !in used } ?: films.firstOrNull()
                if (film != null) {
                    pick = film.ratingKey to film.art
                    filmCache[id] = pick
                }
            }
            if (pick != null) {
                used += pick.first
                val (key, path) = pick
                posters.load("style-$key", path, wide(path)) { bitmap -> backgrounds[card.label] = bitmap }
            }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .fillMaxSize(0.9f)
                .background(Ink, RoundedCornerShape(20.dp))
                .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Styles", color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val list = cards
            when {
                failed -> Text("Styles indisponibles pour le moment.", color = Color(0xFF9AA3AD), fontSize = 15.sp)
                list == null -> Text("Chargement…", color = Color(0xFF9AA3AD), fontSize = 15.sp)
                list.isEmpty() -> Text("Aucun style dans cette bibliothèque.", color = Color(0xFF9AA3AD), fontSize = 15.sp)
                else -> LazyVerticalGrid(
                    // Mobile : autant de colonnes que la largeur le permet (4 sur grand écran).
                    columns = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 900) GridCells.Adaptive(140.dp) else GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.label }) { card ->
                        StyleTile(
                            card = card,
                            background = backgrounds[card.label],
                            requester = if (card === list.first()) first else null,
                            onClick = { onPick(card) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleTile(card: StyleCard, background: android.graphics.Bitmap?, requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .border(3.dp, if (focused) FocusRing else Color.Transparent, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().background(fallbackBrush(card.label)))
        if (background != null) {
            Image(
                bitmap = background.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Fond assombri pour que le nom reste lisible.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (focused) 0.45f else 0.62f)))
        Text(
            card.label,
            color = if (focused) Ink else Paper,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .background(if (focused) Accent else Color(0xCC2A333C), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
