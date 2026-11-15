package tv.plexiplay.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ligne du panneau « i » : un titre de section (valeur vide) ou « libellé : valeur ». */
internal typealias InfoLine = Pair<String, String>

/** Panneau « Informations de lecture » (touche « i »), identique pour ExoPlayer et mpv. [twoColumns] : page Diagnostic, plus longue. */
@Composable
internal fun InfoPanel(lines: List<InfoLine>, modifier: Modifier = Modifier, twoColumns: Boolean = false) {
    if (lines.isEmpty()) return
    // Mobile : largeur limitée à l'écran ; Diagnostic sur deux colonnes seulement s'il y a la place, sinon une colonne qui défile.
    val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val room = (screen - 68).coerceAtLeast(260)
    val split = twoColumns && room >= 2 * 470 + 16
    val colWidth = if (split) 470 else minOf(if (twoColumns) 470 else 520, room)
    @Composable
    fun Block(block: List<InfoLine>, labelWidth: Int, size: Int) {
        Column(Modifier.width(colWidth.dp)) {
            block.forEach { (label, value) ->
                if (value.isEmpty()) {
                    Text(label.uppercase(), color = Color(0xFF5DD39E), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp, bottom = 1.dp))
                } else {
                    Row(Modifier.padding(top = 1.dp)) {
                        Text(label, color = Color(0xFF9AA3AD), fontSize = size.sp, modifier = Modifier.width(labelWidth.dp))
                        Text(value, color = Color.White, fontSize = size.sp, lineHeight = (size + 2).sp, modifier = Modifier.width((colWidth - labelWidth).dp))
                    }
                }
            }
        }
    }
    Row(
        modifier
            .padding(20.dp)
            .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - 40).dp)
            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
    ) {
        if (!twoColumns) {
            Block(lines, if (screen < 600) 96 else 112, if (screen < 600) 12 else 13)
        } else if (split) {
            // Coupe à un titre de section proche du milieu pour équilibrer les deux colonnes.
            val half = lines.size / 2
            val cut = (half..lines.lastIndex).firstOrNull { lines[it].second.isEmpty() } ?: half
            Block(lines.subList(0, cut), 96, 10)
            Block(lines.subList(cut, lines.size), 96, 10)
        } else {
            Block(lines, 96, 11)
        }
    }
}

/** Section « Lecture » : direct ou converti, et ce que le serveur fait de l'image, du son et des sous-titres. */
internal fun playbackLines(clip: ClipPlay?, player: String): List<InfoLine> {
    if (clip == null) return emptyList()
    val lines = mutableListOf<InfoLine>()
    val uri = runCatching { android.net.Uri.parse(clip.url) }.getOrNull()
    fun param(name: String) = uri?.getQueryParameter(name).orEmpty()
    lines += "Lecture" to ""
    lines += "Lecteur" to "$player · Plexiplay ${tv.plexiplay.plex.PlexHeaders.VERSION}"
    if (clip.direct) {
        lines += "Mode" to "Lecture directe (fichier envoyé tel quel)"
        lines += "Image" to "directe (non convertie)"
        lines += "Son" to "direct (non converti)"
    } else {
        val streamCopy = param("directStream") == "1"
        lines += "Mode" to "Conversion par le serveur"
        val resolution = param("videoResolution")
        val videoOut = param("videoCodec").ifBlank { "h264" }.uppercase()
        lines += "Image" to if (streamCopy && resolution.isBlank()) "copiée du fichier (non convertie)" else "convertie en $videoOut" + (if (resolution.isNotBlank()) " · ${resolution}p" else "")
        val audioOut = param("audioCodec")
        val channels = param("maxAudioChannels")
        lines += "Son" to if (audioOut.isBlank() && streamCopy) "copié du fichier" else "converti en ${audioOut.ifBlank { "AAC" }.uppercase()}" + (if (channels.isNotBlank()) " (jusqu'à $channels canaux)" else "")
    }
    val parts = clip.info.split(" · ").filter { it.isNotBlank() }
    if (parts.size >= 2) {
        lines += "Fichier" to parts.drop(1).joinToString(" · ") { part ->
            when (part.lowercase()) {
                "dca", "dts" -> "DTS"
                "dtshd", "dts-hd" -> "DTS-HD"
                "hevc", "h265" -> "HEVC"
                "h264", "avc" -> "H.264"
                "truehd" -> "TrueHD"
                "eac3" -> "E-AC3"
                "ac3" -> "AC3"
                "aac" -> "AAC"
                else -> part.uppercase()
            }
        }
        if (parts[0].contains("compatible", true) || parts[0].contains("conversion complète", true)) lines += "Remarque" to parts[0]
    }
    val subs = when {
        clip.textMode == "off" -> "Sans"
        else -> clip.textLanguage.ifBlank { "?" } + (if (clip.textMode == "forced") " (forcés)" else " (complets)")
    }
    lines += "Sous-titres" to subs
    return lines
}

internal fun codecLabel(mime: String?): String {
    val value = mime.orEmpty().lowercase()
    return when {
        value.isBlank() -> "?"
        "dolby-vision" in value || "dvhe" in value -> "Dolby Vision (HEVC)"
        "hevc" in value || "h265" in value -> "HEVC"
        "avc" in value || "h264" in value -> "H.264"
        "av01" in value || "av1" in value -> "AV1"
        "vp9" in value -> "VP9"
        "eac3" in value || "ec-3" in value -> "E-AC3 (Dolby Digital+)"
        "true-hd" in value || "truehd" in value || "mlp" in value -> "TrueHD"
        "ac3" in value || "ac-3" in value -> "AC3 (Dolby Digital)"
        "dts" in value && "hd" in value -> "DTS-HD"
        "dts" in value || "dca" in value -> "DTS"
        "mp4a" in value || "aac" in value -> "AAC"
        "opus" in value -> "Opus"
        "flac" in value -> "FLAC"
        "mpeg" in value || "mp3" in value || "mpa" in value -> "MP3"
        else -> value.substringAfter('/')
    }
}

internal fun channelsLabel(count: Int): String = when (count) {
    1 -> "mono"
    2 -> "stéréo"
    6 -> "5.1"
    8 -> "7.1"
    else -> if (count > 0) "$count canaux" else ""
}

/** Décodeur logiciel (FFmpeg, décodeur Google) ou matériel (puce de la TV), d'après son nom. */
internal fun decoderKind(name: String): String {
    if (name.isBlank()) return "pas encore initialisé"
    val lower = name.lowercase()
    return when {
        "ffmpeg" in lower -> "FFmpeg (logiciel)"
        "c2.android" in lower || "omx.google" in lower || lower.endsWith(".sw") || ".sw." in lower -> "$name (logiciel)"
        else -> "$name (matériel TV)"
    }
}
