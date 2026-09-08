package com.zamerplan.app.alarm

import android.content.Context

class SettingsStore(ctx: Context) {

    private val prefs =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        migrateReminders()
    }

    var ringtoneUri: String
        get() = prefs.getString("ringtone", "") ?: ""
        set(value) = prefs.edit().putString("ringtone", value).apply()

    var themeMode: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) = prefs.edit().putString("theme", value).apply()

    var sources: Set<String>
        get() = prefs.getStringSet("sources", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("sources", value).apply()

    /*
     * Виджет: 2 или 4 карточки.
     * Экран настроек пишет "widget_cards_count",
     * старый ключ оставляем как фолбэк.
     */
    var widgetItemsCount: Int
        get() {
            val value = prefs.getInt(
                "widget_cards_count",
                prefs.getInt("widget_items_count", 2)
            )
            return if (value == 4) 4 else 2
        }
        set(value) {
            val safe = if (value == 4) 4 else 2
            prefs.edit()
                .putInt("widget_items_count", safe)
                .putInt("widget_cards_count", safe)
                .apply()
        }

    /*
     * ============================================
     * СЛОТЫ НАПОМИНАНИЙ (1..4)
     * ============================================
     * У каждого слота: включён ли он и за сколько
     * минут до замера он срабатывает.
     */

    fun slotOn(index: Int): Boolean =
        prefs.getBoolean("rem${index}_on", defaultSlotOn(index))

    fun setSlotOn(index: Int, value: Boolean) {
        prefs.edit().putBoolean("rem${index}_on", value).apply()
    }

    fun slotMinutes(index: Int): Int =
        prefs.getInt("rem${index}_min", defaultSlotMin(index))

    fun setSlotMinutes(index: Int, value: Int) {
        prefs.edit()
            .putInt("rem${index}_min", value.coerceIn(1, 7 * 24 * 60))
            .apply()
    }

    private fun defaultSlotOn(index: Int): Boolean = when (index) {
        1 -> false
        2 -> true
        3 -> true
        4 -> false
        else -> false
    }

    private fun defaultSlotMin(index: Int): Int = when (index) {
        1 -> 24 * 60
        2 -> 120
        3 -> 30
        4 -> 10
        else -> 60
    }

    /*
     * Одноразовая миграция старых галочек
     * (b_day, b_2h, b_30m, b_10m) в новые слоты,
     * чтобы ничего не потерялось при обновлении.
     */
    private fun migrateReminders() {
        if (prefs.getBoolean("rem_migrated", false)) return
        prefs.edit()
            .putBoolean("rem1_on", prefs.getBoolean("b_day", false))
            .putInt("rem1_min", 24 * 60)
            .putBoolean("rem2_on", prefs.getBoolean("b_2h", true))
            .putInt("rem2_min", 120)
            .putBoolean("rem3_on", prefs.getBoolean("b_30m", true))
            .putInt("rem3_min", 30)
            .putBoolean("rem4_on", prefs.getBoolean("b_10m", false))
            .putInt("rem4_min", 10)
            .putBoolean("rem_migrated", true)
            .apply()
    }

    /*
     * ============================================
     * СВОЁ ВРЕМЯ (точное время в день замера)
     * ============================================
     */

    var customTimeOn: Boolean
        get() = prefs.getBoolean("custom_time_on", false)
        set(value) = prefs.edit().putBoolean("custom_time_on", value).apply()

    var customReminderTime: String
        get() = prefs.getString("custom_reminder_time", "") ?: ""
        set(value) = prefs.edit().putString("custom_reminder_time", value).apply()

    /*
     * ============================================
     * ЧТО ПРОИГРЫВАТЬ
     * ============================================
     * "voice_ring" = голос + мелодия (по умолчанию)
     * "voice"      = только голос
     * "ring"       = только мелодия
     */
    var playMode: String
        get() = prefs.getString("play_mode", "voice_ring") ?: "voice_ring"
        set(value) = prefs.edit().putString("play_mode", value).apply()
}
