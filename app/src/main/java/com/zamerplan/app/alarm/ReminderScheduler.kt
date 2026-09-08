package com.zamerplan.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.zamerplan.app.model.Zamer
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
        val now = LocalDateTime.now()

        // Индексы 0..3 = слоты, индекс 4 = своё время
        val raw = mutableListOf<Pair<Int, LocalDateTime>>()

        for (i in 0..3) {
            val slot = i + 1
            if (!settings.slotOn(slot)) continue
            val fire = base.minusMinutes(settings.slotMinutes(slot).toLong())
            raw.add(i to fire)
        }

        if (settings.customTimeOn) {
            parseTime(settings.customReminderTime)?.let { t ->
                raw.add(4 to LocalDateTime.of(z.date, t))
            }
        }

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Защита от дублей: одинаковое время = один будильник
        raw.distinctBy { (_, fire) -> epochMinutes(fire) }
            .forEach { (index, fire) ->
                if (fire.isBefore(now)) return@forEach
                setAlarm(ctx, am, z.id, index, fire)
            }
    }

    private fun setAlarm(
        ctx: Context,
        am: AlarmManager,
        zamerId: Long,
        index: Int,
        fire: LocalDateTime
    ) {
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            action = ACTION
            data = Uri.parse("zamer://$zamerId/$index")
            putExtra("zamer_id", zamerId)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, (zamerId * 10 + index).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ms = fire.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } catch (e: Exception) {
            am.set(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    fun cancel(ctx: Context, id: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0..4) {
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

    private fun epochMinutes(dt: LocalDateTime): Long =
        dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 60000

    private fun parseTime(s: String): LocalTime? = try {
        LocalTime.parse(s)
    } catch (e: Exception) {
        null
    }
}
