package tv.plexiplay.data

/** Choix retenu pour une série : audio, langue des sous-titres (vide = sans) et forcés ou non. */
data class SeriesChoice(val audio: String, val subtitleLanguage: String, val subtitleForced: Boolean)
