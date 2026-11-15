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
        prefs.edit().remove(KEY_TOKEN).remove(KEY_SERVERS).remove(KEY_SERVER).remove("account_name").remove("account_id").apply()
    }

    /** Nom du compte Plex affiché dans les paramètres (mémorisé pour l'afficher tout de suite). */
    fun accountName(): String = prefs.getString("account_name", "").orEmpty()

    fun saveAccountName(name: String) = prefs.edit().putString("account_name", name).apply()

    /** Identifiant plex.tv du compte connecté : base (hachée) du votant pour la note Plexiplay. 0 si inconnu. */
    fun accountId(): Long = prefs.getLong("account_id", 0L)

    fun saveAccountId(id: Long) = prefs.edit().putLong("account_id", id).apply()

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
        // Mobile : réglage « Puissance » retiré, l'appareil prépare toujours la vidéo lui-même.
        engine = Engine.TV,
        player = runCatching { PlayerKind.valueOf(prefs.getString(KEY_PLAYER, "EXO") ?: "EXO") }
            .getOrDefault(PlayerKind.EXO),
        trailerCount = 0,
        compatMode = prefs.getString(KEY_COMPAT, "auto") ?: "auto",
        mpvMode = "light",
        audioBoost = prefs.getInt("audio_boost", 100),
        convertTitles = prefs.getStringSet("convert_titles", emptySet()).orEmpty().toSet(),
        boxFullHd = prefs.getBoolean("box_fullhd", false),
        dvNoSkip = prefs.getBoolean("dv_no_skip", false),
        skipIntro = prefs.getBoolean("skip_intro", false),
        skipCredits = prefs.getBoolean("skip_credits", false),
        prerollMode = if (prefs.getBoolean("preroll_locked", false)) "random" else prefs.getString("preroll_mode", "sequential") ?: "sequential",
        prerollLocked = prefs.getBoolean("preroll_locked", false),
        formatFix = prefs.getBoolean(KEY_FORMAT_FIX, true),
        compat = prefs.getString(KEY_COMPAT, "auto") == "on",
        compatTitles = if ((prefs.getString(KEY_COMPAT, "auto") ?: "auto") == "auto") {
            prefs.getStringSet(KEY_COMPAT_TITLES, emptySet()).orEmpty().toSet()
        } else {
            emptySet()
        },
        )
    }

    /** Conversion complète par le serveur pour ce film : case « Forcer la conversion » ou repli automatique. */
    fun setConvert(ratingKey: String, on: Boolean) {
        if (ratingKey.isBlank()) return
        val next = prefs.getStringSet("convert_titles", emptySet()).orEmpty().toMutableSet().apply { if (on) add(ratingKey) else remove(ratingKey) }
        prefs.edit().putStringSet("convert_titles", next).apply()
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

    /** Volume de la musique de générique (0 à 60 %, par paliers de 10), 40 % par défaut. Nouvelle clé : la courbe a changé en 1.4.4. */
    fun themeVolume(): Int = prefs.getInt("theme_volume2", 40).coerceIn(0, 60)

    fun saveThemeVolume(value: Int) {
        prefs.edit().putInt("theme_volume2", value.coerceIn(0, 60)).apply()
    }

    /** Fin de la pause des prerolls (ms), 0 si aucune pause active. */
    fun prerollPauseUntil(): Long = prefs.getLong("preroll_pause_until", 0L).takeIf { it > System.currentTimeMillis() } ?: 0L

    /**
     * Pause de 7 jours. Si une pause annulée avait une date de fin non atteinte, c'est cette date qui est reprise ;
     * sinon (aucune, ou dépassée) 7 nouveaux jours démarrent maintenant.
     */
    fun startPrerollPause(): Long {
        val now = System.currentTimeMillis()
        val saved = prefs.getLong("preroll_pause_saved", 0L)
        val until = if (saved > now) saved else now + 7L * 24 * 60 * 60 * 1000
        prefs.edit().putLong("preroll_pause_until", until).putLong("preroll_pause_saved", 0L).apply()
        return until
    }

    /** Annule la pause en cours en gardant sa date de fin pour une éventuelle reprise. */
    fun cancelPrerollPause() {
        val until = prefs.getLong("preroll_pause_until", 0L)
        prefs.edit().putLong("preroll_pause_saved", if (until > System.currentTimeMillis()) until else 0L)
            .putLong("preroll_pause_until", 0L).apply()
    }

    fun setPrerollLocked(locked: Boolean) {
        prefs.edit().putBoolean("preroll_locked", locked).apply()
    }

    /** Prérolls séquentiels : nom du dernier fichier lu sur cette TV. */
    fun prerollLast(): String = prefs.getString("preroll_last", "") ?: ""

    fun setPrerollLast(name: String) {
        prefs.edit().putString("preroll_last", name).apply()
    }

    /** Prérolls séquentiels : fichiers (nom|signature) qui n'ont pas pu être lus sur cette TV ; ils ne sont plus proposés. */
    fun prerollBad(): Set<String> = prefs.getStringSet("preroll_bad", emptySet()).orEmpty().toSet()

    fun addPrerollBad(key: String) {
        prefs.edit().putStringSet("preroll_bad", prerollBad() + key).apply()
    }

    /** Volume des prerolls (annonces avant le film), de 20 à 100 %, 75 % par défaut. */
    fun prerollVolume(): Int = prefs.getInt("preroll_volume", 75).coerceIn(20, 100)

    fun savePrerollVolume(value: Int) {
        prefs.edit().putInt("preroll_volume", value.coerceIn(20, 100)).apply()
    }

    /** Interrupteur « Sagas » des bibliothèques de films : affiche de la saga à la place de ses films (éteint par défaut). */
    fun sagaMode(): Boolean = prefs.getBoolean("saga_mode", false)

    fun saveSagaMode(value: Boolean) {
        prefs.edit().putBoolean("saga_mode", value).apply()
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
            .putInt("audio_boost", settings.audioBoost)
            .putBoolean("box_fullhd", settings.boxFullHd)
            .putBoolean("dv_no_skip", settings.dvNoSkip)
            .putBoolean("skip_intro", settings.skipIntro)
            .putBoolean("skip_credits", settings.skipCredits)
            .remove("decode_trial")
            .remove("preroll_texture")
            .remove("frame_match")
            .putString("preroll_mode", settings.prerollMode)
            .putBoolean("preroll_locked", settings.prerollLocked)
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
