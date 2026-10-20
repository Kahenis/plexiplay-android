package tv.plexiplay.plex

/** Réponse de /video/:/transcode/universal/decision : uniquement un flux HTTP du serveur. */
object DecisionReply {
    fun transcodeKey(raw: String): String? {
        val pattern = Regex("""key(?:"\s*:\s*"|\s*=\s*")(/video/:/transcode/[^"\\<]+)""")
        return pattern.findAll(raw)
            .map { it.groupValues[1].replace("\\/", "/") }
            .firstOrNull { key -> !key.startsWith("file:") && "file://" !in key }
    }
}
