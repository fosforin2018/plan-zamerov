package com.zamerplan.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zamerplan.app.alarm.ReminderScheduler
import com.zamerplan.app.alarm.SettingsStore
import com.zamerplan.app.backup.BackupManager
import com.zamerplan.app.model.Storage
import com.zamerplan.app.widget.ZamerWidget
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun plural(n: Int, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && (mod100 < 12 || mod100 > 14) -> few
        else -> many
    }
}

fun offsetLabel(mins: Int): String = when {
    mins >= 24 * 60 && mins % (24 * 60) == 0 -> {
        val d = mins / (24 * 60)
        "За $d ${plural(d, "день", "дня", "дней")}"
    }
    mins >= 60 && mins % 60 == 0 -> {
        val h = mins / 60
        "За $h ${plural(h, "час", "часа", "часов")}"
    }
    mins >= 60 -> "За ${mins / 60} ч ${mins % 60} мин"
    else -> "За $mins ${plural(mins, "минуту", "минуты", "минут")}"
}

private fun parseHHMM(s: String): Pair<Int, Int> {
    val parts = s.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 30
    return Pair(h.coerceIn(0, 23), m.coerceIn(0, 59))
}

private fun fmtBackupTime(ms: Long): String =
    if (ms == 0L) "копии ещё нет"
    else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ms))

