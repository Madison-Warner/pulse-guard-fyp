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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.pulseguard.auth.AuthManager
import com.example.pulseguard.comms.HrLiveBus
import com.example.pulseguard.comms.HrUiState
import com.example.pulseguard.emergency.EmergencyContactRepository
import com.example.pulseguard.model.EmergencyContact
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val authManager = AuthManager()
    private val contactRepository = EmergencyContactRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val hrState by HrLiveBus.state.collectAsState()
            var loggedIn by remember { mutableStateOf(authManager.currentUser() != null)}

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                if (loggedIn) {
                    HrDashboard(
                        state = hrState,
                        email = authManager.currentUser()?.email ?: "Unknown user",
                        onLogout = {
                            authManager.signOut()
                            loggedIn = false
                        },
                        contactRepository = contactRepository
                    )
                } else {
                    LoginScreen(
                        onLoginSuccess = { loggedIn = true },
                        authManager = authManager

                    )
                }
            }
        }
    }
}

@Composable
fun HrDashboard(
    state: HrUiState,
    email: String,
    onLogout: () -> Unit,
    contactRepository: EmergencyContactRepository
) {
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
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Logged in as: $email",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onLogout) {
            Text("Logout")
        }

        Spacer(modifier = Modifier.height(24.dp))
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

        Spacer(modifier = Modifier.height(32.dp))

        EmergencyContactSection(contactRepository = contactRepository)
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

@Composable
fun LoginScreen(
    authManager: AuthManager,
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Please sign in") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("PulseGuard Login", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    status = "Signing in..."
                    val result = authManager.signIn(email.trim(), password)
                    if (result.isSuccess) {
                        status = "Login successful"
                        onLoginSuccess()
                    } else {
                        status = result.exceptionOrNull()?.message ?:"Login failed"
                    }
                }
            }
        ) {
            Text("Login")
        }

        Spacer(Modifier.fillMaxWidth())

        Button(
            onClick = {
                scope.launch {
                    status = "Creating account..."
                    val result = authManager.signUp(email.trim(), password)
                    if (result.isSuccess) {
                        status = "Account created"
                        onLoginSuccess()
                    } else {
                        status = result.exceptionOrNull()?.message ?: "Sign-up failed"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Account")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(status, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun EmergencyContactSection(contactRepository: EmergencyContactRepository) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val contacts = remember { mutableStateListOf<EmergencyContact>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            contacts.clear()
            contacts.addAll(contactRepository.getContacts())
        } catch (t: Throwable) {
            status = t.message ?: "Failed to load contacts"
        }
    }

    Text(
        text = "Emergency Contacts",
        style = MaterialTheme.typography.titleMedium
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Contact Name") }
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Phone Number") }
    )

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = {
            scope.launch {
                try {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        contactRepository.addContact(name.trim(), phone.trim())
                        contacts.clear()
                        contacts.addAll(contactRepository.getContacts())
                        name = ""
                        phone = ""
                        status = "Contact added"
                    }
                } catch (t: Throwable) {
                    status = t.message ?: "Failed to add contact"
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Add Emergency Contact")
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (status.isNotBlank()) {
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
    }

    Column {
        contacts.forEach { contact ->
            ContactRow(
                contact = contact,
                onDelete = {
                    scope.launch {
                        try {
                            contactRepository.deleteContact(contact.id)
                            contacts.clear()
                            contacts.addAll(contactRepository.getContacts())
                            status = "Contact deleted"
                        } catch (t: Throwable) {
                            status = t.message ?: "Failed to delete contact"
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ContactRow(
    contact: EmergencyContact,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = contact.name,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = contact.phoneNumber,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(6.dp))

        Button(onClick = onDelete) {
            Text("Delete")
        }
    }
}