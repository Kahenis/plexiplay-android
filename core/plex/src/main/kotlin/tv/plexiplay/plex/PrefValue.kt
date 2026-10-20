package tv.plexiplay.plex

/** Extrait un réglage Plex, que la réponse soit du JSON ou du XML. */
object PrefValue {
    fun extract(raw: String, id: String): String? {
        val escaped = Regex.escape(id)
        val xml = Regex("<Setting\\b([^>]*\\bid\\s*=\\s*\"$escaped\"[^>]*)>", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.get(1)
            ?.let { tag -> Regex("\\bvalue\\s*=\\s*\"([^\"]*)\"").find(tag)?.groupValues?.get(1) }
        if (!xml.isNullOrEmpty()) {
            val amp = "&" + "amp;"
            val quot = "&" + "quot;"
            return xml.replace(amp, "&").replace(quot, "\"")
        }
        val at = Regex("\"id\"\\s*:\\s*\"$escaped\"", RegexOption.IGNORE_CASE).find(raw) ?: return null
        val window = raw.substring(at.range.first, minOf(raw.length, at.range.last + 8000))
        val encoded = Regex("\"value\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(window)?.groupValues?.get(1)
            ?: return null
        return encoded.replace("\\/", "/").replace("\\\\", "\\").replace("\\\"", "\"")
    }
}
