package tv.plexiplay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.plexiplay.plex.model.LibrarySection
import tv.plexiplay.plex.model.ServerEndpoint
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper
import tv.plexiplay.ui.theme.Panel

private sealed class PickerRow {
    object Server : PickerRow()
    data class Library(val section: LibrarySection) : PickerRow()
    object Ok : PickerRow()
    object Cancel : PickerRow()
}

/**
 * Choix des bibliothèques affichées dans le menu de gauche, serveur par serveur.
 * Une seule zone prend le focus ; haut / bas déplacent le curseur, OK coche ou valide.
 */
@Composable
fun LibraryPicker(
    servers: List<ServerEndpoint>,
    startIndex: Int,
    fetch: (ServerEndpoint) -> List<LibrarySection>,
    hiddenOf: (ServerEndpoint) -> Set<String>,
    onSave: (ServerEndpoint, Set<String>) -> Unit,
    onClose: () -> Unit,
) {
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (servers.size - 1).coerceAtLeast(0))) }
    var libraries by remember { mutableStateOf<List<LibrarySection>?>(null) }
    var hidden by remember { mutableStateOf<Set<String>>(emptySet()) }
    var message by remember { mutableStateOf("") }
    var cursor by remember { mutableIntStateOf(0) }
    val server = servers.getOrNull(index)
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(index) {
        val current = server ?: return@LaunchedEffect
        libraries = null
        message = ""
        val loaded = withContext(Dispatchers.IO) { runCatching { fetch(current) }.getOrNull() }
        hidden = hiddenOf(current)
        libraries = loaded ?: emptyList()
        if (loaded == null) message = "Serveur injoignable"
        else if (loaded.isEmpty()) message = "Aucune bibliothèque sur ce serveur"
    }

    val rows = buildList<PickerRow> {
        if (servers.size > 1) add(PickerRow.Server)
        libraries.orEmpty().forEach { add(PickerRow.Library(it)) }
        add(PickerRow.Ok)
        add(PickerRow.Cancel)
    }
    if (cursor > rows.lastIndex) cursor = rows.lastIndex.coerceAtLeast(0)
    LaunchedEffect(cursor) { listState.animateScrollToItem(cursor.coerceAtLeast(0)) }

    fun activate() {
        when (val row = rows.getOrNull(cursor)) {
            PickerRow.Server -> index = (index + 1) % servers.size
            is PickerRow.Library -> {
                val key = row.section.key
                if (key in hidden) {
                    hidden = hidden - key
                    message = ""
                } else {
                    val remaining = libraries.orEmpty().count { it.key !in hidden && it.key != key }
                    if (remaining == 0) message = "Gardez au moins une bibliothèque affichée"
                    else {
                        hidden = hidden + key
                        message = ""
                    }
                }
            }
            PickerRow.Ok -> {
                if (server != null && libraries != null && libraries!!.isNotEmpty()) onSave(server, hidden)
                onClose()
            }
            PickerRow.Cancel -> onClose()
            null -> Unit
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> cursor = (cursor - 1).coerceAtLeast(0)
                        Key.DirectionDown -> cursor = (cursor + 1).coerceAtMost(rows.lastIndex)
                        Key.Back, Key.Escape -> onClose()
                        else -> Unit
                    }
                } else if (event.type == KeyEventType.KeyUp && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    activate()
                }
                true
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(440.dp)
                .background(Panel, RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF3A4450), RoundedCornerShape(10.dp))
                .padding(16.dp),
        ) {
            Text("Bibliothèques affichées", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Haut / bas pour choisir, OK pour cocher ou décocher.",
                color = Color(0xFF9AA3AD),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            if (libraries == null) {
                Text("Chargement…", color = Color(0xFF9AA3AD), fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(max = 260.dp),
            ) {
                itemsIndexed(rows) { position, row ->
                    val selected = position == cursor
                    when (row) {
                        PickerRow.Server -> PickerLine(
                            "Serveur : ${server?.name.orEmpty()}",
                            selected,
                        )
                        is PickerRow.Library -> PickerLine(
                            row.section.title.ifBlank { "Bibliothèque" },
                            selected,
                            checked = row.section.key !in hidden,
                        )
                        PickerRow.Ok -> PickerButton("OK", selected, true)
                        PickerRow.Cancel -> PickerButton("Annuler", selected, false)
                    }
                }
            }
            if (message.isNotBlank()) {
                Text(message, color = Color(0xFFE8C07A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun PickerLine(label: String, selected: Boolean, checked: Boolean? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Accent else Color(0xFF2A333C), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (checked != null) {
            val edge = if (selected) Ink else Paper
            Box(
                Modifier
                    .size(18.dp)
                    .border(2.dp, edge, RoundedCornerShape(3.dp))
                    .background(if (checked) edge else Color.Transparent, RoundedCornerShape(3.dp)),
            )
        }
        Text(
            label,
            color = if (selected) Ink else Paper,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PickerButton(label: String, selected: Boolean, primary: Boolean) {
    Text(
        label,
        color = if (selected) Ink else Paper,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    selected -> Accent
                    primary -> Color(0xFF3A4450)
                    else -> Color(0xFF2A333C)
                },
                RoundedCornerShape(6.dp),
            )
            .padding(vertical = 8.dp),
    )
}
