package com.example.pulseguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pulseguard.comms.HrLiveBus
import com.example.pulseguard.comms.HrUiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val hrState by HrLiveBus.state.collectAsState()

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                HrDashboard(hrState)
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun HrDashboard(state: HrUiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = if (state.connected) "Watch Connected" else "Waiting for watch data...",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Current BPM: ${if (state.rawBpm > 0) state.rawBpm else "--"}",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Filtered BPM: ${if (state.filteredBpm > 0) state.filteredBpm else "--"}",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Status: ${eventLabel(state.eventCode)}",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Timestamp: ${if (state.timestamp > 0) state.timestamp.toString() else "--"}",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

fun eventLabel(eventCode: Int): String {
    return when (eventCode) {
        1 -> "Tachycardia detected"
        2 -> "Bradycardia detected"
        else -> "Normal"
    }
}
