package com.zamerplan.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zamerplan.app.alarm.VoiceRecorder
import com.zamerplan.app.model.Zamer
import com.zamerplan.app.model.ZamerParser
import com.zamerplan.app.model.ZamerStatus
import kotlinx.coroutines.delay
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
    onDelete: (() -> Unit)? = null,
    sources: List<String> = emptyList()
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
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showTimeEnd by remember { mutableStateOf(false) }

    val id = existing?.id ?: System.currentTimeMillis()
    val voiceFile = File(ctx.filesDir, "voice_$id.m4a")

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Разрешение получено, можно начинать запись
    }

    val dateState = rememberDatePickerState(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    val timeState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    val timeEndState = rememberTimePickerState(initialHour = 14, initialMinute = 0, is24Hour = true)

    AlertDialog(
        onDismissRequest = { onDismiss() },
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
                                modifier = Modifier.weight(1f).background(
                                    if (selected) statusColor(s) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                ),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (selected) Color.White else statusColor(s)
                                ),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                            ) {
                                Text(s.label, fontSize = 9.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                // Поле "От кого" с выпадающим списком
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = contactFrom,
                        onValueChange = { contactFrom = it },
                        label = { Text("От кого контакт") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { expanded = true }) { Text("▼") }
                        }
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sources.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(source) },
                                onClick = {
                                    contactFrom = source
                                    expanded = false
                                }
                            )
                        }
                        if (sources.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Нет сохранённых источников") },
                                onClick = { expanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(name, { name = it }, label = { Text("Имя клиента") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Телефон") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it }, label = { Text("Объект / адрес") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3)
                OutlinedTextField(area, { area = it }, label = { Text("Площадь, м²") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(thickness, { thickness = it }, label = { Text("Толщина стяжки, см") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(price, { price = it }, label = { Text("Цена, ₽") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(comment, { comment = it }, label = { Text("Комментарий (лифт, паркинг и т.д.)") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4)

                // ===================== ГОЛОСОВОЕ СООБЩЕНИЕ =====================
                VoiceMessageRecorder(
                    recorder = recorder,
                    voiceFile = voiceFile,
                    hasVoiceInitially = hasVoice,
                    onDeleteVoice = {
                        voiceFile.delete()
                        hasVoice = false
                    },
                    onPermissionRequest = {
                        val granted = ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))

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
                    if (p.timeStr.isNotBlank()) {
                        try { time = LocalTime.parse(p.timeStr) } catch (e: Exception) { }
                    }
                }) { Text("🧠 Разобрать текст и заполнить поля", color = Orange) }

                if (existing != null && onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onDelete() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Удалить замер", color = Color.White) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val voiceFileFinal = if (voiceFile.exists() && voiceFile.length() > 0L) voiceFile.name else ""
                onSave(
                    Zamer(
                        id = id,
                        date = date,
                        time = time,
                        timeEnd = timeEnd.trim(),
                        name = name.trim(),
                        phone = phone.trim(),
                        contactFrom = contactFrom.trim(),
                        address = address.trim(),
                        area = area.trim(),
                        thickness = thickness.trim(),
                        price = price.trim(),
                        comment = comment.trim(),
                        voiceFile = voiceFileFinal,
                        status = status
                    )
                )
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text("Отмена") }
        }
    )

    if (showDate) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms ->
                        date = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDate = false
                }) { Text("ОК") }
            }
        ) { DatePicker(state = dateState) }
    }

    if (showTime) {
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(timeState.hour, timeState.minute)
                    showTime = false
                }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Отмена") } },
            text = { TimePicker(state = timeState) }
        )
    }

    if (showTimeEnd) {
        AlertDialog(
            onDismissRequest = { showTimeEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    timeEnd = LocalTime.of(timeEndState.hour, timeEndState.minute).format(T)
                    showTimeEnd = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = {
                    timeEnd = ""
                    showTimeEnd = false
                }) { Text("Сбросить") }
            },
            text = { TimePicker(state = timeEndState) }
        )
    }
}

