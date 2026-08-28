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

private fun writeLog(context: Context, message: String) {
    try {
        val file = File(context.filesDir, "widget_log.txt")
        file.appendText(
            "${System.currentTimeMillis()}: $message\n"
        )
    } catch (e: Exception) {
        Log.e("ZamerWidget", "Ошибка записи лога", e)
    }
}

class ZamerWidget : AppWidgetProvider() {

    companion object {

        private const val TAG = "ZamerWidget"

        private const val ACTION_PREV_PAGE =
            "com.zamerplan.app.widget.PREV_PAGE"

        private const val ACTION_NEXT_PAGE =
            "com.zamerplan.app.widget.NEXT_PAGE"

        private const val ACTION_DONE =
            "com.zamerplan.app.widget.DONE"

        private const val PREFS_NAME =
            "widget_page_state"

        private const val KEY_PAGE =
            "page"

        fun refreshAll(context: Context) {

            val appContext = context.applicationContext

            try {

                val manager =
                    AppWidgetManager.getInstance(appContext)

                val componentName =
                    ComponentName(
                        appContext,
                        ZamerWidget::class.java
                    )

                val ids =
                    manager.getAppWidgetIds(componentName)

                writeLog(
                    appContext,
                    "refreshAll: widget count=${ids.size}"
                )

                for (id in ids) {

                    try {

                        ZamerWidget().updateWidget(
                            appContext,
                            manager,
                            id
                        )

                    } catch (e: Exception) {

                        writeLog(
                            appContext,
                            "refreshAll widget=$id ERROR: ${e.stackTraceToString()}"
                        )
                    }
                }

            } catch (e: Exception) {

                writeLog(
                    appContext,
                    "refreshAll ERROR: ${e.stackTraceToString()}"
                )

                Log.e(
                    TAG,
                    "refreshAll error",
                    e
                )
            }
        }
    }

    override fun onEnabled(context: Context) {

        super.onEnabled(context)

        writeLog(
            context,
            "onEnabled"
        )
    }

