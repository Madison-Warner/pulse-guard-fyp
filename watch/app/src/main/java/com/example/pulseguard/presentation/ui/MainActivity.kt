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

class MainActivity : ComponentActivity() {

    private val sensorsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startHrService()
            } else {
                // For Sprint 0: just log or show a message
                // You can’t start a health-type FGS without it on targetSdk 36
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
}