@Composable
private fun VoiceMessageRecorder(
    recorder: VoiceRecorder,
    voiceFile: File,
    hasVoiceInitially: Boolean,
    onDeleteVoice: () -> Unit,
    onPermissionRequest: () -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableStateOf(0) }
    var hasVoice by remember { mutableStateOf(hasVoiceInitially) }
    var isPlaying by remember { mutableStateOf(false) }
    var playProgress by remember { mutableFloatStateOf(0f) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    // Таймер записи
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordSeconds = 0
            while (isRecording) {
                delay(1000)
                recordSeconds++
            }
        }
    }

    // Отслеживание прогресса воспроизведения
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val duration = player?.duration ?: 0
            if (duration > 0) {
                while (isPlaying) {
                    val current = player?.currentPosition ?: 0
                    playProgress = current.toFloat() / duration
                    delay(100)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
        }
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionRequest()
            return
        }
        recorder.start(voiceFile)
        isRecording = true
        recordSeconds = 0
    }

    fun stopRecording() {
        recorder.stop()
        isRecording = false
        hasVoice = voiceFile.exists() && voiceFile.length() > 0L
    }

    fun togglePlayback() {
        if (isPlaying) {
            player?.pause()
            isPlaying = false
        } else {
            if (player == null) {
                val mp = MediaPlayer().apply {
                    setDataSource(voiceFile.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        isPlaying = false
                        playProgress = 0f
                        it.release()
                        player = null
                    }
                    setOnErrorListener { _, _, _ ->
                        isPlaying = false
                        playProgress = 0f
                        release()
                        player = null
                        true
                    }
                }
                player = mp
            }
            player?.start()
            isPlaying = true
        }
    }

    fun deleteVoice() {
        player?.release()
        player = null
        isPlaying = false
        isRecording = false
        voiceFile.delete()
        hasVoice = false
        onDeleteVoice()
    }

    // Пульсация при записи
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // Состояние "нет записи"
        AnimatedVisibility(
            visible = !hasVoice && !isRecording,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { startRecording() },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        drawCircle(color = Color.Red, radius = 7.dp.toPx(), center = center)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("Нажмите для записи", fontSize = 13.sp, color = Color.White)
            }
        }

        // Состояние "идёт запись"
        AnimatedVisibility(
            visible = isRecording,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(pulseScale)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Red)
                        .clickable { stopRecording() },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawRect(color = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatSeconds(recordSeconds),
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(pulseScale)
                        .background(Color.Red, CircleShape)
                )
            }
        }

        // Плеер
        AnimatedVisibility(
            visible = hasVoice && !isRecording,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC1E1E1E), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Анимированные волны
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { index ->
                        val targetHeight = if (isPlaying) {
                            // Каждая полоска "танцует" в зависимости от прогресса
                            val wave = (playProgress * 10).toInt()
                            (10 + ((index + wave) % 4) * 4).dp
                        } else {
                            6.dp
                        }
                        val height by animateDpAsState(
                            targetValue = targetHeight,
                            animationSpec = tween(durationMillis = 300)
                        )
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(height)
                                .background(Color.White, RoundedCornerShape(2.dp))
                                .padding(horizontal = 1.dp)
                        )
                        if (index < 4) Spacer(Modifier.width(2.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Таймер
                val duration = if (isPlaying) {
                    val current = player?.currentPosition ?: 0
                    formatSeconds(current / 1000)
                } else {
                    formatSeconds(recordSeconds)
                }
                Text(
                    text = duration,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Spacer(Modifier.weight(1f))

                // Кнопка Play/Pause с анимацией иконки
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { togglePlayback() },
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(targetState = isPlaying, animationSpec = tween(200)) { playing ->
                        if (playing) {
                            Row {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(12.dp)
                                        .background(Color.Black)
                                )
                                Spacer(Modifier.width(3.dp))
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(12.dp)
                                        .background(Color.Black)
                                )
                            }
                        } else {
                            Canvas(modifier = Modifier.size(16.dp)) {
                                val path = Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(size.width, size.height / 2f)
                                    lineTo(0f, size.height)
                                    close()
                                }
                                drawPath(path, color = Color.Black)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "🗑",
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable { deleteVoice() }
                        .padding(4.dp)
                )
            }
        }
    }
}

private fun formatSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes > 0) "$minutes:${secs.toString().padStart(2, '0')}" else "0:${secs.toString().padStart(2, '0')}"
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
                    pickerState.selectedDateMillis?.let { ms ->
                        onMove(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate())
                    }
                    showPicker = false
                }) { Text("ОК") }
            }
        ) { DatePicker(state = pickerState) }
    }
}
