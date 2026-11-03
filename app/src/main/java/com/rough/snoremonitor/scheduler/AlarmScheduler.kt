package com.rough.snoremonitor.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rough.snoremonitor.data.AppPreferences
import com.rough.snoremonitor.receiver.AlarmReceiver
import java.util.Calendar
import java.util.TimeZone

object AlarmScheduler {
    const val REQUEST_CODE = 2001

    /**
     * Calculates the trigger timestamp for the next occurrence of [hour]:[minute]
     * in the Indian Standard Time (Asia/Kolkata) timezone.
     */
    fun calculateNextTriggerMillis(hour: Int, minute: Int, currentTimeMillis: Long = System.currentTimeMillis()): Long {
        val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val calendar = Calendar.getInstance(istTimeZone).apply {
            timeInMillis = currentTimeMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= currentTimeMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return calendar.timeInMillis
    }

    fun scheduleNightlyAlarm(context: Context) {
        val prefs = AppPreferences(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = calculateNextTriggerMillis(prefs.scheduledHour, prefs.scheduledMinute)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

        prefs.isScheduled = true
    }

    fun cancelNightlyAlarm(context: Context) {
        val prefs = AppPreferences(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        prefs.isScheduled = false
    }
}
