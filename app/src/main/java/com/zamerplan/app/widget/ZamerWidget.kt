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

    // Список ID карточек
    private val cardIds = listOf(
        R.id.card_1, R.id.card_2, R.id.card_3,
        R.id.card_4, R.id.card_5, R.id.card_6
    )

    private fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
        try {
            val rv = RemoteViews(ctx.packageName, R.layout.zamer_widget)

            // Получаем список замеров
            val all = Storage(ctx).load()
            val today = LocalDate.now()
            val todayItems = all.filter { it.date == today }.sortedBy { it.time }
            val upcoming = all.filter { it.status == ZamerStatus.PLANNED && it.date >= today }
                .sortedWith(compareBy({ it.date }, { it.time }))
            val list = (todayItems + upcoming).take(6) // максимум 6

            // Клик по корню – открыть приложение
            rv.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))

            if (list.isEmpty()) {
                rv.setViewVisibility(R.id.w_empty, View.VISIBLE)
                cardIds.forEach { cardId -> rv.setViewVisibility(cardId, View.GONE) }
                rv.setTextViewText(R.id.w_title, "📏 План замеров")
            } else {
                rv.setViewVisibility(R.id.w_empty, View.GONE)
                rv.setTextViewText(R.id.w_title, "📏 План замеров · ${list.size} шт.")

                // Проходим по всем карточкам
                cardIds.forEachIndexed { index, cardId ->
                    val z = list.getOrNull(index)
                    if (z != null) {
                        rv.setViewVisibility(cardId, View.VISIBLE)
                        // Заполняем поля в зависимости от индекса
                        val prefix = "t${index + 1}_"
                        fillCard(rv, ctx, z, prefix, statusColor(z.status))
                    } else {
                        rv.setViewVisibility(cardId, View.GONE)
                    }
                }
            }

            mgr.updateAppWidget(id, rv)
        } catch (e: Exception) {
            Log.e("ZamerWidget", "Ошибка обновления виджета", e)
        }
    }

    private fun fillCard(rv: RemoteViews, ctx: Context, z: Zamer, prefix: String, color: Int) {
        // Динамически формируем ID
        val timeId = ctx.resources.getIdentifier(prefix + "time", "id", ctx.packageName)
        val stripeId = ctx.resources.getIdentifier(prefix + "stripe", "id", ctx.packageName)
        val statusId = ctx.resources.getIdentifier(prefix + "status", "id", ctx.packageName)
        val nameId = ctx.resources.getIdentifier(prefix + "name", "id", ctx.packageName)
        val addrId = ctx.resources.getIdentifier(prefix + "addr", "id", ctx.packageName)
        val metaId = ctx.resources.getIdentifier(prefix + "meta", "id", ctx.packageName)
        val priceId = ctx.resources.getIdentifier(prefix + "price", "id", ctx.packageName)
        val callId = ctx.resources.getIdentifier(prefix + "call", "id", ctx.packageName)
        val mapId = ctx.resources.getIdentifier(prefix + "map", "id", ctx.packageName)

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

        // Мета (от кого, площадь, толщина)
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
                if (ids.isEmpty()) return
                ids.forEach { id -> ZamerWidget().update(ctx, mgr, id) }
            } catch (e: Exception) {
                Log.e("ZamerWidget", "Ошибка refreshAll", e)
            }
        }
    }
}
