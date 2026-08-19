package com.zamerplan.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.zamerplan.app.model.Zamer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ReminderScheduler {
    private const val ACTION = "com.zamerplan.REMINDER"

    fun scheduleAll(ctx: Context, zamers: List<Zamer>, settings: SettingsStore) {
        zamers.forEach { schedule(ctx, it, settings) }
    }

    fun schedule(ctx: Context, z: Zamer, settings: SettingsStore) {
        cancel(ctx, z.id)
        val base = LocalDateTime.of(z.date, z.time)
        val offsets = listOf(
            24 * 60L to settings.beforeDay,
            120L to settings.before2h,
            30L to settings.before30m,
            10L to settings.before10m
        )
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        offsets.forEachIndexed { i, (mins, on) ->
            if (!on) return@forEachIndexed
            val fire = base.minusMinutes(mins)
            if (fire.isBefore(LocalDateTime.now())) return@forEachIndexed
            val intent = Intent(ctx, ReminderReceiver::class.java).apply {
                action = ACTION
                data = Uri.parse("zamer://${z.id}/$i")
                putExtra("zamer_id", z.id)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, (z.id * 10 + i).toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val ms = fire.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            } catch (e: Exception) {
                am.set(AlarmManager.RTC_WAKEUP, ms, pi)
            }
        }
    }

    fun cancel(ctx: Context, id: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0..3) {
            val intent = Intent(ctx, ReminderReceiver::class.java).apply {
                action = ACTION
                data = Uri.parse("zamer://$id/$i")
                putExtra("zamer_id", id)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, (id * 10 + i).toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }
    }
}
