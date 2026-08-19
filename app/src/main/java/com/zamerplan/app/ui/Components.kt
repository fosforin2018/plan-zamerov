package com.zamerplan.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zamerplan.app.model.Zamer
import com.zamerplan.app.model.ZamerStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

val Orange = Color(0xFFF4511E)
val Green = Color(0xFF43A047)
val Gray = Color(0xFF757575)
val Red = Color(0xFFE53935)
val Blue = Color(0xFF1E88E5)

fun statusColor(s: ZamerStatus): Color = when (s) {
    ZamerStatus.PLANNED -> Orange
    ZamerStatus.DONE -> Green
    ZamerStatus.POSTPONED -> Gray
    ZamerStatus.CANCELLED -> Red
}

@Composable
fun CollapsibleCalendar(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    countsByDay: Map<LocalDate, Int>
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📅 Календарь",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(if (expanded) "▲" else "▼", color = Orange, fontSize = 14.sp)
            }
            if (expanded) {
                MonthCalendar(month, onMonthChange, selectedDate, onSelectDate, countsByDay)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun MonthCalendar(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    countsByDay: Map<LocalDate, Int>
) {
    val ru = java.util.Locale.forLanguageTag("ru")
    val title = DateTimeFormatter.ofPattern("LLLL yyyy", ru).format(month.atDay(1))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(ru) else it.toString() }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Text("‹") }
            Text(text = title, modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            TextButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Text("›") }
        }
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { d ->
                Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val cells = mutableListOf<LocalDate?>()
        repeat(offset) { cells.add(null) }
        for (d in 1..month.lengthOfMonth()) cells.add(month.atDay(d))
        while (cells.size % 7 != 0) cells.add(null)
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f).height(44.dp),
                        contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val count = countsByDay[day] ?: 0
                            val selected = day == selectedDate
                            Column(
                                modifier = Modifier.size(38.dp).background(
                                    color = when {
                                        selected -> Orange
                                        count > 0 -> Orange.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    }, shape = CircleShape
                                ).clickable { onSelectDate(day) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(day.dayOfMonth.toString(), fontSize = 14.sp,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected || count > 0) FontWeight.Bold else FontWeight.Normal)
                                if (count > 0) {
                                    Box(modifier = Modifier.size(5.dp)
                                        .background(if (selected) Color.White else Orange, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionButton(text: String, onClick: () -> Unit, filled: Boolean, color: Color, modifier: Modifier = Modifier) {
    if (filled) {
        Button(
            onClick = onClick, modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
            shape = RoundedCornerShape(10.dp)
        ) { Text(text, fontSize = 11.sp, maxLines = 1, softWrap = false) }
    } else {
        TextButton(
            onClick = onClick, modifier = modifier,
            colors = ButtonDefaults.textButtonColors(contentColor = color),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) { Text(text, fontSize = 11.sp, maxLines = 1, softWrap = false) }
    }
}

@Composable
fun ZamerCard(
    z: Zamer,
    hasVoice: Boolean,
    onCall: () -> Unit,
    onDone: () -> Unit,
    onReturn: () -> Unit,
    onReschedule: () -> Unit,
    onEdit: () -> Unit,
    onMap: () -> Unit,
    onPlayVoice: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(6.dp).background(statusColor(z.status)))
            Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    if (hasVoice) {
                        Box(modifier = Modifier.size(26.dp)
                            .background(Orange.copy(alpha = 0.15f), CircleShape)
                            .clickable { onPlayVoice() },
                            contentAlignment = Alignment.Center) {
                            Text("🎤", fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Surface(color = statusColor(z.status), shape = RoundedCornerShape(8.dp)) {
                        Text(z.status.label, color = Color.White, fontSize = 9.sp,
                            maxLines = 1, softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text(z.timeText(), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = Orange, maxLines = 1, lineHeight = 20.sp)
                if (z.name.isNotBlank()) Text(z.name, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (z.address.isNotBlank()) Text(z.address, fontSize = 12.sp, lineHeight = 15.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOf(
                    if (z.contactFrom.isNotBlank()) "От: " + z.contactFrom else "",
                    z.area, z.thickness
                ).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, fontSize = 11.sp, lineHeight = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (z.price.isNotBlank()) Text(z.price + " ₽", fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, lineHeight = 16.sp)
                if (z.comment.isNotBlank()) Text(z.comment, fontSize = 11.sp, lineHeight = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    ActionButton("Позвонить", onCall, true, Orange, Modifier.weight(1f))
                    ActionButton("Карта", onMap, false, Blue, Modifier.weight(1f))
                }
                Row(modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    ActionButton("Перенести", onReschedule, false, Orange, Modifier.weight(1f))
                    if (z.status == ZamerStatus.DONE || z.status == ZamerStatus.CANCELLED) {
                        ActionButton("Вернуть", onReturn, true, Green, Modifier.weight(1f))
                    } else {
                        ActionButton("Выполнено", onDone, true, Green, Modifier.weight(1f))
                    }
                }
                Row(modifier = Modifier.padding(top = 2.dp)) {
                    ActionButton("Изменить", onEdit, false, Gray, Modifier.weight(1f))
                }
            }
        }
    }
}
