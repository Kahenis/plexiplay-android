package tv.plexiplay.ui.player

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.WindowManager

/** Page « Diagnostic » du panneau « i » : appareil, écran, décodeurs, demande envoyée au serveur, réseau. */

private fun mb(bytes: Long) = "${bytes / (1024 * 1024)} Mo"

internal fun deviceLines(context: Context): List<InfoLine> {
    val lines = mutableListOf<InfoLine>()
    lines += "Appareil" to ""
    lines += "Modèle" to "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim() else ""
    lines += "Puce" to listOf(soc.takeIf { it.isNotBlank() && it != "unknown unknown" }, Build.HARDWARE).filterNotNull().joinToString(" · ")
    lines += "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        lines += "Mémoire" to "${mb(info.availMem)} libres sur ${mb(info.totalMem)}" + if (info.lowMemory) " · MÉMOIRE BASSE" else ""
        lines += "Appareil léger" to if (manager.isLowRamDevice) "oui" else "non"
    }
    return lines
}

@Suppress("DEPRECATION")
internal fun displayLines(context: Context): List<InfoLine> {
    val lines = mutableListOf<InfoLine>()
    lines += "Écran" to ""
    runCatching {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = manager.defaultDisplay
        val mode = display.mode
        lines += "Mode" to "${mode.physicalWidth}×${mode.physicalHeight} à %.1f Hz".format(mode.refreshRate)
        val hdr = display.hdrCapabilities?.supportedHdrTypes?.map {
            when (it) {
                1 -> "Dolby Vision"
                2 -> "HDR10"
                3 -> "HLG"
                4 -> "HDR10+"
                else -> "type $it"
            }
        }.orEmpty()
        lines += "HDR accepté" to if (hdr.isEmpty()) "aucun" else hdr.joinToString(", ")
    }
    return lines
}

private fun codecProfiles(caps: MediaCodecInfo.CodecCapabilities, mime: String): String {
    val levels = caps.profileLevels
    return if (mime == "video/hevc") {
        val main10 = levels.any { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 }
        val hdr10 = levels.any { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 }
        "10 bits : ${if (main10) "oui" else "non"}" + if (hdr10) " · HDR10 : oui" else ""
    } else {
        val high = levels.any { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh }
        "profil High : ${if (high) "oui" else "non"}"
    }
}

internal fun decoderLines(): List<InfoLine> {
    val lines = mutableListOf<InfoLine>()
    lines += "Décodeurs de la TV ou de la box" to ""
    val list = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos }.getOrNull() ?: return lines
    for ((label, mime) in listOf("HEVC" to "video/hevc", "H.264" to "video/avc")) {
        list.filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, true) } }.take(3).forEach { info ->
            runCatching {
                val caps = info.getCapabilitiesForType(mime)
                val video = caps.videoCapabilities
                val hardware = if (Build.VERSION.SDK_INT >= 29) (if (info.isHardwareAccelerated && !info.isSoftwareOnly) "matériel" else "logiciel") else "?"
                val maxW = video.supportedWidths.upper
                val maxH = video.supportedHeights.upper
                val fps = runCatching { video.getSupportedFrameRatesFor(1920, 1080).upper.toInt() }.getOrDefault(0)
                val k4 = if (video.isSizeSupported(3840, 2160)) "oui" else "non"
                lines += label to "${info.name} ($hardware)"
                lines += "  taille max" to "${maxW}×$maxH · 1080p : jusqu'à $fps i/s · 4K : $k4 · ${codecProfiles(caps, mime)} · ${caps.maxSupportedInstances} flux"
            }
        }
    }
    return lines
}

/** Ce que Plexiplay demande au serveur, sans le jeton : lu dans l'adresse du flux. */
internal fun requestLines(clip: ClipPlay?): List<InfoLine> {
    if (clip == null) return emptyList()
    val lines = mutableListOf<InfoLine>()
    lines += "Demande au serveur" to ""
    if (clip.direct) {
        lines += "Type" to "lecture directe du fichier"
    } else {
        val uri = runCatching { android.net.Uri.parse(clip.url) }.getOrNull()
        fun p(name: String) = uri?.getQueryParameter(name)?.ifBlank { null } ?: "—"
        lines += "Type" to "flux HLS converti"
        lines += "Image" to "codec ${p("videoCodec")} · résolution ${p("videoResolution")} · débit max ${p("maxVideoBitrate")} kbit/s"
        lines += "Copie image" to "directStream ${p("directStream")} · profil étendu ${if (clip.url.contains("append-transcode-target-codec")) "oui (HEVC permis)" else "non"}"
        lines += "Son" to "codec ${p("audioCodec")} · canaux max ${p("maxAudioChannels")} · gain ${p("audioBoost")}"
        lines += "Pistes" to "audio ${p("audioStreamID")} · sous-titres ${p("subtitleStreamID")}"
    }
    lines += "Raison" to clip.info.split(" · ").firstOrNull().orEmpty().ifBlank { "?" }
    return lines
}

@Suppress("DEPRECATION")
internal fun networkLines(context: Context): List<InfoLine> {
    val lines = mutableListOf<InfoLine>()
    lines += "Connexion de l'appareil" to ""
    runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        val kind = when {
            caps == null -> "aucune"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "câble (Ethernet)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            else -> "autre"
        }
        lines += "Type" to kind
        if (caps != null) {
            lines += "Débit annoncé" to "descendant ${caps.linkDownstreamBandwidthKbps / 1000} Mbit/s · montant ${caps.linkUpstreamBandwidthKbps / 1000} Mbit/s"
            if (Build.VERSION.SDK_INT >= 29 && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val signal = caps.signalStrength
                if (signal != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) lines += "Signal Wi-Fi" to "$signal dBm"
            }
        }
    }
    return lines
}

/** Page Diagnostic complète pour ExoPlayer ou mpv : [live] = mesures de lecture propres au lecteur. */
internal fun diagnosticLines(context: Context, clip: ClipPlay?, live: List<InfoLine>): List<InfoLine> =
    (clip?.fileLines.orEmpty()) + requestLines(clip) + live + deviceLines(context) + displayLines(context) + decoderLines() + networkLines(context)
