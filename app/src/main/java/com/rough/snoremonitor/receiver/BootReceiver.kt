package com.rough.snoremonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rough.snoremonitor.data.AppPreferences
import com.rough.snoremonitor.scheduler.AlarmScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = AppPreferences(context)
            if (prefs.isScheduled) {
                AlarmScheduler.scheduleNightlyAlarm(context)
            }
        }
    }
}
