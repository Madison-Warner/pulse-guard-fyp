package com.example.pulseguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

@Composable
fun HrDashboard(state: HrUiState) {
    val hrHistory = remember { mutableStateListOf<Int>() }

    LaunchedEffect(state.filteredBpm) {
        if (state.filteredBpm > 0) {
            hrHistory.add(state.filteredBpm)

            // Keep only the latest 30 points
            if (hrHistory.size > 30) {
                hrHistory.removeAt(0)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Live Heart Rate Graph",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        HeartRateGraph(
            values = hrHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )
    }
}

@Composable
fun HeartRateGraph(
    values: List<Int>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas

        val maxValue = (values.maxOrNull() ?: 100) + 5
        val minValue = (values.minOrNull() ?: 40) - 5
        val range = (maxValue - minValue).coerceAtLeast(1)

        val widthStep = size.width / (values.size - 1)

        val path = Path()

        values.forEachIndexed { index, value ->
            val x = index * widthStep
            val normalized = (value - minValue).toFloat() / range.toFloat()
            val y = size.height - (normalized * size.height)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Draw graph line
        drawPath(
            path = path,
            color = Color(0xFF1976D2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
        )

        // Optional horizontal guide lines
        val guideLines = 4
        repeat(guideLines) { i ->
            val y = size.height * i / guideLines
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }
    }
}

fun eventLabel(eventCode: Int): String {
    return when (eventCode) {
        1 -> "Tachycardia detected"
        2 -> "Bradycardia detected"
        else -> "Normal"
    }
}