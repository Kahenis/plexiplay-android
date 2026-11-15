package tv.plexiplay.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.plex.votes.CommunityRating
import tv.plexiplay.plex.votes.Review
import tv.plexiplay.plex.votes.VotesClient
import tv.plexiplay.ui.theme.tap
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.FocusRing
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Panel
import tv.plexiplay.ui.theme.Paper
import tv.plexiplay.ui.theme.Signal

/**
 * Étoiles communes à tous les utilisateurs Plexiplay, quel que soit leur serveur : la moyenne de tous les autres
 * votants, et ma note séparément (VotesClient, petit service sur guilard.com). La fiche n'affiche qu'une ligne :
 * la mienne seule, la moyenne mondiale seule, ou la moyenne des deux si j'ai aussi voté.
 */
object PlexiStars {
    /** Valeur affichée, en demi-étoiles : 0 si personne n'a noté (ni moi, ni personne d'autre). */
    fun combined(community: CommunityRating?): Float {
        val average = community?.average
        val mine = community?.mine ?: 0
        return when {
            average != null && mine > 0 -> (average + mine) / 2f
            average != null -> average
            mine > 0 -> mine.toFloat()
            else -> 0f
        }
    }

    /**
     * Enregistre ma note (1 à 5) et, si fourni, mon avis court dans la base commune à tous les utilisateurs
     * Plexiplay. `review` : null = ne pas y toucher, chaîne vide = l'effacer, texte = le remplacer.
     * Renvoie false si le service guilard.com est injoignable ou a refusé.
     */
    fun save(meta: Metadata, voter: String, stars: Int, review: String? = null): Boolean {
        val guid = VotesClient.communityKey(meta)
        if (guid.isBlank() || voter.isBlank()) return false
        return VotesClient.submit(guid, voter, stars, review)
    }

    /** Relit ma note et la moyenne des autres pour ce titre. Null si le titre n'a pas d'identifiant commun, ou si le service est injoignable. */
    fun fetch(meta: Metadata, voter: String): CommunityRating? {
        val guid = VotesClient.communityKey(meta)
        if (guid.isBlank() || voter.isBlank()) return null
        return VotesClient.fetch(guid, voter)
    }

    /** Avis courts des autres utilisateurs Plexiplay sur ce titre (popup ouverte en cliquant sur les étoiles). */
    fun reviews(meta: Metadata): List<Review> {
        val guid = VotesClient.communityKey(meta)
        if (guid.isBlank()) return emptyList()
        return VotesClient.reviews(guid)
    }
}

/**
 * « Plexiplay » suivi de la ligne d'étoiles de la fiche : pleines en doré, vides nettement grisées pour qu'on
 * voie bien lesquelles comptent. Focusable/cliquable : OK ouvre le popup des avis courts des utilisateurs.
 */
@Composable
fun PlexiStarsRow(community: CommunityRating?, onClick: () -> Unit) {
    val value = PlexiStars.combined(community)
    val full = value.toInt().coerceIn(0, 5)
    val half = value - full >= 0.5f && full < 5
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable(onClick = onClick)
            .border(1.dp, if (focused) FocusRing else Color.Transparent, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text("Plexiplay  ", color = if (focused) Accent else Paper, fontSize = 15.sp)
        for (index in 1..5) {
            val filled = index <= full || (half && index == full + 1)
            Text(if (filled) "★" else "☆", color = if (filled) Signal else Color(0xFF4D5860), fontSize = 15.sp)
        }
    }
}

