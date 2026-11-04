package com.rough.snoremonitor.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.rough.snoremonitor.data.AppPreferences
import com.rough.snoremonitor.dispatch.TelegramDispatcher
import com.rough.snoremonitor.recorder.SnippetRecorder
import com.rough.snoremonitor.remote.BotCommand
import com.rough.snoremonitor.remote.DeviceStatusHelper
import com.rough.snoremonitor.remote.TelegramCommandParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class TelegramRemoteService : Service() {

    companion object {
        const val ACTION_START = "com.rough.snoremonitor.START_REMOTE"
        const val ACTION_STOP = "com.rough.snoremonitor.STOP_REMOTE"
        var isServiceRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var prefs: AppPreferences
    private lateinit var dispatcher: TelegramDispatcher
    private lateinit var snippetRecorder: SnippetRecorder
    private val isRecording = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        dispatcher = TelegramDispatcher()
        snippetRecorder = SnippetRecorder(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SnoreMonitor::RemoteWakeLock").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRemoteService()
            return START_NOT_STICKY
        }

        isServiceRunning = true
        wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12 hours safety timeout

        val notification = ServiceNotificationManager.buildRemoteNotification(
            this,
            "Listening for Telegram commands (/listen, /status)..."
        )
        startForeground(ServiceNotificationManager.REMOTE_NOTIFICATION_ID, notification)

        startPollingLoop()

        return START_STICKY
    }

    private fun startPollingLoop() {
        serviceScope.launch {
            while (isActive) {
                val token = prefs.botToken
                val chatId = prefs.chatId

                if (token.isBlank() || chatId.isBlank()) {
                    delay(5000L)
                    continue
                }

                val parser = TelegramCommandParser(chatId)
                val nextOffset = prefs.lastTelegramUpdateId + 1

                val result = dispatcher.getUpdates(token, nextOffset, timeoutSeconds = 20)
                result.onSuccess { jsonString ->
                    val commands = parser.parseUpdates(jsonString)
                    for (cmd in commands) {
                        prefs.lastTelegramUpdateId = max(prefs.lastTelegramUpdateId, cmd.updateId)
                        handleCommand(cmd.command, cmd.rawText, token, chatId)
                    }
                }.onFailure {
                    // Back off briefly on error (e.g. temporary network drop)
                    delay(3000L)
                }

                delay(500L)
            }
        }
    }

    private fun handleCommand(command: BotCommand, rawText: String, token: String, chatId: String) {
        serviceScope.launch {
            when (command) {
                BotCommand.LISTEN -> {
                    if (isRecording.compareAndSet(false, true)) {
                        dispatcher.sendTextMessage(
                            token,
                            chatId,
                            "🎙️ Recording 15-second room audio right now. Please wait..."
                        )

                        val recordResult = snippetRecorder.recordSnippet(15_000L)
                        recordResult.onSuccess { snippetFile ->
                            dispatcher.sendVoiceSnippet(
                                botToken = token,
                                chatId = chatId,
                                audioFile = snippetFile,
                                caption = "🎙️ On-demand room audio requested from upstairs."
                            )
                            try {
                                snippetFile.delete()
                            } catch (_: Exception) {}
                        }.onFailure { error ->
                            dispatcher.sendTextMessage(
                                token,
                                chatId,
                                "⚠️ Failed to record audio: ${error.message ?: "Microphone error"}"
                            )
                        }

                        isRecording.set(false)
                    } else {
                        dispatcher.sendTextMessage(
                            token,
                            chatId,
                            "⏳ Already recording an audio snippet downstairs. Please wait a moment."
                        )
                    }
                }

                BotCommand.STATUS -> {
                    val statusReport = DeviceStatusHelper.getStatusReport(this@TelegramRemoteService)
                    dispatcher.sendTextMessage(token, chatId, statusReport)
                }

                BotCommand.HELP -> {
                    val helpText = "🤖 *Snore Monitor Remote Commands*\n\n" +
                            "• `/listen` or `/record` — Record and send 15s live room sound\n" +
                            "• `/status` — View downstairs phone battery & monitoring status\n" +
                            "• `/help` — Show this command list"
                    dispatcher.sendTextMessage(token, chatId, helpText)
                }

                BotCommand.UNKNOWN -> {
                    val unknownText = "❓ Unrecognized command: \"$rawText\"\n\n" +
                            "Send `/listen` to record audio, or `/status` to check downstairs device status."
                    dispatcher.sendTextMessage(token, chatId, unknownText)
                }
            }
        }
    }

    private fun stopRemoteService() {
        isServiceRunning = false
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}

        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRemoteService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
