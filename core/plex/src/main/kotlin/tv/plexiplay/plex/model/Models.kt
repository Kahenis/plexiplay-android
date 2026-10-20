package tv.plexiplay.plex.model

import kotlinx.serialization.Serializable

@Serializable
data class PlexConnection(
    val protocol: String = "",
    val address: String = "",
    val port: Int = 0,
    val uri: String = "",
    val local: Boolean = false,
    val relay: Boolean = false,
)

@Serializable
data class PlexResource(
    val name: String = "",
    val product: String = "",
    val clientIdentifier: String = "",
    val provides: String = "",
    val owned: Boolean = false,
    val accessToken: String = "",
    val connections: List<PlexConnection> = emptyList(),
) {
    fun isServer(): Boolean = provides.split(",").any { it.equals("server", true) }
}

@Serializable
data class ServerEndpoint(
    val name: String,
    val clientIdentifier: String,
    val accessToken: String,
    val uri: String,
    val owned: Boolean,
    /** Autres adresses du serveur (distante, relais), essayées si la première ne répond pas. */
    val alternates: List<String> = emptyList(),
)

@Serializable
data class LibrarySection(
    val key: String = "",
    val title: String = "",
    val type: String = "",
    val agent: String = "",
    val scanner: String = "",
) {
    /** Bibliothèque « Vidéos personnelles » / « Plex Video Files » : pas de métadonnées, donc pas de fiche. */
    val personal: Boolean
        get() = type == "movie" && (
            agent.endsWith("agents.none") ||
                scanner.contains("Video Files", ignoreCase = true) ||
                scanner.contains("Personal Media", ignoreCase = true)
            )
}

@Serializable
data class StreamInfo(
    val id: Int = 0,
    val streamType: Int = 0,
    val codec: String = "",
    val language: String = "",
    val languageTag: String = "",
    val displayTitle: String = "",
    val extendedDisplayTitle: String = "",
    val channels: Int = 0,
    val selected: Boolean = false,
    val forced: Boolean = false,
    val languageCode: String = "",
    val index: Int = -1,
    val key: String = "",
)

@Serializable
data class MediaPart(
    val id: Long = 0,
    val key: String = "",
    val file: String = "",
    val container: String = "",
    val Stream: List<StreamInfo> = emptyList(),
)

@Serializable
data class MediaInfo(
    val id: Long = 0,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val container: String = "",
    val bitrate: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val videoResolution: String = "",
    val Part: List<MediaPart> = emptyList(),
)

@Serializable
data class Metadata(
    val librarySectionID: Long = 0L,
    val ratingKey: String = "",
    val key: String = "",
    val title: String = "",
    val type: String = "",
    val year: Int = 0,
    val thumb: String = "",
    val parentThumb: String = "",
    val grandparentThumb: String = "",
    val art: String = "",
    val summary: String = "",
    val tagline: String = "",
    val theme: String = "",
    val duration: Long = 0,
    val rating: Float = 0f,
    val audienceRating: Float = 0f,
    val userRating: Float = 0f,
    val ratingImage: String = "",
    val viewCount: Int = 0,
    val viewOffset: Long = 0,
    val index: Int = 0,
    val parentIndex: Int = 0,
    val leafCount: Int = 0,
    /** Local : nombre d'épisodes regroupés dans une vignette « ajouts multiples ». */
    val added: Int = 0,
    val viewedLeafCount: Int = 0,
    val childCount: Int = 0,
    val grandparentRatingKey: String = "",
    val parentRatingKey: String = "",
    val grandparentTitle: String = "",
    val parentTitle: String = "",
    val grandparentTheme: String = "",
    val grandparentArt: String = "",
    val extraType: Int = 0,
    val Genre: List<TagItem> = emptyList(),
    val Rating: List<RatingItem> = emptyList(),
    val Media: List<MediaInfo> = emptyList(),
    val Role: List<RoleItem> = emptyList(),
)

/** Acteur d'un film ou d'une série : nom (tag), personnage (role), photo (thumb), identifiants Plex. */
@Serializable
data class RoleItem(
    val id: Long = 0,
    val tag: String = "",
    val tagKey: String = "",
    val role: String = "",
    val thumb: String = "",
)

@Serializable
data class TagItem(val tag: String = "")

@Serializable
data class RatingItem(
    val image: String = "",
    val value: Float = 0f,
    val type: String = "",
)

@Serializable
data class ServerSetting(
    val id: String = "",
    val value: String = "",
)

@Serializable
data class DirectoryBox(val Directory: List<LibrarySection> = emptyList())

@Serializable
data class MetadataBox(val Metadata: List<Metadata> = emptyList())

@Serializable
data class SettingBox(val Setting: List<ServerSetting> = emptyList())

@Serializable
data class Wrapped<T>(val MediaContainer: T? = null)
