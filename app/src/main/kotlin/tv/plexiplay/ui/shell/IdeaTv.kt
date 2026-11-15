package tv.plexiplay.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tv.plexiplay.plex.PlexClient
import tv.plexiplay.plex.model.GenreEntry
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.plex.model.ServerEndpoint
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.FocusRing
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper
import tv.plexiplay.ui.theme.themedButton

private const val NO_PREFERENCE = "Pas de préférence"

/**
 * Tire [count] films au hasard (SecureRandom) parmi les films pas encore vus de la bibliothèque [section],
 * limités au style [card] s'il y en a un. Évite de redonner les films de [previous] tant qu'il en reste assez.
 */
fun pickIdeas(
    client: PlexClient,
    server: ServerEndpoint,
    section: String,
    card: StyleCard?,
    previous: Set<String>,
    count: Int = 5,
): List<Metadata> {
    val all = if (card == null) client.items(server, section) else client.itemsByGenre(server, section, card.keys)
    val unseen = all.filter { it.type == "movie" && it.viewCount == 0 }.distinctBy { it.ratingKey }
    val fresh = unseen.filter { it.ratingKey !in previous }
    val pool = (if (fresh.size >= count) fresh else unseen).toMutableList()
    java.util.Collections.shuffle(pool, java.security.SecureRandom())
    return pool.take(count)
}

/**
 * Popup « Idées de films pour votre séance » : menu déroulant des styles (plus « Pas de préférence »), puis 5 films tirés
 * au hasard, chacun avec son affiche (même comportement qu'ailleurs) et un bouton « Bande-annonce ».
 */
@Composable
fun IdeaTvDialog(
    loadStyles: () -> List<GenreEntry>,
    choice: String?,
    films: List<Metadata>,
    busy: Boolean,
    note: String,
    thumb: (Metadata) -> List<String>,
    posters: PosterStore,
    onChoose: (StyleCard?, String) -> Unit,
    onRedraw: () -> Unit,
    onOpen: (Metadata) -> Unit,
    onTrailer: (Metadata) -> Unit,
    onClose: () -> Unit,
) {
    var cards by remember { mutableStateOf<List<StyleCard>?>(null) }
    var open by remember { mutableStateOf(choice == null) }
    val firstOption = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    val firstPoster = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        cards = withContext(Dispatchers.IO) { runCatching { styleCards(loadStyles()) }.getOrDefault(emptyList()) }
    }
    LaunchedEffect(open, cards) {
        if (!open) return@LaunchedEffect
        delay(60)
        runCatching { firstOption.requestFocus() }
    }
    LaunchedEffect(films) {
        if (films.isEmpty() || open) return@LaunchedEffect
        delay(120)
        runCatching { firstPoster.requestFocus() }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) 0.96f else 0.9f)
                .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.94f).dp)
                .background(Ink, RoundedCornerShape(20.dp))
                .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) 16.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Idées de films pour votre séance", color = Paper, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Plexiplay va vous faire 5 propositions pour votre séance ciné :", color = Color(0xFFC5CDD6), fontSize = 15.sp)

            IdeaButton(
                label = (if (choice == null) "Choisir un thème" else "Thème : $choice") + (if (open) "  ▴" else "  ▾"),
                requester = field,
                onClick = { open = !open },
            )

            if (open) {
                val options = listOf<StyleCard?>(null) + cards.orEmpty()
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 230.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(options.size) { index ->
                        val card = options[index]
                        val label = card?.label ?: NO_PREFERENCE
                        IdeaButton(
                            label = label,
                            requester = if (index == 0) firstOption else null,
                            selected = label == choice,
                            onClick = {
                                open = false
                                onChoose(card, label)
                            },
                        )
                    }
                }
            } else if (choice != null) {
                when {
                    busy -> Text("Tirage au sort…", color = Color(0xFF9AA3AD), fontSize = 15.sp)
                    films.isEmpty() -> Text(note.ifBlank { "Aucune proposition." }, color = Color(0xFF9AA3AD), fontSize = 15.sp)
                    // Mobile : les 5 propositions défilent de gauche à droite si elles ne tiennent pas.
                    else -> Row(
                        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    ) {
                        films.forEachIndexed { index, film ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Poster(
                                    item = film,
                                    thumb = thumb,
                                    posters = posters,
                                    requester = if (index == 0) firstPoster else null,
                                    active = true,
                                    onOpen = { onOpen(film) },
                                    appear = index,
                                )
                                IdeaButton(label = "Bande-annonce", small = true, modifier = Modifier.width(112.dp)) { onTrailer(film) }
                            }
                        }
                    }
                }
                if (films.isNotEmpty() && note.isNotBlank()) {
                    Text(note, color = Color(0xFFE39A3C), fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IdeaButton(label = "Autre tirage", onClick = onRedraw)
                    IdeaButton(label = "Fermer", onClick = onClose)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IdeaButton(label = "Fermer", onClick = onClose)
                }
            }
        }
    }
}

@Composable
private fun IdeaButton(
    label: String,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    selected: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (focused || selected) Ink else Paper,
        fontSize = if (small) 10.sp else 15.sp,
        fontWeight = if (small) FontWeight.Normal else FontWeight.SemiBold,
        textAlign = if (small) TextAlign.Center else TextAlign.Start,
        maxLines = 1,
        modifier = modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .themedButton(if (focused || selected) Accent else Color(0xFF2A333C), focused)
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 4.dp else 14.dp, vertical = if (small) 4.dp else 8.dp),
    )
}
