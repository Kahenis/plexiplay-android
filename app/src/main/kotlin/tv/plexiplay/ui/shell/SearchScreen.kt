package tv.plexiplay.ui.shell

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
import kotlinx.coroutines.delay
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper

private const val MIN_LETTERS = 3
private val keyRows = listOf("ABCDEFGHI", "JKLMNOPQR", "STUVWXYZ0", "123456789")

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
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstKey.requestFocus() }
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

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(330.dp)) {
            Text("Recherche", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF2A333C), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (query.isEmpty()) {
                    Text("Titre ou partie du titre", color = Color(0xFF6F7A86), fontSize = 16.sp)
                } else {
                    Text(query, color = Paper, fontSize = 18.sp, maxLines = 1)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                keyRows.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEachIndexed { column, ch ->
                            KeyButton(
                                label = ch.toString(),
                                width = 34.dp,
                                requester = if (rowIndex == 0 && column == 0) firstKey else null,
                            ) { query += ch.lowercaseChar() }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyButton("Espace", 108.dp) { if (query.isNotEmpty() && !query.endsWith(" ")) query += " " }
                    KeyButton("Effacer", 96.dp) { query = query.dropLast(1) }
                    KeyButton("Tout", 68.dp) { query = "" }
                }
            }
        }
        Column(Modifier.weight(1f)) {
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
}

@Composable
private fun KeyButton(label: String, width: androidx.compose.ui.unit.Dp, requester: FocusRequester? = null, onPress: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (focused) Ink else Paper,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onPress()
                    true
                } else false
            }
            .focusable()
            .size(width, 34.dp)
            .border(2.dp, if (focused) Color(0xFFE8C07A) else Color.Transparent, RoundedCornerShape(6.dp))
            .background(if (focused) Accent else Color(0xFF2A333C), RoundedCornerShape(6.dp))
            .padding(top = 6.dp),
    )
}
