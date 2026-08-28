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
import com.zamerplan.app.alarm.ReminderScheduler
import com.zamerplan.app.model.Storage
import com.zamerplan.app.model.Zamer
import com.zamerplan.app.model.ZamerStatus
import java.io.File
import java.time.LocalDate

// Функция записи в лог (на верхнем уровне, доступна везде в файле)
private fun writeLog(context: Context, message: String) {
    try {
        val logFile = File(context.filesDir, "widget_log.txt")
        logFile.appendText("${System.currentTimeMillis()}: $message\n")
    } catch (e: Exception) {
        Log.e("ZamerWidget", "Ошибка записи лога", e)
    }
}

class ZamerWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_PREV = "com.zamerplan.app.widget.PREV"
        private const val ACTION_NEXT = "com.zamerplan.app.widget.NEXT"
        private const val ACTION_DONE = "com.zamerplan.app.widget.DONE"

        fun refreshAll(context: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, ZamerWidget::class.java))
                if (ids.isEmpty()) return
                ids.forEach { id -> ZamerWidget().updateWidget(context, mgr, id) }
            } catch (e: Exception) {
                Log.e("ZamerWidget", "Ошибка refreshAll", e)
                writeLog(context, "refreshAll exception: ${e.message}")
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        writeLog(context, "onUpdate called, ids count=${appWidgetIds.size}")
        try {
            for (widgetId in appWidgetIds) {
                writeLog(context, "onUpdate: updating widget $widgetId")
                updateWidget(context, appWidgetManager, widgetId)
            }
        } catch (e: Exception) {
            writeLog(context, "onUpdate exception: ${e.message}")
            Log.e("ZamerWidget", "onUpdate exception", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        writeLog(context, "onReceive action=${intent.action}")
        try {
            when (intent.action) {
                ACTION_NEXT -> {
                    val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    writeLog(context, "ACTION_NEXT, widgetId=$widgetId")
                    if (widgetId != -1) {
                        val rv = RemoteViews(context.packageName, R.layout.zamer_widget)
                        rv.showNext(R.id.view_flipper)
                        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, rv)
                    }
                }
                ACTION_PREV -> {
                    val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    writeLog(context, "ACTION_PREV, widgetId=$widgetId")
                    if (widgetId != -1) {
                        val rv = RemoteViews(context.packageName, R.layout.zamer_widget)
                        rv.showPrevious(R.id.view_flipper)
                        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, rv)
                    }
                }
                ACTION_DONE -> {
                    val zamerId = intent.getLongExtra("zamer_id", -1L)
                    val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    writeLog(context, "ACTION_DONE, zamerId=$zamerId, widgetId=$widgetId")
                    if (zamerId != -1L) {
                        val storage = Storage(context)
                        val list = storage.load().toMutableList()
                        val index = list.indexOfFirst { it.id == zamerId }
                        writeLog(context, "index=$index, list size=${list.size}")
                        if (index != -1) {
                            list[index] = list[index].copy(status = ZamerStatus.DONE)
                            storage.save(list)
                            ReminderScheduler.cancel(context, zamerId)
                            if (widgetId != -1) {
                                val mgr = AppWidgetManager.getInstance(context)
                                updateWidget(context, mgr, widgetId)
                            } else {
                                refreshAll(context)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            writeLog(context, "onReceive exception: ${e.message}")
            Log.e("ZamerWidget", "onReceive exception", e)
        }
    }

    private fun statusColor(s: ZamerStatus): Int = when (s) {
        ZamerStatus.PLANNED -> 0xFFF4511E.toInt()
        ZamerStatus.DONE -> 0xFF43A047.toInt()
        ZamerStatus.POSTPONED -> 0xFF9E9E9E.toInt()
        ZamerStatus.CANCELLED -> 0xFFE53935.toInt()
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        writeLog(context, "updateWidget started for widgetId=$widgetId")
        try {
            val all = Storage(context).load()
            writeLog(context, "loaded ${all.size} zamers")
            val today = LocalDate.now()
            val todayItems = all.filter { it.date == today && it.status == ZamerStatus.PLANNED }
                .sortedBy { it.time }
            val futureItems = all.filter { it.status == ZamerStatus.PLANNED && it.date > today }
                .sortedWith(compareBy({ it.date }, { it.time }))
            val list = (todayItems + futureItems).distinctBy { it.id }
            writeLog(context, "filtered list size=${list.size}")

            val root = RemoteViews(context.packageName, R.layout.zamer_widget)

            val total = list.size
            root.setTextViewText(R.id.w_title, "📏 План замеров · всего $total")
            writeLog(context, "title set")

            if (total == 0) {
                root.setViewVisibility(R.id.view_flipper, View.GONE)
                root.setViewVisibility(R.id.btn_prev, View.GONE)
                root.setViewVisibility(R.id.btn_next, View.GONE)
                root.setViewVisibility(R.id.w_empty, View.VISIBLE)
                writeLog(context, "empty state set")
            } else {
                root.setViewVisibility(R.id.w_empty, View.GONE)
                root.setViewVisibility(R.id.view_flipper, View.VISIBLE)
                root.setViewVisibility(R.id.btn_prev, View.VISIBLE)
                root.setViewVisibility(R.id.btn_next, View.VISIBLE)

                // Очищаем ViewFlipper от старых страниц
                root.removeAllViews(R.id.view_flipper)
                writeLog(context, "view_flipper cleared")

                val pageSize = 4
                val pageCount = (total + pageSize - 1) / pageSize
                writeLog(context, "pageCount=$pageCount")

                for (page in 0 until pageCount) {
                    val pageRemoteViews = createPage(context, list, page, pageSize, widgetId)
                    root.addView(R.id.view_flipper, pageRemoteViews)
                    writeLog(context, "added page $page")
                }

                val prevIntent = PendingIntent.getBroadcast(
                    context,
                    widgetId * 10 + 1,
                    Intent(context, ZamerWidget::class.java).apply {
                        action = ACTION_PREV
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                root.setOnClickPendingIntent(R.id.btn_prev, prevIntent)

                val nextIntent = PendingIntent.getBroadcast(
                    context,
                    widgetId * 10 + 2,
                    Intent(context, ZamerWidget::class.java).apply {
                        action = ACTION_NEXT
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                root.setOnClickPendingIntent(R.id.btn_next, nextIntent)
                writeLog(context, "navigation intents set")
            }

            val openAppIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            root.setOnClickPendingIntent(R.id.w_root, openAppIntent)

            appWidgetManager.updateAppWidget(widgetId, root)
            writeLog(context, "updateWidget finished successfully")
        } catch (e: Exception) {
            writeLog(context, "updateWidget exception: ${e.message}")
            Log.e("ZamerWidget", "updateWidget exception", e)
        }
    }

    private fun createPage(
        context: Context,
        list: List<Zamer>,
        page: Int,
        pageSize: Int,
        widgetId: Int
    ): RemoteViews {
        writeLog(context, "createPage page=$page")
        val pageRemoteViews = RemoteViews(context.packageName, R.layout.zamer_widget_page)
        val start = page * pageSize
        val end = minOf(start + pageSize, list.size)
        writeLog(context, "createPage start=$start, end=$end")

        for (i in start until end) {
            val z = list[i]
            try {
                when (i - start) {
                    0 -> fillCard(pageRemoteViews, context, z,
                        R.id.t1_time, R.id.t1_name, R.id.t1_addr, R.id.t1_from,
                        R.id.t1_call, R.id.t1_map, R.id.t1_done, R.id.t1_mic, widgetId)
                    1 -> fillCard(pageRemoteViews, context, z,
                        R.id.t2_time, R.id.t2_name, R.id.t2_addr, R.id.t2_from,
                        R.id.t2_call, R.id.t2_map, R.id.t2_done, R.id.t2_mic, widgetId)
                    2 -> fillCard(pageRemoteViews, context, z,
                        R.id.t3_time, R.id.t3_name, R.id.t3_addr, R.id.t3_from,
                        R.id.t3_call, R.id.t3_map, R.id.t3_done, R.id.t3_mic, widgetId)
                    3 -> fillCard(pageRemoteViews, context, z,
                        R.id.t4_time, R.id.t4_name, R.id.t4_addr, R.id.t4_from,
                        R.id.t4_call, R.id.t4_map, R.id.t4_done, R.id.t4_mic, widgetId)
                }
                writeLog(context, "fillCard for $i done")
            } catch (e: Exception) {
                writeLog(context, "fillCard exception for $i: ${e.message}")
                Log.e("ZamerWidget", "fillCard exception", e)
            }
        }

        for (i in (end - start) until 4) {
            val cardId = when (i) {
                0 -> R.id.card_1
                1 -> R.id.card_2
                2 -> R.id.card_3
                else -> R.id.card_4
            }
            pageRemoteViews.setViewVisibility(cardId, View.GONE)
        }
        writeLog(context, "createPage finished")
        return pageRemoteViews
    }

    private fun fillCard(
        rv: RemoteViews,
        context: Context,
        z: Zamer,
        timeId: Int,
        nameId: Int,
        addrId: Int,
        fromId: Int,
        callId: Int,
        mapId: Int,
        doneId: Int,
        micId: Int,
        widgetId: Int
    ) {
        writeLog(context, "fillCard for zamer ${z.id}")
        rv.setTextViewText(timeId, z.timeText())
        rv.setInt(timeId, "setTextColor", statusColor(z.status))

        if (z.name.isNotBlank()) {
            rv.setTextViewText(nameId, z.name)
            rv.setViewVisibility(nameId, View.VISIBLE)
        } else rv.setViewVisibility(nameId, View.GONE)

        if (z.address.isNotBlank()) {
            rv.setTextViewText(addrId, z.address)
            rv.setViewVisibility(addrId, View.VISIBLE)
        } else rv.setViewVisibility(addrId, View.GONE)

        if (z.contactFrom.isNotBlank()) {
            rv.setTextViewText(fromId, "От: " + z.contactFrom)
            rv.setViewVisibility(fromId, View.VISIBLE)
        } else rv.setViewVisibility(fromId, View.GONE)

        // Кнопка звонка
        val tel = z.phone.filter { c -> c.isDigit() || c == '+' }
        if (tel.isNotEmpty()) {
            rv.setOnClickPendingIntent(callId, PendingIntent.getActivity(
                context,
                z.id.toInt(),
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            rv.setViewVisibility(callId, View.VISIBLE)
        } else rv.setViewVisibility(callId, View.GONE)

        // Кнопка карты
        if (z.address.isNotBlank()) {
            rv.setOnClickPendingIntent(mapId, PendingIntent.getActivity(
                context,
                z.id.toInt() + 1000,
                Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?text=" + Uri.encode(z.address)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            rv.setViewVisibility(mapId, View.VISIBLE)
        } else rv.setViewVisibility(mapId, View.GONE)

        // Кнопка "Готово"
        val doneIntent = PendingIntent.getBroadcast(
            context,
            z.id.toInt() + 2000,
            Intent(context, ZamerWidget::class.java).apply {
                action = ACTION_DONE
                putExtra("zamer_id", z.id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(doneId, doneIntent)
        rv.setViewVisibility(doneId, View.VISIBLE)

        // Микрофон
        val voiceFile = File(context.filesDir, "voice_${z.id}.m4a")
        writeLog(context, "voice file exists: ${voiceFile.exists()}")
        if (voiceFile.exists()) {
            val playIntent = PendingIntent.getService(
                context,
                z.id.toInt() + 3000,
                Intent(context, VoicePlaybackService::class.java).apply {
                    putExtra("voice_file_path", voiceFile.absolutePath)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(micId, playIntent)
            rv.setViewVisibility(micId, View.VISIBLE)
        } else {
            rv.setViewVisibility(micId, View.GONE)
        }
        writeLog(context, "fillCard finished")
    }
}
