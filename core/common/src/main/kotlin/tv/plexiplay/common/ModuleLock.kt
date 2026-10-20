package tv.plexiplay.common

/**
 * Registre des modules figés. Le contrôle réel est tools/check_locks.py,
 * exécuté avant chaque compilation. Retirer l'id de tools/locks.json
 * pour rouvrir un module. Ne pas modifier un module tant qu'il est listé.
 */
object ModuleLock {
    const val AUTH = "auth"
    const val LIBRARY = "library"
    const val PREROLL = "preroll"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val SHELL = "shell"
    const val PLAYBACK = "playback"
    const val PLAYERS = "players"

    val locked: Set<String> = setOf(PLAYBACK, PLAYERS)

    fun ensure(id: String) {
        check(id !in locked) { "Module '$id' verrouillé. Retirez-le de tools/locks.json avant de le modifier." }
    }
}