/**
 * Panneau « Votre note » : flèches gauche / droite sur les étoiles, OK valide ; focus par défaut sur les
 * étoiles. Bas puis OK ouvre « Écrire un avis » (ReviewDialog) ; Retour annule. La note se change simplement
 * en déplaçant les étoiles, plus de bouton pour la retirer.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RateDialog(current: Int, onRate: (Int) -> Unit, onWriteReview: (Int) -> Unit, onClose: () -> Unit) {
    var choice by remember { mutableIntStateOf(if (current in 1..5) current else 3) }
    // 0 : rangée des étoiles ; 1 : bouton « Écrire un avis » (télécommande).
    var row by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            // Mobile : toucher à côté du panneau ferme sans rien changer.
            .tap { onClose() }
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> if (row == 0 && choice > 1) choice -= 1
                        Key.DirectionRight -> if (row == 0 && choice < 5) choice += 1
                        Key.DirectionDown -> row = 1
                        Key.DirectionUp -> row = 0
                        Key.Back, Key.Escape -> onClose()
                        else -> Unit
                    }
                } else if (event.type == KeyEventType.KeyUp && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    if (row == 1) onWriteReview(choice) else onRate(choice)
                }
                event.key == Key.Back || event.key == Key.Escape || event.key == Key.DirectionCenter || event.key == Key.Enter ||
                    event.key == Key.DirectionLeft || event.key == Key.DirectionRight || event.key == Key.DirectionUp || event.key == Key.DirectionDown
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(minOf(420, screen - 32).dp)
                .background(Panel, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(12.dp))
                .tap { }
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Votre note", color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (index in 1..5) {
                    // Toucher une étoile choisit la note.
                    Text(
                        if (index <= choice) "★" else "☆",
                        color = if (index <= choice) Signal else Color(0xFF4D5860),
                        fontSize = 40.sp,
                        modifier = Modifier.tap {
                            choice = index
                            row = 0
                        },
                    )
                }
            }
            Text(
                when (choice) {
                    1 -> "1 étoile"
                    else -> "$choice étoiles"
                } + if (current > 0) "  (actuellement $current)" else "",
                color = Color(0xFFC5CDD6),
                fontSize = 14.sp,
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DialogButton("Valider", primary = true) { onRate(choice) }
                DialogButton("Écrire un avis", selected = row == 1) { onWriteReview(choice) }
                DialogButton("Annuler") { onClose() }
            }
        }
    }
}

/** Bouton des panneaux de notation : touchable, surligné quand il est choisi à la télécommande. */
@Composable
private fun DialogButton(label: String, primary: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (primary || selected) Ink else Paper,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(if (primary) Signal else if (selected) Accent else Color(0xFF2A333C), RoundedCornerShape(8.dp))
            .tap { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/**
 * Saisie de l'avis court (200 caractères). Mobile : clavier du téléphone (le clavier virtuel de la TV n'est pas repris).
 * Pré-rempli avec l'avis déjà en place. Valider enregistre (chaîne vide = efface l'avis sans toucher à la note),
 * Annuler ferme sans rien changer.
 */
@Composable
fun ReviewDialog(initial: String, stars: Int, onValidate: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(initial.take(200)) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .imePadding()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                    onCancel()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(minOf(640, screen - 32).dp)
                .background(Panel, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(12.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Votre avis" + if (stars in 1..5) "  ·  " + "★".repeat(stars) + "☆".repeat(5 - stars) else "",
                color = Paper,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { text = it.replace('\n', ' ').take(200) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Paper, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onValidate(text) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp)
                    .focusRequester(field)
                    .background(Color(0xFF2A333C), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("Votre avis en quelques mots…", color = Color(0xFF6F7A86), fontSize = 16.sp)
                        inner()
                    }
                },
            )
            Text("${text.length} / 200", color = Color(0xFF9AA3AD), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton("Valider", primary = true) { onValidate(text) }
                DialogButton("Annuler") { onCancel() }
            }
        }
    }
}

/**
 * Popup horizontal des avis courts laissés par les autres utilisateurs Plexiplay, ouvert en touchant les
 * étoiles de la fiche. Retour, « Fermer » ou un toucher à côté le referme.
 */
@Composable
fun ReviewsPopup(reviews: List<Review>, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(reviews) { runCatching { first.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .tap { onClose() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                    onClose()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .focusRequester(first),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .padding(bottom = 24.dp)
                .background(Ink, RoundedCornerShape(20.dp))
                .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .tap { }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Avis Plexiplay", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                DialogButton("Fermer") { onClose() }
            }
            if (reviews.isEmpty()) {
                Text("Aucun avis pour l'instant.", color = Color(0xFF9AA3AD), fontSize = 14.sp)
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                ) {
                    items(reviews) { entry -> ReviewCard(entry) }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(entry: Review) {
    Column(
        Modifier
            .width(minOf(260, (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * 0.72f).toInt()).dp)
            .background(Color(0xFF1C232B), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row {
            for (index in 1..5) {
                Text(if (index <= entry.stars) "★" else "☆", color = if (index <= entry.stars) Signal else Color(0xFF4D5860), fontSize = 15.sp)
            }
        }
        Text(entry.text, color = Color(0xFFD5DBE2), fontSize = 14.sp, lineHeight = 18.sp)
    }
}
