package com.example.vibrationtester

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Runs the vibration patterns outside of the Activity's lifecycle.
 *
 * When started with EXTRA_KEEP_ALIVE = true, the service promotes itself to a
 * foreground service (with a persistent notification) using the "specialUse"
 * foreground service type introduced in Android 14. That keeps the process,
 * and therefore the vibration pattern loop, alive after the screen is locked
 * or the app is swiped away from Recents. When EXTRA_KEEP_ALIVE = false, the
 * service runs as an ordinary background service, which the system is free
 * to stop shortly after the app leaves the foreground (this mirrors the
 * previous, less reliable behaviour on purpose, so the checkbox has a real
 * effect the user can feel).
 *
 * All duration/pause/amplitude/effect-type parameters are read from
 * VibrationSettings at the moment a pattern starts, so changes made in
 * SettingsActivity take effect the next time a mode button is pressed.
 */
class VibrationService : Service() {

    companion object {
        const val ACTION_START = "com.example.vibrationtester.action.START"
        const val ACTION_STOP = "com.example.vibrationtester.action.STOP"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_KEEP_ALIVE = "extra_keep_alive"

        const val MODE_CONSISTENT = 0
        const val MODE_PULSING = 1
        const val MODE_INCONSISTENT = 2
        const val MODE_VACUUM = 3
        const val MODE_TRULY_RANDOM = 4
        const val MODE_HAMMER = 5
        const val MODE_SHORT_LONG = 6
        const val MODE_RAMP_UP = 7
        const val MODE_RAMP_DOWN = 8
        const val MODE_WAVE = 9

        private const val CHANNEL_ID = "vibration_service_channel"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "VibrationTester:VibrationWakeLock"
        // Safety cap so a forgotten notification can't hold a wake lock forever.
        private const val WAKE_LOCK_TIMEOUT_MS = 30L * 60L * 1000L

        // Melyik mód fut éppen (null = nincs). A MainActivity ezt olvassa ki,
        // hogy el tudja dönteni: egy gombnyomás új indítás-e, vagy egy már
        // aktív mód ismételt (esetleg szándéktalan, dupla) megnyomása.
        @Volatile
        var currentActiveMode: Int? = null
            private set
    }

    private lateinit var vibrator: Vibrator
    private lateinit var settings: VibrationSettings
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var patternJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isPromotedToForeground = false

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        settings = VibrationSettings(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val mode = intent.getIntExtra(EXTRA_MODE, MODE_CONSISTENT)
                val keepAlive = intent.getBooleanExtra(EXTRA_KEEP_ALIVE, false)
                if (keepAlive) {
                    promoteToForeground(mode)
                } else {
                    demoteFromForeground()
                }
                currentActiveMode = mode
                startPattern(mode)
            }
            ACTION_STOP -> {
                stopPattern()
                demoteFromForeground()
                currentActiveMode = null
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPattern()
        demoteFromForeground()
        currentActiveMode = null
        scope.cancel()
        super.onDestroy()
    }

    // --- Foreground promotion -------------------------------------------------

