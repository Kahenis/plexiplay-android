package tv.plexiplay

import android.content.Context
import android.content.pm.ActivityInfo

/**
 * Orientation de l'appli hors lecture.
 * - Téléphone classique (plus petite dimension < 600 dp, sans charnière) : paysage forcé (selon le capteur),
 *   la mise en page portrait ne permettant pas un bon fonctionnement.
 * - Pliant (capteur d'angle de charnière, ouvert ou fermé) et tablette : rotation libre.
 */
fun appOrientation(context: Context): Int {
    val smallest = context.resources.configuration.smallestScreenWidthDp
    val foldable = context.packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle")
    return if (smallest in 1 until 600 && !foldable) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    }
}
