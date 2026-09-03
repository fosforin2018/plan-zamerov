package com.zamerplan.app.alarm

import android.content.Context

class SettingsStore(ctx: Context) {

    private val prefs =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var ringtoneUri: String
        get() = prefs.getString("ringtone", "") ?: ""
        set(value) =
            prefs.edit().putString("ringtone", value).apply()

    var beforeDay: Boolean
        get() = prefs.getBoolean("b_day", false)
        set(value) =
            prefs.edit().putBoolean("b_day", value).apply()

    var before2h: Boolean
        get() = prefs.getBoolean("b_2h", true)
        set(value) =
            prefs.edit().putBoolean("b_2h", value).apply()

    var before30m: Boolean
        get() = prefs.getBoolean("b_30m", true)
        set(value) =
            prefs.edit().putBoolean("b_30m", value).apply()

    var before10m: Boolean
        get() = prefs.getBoolean("b_10m", false)
        set(value) =
            prefs.edit().putBoolean("b_10m", value).apply()

    var themeMode: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) =
            prefs.edit().putString("theme", value).apply()

    var customReminderTime: String
        get() = prefs.getString("custom_reminder_time", "") ?: ""
        set(value) =
            prefs.edit()
                .putString("custom_reminder_time", value)
                .apply()

    var sources: Set<String>
        get() =
            prefs.getStringSet("sources", emptySet()) ?: emptySet()
        set(value) =
            prefs.edit().putStringSet("sources", value).apply()

    /*
     * ============================================
     * НАСТРОЙКА ВИДЖЕТА
     * ============================================
     *
     * 2 = компактный режим
     * 4 = расширенный режим
     *
     * По умолчанию — 2.
     *
     * ВАЖНО: экран настроек (SettingsScreen) пишет
     * значение в ключ "widget_cards_count",
     * поэтому читаем именно его, а старый ключ
     * "widget_items_count" оставляем как фолбэк.
     */
    var widgetItemsCount: Int
        get() {
            val value = prefs.getInt(
                "widget_cards_count",
                prefs.getInt("widget_items_count", 2)
            )
            return if (value == 4) {
                4
            } else {
                2
            }
        }
        set(value) {
            val safeValue =
                if (value == 4) 4 else 2
            prefs.edit()
                .putInt("widget_items_count", safeValue)
                .putInt("widget_cards_count", safeValue)
                .apply()
        }
}
