package com.example.vibrationtester

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
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

    /**
     * Hogyan érje el az app a "közepes/gyenge/erős" rezgést.
     * AUTO: ha a hardver jelzi az erősségszabályzást, amplitúdót használ,
     * különben a kitöltési tényezőt (on/off arányt) moduláló emulációt.
     * AMPLITUDE: mindig amplitúdót kér, akkor is, ha a készülék azt
     * jelzi, hogy nem támogatja (egyes gyártók eszközei rosszul jelentik).
     * DUTY_CYCLE: mindig az on/off arányt modulálja - ez minden
     * hardveren érezhető, mert nem az amplitúdó-támogatásra épül.
     */
    enum class IntensityMethod { AUTO, AMPLITUDE, DUTY_CYCLE }

    /**
     * A rezgés rendszer-szintű "felhasználási osztálya". Az Android a
     * felhasználó rendszerbeállításai (Rezgés erőssége: hívás / értesítés
     * / érintés / média) szerint skálázza az app rezgését, és a háttérből
     * indított rezgést csak "figyelemfelkeltő" osztállyal engedi.
     */
    enum class VibrationUsage {
        ALARM, ACCESSIBILITY, NOTIFICATION, TOUCH, MEDIA, UNKNOWN;

        fun toAudioAttributes(): AudioAttributes? {
            val usage = when (this) {
                ALARM -> AudioAttributes.USAGE_ALARM
                ACCESSIBILITY -> AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
                NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
                TOUCH -> AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
                MEDIA -> AudioAttributes.USAGE_MEDIA
                UNKNOWN -> return null
            }
            return AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }
    }

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
    fun effectiveAmplitude(): Int = 255

    /**
     * Fő erősség százalékban (1-100), MINDEN módra érvényes: a lejátszás
     * központi pontján (VibrationService) skálázza az összes amplitúdót,
     * kitöltési tényezőt, primitív-skálát és envelope-intenzitást. 100% =
     * a mód saját, legnagyobb erőssége. (A korábbi "egyéni erősség"
     * kapcsoló csak négy módra hatott, ezért tűnt úgy, hogy a csúszka nem
     * csinál semmit.)
     */
    var masterIntensityPercent: Int
        get() = prefs.getInt(KEY_MASTER_INTENSITY, 100)
        set(value) = prefs.edit().putInt(KEY_MASTER_INTENSITY, value.coerceIn(1, 100)).apply()

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

    var intensityMethod: IntensityMethod
        get() = try {
            IntensityMethod.valueOf(prefs.getString(KEY_INTENSITY_METHOD, IntensityMethod.AUTO.name) ?: IntensityMethod.AUTO.name)
        } catch (e: IllegalArgumentException) {
            IntensityMethod.AUTO
        }
        set(value) = prefs.edit().putString(KEY_INTENSITY_METHOD, value.name).apply()

    var vibrationUsage: VibrationUsage
        get() = try {
            VibrationUsage.valueOf(prefs.getString(KEY_USAGE, VibrationUsage.ALARM.name) ?: VibrationUsage.ALARM.name)
        } catch (e: IllegalArgumentException) {
            VibrationUsage.ALARM
        }
        set(value) = prefs.edit().putString(KEY_USAGE, value.name).apply()

    // Tizedhertzben tárolva (1-100 -> 0.1-10.0 Hz), hogy egész SeekBar
    // értékként állítható legyen. A Szinuszos és a Négyszögjel-szerű mód
    // közösen használja ezt a frekvenciát.
    var waveFrequencyTenthsHz: Int
        get() = prefs.getInt(KEY_WAVE_FREQUENCY, DEFAULT_WAVE_FREQUENCY_TENTHS_HZ)
        set(value) = prefs.edit()
            .putInt(KEY_WAVE_FREQUENCY, value.coerceIn(1, 100)).apply()

    fun waveFrequencyHz(): Double = waveFrequencyTenthsHz / 10.0

    /** 5-100: mekkora legyen a Szinuszos mód erősség-lengésének mértéke. */
    var sineAmplitudePercent: Int
        get() = prefs.getInt(KEY_SINE_AMPLITUDE, DEFAULT_SINE_AMPLITUDE_PERCENT)
        set(value) = prefs.edit()
            .putInt(KEY_SINE_AMPLITUDE, value.coerceIn(5, 100)).apply()

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
        private const val KEY_MASTER_INTENSITY = "master_intensity_percent"
        private const val KEY_INTENSITY_METHOD = "intensity_method"
        private const val KEY_USAGE = "vibration_usage"
        private const val KEY_WAVE_FREQUENCY = "wave_frequency_tenths_hz"
        private const val KEY_SINE_AMPLITUDE = "sine_amplitude_percent"

        const val DEFAULT_DURATION_MS = 400L
        const val DEFAULT_PAUSE_MS = 800L
        const val MIN_DURATION_MS = 20L
        const val MAX_DURATION_MS = 5000L
        const val MIN_PAUSE_MS = 0L
        const val MAX_PAUSE_MS = 5000L
        const val DEFAULT_UNPREDICTABILITY = 60
        const val DEFAULT_WAVE_FREQUENCY_TENTHS_HZ = 20 // 2.0 Hz
        const val DEFAULT_SINE_AMPLITUDE_PERCENT = 80
    }
}
