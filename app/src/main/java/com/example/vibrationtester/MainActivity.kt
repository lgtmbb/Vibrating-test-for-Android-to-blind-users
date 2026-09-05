package com.example.vibrationtester

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
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

    private lateinit var prefs: SharedPreferences
    private lateinit var chkKeepAlive: CheckBox

    // Android 13+ (API 33) requires this runtime permission for the foreground
    // service's notification to actually be shown to the user. The service
    // itself still runs even if this is denied; only the notification is hidden.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val btnConsistent = findViewById<Button>(R.id.btnConsistent)
        val btnPulsing = findViewById<Button>(R.id.btnPulsing)
        val btnUnpredictable = findViewById<Button>(R.id.btnUnpredictable)
        val btnVacuum = findViewById<Button>(R.id.btnVacuum)
        val btnStop = findViewById<Button>(R.id.btnStop)
        chkKeepAlive = findViewById(R.id.chkKeepAlive)

        chkKeepAlive.isChecked = prefs.getBoolean(PREF_KEEP_ALIVE, false)
        chkKeepAlive.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_KEEP_ALIVE, isChecked).apply()
            if (isChecked) {
                ensureNotificationPermission()
                announceForAccessibility(getString(R.string.announce_keep_alive_on))
            } else {
                announceForAccessibility(getString(R.string.announce_keep_alive_off))
            }
        }

        btnConsistent.setOnClickListener {
            startMode(VibrationService.MODE_CONSISTENT)
            announceForAccessibility(getString(R.string.announce_consistent))
        }

        btnPulsing.setOnClickListener {
            startMode(VibrationService.MODE_PULSING)
            announceForAccessibility(getString(R.string.announce_pulsing))
        }

        btnUnpredictable.setOnClickListener {
            startMode(VibrationService.MODE_UNPREDICTABLE)
            announceForAccessibility(getString(R.string.announce_unpredictable))
        }

        btnVacuum.setOnClickListener {
            startMode(VibrationService.MODE_VACUUM)
            announceForAccessibility(getString(R.string.announce_vacuum))
        }

        btnStop.setOnClickListener {
            stopMode()
            announceForAccessibility(getString(R.string.announce_stop))
        }
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

    companion object {
        private const val PREFS_NAME = "vibration_tester_prefs"
        private const val PREF_KEEP_ALIVE = "keep_alive_after_lock"
    }
}
