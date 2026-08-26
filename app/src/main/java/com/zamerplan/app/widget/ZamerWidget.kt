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

class ZamerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    private fun statusColor(s: ZamerStatus): Int = when (s) {
        ZamerStatus.PLANNED -> 0xFFF4511E.toInt()
        ZamerStatus.DONE -> 0xFF43A047.toInt()
        ZamerStatus.POSTPONED -> 0xFF9E9E9E.toInt()
        ZamerStatus.CANCELLED -> 0xFFE53935.toInt()
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val rv = RemoteViews(context.packageName, R.layout.zamer_widget)
        val all = Storage(context).load()
        val today = LocalDate.now()

        val todayItems = all.filter { it.date == today }.sortedBy { it.time }
        val futureItems = all.filter { it.status == ZamerStatus.PLANNED && it.date > today }
            .sortedWith(compareBy({ it.date }, { it.time }))

        val list = (todayItems + futureItems).take(4)

        rv.setTextViewText(R.id.w_title, "📏 План замеров · ${list.size} шт.")

        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.w_root, openAppIntent)

        if (list.isEmpty()) {
            rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
            rv.setViewVisibility(R.id.card_1, View.GONE)
            rv.setViewVisibility(R.id.card_2, View.GONE)
            rv.setViewVisibility(R.id.card_3, View.GONE)
            rv.setViewVisibility(R.id.card_4, View.GONE)
        } else {
            rv.setViewVisibility(R.id.w_empty, View.GONE)

            if (list.size > 0) {
                rv.setViewVisibility(R.id.card_1, View.VISIBLE)
                fillCard(rv, context, list[0], R.id.card_1, R.id.t1_time, R.id.t1_name, R.id.t1_addr, R.id.t1_call, R.id.t1_map, statusColor(list[0].status))
            } else rv.setViewVisibility(R.id.card_1, View.GONE)

            if (list.size > 1) {
                rv.setViewVisibility(R.id.card_2, View.VISIBLE)
                fillCard(rv, context, list[1], R.id.card_2, R.id.t2_time, R.id.t2_name, R.id.t2_addr, R.id.t2_call, R.id.t2_map, statusColor(list[1].status))
            } else rv.setViewVisibility(R.id.card_2, View.GONE)

            if (list.size > 2) {
                rv.setViewVisibility(R.id.card_3, View.VISIBLE)
                fillCard(rv, context, list[2], R.id.card_3, R.id.t3_time, R.id.t3_name, R.id.t3_addr, R.id.t3_call, R.id.t3_map, statusColor(list[2].status))
            } else rv.setViewVisibility(R.id.card_3, View.GONE)

            if (list.size > 3) {
                rv.setViewVisibility(R.id.card_4, View.VISIBLE)
                fillCard(rv, context, list[3], R.id.card_4, R.id.t4_time, R.id.t4_name, R.id.t4_addr, R.id.t4_call, R.id.t4_map, statusColor(list[3].status))
            } else rv.setViewVisibility(R.id.card_4, View.GONE)
        }

        appWidgetManager.updateAppWidget(widgetId, rv)
    }

    private fun fillCard(
        rv: RemoteViews,
        context: Context,
        z: Zamer,
        cardId: Int,
        timeId: Int,
        nameId: Int,
        addrId: Int,
        callId: Int,
        mapId: Int,
        color: Int
    ) {
        rv.setTextViewText(timeId, z.timeText())
        rv.setInt(timeId, "setTextColor", color)

        if (z.name.isNotBlank()) {
            rv.setTextViewText(nameId, z.name)
            rv.setViewVisibility(nameId, View.VISIBLE)
        } else rv.setViewVisibility(nameId, View.GONE)

        if (z.address.isNotBlank()) {
            rv.setTextViewText(addrId, z.address)
            rv.setViewVisibility(addrId, View.VISIBLE)
        } else rv.setViewVisibility(addrId, View.GONE)

        val tel = z.phone.filter { c -> c.isDigit() || c == '+' }
        if (tel.isNotEmpty()) {
            rv.setOnClickPendingIntent(callId, PendingIntent.getActivity(
                context,
                z.id.toInt(),
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            rv.setViewVisibility(callId, View.VISIBLE)
        } else rv.setViewVisibility(callId, View.GONE)

        if (z.address.isNotBlank()) {
            rv.setOnClickPendingIntent(mapId, PendingIntent.getActivity(
                context,
                z.id.toInt() + 1000,
                Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?text=" + Uri.encode(z.address)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            rv.setViewVisibility(mapId, View.VISIBLE)
        } else rv.setViewVisibility(mapId, View.GONE)
    }

    companion object {
        fun refreshAll(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, ZamerWidget::class.java))
                if (ids.isEmpty()) return
                ids.forEach { id -> ZamerWidget().updateWidget(context, mgr, id) }
            } catch (e: Exception) {
                Log.e("ZamerWidget", "Ошибка refreshAll", e)
            }
        }
    }
}
