package com.zamerplan.app.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zamerplan.app.alarm.SettingsStore

@Composable
fun SettingsScreen(onBack: () -> Unit, store: SettingsStore) {
    val ctx = LocalContext.current
    var ringUri by remember { mutableStateOf(store.ringtoneUri) }
    var bDay by remember { mutableStateOf(store.beforeDay) }
    var b2h by remember { mutableStateOf(store.before2h) }
    var b30 by remember { mutableStateOf(store.before30m) }
    var b10 by remember { mutableStateOf(store.before10m) }

    fun ringName(): String {
        if (ringUri.isBlank()) return "Стандартное уведомление"
        return try {
            val r = RingtoneManager.getRingtone(ctx, Uri.parse(ringUri))
            r?.getTitle(ctx) ?: "Выбранная мелодия"
        } catch (e: Exception) { "Выбранная мелодия" }
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
        Text("⚙ Настройки", fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp))

        Text("Напоминать о замере:", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        CheckRow("За 1 день", bDay) { bDay = it; store.beforeDay = it }
        CheckRow("За 2 часа", b2h) { b2h = it; store.before2h = it }
        CheckRow("За 30 минут", b30) { b30 = it; store.before30m = it }
        CheckRow("За 10 минут", b10) { b10 = it; store.before10m = it }

        Spacer(Modifier.height(16.dp))
        Text("Мелодия:", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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

        Spacer(Modifier.height(24.dp))
        Text("💡 Разрешения", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Если напоминания не срабатывают — разрешите точные будильники:",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = {
            try {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", ctx.packageName, null)
                })
            } catch (e: Exception) { }
        }) { Text("Открыть настройки приложения", color = Orange) }
    }
}

@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = onChange)
        Text(label, fontSize = 14.sp)
    }
}
