package com.example.pulseguard.presentation.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.pulseguard.service.HrForegroundService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private fun requiredHrPermission(): String = Manifest.permission.BODY_SENSORS


class MainActivity : ComponentActivity() {

    private var onPermissionGranted: (() -> Unit)? = null

    private val sensorsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) onPermissionGranted?.invoke()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isRunning by remember { mutableStateOf(false) }
            var bpm by remember { mutableStateOf<Int?>(null) }

            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action == HrForegroundService.ACTION_HR_UPDATE){
                            isRunning = intent.getBooleanExtra(
                                HrForegroundService.EXTRA_RUNNING,
                                false
                            )
                            if (intent.hasExtra(HrForegroundService.EXTRA_BPM)) {
                                val value = intent.getIntExtra(HrForegroundService.EXTRA_BPM, -1)
                                bpm = value.takeIf { it > 0 }
                            }
                        }
                    }
                }
                val filter = IntentFilter(HrForegroundService.ACTION_HR_UPDATE)

                val flags = if (Build.VERSION.SDK_INT >= 33) {
                    Context.RECEIVER_NOT_EXPORTED
                } else {
                    0
                }

                registerReceiver(receiver, filter, flags)
                onDispose { unregisterReceiver(receiver) }
            }

            MaterialTheme{
                PulseGuardScreen(
                    isRunning = isRunning,
                    bpm = bpm,
                    onStart = { ensureSensorsPermissionThen { startHrService() } },
                    onStop = { stopHrService() }
                )
            }
        }
    }

    private fun ensureSensorsPermissionThen(onGranted: () -> Unit) {
        onPermissionGranted = onGranted

        val perm = requiredHrPermission()
        val granted = ContextCompat.checkSelfPermission(this,perm) == PackageManager.PERMISSION_GRANTED

        Log.d("MainActivity", "Using permission: $perm")
        Log.d("MainActivity", "Granted? $granted")

        if (granted) onGranted() else sensorsPermissionLauncher.launch(perm)
    }

    private fun startHrService() {
        val intent = Intent(this, HrForegroundService::class.java)
        startForegroundService(this, intent)
    }


    private fun stopHrService() {
        val intent = Intent(this, HrForegroundService::class.java).apply {
            action = HrForegroundService.ACTION_STOP
        }
        startService(intent)
    }
}

@Composable
private fun PulseGuardScreen(
    isRunning: Boolean,
    bpm: Int?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (isRunning) "Service: RUNNING" else "Service: STOPPED"
        )
        Text(
            text = "BPM: ${bpm ?: "--"}",
            modifier = Modifier.padding(top = 6.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, enabled = !isRunning) { Text("Start") }
            Button(onClick = onStop, enabled = isRunning) { Text("Stop")}
        }
    }
}


