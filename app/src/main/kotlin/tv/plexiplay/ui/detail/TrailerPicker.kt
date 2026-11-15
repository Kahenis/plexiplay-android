package tv.plexiplay.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.ui.player.ClipPlay
import tv.plexiplay.ui.shell.PosterStore
import tv.plexiplay.ui.theme.Accent
import tv.plexiplay.ui.theme.FocusRing
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper
import java.util.Locale

/** Une bande-annonce proposée dans la bande de choix : sa fiche Plex, le clip prêt à lire, et les lettres de sa langue (vide si inconnue). */
data class TrailerOption(
    val meta: Metadata,
    val clip: ClipPlay,
    val language: String,
)

// Codes bibliographiques à trois lettres que Java ne connaît pas.
private val OLD_CODES = mapOf(
    "fre" to "FR", "ger" to "DE", "dut" to "NL", "chi" to "ZH", "cze" to "CS", "gre" to "EL",
    "rum" to "RO", "slo" to "SK", "ice" to "IS", "alb" to "SQ", "arm" to "HY", "baq" to "EU",
    "bur" to "MY", "geo" to "KA", "mac" to "MK", "may" to "MS", "per" to "FA", "tib" to "BO", "wel" to "CY",
)

private val NAMES = listOf(
    "vostfr" to "VOST", "vost" to "VOST",
    "français" to "FR", "francais" to "FR", "french" to "FR", "québécois" to "FR",
    "english" to "EN", "anglais" to "EN",
    "deutsch" to "DE", "german" to "DE", "allemand" to "DE",
    "español" to "ES", "espanol" to "ES", "spanish" to "ES", "espagnol" to "ES", "castellano" to "ES", "latino" to "ES",
    "italiano" to "IT", "italian" to "IT", "italien" to "IT",
    "português" to "PT", "portuguese" to "PT", "portugais" to "PT",
    "日本語" to "JA", "japanese" to "JA", "japonais" to "JA",
    "한국어" to "KO", "korean" to "KO", "coréen" to "KO",
    "русский" to "RU", "russian" to "RU", "russe" to "RU",
)

/** Deux lettres (FR, EN, DE…) d'un code de langue Plex : « fra », « fre », « fr », « fr-FR », « eng »… Vide si inconnu. */
fun languageLetters(raw: String): String {
    val code = raw.trim().lowercase(Locale.ROOT)
    if (code.isBlank() || code == "und" || code == "zxx" || code == "mis" || code == "mul") return ""
    if (code.length == 2) return code.uppercase(Locale.ROOT)
    if (code.length > 2 && (code[2] == '-' || code[2] == '_')) return code.substring(0, 2).uppercase(Locale.ROOT)
    OLD_CODES[code]?.let { return it }
    if (code.length == 3) {
        for (iso in Locale.getISOLanguages()) {
            val three = runCatching { Locale(iso).isO3Language }.getOrDefault("")
            if (three.equals(code, true)) return iso.uppercase(Locale.ROOT)
        }
    }
    return ""
}

/** Langue d'une bande-annonce : piste audio du fichier, sinon mots du titre (VF, VOST, « english »…). Vide si rien ne le dit. */
fun trailerLanguage(meta: Metadata): String {
    for (media in meta.Media) for (part in media.Part) for (stream in part.Stream) {
        if (stream.streamType != 2) continue
        val letters = languageLetters(stream.languageCode).ifBlank { languageLetters(stream.languageTag) }
        if (letters.isNotBlank()) return letters
        val byName = nameLetters(stream.language)
        if (byName.isNotBlank()) return byName
    }
    val title = meta.title.lowercase(Locale.ROOT)
    val words = title.split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }
    nameLetters(title).let { if (it.isNotBlank()) return it }
    if ("vf" in words || "vff" in words || "vfq" in words || "truefrench" in words) return "FR"
    if ("vo" in words) return "VO"
    return ""
}

private fun nameLetters(text: String): String {
    val lower = text.lowercase(Locale.ROOT)
    for ((word, letters) in NAMES) if (lower.contains(word)) return letters
    return ""
}

private fun durationLabel(ms: Long): String {
    if (ms <= 0L) return ""
    val total = (ms + 500) / 1000
    val minutes = total / 60
    val seconds = total % 60
    return if (minutes > 0) "$minutes min ${seconds.toString().padStart(2, '0')}" else "$seconds s"
}

/**
 * Bande de choix des bandes-annonces, par-dessus la fiche : une vignette par bande-annonce,
 * flèches gauche / droite pour choisir, OK pour lancer, Retour pour fermer et revenir à la fiche.
 */
@Composable
fun TrailerStrip(
    options: List<TrailerOption>,
    wide: (String) -> List<String>,
    posters: PosterStore,
    onPick: (TrailerOption) -> Unit,
    onClose: () -> Unit,
) {
    val images = remember { mutableStateMapOf<String, android.graphics.Bitmap>() }
    val first = remember { FocusRequester() }
    LaunchedEffect(options) {
        if (options.isNotEmpty()) runCatching { first.requestFocus() }
        for (option in options) {
            val path = option.meta.thumb.ifBlank { option.meta.art }
            if (path.isBlank()) continue
            posters.load("trailer-" + option.meta.ratingKey, path, wide(path)) { bitmap -> images[option.meta.ratingKey] = bitmap }
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .background(Ink, RoundedCornerShape(20.dp))
                .border(1.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Bandes-annonces", color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${options.size} disponibles  ·  OK : lancer  ·  Retour : fermer", color = Color(0xFF9AA3AD), fontSize = 13.sp)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
                itemsIndexed(options, key = { _, item -> item.meta.ratingKey }) { index, option ->
                    TrailerTile(
                        option = option,
                        image = images[option.meta.ratingKey],
                        requester = if (index == 0) first else null,
                        onClick = { onPick(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrailerTile(option: TrailerOption, image: android.graphics.Bitmap?, requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .border(3.dp, if (focused) FocusRing else Color(0xFF3A4450), shape)
                .clickable(onClick = onClick),
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF1C232B)))
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("🎬", fontSize = 40.sp, modifier = Modifier.align(Alignment.Center))
            }
            // Bas assombri pour la durée.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))),
            )
            val duration = durationLabel(option.meta.duration)
            if (duration.isNotBlank()) {
                Text(
                    duration,
                    color = Paper,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 8.dp),
                )
            }
            if (option.language.isNotBlank()) {
                Text(
                    option.language,
                    color = Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Accent, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (focused) {
                Text(
                    "▶",
                    color = Paper,
                    fontSize = 30.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0x99000000), RoundedCornerShape(30.dp))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
        Text(
            option.meta.title.ifBlank { "Bande-annonce" },
            color = if (focused) Paper else Color(0xFFC5CDD6),
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
