package tv.plexiplay.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tv.plexiplay.player.AspectMode
import tv.plexiplay.player.Engine
import tv.plexiplay.player.PlaybackSettings
import tv.plexiplay.player.PlayerKind
import tv.plexiplay.plex.model.ServerEndpoint

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("plexiplay", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun clientId(): String {
        val existing = prefs.getString(KEY_CLIENT, null)
        if (!existing.isNullOrBlank()) return existing
        val created = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_CLIENT, created).apply()
        return created
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveToken(token: String) = prefs.edit().putString(KEY_TOKEN, token).apply()

    fun clearSession() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_SERVERS).remove(KEY_SERVER).apply()
    }

    fun servers(): List<ServerEndpoint> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ServerEndpoint>>(raw) }.getOrDefault(emptyList())
    }

    fun saveServers(servers: List<ServerEndpoint>, active: Int) {
        prefs.edit()
            .putString(KEY_SERVERS, json.encodeToString(servers))
            .putInt(KEY_SERVER, active)
            .apply()
    }

    /** Clés des bibliothèques masquées dans le menu de gauche, pour un serveur donné. */
    fun hiddenLibraries(serverId: String): Set<String> =
        prefs.getStringSet("hidden_$serverId", emptySet())?.toSet().orEmpty()

    fun saveHiddenLibraries(serverId: String, hidden: Set<String>) {
        prefs.edit().putStringSet("hidden_$serverId", hidden.toSet()).apply()
    }

    fun activeServerIndex(): Int = prefs.getInt(KEY_SERVER, 0)

    fun settings(): PlaybackSettings {
        if (prefs.getInt(KEY_DEFAULTS, 0) < 4) {
            prefs.edit()
                .putString(KEY_PLAYER, PlayerKind.EXO.name)
                .putInt(KEY_DEFAULTS, 4)
                .apply()
        }
        return PlaybackSettings(
        uiLanguage = "fr",
        audioLanguage = prefs.getString(KEY_AUDIO, "fr") ?: "fr",
        subtitleLanguage = prefs.getString(KEY_SUB, "off") ?: "off",
        aspect = AspectMode.AUTO,
        engine = runCatching { Engine.valueOf(prefs.getString(KEY_ENGINE, "TV") ?: "TV") }
            .getOrDefault(Engine.TV),
        player = runCatching { PlayerKind.valueOf(prefs.getString(KEY_PLAYER, "EXO") ?: "EXO") }
            .getOrDefault(PlayerKind.EXO),
        trailerCount = 0,
        compatMode = prefs.getString(KEY_COMPAT, "auto") ?: "auto",
        mpvMode = "light",
        formatFix = prefs.getBoolean(KEY_FORMAT_FIX, true),
        compat = prefs.getString(KEY_COMPAT, "auto") == "on",
        compatTitles = if ((prefs.getString(KEY_COMPAT, "auto") ?: "auto") == "auto") {
            prefs.getStringSet(KEY_COMPAT_TITLES, emptySet()).orEmpty().toSet()
        } else {
            emptySet()
        },
        )
    }

    /** Appelé quand la lecture a bloqué : le mode automatique devient compatible pour cet appareil. */
    fun learnCompat(ratingKey: String) {
        if (ratingKey.isBlank()) return
        val next = prefs.getStringSet(KEY_COMPAT_TITLES, emptySet()).orEmpty().toMutableSet().apply { add(ratingKey) }
        prefs.edit().putStringSet(KEY_COMPAT_TITLES, next).apply()
    }

    fun seriesChoice(showKey: String): SeriesChoice? {
        if (showKey.isBlank()) return null
        val raw = prefs.getString("series_$showKey", null) ?: return null
        val parts = raw.split("|")
        if (parts.size < 3) return null
        return SeriesChoice(parts[0], parts[1], parts[2] == "1")
    }

    fun saveSeriesChoice(showKey: String, choice: SeriesChoice) {
        if (showKey.isBlank()) return
        prefs.edit()
            .putString("series_$showKey", "${choice.audio}|${choice.subtitleLanguage}|${if (choice.subtitleForced) "1" else "0"}")
            .apply()
    }

    /** Volume de la musique de générique (0 à 50 %, par paliers de 10), 20 % par défaut. */
    fun themeVolume(): Int = prefs.getInt("theme_volume", 20).coerceIn(0, 50)

    fun saveThemeVolume(value: Int) {
        prefs.edit().putInt("theme_volume", value.coerceIn(0, 50)).apply()
    }

    fun theme(): String = prefs.getString("theme", "classic") ?: "classic"

    fun saveTheme(value: String) {
        prefs.edit().putString("theme", value).apply()
    }

    fun saveSettings(settings: PlaybackSettings) {
        prefs.edit()
            .putString(KEY_UI, settings.uiLanguage)
            .putString(KEY_AUDIO, settings.audioLanguage)
            .putString(KEY_SUB, settings.subtitleLanguage)
            .putString(KEY_ASPECT, settings.aspect.name)
            .putString(KEY_ENGINE, settings.engine.name)
            .putString(KEY_PLAYER, settings.player.name)
            .putInt(KEY_TRAILERS, settings.trailerCount)
            .putString(KEY_COMPAT, settings.compatMode)
            .putString(KEY_MPV_MODE, settings.mpvMode)
            .putBoolean(KEY_FORMAT_FIX, settings.formatFix)
            .apply()
        if (settings.compatMode != "auto") prefs.edit().remove(KEY_COMPAT_TITLES).apply()
    }

    private companion object {
        const val KEY_CLIENT = "client"
        const val KEY_TOKEN = "token"
        const val KEY_SERVERS = "servers"
        const val KEY_SERVER = "server"
        const val KEY_UI = "ui"
        const val KEY_AUDIO = "audio"
        const val KEY_SUB = "sub"
        const val KEY_ASPECT = "aspect"
        const val KEY_ENGINE = "engine"
        const val KEY_PLAYER = "player"
        const val KEY_TRAILERS = "trailers"
        const val KEY_COMPAT = "compat"
        const val KEY_MPV_MODE = "mpv_mode"
        const val KEY_FORMAT_FIX = "format_fix"
        const val KEY_COMPAT_LEARNED = "compat_learned"
        const val KEY_COMPAT_TITLES = "compat_titles"
        const val KEY_DEFAULTS = "defaults"
    }
}
