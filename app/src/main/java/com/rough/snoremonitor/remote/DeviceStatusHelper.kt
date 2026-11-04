package com.rough.snoremonitor.remote

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.rough.snoremonitor.data.AppPreferences
import com.rough.snoremonitor.service.SnoreDetectionService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceStatusHelper {

    fun getStatusReport(context: Context): String {
        val prefs = AppPreferences(context)

        // Query Battery
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val batteryEmoji = when {
            isCharging -> "⚡"
            batteryPct > 50 -> "🔋"
            batteryPct > 20 -> "🪫"
            else -> "⚠️"
        }
        val batteryInfo = if (batteryPct >= 0) {
            "$batteryEmoji $batteryPct% ${if (isCharging) "(Charging)" else "(On Battery)"}"
        } else {
            "🔋 Unknown"
        }

        // Snore Service status
        val snoreServiceStatus = if (SnoreDetectionService.isServiceRunning) {
            "🟢 Actively listening for sleep sounds"
        } else {
            "⚪ Inactive (Standing by)"
        }

        // Schedule status
        val scheduleStatus = if (prefs.isScheduled) {
            val hourFormatted = String.format(Locale.getDefault(), "%02d:%02d", prefs.scheduledHour, prefs.scheduledMinute)
            "⏰ Armed for $hourFormatted IST nightly"
        } else {
            "⏰ Schedule inactive"
        }

        val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        return buildString {
            appendLine("📱 *Downstairs Monitor Status*")
            appendLine("⏱️ Time: $currentTime")
            appendLine("🔋 Battery: $batteryInfo")
            appendLine("🌙 Detection: $snoreServiceStatus")
            appendLine("🗓️ Schedule: $scheduleStatus")
            appendLine()
            append("Send /listen to record 15s room audio.")
        }
    }
}
