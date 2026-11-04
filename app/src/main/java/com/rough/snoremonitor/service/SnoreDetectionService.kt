package com.rough.snoremonitor.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.rough.snoremonitor.data.AppPreferences
import com.rough.snoremonitor.detection.SnoreDetector
import com.rough.snoremonitor.dispatch.TelegramDispatcher
import com.rough.snoremonitor.recorder.SnippetRecorder
import com.rough.snoremonitor.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class SnoreDetectionService : Service() {

    companion object {
        const val ACTION_START_MONITORING = "com.rough.snoremonitor.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.rough.snoremonitor.STOP_MONITORING"
        const val ACTION_PAUSE_FOR_RECORDING = "com.rough.snoremonitor.PAUSE_FOR_RECORDING"
        const val ACTION_RESUME_AFTER_RECORDING = "com.rough.snoremonitor.RESUME_AFTER_RECORDING"
        var isServiceRunning = false
            private set
    }

    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var listeningJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioRecord: AudioRecord? = null
    private var audioManager: AudioManager? = null
    private var audioFocusGranted = false

    private lateinit var snoreDetector: SnoreDetector
    private lateinit var snippetRecorder: SnippetRecorder
    private lateinit var telegramDispatcher: TelegramDispatcher
    private lateinit var prefs: AppPreferences

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                audioFocusGranted = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioFocusGranted = true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        snoreDetector = SnoreDetector(sensitivityDb = prefs.sensitivityDb)
        snippetRecorder = SnippetRecorder(this)
        telegramDispatcher = TelegramDispatcher()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SnoreMonitor::WakeLock").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_FOR_RECORDING -> {
                listeningJob?.cancel()
                releaseAudioRecord()
                return START_STICKY
            }
            ACTION_RESUME_AFTER_RECORDING -> {
                if (isServiceRunning) {
                    startListeningLoop()
                }
                return START_STICKY
            }
        }

        isServiceRunning = true
        wakeLock?.acquire(6 * 60 * 60 * 1000L) // 6 hours safety timeout

        val notification = ServiceNotificationManager.buildForegroundNotification(
            this,
            "Monitoring sleep audio downstairs..."
        )
        startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)

        requestAudioFocus()
        startListeningLoop()

        return START_STICKY
    }

    private fun requestAudioFocus() {
        @Suppress("DEPRECATION")
        val result = audioManager?.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
        audioFocusGranted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
    }

    private fun startListeningLoop() {
        listeningJob?.cancel()
        listeningJob = serviceScope.launch {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = max(minBufferSize, 2048)
            val buffer = ShortArray(bufferSize)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                audioRecord?.startRecording()
            } catch (e: SecurityException) {
                stopMonitoring()
                return@launch
            } catch (e: Exception) {
                stopMonitoring()
                return@launch
            }

            while (isActive) {
                if (!audioFocusGranted) {
                    delay(1000L)
                    continue
                }

                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readCount > 0) {
                    var sumSquares = 0.0
                    for (i in 0 until readCount) {
                        sumSquares += buffer[i] * buffer[i]
                    }
                    val rms = sqrt(sumSquares / readCount)
                    val db = (20.0 * log10(max(1.0, rms))).toFloat()

                    val isSnoringConfirmed = snoreDetector.processRms(db, System.currentTimeMillis())

                    if (isSnoringConfirmed) {
                        handleSnoringDetected()
                        break // End the loop for one-and-done
                    }
                }
                delay(100L)
            }
        }
    }

    private suspend fun handleSnoringDetected() {
        // Update notification
        val notification = ServiceNotificationManager.buildForegroundNotification(
            this,
            "Snoring detected! Recording 15-second audio snippet..."
        )
        NotificationManagerCompat.from(this).notify(ServiceNotificationManager.NOTIFICATION_ID, notification)

        // Release AudioRecord to free mic for MediaRecorder
        releaseAudioRecord()

        // Capture 15-second snippet
        val recordResult = snippetRecorder.recordSnippet(15_000L)

        recordResult.onSuccess { snippetFile ->
            val timeString = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            val caption = "😴 Brother is asleep! Snoring confirmed at $timeString.\nHere is the 15-second audio proof."

            telegramDispatcher.sendVoiceSnippet(
                botToken = prefs.botToken,
                chatId = prefs.chatId,
                audioFile = snippetFile,
                caption = caption
            )

            // Clean up temporary audio file after sending
            try {
                snippetFile.delete()
            } catch (_: Exception) {}
        }

        // ONE-AND-DONE: Reschedule for next day and stop service cleanly
        AlarmScheduler.scheduleNightlyAlarm(this)
        stopMonitoring()
    }

    private fun releaseAudioRecord() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun stopMonitoring() {
        isServiceRunning = false
        listeningJob?.cancel()
        releaseAudioRecord()
        @Suppress("DEPRECATION")
        audioManager?.abandonAudioFocus(audioFocusChangeListener)

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}

        serviceJob.cancel()
        serviceJob = SupervisorJob()
        serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isServiceRunning) {
            val restartIntent = Intent(applicationContext, SnoreDetectionService::class.java).apply {
                action = ACTION_START_MONITORING
                `package` = packageName
            }
            val pendingIntent = PendingIntent.getService(
                applicationContext,
                1001,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 1000L,
                    pendingIntent
                )
            } catch (_: Exception) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 1000L,
                    pendingIntent
                )
            }
        }
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
