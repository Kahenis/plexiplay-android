package tv.plexiplay.player

import android.media.MediaCodecList
import tv.plexiplay.player.CodecRegistry

object DeviceCodecs {
    fun decodes(codec: String): Boolean {
        val mime = mime(codec) ?: return false
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return list.codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, true) }
        }
    }

    private fun hasMime(mime: String): Boolean {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return list.codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, true) }
        }
    }

    /** Résumé affiché dans les paramètres : ce que la TV annonce savoir décoder. */
    fun summary(): String {
        fun yn(ok: Boolean) = if (ok) "oui" else "non"
        return "Dolby Vision : ${yn(hasMime("video/dolby-vision"))} · HEVC : ${yn(hasMime("video/hevc"))} · " +
            "AC3/EAC3 : ${yn(hasMime("audio/ac3") && hasMime("audio/eac3"))} · " +
            "DTS : ${yn(hasMime("audio/vnd.dts"))} · DTS-HD : ${yn(hasMime("audio/vnd.dts.hd"))} · " +
            "TrueHD : ${yn(hasMime("audio/true-hd"))}"
    }

    /** Codec audio demandé au serveur quand seul le son doit être converti. */
    fun convertedAudio(): String = if (decodes("ac3")) "ac3" else "aac"

    fun ffmpegPresent(): Boolean = runCatching {
        Class.forName("androidx.media3.decoder.ffmpeg.FfmpegLibrary")
        true
    }.getOrDefault(false)

    private fun mime(codec: String): String? = when (codec.lowercase()) {
        "h264", "avc" -> "video/avc"
        "hevc", "h265" -> "video/hevc"
        "mpeg2video", "mpeg2" -> "video/mpeg2"
        "mpeg4" -> "video/mp4v-es"
        "vp8" -> "video/x-vnd.on2.vp8"
        "vp9" -> "video/x-vnd.on2.vp9"
        "av1" -> "video/av01"
        "vc1" -> "video/vc1"
        "aac" -> "audio/mp4a-latm"
        "mp3" -> "audio/mpeg"
        "opus" -> "audio/opus"
        "vorbis" -> "audio/vorbis"
        "flac" -> "audio/flac"
        "ac3" -> "audio/ac3"
        "eac3" -> "audio/eac3"
        "dts" -> "audio/vnd.dts"
        "truehd" -> "audio/true-hd"
        else -> null
    }
}