    private fun promoteToForeground(mode: Int) {
        createNotificationChannel()

        val stopIntent = Intent(this, VibrationService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(modeLabelRes(mode)))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.stop_button), stopPendingIntent)
            .build()

        // ServiceCompat dispatches to the right startForeground() overload for
        // the running OS version on its own (the type is ignored pre-Q, and
        // required from Android 14 onward), so no manual SDK_INT branch is needed.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        isPromotedToForeground = true
        acquireWakeLock()
    }

    private fun demoteFromForeground() {
        if (isPromotedToForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isPromotedToForeground = false
        }
        releaseWakeLock()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }
        }
    }

    // --- Wake lock --------------------------------------------------------
    // A foreground service alone does not guarantee precise delay() timing
    // once the screen is off; a partial wake lock keeps the CPU awake so the
    // pattern loop keeps its timing.

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // --- Vibration patterns -------------------------------------------------

    private fun startPattern(mode: Int) {
        stopPattern()
        patternJob = scope.launch {
            when (mode) {
                MODE_CONSISTENT -> runConsistent()
                MODE_PULSING -> runPulsing()
                MODE_INCONSISTENT -> runInconsistent()
                MODE_VACUUM -> runVacuum()
                MODE_TRULY_RANDOM -> runTrulyRandom()
                MODE_HAMMER -> runHammer()
                MODE_SHORT_LONG -> runShortLong()
                MODE_RAMP_UP -> runRampUp()
                MODE_RAMP_DOWN -> runRampDown()
                MODE_WAVE -> runWave()
            }
        }
    }

    private fun stopPattern() {
        patternJob?.cancel()
        patternJob = null
        vibrator.cancel()
    }

    // 1. Consistent Mode: folyamatos, gyakorlatilag szünetmentes rezgés.
    // A duration most már beállítható; a következő impulzus mindig 100ms-cel
    // az előző vége előtt indul, hogy az átfedés (és így a folytonosság
    // érzete) a hossztól függetlenül megmaradjon.
    private suspend fun CoroutineScope.runConsistent() {
        while (isActive) {
            val duration = settings.durationMs
            vibratePulse(duration, settings.effectiveAmplitude())
            delay((duration - 100).coerceAtLeast(50))
        }
    }

    // 2. Pulsing Mode: beállítható hosszúságú impulzus, beállítható szünettel.
    private suspend fun CoroutineScope.runPulsing() {
        while (isActive) {
            vibratePulse(settings.durationMs, settings.effectiveAmplitude())
            delay(settings.pauseMs)
        }
    }

    // 3. Inconsistent Mode (korábban "Kiszámíthatatlan"): a beállított
    // duration/pause körül mérsékelten véletlenszerűen szór, a
    // "Kiszámíthatatlanság mértéke" (0-100%) csúszka által vezérelve.
    private suspend fun CoroutineScope.runInconsistent() {
        while (isActive) {
            val variance = settings.unpredictabilityPercent / 100.0
            val duration = randomizedValue(settings.durationMs, variance)
            val pause = randomizedValue(settings.pauseMs, variance)
            vibratePulse(duration, settings.effectiveAmplitude())
            delay(duration + pause)
        }
    }

    // 4. Vacuum / Sucking Mode: fokozatosan erősödő rezgés, majd hirtelen
    // leállás. A lépésenkénti 40ms-es ütem adja a mód jellegét, ezért az
    // mindig fix marad; csak a ciklusok közti szünet jön a beállításokból.
    // Ez a mód mindig az egyéni hullámformát használja (nem az előre
    // definiált effektust vagy a primitíveket), mert a fokozatos erősödés a
    // lényege, amit azok nem tudnak kifejezni.
    private suspend fun CoroutineScope.runVacuum() {
        while (isActive) {
            for (amplitude in 50..255 step 51) {
                vibrateOneShot(40, amplitude)
                delay(40)
            }
            vibrator.cancel()
            delay(settings.pauseMs)
        }
    }

    private fun randomizedValue(base: Long, variance: Double): Long {
        if (variance <= 0.0) return base
        val factor = 1.0 + Random.nextDouble(-variance, variance)
        return (base * factor).toLong().coerceAtLeast(20L)
    }

    // 5. Truly Unpredictable Mode ("Kiszámíthatatlan mód"): minden lehetséges
    // paramétert - erősség, hossz, szünet, rezgéstípus (egyéni hullámforma /
    // előre definiált effektus / összetett primitívek / Android 16-os
    // envelope-effektus), sőt a lüktetések száma és BELSŐ SZERKEZETE (egy
    // sima, folytonos impulzus vagy egy apró mikro-lüktetésekből álló,
    // "recés" sorozat) egy-egy "eseményen" belül is - egymástól
    // függetlenül, széles tartományban véletlenszerűsít minden ciklusban.
    //
    // Négy különböző ESEMÉNY-STRATÉGIA (lásd RandomStrategy) között is
    // véletlenszerűen vált, hogy ne csak az egyes értékek, hanem a
    // véletlenszerűség JELLEGE (ritmusa, sűrűsége) is változzon: BURST
    // (1-3 gyors lüktetés), SPARSE (hosszú csend, majd egyetlen lüktetés),
    // ROLLING (sok apró lüktetésből álló, folyamatos "hullám"), PAIRED (két
    // lüktetés rövid, majd egy hosszabb szünettel). Enélkül, még ha minden
    // ÉRTÉK véletlenszerű is, a minta "alakja" (mindig azonos ritmusú
    // lüktetés-sorozatok) idővel felismerhetővé válna.
    //
    // ISMÉTLŐDÉS ELKERÜLÉSE: mind a stratégia-, mind az íz-, mind az előre
    // definiált effektus-választás a RepeatAvoidingPicker segítségével
    // történik, ami nem választja ki kétszer egymás után ugyanazt. Ez egy
    // dokumentált, más területeken (játék-hang középrétegek: Unity Audio
    // Random Container "Avoid Repeating Last" / "No Repeat" módja, RNGNeeds
    // könyvtár "Repeat Prevention" funkciója) bevett technika, mert a
    // matematikailag helyes, egyenletes eloszlású véletlen is "csomósnak"
    // és ismétlődőnek tűnhet az emberi észlelésnek, még akkor is, ha
    // statisztikailag helyes - lásd VIBRATION_API_RESEARCH.md.
    //
    // Fontos: az erősségszabályzás (hasAmplitudeControl) sok, főleg
    // olcsóbb, ERM-motoros (nem LRA) készüléken a hardver szintjén
    // egyáltalán nem létezik - ilyenkor a VibrationEffect.DEFAULT_AMPLITUDE
    // az egyetlen lehetséges érték, és ez NEM hiba, hanem a hardver valódi
    // korlátja (ezt a Beállítások "Eszköz képességei" szakasza is kiírja).
    // Hogy a mód ettől függetlenül is érezhetően változatos maradjon, a
    // mikro-lüktetés-sorozat (lásd lent) a hullámforma SZERKEZETÉT - nem az
    // erősségét - változtatja, ami erősségszabályzás nélkül is más
    // tapintási élményt ad, mint egy sima, folytonos impulzus.
    //
    // Szándékosan NEM használja a Beállításokban megadott
    // duration/pause/unpredictability/vibrationType értékeket - azok a
    // Következetlen módot vezérlik -, hogy a szórás mértéke ne legyen
    // felülről korlátozva, és az agy ne tudjon idővel mintát felismerni
    // benne. A rendelkezésre álló "ízek" közül csak azokat választja,
    // amelyeket a VibrationCapabilities ténylegesen támogatottnak (vagy
    // Android 10-en nem ellenőrizhetőnek) jelez az adott készüléken.
    private suspend fun CoroutineScope.runTrulyRandom() {
        val report = VibrationCapabilities.buildReport(vibrator)
        val hasAmplitude = report.hasAmplitudeControl
        val predefinedCandidates = report.predefinedEffects
            .filter { it.support != VibrationCapabilities.Support.NO }
            .map { it.id }
        val primitiveCandidates = report.primitives
            .filter { it.supported }
            .map { it.id }
        val envelopeAvailable = report.hasEnvelopeSupport

        val flavors = mutableListOf(TrulyRandomFlavor.CUSTOM)
        if (report.predefinedEffectsApiExists && predefinedCandidates.isNotEmpty()) {
            flavors.add(TrulyRandomFlavor.PREDEFINED)
        }
        if (report.compositionApiExists && primitiveCandidates.isNotEmpty()) {
            flavors.add(TrulyRandomFlavor.COMPOSITION)
        }
        if (envelopeAvailable) {
            flavors.add(TrulyRandomFlavor.ENVELOPE)
        }

        // Az utolsó 1 választást kizárva választ mindegyik picker - ennyi
        // egy 4 elem körüli listánál (stratégiák, ízek) már érzékelhetően
        // csökkenti az "egymás utáni ismétlődés" érzetét, anélkül hogy
        // hosszabb, mesterkéltebb ciklikusságot vinne be.
        val strategyPicker = RepeatAvoidingPicker(RandomStrategy.entries.toList(), historySize = 1)
        val flavorPicker = RepeatAvoidingPicker(flavors, historySize = 1)
        val predefinedPicker = if (predefinedCandidates.isNotEmpty()) {
            RepeatAvoidingPicker(predefinedCandidates, historySize = 1)
        } else {
            null
        }

        while (isActive) {
            when (strategyPicker.pick()) {
                RandomStrategy.BURST -> {
                    // 1-3 gyors lüktetés, apró résekkel, majd közepes szünet.
                    val burstSize = Random.nextInt(1, 4)
                    for (index in 0 until burstSize) {
                        delay(playOnePulse(flavorPicker, predefinedPicker, primitiveCandidates, hasAmplitude))
                        if (index < burstSize - 1) {
                            delay(Random.nextLong(10L, 150L))
                        }
                    }
                    delay(Random.nextLong(50L, 1500L))
                }
                RandomStrategy.SPARSE -> {
                    // Hosszú csend, majd egyetlen, "meglepetésszerű" lüktetés.
                    delay(Random.nextLong(1500L, 5000L))
                    delay(playOnePulse(flavorPicker, predefinedPicker, primitiveCandidates, hasAmplitude))
                }
                RandomStrategy.ROLLING -> {
                    // Sok apró lüktetésből álló, majdnem folyamatos "hullám".
                    val waveCount = Random.nextInt(4, 10)
                    repeat(waveCount) {
                        delay(playOnePulse(flavorPicker, predefinedPicker, primitiveCandidates, hasAmplitude))
                        delay(Random.nextLong(5L, 40L))
                    }
                    delay(Random.nextLong(200L, 2000L))
                }
                RandomStrategy.PAIRED -> {
                    // Két lüktetés rövid réssel ("kop-kop"), majd hosszabb szünet.
                    delay(playOnePulse(flavorPicker, predefinedPicker, primitiveCandidates, hasAmplitude))
                    delay(Random.nextLong(60L, 300L))
                    delay(playOnePulse(flavorPicker, predefinedPicker, primitiveCandidates, hasAmplitude))
                    delay(Random.nextLong(300L, 3000L))
                }
            }
        }
    }

    private enum class TrulyRandomFlavor { CUSTOM, PREDEFINED, COMPOSITION, ENVELOPE }

    // 6. Kalapács mód ("Hammer Mode"): a lehető legerősebb rezgés,
    // folyamatosan, de nagyon apró szünetekkel megszakítva - mint amikor
    // valaki ismételten lesújt egy kalapáccsal. Szándékosan NEM
    // véletlenszerűsíti az erősséget (ellentétben a Kiszámíthatatlan
    // móddal, aminek pont az volt a lényege) - itt az erősség mindig a
    // lehető legnagyobb, csak az "ütések" apró időzítése kap enyhe,
    // emberi jellegű ingadozást, hogy ne érződjön robotikusan
    // egyenletesnek.
    //
    // Amikor elérhető és a készülék jelzi a támogatását, az
    // EFFECT_HEAVY_CLICK előre definiált effektust használja: ezt kifejezetten
    // erős, hirtelen "ütés" érzetre tervezték, és gyakran a gyártó hangolja
    // az adott hardverre - hitelesebb "csattanást" ad, mint egy generikus,
    // egyenletes erősségű impulzus. Ha nem támogatott, egy maximális
    // erősségű, rövid impulzusra esik vissza.
    //
    // A 255-ös (legnagyobb) erősség biztonságosan kérhető erősségszabályzás
    // NÉLKÜLI hardveren is: a hivatalos Android dokumentáció szerint minden
    // nem nulla erősségérték automatikusan 100%-ra kerekítődik olyan
    // eszközön, ami nem támogatja a finomabb szabályozást - tehát itt,
    // ellentétben a többi móddal, nem kell előtte hasAmplitudeControl()-t
    // ellenőrizni (lásd VIBRATION_API_RESEARCH.md).
    private suspend fun CoroutineScope.runHammer() {
        val report = VibrationCapabilities.buildReport(vibrator)
        val useHeavyClick = report.predefinedEffectsApiExists &&
            report.predefinedEffects.any {
                it.id == VibrationEffect.EFFECT_HEAVY_CLICK && it.support != VibrationCapabilities.Support.NO
            }

        while (isActive) {
            if (useHeavyClick) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                delay(Random.nextLong(70L, 100L))
            } else {
                val duration = Random.nextLong(120L, 180L)
                vibrateOneShot(duration, 255)
                delay(duration)
            }
            // Nagyon apró szünet a következő "ütés" előtt - ez adja a
            // "megszakított folyamatosság", kalapácsütés-szerű érzetet.
            delay(Random.nextLong(25L, 60L))
        }
    }

    // --- Determinisztikus, ismétlődő mintázatok (7-10. mód) --------------
    //
    // A négy alábbi mód közös vonása, hogy - a Kiszámíthatatlan móddal és a
    // Kalapács móddal ellentétben - SZÁNDÉKOSAN nem véletlenszerűek: a
    // mintázat minden ismétlésnél pontosan ugyanaz. Ezért nem saját,
    // delay()-alapú ciklussal valósítják meg az ismétlést, hanem a natív
    // VibrationEffect.createWaveform(timings, amplitudes, repeat=0)
    // mechanizmust használják - ez egyetlen hívással létrehoz egy
    // hullámformát, amit maga a rendszer ismétel a 0. indextől a
    // végtelenségig, amíg vibrator.cancel() le nem állítja. Ez garantáltan
    // azonos időzítést ad minden körben, mert nem a mi coroutine-unk (ami
    // apró, valós idejű ütemezési ingadozásoknak van kitéve), hanem a
    // platform saját, natív rezgésütemezője hajtja végre.
    //
    // Mindegyik a Beállítások meglévő "Rezgés hossza" és/vagy "Szünet
    // hossza" értékét használja fel (nem vezet be új beállítást):
    // "Rövid-hosszú váltakozás" a hosszhoz és a szünethez, a rámpák és a
    // hullám a "Rezgés hossza" értéket a rámpa/ciklus teljes
    // időtartamaként. Ezt a Beállítások leírásai is jelzik.

    // 7. Rövid-hosszú váltakozás ("Short-Long Alternation"): fix mintázat -
    // rövid rezgés, majd hosszú rezgés -, ami minden ismétlésnél pontosan
    // ugyanazzal az időzítéssel fut.
    private suspend fun CoroutineScope.runShortLong() {
        val shortMs = settings.durationMs
        val longMs = (settings.durationMs * 3).coerceAtMost(VibrationSettings.MAX_DURATION_MS)
        val gapMs = settings.pauseMs.coerceAtLeast(20L)
        val timings = longArrayOf(shortMs, gapMs, longMs, gapMs)
        // A 255-ös erősség biztonságosan kérhető erősségszabályzás nélküli
        // hardveren is (100%-ra kerekítődik) - lásd VIBRATION_API_RESEARCH.md.
        val amplitudes = intArrayOf(255, 0, 255, 0)
        vibrateWaveform(timings, amplitudes, repeat = 0)
        while (isActive) {
            delay(500)
        }
    }

    // 8. Fokozatos erősödés ("Gradual Strengthening"): alacsony
    // intenzitásról indulva, a "Rezgés hossza" beállítás alatt folyamatosan
    // és fokozatosan erősödik a maximumig, majd újrakezdi.
    private suspend fun CoroutineScope.runRampUp() {
        val hasAmplitude = VibrationCapabilities.buildReport(vibrator).hasAmplitudeControl
        val (timings, amplitudes) = buildIntensityWaveform(settings.durationMs, hasAmplitude) { t ->
            0.1f + 0.9f * t
        }
        vibrateWaveform(timings, amplitudes, repeat = 0)
        while (isActive) {
            delay(500)
        }
    }

    // 9. Fokozatos gyengülés ("Gradual Weakening"): a Beállításokban
    // megadott erősségről (vagy ha nincs egyéni erősség beállítva,
    // maximumról) indulva fokozatosan csökken majdnem nulláig, majd
    // újrakezdi.
    private suspend fun CoroutineScope.runRampDown() {
        val hasAmplitude = VibrationCapabilities.buildReport(vibrator).hasAmplitudeControl
        val startAmplitude = settings.effectiveAmplitude().let {
            if (it == VibrationEffect.DEFAULT_AMPLITUDE) 255 else it
        }
        val startIntensity = startAmplitude / 255f
        val (timings, amplitudes) = buildIntensityWaveform(settings.durationMs, hasAmplitude) { t ->
            startIntensity * (1.0f - 0.95f * t)
        }
        vibrateWaveform(timings, amplitudes, repeat = 0)
        while (isActive) {
            delay(500)
        }
    }

    // 10. Hullámzó intenzitás ("Wave-like Intensity"): az erősség
    // folyamatosan, simán hullámzik fel-le, szinusz-görbe szerint, fix
    // ciklushosszal (a "Rezgés hossza" beállítás egy teljes ciklus hossza).
    private suspend fun CoroutineScope.runWave() {
        val hasAmplitude = VibrationCapabilities.buildReport(vibrator).hasAmplitudeControl
        val (timings, amplitudes) = buildIntensityWaveform(settings.durationMs, hasAmplitude) { t ->
            val normalized = (kotlin.math.sin(2.0 * Math.PI * t) + 1.0) / 2.0 // 0..1
            (0.15 + 0.85 * normalized).toFloat()
        }
        vibrateWaveform(timings, amplitudes, repeat = 0)
        while (isActive) {
            delay(500)
        }
    }

    /**
     * Egy hullámformát épít, amelyben az intenzitás az idő függvényében az
     * [intensityAt] függvény szerint alakul (0.0-1.0 tartomány, t=0..1 a
     * teljes hossz relatív pozíciója). Ha a hardver támogatja az
     * erősségszabályzást, közvetlenül az amplitúdót modulálja 30ms-es
     * lépésekkel - elég finom felbontás ahhoz, hogy simának érződjön. Ha
     * nem, az on/off arányt (duty cycle-t) modulálja ugyanazzal a
     * függvénnyel 80ms-es ciklusokban - ez erősségszabályzás nélkül is
     * érzékelhető erősödés/gyengülés/hullámzás benyomást kelt, ugyanazzal
     * az elvvel, amit a Kiszámíthatatlan mód mikro-lüktetés-sorozata is
     * használ (lásd VIBRATION_API_RESEARCH.md).
     */
    private fun buildIntensityWaveform(
        totalDurationMs: Long,
        hasAmplitude: Boolean,
        intensityAt: (Float) -> Float
    ): Pair<LongArray, IntArray> {
        return if (hasAmplitude) {
            val stepMs = 30L
            val stepCount = (totalDurationMs / stepMs).toInt().coerceAtLeast(4)
            val timings = LongArray(stepCount) { stepMs }
            val amplitudes = IntArray(stepCount) { i ->
                val t = i.toFloat() / (stepCount - 1).coerceAtLeast(1)
                (intensityAt(t).coerceIn(0f, 1f) * 254 + 1).toInt().coerceIn(1, 255)
            }
            timings to amplitudes
        } else {
            val cycleMs = 80L
            val cycleCount = (totalDurationMs / cycleMs).toInt().coerceAtLeast(4)
            val timings = LongArray(cycleCount * 2)
            val amplitudes = IntArray(cycleCount * 2)
            for (i in 0 until cycleCount) {
                val t = i.toFloat() / (cycleCount - 1).coerceAtLeast(1)
                val intensity = intensityAt(t).coerceIn(0f, 1f)
                val onMs = (8L + (cycleMs - 16L) * intensity).toLong().coerceIn(4L, cycleMs - 4L)
                val offMs = cycleMs - onMs
                timings[i * 2] = onMs
                timings[i * 2 + 1] = offMs
                amplitudes[i * 2] = 255
                amplitudes[i * 2 + 1] = 0
            }
            timings to amplitudes
        }
    }

    // A Kiszámíthatatlan mód négy különböző "eseményalakja" - lásd a
    // runTrulyRandom elején lévő magyarázatot.
    private enum class RandomStrategy { BURST, SPARSE, ROLLING, PAIRED }

    /**
     * Lejátszik egy lüktetést a flavorPicker által választott íz szerint, és
     * visszaadja a becsült időtartamát (ennyit kell a hívónak delay()-elnie,
     * mielőtt a következő lépés jönne).
     */
    private fun playOnePulse(
        flavorPicker: RepeatAvoidingPicker<TrulyRandomFlavor>,
        predefinedPicker: RepeatAvoidingPicker<Int>?,
        primitiveCandidates: List<Int>,
        hasAmplitude: Boolean
    ): Long {
        return when (flavorPicker.pick()) {
            TrulyRandomFlavor.CUSTOM -> {
                if (Random.nextBoolean()) {
                    // Sima, folytonos impulzus.
                    val duration = Random.nextLong(20L, 1500L)
                    val amplitude = if (hasAmplitude) Random.nextInt(1, 256) else VibrationEffect.DEFAULT_AMPLITUDE
                    vibrateOneShot(duration, amplitude)
                    duration
                } else {
                    // Mikro-lüktetés-sorozat: a hullámforma SZERKEZETÉT teszi
                    // véletlenszerűvé (hány rövid be/ki szakaszból áll, milyen
                    // hosszúak) - ez erősségszabályzás NÉLKÜL is más tapintási
                    // benyomást kelt, mint egy sima impulzus.
                    val segmentCount = Random.nextInt(2, 7)
                    val timings = LongArray(segmentCount * 2)
                    val amplitudes = IntArray(segmentCount * 2)
                    var total = 0L
                    for (i in 0 until segmentCount) {
                        val onMs = Random.nextLong(15L, 120L)
                        val offMs = Random.nextLong(10L, 100L)
                        timings[i * 2] = onMs
                        timings[i * 2 + 1] = offMs
                        amplitudes[i * 2] = if (hasAmplitude) Random.nextInt(1, 256) else 255
                        amplitudes[i * 2 + 1] = 0
                        total += onMs + offMs
                    }
                    vibrateWaveform(timings, amplitudes)
                    total
                }
            }
            TrulyRandomFlavor.PREDEFINED -> {
                val effectId = predefinedPicker!!.pick()
                vibrator.vibrate(VibrationEffect.createPredefined(effectId))
                Random.nextLong(60L, 400L)
            }
            TrulyRandomFlavor.COMPOSITION -> {
                val count = Random.nextInt(1, primitiveCandidates.size.coerceAtMost(3) + 1)
                val chosen = primitiveCandidates.shuffled().take(count)
                val composition = VibrationEffect.startComposition()
                chosen.forEachIndexed { i, primitiveId ->
                    val scale = Random.nextDouble(0.3, 1.0).toFloat()
                    val primitiveDelay = if (i == 0) 0 else Random.nextInt(0, 80)
                    composition.addPrimitive(primitiveId, scale, primitiveDelay)
                }
                vibrator.vibrate(composition.compose())
                Random.nextLong(80L, 400L)
            }
            TrulyRandomFlavor.ENVELOPE -> {
                // Android 16 (API 36): PWLE-alapú, folytonosan változó
                // intenzitású/élességű hullámforma - 1-2 köztes, véletlenszerű
                // vezérlőpont, majd a kötelező, nullára visszatérő záró pont.
                val builder = VibrationEffect.BasicEnvelopeBuilder()
                var total = 0L
                val pointCount = Random.nextInt(1, 3)
                repeat(pointCount) {
                    val intensity = Random.nextDouble(0.15, 1.0).toFloat()
                    val sharpness = Random.nextDouble(0.0, 1.0).toFloat()
                    val pointDuration = Random.nextLong(40L, 220L)
                    builder.addControlPoint(intensity, sharpness, pointDuration)
                    total += pointDuration
                }
                val closingSharpness = Random.nextDouble(0.0, 1.0).toFloat()
                val closingDuration = Random.nextLong(40L, 150L)
                builder.addControlPoint(0f, closingSharpness, closingDuration)
                total += closingDuration
                vibrator.vibrate(builder.build())
                total
            }
        }
    }

    private fun vibrateWaveform(timings: LongArray, amplitudes: IntArray, repeat: Int = -1) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, repeat))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, repeat)
        }
    }

    /**
     * Egyetlen rezgési impulzus lejátszása a beállított típus szerint:
     * egyéni hullámforma (duration+amplitude), előre definiált effektus,
     * vagy összetett primitívek. Ha a kiválasztott típus az adott
     * API-szinten vagy hardveren nem elérhető, csendben visszaesik az
     * egyéni hullámformára.
     */
    private fun vibratePulse(durationMs: Long, amplitude: Int) {
        when (settings.vibrationType) {
            VibrationSettings.VibrationType.PREDEFINED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(settings.predefinedEffectId))
                } else {
                    vibrateOneShot(durationMs, amplitude)
                }
            }
            VibrationSettings.VibrationType.COMPOSITION -> {
                val ids = settings.selectedPrimitiveIds
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ids.isNotEmpty()) {
                    val composition = VibrationEffect.startComposition()
                    ids.forEach { composition.addPrimitive(it) }
                    vibrator.vibrate(composition.compose())
                } else {
                    vibrateOneShot(durationMs, amplitude)
                }
            }
            VibrationSettings.VibrationType.CUSTOM -> vibrateOneShot(durationMs, amplitude)
        }
    }

    private fun vibrateOneShot(durationMs: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun modeLabelRes(mode: Int): Int = when (mode) {
        MODE_CONSISTENT -> R.string.mode_consistent_short
        MODE_PULSING -> R.string.mode_pulsing_short
        MODE_INCONSISTENT -> R.string.mode_inconsistent_short
        MODE_VACUUM -> R.string.mode_vacuum_short
        MODE_TRULY_RANDOM -> R.string.mode_truly_random_short
        MODE_HAMMER -> R.string.mode_hammer_short
        MODE_SHORT_LONG -> R.string.mode_short_long_short
        MODE_RAMP_UP -> R.string.mode_ramp_up_short
        MODE_RAMP_DOWN -> R.string.mode_ramp_down_short
        MODE_WAVE -> R.string.mode_wave_short
        else -> R.string.notification_title
    }
}

