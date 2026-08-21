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

            // Получаем список замеров: сначала сегодняшние, потом будущие
            val all = Storage(ctx).load()
            val today = LocalDate.now()
            val todayItems = all.filter { it.date == today }.sortedBy { it.time }
            val upcoming = all.filter { it.status == ZamerStatus.PLANNED && it.date >= today }
                .sortedWith(compareBy({ it.date }, { it.time }))
            val list = todayItems + upcoming
            val maxCards = 8 // ограничение на количество карточек (можно больше)

            // Клик по корню – открыть приложение
            rv.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))

            if (list.isEmpty()) {
                rv.setViewVisibility(R.id.card_container, View.GONE)
                rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
                rv.setTextViewText(R.id.w_title, "📏 План замеров")
            } else {
                rv.setViewVisibility(R.id.card_container, View.VISIBLE)
                rv.setViewVisibility(R.id.w_empty, View.GONE)
                rv.setTextViewText(R.id.w_title, "📏 План замеров · ${list.size} шт.")

                // Берём до maxCards замеров
                val visibleList = list.take(maxCards)
                // Группируем по 2 (пары)
                val rows = visibleList.chunked(2)

                // Добавляем каждую пару как отдельный ряд
                rows.forEach { pair ->
                    val row = RemoteViews(ctx.packageName, R.layout.zamer_widget_row)
                    pair.forEach { z ->
                        val card = buildCardRemoteViews(ctx, z, statusColor(z.status))
                        row.addView(R.id.row_root, card) // id ряда? У нас в zamer_widget_row.xml нет id, но можно использовать сам корень (id = row_root?)
                        // Добавим id в XML ряда: android:id="@+id/row_root"
                        // Тогда row.addView(R.id.row_root, card) добавит карточку в ряд.
                    }
                    rv.addView(R.id.card_container, row)
                }
            }

            mgr.updateAppWidget(id, rv)
        } catch (e: Exception) {
            Log.e("ZamerWidget", "Ошибка обновления виджета", e)
        }
    }

    private fun buildCardRemoteViews(ctx: Context, z: Zamer, color: Int): RemoteViews {
        val card = RemoteViews(ctx.packageName, R.layout.zamer_widget_item)

        // Время и полоска
        card.setTextViewText(R.id.t_time, z.timeText())
        card.setInt(R.id.t_stripe, "setBackgroundColor", color)

        // Статус
        card.setTextViewText(R.id.t_status, z.status.label)
        card.setInt(R.id.t_status, "setBackgroundColor", color)

        // Имя
        if (z.name.isNotBlank()) {
            card.setTextViewText(R.id.t_name, z.name)
            card.setViewVisibility(R.id.t_name, View.VISIBLE)
        } else card.setViewVisibility(R.id.t_name, View.GONE)

        // Адрес
        if (z.address.isNotBlank()) {
            card.setTextViewText(R.id.t_addr, z.address)
            card.setViewVisibility(R.id.t_addr, View.VISIBLE)
        } else card.setViewVisibility(R.id.t_addr, View.GONE)

        // Мета
        val meta = listOf(
            if (z.contactFrom.isNotBlank()) "От: " + z.contactFrom else "",
            z.area,
            z.thickness
        ).filter { it.isNotBlank() }.joinToString(" · ")

        if (meta.isNotBlank()) {
            card.setTextViewText(R.id.t_meta, meta)
            card.setViewVisibility(R.id.t_meta, View.VISIBLE)
        } else card.setViewVisibility(R.id.t_meta, View.GONE)

        // Цена
        card.setTextViewText(R.id.t_price, if (z.price.isNotBlank()) z.price + " ₽" else "")

        // Кнопка "Позвонить"
        val tel = z.phone.filter { c -> c.isDigit() || c == '+' }
        if (tel.isNotEmpty()) {
            card.setOnClickPendingIntent(R.id.t_call, PendingIntent.getActivity(
                ctx, z.id.toInt(), Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            card.setViewVisibility(R.id.t_call, View.VISIBLE)
        } else card.setViewVisibility(R.id.t_call, View.GONE)

        // Кнопка "Карта"
        if (z.address.isNotBlank()) {
            card.setOnClickPendingIntent(R.id.t_map, PendingIntent.getActivity(
                ctx, z.id.toInt() + 1000, Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://yandex.ru/maps/?text=" + Uri.encode(z.address)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            card.setViewVisibility(R.id.t_map, View.VISIBLE)
        } else card.setViewVisibility(R.id.t_map, View.GONE)

        return card
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
