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

// Функция логирования на верхнем уровне
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
        private const val ACTION_PREV_PAGE = "com.zamerplan.app.widget.PREV_PAGE"
        private const val ACTION_NEXT_PAGE = "com.zamerplan.app.widget.NEXT_PAGE"
        private const val ACTION_DONE = "com.zamerplan.app.widget.DONE"
        private const val PREFS_NAME = "widget_page_state"
        private const val KEY_PAGE = "page"

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
                ACTION_PREV_PAGE -> {
                    val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    writeLog(context, "ACTION_PREV_PAGE, widgetId=$widgetId")
                    if (widgetId != -1) {
                        changePage(context, widgetId, -1)
                    }
                }
                ACTION_NEXT_PAGE -> {
                    val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    writeLog(context, "ACTION_NEXT_PAGE, widgetId=$widgetId")
                    if (widgetId != -1) {
                        changePage(context, widgetId, +1)
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

    private fun changePage(context: Context, widgetId: Int, delta: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentPage = prefs.getInt(KEY_PAGE, 0)
        val totalPages = getTotalPages(context)
        writeLog(context, "changePage: current=$currentPage, delta=$delta, total=$totalPages")
        if (totalPages == 0) return

        var newPage = currentPage + delta
        if (newPage < 0) newPage = 0
        if (newPage >= totalPages) newPage = totalPages - 1

        prefs.edit().putInt(KEY_PAGE, newPage).apply()
        val mgr = AppWidgetManager.getInstance(context)
        updateWidget(context, mgr, widgetId)
    }

    private fun getTotalPages(context: Context): Int {
        val all = Storage(context).load()
        val today = LocalDate.now()
        val todayItems = all.filter { it.date == today && it.status == ZamerStatus.PLANNED }
            .sortedBy { it.time }
        val futureItems = all.filter { it.status == ZamerStatus.PLANNED && it.date > today }
            .sortedWith(compareBy({ it.date }, { it.time }))
        val list = (todayItems + futureItems).distinctBy { it.id }
        val pageSize = 4
        return if (list.isEmpty()) 0 else (list.size + pageSize - 1) / pageSize
    }

    private fun getCurrentPage(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PAGE, 0)
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

            val pageSize = 4
            val totalPages = if (list.isEmpty()) 0 else (list.size + pageSize - 1) / pageSize
            var currentPage = getCurrentPage(context)
            if (currentPage >= totalPages) {
                currentPage = if (totalPages > 0) totalPages - 1 else 0
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_PAGE, currentPage).apply()
            }

            val start = currentPage * pageSize
            val end = minOf(start + pageSize, list.size)
            val pageItems = list.subList(start, end)
            writeLog(context, "pageItems count=${pageItems.size}, currentPage=$currentPage, totalPages=$totalPages")

            val root = RemoteViews(context.packageName, R.layout.zamer_widget)

            val total = list.size
            root.setTextViewText(R.id.w_title, "📏 План замеров · всего $total (стр. ${currentPage + 1}/$totalPages)")
            writeLog(context, "title set")

            if (pageItems.isEmpty()) {
                root.setViewVisibility(R.id.cards_container, View.GONE)
                root.setViewVisibility(R.id.btn_prev, View.GONE)
                root.setViewVisibility(R.id.btn_next, View.GONE)
                root.setViewVisibility(R.id.w_empty, View.VISIBLE)
                writeLog(context, "empty state set")
            } else {
                root.setViewVisibility(R.id.w_empty, View.GONE)
                root.setViewVisibility(R.id.cards_container, View.VISIBLE)

                root.setViewVisibility(R.id.btn_prev, if (currentPage > 0) View.VISIBLE else View.GONE)
                root.setViewVisibility(R.id.btn_next, if (currentPage < totalPages - 1) View.VISIBLE else View.GONE)

                val cardIds = intArrayOf(R.id.card_1, R.id.card_2, R.id.card_3, R.id.card_4)
                val timeIds = intArrayOf(R.id.t1_time, R.id.t2_time, R.id.t3_time, R.id.t4_time)
                val nameIds = intArrayOf(R.id.t1_name, R.id.t2_name, R.id.t3_name, R.id.t4_name)
                val addrIds = intArrayOf(R.id.t1_addr, R.id.t2_addr, R.id.t3_addr, R.id.t4_addr)
                val fromIds = intArrayOf(R.id.t1_from, R.id.t2_from, R.id.t3_from, R.id.t4_from)
                val callIds = intArrayOf(R.id.t1_call, R.id.t2_call, R.id.t3_call, R.id.t4_call)
                val mapIds = intArrayOf(R.id.t1_map, R.id.t2_map, R.id.t3_map, R.id.t4_map)
                val doneIds = intArrayOf(R.id.t1_done, R.id.t2_done, R.id.t3_done, R.id.t4_done)
                val micIds = intArrayOf(R.id.t1_mic, R.id.t2_mic, R.id.t3_mic, R.id.t4_mic)

                for (i in 0 until 4) {
                    if (i < pageItems.size) {
                        root.setViewVisibility(cardIds[i], View.VISIBLE)
                        fillCard(root, context, pageItems[i],
                            timeIds[i], nameIds[i], addrIds[i], fromIds[i],
                            callIds[i], mapIds[i], doneIds[i], micIds[i], widgetId)
                        writeLog(context, "fillCard for $i done")
                    } else {
                        root.setViewVisibility(cardIds[i], View.GONE)
                    }
                }
            }

            val openAppIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            root.setOnClickPendingIntent(R.id.w_root, openAppIntent)

            val prevIntent = PendingIntent.getBroadcast(
                context,
                widgetId * 10 + 1,
                Intent(context, ZamerWidget::class.java).apply {
                    action = ACTION_PREV_PAGE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            root.setOnClickPendingIntent(R.id.btn_prev, prevIntent)

            val nextIntent = PendingIntent.getBroadcast(
                context,
                widgetId * 10 + 2,
                Intent(context, ZamerWidget::class.java).apply {
                    action = ACTION_NEXT_PAGE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            root.setOnClickPendingIntent(R.id.btn_next, nextIntent)

            appWidgetManager.updateAppWidget(widgetId, root)
            writeLog(context, "updateWidget finished successfully")
        } catch (e: Exception) {
            writeLog(context, "updateWidget exception: ${e.message}")
            Log.e("ZamerWidget", "updateWidget exception", e)
        }
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

    private fun statusColor(s: ZamerStatus): Int = when (s) {
        ZamerStatus.PLANNED -> 0xFFF4511E.toInt()
        ZamerStatus.DONE -> 0xFF43A047.toInt()
        ZamerStatus.POSTPONED -> 0xFF9E9E9E.toInt()
        ZamerStatus.CANCELLED -> 0xFFE53935.toInt()
    }
}
