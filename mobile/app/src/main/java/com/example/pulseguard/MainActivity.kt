package com.example.pulseguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.pulseguard.auth.*
import com.example.pulseguard.comms.*
import com.example.pulseguard.emergency.*
import com.example.pulseguard.logs.*
import com.example.pulseguard.model.*
import kotlinx.coroutines.launch



class MainActivity : ComponentActivity() {
    private val authManager = AuthManager()
    private val contactRepository = EmergencyContactRepository()
    private lateinit var phoneToWatchSender: PhoneToWatchSender
    private var onSmsPermissionGranted: (()->Unit)? = null
    private val smsPermissionLauncher=
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if(granted) {
                onSmsPermissionGranted?.invoke()
            }
        }
    private lateinit var smsHelper: SmsHelper
    private lateinit var hrLogRepository: HrLogRepository

    private lateinit var hrLogAggregator: HrLogAggregator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        phoneToWatchSender = PhoneToWatchSender(this)

        smsHelper = SmsHelper(this)

        hrLogRepository = HrLogRepository()

        hrLogAggregator = HrLogAggregator(bucketDurationMillis = 20 * 60 * 1000L) //10_000L For testing purpose

        setContent {
            val hrState by HrLiveBus.state.collectAsState()

            LaunchedEffect(hrState.timestamp, hrState.filteredBpm) {
                Log.d("HrLogs", "Sample received ts=${hrState.timestamp}, bpm=${hrState.filteredBpm}")

                if (hrState.timestamp > 0 && hrState.filteredBpm > 0) {
                    val completedBucket = hrLogAggregator.addSample(
                        timestamp = hrState.timestamp,
                        bpm = hrState.filteredBpm
                    )

                    if (completedBucket != null) {
                        try {
                            Log.d("HrLogs", "Bucket ready: $completedBucket")
                            hrLogRepository.saveBucket(completedBucket)
                            hrLogRepository.deleteOlderThan24Hours()
                            Log.d("HrLogs", "Bucket saved to Firestore")
                        } catch (t: Throwable) {
                            Log.e("HrLogs", "Failed to store HR log bucket", t)
                        }
                    }
                }
            }

            LaunchedEffect(hrState.escalationRequested, hrState.escalationHandled) {
                if (hrState.escalationRequested && !hrState.escalationHandled) {
                    val emergencyMessage =
                        "PulseGuard emergency alert: abnormal heart rate detected. Please check on me immediately."

                    sendEmergencySmsToContacts(
                        contactRepository = contactRepository,
                        message = emergencyMessage
                    )

                    val current = HrLiveBus.state.value
                    HrLiveBus.post(
                        current.copy(
                            alertActive = false,
                            escalationHandled = true,
                            escalationRequested = false,
                            alertMessage = "Emergency SMS sent"
                        )
                    )
                }
            }

            var loggedIn by remember { mutableStateOf(authManager.currentUser() != null)}

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                if (loggedIn) {
                    if (hrState.alertActive) {
                        MobileAlertScreen(
                            message = hrState.alertMessage,
                            onDismiss = {
                                val current = HrLiveBus.state.value
                                HrLiveBus.post(
                                    current.copy(
                                        alertActive = false,
                                        alertMessage = ""
                                    )
                                )

                                lifecycleScope.launch {
                                    phoneToWatchSender.send(
                                        """{"type":"alert_cancelled","timestamp":${System.currentTimeMillis()}}"""
                                    )
                                }
                            },
                            onSendHelpNow = {
                                val current = HrLiveBus.state.value

                                HrLiveBus.post(
                                    current.copy(
                                        alertActive = false,
                                        alertMessage = "Emergency response sent",
                                        escalationRequested = true
                                    )
                                )

                                lifecycleScope.launch {
                                    phoneToWatchSender.send(
                                        """{"type":"alert_escalate","timestamp":${System.currentTimeMillis()}}"""
                                    )
                                }
                            }
                        )
                    } else {
                        HrDashboard(
                            state = hrState,
                            email = authManager.currentUser()?.email ?: "Unknown user",
                            onLogout = {
                                authManager.signOut()
                                loggedIn = false
                            },
                            contactRepository = contactRepository
                        )
                    }

                } else {
                    LoginScreen(
                        onLoginSuccess = { loggedIn = true },
                        authManager = authManager
                    )
                }
            }
        }
    }

    private fun ensureSmsPermissionThen(onGranted: ()-> Unit){
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if(granted) {
            onGranted()
        } else {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    private fun sendEmergencySmsToContacts(
        contactRepository: EmergencyContactRepository,
        message: String
    ){
        ensureSmsPermissionThen {
            lifecycleScope.launch {
                try {
                    val contacts = contactRepository.getContacts()

                    contacts.forEach { contact -> smsHelper.sendSms(contact.phoneNumber, message) }
                } catch(t: Throwable) {
                    Log.e("MainActivity", "Failed to send emergency SMS", t)
                }
            }
        }
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

    val backgroundColor = Color(0xFFF4F7F6)
    val surfaceColor = Color.White
    val primaryColor = Color(0xFF2F6F6D)
    val mutedText = Color(0xFF5F6F6D)
    val titleText = Color(0xFF223333)
    val borderColor = Color(0xFFD7E2DF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = "PulseGuard",
                style = MaterialTheme.typography.headlineMedium,
                color = titleText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Monitor heart rate and reach help faster.",
                style = MaterialTheme.typography.bodyMedium,
                color = mutedText
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Sign In",
                    style = MaterialTheme.typography.titleLarge,
                    color = titleText
                )

                Text(
                    text = "Use your existing PulseGuard account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedText
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = primaryColor,
                        cursorColor = primaryColor
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = primaryColor,
                        cursorColor = primaryColor
                    )
                )

                Button(
                    onClick = {
                        scope.launch {
                            status = "Signing in..."
                            val result = authManager.signIn(email.trim(), password)
                            if (result.isSuccess) {
                                status = "Login successful"
                                onLoginSuccess()
                            } else {
                                status = result.exceptionOrNull()?.message ?: "Login failed"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = Color.White
                    )
                ) {
                    Text("Login")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Create Account",
                    style = MaterialTheme.typography.titleLarge,
                    color = titleText
                )

                Text(
                    text = "New here? Use the same details above to create your account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedText
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFEAF1EF),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Enter your email and password above, then tap Create Account.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedText
                    )
                }

                OutlinedButton(
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = primaryColor
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = SolidColor(primaryColor)
                    )
                ) {
                    Text("Create Account")
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                color = mutedText,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
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
            if (hrHistory.size > 30) {
                hrHistory.removeAt(0)
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7F6))
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Welcome back",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF223333)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF5F6F6D)
                )
            }

            TextButton(onClick = onLogout) {
                Text(
                    "Logout",
                    color = Color(0xFF2F6F6D)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (state.connected) "Watch connected" else "Waiting for watch data...",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF5F6F6D)
                )

                HorizontalDivider(
                    color = Color(0xFFE3EBE8),
                    thickness = 1.dp
                )

                Text(
                    text = "BPM",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF5F6F6D)
                )

                Text(
                    text = if (state.filteredBpm > 0) "${state.filteredBpm}" else "--",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color(0xFF223333)
                )

                Text(
                    text = "Status: ${eventLabel(state.eventCode)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = when (eventLabel(state.eventCode)) {
                        "Normal" -> Color(0xFF3E7C66)
                        "Warning" -> Color(0xFFB9852F)
                        "Danger" -> Color(0xFFB85042)
                        else -> Color(0xFF5F6F6D)
                    }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Live Heart Rate Graph",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF223333)
                )

                Spacer(modifier = Modifier.height(12.dp))

                HeartRateGraph(
                    values = hrHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                EmergencyContactSection(contactRepository = contactRepository)
            }
        }
    }
}

