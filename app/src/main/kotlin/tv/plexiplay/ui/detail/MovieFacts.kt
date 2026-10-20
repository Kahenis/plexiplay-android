package tv.plexiplay.ui.detail

import tv.plexiplay.plex.model.MediaInfo
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.plex.model.StreamInfo
import kotlin.math.roundToInt

object MovieFacts {
    private val names = mapOf(
        "français" to "fr", "french" to "fr", "fra" to "fr", "fre" to "fr",
        "anglais" to "en", "english" to "en", "eng" to "en",
        "espagnol" to "es", "spanish" to "es", "español" to "es", "spa" to "es",
        "allemand" to "de", "german" to "de", "deutsch" to "de", "ger" to "de", "deu" to "de",
        "italien" to "it", "italian" to "it", "italiano" to "it", "ita" to "it",
        "portugais" to "pt", "portuguese" to "pt", "por" to "pt",
        "japonais" to "ja", "japanese" to "ja", "jpn" to "ja",
        "russe" to "ru", "russian" to "ru",
        "chinois" to "zh", "chinese" to "zh",
        "coréen" to "ko", "coreen" to "ko", "korean" to "ko",
        "néerlandais" to "nl", "neerlandais" to "nl", "dutch" to "nl",
        "polonais" to "pl", "polish" to "pl",
        "arabe" to "ar", "arabic" to "ar",
    )

    private val priority = listOf("fr", "en", "es", "de", "it", "pt")

    fun code(stream: StreamInfo): String {
        val tag = stream.languageTag.ifBlank { stream.languageCode }.ifBlank { stream.language }.trim()
        if (tag.length in 2..3 && tag.all { it.isLetter() } && tag.lowercase() in names.values) return tag.take(2).lowercase()
        val mapped = names[tag.lowercase()]
        if (mapped != null) return mapped
        if (tag.length >= 2 && tag[0].isLetter() && tag[1].isLetter() && tag.take(2).all { it.isLetter() }) {
            val two = tag.take(2).lowercase()
            if (two in names.values || two in priority) return two
        }
        return names[tag.lowercase().substringBefore('-')] ?: tag.take(2).lowercase().filter { it.isLetter() }
    }

    fun audios(movie: Metadata): List<StreamInfo> = streams(movie, 2)

    fun subtitles(movie: Metadata): List<StreamInfo> = streams(movie, 3)

    fun languageLabels(movie: Metadata, limit: Int = 4): List<String> {
        val codes = audios(movie).map { code(it) }.filter { it.length == 2 }.distinct()
        return order(codes).take(limit).map { it.uppercase() }
    }

    fun defaultAudio(movie: Metadata, preferred: String = ""): String {
        val codes = audioChoicesLabeled(movie).map { it.first }
        val original = audios(movie).map { code(it) }.firstOrNull { it in codes }
        return when {
            preferred.isNotBlank() && preferred != "original" && preferred in codes -> preferred
            preferred == "original" && original != null -> original
            "fr" in codes -> "fr"
            "en" in codes -> "en"
            else -> codes.firstOrNull().orEmpty()
        }
    }

    /** Sous-titre du même type (langue, forcé) dans un autre média, ou « off ». */
    fun subtitleMatch(movie: Metadata, language: String, forced: Boolean): String {
        if (language.isBlank()) return "off"
        val options = subtitleOptions(movie).filter { it.id != "off" && it.language == language }
        return (options.firstOrNull { it.forced == forced } ?: options.firstOrNull { !it.forced })?.id ?: "off"
    }

    fun defaultSubtitle(movie: Metadata, preferred: String): String {
        if (preferred.isBlank() || preferred == "off") return "off"
        val options = subtitleOptions(movie).filter { it.id != "off" && it.language == preferred }
        return (options.firstOrNull { !it.forced } ?: options.firstOrNull())?.id ?: "off"
    }

    fun languageName(code: String): String = when (code) {
        "fr" -> "Français"
        "en" -> "Anglais"
        "es" -> "Espagnol"
        "de" -> "Allemand"
        "it" -> "Italien"
        "pt" -> "Portugais"
        "ja" -> "Japonais"
        "ru" -> "Russe"
        "zh" -> "Chinois"
        "ko" -> "Coréen"
        "nl" -> "Néerlandais"
        "pl" -> "Polonais"
        "ar" -> "Arabe"
        else -> code.uppercase()
    }

    fun nativeResolution(movie: Metadata): String {
        val found = movie.Media.map { it.videoResolution }.filter { it.isNotBlank() }
        return found.maxByOrNull { rank(it) } ?: "1080"
    }

