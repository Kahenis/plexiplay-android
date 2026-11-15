package tv.plexiplay

/** Dernier chargement de l'accueil, pour la page Diagnostic : une ligne par bibliothèque. */
object HomeDebug {
    @Volatile
    var lines: List<Pair<String, String>> = emptyList()
        private set

    fun set(value: List<Pair<String, String>>) {
        lines = value
    }
}
