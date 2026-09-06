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
        val btnUnpredictable = findViewById<Button>(R.id.btnUnpredictable)
        val btnVacuum = findViewById<Button>(R.id.btnVacuum)
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
        btnUnpredictable.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_UNPREDICTABLE, R.string.announce_unpredictable)
        }
        btnVacuum.setOnClickListener {
            onModeButtonPressed(VibrationService.MODE_VACUUM, R.string.announce_vacuum)
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
