package com.rough.snoremonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rough.snoremonitor.service.SnoreDetectionService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, SnoreDetectionService::class.java).apply {
            action = SnoreDetectionService.ACTION_START_MONITORING
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
