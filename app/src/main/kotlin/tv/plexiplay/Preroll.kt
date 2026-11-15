package tv.plexiplay

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import tv.plexiplay.data.SessionStore

/** Un fichier du dossier de prérolls : nom, et « signature » (date et taille affichées par le serveur) pour reconnaître un fichier remplacé. */
internal data class RemotePreroll(val name: String, val signature: String) {
    val key: String get() = "$name|$signature"
    val url: String get() = PrerollFolder.BASE + Uri.encode(name)
}

/**
 * Prérolls « séquentiels » : fichiers du dossier guilard.com/preroll, lus un par film dans l'ordre alphabétique.
 * La position retenue est le NOM du dernier fichier lu (pas un numéro) : ajouter ou retirer des fichiers ne change rien d'autre.
 */
internal object PrerollFolder {
    const val BASE = "https://guilard.com/preroll/"

    /** Mémoire de cette TV (position et fichiers défectueux), donnée au démarrage de l'appli. */
    @Volatile
    var store: SessionStore? = null

    enum class Status { OK, MISSING, UNREACHABLE }

    /** Lit la page du dossier. MISSING : le serveur répond « introuvable » (404, 410) ou « interdit » (403) ; UNREACHABLE : réseau ou serveur en panne. */
    fun fetch(): Pair<Status, List<RemotePreroll>> = runCatching {
        val conn = (URL(BASE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
        }
        when (val code = conn.responseCode) {
            200 -> Status.OK to parse(conn.inputStream.bufferedReader().use { it.readText() })
            404, 410, 403 -> Status.MISSING to emptyList()
            else -> {
                if (code in 200..299) Status.OK to parse(conn.inputStream.bufferedReader().use { it.readText() }) else Status.UNREACHABLE to emptyList()
            }
        }
    }.getOrDefault(Status.UNREACHABLE to emptyList())

    /** Appelé quand le dossier est reconnu absent (le réglage est alors grisé) : l'appli rafraîchit ses réglages. */
    @Volatile
    var onLocked: (() -> Unit)? = null

    sealed interface Choice {
        data class Remote(val file: RemotePreroll) : Choice
        /** Pas de fichier utilisable : les prérolls du serveur Plex servent pour ce film. */
        data object ServerFallback : Choice
    }

    fun parse(html: String): List<RemotePreroll> {
        val lines = html.split('\n')
        val found = LinkedHashMap<String, RemotePreroll>()
        val pattern = Regex("""href="([^"?#]+\.(?:mp4|m4v|mkv|mov|webm))"""", RegexOption.IGNORE_CASE)
        for (line in lines) {
            for (match in pattern.findAll(line)) {
                val name = Uri.decode(match.groupValues[1].substringAfterLast('/'))
                if (name.isBlank() || name in found) continue
                // Ce qui suit le lien sur la même ligne (date, taille) sert de signature, sans les balises.
                val tail = line.substring(match.range.last + 1).replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
                found[name] = RemotePreroll(name, tail)
            }
        }
        return found.values.toList()
    }

    /** Fichier suivant : le premier, dans l'ordre alphabétique, après [last] ; retour au premier après le dernier. */
    fun next(files: List<RemotePreroll>, last: String, bad: Set<String>): RemotePreroll? {
        val usable = files.filter { it.key !in bad }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        if (usable.isEmpty()) return null
        if (last.isBlank()) return usable.first()
        return usable.firstOrNull { String.CASE_INSENSITIVE_ORDER.compare(it.name, last) > 0 } ?: usable.first()
    }

    /**
     * Choisit le préroll de ce film. Dossier absent : le réglage passe en « Aléatoire serveur » et se grise (SessionStore.prerollLocked) ;
     * dossier injoignable, vide ou sans fichier utilisable : prérolls du serveur pour ce film seulement, sans rien griser.
     */
    fun choose(): Choice {
        val s = store ?: return Choice.ServerFallback
        val (status, files) = fetch()
        if (status == Status.MISSING) {
            s.setPrerollLocked(true)
            onLocked?.invoke()
            return Choice.ServerFallback
        }
        if (status != Status.OK) return Choice.ServerFallback
        val file = next(files, s.prerollLast(), s.prerollBad()) ?: return Choice.ServerFallback
        return Choice.Remote(file)
    }
}
