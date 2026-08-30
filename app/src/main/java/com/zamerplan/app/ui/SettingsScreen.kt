package com.zamerplan.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zamerplan.app.alarm.SettingsStore
import java.io.File

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    store: SettingsStore,
    onThemeChanged: () -> Unit
) {
    val ctx = LocalContext.current

    var ringUri by remember {
        mutableStateOf(store.ringtoneUri)
    }

    var bDay by remember {
        mutableStateOf(store.beforeDay)
    }

    var b2h by remember {
        mutableStateOf(store.before2h)
    }

    var b30 by remember {
        mutableStateOf(store.before30m)
    }

    var b10 by remember {
        mutableStateOf(store.before10m)
    }

    var customTime by remember {
        mutableStateOf(store.customReminderTime)
    }

    var sources by remember {
        mutableStateOf(store.sources.toList())
    }

    var newSource by remember {
        mutableStateOf("")
    }

    var showLogs by remember {
        mutableStateOf(false)
    }

    var logsText by remember {
        mutableStateOf("")
    }

    var themeMode by remember {
        mutableStateOf(store.themeMode)
    }

    fun ringName(): String {
        if (ringUri.isBlank()) {
            return "Стандартное уведомление"
        }

        return try {
            val r = RingtoneManager.getRingtone(
                ctx,
                Uri.parse(ringUri)
            )

            r?.getTitle(ctx) ?: "Выбранная мелодия"
        } catch (e: Exception) {
            "Выбранная мелодия"
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->

        if (result.resultCode == Activity.RESULT_OK) {

            val u: Uri? =
                result.data?.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI
                )

            ringUri = u?.toString() ?: ""
            store.ringtoneUri = ringUri
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        // ---------------------------------------------------------
        // НАЗАД
        // ---------------------------------------------------------

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            TextButton(
                onClick = onBack
            ) {
                Text("← Назад")
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "⚙ Настройки",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ---------------------------------------------------------
        // ТЕМА
        // ---------------------------------------------------------

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkCardBg
            ),
            border = BorderStroke(
                1.dp,
                DarkCardBorder
            )
        ) {

            Column(
                modifier = Modifier.padding(12.dp)
            ) {

                Text(
                    text = "Тема:",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    TextButton(
                        onClick = {
                            themeMode = "system"
                            store.themeMode = "system"
                            onThemeChanged()
                        }
                    ) {
                        Text(
                            text = "Системная",
                            color =
                                if (themeMode == "system") {
                                    Orange
                                } else {
                                    Gray
                                }
                        )
                    }

                    TextButton(
                        onClick = {
                            themeMode = "dark"
                            store.themeMode = "dark"
                            onThemeChanged()
                        }
                    ) {
                        Text(
                            text = "Тёмная",
                            color =
                                if (themeMode == "dark") {
                                    Orange
                                } else {
                                    Gray
                                }
                        )
                    }

                    TextButton(
                        onClick = {
                            themeMode = "light"
                            store.themeMode = "light"
                            onThemeChanged()
                        }
                    ) {
                        Text(
                            text = "Светлая",
                            color =
                                if (themeMode == "light") {
                                    Orange
                                } else {
                                    Gray
                                }
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // НАПОМИНАНИЯ
        // ---------------------------------------------------------

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkCardBg
            ),
            border = BorderStroke(
                1.dp,
                DarkCardBorder
            )
        ) {

            Column(
                modifier = Modifier.padding(12.dp)
            ) {

                Text(
                    text = "Напоминать о замере:",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        CheckRowSmall(
                            label = "За 1 день",
                            checked = bDay,
                            onChange = {
                                bDay = it
                                store.beforeDay = it
                            }
                        )

                        CheckRowSmall(
                            label = "За 2 часа",
                            checked = b2h,
                            onChange = {
                                b2h = it
                                store.before2h = it
                            }
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        CheckRowSmall(
                            label = "За 30 минут",
                            checked = b30,
                            onChange = {
                                b30 = it
                                store.before30m = it
                            }
                        )

                        CheckRowSmall(
                            label = "За 10 минут",
                            checked = b10,
                            onChange = {
                                b10 = it
                                store.before10m = it
                            }
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "Своё время (например, 08:30):",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = customTime,
                    onValueChange = {
                        customTime = it
                        store.customReminderTime = it
                    },
                    placeholder = {
                        Text(
                            text = "ЧЧ:ММ",
                            color = TextSecondary
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }

        // ---------------------------------------------------------
        // ИСТОЧНИКИ
        // ---------------------------------------------------------

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkCardBg
            ),
            border = BorderStroke(
                1.dp,
                DarkCardBorder
            )
        ) {

            Column(
                modifier = Modifier.padding(12.dp)
            ) {

                Text(
                    text = "Источники (От кого):",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    OutlinedTextField(
                        value = newSource,
                        onValueChange = {
                            newSource = it
                        },
                        placeholder = {
                            Text(
                                text = "Имя",
                                color = TextSecondary
                            )
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Button(
                        onClick = {

                            if (newSource.isNotBlank()) {

                                val updated =
                                    sources + newSource.trim()

                                sources = updated

                                store.sources =
                                    updated.toSet()

                                newSource = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Orange
                        )
                    ) {

                        Text(
                            text = "Добавить",
                            color = Color.White
                        )
                    }
                }

                if (sources.isNotEmpty()) {

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    sources.forEach { source ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                text = "• $source",
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = {

                                    val updated =
                                        sources - source

                                    sources = updated

                                    store.sources =
                                        updated.toSet()
                                }
                            ) {

                                Text(
                                    text = "✕",
                                    color = Red
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // МЕЛОДИЯ
        // ---------------------------------------------------------

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkCardBg
            ),
            border = BorderStroke(
                1.dp,
                DarkCardBorder
            )
        ) {

            Column(
                modifier = Modifier.padding(12.dp)
            ) {

                Text(
                    text = "Мелодия:",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                TextButton(
                    onClick = {

                        val intent =
                            Intent(
                                RingtoneManager.ACTION_RINGTONE_PICKER
                            ).apply {

                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                    RingtoneManager.TYPE_NOTIFICATION
                                )

                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,
                                    true
                                )

                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,
                                    true
                                )

                                if (ringUri.isNotBlank()) {

                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                        Uri.parse(ringUri)
                                    )
                                }
                            }

                        picker.launch(intent)
                    }
                ) {

                    Text(
                        text = "🎵 " + ringName(),
                        color = Orange
                    )
                }
            }
        }

        // ---------------------------------------------------------
        // РАЗРЕШЕНИЯ
        // ---------------------------------------------------------

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkCardBg
            ),
            border = BorderStroke(
                1.dp,
                DarkCardBorder
            )
        ) {

            Column(
                modifier = Modifier.padding(12.dp)
            ) {

                Text(
                    text = "💡 Разрешения",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Text(
                    text = "Если напоминания не срабатывают — разрешите точные будильники:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                TextButton(
                    onClick = {

                        try {

                            ctx.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                ).apply {

                                    data = Uri.fromParts(
                                        "package",
                                        ctx.packageName,
                                        null
                                    )
                                }
                            )

                        } catch (e: Exception) {
                            // Ничего не делаем
                        }
                    }
                ) {

                    Text(
                        text = "Открыть настройки приложения",
                        color = Orange
                    )
                }
            }
        }

        // ---------------------------------------------------------
        // ЛОГИ ВИДЖЕТА
        // ---------------------------------------------------------

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkCardBg
            ),
            border = BorderStroke(
                1.dp,
                DarkCardBorder
            )
        ) {

            Column(
                modifier = Modifier.padding(12.dp)
            ) {

                Text(
                    text = "Отладка виджета",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "Логи нужны для проверки работы виджета.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                TextButton(
                    onClick = {

                        val file = File(
                            ctx.filesDir,
                            "widget_log.txt"
                        )

                        logsText =
                            if (file.exists()) {
                                try {
                                    file.readText()
                                } catch (e: Exception) {
                                    "Ошибка чтения логов:\n${e.message}"
                                }
                            } else {
                                "Файл логов не найден"
                            }

                        showLogs = true
                    }
                ) {

                    Text(
                        text = "📋 Показать логи виджета",
                        color = Blue
                    )
                }
            }
        }
    }

    // =============================================================
    // ОКНО ЛОГОВ
    // =============================================================

    if (showLogs) {

        val logScrollState =
            rememberScrollState()

        val lineCount =
            if (logsText.isBlank()) {
                0
            } else {
                logsText.lines().size
            }

        AlertDialog(

            onDismissRequest = {
                showLogs = false
            },

            title = {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text = "Логи виджета",
                        modifier = Modifier.weight(1f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "$lineCount строк",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            },

            text = {

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = 250.dp,
                                max = 500.dp
                            ),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF111111)
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(
                                    logScrollState
                                )
                                .padding(12.dp)
                        ) {

                            Text(
                                text = if (logsText.isBlank()) {
                                    "Логи пока отсутствуют"
                                } else {
                                    logsText
                                },
                                color = Color(0xFFEAEAEA),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "↕ Прокрутите область для просмотра всего лога",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            },

            confirmButton = {

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(4.dp),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Button(
                        onClick = {

                            val clipboard =
                                ctx.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager

                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "Логи виджета",
                                    logsText
                                )
                            )

                            Toast.makeText(
                                ctx,
                                "Все логи скопированы",
                                Toast.LENGTH_SHORT
                            ).show()
                        },

                        enabled = logsText.isNotBlank(),

                        shape = RoundedCornerShape(10.dp),

                        contentPadding =
                            PaddingValues(
                                horizontal = 12.dp,
                                vertical = 8.dp
                            )
                    ) {

                        Text(
                            text = "📋 Скопировать всё",
                            fontSize = 12.sp
                        )
                    }

                    TextButton(
                        onClick = {

                            val file = File(
                                ctx.filesDir,
                                "widget_log.txt"
                            )

                            try {

                                if (file.exists()) {
                                    file.writeText("")
                                }

                                logsText = ""

                                Toast.makeText(
                                    ctx,
                                    "Логи очищены",
                                    Toast.LENGTH_SHORT
                                ).show()

                            } catch (e: Exception) {

                                Toast.makeText(
                                    ctx,
                                    "Не удалось очистить логи",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },

                        enabled = logsText.isNotBlank()
                    ) {

                        Text(
                            text = "Очистить",
                            color = Color(0xFFD32F2F),
                            fontSize = 12.sp
                        )
                    }
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        showLogs = false
                    }
                ) {

                    Text(
                        text = "Закрыть"
                    )
                }
            }
        )
    }
}

// ================================================================
// СТРОКА CHECKBOX
// ================================================================

@Composable
fun CheckRowSmall(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(
            vertical = 2.dp
        )
    ) {

        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.size(24.dp)
        )

        Text(
            text = label,
            fontSize = 12.sp,
            color = TextPrimary
        )
    }
}
