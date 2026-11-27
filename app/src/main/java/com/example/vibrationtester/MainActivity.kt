package com.example.vibrationtester

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.CheckBox
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var settings: VibrationSettings
    private lateinit var chkKeepAlive: CheckBox

    // Android 13+ (API 33) requires this runtime permission for the foreground
    // service's notification to actually be shown to the user. The service
    // itself still runs even if this is denied; only the notification is hidden.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = VibrationSettings(this)

        val btnConsistent = findViewById<Button>(R.id.btnConsistent)
        val btnPulsing = findViewById<Button>(R.id.btnPulsing)
        val btnInconsistent = findViewById<Button>(R.id.btnInconsistent)
        val btnVacuum = findViewById<Button>(R.id.btnVacuum)
        val btnTrulyRandom = findViewById<Button>(R.id.btnTrulyRandom)
        val btnHammer = findViewById<Button>(R.id.btnHammer)
        val btnShortLong = findViewById<Button>(R.id.btnShortLong)
        val btnRampUp = findViewById<Button>(R.id.btnRampUp)
        val btnRampDown = findViewById<Button>(R.id.btnRampDown)
        val btnWave = findViewById<Button>(R.id.btnWave)
        val btnStepped = findViewById<Button>(R.id.btnStepped)
        val btnSuddenSwitch = findViewById<Button>(R.id.btnSuddenSwitch)
        val btnBurstSeries = findViewById<Button>(R.id.btnBurstSeries)
        val btnRandomTiming = findViewById<Button>(R.id.btnRandomTiming)
        val btnRandomIntensity = findViewById<Button>(R.id.btnRandomIntensity)
        val btnRandomTimingIntensity = findViewById<Button>(R.id.btnRandomTimingIntensity)
        val btnSine = findViewById<Button>(R.id.btnSine)
        val btnSquareWave = findViewById<Button>(R.id.btnSquareWave)
        val btnSawtooth = findViewById<Button>(R.id.btnSawtooth)
        val btnTriangle = findViewById<Button>(R.id.btnTriangle)
        val btnDualRhythm = findViewById<Button>(R.id.btnDualRhythm)
        val btnMotionResponsive = findViewById<Button>(R.id.btnMotionResponsive)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
        chkKeepAlive = findViewById(R.id.chkKeepAlive)

        chkKeepAlive.isChecked = settings.keepAliveAfterLock
        chkKeepAlive.setOnCheckedChangeListener { _, isChecked ->
            settings.keepAliveAfterLock = isChecked
            if (isChecked) {
                ensureNotificationPermission()
                announceForAccessibility(getString(R.string.announce_keep_alive_on))
            } else {
                announceForAccessibility(getString(R.string.announce_keep_alive_off))
            }
        }

        btnConsistent.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_CONSISTENT, R.string.announce_consistent)
        }
        btnPulsing.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_PULSING, R.string.announce_pulsing)
        }
        btnInconsistent.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_INCONSISTENT, R.string.announce_inconsistent)
        }
        btnVacuum.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_VACUUM, R.string.announce_vacuum)
        }
        btnTrulyRandom.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_TRULY_RANDOM, R.string.announce_truly_random)
        }
        btnHammer.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_HAMMER, R.string.announce_hammer)
        }
        btnShortLong.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_SHORT_LONG, R.string.announce_short_long)
        }
        btnRampUp.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_RAMP_UP, R.string.announce_ramp_up)
        }
        btnRampDown.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_RAMP_DOWN, R.string.announce_ramp_down)
        }
        btnWave.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_WAVE, R.string.announce_wave)
        }
        btnStepped.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_STEPPED_STRENGTHENING, R.string.announce_stepped)
        }
        btnSuddenSwitch.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_SUDDEN_SWITCH, R.string.announce_sudden_switch)
        }
        btnBurstSeries.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_BURST_SERIES, R.string.announce_burst_series)
        }
        btnRandomTiming.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_RANDOM_TIMING, R.string.announce_random_timing)
        }
        btnRandomIntensity.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_RANDOM_INTENSITY, R.string.announce_random_intensity)
        }
        btnRandomTimingIntensity.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_RANDOM_TIMING_INTENSITY, R.string.announce_random_timing_intensity)
        }
        btnSine.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_SINE, R.string.announce_sine)
        }
        btnSquareWave.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_SQUARE_WAVE, R.string.announce_square_wave)
        }
        btnSawtooth.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_SAWTOOTH, R.string.announce_sawtooth)
        }
        btnTriangle.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_TRIANGLE, R.string.announce_triangle)
        }
        btnDualRhythm.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_DUAL_RHYTHM, R.string.announce_dual_rhythm)
        }
        btnMotionResponsive.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_MOTION_RESPONSIVE, R.string.announce_motion_responsive)
        }
        btnStop.setOnClickListener {
            stopMode()
            announceForAccessibility(getString(R.string.announce_stop))
        }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    /**
     * Kezeli egy mód-gomb megnyomását, figyelembe véve a "gomb ismételt
     * megnyomása újraindítja-e a rezgést" beállítást: ha ki van kapcsolva, és
     * a most megnyomott gomb módja már fut, a megnyomás nem csinál semmit
     * (nem indítja újra a rezgést), csak egy rövid visszajelzést ad.
     */
    private fun onModeButtonPressed(mode: Int, announceRes: Int) {
        if (!settings.restartOnRepeatPress && VibrationService.currentActiveMode == mode) {
            announceForAccessibility(getString(R.string.announce_already_running))
            return
        }
        startMode(mode)
        announceForAccessibility(getString(announceRes))
    }

    private fun startMode(mode: Int) {
        val keepAlive = chkKeepAlive.isChecked
        val intent = Intent(this, VibrationService::class.java).apply {
            action = VibrationService.ACTION_START
            putExtra(VibrationService.EXTRA_MODE, mode)
            putExtra(VibrationService.EXTRA_KEEP_ALIVE, keepAlive)
        }
        if (keepAlive) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMode() {
        val intent = Intent(this, VibrationService::class.java).apply {
            action = VibrationService.ACTION_STOP
        }
        startService(intent)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun announceForAccessibility(text: String) {
        val accessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (accessibilityManager.isEnabled) {
            window.decorView.announceForAccessibility(text)
        }
    }
}
