package org.stypox.dicio.io.wake.oww

import android.content.Context

/**
 * Wake word activation threshold. Upstream Dicio hardcodes 0.8, while the openWakeWord README
 * says its models were "trained to work well with a default threshold of 0.5".
 * Lower = triggers more easily (fewer missed "Hey Dicio", more false activations).
 */
object OwwSensitivity {
    private const val PREFS = "oww_settings"
    private const val KEY_THRESHOLD = "threshold"
    const val DEFAULT_THRESHOLD = 0.5f
    val THRESHOLDS = listOf(0.3f, 0.4f, 0.5f, 0.65f, 0.8f)

    fun threshold(context: Context): Float =
        prefs(context).getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)

    fun setThreshold(context: Context, value: Float) =
        prefs(context).edit().putFloat(KEY_THRESHOLD, value).apply()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
