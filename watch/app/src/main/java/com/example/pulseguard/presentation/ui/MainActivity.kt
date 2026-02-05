package com.example.pulseguard.presentation.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import com.example.pulseguard.service.HrForegroundService

private fun requireHrPermission(): String {
    // Android 16 / "Baklava" and above
    return if (Build.VERSION.SDK_INT >= 36) {
        "android.permission.health.READ_HEART_RATE"
    } else{
        Manifest.permission.BODY_SENSORS
    }
}

class MainActivity : ComponentActivity() {

    private val sensorsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startHrService()
            } else {
                // Sprint 0: permission denied → do nothing / log
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureSensorsPermissionThenStart()
    }

    private fun ensureSensorsPermissionThenStart() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startHrService()
        } else {
            sensorsPermissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        }
    }

    private fun startHrService() {
        val intent = Intent(this, HrForegroundService::class.java)
        startForegroundService(this, intent)
    }

    // Call this later from a button / test action
    private fun stopHrService() {
        val intent = Intent(this, HrForegroundService::class.java).apply {
            action = HrForegroundService.ACTION_STOP
        }
        startService(intent)
    }
}

