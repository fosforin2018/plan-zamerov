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

    private fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
        try {
            val rv = RemoteViews(ctx.packageName, R.layout.zamer_widget)

            // Получаем данные
            val all = Storage(ctx).load()
            val today = LocalDate.now()
            val todayItems = all.filter { it.date == today }.sortedBy { it.time }
            val upcoming = all.filter { it.status == ZamerStatus.PLANNED && it.date >= today }
                .sortedWith(compareBy({ it.date }, { it.time }))
            val list = (todayItems + upcoming)

            rv.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))

            if (list.isEmpty()) {
                rv.setViewVisibility(R.id.card_1, View.GONE)
                rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
                rv.setTextViewText(R.id.w_title, "📏 План замеров")
            } else {
                rv.setViewVisibility(R.id.card_1, View.VISIBLE)
                rv.setViewVisibility(R.id.w_empty, View.GONE)
                rv.setTextViewText(R.id.w_title, "📏 План замеров · ${list.size} шт.")
                val z = list[0]
                rv.setTextViewText(R.id.t1_time, z.timeText())
                rv.setTextViewText(R.id.t1_name, if (z.name.isNotBlank()) z.name else "")
                rv.setTextViewText(R.id.t1_addr, if (z.address.isNotBlank()) z.address else "")
            }

            mgr.updateAppWidget(id, rv)
        } catch (e: Exception) {
            Log.e("ZamerWidget", "Ошибка", e)
        }
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
