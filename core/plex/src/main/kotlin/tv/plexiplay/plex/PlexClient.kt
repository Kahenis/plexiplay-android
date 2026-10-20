package tv.plexiplay.plex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import tv.plexiplay.plex.model.DirectoryBox
import tv.plexiplay.plex.model.LibrarySection
import tv.plexiplay.plex.model.Metadata
import tv.plexiplay.plex.model.MetadataBox
import tv.plexiplay.plex.model.PlexResource
import tv.plexiplay.plex.model.RoleItem
import tv.plexiplay.plex.model.ServerEndpoint
import tv.plexiplay.plex.model.ServerSetting
import tv.plexiplay.plex.model.SettingBox
import tv.plexiplay.plex.model.Wrapped
import java.io.IOException
import java.util.concurrent.TimeUnit

class PlexClient(
    private val clientId: String,
    private val deviceName: String = "Plexiplay Android",
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true },
) {
    /** Octets lus pendant un téléchargement. total vaut -1 si le serveur ne le dit pas. */
    @Volatile
    var onBytes: ((read: Long, total: Long) -> Unit)? = null

    fun resources(token: String): List<PlexResource> {
        val url = "https://plex.tv/api/v2/resources".toHttpUrl().newBuilder()
            .addQueryParameter("includeHttps", "1")
            .addQueryParameter("includeRelay", "1")
            .build()
        val raw = get(url.toString(), token)
        val element = json.parseToJsonElement(raw)
        return if (element is JsonArray) {
            json.decodeFromJsonElement<List<PlexResource>>(element)
        } else {
            emptyList()
        }
    }

    /** Invitations reçues (demande d'ami, partage de bibliothèques). Vide s'il n'y en a pas. */
    fun invites(token: String): List<PlexInvite> {
        val request = Request.Builder().url("https://plex.tv/api/invites/requests").get()
            .let { PlexHeaders.apply(it, clientId, token, deviceName) }
            .header("Accept", "application/xml")
            .build()
        val raw = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Plex ${response.code}")
            response.body?.string().orEmpty()
        }
        fun unescape(v: String) = v.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&amp;", "&")
        fun attrs(text: String) = Regex("""([\w:]+)="([^"]*)"""").findAll(text).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        return Regex("""<Invite\b([^>]*?)(?:/>|>(.*?)</Invite>)""", RegexOption.DOT_MATCHES_ALL).findAll(raw).mapNotNull { match ->
            val a = attrs(match.groupValues[1])
            val id = a["id"] ?: return@mapNotNull null
            val serverAttrs = Regex("""<Server\b([^>]*)""").find(match.groupValues[2])?.let { attrs(it.groupValues[1]) }
            PlexInvite(
                id = id,
                name = a["friendlyName"]?.ifBlank { null } ?: a["username"]?.ifBlank { null } ?: a["email"].orEmpty(),
                email = a["email"].orEmpty(),
                friend = a["friend"] == "1",
                home = a["home"] == "1",
                server = a["server"] == "1",
                serverName = serverAttrs?.get("name").orEmpty(),
                libraries = serverAttrs?.get("numLibraries")?.toIntOrNull() ?: 0,
            )
        }.toList()
    }

    fun acceptInvite(token: String, invite: PlexInvite) {
        val url = "https://plex.tv/api/invites/requests/${invite.id}".toHttpUrl().newBuilder()
            .addQueryParameter("friend", if (invite.friend) "1" else "0")
            .addQueryParameter("home", if (invite.home) "1" else "0")
            .addQueryParameter("server", if (invite.server) "1" else "0")
            .build()
        val request = Request.Builder().url(url).put(ByteArray(0).toRequestBody(null))
            .let { PlexHeaders.apply(it, clientId, token, deviceName) }
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Plex ${response.code}")
        }
    }

    /**
     * Tous les serveurs du compte : les siens d'abord, puis ceux partagés par des amis.
     * Connexion locale non relais d'abord.
     */
    fun chooseServers(resources: List<PlexResource>): List<ServerEndpoint> {
        val servers = resources.filter { it.isServer() && it.accessToken.isNotBlank() }
        val pool = servers.filter { it.owned } + servers.filter { !it.owned }
        return pool.mapNotNull { resource ->
            val ordered = resource.connections
                .filter { it.uri.isNotBlank() }
                .sortedWith(compareBy<tv.plexiplay.plex.model.PlexConnection> { it.relay }.thenBy { !it.local })
            val connection = ordered.firstOrNull() ?: return@mapNotNull null
            ServerEndpoint(
                alternates = ordered.drop(1).map { it.uri.trimEnd('/') }.distinct(),
                name = resource.name.ifBlank { "Serveur" },
                clientIdentifier = resource.clientIdentifier,
                accessToken = resource.accessToken,
                uri = connection.uri.trimEnd('/'),
                owned = resource.owned,
            )
        }
    }

    /**
     * Essaie les adresses du serveur l'une après l'autre (locale, distante, relais) et renvoie
     * le serveur avec la première qui répond, ou null si aucune ne répond.
     */
    fun reach(server: ServerEndpoint): ServerEndpoint? {
        val quick = http.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val all = (listOf(server.uri) + server.alternates).distinct()
        for (candidate in all) {
            val ok = runCatching {
                val request = PlexHeaders.apply(
                    Request.Builder().url("$candidate/identity").get(),
                    clientId, server.accessToken, deviceName,
                ).build()
                quick.newCall(request).execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (ok) {
                return server.copy(uri = candidate, alternates = all.filter { it != candidate })
            }
        }
        return null
    }

    fun sections(server: ServerEndpoint): List<LibrarySection> {
        val raw = get("${server.uri}/library/sections", server.accessToken)
        return json.decodeFromString(Wrapped.serializer(DirectoryBox.serializer()), raw)
            .MediaContainer?.Directory.orEmpty()
            .filter { it.type == "movie" || it.type == "show" }
    }

    fun onDeck(server: ServerEndpoint, sectionKey: String): List<Metadata> =
        metadataList(get("${server.uri}/library/sections/$sectionKey/onDeck", server.accessToken))

    fun recentlyAdded(server: ServerEndpoint, sectionKey: String, size: Int = 24): List<Metadata> =
        metadataList(get(sized("${server.uri}/library/sections/$sectionKey/recentlyAdded", size), server.accessToken))

    fun mostWatched(server: ServerEndpoint, sectionKey: String): List<Metadata> =
        metadataList(get(sized("${server.uri}/library/sections/$sectionKey/all?sort=viewCount:desc"), server.accessToken))

    fun watched(server: ServerEndpoint, sectionKey: String): List<Metadata> =
        metadataList(get(sized("${server.uri}/library/sections/$sectionKey/all?unwatched=0&sort=lastViewedAt:desc"), server.accessToken))

    fun collections(server: ServerEndpoint, sectionKey: String): List<Metadata> =
        metadataList(get("${server.uri}/library/sections/$sectionKey/collections", server.accessToken))

    /** Prochain épisode à voir d'une série (Plex : onDeck de la série). */
    fun showOnDeck(server: ServerEndpoint, showKey: String): Metadata? = runCatching {
        metadataList(get("${server.uri}/library/metadata/$showKey/onDeck", server.accessToken)).firstOrNull()
    }.getOrNull()

    /** Tous les épisodes d'une série, dans l'ordre saison / épisode. */
    fun allEpisodes(server: ServerEndpoint, showKey: String): List<Metadata> =
        metadataList(get("${server.uri}/library/metadata/$showKey/allLeaves", server.accessToken))

    /** Image large (16/9) pour une miniature d'épisode ou un fond de série. */
    fun wideSources(server: ServerEndpoint, path: String?, width: Int = 320, height: Int = 180): List<String> {
        if (path.isNullOrBlank()) return emptyList()
        val token = java.net.URLEncoder.encode(server.accessToken, Charsets.UTF_8.name())
        val encodedPath = java.net.URLEncoder.encode(path, Charsets.UTF_8.name())
        val transcode = "${server.uri}/photo/:/transcode?width=$width&height=$height&minSize=1&upscale=1&url=$encodedPath&X-Plex-Token=$token"
        val raw = if (path.startsWith("http")) path else server.uri + path + "?X-Plex-Token=" + token
        return listOf(transcode, raw)
    }

    private fun sized(url: String, size: Int = 24): String =
        url + (if (url.contains("?")) "&" else "?") + "X-Plex-Container-Start=0&X-Plex-Container-Size=$size"

    fun items(server: ServerEndpoint, sectionKey: String): List<Metadata> {
        val raw = get("${server.uri}/library/sections/$sectionKey/all", server.accessToken)
        return metadataList(raw)
    }

    fun children(server: ServerEndpoint, ratingKey: String): List<Metadata> {
        val raw = get("${server.uri}/library/metadata/$ratingKey/children", server.accessToken)
        return metadataList(raw)
    }

    fun metadata(server: ServerEndpoint, ratingKey: String): Metadata? {
        val raw = get("${server.uri}/library/metadata/$ratingKey", server.accessToken, language = "fr")
        return metadataList(raw).firstOrNull()
    }

    fun extras(server: ServerEndpoint, ratingKey: String): List<Metadata> {
        val raw = get("${server.uri}/library/metadata/$ratingKey/extras", server.accessToken)
        return metadataList(raw)
    }

    fun preference(server: ServerEndpoint, id: String): String? = runCatching {
        val raw = get("${server.uri}/:/prefs", server.accessToken)
        val fromJson = runCatching {
            json.decodeFromString(Wrapped.serializer(SettingBox.serializer()), raw)
                .MediaContainer?.Setting.orEmpty()
                .firstOrNull { it.id.equals(id, true) }?.value
        }.getOrNull()
        if (!fromJson.isNullOrBlank()) return@runCatching fromJson
        PrefValue.extract(raw, id)
    }.getOrNull()

    /**
     * Même demande que le client officiel quand « lire 1 avant le film » est actif.
     * extrasPrefixCount est le nombre de bandes-annonces. Le bonus du serveur est ajouté
     * par le serveur dès que ce paramètre est présent, même à 0.
     * On ne l'envoie pas si le film est déjà commencé.
     */
    fun cinemaQueue(server: ServerEndpoint, ratingKey: String, trailerCount: Int): List<Metadata> {
        val uri = "server://${server.clientIdentifier}/com.plexapp.plugins.library/library/metadata/$ratingKey"
        val url = "${server.uri}/playQueues".toHttpUrl().newBuilder()
            .addQueryParameter("type", "video")
            .addQueryParameter("uri", uri)
            .addQueryParameter("continuous", "0")
            .addQueryParameter("shuffle", "0")
            .addQueryParameter("repeat", "0")
            .addQueryParameter("includeChapters", "1")
            .addQueryParameter("includeRelated", "0")
            .addQueryParameter("extrasPrefixCount", trailerCount.coerceAtLeast(0).toString())
            .build()
        return metadataList(post(url.toString(), server.accessToken))
    }

    fun timeline(
        server: ServerEndpoint,
        ratingKey: String,
        state: String,
        timeMs: Long,
        durationMs: Long,
        sessionId: String = "",
    ) {
        val url = "${server.uri}/:/timeline".toHttpUrl().newBuilder()
            .addQueryParameter("ratingKey", ratingKey)
            .addQueryParameter("key", "/library/metadata/$ratingKey")
            .addQueryParameter("state", state)
            .addQueryParameter("hasMDE", "1")
            .addQueryParameter("time", timeMs.coerceAtLeast(0L).toString())
            .addQueryParameter("duration", durationMs.coerceAtLeast(0L).toString())
            .build()
        val extra = if (sessionId.isBlank()) emptyMap() else mapOf("X-Plex-Session-Identifier" to sessionId)
        runCatching { get(url.toString(), server.accessToken, extra = extra) }
    }

    fun setWatched(server: ServerEndpoint, ratingKey: String, watched: Boolean) {
        val action = if (watched) "scrobble" else "unscrobble"
        val url = "${server.uri}/:/$action?identifier=com.plexapp.plugins.library&key=$ratingKey"
        get(url, server.accessToken)
    }

    /** Arrête une session de transcodage : le nouveau flux démarre alors sur une session neuve, avec les pistes enregistrées. */
    fun stopTranscode(server: ServerEndpoint, session: String) {
        if (session.isBlank()) return
        val url = "${server.uri}/video/:/transcode/universal/stop".toHttpUrl().newBuilder()
            .addQueryParameter("session", session)
            .build()
        runCatching { get(url.toString(), server.accessToken) }
    }

    /** Enregistre sur le serveur la piste audio choisie (comme le fait le client officiel). */
    fun setAudioStream(server: ServerEndpoint, partId: Long, streamId: Int) {
        if (partId <= 0L || streamId <= 0) return
        val url = "${server.uri}/library/parts/$partId".toHttpUrl().newBuilder()
            .addQueryParameter("audioStreamID", streamId.toString())
            .addQueryParameter("allParts", "1")
            .build()
        val request = Request.Builder().url(url).put(ByteArray(0).toRequestBody(null))
            .let { PlexHeaders.apply(it, clientId, server.accessToken, deviceName) }
            .build()
        http.newCall(request).execute().use { }
    }

    /**
     * Enregistre sur le serveur la piste de sous-titres choisie pour ce fichier (0 = aucune), comme le font les apps Plex :
     * sans cela, la piste « sélectionnée » du compte est ajoutée au flux converti malgré la demande « aucun ».
     * Renvoie vrai si le serveur a accepté.
     */
    fun setSubtitleStream(server: ServerEndpoint, partId: Long, streamId: Int): Boolean {
        if (partId <= 0L || streamId < 0) return false
        val url = "${server.uri}/library/parts/$partId".toHttpUrl().newBuilder()
            .addQueryParameter("subtitleStreamID", streamId.toString())
            .addQueryParameter("allParts", "1")
            .build()
        val request = Request.Builder().url(url).put(ByteArray(0).toRequestBody(null))
            .let { PlexHeaders.apply(it, clientId, server.accessToken, deviceName) }
            .build()
        return http.newCall(request).execute().use { it.isSuccessful }
    }

    /**
     * Fiche d'un acteur : biographie et œuvres principales. Le service « Discover » de Plex (compte plex.tv) est
     * interrogé d'abord, au mieux ; sinon les œuvres sont celles des bibliothèques du serveur où il joue.
     */
    fun person(server: ServerEndpoint, role: RoleItem, accountToken: String): PersonInfo {
        var bio = ""
        var works = emptyList<PersonWork>()
        var inLibrary = false
        val key = role.tagKey.trim()
        if (key.isNotBlank() && accountToken.isNotBlank()) {
            val quick = http.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
            fun cloud(path: String): List<JsonObject> {
                val request = Request.Builder().url("https://discover.provider.plex.tv/library/people/" + java.net.URLEncoder.encode(key, "UTF-8") + path).get()
                    .let { PlexHeaders.apply(it, clientId, accountToken, deviceName) }
                    // Biographie et titres en français quand Plex les a (sinon sa langue par défaut).
                    .header("X-Plex-Language", "fr")
                    .header("Accept-Language", "fr-FR,fr;q=0.9")
                    .build()
                val raw = quick.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Plex ${response.code}")
                    response.body?.string().orEmpty()
                }
                val container = json.parseToJsonElement(raw).jsonObject["MediaContainer"]?.jsonObject
                return container?.get("Metadata")?.jsonArray?.map { it.jsonObject }.orEmpty()
            }
            bio = runCatching { cloud("").firstOrNull()?.get("summary")?.jsonPrimitive?.contentOrNull.orEmpty() }.getOrDefault("")
            works = runCatching {
                cloud("/media").filter { entry ->
                    val type = entry["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    type == "movie" || type == "show"
                }.mapNotNull { entry ->
                    val title = entry["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val thumb = entry["thumb"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    PersonWork(
                        title,
                        entry["year"]?.jsonPrimitive?.intOrNull ?: 0,
                        if (thumb.startsWith("http")) listOf(thumb) else emptyList(),
                    )
                }.take(12)
            }.getOrDefault(emptyList())
        }
        if (works.isEmpty() && role.id > 0L) {
            works = runCatching {
                sections(server).filter { !it.personal }.flatMap { section ->
                    runCatching {
                        metadataList(get(sized("${server.uri}/library/sections/${section.key}/all?actor=${role.id}", 12), server.accessToken))
                    }.getOrDefault(emptyList())
                }.sortedByDescending { it.audienceRating }.take(12).map { item ->
                    PersonWork(item.title, item.year, thumbSources(server, item.thumb))
                }
            }.getOrDefault(emptyList())
            inLibrary = works.isNotEmpty()
        }
        return PersonInfo(bio.trim(), works, inLibrary)
    }

    fun fileUrl(server: ServerEndpoint, path: String?): String? {
        if (path.isNullOrBlank()) return null
        val base = if (path.startsWith("http")) path else server.uri.trimEnd('/') + path
        val join = if (base.contains('?')) "&" else "?"
        return base + join + "X-Plex-Token=" + java.net.URLEncoder.encode(server.accessToken, Charsets.UTF_8.name())
    }

    /**
     * D'abord une affiche déjà réduite par le serveur, puis l'originale si le
     * transcodeur refuse (il sature vite quand on lui en demande trop).
     */
    fun thumbSources(server: ServerEndpoint, path: String?): List<String> {
        if (path.isNullOrBlank()) return emptyList()
        if (path.startsWith("http")) {
            return listOf(
                path.toHttpUrl().newBuilder()
                    .addQueryParameter("X-Plex-Token", server.accessToken)
                    .build()
                    .toString(),
            )
        }
        val token = java.net.URLEncoder.encode(server.accessToken, Charsets.UTF_8.name())
        val encodedPath = java.net.URLEncoder.encode(path, Charsets.UTF_8.name())
        val transcode = "${server.uri}/photo/:/transcode?width=240&height=360&minSize=1&upscale=1&url=$encodedPath&X-Plex-Token=$token"
        val raw = server.uri + path + "?X-Plex-Token=" + token
        return listOf(transcode, raw)
    }

    private fun metadataList(raw: String): List<Metadata> =
        json.decodeFromString(Wrapped.serializer(MetadataBox.serializer()), raw)
            .MediaContainer?.Metadata.orEmpty()

    private fun post(url: String, token: String): String = get(url, token, methodPost = true)

    private fun get(url: String, token: String, language: String? = null, extra: Map<String, String> = emptyMap(), methodPost: Boolean = false): String {
        val builder = Request.Builder().url(url)
        val withMethod = if (methodPost) builder.post(ByteArray(0).toRequestBody(null)) else builder.get()
        val request = withMethod
            .let { PlexHeaders.apply(it, clientId, token, deviceName) }
            .apply {
                if (!language.isNullOrBlank()) header("X-Plex-Language", language)
                extra.forEach { (name, value) -> header(name, value) }
            }
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body ?: throw IOException("Plex ${response.code}")
            val total = body.contentLength()
            val source = body.source()
            val buffer = Buffer()
            var read = 0L
            while (true) {
                val count = source.read(buffer, 8192)
                if (count == -1L) break
                read += count
                runCatching { onBytes?.invoke(read, total) }
            }
            val raw = buffer.readUtf8()
            if (!response.isSuccessful) throw IOException("Plex ${response.code}")
            return raw
        }
    }
}

data class PlexInvite(
    val id: String,
    val name: String,
    val email: String,
    val friend: Boolean,
    val home: Boolean,
    val server: Boolean,
    val serverName: String,
    val libraries: Int,
)

data class PersonWork(val title: String, val year: Int, val images: List<String>)

data class PersonInfo(val bio: String = "", val works: List<PersonWork> = emptyList(), val worksInLibrary: Boolean = false)
