package tv.plexiplay.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.plexiplay.plex.PlexClient
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.plex.model.ServerEndpoint

/** Fiche du titre en cours (appui court sur « i ») : nom, détails, équipe, distribution, résumé. */
data class TitleInfo(
    val key: String,
    val heading: String,
    val details: String = "",
    val lines: List<Pair<String, String>> = emptyList(),
    val summary: String = "",
)

@Composable
internal fun TitleInfoPanel(info: TitleInfo, nowPlaying: String, modifier: Modifier = Modifier) {
    // Mobile : largeur limitée à l'écran.
    val panelWidth = minOf(660, androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 40)
    Column(
        modifier
            .padding(20.dp)
            .width(panelWidth.dp)
            .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(info.heading, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (info.details.isNotBlank()) {
            Text(info.details, color = Color(0xFF5DD39E), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        info.lines.forEach { (label, value) ->
            Row(Modifier.padding(top = 5.dp)) {
                Text(label, color = Color(0xFF9AA3AD), fontSize = 14.sp, modifier = Modifier.width(110.dp))
                Text(value, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width((panelWidth - 36 - 110).coerceAtLeast(120).dp))
            }
        }
        if (info.summary.isNotBlank()) {
            Text(
                info.summary,
                color = Color(0xFFE4E7EB),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (nowPlaying.isNotBlank()) {
            Row(Modifier.padding(top = 10.dp)) {
                Text("Lecture en cours :", color = Color(0xFF5DD39E), fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                Text(nowPlaying, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** « 4K (3840×2160) », « Full HD (1920×800) »… */
internal fun resolutionLabel(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return ""
    val name = when {
        width >= 3600 || height >= 2000 -> "4K"
        width >= 2400 || height >= 1300 -> "2K"
        width >= 1800 || height >= 1000 -> "Full HD"
        width >= 1200 || height >= 700 -> "HD"
        else -> "SD"
    }
    return "$name (${width}×${height})"
}

/** Nom français d'une langue à partir d'un code Plex ou ExoPlayer (fr, fra, fre, eng…). */
internal fun languageName(code: String): String {
    val raw = code.trim().lowercase().substringBefore('-').substringBefore('_')
    if (raw.isBlank() || raw == "und" || raw == "?") return ""
    val two = when (raw) {
        "fra", "fre" -> "fr"; "eng" -> "en"; "deu", "ger" -> "de"; "spa" -> "es"; "ita" -> "it"
        "por" -> "pt"; "jpn" -> "ja"; "kor" -> "ko"; "zho", "chi" -> "zh"; "rus" -> "ru"
        "ara" -> "ar"; "nld", "dut" -> "nl"; "pol" -> "pl"; "tur" -> "tr"; "swe" -> "sv"
        else -> raw
    }
    val name = java.util.Locale(two).getDisplayLanguage(java.util.Locale.FRENCH)
    return if (name.isBlank() || name.equals(two, ignoreCase = true)) raw.uppercase() else name.replaceFirstChar { it.uppercase() }
}

/** Ligne « Lecture en cours » : image / son / langue du son / sous-titres. */
internal fun nowPlayingLine(width: Int, height: Int, audioCodec: String, audioChannels: Int, audioLanguage: String, subtitles: String): String {
    val sound = listOf(if (audioCodec.isBlank()) "" else codecLabel(audioCodec), channelsLabel(audioChannels)).filter { it.isNotBlank() }.joinToString(" ")
    return listOf(
        resolutionLabel(width, height),
        sound,
        languageName(audioLanguage),
        "Sous-titres : " + subtitles.ifBlank { "aucun" },
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun runtimeLabel(ms: Long): String {
    val minutes = (ms / 60_000L).toInt()
    if (minutes <= 0) return ""
    return if (minutes >= 60) "${minutes / 60} h %02d".format(minutes % 60) else "$minutes min"
}

private fun names(tags: List<tv.plexiplay.plex.model.TagItem>, limit: Int): String =
    tags.map { it.tag }.filter { it.isNotBlank() }.take(limit).joinToString(", ")

/** Demande au serveur la fiche d'un film ou d'un épisode (pour un épisode, la série complète genres et distribution). */
internal fun titleInfoOf(client: PlexClient, server: ServerEndpoint, key: String): TitleInfo? {
    val meta = client.metadata(server, key) ?: return null
    val episode = meta.type == "episode"
    val show: Metadata? = if (episode && meta.grandparentRatingKey.isNotBlank()) {
        runCatching { client.metadata(server, meta.grandparentRatingKey) }.getOrNull()
    } else null
    val heading = if (episode) {
        val code = if (meta.parentIndex > 0 && meta.index > 0) "S%02dE%02d".format(meta.parentIndex, meta.index) else ""
        listOf(meta.grandparentTitle, code, meta.title).filter { it.isNotBlank() }.joinToString(" - ")
    } else {
        meta.title
    }
    val year = meta.year.takeIf { it > 0 } ?: show?.year?.takeIf { it > 0 }
    val genres = names(meta.Genre.ifEmpty { show?.Genre.orEmpty() }, 4)
    val grade = (meta.audienceRating.takeIf { it > 0f } ?: meta.rating.takeIf { it > 0f })?.let { "★ %.1f".format(java.util.Locale.FRANCE, it) }.orEmpty()
    val details = listOf(
        year?.toString().orEmpty(),
        runtimeLabel(meta.duration),
        genres,
        meta.contentRating.ifBlank { show?.contentRating.orEmpty() },
        grade,
    ).filter { it.isNotBlank() }.joinToString(" · ")
    val cast = meta.Role.ifEmpty { show?.Role.orEmpty() }.map { it.tag }.filter { it.isNotBlank() }.take(6).joinToString(", ")
    val lines = listOf(
        "Réalisation" to names(meta.Director, 3),
        "Production" to names(meta.Producer, 3),
        "Scénario" to names(meta.Writer, 3),
        "Avec" to cast,
    ).filter { it.second.isNotBlank() }
    return TitleInfo(key, heading.ifBlank { "Titre" }, details, lines, meta.summary.ifBlank { show?.summary.orEmpty() })
}
