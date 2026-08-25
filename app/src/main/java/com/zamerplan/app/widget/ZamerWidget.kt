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
import com.zamerplan.app.model.ZamerStatus
import java.time.LocalDate

class ZamerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val rv = RemoteViews(context.packageName, R.layout.zamer_widget)

        // Заголовок
        val all = Storage(context).load()
        val today = LocalDate.now()
        val count = all.filter { it.status == ZamerStatus.PLANNED && it.date >= today }.size
        rv.setTextViewText(R.id.w_title, "План замеров · $count шт.")

        if (count == 0) {
            rv.setViewVisibility(R.id.widget_list, View.GONE)
            rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.widget_list, View.VISIBLE)
            rv.setViewVisibility(R.id.w_empty, View.GONE)

            val intent = Intent(context, ZamerWidgetService::class.java).apply {
                data = Uri.parse("zamerwidget://$widgetId")
            }
            rv.setRemoteAdapter(R.id.widget_list, intent)
        }

        // Клик по корню открывает приложение
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.w_root, openAppIntent)

        appWidgetManager.updateAppWidget(widgetId, rv)
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
