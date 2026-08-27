package com.zamerplan.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zamerplan.app.alarm.ReminderScheduler
import com.zamerplan.app.alarm.SettingsStore
import com.zamerplan.app.alarm.VoiceRecorder
import com.zamerplan.app.model.Storage
import com.zamerplan.app.model.Zamer
import com.zamerplan.app.model.ZamerStatus
import com.zamerplan.app.ui.CollapsibleCalendar
import com.zamerplan.app.ui.Orange
import com.zamerplan.app.ui.RescheduleDialog
import com.zamerplan.app.ui.SettingsScreen
import com.zamerplan.app.ui.ZamerCard
import com.zamerplan.app.ui.ZamerFormDialog
import com.zamerplan.app.widget.ZamerWidget
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var storage: Storage
    private lateinit var settings: SettingsStore
    private val zamers = mutableStateListOf<Zamer>()
    private val recorder = VoiceRecorder(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = Storage(this)
        settings = SettingsStore(this)
        reloadZamers()  // загружаем данные при старте

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        ReminderScheduler.scheduleAll(this, zamers, settings)  // только при старте

        val themeMode = settings.themeMode
        setContent {
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme(primary = Orange) else lightColorScheme(primary = Orange)
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Перезагружаем данные, но НЕ пересоздаём будильники
        reloadZamers()
    }

    private fun reloadZamers() {
        zamers.clear()
        zamers.addAll(storage.load())
    }

    @Composable
    private fun AppRoot() {
        var screen by remember { mutableStateOf("main") }
        if (screen == "main") {
            MainScreen(
                zamers = zamers,
                recorder = recorder,
                settings = settings,
                onOpenSettings = { screen = "settings" },
                onSave = { z ->
                    zamers.add(z)
                    storage.save(zamers)
                    ReminderScheduler.scheduleAll(this, zamers, settings)  // при создании
                    ZamerWidget.refreshAll(this)
                },
                onUpdate = { z ->
                    val i = zamers.indexOfFirst { it.id == z.id }
                    if (i >= 0) {
                        zamers[i] = z
                        ReminderScheduler.schedule(this, z, settings)  // при обновлении
                    }
                    storage.save(zamers)
                    ZamerWidget.refreshAll(this)
                },
                onDelete = { z ->
                    storage.deleteVoice(z.id)
                    ReminderScheduler.cancel(this, z.id)  // при удалении
                    zamers.removeAll { it.id == z.id }
                    storage.save(zamers)
                    ZamerWidget.refreshAll(this)
                }
            )
        } else {
            SettingsScreen(
                onBack = { screen = "main" },
                store = settings,
                onThemeChanged = { recreate() }
            )
        }
    }
}

@Composable
fun MainScreen(
    zamers: List<Zamer>,
    recorder: VoiceRecorder,
    settings: SettingsStore,
    onOpenSettings: () -> Unit,
    onSave: (Zamer) -> Unit,
    onUpdate: (Zamer) -> Unit,
    onDelete: (Zamer) -> Unit
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var showForm by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Zamer?>(null) }
    var rescheduleTarget by remember { mutableStateOf<Zamer?>(null) }

    val sources = settings.sources.toList()
    val context = LocalContext.current

    @Composable
    fun CardSlot(z: Zamer) {
        val voiceFile = java.io.File(context.filesDir, "voice_${z.id}.m4a")
        ZamerCard(
            z = z,
            hasVoice = voiceFile.exists(),
            onCall = {
                val tel = z.phone.filter { c -> c.isDigit() || c == '+' }
                if (tel.isNotEmpty()) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel")))
                }
            },
            onMap = {
                if (z.address.isNotBlank()) {
                    val enc = Uri.encode(z.address)
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?text=$enc")))
                    } catch (e: Exception) { }
                }
            },
            onDone = { onUpdate(z.copy(status = ZamerStatus.DONE)) },
            onReturn = { onUpdate(z.copy(status = ZamerStatus.PLANNED)) },
            onReschedule = { rescheduleTarget = z },
            onEdit = { editTarget = z },
            onPlayVoice = { if (voiceFile.exists()) recorder.play(voiceFile) }
        )
    }

    val counts = zamers.filter { it.status != ZamerStatus.CANCELLED }
        .groupingBy { it.date }.eachCount()
    val dayList = zamers.filter { it.date == selectedDate }.sortedBy { it.time }
    val daySum = dayList.filter { it.status != ZamerStatus.CANCELLED }
        .sumOf { it.price.toIntOrNull() ?: 0 }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showForm = true },
                containerColor = Orange,
                contentColor = Color.White
            ) { Text("+", fontSize = 28.sp) }
        }
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("План замеров", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenSettings) { Text("⚙") }
            }

            CollapsibleCalendar(
                selectedDate = selectedDate,
                onSelectDate = { selectedDate = it },
                month = month,
                onMonthChange = { month = it },
                countsByDay = counts
            )

            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))) +
                    " · замеров: " + dayList.size + " · " + daySum + " ₽",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            if (dayList.isEmpty()) {
                Text(
                    "Нет замеров на этот день. Нажмите «+», чтобы добавить.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (dayList.size == 1) {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    CardSlot(dayList[0])
                }
            } else {
                dayList.chunked(2).forEach { rowItems ->
                    Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                        rowItems.forEach { z ->
                            Column(modifier = Modifier.weight(1f)) { CardSlot(z) }
                        }
                        if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showForm) {
        ZamerFormDialog(
            initialDate = selectedDate,
            recorder = recorder,
            onSave = { z -> onSave(z); showForm = false },
            onDismiss = { showForm = false },
            sources = sources
        )
    }

    editTarget?.let { z ->
        ZamerFormDialog(
            initialDate = z.date,
            existing = z,
            recorder = recorder,
            onSave = { updated -> onUpdate(updated); editTarget = null },
            onDismiss = { editTarget = null },
            onDelete = { onDelete(z); editTarget = null },
            sources = sources
        )
    }

    rescheduleTarget?.let { z ->
        RescheduleDialog(
            onMove = { d -> onUpdate(z.copy(date = d, status = ZamerStatus.PLANNED)); rescheduleTarget = null },
            onCancelZamer = { onUpdate(z.copy(status = ZamerStatus.CANCELLED)); rescheduleTarget = null },
            onDismiss = { rescheduleTarget = null }
        )
    }
}
