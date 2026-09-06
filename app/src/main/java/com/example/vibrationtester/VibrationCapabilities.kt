package com.example.vibrationtester

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Az adott készülék rezgő-hardverének futásidejű képesség-lekérdezése.
 *
 * Alapelv: soha nem következtetünk a támogatásra pusztán az Android
 * verzióból. Ugyanaz az API-szint nagyon eltérő haptikai hardveren futhat,
 * ezért mindenhol a Vibrator saját, futásidejű ellenőrző metódusait hívjuk
 * (hasAmplitudeControl, areEffectsSupported, arePrimitivesSupported), és
 * csak ott zárjuk ki eleve egy funkciót, ahol maga az API nem is létezik az
 * adott API-szinten.
 */
object VibrationCapabilities {

    enum class Support { YES, NO, UNKNOWN }

    data class PredefinedEffectInfo(val id: Int, val nameRes: Int, val support: Support)
    data class PrimitiveInfo(val id: Int, val nameRes: Int, val supported: Boolean, val checkReliable: Boolean)

    data class Report(
        val sdkInt: Int,
        val hasVibrator: Boolean,
        val amplitudeControlApiExists: Boolean, // API >= 26 (Android 8.0)
        val hasAmplitudeControl: Boolean,       // a fentiek ÉS a hardver ténylegesen támogatja
        val predefinedEffectsApiExists: Boolean, // API >= 29 (Android 10)
        val effectSupportQueryApiExists: Boolean, // API >= 30 (Android 11): areEffectsSupported
        val predefinedEffects: List<PredefinedEffectInfo>,
        val compositionApiExists: Boolean, // API >= 30 (Android 11)
        val primitives: List<PrimitiveInfo>,
        val resonantFrequencyHz: Float?, // API >= 33 (Android 13) és csak ha a hardver jelenti
        val qFactor: Float?
    )

    fun buildReport(vibrator: Vibrator): Report {
        val sdkInt = Build.VERSION.SDK_INT
        val hasVibrator = vibrator.hasVibrator()
        val amplitudeControlApiExists = sdkInt >= Build.VERSION_CODES.O
        val hasAmplitudeControl = amplitudeControlApiExists && hasVibrator && vibrator.hasAmplitudeControl()

        val predefinedEffectsApiExists = sdkInt >= Build.VERSION_CODES.Q
        val effectSupportQueryApiExists = sdkInt >= Build.VERSION_CODES.R
        val predefinedEffects = if (predefinedEffectsApiExists) {
            buildPredefinedEffectsList(vibrator, effectSupportQueryApiExists)
        } else {
            emptyList()
        }

        val compositionApiExists = sdkInt >= Build.VERSION_CODES.R
        val primitives = if (compositionApiExists) {
            buildPrimitivesList(vibrator, sdkInt)
        } else {
            emptyList()
        }

        var resonantFrequency: Float? = null
        var qFactor: Float? = null
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            val freq = vibrator.resonantFrequency
            val q = vibrator.qFactor
            // A készülék NaN-t ad vissza, ha nem jelenti ezt az értéket.
            resonantFrequency = if (!freq.isNaN()) freq else null
            qFactor = if (!q.isNaN()) q else null
        }

        return Report(
            sdkInt = sdkInt,
            hasVibrator = hasVibrator,
            amplitudeControlApiExists = amplitudeControlApiExists,
            hasAmplitudeControl = hasAmplitudeControl,
            predefinedEffectsApiExists = predefinedEffectsApiExists,
            effectSupportQueryApiExists = effectSupportQueryApiExists,
            predefinedEffects = predefinedEffects,
            compositionApiExists = compositionApiExists,
            primitives = primitives,
            resonantFrequencyHz = resonantFrequency,
            qFactor = qFactor
        )
    }

    private fun buildPredefinedEffectsList(
        vibrator: Vibrator,
        canQuerySupport: Boolean
    ): List<PredefinedEffectInfo> {
        val candidates = listOf(
            VibrationEffect.EFFECT_CLICK to R.string.effect_click,
            VibrationEffect.EFFECT_DOUBLE_CLICK to R.string.effect_double_click,
            VibrationEffect.EFFECT_TICK to R.string.effect_tick,
            VibrationEffect.EFFECT_HEAVY_CLICK to R.string.effect_heavy_click
        )

        return if (canQuerySupport) {
            // areEffectsSupported csak API 30-tól létezik.
            val ids = candidates.map { it.first }.toIntArray()
            val results = vibrator.areEffectsSupported(*ids)
            candidates.mapIndexed { index, (id, nameRes) ->
                val support = when (results[index]) {
                    Vibrator.VIBRATION_EFFECT_SUPPORT_YES -> Support.YES
                    Vibrator.VIBRATION_EFFECT_SUPPORT_NO -> Support.NO
                    else -> Support.UNKNOWN
                }
                PredefinedEffectInfo(id, nameRes, support)
            }
        } else {
            // Android 10-en (API 29) az effektusok elérhetők, de nincs API a
            // hardvertámogatás lekérdezésére (az areEffectsSupported csak API
            // 30-tól létezik) - a rendszer ilyenkor csendben egy általános
            // mintára esik vissza, ha nincs eszközre optimalizált verzió.
            candidates.map { (id, nameRes) -> PredefinedEffectInfo(id, nameRes, Support.UNKNOWN) }
        }
    }

    private fun buildPrimitivesList(vibrator: Vibrator, sdkInt: Int): List<PrimitiveInfo> {
        // CLICK, TICK, QUICK_RISE, SLOW_RISE, QUICK_FALL: API 30 (Android 11) óta.
        val api30Primitives = listOf(
            VibrationEffect.Composition.PRIMITIVE_CLICK to R.string.primitive_click,
            VibrationEffect.Composition.PRIMITIVE_TICK to R.string.primitive_tick,
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE to R.string.primitive_quick_rise,
            VibrationEffect.Composition.PRIMITIVE_SLOW_RISE to R.string.primitive_slow_rise,
            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL to R.string.primitive_quick_fall
        )
        // THUD, SPIN, LOW_TICK: csak API 31 (Android 12) óta léteznek.
        val api31Primitives = if (sdkInt >= Build.VERSION_CODES.S) {
            listOf(
                VibrationEffect.Composition.PRIMITIVE_THUD to R.string.primitive_thud,
                VibrationEffect.Composition.PRIMITIVE_SPIN to R.string.primitive_spin,
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK to R.string.primitive_low_tick
            )
        } else {
            emptyList()
        }
        val candidates = api30Primitives + api31Primitives

        // Fontos: az arePrimitivesSupported eredménye Android 11-en (API 30)
        // nem megbízható - a dokumentáció szerint ott a hívás pusztán azt
        // ellenőrzi, hogy a kompozíció-API egyáltalán elérhető-e, és emiatt
        // minden kért azonosítóra "true"-t ad vissza, függetlenül a tényleges
        // hardvertámogatástól. Csak Android 12-től (API 31) megbízható
        // eszközönkénti ellenőrzés.
        val checkReliable = sdkInt >= Build.VERSION_CODES.S
        val ids = candidates.map { it.first }.toIntArray()
        val supported = vibrator.arePrimitivesSupported(*ids)
        return candidates.mapIndexed { index, (id, nameRes) ->
            PrimitiveInfo(id, nameRes, supported[index], checkReliable)
        }
    }
}
