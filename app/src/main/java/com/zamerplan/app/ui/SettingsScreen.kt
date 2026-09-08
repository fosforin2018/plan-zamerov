package com.zamerplan.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zamerplan.app.alarm.ReminderScheduler
import com.zamerplan.app.alarm.SettingsStore
import com.zamerplan.app.model.Storage
import com.zamerplan.app.widget.ZamerWidget
import java.io.File

// ================================================================
// ПОДПИСИ СЛОТОВ: "За 2 часа", "За 30 минут", "За 1 день"
// ================================================================

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

// ================================================================
// БАРАБАН: 3 цифры (соседняя / активная / соседняя)
// ================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
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

// ================================================================
// ОКНО ИЗМЕНЕНИЯ СЛОТА (цвета из темы — видно в светлой теме)
// ================================================================

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

// ================================================================
// ЭКРАН НАСТРОЕК
// ================================================================

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    store: SettingsStore,
    onThemeChanged: () -> Unit
) {
    val ctx = LocalContext.current

    // Любое изменение напоминаний сразу пересоздаёт будильники
    fun reschedule() {
        ReminderScheduler.scheduleAll(ctx, Storage(ctx).load(), store)
    }

    var ringUri by remember { mutableStateOf(store.ringtoneUri) }

    // Слоты напоминаний
    var slotOn by remember {
        mutableStateOf(listOf(store.slotOn(1), store.slotOn(2), store.slotOn(3), store.slotOn(4)))
    }
    var slotMin by remember {
        mutableStateOf(listOf(store.slotMinutes(1), store.slotMinutes(2), store.slotMinutes(3), store.slotMinutes(4)))
    }
    var editSlot by remember { mutableStateOf<Int?>(null) }

    // Гармошка: по умолчанию СВЁРНУТО
    var remindersExpanded by remember { mutableStateOf(false) }

    // Своё время
    var customOn by remember { mutableStateOf(store.customTimeOn) }
    val initTime = remember { parseHHMM(store.customReminderTime) }
    var customH by remember { mutableStateOf(initTime.first) }
    var customM by remember { mutableStateOf(initTime.second) }

    // Режим проигрывания
    var playMode by remember { mutableStateOf(store.playMode) }

    var sources by remember { mutableStateOf(store.sources.toList()) }
    var newSource by remember { mutableStateOf("") }
    var showLogs by remember { mutableStateOf(false) }
    var logsText by remember { mutableStateOf("") }
    var themeMode by remember { mutableStateOf(store.themeMode) }

    fun saveCustomTime(h: Int, m: Int) {
        store.customReminderTime =
            h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')
        reschedule()
    }

    // Краткая сводка для свёрнутой шапки
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

    // ============================================================
    // НАСТРОЙКИ ВИДЖЕТА
    // ============================================================
    val widgetPrefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var widgetCardsCount by remember { mutableStateOf(widgetPrefs.getInt("widget_cards_count", 2)) }
    fun refreshWidgetWithCardCount(count: Int) {
        widgetCardsCount = count
        widgetPrefs.edit().putInt("widget_cards_count", count).apply()
        ctx.getSharedPreferences("widget_page_state", Context.MODE_PRIVATE)
            .edit().putInt("page", 0).apply()
        ZamerWidget.refreshAll(ctx)
    }

    // ============================================================
    // НАЗВАНИЕ МЕЛОДИИ
    // ============================================================
    fun ringName(): String {
        if (ringUri.isBlank()) return "Стандартное уведомление"
        return try {
            val ringtone = RingtoneManager.getRingtone(ctx, Uri.parse(ringUri))
            ringtone?.getTitle(ctx) ?: "Выбранная мелодия"
        } catch (e: Exception) {
            "Выбранная мелодия"
        }
    }

    // ============================================================
    // ВЫБОР МЕЛОДИИ
    // ============================================================
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

    // ============================================================
    // ОСНОВНОЙ UI
    // ============================================================
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

        // ========================================================
        // ВИДЖЕТ
        // ========================================================
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

        // ========================================================
        // ТЕМА
        // ========================================================
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

        // ========================================================
        // НАПОМИНАНИЯ И ЗВУК (гармошка, свёрнута по умолчанию)
        // ========================================================
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // ---------- ШАПКА-ГАРМОШКА ----------
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

                // ---------- РАСКРЫТОЕ СОДЕРЖИМОЕ ----------
                if (remindersExpanded) {
                    Spacer(Modifier.height(8.dp))

                    // Слоты
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

                    // Своё время: тумблер + барабаны в ОДНУ строку
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

                    // Что проигрывать: 3 чипа в ОДНУ строку
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

                    // Мелодия
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

        // ========================================================
        // ИСТОЧНИКИ
        // ========================================================
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

        // ========================================================
        // РАЗРЕШЕНИЯ
        // ========================================================
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

        // ========================================================
        // ЛОГИ ВИДЖЕТА
        // ========================================================
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

    // ============================================================
    // ОКНО ИЗМЕНЕНИЯ СЛОТА
    // ============================================================
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

    // ============================================================
    // ОКНО ЛОГОВ (цвета из темы)
    // ============================================================
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val linesCount = if (logsText.isBlank()) 0 else logsText.lines().size
                    Text("Строк: $linesCount", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}
