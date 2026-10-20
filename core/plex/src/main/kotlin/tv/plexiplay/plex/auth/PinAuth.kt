package tv.plexiplay.plex.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.plexiplay.plex.PlexHeaders
import java.io.IOException

@Serializable
data class PinChallenge(
    val id: Long,
    val code: String,
    val authToken: String? = null,
    val expiresIn: Int? = null,
)

class PinAuthClient(
    private val http: OkHttpClient,
    private val clientId: String,
    private val deviceName: String = "Plexiplay Android",
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun createPin(strong: Boolean = false): PinChallenge {
        val url = "https://plex.tv/api/v2/pins".toHttpUrl().newBuilder()
            .addQueryParameter("strong", if (strong) "true" else "false")
            .build()
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .let { PlexHeaders.apply(it, clientId, deviceName = deviceName) }
            .build()
        return executePin(request)
    }

    fun poll(pinId: Long, code: String): PinChallenge {
        val url = "https://plex.tv/api/v2/pins/$pinId".toHttpUrl().newBuilder()
            .addQueryParameter("code", code)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .let { PlexHeaders.apply(it, clientId, deviceName = deviceName) }
            .build()
        return executePin(request)
    }

    /**
     * Connexion classique. Beaucoup de comptes exigent la 2FA : dans ce cas
     * plex.tv répond 401 et le code à 4 caractères reste le chemin principal.
     */
    /** Adresse à ouvrir sur un téléphone pour se connecter (Google, Apple, e-mail) avec un code fort. */
    fun webAuthUrl(code: String): String =
        "https://app.plex.tv/auth#?clientID=" + java.net.URLEncoder.encode(clientId, "UTF-8") +
            "&code=" + java.net.URLEncoder.encode(code, "UTF-8") +
            "&context%5Bdevice%5D%5Bproduct%5D=" + PlexHeaders.PRODUCT

    fun signIn(login: String, password: String, verificationCode: String = ""): String {
        val body = FormBody.Builder()
            .add("login", login)
            .add("password", password)
            .add("noPrompt", "true")
            .apply { if (verificationCode.isNotBlank()) add("verificationCode", verificationCode.trim()) }
            .build()
        val v2 = Request.Builder()
            .url("https://plex.tv/api/v2/users/signin")
            .post(body)
            .let { PlexHeaders.apply(it, clientId, deviceName = deviceName) }
            .build()
        val classic = Request.Builder()
            .url("https://plex.tv/users/sign_in.json")
            .post(body)
            .header("Authorization", Credentials.basic(login, password + verificationCode.trim()))
            .header("X-Plex-Username", login)
            .let { PlexHeaders.apply(it, clientId, deviceName = deviceName) }
            .build()
        val errors = mutableListOf<String>()
        for (request in listOf(v2, classic)) {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 401 && verificationCode.isBlank() &&
                        (raw.contains("1029") || raw.contains("verification", ignoreCase = true))
                    ) {
                        throw TwoFactorRequired()
                    }
                    errors += "${response.code}"
                    return@use
                }
                val token = extractToken(raw)
                if (!token.isNullOrBlank()) return token
                errors += "jeton absent"
            }
        }
        throw IOException("Connexion refusée (${errors.joinToString()}). Si le compte a la 2FA, utilisez le code plex.tv/link.")
    }

    private fun extractToken(raw: String): String? {
        return runCatching { json.decodeFromString(SignInResponse.serializer(), raw).authToken }.getOrNull()
            ?: runCatching { json.decodeFromString(ClassicSignIn.serializer(), raw).user?.authToken }.getOrNull()
    }

    private fun executePin(request: Request): PinChallenge {
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Plex PIN ${response.code}")
            return json.decodeFromString(PinChallenge.serializer(), raw)
        }
    }
}

/** Le compte a la double authentification : il faut le code à 6 chiffres. */
class TwoFactorRequired : IOException("Code de vérification requis")

@Serializable
private data class SignInResponse(val authToken: String? = null)

@Serializable
private data class ClassicSignIn(val user: ClassicUser? = null)

@Serializable
private data class ClassicUser(val authToken: String? = null)
