package com.rough.snoremonitor.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rough.snoremonitor.data.AppPreferences
import com.rough.snoremonitor.dispatch.TelegramDispatcher
import com.rough.snoremonitor.scheduler.AlarmScheduler
import com.rough.snoremonitor.service.SnoreDetectionService
import com.rough.snoremonitor.service.TelegramRemoteService
import kotlinx.coroutines.launch

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val coroutineScope = rememberCoroutineScope()

    var botToken by remember { mutableStateOf(prefs.botToken) }
    var chatId by remember { mutableStateOf(prefs.chatId) }
    var sensitivity by remember { mutableFloatStateOf(prefs.sensitivityDb) }
    var isScheduled by remember { mutableStateOf(prefs.isScheduled) }
    var isRemoteEnabled by remember { mutableStateOf(prefs.isRemoteControlEnabled) }
    var testStatusMessage by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "🌙 Snore Monitor",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Personal sleep detection and audio snippet monitor.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (SnoreDetectionService.isServiceRunning) Color(0xFFE8F5E9) else Color(0xFFF3F4F6)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Current Status",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (SnoreDetectionService.isServiceRunning) {
                            "🟢 Active: Listening for sleep sounds now"
                        } else if (isScheduled) {
                            "⏰ Scheduled: Starts at ${String.format("%02d:%02d", prefs.scheduledHour, prefs.scheduledMinute)} IST nightly"
                        } else {
                            "⚪ Inactive: Set schedule or start manually below"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (SnoreDetectionService.isServiceRunning) Color(0xFF2E7D32) else Color(0xFF374151)
                    )
                }
            }

            // Telegram Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Telegram Bot Delivery",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Sends the 15-second snoring audio clip to your phone upstairs.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = botToken,
                        onValueChange = {
                            botToken = it
                            prefs.botToken = it
                        },
                        label = { Text("Bot Token (from @BotFather)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = chatId,
                        onValueChange = {
                            chatId = it
                            prefs.chatId = it
                        },
                        label = { Text("Your Chat ID (from @userinfobot)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            testStatusMessage = "Connecting to Telegram..."
                            coroutineScope.launch {
                                val result = TelegramDispatcher().sendTestMessage(botToken, chatId)
                                testStatusMessage = if (result.isSuccess) {
                                    "✅ Test message sent! Check your Telegram."
                                } else {
                                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                                    "❌ Error: $err\n\n📌 Quick Checklist:\n• Open your bot in Telegram and tap 'START'.\n• Ensure Bot Token format is numbers:letters (e.g. 712345:ABCdef).\n• Check phone internet connection."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send Test Ping to Upstairs Phone")
                    }

                    if (testStatusMessage.isNotBlank()) {
                        Text(
                            text = testStatusMessage,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (testStatusMessage.startsWith("✅")) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                        )
                    }
                }
            }

            // Remote Control Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRemoteEnabled) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📡 Telegram Remote Control",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Listen or check status from upstairs anytime",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isRemoteEnabled,
                            onCheckedChange = { enabled ->
                                isRemoteEnabled = enabled
                                prefs.isRemoteControlEnabled = enabled
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                                    if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false) {
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                }
                                val serviceIntent = Intent(context, TelegramRemoteService::class.java).apply {
                                    action = if (enabled) TelegramRemoteService.ACTION_START else TelegramRemoteService.ACTION_STOP
                                }
                                ContextCompat.startForegroundService(context, serviceIntent)
                            }
                        )
                    }

                    Text(
                        text = if (isRemoteEnabled) {
                            "🟢 Active: Send /listen or /status on Telegram anytime."
                        } else {
                            "⚪ Inactive: Turn switch ON to control this phone from upstairs."
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isRemoteEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Telegram Commands from upstairs:\n• /listen — Record 15-second room audio now\n• /status — Check battery and connection\n• /help — View available commands",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Schedule & Sensitivity Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Nightly Schedule",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )

                    Text(
                        text = "Configured for ${prefs.scheduledHour}:${String.format("%02d", prefs.scheduledMinute)} IST (11:00 PM)",
                        fontSize = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                AlarmScheduler.scheduleNightlyAlarm(context)
                                isScheduled = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Arm for 11 PM")
                        }

                        OutlinedButton(
                            onClick = {
                                AlarmScheduler.cancelNightlyAlarm(context)
                                isScheduled = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel Schedule")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Detection Sensitivity: ${sensitivity.toInt()} dB",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = sensitivity,
                        onValueChange = {
                            sensitivity = it
                            prefs.sensitivityDb = it
                        },
                        valueRange = 8.0f..25.0f,
                        steps = 17
                    )
                    Text(
                        text = "Lower = more sensitive (detects lighter breathing). Higher = detects only loud snoring.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Manual Test Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Manual Test Controls",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val serviceIntent = Intent(context, SnoreDetectionService::class.java).apply {
                                    action = SnoreDetectionService.ACTION_START_MONITORING
                                }
                                ContextCompat.startForegroundService(context, serviceIntent)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("Start Listening")
                        }

                        Button(
                            onClick = {
                                val serviceIntent = Intent(context, SnoreDetectionService::class.java).apply {
                                    action = SnoreDetectionService.ACTION_STOP_MONITORING
                                }
                                ContextCompat.startForegroundService(context, serviceIntent)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
