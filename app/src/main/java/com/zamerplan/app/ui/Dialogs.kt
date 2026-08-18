package com.zamerplan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zamerplan.app.model.Zamer
import com.zamerplan.app.model.ZamerParser
import com.zamerplan.app.model.ZamerStatus
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
    onSave: (Zamer) -> Unit,
    onDismiss: () -> Unit
) {
    var date by remember { mutableStateOf(existing?.date ?: initialDate) }
    var time by remember { mutableStateOf(existing?.time ?: LocalTime.of(12, 0)) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var contactFrom by remember { mutableStateOf(existing?.contactFrom ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var area by remember { mutableStateOf(existing?.area ?: "") }
    var thickness by remember { mutableStateOf(existing?.thickness ?: "") }
    var price by remember { mutableStateOf(existing?.price ?: "") }
    var comment by remember { mutableStateOf(existing?.comment ?: "") }
    var rawText by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState(
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    val timeState = rememberTimePickerState(
        initialHour = time.hour, initialMinute = time.minute, is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Редактировать замер" else "Новый замер") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                        Text(date.format(D))
                    }
                    TextButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
                        Text(time.format(T))
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
                OutlinedTextField(rawText, { rawText = it }, label = { Text("Вставьте текст сообщения (WhatsApp/Telegram)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
                TextButton(onClick = {
                    val p = ZamerParser.parse(rawText)
                    if (p.phone.isNotBlank()) phone = p.phone
                    if (p.address.isNotBlank()) address = p.address
                    if (p.area.isNotBlank()) area = p.area
                    if (p.thickness.isNotBlank()) thickness = p.thickness
                    if (p.contactFrom.isNotBlank()) contactFrom = p.contactFrom
                    if (p.comment.isNotBlank()) comment = p.comment
                }) { Text("Разобрать текст и заполнить поля", color = Orange) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    Zamer(
                        id = existing?.id ?: System.currentTimeMillis(),
                        date = date, time = time,
                        name = name.trim(), phone = phone.trim(),
                        contactFrom = contactFrom.trim(), address = address.trim(),
                        area = area.trim(), thickness = thickness.trim(),
                        price = price.trim(), comment = comment.trim(),
                        status = existing?.status ?: ZamerStatus.PLANNED
                    )
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleDialog(
    onMove: (LocalDate) -> Unit,
    onCancelZamer: () -> Unit,
    onDismiss: () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
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
                    onClick = onCancelZamer,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))
                ) { Text("Отменить замер") }
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
