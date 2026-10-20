package tv.plexiplay.plex

import okhttp3.Request

object PlexHeaders {
    const val PRODUCT = "Plexiplay"
    const val VERSION = "1.5.2"

    fun apply(
        builder: Request.Builder,
        clientId: String,
        token: String? = null,
        deviceName: String = "Plexiplay Android",
    ): Request.Builder {
        builder
            .header("Accept", "application/json")
            .header("X-Plex-Product", PRODUCT)
            .header("X-Plex-Version", VERSION)
            .header("X-Plex-Client-Identifier", clientId)
            .header("X-Plex-Platform", "Android")
            .header("X-Plex-Platform-Version", "14")
            .header("X-Plex-Device", "Android TV")
            .header("X-Plex-Device-Name", deviceName)
            .header("X-Plex-Provides", "player,client")
            .header("X-Plex-Model", "Plexiplay")
        if (!token.isNullOrBlank()) builder.header("X-Plex-Token", token)
        return builder
    }
}
