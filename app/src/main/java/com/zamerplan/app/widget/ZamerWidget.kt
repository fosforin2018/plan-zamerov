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
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ZamerWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        logToFile(ctx, "onUpdate вызван, ids: ${ids.joinToString()}")
        ids.forEach { update(ctx, mgr, it) }
    }

    private fun statusColor(s: ZamerStatus): Int = when (s) {
        ZamerStatus.PLANNED -> 0xFFF4511E.toInt()
        ZamerStatus.DONE -> 0xFF43A047.toInt()
        ZamerStatus.POSTPONED -> 0xFF9E9E9E.toInt()
        ZamerStatus.CANCELLED -> 0xFFE53935.toInt()
    }

    // Список ID карточек
    private val cardIds = listOf(
        R.id.card_1, R.id.card_2, R.id.card_3,
        R.id.card_4, R.id.card_5, R.id.card_6
    )

    private fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
        try {
            logToFile(ctx, "Обновление виджета id=$id")
            val rv = RemoteViews(ctx.packageName, R.layout.zamer_widget)

            // Получаем список замеров
            val all = Storage(ctx).load()
            logToFile(ctx, "Загружено замеров из Storage: ${all.size}")

            val today = LocalDate.now()
            val todayItems = all.filter { it.date == today }.sortedBy { it.time }
            val upcoming = all.filter { it.status == ZamerStatus.PLANNED && it.date >= today }
                .sortedWith(compareBy({ it.date }, { it.time }))
            val list = (todayItems + upcoming).take(6)
            logToFile(ctx, "Отображаемых замеров: ${list.size}")

            // Клик по корню – открыть приложение
            rv.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))

            if (list.isEmpty()) {
                rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
                cardIds.forEach { cardId -> rv.setViewVisibility(cardId, View.GONE) }
                rv.setTextViewText(R.id.w_title, "📏 План замеров")
                logToFile(ctx, "Список пуст – показываем заглушку")
            } else {
                rv.setViewVisibility(R.id.w_empty, View.GONE)
                rv.setTextViewText(R.id.w_title, "📏 План замеров · ${list.size} шт.")

                // Заполняем карточки
                fillCard(rv, ctx, list[0], R.id.card_1,
                    R.id.t1_time, R.id.t1_stripe, R.id.t1_status,
                    R.id.t1_name, R.id.t1_addr, R.id.t1_meta,
                    R.id.t1_price, R.id.t1_call, R.id.t1_map, statusColor(list[0].status))

                if (list.size > 1) {
                    rv.setViewVisibility(R.id.card_2, View.VISIBLE)
                    fillCard(rv, ctx, list[1], R.id.card_2,
                        R.id.t2_time, R.id.t2_stripe, R.id.t2_status,
                        R.id.t2_name, R.id.t2_addr, R.id.t2_meta,
                        R.id.t2_price, R.id.t2_call, R.id.t2_map, statusColor(list[1].status))
                } else rv.setViewVisibility(R.id.card_2, View.GONE)

                if (list.size > 2) {
                    rv.setViewVisibility(R.id.card_3, View.VISIBLE)
                    fillCard(rv, ctx, list[2], R.id.card_3,
                        R.id.t3_time, R.id.t3_stripe, R.id.t3_status,
                        R.id.t3_name, R.id.t3_addr, R.id.t3_meta,
                        R.id.t3_price, R.id.t3_call, R.id.t3_map, statusColor(list[2].status))
                } else rv.setViewVisibility(R.id.card_3, View.GONE)

                if (list.size > 3) {
                    rv.setViewVisibility(R.id.card_4, View.VISIBLE)
                    fillCard(rv, ctx, list[3], R.id.card_4,
                        R.id.t4_time, R.id.t4_stripe, R.id.t4_status,
                        R.id.t4_name, R.id.t4_addr, R.id.t4_meta,
                        R.id.t4_price, R.id.t4_call, R.id.t4_map, statusColor(list[3].status))
                } else rv.setViewVisibility(R.id.card_4, View.GONE)

                if (list.size > 4) {
                    rv.setViewVisibility(R.id.card_5, View.VISIBLE)
                    fillCard(rv, ctx, list[4], R.id.card_5,
                        R.id.t5_time, R.id.t5_stripe, R.id.t5_status,
                        R.id.t5_name, R.id.t5_addr, R.id.t5_meta,
                        R.id.t5_price, R.id.t5_call, R.id.t5_map, statusColor(list[4].status))
                } else rv.setViewVisibility(R.id.card_5, View.GONE)

                if (list.size > 5) {
                    rv.setViewVisibility(R.id.card_6, View.VISIBLE)
                    fillCard(rv, ctx, list[5], R.id.card_6,
                        R.id.t6_time, R.id.t6_stripe, R.id.t6_status,
                        R.id.t6_name, R.id.t6_addr, R.id.t6_meta,
                        R.id.t6_price, R.id.t6_call, R.id.t6_map, statusColor(list[5].status))
                } else rv.setViewVisibility(R.id.card_6, View.GONE)
            }

            mgr.updateAppWidget(id, rv)
            logToFile(ctx, "Виджет успешно обновлён id=$id")
        } catch (e: Exception) {
            Log.e("ZamerWidget", "Ошибка обновления виджета", e)
            logToFile(ctx, "ОШИБКА: ${e.message}\n${e.stackTraceToString()}")
        }
    }

    private fun fillCard(
        rv: RemoteViews, ctx: Context, z: Zamer,
        cardId: Int,
        timeId: Int, stripeId: Int, statusId: Int,
        nameId: Int, addrId: Int, metaId: Int,
        priceId: Int, callId: Int, mapId: Int,
        color: Int
    ) {
        // Показываем карточку
        rv.setViewVisibility(cardId, View.VISIBLE)

        // Время и полоска
        rv.setTextViewText(timeId, z.timeText())
        rv.setInt(stripeId, "setBackgroundColor", color)

        // Статус
        rv.setTextViewText(statusId, z.status.label)
        rv.setInt(statusId, "setBackgroundColor", color)

        // Имя
        if (z.name.isNotBlank()) {
            rv.setTextViewText(nameId, z.name)
            rv.setViewVisibility(nameId, View.VISIBLE)
        } else rv.setViewVisibility(nameId, View.GONE)

        // Адрес
        if (z.address.isNotBlank()) {
            rv.setTextViewText(addrId, z.address)
            rv.setViewVisibility(addrId, View.VISIBLE)
        } else rv.setViewVisibility(addrId, View.GONE)

        // Мета
        val meta = listOf(
            if (z.contactFrom.isNotBlank()) "От: " + z.contactFrom else "",
            z.area,
            z.thickness
        ).filter { it.isNotBlank() }.joinToString(" · ")
        if (meta.isNotBlank()) {
            rv.setTextViewText(metaId, meta)
            rv.setViewVisibility(metaId, View.VISIBLE)
        } else rv.setViewVisibility(metaId, View.GONE)

        // Цена
        rv.setTextViewText(priceId, if (z.price.isNotBlank()) z.price + " ₽" else "")

        // Кнопка "Позвонить"
        val tel = z.phone.filter { c -> c.isDigit() || c == '+' }
        if (tel.isNotEmpty()) {
            rv.setOnClickPendingIntent(callId, PendingIntent.getActivity(
                ctx, z.id.toInt(), Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            rv.setViewVisibility(callId, View.VISIBLE)
        } else rv.setViewVisibility(callId, View.GONE)

        // Кнопка "Карта"
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
                if (ids.isEmpty()) {
                    logToFile(ctx, "refreshAll: нет активных виджетов")
                    return
                }
                ids.forEach { id -> ZamerWidget().update(ctx, mgr, id) }
            } catch (e: Exception) {
                Log.e("ZamerWidget", "Ошибка refreshAll", e)
                logToFile(ctx, "ОШИБКА refreshAll: ${e.message}")
            }
        }

        // Запись логов в файл
        fun logToFile(ctx: Context, message: String) {
            try {
                val file = File(ctx.filesDir, "widget_log.txt")
                val existing = if (file.exists()) file.readText() else ""
                val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
                file.writeText(existing + "\n[$time] $message")
            } catch (e: Exception) {
                // игнорируем
            }
        }
    }
}
