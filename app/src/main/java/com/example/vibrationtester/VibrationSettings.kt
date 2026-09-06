package com.example.vibrationtester

import android.content.Context
import android.content.SharedPreferences
import android.os.VibrationEffect

/**
 * Minden felhasználó által állítható rezgés-paraméter és viselkedési kapcsoló
 * egyetlen forrása, SharedPreferences-re épülve. A MainActivity, a
 * SettingsActivity és a VibrationService (ami az Activity-től függetlenül fut)
 * mind ugyanazokat az értékeket olvassák, anélkül hogy kézzel kellene őket
 * továbbadni egymásnak.
 */
class VibrationSettings(context: Context) {

    enum class VibrationType { CUSTOM, PREDEFINED, COMPOSITION }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var keepAliveAfterLock: Boolean
        get() = prefs.getBoolean(KEY_KEEP_ALIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_ALIVE, value).apply()

    // Alapértelmezésben BE (true), mert ez felel meg az eredeti, módosítás
    // előtti viselkedésnek: minden gombnyomás újraindítja a rezgést.
    var restartOnRepeatPress: Boolean
        get() = prefs.getBoolean(KEY_RESTART_ON_REPEAT, true)
        set(value) = prefs.edit().putBoolean(KEY_RESTART_ON_REPEAT, value).apply()

    var useCustomAmplitude: Boolean
        get() = prefs.getBoolean(KEY_USE_CUSTOM_AMPLITUDE, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_CUSTOM_AMPLITUDE, value).apply()

    var customAmplitude: Int
        get() = prefs.getInt(KEY_AMPLITUDE, 255)
        set(value) = prefs.edit().putInt(KEY_AMPLITUDE, value.coerceIn(1, 255)).apply()

    /** A ténylegesen a VibrationEffect hívásoknak átadandó erősség. */
    fun effectiveAmplitude(): Int =
        if (useCustomAmplitude) customAmplitude else VibrationEffect.DEFAULT_AMPLITUDE

    var durationMs: Long
        get() = prefs.getLong(KEY_DURATION, DEFAULT_DURATION_MS)
        set(value) = prefs.edit()
            .putLong(KEY_DURATION, value.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)).apply()

    var pauseMs: Long
        get() = prefs.getLong(KEY_PAUSE, DEFAULT_PAUSE_MS)
        set(value) = prefs.edit()
            .putLong(KEY_PAUSE, value.coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS)).apply()

    /** 0-100: mennyire térhet el véletlenszerűen a Kiszámíthatatlan mód a duration/pause alapértéktől. */
    var unpredictabilityPercent: Int
        get() = prefs.getInt(KEY_UNPREDICTABILITY, DEFAULT_UNPREDICTABILITY)
        set(value) = prefs.edit()
            .putInt(KEY_UNPREDICTABILITY, value.coerceIn(0, 100)).apply()

    var vibrationType: VibrationType
        get() {
            val name = prefs.getString(KEY_VIBRATION_TYPE, VibrationType.CUSTOM.name)
            return try {
                VibrationType.valueOf(name ?: VibrationType.CUSTOM.name)
            } catch (e: IllegalArgumentException) {
                VibrationType.CUSTOM
            }
        }
        set(value) = prefs.edit().putString(KEY_VIBRATION_TYPE, value.name).apply()

    var predefinedEffectId: Int
        get() = prefs.getInt(KEY_PREDEFINED_EFFECT, VibrationEffect.EFFECT_CLICK)
        set(value) = prefs.edit().putInt(KEY_PREDEFINED_EFFECT, value).apply()

    var selectedPrimitiveIds: Set<Int>
        get() = prefs.getStringSet(KEY_PRIMITIVES, emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        set(value) = prefs.edit()
            .putStringSet(KEY_PRIMITIVES, value.map { it.toString() }.toSet()).apply()

    companion object {
        private const val PREFS_NAME = "vibration_tester_prefs"
        private const val KEY_KEEP_ALIVE = "keep_alive_after_lock"
        private const val KEY_RESTART_ON_REPEAT = "restart_on_repeat_press"
        private const val KEY_USE_CUSTOM_AMPLITUDE = "use_custom_amplitude"
        private const val KEY_AMPLITUDE = "amplitude"
        private const val KEY_DURATION = "duration_ms"
        private const val KEY_PAUSE = "pause_ms"
        private const val KEY_UNPREDICTABILITY = "unpredictability_percent"
        private const val KEY_VIBRATION_TYPE = "vibration_type"
        private const val KEY_PREDEFINED_EFFECT = "predefined_effect_id"
        private const val KEY_PRIMITIVES = "selected_primitives"

        const val DEFAULT_DURATION_MS = 400L
        const val DEFAULT_PAUSE_MS = 800L
        const val MIN_DURATION_MS = 20L
        const val MAX_DURATION_MS = 5000L
        const val MIN_PAUSE_MS = 0L
        const val MAX_PAUSE_MS = 5000L
        const val DEFAULT_UNPREDICTABILITY = 60
    }
}