@Composable
fun HeartRateGraph(
    values: List<Int>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(
                color = Color(0xFFF8FBFA),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(8.dp)
    ) {
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

            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        repeat(4) { i ->
            val y = size.height * i / 4
            drawLine(
                color = Color(0xFFDCE8E5),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        drawPath(
            path = path,
            color = Color(0xFF2F6F6D),
            style = Stroke(width = 4f)
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
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFF223333)
    )

    Spacer(modifier = Modifier.height(14.dp))

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Contact Name") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Phone Number") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2F6F6D),
            contentColor = Color.White
        )
    ) {
        Text("Add Emergency Contact")
    }

    if (status.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF5F6F6D)
        )
    }

    if (contacts.isNotEmpty()) {
        Spacer(modifier = Modifier.height(18.dp))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        }
    }
}

@Composable
fun ContactRow(
    contact: EmergencyContact,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFEAF1EF),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF223333)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF5F6F6D)
                )
            }

            OutlinedButton(
                onClick = onDelete,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF2F6F6D)
                )
            ) {
                Text("Delete")
            }
        }
    }
}

@Composable
fun MobileAlertScreen(
    message: String,
    onDismiss: () -> Unit,
    onSendHelpNow: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF2B0000)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "EMERGENCY ALERT",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Red
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I'm OK - Cancel Alert")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSendHelpNow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Help Now")
            }
        }
    }
}