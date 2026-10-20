package tv.plexiplay.ui.shell

import tv.plexiplay.ui.theme.tap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import tv.plexiplay.ui.theme.isTablet
import kotlinx.coroutines.delay
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper

private const val MIN_LETTERS = 3

private fun fold(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

/** Titres qui contiennent la saisie (accents et majuscules ignorés), ceux qui commencent par elle d'abord. */
internal fun searchTitles(catalog: List<Metadata>, query: String): List<Metadata> {
    val wanted = fold(query).trim()
    if (wanted.length < MIN_LETTERS) return emptyList()
    val words = wanted.split(Regex("\\s+")).filter { it.isNotBlank() }
    return catalog
        .mapNotNull { item ->
            val title = fold(item.title)
            val rank = when {
                title.startsWith(wanted) -> 0
                title.split(Regex("[^a-z0-9]+")).any { it.startsWith(wanted) } -> 1
                title.contains(wanted) -> 2
                words.size > 1 && words.all { title.contains(it) } -> 3
                else -> return@mapNotNull null
            }
            rank to item
        }
        .sortedWith(compareBy({ it.first }, { fold(it.second.title) }))
        .map { it.second }
        .take(120)
}

@Composable
fun SearchScreen(
    catalog: List<Metadata>?,
    thumb: (Metadata) -> List<String>,
    posters: PosterStore,
    onOpen: (Metadata) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(200)
        runCatching { field.requestFocus() }
        keyboard?.show()
    }
    val results = if (catalog == null) emptyList() else searchTitles(catalog, query)
    val letters = fold(query).trim().length
    val message = when {
        catalog == null -> "Chargement du catalogue…"
        letters < MIN_LETTERS -> "Tapez au moins $MIN_LETTERS lettres."
        results.isEmpty() -> "Aucun résultat."
        results.size == 1 -> "1 résultat"
        else -> "${results.size} résultats"
    }
    val tablet = isTablet()

    Column(Modifier.fillMaxSize()) {
        Text("Recherche", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier
                .padding(top = 8.dp, bottom = 6.dp)
                .then(if (tablet) Modifier.width(420.dp) else Modifier.fillMaxWidth())
                .background(Color(0xFF2A333C), RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Titre ou partie du titre", color = Color(0xFF6F7A86), fontSize = 16.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Paper, fontSize = 18.sp),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(field),
                )
            }
            if (query.isNotEmpty()) {
                Text(
                    "Effacer",
                    color = Accent,
                    fontSize = 14.sp,
                    modifier = Modifier.tap { query = "" }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
        Text(message, color = Color(0xFF9AA3AD), fontSize = 13.sp)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
        ) {
            items(results, key = { it.ratingKey }) { item ->
                Poster(
                    item = item,
                    thumb = thumb,
                    posters = posters,
                    requester = null,
                    active = true,
                    onOpen = { onOpen(item) },
                )
            }
        }
    }
}