    override fun onDisabled(context: Context) {

        super.onDisabled(context)

        writeLog(
            context,
            "onDisabled"
        )
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {

        val appContext =
            context.applicationContext

        writeLog(
            appContext,
            "onUpdate called, ids count=${appWidgetIds.size}"
        )

        for (widgetId in appWidgetIds) {

            try {

                writeLog(
                    appContext,
                    "onUpdate: updating widget $widgetId"
                )

                updateWidget(
                    appContext,
                    appWidgetManager,
                    widgetId
                )

            } catch (e: Exception) {

                writeLog(
                    appContext,
                    "onUpdate ERROR widget=$widgetId: ${e.stackTraceToString()}"
                )

                Log.e(
                    TAG,
                    "onUpdate error",
                    e
                )
            }
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        val appContext =
            context.applicationContext

        writeLog(
            appContext,
            "onReceive action=${intent.action}"
        )

        try {

            super.onReceive(
                appContext,
                intent
            )

            when (intent.action) {

                ACTION_PREV_PAGE -> {

                    val widgetId =
                        intent.getIntExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID
                        )

                    writeLog(
                        appContext,
                        "PREV_PAGE widget=$widgetId"
                    )

                    if (
                        widgetId !=
                        AppWidgetManager.INVALID_APPWIDGET_ID
                    ) {

                        changePage(
                            appContext,
                            widgetId,
                            -1
                        )
                    }
                }

                ACTION_NEXT_PAGE -> {

                    val widgetId =
                        intent.getIntExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID
                        )

                    writeLog(
                        appContext,
                        "NEXT_PAGE widget=$widgetId"
                    )

                    if (
                        widgetId !=
                        AppWidgetManager.INVALID_APPWIDGET_ID
                    ) {

                        changePage(
                            appContext,
                            widgetId,
                            1
                        )
                    }
                }

                ACTION_DONE -> {

                    val zamerId =
                        intent.getLongExtra(
                            "zamer_id",
                            -1L
                        )

                    val widgetId =
                        intent.getIntExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID
                        )

                    writeLog(
                        appContext,
                        "DONE zamerId=$zamerId widgetId=$widgetId"
                    )

                    if (zamerId != -1L) {

                        val storage =
                            Storage(appContext)

                        val list =
                            storage.load().toMutableList()

                        val index =
                            list.indexOfFirst {
                                it.id == zamerId
                            }

                        writeLog(
                            appContext,
                            "DONE index=$index list=${list.size}"
                        )

                        if (index >= 0) {

                            list[index] =
                                list[index].copy(
                                    status = ZamerStatus.DONE
                                )

                            storage.save(list)

                            try {

                                ReminderScheduler.cancel(
                                    appContext,
                                    zamerId
                                )

                            } catch (e: Exception) {

                                writeLog(
                                    appContext,
                                    "ReminderScheduler.cancel ERROR: ${e.stackTraceToString()}"
                                )
                            }

                            refreshAll(appContext)
                        }
                    }
                }
            }

        } catch (e: Exception) {

            writeLog(
                appContext,
                "onReceive ERROR: ${e.stackTraceToString()}"
            )

            Log.e(
                TAG,
                "onReceive error",
                e
            )
        }
    }

    private fun getPlannedZamers(
        context: Context
    ): List<Zamer> {

        return try {

            val all =
                Storage(context).load()

            val today =
                LocalDate.now()

            val todayItems =
                all
                    .filter {
                        it.date == today &&
                        it.status == ZamerStatus.PLANNED
                    }
                    .sortedBy {
                        it.time
                    }

            val futureItems =
                all
                    .filter {
                        it.date > today &&
                        it.status == ZamerStatus.PLANNED
                    }
                    .sortedWith(
                        compareBy(
                            { it.date },
                            { it.time }
                        )
                    )

            (
                todayItems + futureItems
            ).distinctBy {
                it.id
            }

        } catch (e: Exception) {

            writeLog(
                context,
                "getPlannedZamers ERROR: ${e.stackTraceToString()}"
            )

            emptyList()
        }
    }

    private fun getTotalPages(
        context: Context
    ): Int {

        val list =
            getPlannedZamers(context)

        val pageSize = 4

        return if (list.isEmpty()) {
            0
        } else {
            (list.size + pageSize - 1) / pageSize
        }
    }

    private fun getCurrentPage(
        context: Context
    ): Int {

        return context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getInt(
                KEY_PAGE,
                0
            )
    }

    private fun changePage(
        context: Context,
        widgetId: Int,
        delta: Int
    ) {

        try {

            val totalPages =
                getTotalPages(context)

            if (totalPages <= 0) {

                context
                    .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                    )
                    .edit()
                    .putInt(
                        KEY_PAGE,
                        0
                    )
                    .apply()

                updateWidget(
                    context,
                    AppWidgetManager.getInstance(context),
                    widgetId
                )

                return
            }

            val prefs =
                context.getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

            val currentPage =
                prefs.getInt(
                    KEY_PAGE,
                    0
                )

            var newPage =
                currentPage + delta

            if (newPage < 0) {
                newPage = 0
            }

            if (newPage >= totalPages) {
                newPage = totalPages - 1
            }

            prefs.edit()
                .putInt(
                    KEY_PAGE,
                    newPage
                )
                .apply()

            writeLog(
                context,
                "changePage $currentPage -> $newPage / $totalPages"
            )

            updateWidget(
                context,
                AppWidgetManager.getInstance(context),
                widgetId
            )

        } catch (e: Exception) {

            writeLog(
                context,
                "changePage ERROR: ${e.stackTraceToString()}"
            )
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {

        writeLog(
            context,
            "updateWidget START widgetId=$widgetId"
        )

        try {

            val list =
                getPlannedZamers(context)

            writeLog(
                context,
                "loaded/filtered zamers=${list.size}"
            )

            val pageSize = 4

            val totalPages =
                if (list.isEmpty()) {
                    0
                } else {
                    (list.size + pageSize - 1) / pageSize
                }

            var currentPage =
                getCurrentPage(context)

            if (totalPages == 0) {

                currentPage = 0

            } else if (currentPage >= totalPages) {

                currentPage =
                    totalPages - 1
            }

            context
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .edit()
                .putInt(
                    KEY_PAGE,
                    currentPage
                )
                .apply()

            val start =
                currentPage * pageSize

            val end =
                minOf(
                    start + pageSize,
                    list.size
                )

            val pageItems =
                if (start < list.size) {
                    list.subList(
                        start,
                        end
                    )
                } else {
                    emptyList()
                }

            writeLog(
                context,
                "pageItems=${pageItems.size}, page=$currentPage, totalPages=$totalPages"
            )

            /*
             * ВАЖНО:
             * RemoteViews создаётся только из XML,
             * предназначенного для AppWidget.
             */

            val views =
                RemoteViews(
                    context.packageName,
                    R.layout.zamer_widget
                )

            val title =
                if (list.isEmpty()) {
                    "📏 План замеров"
                } else {
                    "📏 План замеров · ${list.size}"
                }

            views.setTextViewText(
                R.id.w_title,
                title
            )

            /*
             * Пустое состояние.
             */

            if (pageItems.isEmpty()) {

                views.setViewVisibility(
                    R.id.cards_container,
                    View.GONE
                )

                views.setViewVisibility(
                    R.id.w_empty,
                    View.VISIBLE
                )

                views.setViewVisibility(
                    R.id.btn_prev,
                    View.GONE
                )

                views.setViewVisibility(
                    R.id.btn_next,
                    View.GONE
                )

            } else {

                views.setViewVisibility(
                    R.id.cards_container,
                    View.VISIBLE
                )

                views.setViewVisibility(
                    R.id.w_empty,
                    View.GONE
                )

                views.setViewVisibility(
                    R.id.btn_prev,
                    if (currentPage > 0) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                )

                views.setViewVisibility(
                    R.id.btn_next,
                    if (
                        currentPage < totalPages - 1
                    ) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                )

                val cardIds =
                    intArrayOf(
                        R.id.card_1,
                        R.id.card_2,
                        R.id.card_3,
                        R.id.card_4
                    )

                val timeIds =
                    intArrayOf(
                        R.id.t1_time,
                        R.id.t2_time,
                        R.id.t3_time,
                        R.id.t4_time
                    )

                val nameIds =
                    intArrayOf(
                        R.id.t1_name,
                        R.id.t2_name,
                        R.id.t3_name,
                        R.id.t4_name
                    )

                val addrIds =
                    intArrayOf(
                        R.id.t1_addr,
                        R.id.t2_addr,
                        R.id.t3_addr,
                        R.id.t4_addr
                    )

                val fromIds =
                    intArrayOf(
                        R.id.t1_from,
                        R.id.t2_from,
                        R.id.t3_from,
                        R.id.t4_from
                    )

                val callIds =
                    intArrayOf(
                        R.id.t1_call,
                        R.id.t2_call,
                        R.id.t3_call,
                        R.id.t4_call
                    )

                val mapIds =
                    intArrayOf(
                        R.id.t1_map,
                        R.id.t2_map,
                        R.id.t3_map,
                        R.id.t4_map
                    )

                val doneIds =
                    intArrayOf(
                        R.id.t1_done,
                        R.id.t2_done,
                        R.id.t3_done,
                        R.id.t4_done
                    )

                val micIds =
                    intArrayOf(
                        R.id.t1_mic,
                        R.id.t2_mic,
                        R.id.t3_mic,
                        R.id.t4_mic
                    )

                /*
                 * Сначала скрываем все карточки.
                 */

                for (cardId in cardIds) {

                    views.setViewVisibility(
                        cardId,
                        View.GONE
                    )
                }

                /*
                 * Заполняем существующие карточки.
                 */

                for (i in pageItems.indices) {

                    if (i >= 4) {
                        break
                    }

                    views.setViewVisibility(
                        cardIds[i],
                        View.VISIBLE
                    )

                    fillCard(
                        views = views,
                        context = context,
                        z = pageItems[i],
                        timeId = timeIds[i],
                        nameId = nameIds[i],
                        addrId = addrIds[i],
                        fromId = fromIds[i],
                        callId = callIds[i],
                        mapId = mapIds[i],
                        doneId = doneIds[i],
                        micId = micIds[i],
                        widgetId = widgetId
                    )
                }
            }

            /*
             * Нажатие на сам виджет.
             */

            val openIntent =
                Intent(
                    context,
                    MainActivity::class.java
                )

            val openPendingIntent =
                PendingIntent.getActivity(
                    context,
                    widgetId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            views.setOnClickPendingIntent(
                R.id.w_root,
                openPendingIntent
            )

            /*
             * Кнопка назад.
             */

            val prevIntent =
                Intent(
                    context,
                    ZamerWidget::class.java
                ).apply {

                    action =
                        ACTION_PREV_PAGE

                    putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        widgetId
                    )
                }

            val prevPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    widgetId * 10 + 1,
                    prevIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            views.setOnClickPendingIntent(
                R.id.btn_prev,
                prevPendingIntent
            )

            /*
             * Кнопка вперёд.
             */

            val nextIntent =
                Intent(
                    context,
                    ZamerWidget::class.java
                ).apply {

                    action =
                        ACTION_NEXT_PAGE

                    putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        widgetId
                    )
                }

            val nextPendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    widgetId * 10 + 2,
                    nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            views.setOnClickPendingIntent(
                R.id.btn_next,
                nextPendingIntent
            )

            /*
             * Самое главное действие.
             */

            appWidgetManager.updateAppWidget(
                widgetId,
                views
            )

            writeLog(
                context,
                "updateWidget SUCCESS widgetId=$widgetId"
            )

        } catch (e: Exception) {

            writeLog(
                context,
                "updateWidget ERROR: ${e.stackTraceToString()}"
            )

            Log.e(
                TAG,
                "updateWidget error",
                e
            )
        }
    }

    private fun fillCard(
        views: RemoteViews,
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

        try {

            writeLog(
                context,
                "fillCard zamer=${z.id}"
            )

            /*
             * Время.
             */

            views.setTextViewText(
                timeId,
                z.timeText()
            )

            views.setInt(
                timeId,
                "setTextColor",
                statusColor(z.status)
            )

            /*
             * Имя.
             */

            if (z.name.isNotBlank()) {

                views.setTextViewText(
                    nameId,
                    z.name
                )

                views.setViewVisibility(
                    nameId,
                    View.VISIBLE
                )

            } else {

                views.setViewVisibility(
                    nameId,
                    View.GONE
                )
            }

            /*
             * Источник контакта.
             */

            if (z.contactFrom.isNotBlank()) {

                views.setTextViewText(
                    fromId,
                    "От: ${z.contactFrom}"
                )

                views.setViewVisibility(
                    fromId,
                    View.VISIBLE
                )

            } else {

                views.setViewVisibility(
                    fromId,
                    View.GONE
                )
            }

            /*
             * Адрес.
             */

            if (z.address.isNotBlank()) {

                views.setTextViewText(
                    addrId,
                    z.address
                )

                views.setViewVisibility(
                    addrId,
                    View.VISIBLE
                )

            } else {

                views.setViewVisibility(
                    addrId,
                    View.GONE
                )
            }

            /*
             * Телефон.
             */

            val phone =
                z.phone.filter {
                    it.isDigit() || it == '+'
                }

            if (phone.isNotBlank()) {

                val dialIntent =
                    Intent(
                        Intent.ACTION_DIAL,
                        Uri.parse("tel:$phone")
                    )

                val dialPendingIntent =
                    PendingIntent.getActivity(
                        context,
                        requestCode(z.id),
                        dialIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or
                                PendingIntent.FLAG_IMMUTABLE
                    )

                views.setOnClickPendingIntent(
                    callId,
                    dialPendingIntent
                )

                views.setViewVisibility(
                    callId,
                    View.VISIBLE
                )

            } else {

                views.setViewVisibility(
                    callId,
                    View.GONE
                )
            }

            /*
             * Карта.
             */

            if (z.address.isNotBlank()) {

                val mapIntent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://yandex.ru/maps/?text=" +
                                    Uri.encode(z.address)
                        )
                    )

                val mapPendingIntent =
                    PendingIntent.getActivity(
                        context,
                        requestCode(z.id + 1000L),
                        mapIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or
                                PendingIntent.FLAG_IMMUTABLE
                    )

                views.setOnClickPendingIntent(
                    mapId,
                    mapPendingIntent
                )

                views.setViewVisibility(
                    mapId,
                    View.VISIBLE
                )

            } else {

                views.setViewVisibility(
                    mapId,
                    View.GONE
                )
            }

            /*
             * Выполнено.
             */

            val doneIntent =
                Intent(
                    context,
                    ZamerWidget::class.java
                ).apply {

                    action =
                        ACTION_DONE

                    putExtra(
                        "zamer_id",
                        z.id
                    )

                    putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        widgetId
                    )
                }

            val donePendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    requestCode(z.id + 2000L),
                    doneIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            views.setOnClickPendingIntent(
                doneId,
                donePendingIntent
            )

            views.setViewVisibility(
                doneId,
                View.VISIBLE
            )

            /*
             * Голосовая запись.
             *
             * Используем voiceFile из самой модели,
             * если он задан.
             */

            val voicePath =
                if (z.voiceFile.isNotBlank()) {
                    z.voiceFile
                } else {
                    "voice_${z.id}.m4a"
                }

            val voiceFile =
                File(
                    context.filesDir,
                    voicePath
                )

            writeLog(
                context,
                "voice file=$voicePath exists=${voiceFile.exists()}"
            )

            if (
                voiceFile.exists() &&
                voiceFile.length() > 0L
            ) {

                val playIntent =
                    Intent(
                        context,
                        VoicePlaybackService::class.java
                    ).apply {

                        putExtra(
                            "voice_file_path",
                            voiceFile.absolutePath
                        )
                    }

                val playPendingIntent =
                    PendingIntent.getService(
                        context,
                        requestCode(z.id + 3000L),
                        playIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or
                                PendingIntent.FLAG_IMMUTABLE
                    )

                views.setOnClickPendingIntent(
                    micId,
                    playPendingIntent
                )

                views.setViewVisibility(
                    micId,
                    View.VISIBLE
                )

            } else {

                views.setViewVisibility(
                    micId,
                    View.GONE
                )
            }

            writeLog(
                context,
                "fillCard SUCCESS zamer=${z.id}"
            )

        } catch (e: Exception) {

            writeLog(
                context,
                "fillCard ERROR zamer=${z.id}: ${e.stackTraceToString()}"
            )

            Log.e(
                TAG,
                "fillCard error",
                e
            )
        }
    }

    private fun requestCode(
        value: Long
    ): Int {

        return (
            value xor
                    (value ushr 32)
        ).toInt()
    }

    private fun statusColor(
        status: ZamerStatus
    ): Int {

        return when (status) {

            ZamerStatus.PLANNED ->
                0xFFF4511E.toInt()

            ZamerStatus.DONE ->
                0xFF43A047.toInt()

            ZamerStatus.POSTPONED ->
                0xFF9E9E9E.toInt()

            ZamerStatus.CANCELLED ->
                0xFFE53935.toInt()
        }
    }
}
