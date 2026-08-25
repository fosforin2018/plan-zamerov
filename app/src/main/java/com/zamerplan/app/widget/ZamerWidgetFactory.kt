package com.zamerplan.app.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.zamerplan.app.R
import com.zamerplan.app.model.Storage
import com.zamerplan.app.model.Zamer
import com.zamerplan.app.model.ZamerStatus
import java.time.LocalDate

class ZamerWidgetFactory(
    private val context: Context,
    intent: Intent?
) : RemoteViewsService.RemoteViewsFactory {

    private val zamers = mutableListOf<Zamer>()

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        val all = Storage(context).load()
        val today = LocalDate.now()
        val todayItems = all.filter { it.date == today }.sortedBy { it.time }
        val futureItems = all.filter { it.status == ZamerStatus.PLANNED && it.date > today }
            .sortedWith(compareBy({ it.date }, { it.time }))
        zamers.clear()
        zamers.addAll(todayItems + futureItems)
    }

    override fun onDestroy() {
        zamers.clear()
    }

    override fun getCount(): Int = zamers.size

    override fun getViewAt(position: Int): RemoteViews {
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