    fun resolutionChoices(movie: Metadata): List<Pair<String, String>> {
        val native = nativeResolution(movie)
        val lower = listOf(2160, 1080, 720, 480)
            .filter { it < rank(native) }
            .take(2)
            .map { it.toString() to prettyResolution(it.toString()) }
        return listOf("native" to prettyResolution(native)) + lower
    }

    fun audioChoicesLabeled(movie: Metadata): List<Pair<String, String>> {
        val codes = audios(movie).map { code(it) }.filter { it.length == 2 }.distinct()
        val front = listOfNotNull(codes.find { it == "fr" }, codes.find { it == "en" })
        return (front + codes.filter { it != "fr" && it != "en" }).map { it to languageName(it) }
    }

    data class SubtitleOption(
        val id: String,
        val label: String,
        val language: String,
        val forced: Boolean,
        val codec: String,
        val external: Boolean,
        val key: String,
    )

    fun subtitleOptions(movie: Metadata): List<SubtitleOption> {
        val options = subtitles(movie).map { stream ->
            val language = code(stream)
            val name = languageName(language).ifBlank { stream.displayTitle.ifBlank { "Sous-titre" } }
            val kind = if (stream.forced) "forcé" else "normal"
            SubtitleOption(
                id = stream.id.toString(),
                label = "$name · $kind",
                language = language,
                forced = stream.forced,
                codec = stream.codec,
                external = external(stream),
                key = stream.key,
            )
        }
        val counts = options.groupingBy { it.label }.eachCount()
        val distinct = options.map { option ->
            if ((counts[option.label] ?: 0) > 1 && option.codec.isNotBlank()) {
                option.copy(label = option.label + " · " + option.codec.uppercase())
            } else {
                option
            }
        }
        return listOf(SubtitleOption("off", "Sans", "", false, "", false, "")) + distinct
    }

    fun external(stream: StreamInfo): Boolean {
        val title = stream.extendedDisplayTitle.ifBlank { stream.displayTitle }
        if (title.contains("external", true)) return true
        val text = stream.codec.lowercase() in setOf("srt", "ass", "ssa", "vtt", "subrip")
        return text && stream.key.startsWith("/library/streams/") && stream.index < 0
    }

    fun targetHeight(resolution: String): Int? {
        if (resolution.isBlank() || resolution == "native") return null
        val height = rank(resolution)
        return if (height > 0) height else null
    }

    fun resolutions(movie: Metadata): List<String> {
        val found = movie.Media.map { it.videoResolution }.filter { it.isNotBlank() }.distinct()
        return found.sortedByDescending { rank(it) }.ifEmpty { listOf("1080") }
    }

    fun prettyResolution(raw: String): String = when (raw.lowercase()) {
        "4k", "2160", "uhd" -> "4K"
        "1080" -> "1080p"
        "720" -> "720p"
        "480", "576", "sd" -> "SD"
        else -> raw.uppercase()
    }

