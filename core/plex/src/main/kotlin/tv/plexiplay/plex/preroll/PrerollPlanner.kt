package tv.plexiplay.plex.preroll

/**
 * Preroll serveur Plex (réglage CinemaTrailersPrerollID).
 * La valeur est un chemin de fichier sur le serveur, pas un identifiant de film.
 * Virgule : tous les bonus, dans l'ordre, avant le film.
 * Point-virgule : un seul bonus, tiré au hasard dans la liste.
 * Mélange : groupes séparés par des virgules, chaque groupe peut être un tirage.
 * Ce n'est pas un réglage client. Les bandes-annonces cinéma sont un autre flux, optionnel.
 */
enum class PrerollMode { SEQUENTIAL, RANDOM, STAGED }

enum class PrerollAudience { ALL, UNWATCHED }

data class PrerollStage(val candidates: List<String>)

data class PrerollSpec(
    val raw: String,
    val mode: PrerollMode,
    val stages: List<PrerollStage>,
)

enum class ClipKind { PREROLL, TRAILER, FEATURE }

data class PlaybackClip(
    val sourceId: String,
    val label: String,
    val kind: ClipKind,
)

object PrerollPlanner {
    const val PREF_ID = "CinemaTrailersPrerollID"
    const val PREF_AUDIENCE = "CinemaTrailersType"

    /** 0 : tous les films. 1 : seulement les non visionnés. Absent : on ne bloque pas le bonus. */
    fun audience(raw: String?): PrerollAudience {
        return when (raw?.trim()?.lowercase()) {
            "1", "true", "yes" -> PrerollAudience.UNWATCHED
            else -> PrerollAudience.ALL
        }
    }

    fun isLocalFile(source: String): Boolean {
        val value = source.trim()
        if (value.isEmpty() || value.all { it.isDigit() }) return false
        return value.startsWith("/") || value.startsWith("\\\\") || value.contains('\\') || value.contains('/') || value.contains(":/")
    }

    /** Un film déjà commencé et non terminé ne prend jamais de bonus. */
    fun allowed(audience: PrerollAudience, viewCount: Int, viewOffsetMs: Long): Boolean {
        if (viewOffsetMs > 0L) return false
        if (audience == PrerollAudience.UNWATCHED && viewCount > 0) return false
        return true
    }

    fun parse(raw: String?): PrerollSpec? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val hasComma = value.contains(',')
        val hasSemi = value.contains(';')
        val mode = when {
            hasComma && hasSemi -> PrerollMode.STAGED
            hasSemi -> PrerollMode.RANDOM
            else -> PrerollMode.SEQUENTIAL
        }
        val stages = when (mode) {
            PrerollMode.STAGED -> value.split(',').map { stage ->
                PrerollStage(stage.split(';').map { it.trim() }.filter { it.isNotEmpty() })
            }
            PrerollMode.RANDOM -> listOf(
                PrerollStage(value.split(';').map { it.trim() }.filter { it.isNotEmpty() }),
            )
            PrerollMode.SEQUENTIAL -> value.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { PrerollStage(listOf(it)) }
        }.filter { it.candidates.isNotEmpty() }
        if (stages.isEmpty()) return null
        return PrerollSpec(value, mode, stages)
    }

    fun select(
        spec: PrerollSpec,
        random: (Int) -> Int = { bound -> if (bound <= 1) 0 else kotlin.random.Random.nextInt(bound) },
    ): List<String> {
        return when (spec.mode) {
            PrerollMode.SEQUENTIAL -> spec.stages.map { it.candidates.first() }
            PrerollMode.RANDOM -> {
                val pool = spec.stages.first().candidates
                listOf(pool[random(pool.size)])
            }
            PrerollMode.STAGED -> spec.stages.map { stage ->
                stage.candidates[random(stage.candidates.size)]
            }
        }
    }

    /** Preroll serveur d'abord, bandes-annonces optionnelles ensuite, puis le film. */
    fun queue(
        preroll: List<String>,
        trailers: List<String>,
        featureId: String,
        trailerCount: Int,
    ): List<PlaybackClip> {
        val clips = mutableListOf<PlaybackClip>()
        preroll.forEachIndexed { index, source ->
            clips += PlaybackClip(source, "Bonus ${index + 1}", ClipKind.PREROLL)
        }
        trailers.take(trailerCount.coerceAtLeast(0)).forEachIndexed { index, source ->
            clips += PlaybackClip(source, "Bande-annonce ${index + 1}", ClipKind.TRAILER)
        }
        clips += PlaybackClip(featureId, "Film", ClipKind.FEATURE)
        return clips
    }
}
