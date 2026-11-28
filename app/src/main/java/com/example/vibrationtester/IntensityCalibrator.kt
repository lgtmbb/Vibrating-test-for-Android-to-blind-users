package com.example.vibrationtester

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Erősség-kalibráció a telefon SAJÁT gyorsulásmérőjével: a rezgés tényleges
 * fizikai hatását méri, nem azt, amit a rendszer jelent. Három erősségi
 * szintet (15%, 50%, 100%) játszik le két módszerrel (amplitúdó, kitöltési
 * tényező), mindegyik alatt méri a telefon rezgési energiáját (a három
 * tengely szórásának összegéből vett gyök), és azt a módszert választja,
 * amelyiknél az energia a szinttel valóban, mérhetően nő.
 *
 * Erre azért van szükség, mert egyes gyártók eszközei (a Samsung-
 * fórumokon és a libretro-fórumon is szerepelnek ilyen jelentések) az
 * alkalmazás által kért amplitúdót figyelmen kívül hagyhatják, miközben az
 * API hibátlanul fogadja el - ez kódból, mérés nélkül nem látszik.
 *
 * A mérés feltétele: a telefon mozdulatlanul, kemény felületen fekszik.
 */
class IntensityCalibrator(
    context: Context,
    private val vibrator: Vibrator,
    private val usage: VibrationSettings.VibrationUsage
) {
    data class Result(val chosen: VibrationSettings.IntensityMethod?, val report: String)

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val lock = Any()
    private val xs = ArrayList<Float>()
    private val ys = ArrayList<Float>()
    private val zs = ArrayList<Float>()
    private var lastSampleCount = 0

    @Volatile
    private var collecting = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!collecting) return
            synchronized(lock) {
                xs.add(event.values[0])
                ys.add(event.values[1])
                zs.add(event.values[2])
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    suspend fun run(): Result = withContext(Dispatchers.Default) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return@withContext Result(null, "Android 8.0 alatt nincs erősségszabályzás-API, a kalibráció nem értelmezhető.")
        }
        val sensor = accelerometer
            ?: return@withContext Result(null, "Nincs gyorsulásmérő a készüléken, a kalibráció nem végezhető el.")

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        try {
            delay(300)
            val baseline = measure(0L, 500L)
            val levels = listOf(0.15f, 0.5f, 1.0f)
            val amp = levels.map { playAndMeasure(amplitudeEffect(it)) }
            val duty = levels.map { playAndMeasure(dutyEffect(it)) }
            decide(baseline, amp, duty)
        } finally {
            sensorManager.unregisterListener(listener)
            vibrator.cancel()
        }
    }

    private suspend fun playAndMeasure(effect: VibrationEffect): Double {
        play(effect)
        val e = measure(250L, 500L)
        vibrator.cancel()
        delay(500)
        return e
    }

    private suspend fun measure(startDelayMs: Long, windowMs: Long): Double {
        delay(startDelayMs)
        synchronized(lock) {
            xs.clear(); ys.clear(); zs.clear()
        }
        collecting = true
        delay(windowMs)
        collecting = false
        return energy()
    }

    private fun energy(): Double {
        synchronized(lock) {
            lastSampleCount = xs.size
            if (xs.size < 20) return 0.0
            return sqrt(variance(xs) + variance(ys) + variance(zs))
        }
    }

    private fun variance(l: List<Float>): Double {
        val m = l.average()
        return l.sumOf { (it - m) * (it - m) } / l.size
    }

    @Suppress("DEPRECATION")
    private fun play(effect: VibrationEffect) {
        val attrs = usage.toAudioAttributes()
        if (attrs != null) vibrator.vibrate(effect, attrs) else vibrator.vibrate(effect)
    }

    private fun amplitudeEffect(level: Float): VibrationEffect =
        VibrationEffect.createOneShot(800L, (level * 255).toInt().coerceIn(1, 255))

    private fun dutyEffect(level: Float): VibrationEffect {
        if (level >= 0.99f) return VibrationEffect.createOneShot(800L, 255)
        val periodMs = 20L
        val onMs = (periodMs * level).toLong().coerceIn(3L, periodMs - 3L)
        val cycles = 40
        val timings = LongArray(cycles * 2) { if (it % 2 == 0) onMs else periodMs - onMs }
        val amps = IntArray(cycles * 2) { if (it % 2 == 0) 255 else 0 }
        return VibrationEffect.createWaveform(timings, amps, -1)
    }

    private class Score(val ok: Boolean, val separation: Double, val values: List<Double>)

    private fun score(raw: List<Double>, baseline: Double): Score {
        val a = raw.map { (it - baseline).coerceAtLeast(0.0) }
        val mono = a[0] < a[1] * 0.95 && a[1] < a[2] * 0.95
        val sep = if (a[2] > 0.0) (a[2] - a[0]) / a[2] else 0.0
        return Score(mono && sep >= 0.3, sep, a)
    }

    private fun fmt(v: List<Double>) = v.joinToString(" / ") { String.format("%.3f", it) }

    private fun decide(baseline: Double, amp: List<Double>, duty: List<Double>): Result {
        val measurable = amp[2] > baseline * 1.5 + 0.01 || duty[2] > baseline * 1.5 + 0.01
        val header = "Mérés (rezgési energia, 15% / 50% / 100%): alapzaj ${String.format("%.3f", baseline)}; " +
            "amplitúdó ${fmt(score(amp, baseline).values)}; kitöltési tényező ${fmt(score(duty, baseline).values)}; " +
            "minták száma ablakonként: $lastSampleCount. "
        if (!measurable) {
            return Result(
                null,
                header + "A rezgés nem volt mérhető: a telefon feküdjön mozdulatlanul, kemény felületen, és ne legyen a rezgés kikapcsolva a rendszerben."
            )
        }
        val a = score(amp, baseline)
        val d = score(duty, baseline)
        return when {
            a.ok && (!d.ok || a.separation >= d.separation) ->
                Result(VibrationSettings.IntensityMethod.AMPLITUDE, header + "Eredmény: az amplitúdó mérhetően követi a szintet, ezt választottam.")
            d.ok ->
                Result(VibrationSettings.IntensityMethod.DUTY_CYCLE, header + "Eredmény: az amplitúdó nem változtatott mérhetően, a kitöltési tényező igen, ezt választottam.")
            else ->
                Result(null, header + "Eredmény: egyik módszer sem okozott mérhető különbséget. A rendszer Rezgés erőssége beállítása vagy a felhasználási osztály felülírhatja; próbálj másik osztályt, és ellenőrizd a telefon beállításait.")
        }
    }
}
