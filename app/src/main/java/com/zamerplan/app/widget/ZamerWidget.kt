package com.zamerplan.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.zamerplan.app.MainActivity
import com.zamerplan.app.R
import com.zamerplan.app.model.Storage
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
            val all = Storage(ctx).load()
            val today = LocalDate.now()

            val todayItems = all.filter { it.date == today }.sortedBy { it.time }
            val upcoming = all.filter { it.status == ZamerStatus.PLANNED && it.date >= today }
                .sortedWith(compareBy({ it.date }, { it.time }))
            val nearest = todayItems.firstOrNull() ?: upcoming.firstOrNull()

            rv.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            if (nearest == null) {
                rv.setViewVisibility(R.id.t_card, View.GONE)
                rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
                rv.setTextViewText(R.id.w_title, "📏 План замеров")
            } else {
                rv.setViewVisibility(R.id.t_card, View.VISIBLE)
                rv.setViewVisibility(R.id.w_empty, View.GONE)
                val color = statusColor(nearest.status)
                val dFmt = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

                rv.setTextViewText(R.id.w_title,
                    "📏 План замеров · " + nearest.date.format(dFmt))
                rv.setTextViewText(R.id.t_time, nearest.timeText())
                rv.setInt(R.id.t_stripe, "setBackgroundColor", color)
                rv.setTextViewText(R.id.t_status, nearest.status.label)
                rv.setInt(R.id.t_status, "setBackgroundColor", color)

                if (nearest.name.isNotBlank()) {
                    rv.setTextViewText(R.id.t_name, nearest.name)
                    rv.setViewVisibility(R.id.t_name, View.VISIBLE)
                } else rv.setViewVisibility(R.id.t_name, View.GONE)

                if (nearest.address.isNotBlank()) {
                    rv.setTextViewText(R.id.t_addr, nearest.address)
                    rv.setViewVisibility(R.id.t_addr, View.VISIBLE)
                } else rv.setViewVisibility(R.id.t_addr, View.GONE)

                val meta = listOf(
                    if (nearest.contactFrom.isNotBlank()) "От: " + nearest.contactFrom else "",
                    nearest.area, nearest.thickness
                ).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    rv.setTextViewText(R.id.t_meta, meta)
                    rv.setViewVisibility(R.id.t_meta, View.VISIBLE)
                } else rv.setViewVisibility(R.id.t_meta, View.GONE)

                rv.setTextViewText(R.id.t_price,
                    if (nearest.price.isNotBlank()) nearest.price + " ₽" else "")

                val extra = todayItems.size - 1
                rv.setTextViewText(R.id.w_more,
                    if (extra > 0) "и ещё замеров сегодня: $extra" else "")

                val tel = nearest.phone.filter { c -> c.isDigit() || c == '+' }
                if (tel.isNotEmpty()) {
                    rv.setOnClickPendingIntent(R.id.t_call, PendingIntent.getActivity(
                        ctx, 1,
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    rv.setViewVisibility(R.id.t_call, View.VISIBLE)
                } else rv.setViewVisibility(R.id.t_call, View.GONE)

                if (nearest.address.isNotBlank()) {
                    rv.setOnClickPendingIntent(R.id.t_map, PendingIntent.getActivity(
                        ctx, 2,
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://yandex.ru/maps/?text=" + Uri.encode(nearest.address)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    rv.setViewVisibility(R.id.t_map, View.VISIBLE)
                } else rv.setViewVisibility(R.id.t_map, View.GONE)
            }

            mgr.updateAppWidget(id, rv)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun refreshAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, ZamerWidget::class.java))
                val intent = Intent(ctx, ZamerWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                ctx.sendBroadcast(intent)
            } catch (e: Exception) { }
        }
    }
}
