package tv.plexiplay.player

enum class Engine { SERVER, TV }

enum class PlayerKind { EXO, MPV }

enum class AspectMode { AUTO, R16_9, R4_3, FULL, STRETCH }

data class PlaybackSettings(
    val uiLanguage: String = "fr",
    val audioLanguage: String = "fr",
    val subtitleLanguage: String = "off",
    val aspect: AspectMode = AspectMode.AUTO,
    val engine: Engine = Engine.TV,
    val player: PlayerKind = PlayerKind.EXO,
    val trailerCount: Int = 0,
    /** auto, on, off : mode compatible (HDR10 sans Dolby Vision, son converti). */
    val compatMode: String = "auto",
    /** Valeur effective, calculée par SessionStore (réglage + apprentissage). */
    val compat: Boolean = false,
    /** normal, direct (sans sous-titres, 4K fluide) ou light (rendu allégé). */
    val mpvMode: String = "light",
    /** Correction du format d'image (ExoPlayer) : rapport imposé + décalage d'un pixel. */
    val formatFix: Boolean = true,
    /** Films pour lesquels le repli automatique s'est déclenché (mode automatique seulement). */
    val compatTitles: Set<String> = emptySet(),
    /** Gain demandé au serveur pour le son converti (100 = normal). */
    val audioBoost: Int = 100,
    /** Films lus en conversion complète par le serveur (case « Forcer la conversion » ou repli automatique). */
    val convertTitles: Set<String> = emptySet(),
    /** Box d'opérateur sur TV Full HD : image 4K ou HDR / 10 bits convertie en H.264 8 bits SDR, 1080p maximum. */
    val boxFullHd: Boolean = false,
    /** « Dolby Vision sans saut d'image » (cadre TV Philips et équivalent) : les films Dolby Vision sont lus en mode compatible (HDR10). */
    val dvNoSkip: Boolean = false,
    /** « Toujours passer les intros » / « … les génériques » : saut automatique d'après les repères du serveur Plex (par défaut non). */
    val skipIntro: Boolean = false,
    val skipCredits: Boolean = false,
    /** Prérolls : « sequential » (fichiers de guilard.com/preroll, un par film dans l'ordre) ou « random » (prérolls du serveur Plex). */
    val prerollMode: String = "sequential",
    /** Dossier des prérolls introuvable : réglage grisé sur « Aléatoire serveur » (un appui long le réactive). */
    val prerollLocked: Boolean = false,
)

data class CodecEntry(
    val id: String,
    val kind: String,
    val softwareFree: Boolean,
)

/**
 * Liste déclarée par l'application. Le mode TV s'appuie sur les décodeurs
 * de la télé, puis sur l'extension FFmpeg Media3 si elle est présente
 * (codecs libres uniquement). Les codecs brevetés ne sont pas embarqués.
 */
object CodecRegistry {
    val entries: List<CodecEntry> = listOf(
        CodecEntry("h264", "video", false),
        CodecEntry("hevc", "video", false),
        CodecEntry("mpeg2video", "video", false),
        CodecEntry("mpeg4", "video", false),
        CodecEntry("vp8", "video", true),
        CodecEntry("vp9", "video", true),
        CodecEntry("av1", "video", true),
        CodecEntry("vc1", "video", false),
        CodecEntry("aac", "audio", true),
        CodecEntry("mp3", "audio", true),
        CodecEntry("opus", "audio", true),
        CodecEntry("vorbis", "audio", true),
        CodecEntry("flac", "audio", true),
        CodecEntry("pcm", "audio", true),
        CodecEntry("ac3", "audio", false),
        CodecEntry("eac3", "audio", false),
        CodecEntry("dts", "audio", false),
        CodecEntry("truehd", "audio", false),
        CodecEntry("subrip", "text", true),
        CodecEntry("ass", "text", true),
        CodecEntry("pgs", "text", false),
        CodecEntry("vobsub", "text", false),
    )

    fun find(codec: String?): CodecEntry? {
        val key = codec?.lowercase().orEmpty()
        if (key.isEmpty()) return null
        return entries.firstOrNull { it.id == key || key.startsWith(it.id) }
    }
}

enum class Delivery { DIRECT, TRANSCODE }

data class DeliveryChoice(val delivery: Delivery, val reason: String)

object DeliveryPlanner {
    fun choose(
        engine: Engine,
        player: PlayerKind,
        videoCodec: String?,
        audioCodec: String?,
        deviceDecodes: (String) -> Boolean,
        ffmpegPresent: Boolean,
    ): DeliveryChoice {
        if (player == PlayerKind.MPV) {
            return DeliveryChoice(Delivery.DIRECT, "mpv : lecture directe")
        }
        val software = ffmpegPresent && engine == Engine.TV
        val videoOk = canPlay(videoCodec, deviceDecodes, software)
        val audioOk = canPlay(audioCodec, deviceDecodes, software)
        return if (videoOk && audioOk) {
            DeliveryChoice(Delivery.DIRECT, "ExoPlayer : lecture directe")
        } else {
            DeliveryChoice(Delivery.TRANSCODE, "ExoPlayer ne décode pas ce codec : transcodage")
        }
    }

    private fun canPlay(
        codec: String?,
        deviceDecodes: (String) -> Boolean,
        ffmpegPresent: Boolean,
    ): Boolean {
        if (codec.isNullOrBlank()) return true
        if (deviceDecodes(codec)) return true
        val entry = CodecRegistry.find(codec) ?: return false
        return ffmpegPresent && entry.softwareFree
    }
}
