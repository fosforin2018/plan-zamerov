package com.zamerplan.app.alarm

import android.content.Context

class SettingsStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var ringtoneUri: String
        get() = prefs.getString("ringtone", "") ?: ""
        set(value) = prefs.edit().putString("ringtone", value).apply()

    var beforeDay: Boolean
        get() = prefs.getBoolean("b_day", false)
        set(value) = prefs.edit().putBoolean("b_day", value).apply()

    var before2h: Boolean
        get() = prefs.getBoolean("b_2h", true)
        set(value) = prefs.edit().putBoolean("b_2h", value).apply()

    var before30m: Boolean
        get() = prefs.getBoolean("b_30m", true)
        set(value) = prefs.edit().putBoolean("b_30m", value).apply()

    var before10m: Boolean
        get() = prefs.getBoolean("b_10m", false)
        set(value) = prefs.edit().putBoolean("b_10m", value).apply()

    // Новое поле для темы
    var themeMode: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) = prefs.edit().putString("theme", value).apply()

    // Новое поле для кастомного времени (например, "08:30")
    var customReminderTime: String
        get() = prefs.getString("custom_reminder_time", "") ?: ""
        set(value) = prefs.edit().putString("custom_reminder_time", value).apply()
}
