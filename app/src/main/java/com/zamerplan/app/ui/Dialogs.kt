package com.zamerplan.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zamerplan.app.alarm.VoiceRecorder
import com.zamerplan.app.model.Zamer
import com.zamerplan.app.model.ZamerParser
import com.zamerplan.app.model.ZamerStatus
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val D = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val T = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZamerFormDialog(
    initialDate: LocalDate,
    existing: Zamer? = null,
    recorder: VoiceRecorder,
    onSave: (Zamer) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    var date by remember { mutableStateOf(existing?.date ?: initialDate) }
    var time by remember { mutableStateOf(existing?.time ?: LocalTime.of(12, 0)) }
    var timeEnd by remember { mutableStateOf(existing?.timeEnd ?: "") }
    var status by remember { mutableStateOf(existing?.status ?: ZamerStatus.PLANNED) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var contactFrom by remember { mutableStateOf(existing?.contactFrom ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var area by remember { mutableStateOf(existing?.area ?: "") }
    var thickness by remember { mutableStateOf(existing?.thickness ?: "") }
    var price by remember { mutableStateOf(existing?.price ?: "") }
    var comment by remember { mutableStateOf(existing?.comment ?: "") }
    var rawText by remember { mutableStateOf("") }
    var hasVoice by remember { mutableStateOf(existing?.voiceFile?.isNotBlank() == true) }
    var isRecording by remember { mutableStateOf(false) }
    var voicePlaying by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showTimeEnd by remember { mutableStateOf(false) }

    val id = existing?.id ?: System.currentTimeMillis()
    val voiceFile = File(ctx.filesDir, "voice_$id.m4a")

    fun startRec() {
        recorder.start(voiceFile)
        isRecording = true
    }

    fun stopRec() {
        recorder.stop()
        isRecording = false
        hasVoice = voiceFile.exists() && voiceFile.length() > 0L
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRec() }

    val dateState = rememberDatePickerState(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    val timeState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    val timeEndState = rememberTimePickerState(initialHour = 14, initialMinute = 0, is24Hour = true)

    AlertDialog(
        onDismissRequest = { if (isRecording) stopRec(); onDismiss() },
        title = { Text(if (existing != null) "Редактировать замер" else "Новый замер") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showDate = true }, modifier = Modifier.weight(1.2f)) {
                        Text(date.format(D), fontSize = 13.sp, maxLines = 1, softWrap = false)
                    }
                    TextButton(onClick = { showTime = true }, modifier = Modifier.weight(0.8f)) {
                        Text(time.format(T), fontSize = 13.sp, maxLines = 1, softWrap = false)
                    }
                    TextButton(onClick = { showTimeEnd = true }, modifier = Modifier.weight(1f)) {
                        Text(if (timeEnd.isBlank()) "Конец: —" else "до " + timeEnd, fontSize = 13.sp, maxLines = 1, softWrap = false)
                    }
                }
                if (existing != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        ZamerStatus.values().forEach { s ->
                            val selected = s == status
                            TextButton(
                                onClick = { status = s },
                                modifier = Modifier.weight(1f).background(if (selected) statusColor(s) else Color.Transparent, RoundedCornerShape(8.dp)),
                                colors = ButtonDefaults.textButtonColors(contentColor = if (selected) Color.White else statusColor(s)),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                            ) { Text(s.label, fontSize = 9.sp, maxLines = 1, softWrap = false) }
                        }
                    }
                }
                OutlinedTextField(contactFrom, { contactFrom = it }, label = { Text("От кого контакт") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("Имя клиента") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Телефон") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it }, label = { Text("Объект / адрес") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3)
                OutlinedTextField(area, { area = it }, label = { Text("Площадь, м²") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(thickness, { thickness = it }, label = { Text("Толщина стяжки, см") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(price, { price = it }, label = { Text("Цена, ₽") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(comment, { comment = it }, label = { Text("Комментарий (лифт, паркинг и т.д.)") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4)
                Text("🎤 Голосовая напоминалка (себе)", fontSize = 12.sp, color = Orange, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isRecording) {
                        TextButton(
                            onClick = { stopRec() },
                            modifier = Modifier.weight(1f).background(Red.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.textButtonColors(contentColor = Red)
                        ) { Text("🔴 Идет запись... Стоп", maxLines = 1, softWrap = false) }
                    } else {
                        TextButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (granted) startRec() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("🎤 Записать голос", maxLines = 1, softWrap = false) }
                    }
                    if (hasVoice && !isRecording) {
                        TextButton(
                            onClick = { voicePlaying = true; val ok = recorder.play(voiceFile) { voicePlaying = false }; if (!ok) voicePlaying = false },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (voicePlaying) "⏹ Играет" else "▶ Слушать", maxLines = 1, softWrap = false) }
                        TextButton(
                            onClick = { voiceFile.delete(); hasVoice = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = Red),
                            modifier = Modifier.weight(0.6f)
                        ) { Text("🗑", maxLines = 1, softWrap = false) }
                    }
                }
                if (isRecording) {
                    Text("🔴 Говорите... Нажмите «Стоп», когда закончите", fontSize = 11.sp, color = Red, fontWeight = FontWeight.SemiBold)
                }
                if (hasVoice && !isRecording) {
                    Text("💡 Голос прозвучит в напоминании ПЕРЕД мелодией", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(rawText, { rawText = it }, label = { Text("Вставьте текст сообщения (WhatsApp/Telegram)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
                TextButton(onClick = {
                    val p = ZamerParser.parse(rawText)
                    if (p.phone.isNotBlank()) phone = p.phone
                    if (p.address.isNotBlank()) address = p.address
                    if (p.area.isNotBlank()) area = p.area
                    if (p.thickness.isNotBlank()) thickness = p.thickness
                    if (p.contactFrom.isNotBlank()) contactFrom = p.contactFrom
                    if (p.comment.isNotBlank()) comment = p.comment
                    if (p.name.isNotBlank()) name = p.name
                    if (p.price.isNotBlank()) price = p.price
                    p.dateOffset?.let { off -> date = LocalDate.now().plusDays(off.toLong()) }
                    if (p.timeStr.isNotBlank()) { try { time = LocalTime.parse(p.timeStr) } catch (e: Exception) { } }
                }) { Text("🧠 Разобрать текст и заполнить поля", color = Orange) }

                // Кнопка удаления (если есть onDelete и existing != null)
                if (existing != null && onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { if (isRecording) stopRec(); onDelete() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Удалить замер", color = Color.White) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isRecording) stopRec()
                val voiceFileFinal = if (voiceFile.exists() && voiceFile.length() > 0L) voiceFile.name else ""
                onSave(Zamer(id = id, date = date, time = time, timeEnd = timeEnd.trim(), name = name.trim(), phone = phone.trim(), contactFrom = contactFrom.trim(), address = address.trim(), area = area.trim(), thickness = thickness.trim(), price = price.trim(), comment = comment.trim(), voiceFile = voiceFileFinal, status = status))
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = { if (isRecording) stopRec(); onDismiss() }) { Text("Отмена") }
        }
    )

    // Дальше идут DatePickerDialog и TimePicker (как раньше)
    if (showDate) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms -> date = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate() }
                    showDate = false
                }) { Text("ОК") }
            }
        ) { DatePicker(state = dateState) }
    }
    if (showTime) {
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = { time = LocalTime.of(timeState.hour, timeState.minute); showTime = false }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Отмена") } },
            text = { TimePicker(state = timeState) }
        )
    }
    if (showTimeEnd) {
        AlertDialog(
            onDismissRequest = { showTimeEnd = false },
            confirmButton = {
                TextButton(onClick = { timeEnd = LocalTime.of(timeEndState.hour, timeEndState.minute).format(T); showTimeEnd = false }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { timeEnd = ""; showTimeEnd = false }) { Text("Сбросить") } },
            text = { TimePicker(state = timeEndState) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleDialog(
    onMove: (LocalDate) -> Unit,
    onCancelZamer: () -> Unit,
    onDismiss: () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перенести замер") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onMove(LocalDate.now().plusDays(1)) }, modifier = Modifier.fillMaxWidth()) { Text("На завтра") }
                TextButton(onClick = { onMove(LocalDate.now().plusDays(2)) }, modifier = Modifier.fillMaxWidth()) { Text("На послезавтра") }
                TextButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("Выбрать дату") }
                TextButton(
                    onClick = { if (confirmCancel) onCancelZamer() else confirmCancel = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Red)
                ) { Text(if (confirmCancel) "Точно отменить? Ещё раз" else "Отменить замер") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms -> onMove(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()) }
                    showPicker = false
                }) { Text("ОК") }
            }
        ) { DatePicker(state = pickerState) }
    }
}