/**
 * Véletlenszerű választó, ami elkerüli az utolsó [historySize] választás
 * megismétlését - ez az "Avoid Repeating Last N" / "Repeat Prevention"
 * technika, amit pl. a Unity motor Audio Random Container-e ("Avoid
 * Repeating Last" beállítás) és a játék-hangokhoz készült RNGNeeds könyvtár
 * "Repeat Prevention" funkciója is használ (lásd VIBRATION_API_RESEARCH.md).
 * Az indoklás dokumentált jelenség: a matematikailag helyes, egyenletes
 * eloszlású véletlen az embereknek gyakran "csomósnak", ismétlődőnek tűnik,
 * miközben egy enyhén korlátozott - de nem ciklikus - változat inkább
 * megfelel annak, amit valaki "igazán véletlenszerűnek" érez.
 *
 * Ha a jelöltlista mérete nem elég nagy ahhoz, hogy [historySize] elemet ki
 * lehessen zárni és még maradjon választható elem, a korlátozás automatikusan
 * gyengül (lásd effectiveHistorySize) - egyetlen jelölt esetén nincs
 * elkerülés, hiszen nem is lenne mit választani helyette.
 */
private class RepeatAvoidingPicker<T>(private val candidates: List<T>, historySize: Int) {
    private val effectiveHistorySize = historySize.coerceIn(0, (candidates.size - 1).coerceAtLeast(0))
    private val history = ArrayDeque<T>()

    fun pick(): T {
        val pool = if (effectiveHistorySize > 0) {
            candidates.filterNot { it in history }
        } else {
            candidates
        }
        val chosen = (if (pool.isNotEmpty()) pool else candidates).random()
        if (effectiveHistorySize > 0) {
            history.addLast(chosen)
            while (history.size > effectiveHistorySize) {
                history.removeFirst()
            }
        }
        return chosen
    }
}