private fun destIcon(type: String): String = when (type) {
    "saf" -> "📂"
    "telegram" -> "✈️"
    else -> "☁️"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
// ============================================================
// БРЕНДОВЫЕ ИКОНКИ РЕЗЕРВНЫХ КОПИЙ (вектор, путь X)
// ============================================================
private val IcoMailru = ImageVector.Builder(
    name = "ic_mailru", defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 24f, viewportHeight = 24f
).apply {
    addPath(fillColor = Color(0xFF0C6DF2), pathData = "M5,2 L19,2 A3,3 0 0 1 22,5 L22,19 A3,3 0 0 1 19,22 L5,22 A3,3 0 0 1 2,19 L2,5 A3,3 0 0 1 5,2 Z")
    addPath(strokeColor = Color(0xFFFFCC33), strokeWidth = 2f, fillColor = Color.Transparent, pathData = "M15,10 A3,3 0 1 0 15,14 A3,3 0 0 0 15,10 M15,14 A5,5 0 1 1 18,10")
}.build()

private val IcoYandex = ImageVector.Builder(
    name = "ic_yandex", defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 24f, viewportHeight = 24f
).apply {
    addPath(fillColor = Color(0xFFFC3F1D), pathData = "M12,2 A10,10 0 1 0 12,22 A10,10 0 1 0 12,2 Z")
    addPath(fillColor = Color.White, pathData = "M13,6 L16,6 L16,18 L13,18 L13,13 C11,13 9,11 9,9 C9,7 11,6 13,6 Z")
}.build()

private val IcoTelegram = ImageVector.Builder(
    name = "ic_telegram", defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 24f, viewportHeight = 24f
).apply {
    addPath(fillColor = Color(0xFF2AABEE), pathData = "M12,2 A10,10 0 1 0 12,22 A10,10 0 1 0 12,2 Z")
    addPath(fillColor = Color.White, pathData = "M6,12 L18,7 L16,17 L12,14 L10,16 L10,13 Z")
}.build()

private val IcoDownloads = ImageVector.Builder(
    name = "ic_downloads", defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 24f, viewportHeight = 24f
).apply {
    addPath(fillColor = Color(0xFF607D8B), pathData = "M2,6 L8,6 L10,8 L22,8 L22,18 L2,18 Z")
}.build()

private val IcoSafFile = ImageVector.Builder(
    name = "ic_saf_file", defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 24f, viewportHeight = 24f
).apply {
    addPath(fillColor = Color(0xFF9E9E9E), pathData = "M6,2 L14,2 L18,6 L18,22 L6,22 Z")
}.build()

@Composable
private fun backupIcon(type: String, modifier: Modifier = Modifier) {
    val vec = when (type) {
        "mailru" -> IcoMailru
        "yandex" -> IcoYandex
        "telegram" -> IcoTelegram
        "local" -> IcoDownloads
        else -> IcoSafFile
    }
    Icon(vec, contentDescription = null, tint = Color.Unspecified, modifier = modifier.size(20.dp))
}

fun WheelNumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    enabled: Boolean = true,
    digitColor: Color = TextSecondary,
    activeColor: Color = Orange,
    bgColor: Color = Color.White.copy(alpha = 0.08f),
    modifier: Modifier = Modifier
) {
    val itemHeight = 32.dp
    val side = 1
    val values = remember(range) { (range.first..range.last).toList() }
    val listState = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    var touched by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        listState.scrollToItem((value - range.first).coerceAtLeast(0))
    }
    LaunchedEffect(value) {
        val target = (value - range.first).coerceAtLeast(0)
        if (listState.firstVisibleItemIndex != target) {
            listState.animateScrollToItem(target)
        }
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            touched = true
        } else if (touched) {
            val newValue = (range.first + listState.firstVisibleItemIndex).coerceIn(range)
            if (newValue != value) onValueChange(newValue)
        }
    }

    Box(
        modifier = modifier
            .width(56.dp)
            .height(itemHeight * 3)
            .background(bgColor, RoundedCornerShape(10.dp))
            .alpha(if (enabled) 1f else 0.4f)
    ) {
        if (enabled) {
            LazyColumn(
                state = listState,
                flingBehavior = fling,
                modifier = Modifier.fillMaxSize()
            ) {
                items(side) { Spacer(Modifier.height(itemHeight)) }
                items(values.size) { i ->
                    val v = values[i]
                    val selected = v == value
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            v.toString().padStart(2, '0'),
                            fontSize = if (selected) 16.sp else 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) activeColor else digitColor
                        )
                    }
                }
                items(side) { Spacer(Modifier.height(itemHeight)) }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    value.toString().padStart(2, '0'),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = digitColor
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .border(1.dp, activeColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun OffsetDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val chipBg = MaterialTheme.colorScheme.surfaceVariant

    var num by remember {
        mutableStateOf(
            when {
                initialMinutes % (24 * 60) == 0 -> initialMinutes / (24 * 60)
                initialMinutes % 60 == 0 -> initialMinutes / 60
                else -> initialMinutes
            }
        )
    }
    var unit by remember {
        mutableStateOf(
            when {
                initialMinutes % (24 * 60) == 0 -> "d"
                initialMinutes % 60 == 0 -> "h"
                else -> "m"
            }
        )
    }
    val mult = when (unit) {
        "d" -> 24 * 60
        "h" -> 60
        else -> 1
    }
    val minutes = (num * mult).coerceIn(1, 7 * 24 * 60)

    val quick = listOf(
        10 to "10 мин", 30 to "30 мин", 60 to "1 час", 120 to "2 часа",
        180 to "3 часа", 360 to "6 часов", 720 to "12 часов", 1440 to "1 день"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Напоминание", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Быстро:", fontSize = 12.sp, color = onSurfaceVariant)
                quick.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { (m, label) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (minutes == m) Orange else chipBg,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        when {
                                            m % (24 * 60) == 0 -> { unit = "d"; num = m / (24 * 60) }
                                            m % 60 == 0 -> { unit = "h"; num = m / 60 }
                                            else -> { unit = "m"; num = m }
                                        }
                                    }
                                    .padding(horizontal = 2.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    color = if (minutes == m) Color.White else onSurface
                                )
                            }
                        }
                    }
                }
                Text("Или точно:", fontSize = 12.sp, color = onSurfaceVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WheelNumberPicker(
                        value = num,
                        onValueChange = { num = it },
                        range = 1..59,
                        digitColor = onSurfaceVariant,
                        bgColor = chipBg
                    )
                    Row(modifier = Modifier.weight(1f)) {
                        listOf("m" to "Мин", "h" to "Час", "d" to "Дн").forEach { (u, label) ->
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = unit == u, onClick = { unit = u })
                                Text(label, fontSize = 11.sp, color = onSurface)
                            }
                        }
                    }
                }
                Text("Получится: ${offsetLabel(minutes)}", fontSize = 12.sp, color = Orange)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(minutes) }) { Text("Готово") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    store: SettingsStore,
    onThemeChanged: () -> Unit
) {
    val ctx = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    fun reschedule() {
        ReminderScheduler.scheduleAll(ctx, Storage(ctx).load(), store)
    }

    var ringUri by remember { mutableStateOf(store.ringtoneUri) }

    var slotOn by remember {
        mutableStateOf(listOf(store.slotOn(1), store.slotOn(2), store.slotOn(3), store.slotOn(4)))
    }
    var slotMin by remember {
        mutableStateOf(listOf(store.slotMinutes(1), store.slotMinutes(2), store.slotMinutes(3), store.slotMinutes(4)))
    }
    var editSlot by remember { mutableStateOf<Int?>(null) }
    var remindersExpanded by remember { mutableStateOf(false) }

    var customOn by remember { mutableStateOf(store.customTimeOn) }
    val initTime = remember { parseHHMM(store.customReminderTime) }
    var customH by remember { mutableStateOf(initTime.first) }
    var customM by remember { mutableStateOf(initTime.second) }

    var playMode by remember { mutableStateOf(store.playMode) }

    var sources by remember { mutableStateOf(store.sources.toList()) }
    var newSource by remember { mutableStateOf("") }
    var showLogs by remember { mutableStateOf(false) }
    var logsText by remember { mutableStateOf("") }
    var themeMode by remember { mutableStateOf(store.themeMode) }

    var dests by remember { mutableStateOf(BackupManager.destinations(ctx)) }
    var autoBackup by remember { mutableStateOf(store.autoBackup) }
    var backupBusy by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var lastReport by remember { mutableStateOf("") }
    var showRestoreSource by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreDest by remember { mutableStateOf<BackupManager.Destination?>(null) }
    var restoreMeta by remember { mutableStateOf<BackupManager.BackupMeta?>(null) }
    var restoreLocal by remember { mutableStateOf(false) }
    var connectType by remember { mutableStateOf<String?>(null) }
    var connectLogin by remember { mutableStateOf("") }
    var connectPass by remember { mutableStateOf("") }
    var showTelegramConnect by remember { mutableStateOf(false) }
    var historyList by remember { mutableStateOf<List<BackupManager.HistoryEntry>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }
    var historyBusy by remember { mutableStateOf(false) }

    fun saveCustomTime(h: Int, m: Int) {
        store.customReminderTime =
            h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')
        reschedule()
    }

    fun runBackup() {
        if (backupBusy) return
        backupBusy = true
        Thread {
            val results = BackupManager.backupAll(ctx)
            val newDests = BackupManager.destinations(ctx)
            val report = if (results.isEmpty()) {
                "Сначала подключите облако или Telegram"
            } else {
                results.joinToString("\n") { r ->
                    (if (r.ok) "✓ " else "⚠ ") + r.destination.name +
                        if (r.ok) " — копия обновлена" else " — " + r.message
                }
            }
            mainHandler.post {
                backupBusy = false
                dests = newDests
                lastReport = report
                showReport = true
            }
        }.start()
    }

    fun saveLocal() {
        if (backupBusy) return
        backupBusy = true
        Thread {
            val res = BackupManager.saveToLocalDownloads(ctx)
            mainHandler.post {
                backupBusy = false
                lastReport = res.second
                showReport = true
            }
        }.start()
    }

    fun loadHistory() {
        if (historyBusy) return
        historyBusy = true
        Thread {
            val list = BackupManager.listHistory(ctx)
            mainHandler.post {
                historyBusy = false
                historyList = list
                showHistory = true
            }
        }.start()
    }

    fun restoreLastHistory() {
        val first = historyList.firstOrNull() ?: run { loadHistory(); return }
        pickHistory(first)
    }

    fun pickHistory(entry: BackupManager.HistoryEntry) {
        if (historyBusy) return
        historyBusy = true
        showHistory = false
        Thread {
            val r = BackupManager.restoreHistoryUri(ctx, entry.uri)
            mainHandler.post {
                historyBusy = false
                if (r.ok) {
                    ReminderScheduler.scheduleAll(ctx, Storage(ctx).load(), store)
                    ZamerWidget.refreshAll(ctx)
                }
                lastReport = r.message
                showReport = true
            }
        }.start()
    }

    fun connectTelegram() {
        if (!BackupManager.telegramConfigured()) {
            lastReport = "Telegram не настроен: вставь токен бота в константу TELEGRAM_BOT_TOKEN в BackupManager.kt"
            showReport = true
            return
        }
        if (backupBusy) return
        backupBusy = true
        Thread {
            val err = BackupManager.telegramConnect(ctx)
            val newDests = BackupManager.destinations(ctx)
            mainHandler.post {
                backupBusy = false
                dests = newDests
                if (err != null) {
                    lastReport = err
                    showReport = true
                } else {
                    runBackup()
                }
            }
        }.start()
    }

    fun prepareRestoreLocal() {
        val meta = BackupManager.readLocalMeta(ctx)
        restoreLocal = true
        restoreDest = null
        restoreUri = null
        restoreMeta = meta
        if (meta == null) {
            lastReport = "Копия в «Загрузках» не найдена или повреждена — сначала нажмите «💾 Локально»"
            showReport = true
        } else {
            showRestoreConfirm = true
        }
    }

    fun prepareRestoreDest(d: BackupManager.Destination) {
        restoreLocal = false
        if (d.type == "saf") {
            val meta = BackupManager.readMeta(ctx, Uri.parse(d.uri))
            restoreDest = d
            restoreUri = Uri.parse(d.uri)
            restoreMeta = meta
            if (meta == null) {
                lastReport = "Файл не похож на копию или повреждён"
                showReport = true
            } else {
                showRestoreConfirm = true
            }
            return
        }
        backupBusy = true
        Thread {
            val tmp = File(ctx.cacheDir, "restore_tmp.zip")
            val ok = BackupManager.downloadTo(ctx, d, tmp)
            val meta = if (ok) BackupManager.readMetaFile(tmp) else null
            tmp.delete()
            mainHandler.post {
                backupBusy = false
                restoreDest = d
                restoreUri = null
                restoreMeta = meta
                if (meta == null) {
                    lastReport = "Не удалось скачать копию из «" + d.name + "»"
                    showReport = true
                } else {
                    showRestoreConfirm = true
                }
            }
        }.start()
    }

    fun runRestore() {
        if (backupBusy) return
        val d = restoreDest
        val u = restoreUri
        if (!restoreLocal && d == null && u == null) return
        backupBusy = true
        Thread {
            val r = if (restoreLocal) {
                BackupManager.restoreFromLocalDownloads(ctx)
            } else if (d != null && d.type != "saf") {
                BackupManager.restoreCloud(ctx, d)
            } else if (u != null) {
                BackupManager.restoreSaf(ctx, u)
            } else {
                BackupManager.RestoreResult(false, "источник не выбран")
            }
            mainHandler.post {
                backupBusy = false
                if (r.ok) {
                    ReminderScheduler.scheduleAll(ctx, Storage(ctx).load(), store)
                    ZamerWidget.refreshAll(ctx)
                }
                lastReport = r.message
                showReport = true
            }
        }.start()
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val name = BackupManager.displayName(ctx, uri)
                BackupManager.addSafDestination(ctx, uri, name)
                dests = BackupManager.destinations(ctx)
                runBackup()
            }
        }
    }

    val summary = buildString {
        val active = (1..4).filter { slotOn[it - 1] }
            .map { offsetLabel(slotMin[it - 1]).replaceFirstChar { c -> c.lowercase() } }
        if (active.isEmpty()) append("напоминания выкл")
        else append(active.joinToString(", "))
        if (customOn) {
            append(" · своё " + customH.toString().padStart(2, '0') + ":" + customM.toString().padStart(2, '0'))
        }
        append(" · " + when (playMode) {
            "voice" -> "голос"
            "ring" -> "мелодия"
            else -> "голос + мелодия"
        })
    }

    val widgetPrefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var widgetCardsCount by remember { mutableStateOf(widgetPrefs.getInt("widget_cards_count", 2)) }
    fun refreshWidgetWithCardCount(count: Int) {
        widgetCardsCount = count
        widgetPrefs.edit().putInt("widget_cards_count", count).apply()
        ctx.getSharedPreferences("widget_page_state", Context.MODE_PRIVATE)
            .edit().putInt("page", 0).apply()
        ZamerWidget.refreshAll(ctx)
    }

    fun ringName(): String {
        if (ringUri.isBlank()) return "Стандартное уведомление"
        return try {
            val ringtone = RingtoneManager.getRingtone(ctx, Uri.parse(ringUri))
            ringtone?.getTitle(ctx) ?: "Выбранная мелодия"
        } catch (e: Exception) {
            "Выбранная мелодия"
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data
                ?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ringUri = uri?.toString() ?: ""
            store.ringtoneUri = ringUri
        }
    }

    LaunchedEffect(Unit) {
        val pending = BackupManager.takePendingAuth(ctx)
        val code = pending.first
        val err = pending.second
        if (err != null) {
            lastReport = "Яндекс отклонил подключение: $err"
            showReport = true
        } else if (code != null) {
            backupBusy = true
            Thread {
                val connectError = BackupManager.finishYandexConnect(ctx, code)
                val newDests = BackupManager.destinations(ctx)
                mainHandler.post {
                    backupBusy = false
                    dests = newDests
                    if (connectError != null) {
                        lastReport = connectError
                        showReport = true
                    } else {
                        runBackup()
                    }
                }
            }.start()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Spacer(Modifier.weight(1f))
        }
        Text(
            text = "⚙ Настройки",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Виджет", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Количество замеров на виджете:", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = widgetCardsCount == 2, onClick = { refreshWidgetWithCardCount(2) })
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text("2 замера", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Компактный режим", fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = widgetCardsCount == 4, onClick = { refreshWidgetWithCardCount(4) })
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text("4 замера", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Расширенный режим", fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (widgetCardsCount == 2) "Сейчас выбран компактный виджет: 2 карточки."
                    else "Сейчас выбран расширенный виджет: 4 карточки.",
                    fontSize = 12.sp,
                    color = Orange
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Тема:", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { themeMode = "system"; store.themeMode = "system"; onThemeChanged() }) {
                        Text("Системная", color = if (themeMode == "system") Orange else Gray)
                    }
                    TextButton(onClick = { themeMode = "dark"; store.themeMode = "dark"; onThemeChanged() }) {
                        Text("Тёмная", color = if (themeMode == "dark") Orange else Gray)
                    }
                    TextButton(onClick = { themeMode = "light"; store.themeMode = "light"; onThemeChanged() }) {
                        Text("Светлая", color = if (themeMode == "light") Orange else Gray)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { remindersExpanded = !remindersExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🔔 Напоминания и звук",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(if (remindersExpanded) "▲" else "▼", color = Orange, fontSize = 14.sp)
                }
                Spacer(Modifier.height(2.dp))
                Text(summary, fontSize = 12.sp, color = TextSecondary)

                if (remindersExpanded) {
                    Spacer(Modifier.height(8.dp))
                    (1..4).forEach { i ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = slotOn[i - 1],
                                onCheckedChange = { v ->
                                    slotOn = slotOn.toMutableList().also { it[i - 1] = v }
                                    store.setSlotOn(i, v)
                                    reschedule()
                                },
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = offsetLabel(slotMin[i - 1]),
                                fontSize = 13.sp,
                                color = if (slotOn[i - 1]) TextPrimary else TextSecondary,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { editSlot = i }
                            )
                            TextButton(onClick = { editSlot = i }) { Text("✏", color = Orange) }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = customOn,
                            onCheckedChange = { v ->
                                customOn = v
                                store.customTimeOn = v
                                reschedule()
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Своё время",
                            fontSize = 12.sp,
                            color = if (customOn) TextPrimary else TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        WheelNumberPicker(
                            value = customH,
                            onValueChange = { v -> customH = v; saveCustomTime(v, customM) },
                            range = 0..23,
                            enabled = customOn
                        )
                        Text(":", color = TextPrimary, fontSize = 16.sp)
                        WheelNumberPicker(
                            value = customM,
                            onValueChange = { v -> customM = v; saveCustomTime(customH, v) },
                            range = 0..59,
                            enabled = customOn
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "voice_ring" to "Голос+мелод.",
                            "voice" to "Только голос",
                            "ring" to "Только мелод."
                        ).forEach { (mode, label) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (playMode == mode) Orange else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { playMode = mode; store.playMode = mode }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    color = if (playMode == mode) Color.White else TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    TextButton(onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            if (ringUri.isNotBlank()) {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(ringUri))
                            }
                        }
                        picker.launch(intent)
                    }) {
                        Text("🎵 ${ringName()}", color = Orange)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("☁️ Резервные копии", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Копии базы и голосовых: Telegram, Яндекс Диск, Облако Mail.ru или файл на телефоне.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))

                // === СПИСОК СТАТУСОВ ===
                dests.forEachIndexed { i, d ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        backupIcon(d.type, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(d.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(
                                text = when {
                                    d.lastError.isNotBlank() -> "⚠ ${d.lastError}"
                                    d.lastOk > 0L -> "✓ копия: ${fmtBackupTime(d.lastOk)}"
                                    else -> "○ ожидает первой копии"
                                },
                                fontSize = 12.sp,
                                color = if (d.lastError.isNotBlank()) Red else TextSecondary
                            )
                        }
                        IconButton(onClick = {
                            BackupManager.removeDestination(ctx, i)
                            dests = BackupManager.destinations(ctx)
                        }) { Text("✕", color = Gray, fontSize = 16.sp) }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // === КНОПКИ ПОДКЛЮЧЕНИЯ (вертикальные, умные) ===
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val connected = dests.any { it.type == "telegram" }
                            if (connected) {
                                lastReport = "Telegram подключён (${dests.find { it.type == "telegram" }?.name})\nЧтобы переподключить — сначала нажмите ✕."
                                showReport = true
                            } else {
                                showTelegramConnect = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            backupIcon("telegram", Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Подключить Telegram", fontSize = 14.sp, color = Color.White)
                        }
                    }

                    Button(
                        onClick = {
                            val connected = dests.any { it.type == "yandex" }
                            if (connected) {
                                lastReport = "Яндекс Диск подключён (${dests.find { it.type == "yandex" }?.name})\nЧтобы переподключить — сначала нажмите ✕."
                                showReport = true
                            } else {
                                if (!BackupManager.yandexConfigured()) {
                                    lastReport = "Не настроено: вставьте ClientID/Secret в BackupManager.kt"
                                    showReport = true
                                } else {
                                    try { ctx.startActivity(BackupManager.yandexAuthIntent()) }
                                    catch (e: Exception) { lastReport = "Ошибка браузера: ${e.message}"; showReport = true }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            backupIcon("yandex", Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Подключить Яндекс Диск", fontSize = 14.sp, color = Color.White)
                        }
                    }

                    Button(
                        onClick = {
                            val connected = dests.any { it.type == "mailru" }
                            if (connected) {
                                lastReport = "Mail.ru Cloud подключён (${dests.find { it.type == "mailru" }?.name})\nЧтобы переподключить — сначала нажмите ✕."
                                showReport = true
                            } else {
                                connectType = "mailru"
                                connectLogin = ""
                                connectPass = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            backupIcon("mailru", Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Подключить Облако Mail.ru", fontSize = 14.sp, color = Color.White)
                        }
                    }

                    OutlinedButton(
                        onClick = { createLauncher.launch(BackupManager.createDocumentIntent()) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Gray.copy(alpha = 0.5f))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            backupIcon("saf", Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Другая папка (если Загрузки не работают)", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { createLauncher.launch(BackupManager.createDocumentIntent()) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gray)
                    ) { Text("📂 Файл", fontSize = 11.sp, color = Color.White, maxLines = 1) }
                    Button(
                        onClick = { runBackup() },
                        modifier = Modifier.weight(1f),
                        enabled = !backupBusy && dests.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green)
                    ) { Text("📤 Копии", fontSize = 11.sp, color = Color.White, maxLines = 1) }
                    Button(
                        onClick = { showRestoreSource = true },
                        modifier = Modifier.weight(1f),
                        enabled = !backupBusy && dests.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue)
                    ) { Text("📥 Вернуть", fontSize = 11.sp, color = Color.White, maxLines = 1) }
                    Button(
                        onClick = { loadHistory() },
                        modifier = Modifier.weight(1f),
                        enabled = !backupBusy && !historyBusy,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue)
                    ) { Text("🕘 История", fontSize = 11.sp, color = Color.White, maxLines = 1) }
                    Button(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gray)
                    ) { Text("💾 Локально", fontSize = 10.sp, color = Color.White, maxLines = 1) }
                }

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = autoBackup,
                        onCheckedChange = { v -> autoBackup = v; store.autoBackup = v }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Автокопия при изменениях",
                        fontSize = 12.sp,
                        color = if (autoBackup) TextPrimary else TextSecondary
                    )
                }

                if (backupBusy) {
                    Spacer(Modifier.height(6.dp))
                    Text("⏳ Идёт копирование…", fontSize = 12.sp, color = Orange)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Источники (От кого):", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newSource,
                        onValueChange = { newSource = it },
                        placeholder = { Text("Имя", color = TextSecondary) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newSource.isNotBlank()) {
                                val updated = sources + newSource.trim()
                                sources = updated
                                store.sources = updated.toSet()
                                newSource = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange)
                    ) { Text("Добавить", color = Color.White) }
                }
                if (sources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    sources.forEach { source ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• $source", color = TextPrimary, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val updated = sources - source
                                sources = updated
                                store.sources = updated.toSet()
                            }) { Text("✕", color = Red) }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("💡 Разрешения", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    "Если напоминания не срабатывают — разрешите точные будильники:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                TextButton(onClick = {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", ctx.packageName, null)
                            }
                        )
                    } catch (e: Exception) { }
                }) { Text("Открыть настройки приложения", color = Orange) }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Диагностика виджета", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Логи помогут понять, почему виджет не обновляется или не показывает карточки.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    val file = File(ctx.filesDir, "widget_log.txt")
                    logsText = if (file.exists()) {
                        try { file.readText() } catch (e: Exception) { "Не удалось прочитать файл логов: ${e.message}" }
                    } else {
                        "Файл логов не найден"
                    }
                    showLogs = true
                }) { Text("📋 Показать логи виджета", color = Blue) }
            }
        }

        Spacer(Modifier.height(20.dp))
    }

    if (showTelegramConnect) {
        AlertDialog(
            onDismissRequest = { showTelegramConnect = false },
            title = { Text("Подключить Telegram", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("1. Открой Telegram.", fontSize = 13.sp, color = onSurface)
                    Text("2. Найди бота " + BackupManager.telegramBotName() + " через поиск.", fontSize = 13.sp, color = onSurface)
                    Text("3. Нажми START (или отправь /start).", fontSize = 13.sp, color = onSurface)
                    Text("4. Вернись сюда и нажми «Подключить».", fontSize = 13.sp, color = onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Копии будут приходить в твой личный чат с ботом — их видишь только ты. Восстановление — оттуда же.",
                        fontSize = 12.sp,
                        color = onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTelegramConnect = false
                    connectTelegram()
                }) { Text("Подключить") }
            },
            dismissButton = {
                TextButton(onClick = { showTelegramConnect = false }) { Text("Отмена") }
            }
        )
    }

    connectType?.let { type ->
        AlertDialog(
            onDismissRequest = { connectType = null },
            title = { Text("Подключить Облако Mail.ru", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = connectLogin,
                        onValueChange = { connectLogin = it },
                        label = { Text("Почта Mail.ru") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = connectPass,
                        onValueChange = { connectPass = it },
                        label = { Text("Пароль для внешних приложений") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Где взять пароль: cloud.mail.ru → шестерёнка «Настройки» → «Пароль для внешних приложений» → создать и вставить сюда.",
                        fontSize = 11.sp,
                        color = onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val login = connectLogin.trim()
                        val pass = connectPass.trim()
                        if (login.isNotBlank() && pass.isNotBlank()) {
                            BackupManager.addCloudDestination(ctx, type, login, pass)
                            dests = BackupManager.destinations(ctx)
                            connectType = null
                            runBackup()
                        }
                    }
                ) { Text("Подключить") }
            },
            dismissButton = {
                TextButton(onClick = { connectType = null }) { Text("Отмена") }
            }
        )
    }

    if (showRestoreSource) {
        AlertDialog(
            onDismissRequest = { showRestoreSource = false },
            title = { Text("Откуда восстановить", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                        TextButton(
                            onClick = {
                                showRestoreSource = false
                                prepareRestoreDest(d)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                destIcon(d.type) + " " + d.name,
                                color = onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
            },
            confirmButton = {
                TextButton(onClick = { showRestoreSource = false }) { Text("Отмена") }
            }
        )
    }

    if (showRestoreConfirm && restoreMeta != null) {
        val meta = restoreMeta!!
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Восстановить данные?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Копия от: ${meta.createdAt}", fontSize = 13.sp, color = onSurface)
                    Text("Замеров в копии: ${meta.zamerCount}", fontSize = 13.sp, color = onSurface)
                    Text("Голосовых в копии: ${meta.voiceCount}", fontSize = 13.sp, color = onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "ВНИМАНИЕ: текущие замеры и голосовые на этом устройстве будут ПОЛНОСТЬЮ заменены данными из копии.",
                        fontSize = 12.sp,
                        color = Red
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    runRestore()
                }) { Text("Восстановить", color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("Отмена") }
            }
        )
    }

    if (showReport) {
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text("Резервное копирование", fontWeight = FontWeight.Bold) },
            text = { Text(lastReport, fontSize = 13.sp, color = onSurface) },
            confirmButton = {
                TextButton(onClick = { showReport = false }) { Text("ОК") }
            }
        )
    }

    editSlot?.let { slot ->
        OffsetDialog(
            initialMinutes = slotMin[slot - 1],
            onDismiss = { editSlot = null },
            onConfirm = { mins ->
                slotMin = slotMin.toMutableList().also { it[slot - 1] = mins }
                store.setSlotMinutes(slot, mins)
                reschedule()
                editSlot = null
            }
        )
    }

    if (showLogs) {
        AlertDialog(
            onDismissRequest = { showLogs = false },
            title = { Text("Логи виджета", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = if (logsText.isBlank()) "Логи пустые" else logsText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = onSurface
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val linesCount = if (logsText.isBlank()) 0 else logsText.lines().size
                    Text("Строк: $linesCount", fontSize = 11.sp, color = onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                            try {
                                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Логи виджета", logsText))
                            } catch (e: Exception) { }
                        }) { Text("Скопировать всё", fontSize = 12.sp) }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                            try {
                                val file = File(ctx.filesDir, "widget_log.txt")
                                if (file.exists()) file.writeText("")
                                logsText = ""
                            } catch (e: Exception) {
                                logsText = "Ошибка очистки логов: ${e.message}"
                            }
                        }) { Text("Очистить", fontSize = 12.sp) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogs = false }) { Text("Закрыть") }
            }
        )
    }
                // === ДИАЛОГ ИСТОРИИ КОПИЙ ===
                if (showHistory) {
                    AlertDialog(
                        onDismissRequest = { showHistory = false },
                        title = { Text("🕘 История копий", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                        text = {
                            if (historyList.isEmpty()) {
                                Text("Копий пока нет. Нажмите «💾 Локально» или включите автокопию.", fontSize = 13.sp, color = TextSecondary)
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                    items(historyList.size) { idx ->
                                        val entry = historyList[idx]
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { pickHistory(entry) }.padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(entry.name.removePrefix("ZamerPlan_backup_").removeSuffix(".zip"), fontSize = 14.sp, color = TextPrimary)
                                                Text("${entry.size / 1024} КБ · ${SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date(entry.dateAdded * 1000L))}", fontSize = 11.sp, color = TextSecondary)
                                            }
                                            Icon(Icons.Default.ArrowForwardIos, null, tint = Gray, modifier = Modifier.size(16.dp))
                                        }
                                        HorizontalDivider(color = DarkCardBorder)
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = { TextButton(onClick = { showHistory = false }) { Text("Закрыть", color = Blue) } }
                    )
                }
}
