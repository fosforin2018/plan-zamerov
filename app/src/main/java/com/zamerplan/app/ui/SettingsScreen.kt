package com.zamerplan.app.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zamerplan.app.alarm.SettingsStore
import java.io.File

@Composable
fun SettingsScreen(onBack: () -> Unit, store: SettingsStore, onThemeChanged: () -> Unit) {
    val ctx = LocalContext.current
    var ringUri by remember { mutableStateOf(store.ringtoneUri) }
    var bDay by remember { mutableStateOf(store.beforeDay) }
    var b2h by remember { mutableStateOf(store.before2h) }
    var b30 by remember { mutableStateOf(store.before30m) }
    var b10 by remember { mutableStateOf(store.before10m) }
    var customTime by remember { mutableStateOf(store.customReminderTime) }
    var sources by remember { mutableStateOf(store.sources.toList()) }
    var newSource by remember { mutableStateOf("") }
    var showLogs by remember { mutableStateOf(false) }
    var logsText by remember { mutableStateOf("") }

    var themeMode by remember { mutableStateOf(store.themeMode) }

    fun ringName(): String {
        if (ringUri.isBlank()) return "Стандартное уведомление"
        return try {
            val r = RingtoneManager.getRingtone(ctx, Uri.parse(ringUri))
            r?.getTitle(ctx) ?: "Выбранная мелодия"
        } catch (e: Exception) {
            "Выбранная мелодия"
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val u: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ringUri = u?.toString() ?: ""
            store.ringtoneUri = ringUri
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Spacer(Modifier.weight(1f))
        }
        Text("⚙ Настройки", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

        // Блок темы
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
                    TextButton(onClick = {
                        themeMode = "system"; store.themeMode = "system"; onThemeChanged()
                    }) {
                        Text("Системная", color = if (themeMode == "system") Orange else Gray)
                    }
                    TextButton(onClick = {
                        themeMode = "dark"; store.themeMode = "dark"; onThemeChanged()
                    }) {
                        Text("Тёмная", color = if (themeMode == "dark") Orange else Gray)
                    }
                    TextButton(onClick = {
                        themeMode = "light"; store.themeMode = "light"; onThemeChanged()
                    }) {
                        Text("Светлая", color = if (themeMode == "light") Orange else Gray)
                    }
                }
            }
        }

        // Блок напоминаний
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Напоминать о замере:", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                // Два столбца
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        CheckRowSmall("За 1 день", bDay) { bDay = it; store.beforeDay = it }
                        CheckRowSmall("За 2 часа", b2h) { b2h = it; store.before2h = it }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        CheckRowSmall("За 30 минут", b30) { b30 = it; store.before30m = it }
                        CheckRowSmall("За 10 минут", b10) { b10 = it; store.before10m = it }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Своё время (например, 08:30):", fontSize = 13.sp, color = TextSecondary)
                OutlinedTextField(
                    value = customTime,
                    onValueChange = { customTime = it; store.customReminderTime = it },
                    placeholder = { Text("ЧЧ:ММ", color = TextSecondary) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }

        // Блок источников "От кого"
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
                            }) {
                                Text("✕", color = Red)
                            }
                        }
                    }
                }
            }
        }

        // Блок мелодии
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Мелодия:", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
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
                }) { Text("🎵 " + ringName(), color = Orange) }
            }
        }

        // Блок разрешений
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("💡 Разрешения", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("Если напоминания не срабатывают — разрешите точные будильники:", fontSize = 12.sp, color = TextSecondary)
                TextButton(onClick = {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", ctx.packageName, null)
                        })
                    } catch (e: Exception) { }
                }) { Text("Открыть настройки приложения", color = Orange) }
            }
        }

        // Блок логов
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                TextButton(onClick = {
                    val file = File(ctx.filesDir, "widget_log.txt")
                    logsText = if (file.exists()) file.readText() else "Файл логов не найден"
                    showLogs = true
                }) { Text("📋 Показать логи виджета", color = Blue) }
            }
        }
    }

    if (showLogs) {
        AlertDialog(
            onDismissRequest = { showLogs = false },
            title = { Text("Логи виджета") },
            text = {
                Text(
                    text = logsText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 400.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { showLogs = false }) { Text("Закрыть") }
            }
        )
    }
}

@Composable
fun CheckRowSmall(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Checkbox(checked, onCheckedChange = onChange, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 12.sp, color = TextPrimary)
    }
}