    fun duration(ms: Long): String {
        if (ms <= 0L) return ""
        val minutes = (ms / 60000L).toInt()
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours > 0) "$hours h $rest min" else "$rest min"
    }

    fun genres(movie: Metadata): String = movie.Genre.map { it.tag }.filter { it.isNotBlank() }.joinToString(" · ")

    fun imdb(movie: Metadata): String {
        val fromList = movie.Rating.firstOrNull { it.image.contains("imdb", true) }?.value ?: 0f
        val value = if (fromList > 0f) fromList else if (movie.ratingImage.contains("imdb", true) || movie.rating > 0f) movie.rating else 0f
        return if (value > 0f) "IMDb ${formatScore(value)}" else "IMDb —"
    }

    fun audience(movie: Metadata): String {
        val value = if (movie.audienceRating > 0f) movie.audienceRating else 0f
        return if (value > 0f) "Utilisateurs ${formatScore(value)}" else "Utilisateurs —"
    }

    fun stars(movie: Metadata): String {
        val filled = (movie.userRating / 2f).roundToInt().coerceIn(0, 5)
        return "★".repeat(filled) + "☆".repeat(5 - filled)
    }

    fun mediaFor(movie: Metadata, resolution: String): MediaInfo? {
        if (movie.Media.isEmpty()) return null
        return movie.Media.minByOrNull { kotlin.math.abs(rank(it.videoResolution) - rank(resolution)).let { d -> if (d == 0) 0 else d + 1 } }
            ?: movie.Media.first()
    }

    fun mediaIndex(movie: Metadata, resolution: String): Int {
        val media = mediaFor(movie, resolution) ?: return 0
        val index = movie.Media.indexOf(media)
        return if (index < 0) 0 else index
    }

    fun downscale(movie: Metadata, resolution: String): Boolean {
        val file = mediaFor(movie, resolution)?.videoResolution.orEmpty()
        val wanted = rank(resolution)
        val have = rank(file)
        return wanted in 1 until have
    }

    fun audioStreamId(movie: Metadata, language: String): Int? = chosenAudio(movie, language)?.id

    fun audioCodec(movie: Metadata, language: String): String? = chosenAudio(movie, language)?.codec

    private fun chosenAudio(movie: Metadata, language: String): StreamInfo? {
        val streams = (bestMedia(movie)?.Part ?: emptyList()).flatMap { it.Stream }.filter { it.streamType == 2 }
        val same = streams.filter { code(it) == language }
        if (same.isEmpty() && language == "fr") frenchStream(movie)?.let { return it }
        return same.firstOrNull { it.selected } ?: same.firstOrNull() ?: streams.firstOrNull()
    }

    /**
     * Piste audio française du film : d'abord celle dont la langue est « fr » (la sélectionnée du serveur si
     * plusieurs, sinon celle qui a le plus de canaux) ; à défaut, une piste dont le titre dit français
     * (étiquette de langue absente ou fausse dans le fichier).
     */
    fun frenchStream(movie: Metadata): StreamInfo? {
        val streams = (bestMedia(movie)?.Part ?: emptyList()).flatMap { it.Stream }.filter { it.streamType == 2 }
        fun pick(list: List<StreamInfo>) = list.firstOrNull { it.selected } ?: list.maxByOrNull { it.channels }
        val tagged = streams.filter { code(it) == "fr" }
        if (tagged.isNotEmpty()) return pick(tagged)
        val titled = streams.filter {
            val title = (it.displayTitle + " " + it.extendedDisplayTitle).lowercase()
            title.contains("français") || title.contains("francais") || title.contains("french") || title.contains("(fr")
        }
        return pick(titled)
    }

    /** Pistes audio du fichier lu, dans l'ordre du fichier (liste du bouton FR en appui long). */
    fun audioStreams(movie: Metadata): List<StreamInfo> =
        (bestMedia(movie)?.Part?.firstOrNull()?.Stream ?: emptyList()).filter { it.streamType == 2 }

    fun audioLabel(stream: StreamInfo): String {
        val lang = code(stream).takeIf { it.length == 2 }?.let { languageName(it) }
            ?: stream.displayTitle.substringBefore(" (").ifBlank { "Piste ${stream.index}" }
        val codec = when (stream.codec.lowercase()) {
            "dca", "dts" -> "DTS"
            "dtshd", "dts-hd" -> "DTS-HD"
            "eac3" -> "E-AC3"
            "truehd" -> "TrueHD"
            "" -> ""
            else -> stream.codec.uppercase()
        }
        val channels = when (stream.channels) {
            0 -> ""
            1 -> "mono"
            2 -> "2.0"
            6 -> "5.1"
            8 -> "7.1"
            else -> "${stream.channels} canaux"
        }
        return listOf(lang, listOf(codec, channels).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(" · ")
    }

    fun partId(movie: Metadata): Long = bestMedia(movie)?.Part?.firstOrNull()?.id ?: 0L

    fun subtitleOption(movie: Metadata, id: String): SubtitleOption? =
        subtitleOptions(movie).firstOrNull { it.id == id && it.id != "off" }

    fun bestMedia(movie: Metadata): MediaInfo? =
        movie.Media.maxByOrNull { rank(it.videoResolution) }

    private fun streams(movie: Metadata, type: Int): List<StreamInfo> =
        movie.Media.flatMap { media -> media.Part.flatMap { it.Stream } }.filter { it.streamType == type }

    private fun order(codes: List<String>): List<String> =
        codes.sortedWith(compareBy({ priority.indexOf(it).let { index -> if (index < 0) 50 else index } }, { it }))

    private fun rank(raw: String): Int = when (raw.lowercase()) {
        "4k", "2160", "uhd" -> 2160
        "1080" -> 1080
        "720" -> 720
        "480", "576", "sd" -> 480
        else -> raw.filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    private fun formatScore(value: Float): String {
        val rounded = (value * 10).roundToInt() / 10f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }
}
