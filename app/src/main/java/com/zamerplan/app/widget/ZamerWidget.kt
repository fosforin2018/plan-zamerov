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
            val dFmt = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

            val dayItems = all.filter { it.date == today }.sortedBy { it.time }
            val upcoming = all.filter { it.status == ZamerStatus.PLANNED && it.date > today }
                .sortedWith(compareBy({ it.date }, { it.time }))
            val items = (if (dayItems.isNotEmpty()) dayItems else upcoming).take(4)

            rv.setTextViewText(R.id.w_title, "📏 План замеров · " + today.format(dFmt))
            rv.removeAllViews(R.id.w_tiles)

            if (items.isEmpty()) {
                rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.w_empty, View.GONE)
                items.forEach { z ->
                    val tile = RemoteViews(ctx.packageName, R.layout.zamer_widget_tile)
                    val color = statusColor(z.status)
                    tile.setTextViewText(R.id.t_time, z.timeText())
                    tile.setInt(R.id.t_stripe, "setBackgroundColor", color)
                    tile.setTextViewText(R.id.t_status, z.status.label)
                    tile.setInt(R.id.t_status, "setBackgroundColor", color)

                    if (z.name.isNotBlank()) {
                        tile.setTextViewText(R.id.t_name, z.name)
                        tile.setViewVisibility(R.id.t_name, View.VISIBLE)
                    } else tile.setViewVisibility(R.id.t_name, View.GONE)

                    if (z.address.isNotBlank()) {
                        tile.setTextViewText(R.id.t_addr, z.address)
                        tile.setViewVisibility(R.id.t_addr, View.VISIBLE)
                    } else tile.setViewVisibility(R.id.t_addr, View.GONE)

                    val meta = listOf(
                        if (z.contactFrom.isNotBlank()) "От: " + z.contactFrom else "",
                        z.area, z.thickness
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        tile.setTextViewText(R.id.t_meta, meta)
                        tile.setViewVisibility(R.id.t_meta, View.VISIBLE)
                    } else tile.setViewVisibility(R.id.t_meta, View.GONE)

                    tile.setTextViewText(R.id.t_price, if (z.price.isNotBlank()) z.price + " ₽" else "")

                    val tel = z.phone.filter { c -> c.isDigit() || c == '+' }
                    if (tel.isNotEmpty()) {
                        tile.setOnClickPendingIntent(R.id.t_call, PendingIntent.getActivity(
                            ctx, (z.id % 1000000L).toInt() * 100 + 1,
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                        tile.setViewVisibility(R.id.t_call, View.VISIBLE)
                    } else tile.setViewVisibility(R.id.t_call, View.GONE)

                    if (z.address.isNotBlank()) {
                        tile.setOnClickPendingIntent(R.id.t_map, PendingIntent.getActivity(
                            ctx, (z.id % 1000000L).toInt() * 100 + 2,
                            Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://yandex.ru/maps/?text=" + Uri.encode(z.address)))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                        tile.setViewVisibility(R.id.t_map, View.VISIBLE)
                    } else tile.setViewVisibility(R.id.t_map, View.GONE)

                    tile.setOnClickPendingIntent(R.id.t_root, PendingIntent.getActivity(
                        ctx, (z.id % 1000000L).toInt() * 100 + 3,
                        Intent(ctx, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

                    rv.addView(R.id.w_tiles, tile)
                }
            }

            rv.setOnClickPendingIntent(R.id.w_title, PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            mgr.updateAppWidget(id, rv)
        } catch (e: Exception) {
            try {
                val rv = RemoteViews(ctx.packageName, R.layout.zamer_widget)
                rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
                rv.setTextViewText(R.id.w_empty, "Откройте «План замеров»")
                mgr.updateAppWidget(id, rv)
            } catch (e2: Exception) { }
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
