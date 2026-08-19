package com.zamerplan.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.zamerplan.app.MainActivity
import com.zamerplan.app.R
import com.zamerplan.app.model.Storage
import com.zamerplan.app.model.ZamerStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ZamerWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(ctx, mgr, it) }
    }

    private fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val rv = RemoteViews(ctx.packageName, R.layout.zamer_widget)
        val all = Storage(ctx).load()
            .filter { it.status == ZamerStatus.PLANNED }
            .sortedWith(compareBy({ it.date }, { it.time }))
        val now = LocalDate.now()
        val today = all.filter { it.date == now || it.date == now.plusDays(1) }
        val next = all.firstOrNull {
            it.date.isAfter(now) || (it.date == now && it.time.isAfter(LocalTime.now()))
        }

        if (next == null) {
            rv.setTextViewText(R.id.w_title, "План замеров")
            rv.setTextViewText(R.id.w_line1, "Нет предстоящих замеров")
            rv.setTextViewText(R.id.w_line2, "Откройте приложение")
        } else {
            val dFmt = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag("ru"))
            rv.setTextViewText(R.id.w_title, "План замеров · " + today.size + " ближайш.")
            rv.setTextViewText(R.id.w_line1,
                next.date.format(dFmt) + " · " + next.timeText() +
                    " · " + (next.name.ifBlank { "Без имени" }))
            rv.setTextViewText(R.id.w_line2,
                next.address.ifBlank { "Адрес не указан" })
            if (next.phone.isNotBlank()) {
                val tel = next.phone.filter { it.isDigit() || it == '+' }
                val pi = PendingIntent.getActivity(
                    ctx, 1,
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                rv.setOnClickPendingIntent(R.id.w_call, pi)
            }
        }

        val openPi = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.w_root, openPi)

        mgr.updateAppWidget(id, rv)
    }

    companion object {
        fun refreshAll(ctx: Context) {
            val intent = Intent(ctx, ZamerWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(ctx, ZamerWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            ctx.sendBroadcast(intent)
        }
    }
}
