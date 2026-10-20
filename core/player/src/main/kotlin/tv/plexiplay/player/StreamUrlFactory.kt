package tv.plexiplay.player

import java.net.URLEncoder
import java.util.UUID

object StreamUrlFactory {
    fun direct(serverUri: String, partKey: String, token: String): String {
        val path = if (partKey.startsWith("http")) partKey else serverUri.trimEnd('/') + partKey
        val join = if (path.contains("?")) "&" else "?"
        return path + join + "X-Plex-Token=" + enc(token)
    }

    fun transcode(
        serverUri: String,
        ratingKey: String,
        token: String,
        clientId: String,
        session: String = UUID.randomUUID().toString(),
        audioStreamId: Int? = null,
        subtitleStreamId: Int? = null,
        mediaIndex: Int = 0,
        videoResolution: String? = null,
        maxVideoBitrate: Int? = null,
        videoCodec: String? = null,
        audioCodec: String? = null,
        directStream: Boolean = false,
        burnSubtitles: Boolean = false,
        // Baisse de résolution en HEVC : le serveur garde l'image HDR 10 bits et tout se fait sur la carte graphique, comme avec l'appli officielle.
        hevcOutput: Boolean = false,
        // Son converti : jusqu'à 6 canaux (5.1), comme l'appli officielle. Sans cela le serveur mélange en stéréo.
        maxAudioChannels: Int? = null,
    ): String {
        val base = serverUri.trimEnd('/') + "/video/:/transcode/universal/start.m3u8"
        val q = mutableListOf(
            "path=" + enc("/library/metadata/$ratingKey"),
            "mediaIndex=$mediaIndex",
            "partIndex=0",
            "protocol=hls",
            "directPlay=0",
            "directStream=${if (directStream) 1 else 0}",
            "fastSeek=1",
            "session=" + enc(session),
            "X-Plex-Platform=Android",
            "X-Plex-Product=Plexiplay",
            "X-Plex-Client-Identifier=" + enc(clientId),
            "X-Plex-Token=" + enc(token),
        )
        if (audioStreamId != null && audioStreamId > 0) q += "audioStreamID=$audioStreamId"
        if (subtitleStreamId != null) q += "subtitleStreamID=$subtitleStreamId"
        // « Sans » : demandé explicitement, pour que les réglages automatiques du compte Plex ne regravent pas de sous-titres.
        if (subtitleStreamId == 0) q += "subtitles=none"
        // Sous-titres incrustés dans l'image par le serveur : s'affichent quel que soit leur format.
        if (burnSubtitles && subtitleStreamId != null && subtitleStreamId > 0) q += "subtitles=burn"
        if (!videoResolution.isNullOrBlank()) q += "videoResolution=" + enc(videoResolution)
        if (maxVideoBitrate != null && maxVideoBitrate > 0) q += "maxVideoBitrate=$maxVideoBitrate"
        if (!videoCodec.isNullOrBlank()) q += "videoCodec=" + enc(videoCodec)
        if (!audioCodec.isNullOrBlank()) q += "audioCodec=" + enc(audioCodec)
        if (maxAudioChannels != null && maxAudioChannels > 0) q += "maxAudioChannels=$maxAudioChannels"
        if (directStream || hevcOutput) {
            // Autorise le serveur à copier l'image HEVC (HDR) au lieu de la convertir en H.264.
            q += "X-Plex-Client-Profile-Extra=" +
                enc("append-transcode-target-codec(type=videoProfile&context=streaming&protocol=hls&videoCodec=h264,hevc)")
        }
        return base + "?" + q.joinToString("&")
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
