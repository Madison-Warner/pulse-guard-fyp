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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableIntStateOf
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
            var alertActive by remember { mutableStateOf(false) }
            var countdown by remember { mutableIntStateOf(0) }

            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        when (intent.action) {
                            HrForegroundService.ACTION_HR_UPDATE -> {
                                isRunning = intent.getBooleanExtra(
                                    HrForegroundService.EXTRA_RUNNING,
                                    false
                                )

                                if (intent.hasExtra(HrForegroundService.EXTRA_BPM)) {
                                    val value = intent.getIntExtra(
                                        HrForegroundService.EXTRA_BPM,
                                        -1
                                    )
                                    bpm = value.takeIf { it > 0 }
                                }
                            }

                            HrForegroundService.ACTION_ALERT_UPDATE -> {
                                alertActive = intent.getBooleanExtra(
                                    HrForegroundService.EXTRA_ALERT_ACTIVE,
                                    false
                                )
                                countdown = intent.getIntExtra(
                                    HrForegroundService.EXTRA_COUNTDOWN,
                                    0
                                )
                            }
                        }
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(HrForegroundService.ACTION_HR_UPDATE)
                    addAction(HrForegroundService.ACTION_ALERT_UPDATE)
                }

                val flags = if (Build.VERSION.SDK_INT >= 33) {
                    Context.RECEIVER_NOT_EXPORTED
                } else {
                    0
                }

                registerReceiver(receiver, filter, flags)
                onDispose { unregisterReceiver(receiver) }
            }

            MaterialTheme{
                if (alertActive) {
                    AlertScreen(
                        countdown = countdown,
                        onCancel = { sendCancelAlertIntent() },
                        onSendNow = { sendHelpNowIntent() }
                    )
                } else {
                    PulseGuardScreen(
                        isRunning = isRunning,
                        bpm = bpm,
                        onStart = { ensureSensorsPermissionThen { startHrService() } },
                        onStop = { stopHrService() }
                    )
                }
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

    private fun sendCancelAlertIntent() {
        val intent = Intent(this, HrForegroundService::class.java).apply {
            action = HrForegroundService.ACTION_CANCEL_ALERT
        }
        startForegroundService(this, intent)
    }

    private fun sendHelpNowIntent() {
        val intent = Intent(this, HrForegroundService::class.java).apply {
            action = HrForegroundService.ACTION_SEND_HELP_NOW
        }
        startForegroundService(this, intent)
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onStart, enabled = !isRunning) {
                Text("Start")
            }
            Button(onClick = onStop, enabled = isRunning) {
                Text("Stop")
            }
        }
    }
}

@Composable
fun AlertScreen(
    countdown: Int,
    onCancel: () -> Unit,
    onSendNow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Emergency Alert",
            style = MaterialTheme.typography.title1
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Abnormal heart rate detected",
            style = MaterialTheme.typography.body1
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Countdown: $countdown",
            style = MaterialTheme.typography.title2
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I'm OK")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onSendNow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Help Now")
        }
    }
}


