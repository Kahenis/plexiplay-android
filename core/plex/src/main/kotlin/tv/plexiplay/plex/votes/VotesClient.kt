package tv.plexiplay.plex.votes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tv.plexiplay.plex.model.Metadata
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Clé partagée avec `votes.php` : filtre grossier contre les appels extérieurs à l'application, pas une vraie sécurité. */
private const val APP_KEY = "pp-votes-2a7f1c9e"
private const val ENDPOINT = "https://guilard.com/plexiplay-votes/votes.php"

/** Note communautaire d'un titre : la moyenne de tous les autres votants, et la mienne séparément. */
data class CommunityRating(val average: Float?, val count: Int, val mine: Int, val myReview: String?)

/** Un avis court laissé par un utilisateur Plexiplay (anonyme : seules sa note et son texte sont connus). */
data class Review(val stars: Int, val text: String)

/**
 * Moyenne des notes Plexiplay, commune à tous les utilisateurs de l'application quel que soit leur serveur :
 * un petit service sur guilard.com (base MySQL dédiée) associe une note 1-5 à l'identifiant externe du titre
 * (tmdb/imdb/tvdb, le même pour tout le monde) et à une empreinte du compte Plex du votant.
 */
object VotesClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Identifiant d'un titre, commun à tous les serveurs : préfère tmdb, puis imdb, puis tvdb ; vide si aucun. */
    fun communityKey(meta: Metadata): String {
        val guid = meta.Guid.map { it.tag }.firstOrNull { it.startsWith("tmdb://") }
            ?: meta.Guid.map { it.tag }.firstOrNull { it.startsWith("imdb://") }
            ?: meta.Guid.map { it.tag }.firstOrNull { it.startsWith("tvdb://") }
            ?: meta.guid.takeIf { it.isNotBlank() }
            ?: return ""
        return guid.take(190)
    }

    /** Empreinte à sens unique du compte Plex (identifiant plex.tv + clé fixe) : jamais le nom ni l'e-mail du votant. */
    fun voterHash(accountId: Long): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("plexiplay:$accountId:$APP_KEY".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    /** Moyenne des autres votants et ma note, pour ce titre. Null si injoignable (la fiche garde alors les notes locales). */
    fun fetch(guid: String, voter: String): CommunityRating? = runCatching {
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("guid", guid)
            .addQueryParameter("voter", voter)
            .build()
        val request = Request.Builder().url(url).get().header("X-Plexiplay-Key", APP_KEY).build()
        val raw = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            response.body?.string().orEmpty()
        }
        val obj = json.parseToJsonElement(raw).jsonObject
        val average = obj["average"]?.jsonPrimitive?.doubleOrNull?.toFloat()
        val count = obj["count"]?.jsonPrimitive?.intOrNull ?: 0
        val mine = obj["mine"]?.jsonPrimitive?.intOrNull ?: 0
        val myReview = obj["myReview"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.contentOrNull
        CommunityRating(average, count, mine, myReview)
    }.getOrNull()

    /** Avis courts laissés sur ce titre par les autres utilisateurs Plexiplay (popup ouverte en cliquant sur les étoiles). */
    fun reviews(guid: String): List<Review> = runCatching {
        val url = ENDPOINT.toHttpUrl().newBuilder().addQueryParameter("guid", guid).addQueryParameter("reviews", "1").build()
        val request = Request.Builder().url(url).get().header("X-Plexiplay-Key", APP_KEY).build()
        val raw = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching emptyList()
            response.body?.string().orEmpty()
        }
        json.parseToJsonElement(raw).jsonObject["reviews"]?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element.jsonObject
            val stars = item["stars"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val text = item["review"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Review(stars, text)
        }
    }.getOrDefault(emptyList())

    /**
     * Dépose mon vote (1 à 5), ou le retire (0). `review` : null = ne pas y toucher, chaîne vide = l'effacer,
     * texte = le remplacer. Renvoie false si le service est injoignable ou a refusé.
     */
    fun submit(guid: String, voter: String, stars: Int, review: String? = null): Boolean = runCatching {
        val reviewField = if (review != null) ""","review":"${escape(review.take(200))}"""" else ""
        val body = """{"guid":"${escape(guid)}","voter":"${escape(voter)}","stars":$stars$reviewField}"""
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(ENDPOINT).post(body).header("X-Plexiplay-Key", APP_KEY).build()
        http.newCall(request).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
