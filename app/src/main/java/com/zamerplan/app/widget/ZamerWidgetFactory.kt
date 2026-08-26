package com.zamerplan.app.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.zamerplan.app.R
import com.zamerplan.app.model.Storage
import com.zamerplan.app.model.Zamer
import com.zamerplan.app.model.ZamerStatus
import java.io.File
import java.time.LocalDate

class ZamerWidgetFactory(
    private val context: Context,
    intent: Intent?
) : RemoteViewsService.RemoteViewsFactory {

    private val zamers = mutableListOf<Zamer>()

    // Функция для добавления записи в файл widget_log.txt
    private fun log(message: String) {
        try {
            val logFile = File(context.filesDir, "widget_log.txt")
            logFile.appendText("${System.currentTimeMillis()}: $message\n")
        } catch (e: Exception) {
            Log.e("ZamerWidgetFactory", "Не удалось записать лог", e)
        }
    }

    override fun onCreate() {
        log("onCreate")
        loadData()
    }

    override fun onDataSetChanged() {
        log("onDataSetChanged")
        loadData()
        log("После загрузки: ${zamers.size} замеров")
    }

    private fun loadData() {
        val all = Storage(context).load()
        val today = LocalDate.now()
        val todayItems = all.filter { it.date == today }.sortedBy { it.time }
        val futureItems = all.filter { it.status == ZamerStatus.PLANNED && it.date > today }
            .sortedWith(compareBy({ it.date }, { it.time }))
        zamers.clear()
        zamers.addAll(todayItems + futureItems)
        log("Загружено замеров: ${zamers.size}")
    }

    override fun onDestroy() {
        log("onDestroy")
        zamers.clear()
    }

    override fun getCount(): Int {
        log("getCount: ${zamers.size}")
        return zamers.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        log("getViewAt($position): ${zamers[position].name}")
        val z = zamers[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_list_item)

        // Время
        rv.setTextViewText(R.id.t_time, z.timeText())

        // Статус
        val statusColor = when (z.status) {
            ZamerStatus.PLANNED -> 0xFFF4511E.toInt()
            ZamerStatus.DONE -> 0xFF43A047.toInt()
            ZamerStatus.POSTPONED -> 0xFF9E9E9E.toInt()
            ZamerStatus.CANCELLED -> 0xFFE53935.toInt()
        }
        rv.setInt(R.id.t_status, "setBackgroundColor", statusColor)
        rv.setTextViewText(R.id.t_status, z.status.label)

        // Имя
        rv.setTextViewText(R.id.t_name, z.name.ifBlank { " " })

        // Адрес
        rv.setTextViewText(R.id.t_addr, z.address.ifBlank { " " })

        return rv
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = zamers[position].id

    override fun hasStableIds(): Boolean = true
}
