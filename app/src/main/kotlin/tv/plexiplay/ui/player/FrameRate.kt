package tv.plexiplay.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Display
import kotlin.math.abs

/**
 * « Adapter la fréquence d'image » (cadre « TV Philips et équivalent ») : pendant le film, demande à la TV le mode
 * d'écran dont la fréquence est celle du film (23,976 / 24 Hz, ou un multiple), au lieu de rester à 60 Hz.
 * Sans effet si la TV ne propose aucun mode adapté (la page Diagnostic le dit).
 */
internal object FrameRate {
    /** Dernier résultat, affiché dans la page Diagnostic (ligne « Adaptation »). */
    @Volatile
    var note: String = "réglage non activé"

    private fun activity(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun display(activity: Activity): Display? =
        runCatching { activity.windowManager.defaultDisplay }.getOrNull()

    fun hz(value: Float): String = "%.3f".format(value).trimEnd('0').trimEnd(',', '.')

    /** Mode adapté à la cadence : fréquence = 1 à 5 fois celle du film (à 0,5 % près), de préférence à la taille d'écran actuelle. */
    private fun best(display: Display, fps: Float): Display.Mode? {
        val current = display.mode
        val modes = display.supportedModes.toList()
        val same = modes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        val bigger = modes.filter { it.physicalWidth > current.physicalWidth && it.physicalHeight >= current.physicalHeight }
        for (pool in listOf(same, bigger)) {
            for (k in 1..5) {
                val target = fps * k
                val match = pool
                    .filter { abs(it.refreshRate - target) / target <= 0.005f }
                    .minByOrNull { abs(it.refreshRate - target) }
                if (match != null) return match
            }
        }
        return null
    }

    /** Demande le mode adapté au film. À appeler depuis le fil principal. */
    fun apply(context: Context, fps: Float) {
        val act = activity(context)
        if (act == null || Build.VERSION.SDK_INT < 23) {
            note = "non disponible sur cette TV"
            return
        }
        if (fps <= 0f) {
            note = "cadence du film inconnue"
            return
        }
        val display = display(act) ?: run { note = "écran introuvable"; return }
        val current = display.mode
        val mode = best(display, fps)
        if (mode == null) {
            note = "aucun mode adapté à ${hz(fps)} i/s : l'écran reste à ${hz(current.refreshRate)} Hz"
            return
        }
        if (mode.modeId == current.modeId) {
            note = "déjà à ${hz(mode.refreshRate)} Hz pour un film en ${hz(fps)} i/s"
            return
        }
        runCatching {
            val params = act.window.attributes
            params.preferredDisplayModeId = mode.modeId
            act.window.attributes = params
            note = "demandée : ${mode.physicalWidth}×${mode.physicalHeight} à ${hz(mode.refreshRate)} Hz pour un film en ${hz(fps)} i/s"
        }.onFailure { note = "refusée par la TV" }
    }

    /** Rend à l'écran son mode habituel. */
    fun restore(context: Context) {
        val act = activity(context) ?: return
        if (Build.VERSION.SDK_INT < 23) return
        runCatching {
            val params = act.window.attributes
            if (params.preferredDisplayModeId != 0) {
                params.preferredDisplayModeId = 0
                act.window.attributes = params
            }
        }
    }

    /** « Modes disponibles » de la page Diagnostic : une ligne par taille d'écran, avec ses fréquences. */
    @Suppress("DEPRECATION")
    fun modesLines(context: Context): List<Pair<String, String>> {
        val act = activity(context) ?: return emptyList()
        val display = display(act) ?: return emptyList()
        return runCatching {
            display.supportedModes
                .groupBy { it.physicalWidth to it.physicalHeight }
                .toSortedMap(compareBy<Pair<Int, Int>>({ -it.first }, { -it.second }))
                .map { (size, modes) ->
                    "Modes ${size.first}×${size.second}" to modes.map { it.refreshRate }.distinct().sorted().joinToString(" / ") { hz(it) } + " Hz"
                }
        }.getOrDefault(emptyList())
    }
}

/** Cadence du fichier (images par seconde) d'après Plex : flux vidéo, sinon texte « 24p » ; 0 si inconnue. */
internal fun fileFrameRate(media: tv.plexiplay.plex.model.MediaInfo?): Float {
    val stream = media?.Part?.firstOrNull()?.Stream?.firstOrNull { it.streamType == 1 }
    stream?.frameRate?.takeIf { it > 0f }?.let { return it }
    val text = media?.videoFrameRate.orEmpty().lowercase().replace(',', '.').trim()
    return Regex("""\d+(\.\d+)?""").find(text)?.value?.toFloatOrNull()?.takeIf { it in 10f..120f } ?: 0f
}
