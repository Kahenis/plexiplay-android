package tv.plexiplay

import tv.plexiplay.plex.model.MediaInfo
import tv.plexiplay.plex.model.StreamInfo
import tv.plexiplay.player.PlaybackSettings

private fun videoStream(media: MediaInfo?): StreamInfo? =
    media?.Part?.firstOrNull()?.Stream?.firstOrNull { it.streamType == 1 }

/** Image 10 bits ou HDR (HDR10, HLG, Dolby Vision, couleurs BT.2020) d'après les détails donnés par Plex. */
internal fun isHdrOrTenBit(media: MediaInfo?): Boolean {
    val stream = videoStream(media)
    val trc = stream?.colorTrc.orEmpty().lowercase()
    return stream?.DOVIPresent == true ||
        trc in setOf("smpte2084", "arib-std-b67") ||
        (stream?.bitDepth ?: 0) >= 10 ||
        stream?.colorSpace.orEmpty().lowercase().startsWith("bt2020") ||
        media?.videoProfile.orEmpty().lowercase().contains("main 10") ||
        stream?.profile.orEmpty().lowercase().contains("main 10")
}

/** Plus grand que du 1080p (4K, UHD, ou largeur / hauteur au-delà du Full HD). */
internal fun isBiggerThanFullHd(media: MediaInfo?): Boolean {
    val stream = videoStream(media)
    val height = media?.height?.takeIf { it > 0 } ?: stream?.height ?: 0
    val width = media?.width?.takeIf { it > 0 } ?: stream?.width ?: 0
    return height > 1088 || width > 1920 || media?.videoResolution.orEmpty().lowercase() in setOf("4k", "2160")
}

/** Raison de la conversion imposée par « Box Player sur TV FullHD », ou null si le fichier peut être lu comme d'habitude. */
internal fun boxReason(settings: PlaybackSettings, media: MediaInfo?): String? = when {
    !settings.boxFullHd || media == null -> null
    isBiggerThanFullHd(media) -> "Box Player : image plus grande que 1080p"
    isHdrOrTenBit(media) -> "Box Player : image HDR ou 10 bits"
    else -> null
}

/** Lignes « Fichier d'origine » de la page Diagnostic du panneau « i ». */
internal fun sourceLines(settings: PlaybackSettings, media: MediaInfo?, boxReason: String?): List<Pair<String, String>> {
    val lines = mutableListOf<Pair<String, String>>()
    val part = media?.Part?.firstOrNull()
    val video = videoStream(media)
    lines += "Fichier d'origine" to ""
    lines += "Image" to listOfNotNull(
        media?.videoCodec?.uppercase()?.ifBlank { null },
        (video?.profile?.ifBlank { null } ?: media?.videoProfile?.ifBlank { null }),
        video?.level?.takeIf { it > 0 }?.let { "niveau $it" },
    ).joinToString(" · ").ifBlank { "?" }
    val width = media?.width?.takeIf { it > 0 } ?: video?.width ?: 0
    val height = media?.height?.takeIf { it > 0 } ?: video?.height ?: 0
    lines += "Taille" to if (width > 0 && height > 0) "${width}×$height" + (media?.videoResolution?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") else "?"
    lines += "Cadence" to (video?.frameRate?.takeIf { it > 0f }?.let { "%.3f i/s".format(it) } ?: media?.videoFrameRate?.ifBlank { null } ?: "?")
    lines += "Couleur" to listOfNotNull(
        video?.bitDepth?.takeIf { it > 0 }?.let { "$it bits" },
        video?.colorTrc?.ifBlank { null },
        video?.colorSpace?.ifBlank { null },
        if (video?.DOVIPresent == true) "Dolby Vision" else null,
    ).joinToString(" · ").ifBlank { "non précisée par Plex" }
    val bitrate = media?.bitrate ?: 0
    lines += "Débit" to if (bitrate > 0) "%.1f Mbit/s".format(bitrate / 1000f) else "?"
    lines += "Conteneur" to part?.container.orEmpty().ifBlank { "?" }
    lines += "Box Player" to if (settings.boxFullHd) "Oui" + (boxReason?.let { " · $it" } ?: " · fichier sans conversion imposée") else "Non"
    return lines
}
