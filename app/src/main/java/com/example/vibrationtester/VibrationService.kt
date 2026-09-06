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
        const val MODE_UNPREDICTABLE = 2
        const val MODE_VACUUM = 3

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
                MODE_UNPREDICTABLE -> runUnpredictable()
                MODE_VACUUM -> runVacuum()
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

    // 3. Unpredictable Mode: a beállított duration/pause körül véletlenszerűen
    // szór, a "Kiszámíthatatlanság mértéke" (0-100%) csúszka által vezérelve.
    private suspend fun CoroutineScope.runUnpredictable() {
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
        MODE_UNPREDICTABLE -> R.string.mode_unpredictable_short
        MODE_VACUUM -> R.string.mode_vacuum_short
        else -> R.string.notification_title
    }
}
