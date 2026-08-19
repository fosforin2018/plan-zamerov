package com.zamerplan.app.alarm

import android.content.Context

class SettingsStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var ringtoneUri: String
        get() = prefs.getString("ringtone", "") ?: ""
        set(v) = prefs.edit().putString("ringtone", v).apply()

    var beforeDay: Boolean
        get() = prefs.getBoolean("b_day", false)
        set(v) = prefs.edit().putBoolean("b_day", v).apply()

    var before2h: Boolean
        get() = prefs.getBoolean("b_2h", true)
        set(v) = prefs.edit().putBoolean("b_2h", v).apply()

    var before30m: Boolean
        get() = prefs.getBoolean("b_30m", true)
        set(v) = prefs.edit().putBoolean("b_30m", v).apply()

    var before10m: Boolean
        get() = prefs.getBoolean("b_10m", false)
        set(v) = prefs.edit().putBoolean("b_10m", v).apply()
}
