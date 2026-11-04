package com.rough.snoremonitor.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("snore_monitor_prefs", Context.MODE_PRIVATE)

    var botToken: String
        get() = prefs.getString("bot_token", "") ?: ""
        set(value) = prefs.edit().putString("bot_token", value.trim()).apply()

    var chatId: String
        get() = prefs.getString("chat_id", "") ?: ""
        set(value) = prefs.edit().putString("chat_id", value.trim()).apply()

    var scheduledHour: Int
        get() = prefs.getInt("scheduled_hour", 23)
        set(value) = prefs.edit().putInt("scheduled_hour", value).apply()

    var scheduledMinute: Int
        get() = prefs.getInt("scheduled_minute", 0)
        set(value) = prefs.edit().putInt("scheduled_minute", value).apply()

    var sensitivityDb: Float
        get() = prefs.getFloat("sensitivity_db", 14.0f)
        set(value) = prefs.edit().putFloat("sensitivity_db", value).apply()

    var isScheduled: Boolean
        get() = prefs.getBoolean("is_scheduled", false)
        set(value) = prefs.edit().putBoolean("is_scheduled", value).apply()

    var isRemoteControlEnabled: Boolean
        get() = prefs.getBoolean("is_remote_control_enabled", false)
        set(value) = prefs.edit().putBoolean("is_remote_control_enabled", value).apply()

    var lastTelegramUpdateId: Long
        get() = prefs.getLong("last_telegram_update_id", 0L)
        set(value) = prefs.edit().putLong("last_telegram_update_id", value).apply()
}
