package com.zamerplan.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.zamerplan.app.MainActivity
import com.zamerplan.app.R
import com.zamerplan.app.model.Storage
import com.zamerplan.app.model.Zamer
import com.zamerplan.app.model.ZamerStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ZamerWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(ctx, mgr, it) }
    }

    private fun statusColor(s: ZamerStatus): Int = when (s) {
        ZamerStatus.PLANNED -> 0xFFF4511E.toInt()
        ZamerStatus.DONE -> 0xFF43A047.toInt()
        ZamerStatus.POSTPONED -> 0xFF9E9E9E.toInt()
        ZamerStatus.CANCELLED -> 0xFFE53935.toInt()
    }

    private fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
        try {
            val rv = RemoteViews(ctx.packageName, R.layout.zamer_widget)

            // Получаем все замеры
            val all = Storage(ctx).load()
            val today = LocalDate.now()

            // Сегодняшние и будущие, но без дубликатов: futureItems только date > today
            val todayItems = all.filter { it.date == today }.sortedBy { it.time }
            val futureItems = all.filter { it.status == ZamerStatus.PLANNED && it.date > today }
                .sortedWith(compareBy({ it.date }, { it.time }))
            val list = (todayItems + futureItems).take(4)

            // Клик по корню – открыть приложение
            rv.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))

            if (list.isEmpty()) {
                rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
                rv.setViewVisibility(R.id.card_1, View.GONE)
                rv.setViewVisibility(R.id.card_2, View.GONE)
                rv.setViewVisibility(R.id.card_3, View.GONE)
                rv.setViewVisibility(R.id.card_4, View.GONE)
                rv.setTextViewText(R.id.w_title, "📏 План замеров")
            } else {
                rv.setViewVisibility(R.id.w_empty, View.GONE)
                rv.setTextViewText(R.id.w_title, "📏 План замеров · ${list.size} шт.")

                // Карточка 1
                fillCard(rv, ctx, list[0], R.id.card_1, R.id.t1_time, R.id.t1_status, R.id.t1_name, R.id.t1_addr, R.id.t1_call, R.id.t1_map, statusColor(list[0].status))

                // Карточка 2
                if (list.size > 1) {
                    rv.setViewVisibility(R.id.card_2, View.VISIBLE)
                    fillCard(rv, ctx, list[1], R.id.card_2, R.id.t2_time, R.id.t2_status, R.id.t2_name, R.id.t2_addr, R.id.t2_call, R.id.t2_map, statusColor(list[1].status))
                } else rv.setViewVisibility(R.id.card_2, View.GONE)

                // Карточка 3
                if (list.size > 2) {
                    rv.setViewVisibility(R.id.card_3, View.VISIBLE)
                    fillCard(rv, ctx, list[2], R.id.card_3, R.id.t3_time, R.id.t3_status, R.id.t3_name, R.id.t3_addr, R.id.t3_call, R.id.t3_map, statusColor(list[2].status))
                } else rv.setViewVisibility(R.id.card_3, View.GONE)

                // Карточка 4
                if (list.size > 3) {
                    rv.setViewVisibility(R.id.card_4, View.VISIBLE)
                    fillCard(rv, ctx, list[3], R.id.card_4, R.id.t4_time, R.id.t4_status, R.id.t4_name, R.id.t4_addr, R.id.t4_call, R.id.t4_map, statusColor(list[3].status))
                } else rv.setViewVisibility(R.id.card_4, View.GONE)
            }

            mgr.updateAppWidget(id, rv)
        } catch (e: Exception) {
            Log.e("ZamerWidget", "Ошибка обновления виджета", e)
        }
    }

    private fun fillCard(
        rv: RemoteViews, ctx: Context, z: Zamer,
        cardId: Int, timeId: Int, statusId: Int, nameId: Int, addrId: Int, callId: Int, mapId: Int,
        color: Int
    ) {
        rv.setViewVisibility(cardId, View.VISIBLE)
        rv.setTextViewText(timeId, z.timeText())
        rv.setInt(statusId, "setBackgroundColor", color)
        rv.setTextViewText(statusId, z.status.label)
        rv.setTextViewText(nameId, if (z.name.isNotBlank()) z.name else "")
        rv.setTextViewText(addrId, if (z.address.isNotBlank()) z.address else "")

        val tel = z.phone.filter { c -> c.isDigit() || c == '+' }
        if (tel.isNotEmpty()) {
            rv.setOnClickPendingIntent(callId, PendingIntent.getActivity(
                ctx, z.id.toInt(), Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            rv.setViewVisibility(callId, View.VISIBLE)
        } else rv.setViewVisibility(callId, View.GONE)

        if (z.address.isNotBlank()) {
            rv.setOnClickPendingIntent(mapId, PendingIntent.getActivity(
                ctx, z.id.toInt() + 1000, Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://yandex.ru/maps/?text=" + Uri.encode(z.address)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            rv.setViewVisibility(mapId, View.VISIBLE)
        } else rv.setViewVisibility(mapId, View.GONE)
    }

    companion object {
        fun refreshAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, ZamerWidget::class.java))
                if (ids.isEmpty()) return
                ids.forEach { id -> ZamerWidget().update(ctx, mgr, id) }
            } catch (e: Exception) {
                Log.e("ZamerWidget", "Ошибка refreshAll", e)
            }
        }
    }
}
