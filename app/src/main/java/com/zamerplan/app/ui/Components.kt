package com.zamerplan.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import java.util.Locale

val Orange = Color(0xFFF4511E)
val Green = Color(0xFF43A047)
val Gray = Color(0xFF757575)
val Red = Color(0xFFE53935)
val Blue = Color(0xFF1E88E5)

val DarkCardBg = Color(0xCC1E1E1E)
val DarkCardBorder = Color(0x33FFFFFF)
val TextPrimary = Color(0xFFF5F5F5)
val TextSecondary = Color(0xFFB3FFFFFF)
val CalendarBg = Color(0xCC1E1E1E)
val CalendarBorder = Color(0x33FFFFFF)

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
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CalendarBg),
        border = BorderStroke(1.dp, CalendarBorder)
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Календарь",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
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

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Text("‹", color = Orange) }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary
            )
            TextButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Text("›", color = Orange) }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        val offset = month.atDay(1).dayOfWeek.value - 1
        val cells = mutableListOf<LocalDate?>()
        repeat(offset) { cells.add(null) }
        for (d in 1..month.lengthOfMonth()) cells.add(month.atDay(d))
        while (cells.size % 7 != 0) cells.add(null)

        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            val count = countsByDay[day] ?: 0
                            val selected = day == selectedDate
                            Column(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        color = when {
                                            selected -> Orange
                                            count > 0 -> Orange.copy(alpha = 0.2f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { onSelectDate(day) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    day.dayOfMonth.toString(),
                                    fontSize = 13.sp,
                                    color = if (selected) Color.White else TextPrimary,
                                    fontWeight = if (selected || count > 0) FontWeight.Bold else FontWeight.Normal
                                )
                                if (count > 0 && !selected) {
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(Orange, CircleShape)
                                    )
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
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    filled: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
            shape = shape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(text, fontSize = 13.sp, maxLines = 1, softWrap = false)
        }
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier.height(40.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = color),
            shape = shape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(text, fontSize = 13.sp, maxLines = 1, softWrap = false)
        }
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
        modifier = Modifier.fillMaxWidth().padding(4.dp).shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(6.dp).background(statusColor(z.status)))

            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasVoice) {
                        Box(
                            modifier = Modifier.size(26.dp).background(Orange.copy(alpha = 0.15f), CircleShape).clickable { onPlayVoice() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎤", fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Surface(color = statusColor(z.status), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            z.status.label,
                            color = Color.White,
                            fontSize = 9.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    z.timeText(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Orange,
                    maxLines = 1,
                    lineHeight = 20.sp
                )
                if (z.name.isNotBlank()) {
                    Text(
                        z.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = TextPrimary
                    )
                }
                if (z.address.isNotBlank()) {
                    Text(
                        z.address,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = TextSecondary
                    )
                }

                val meta = listOf(
                    if (z.contactFrom.isNotBlank()) "От: " + z.contactFrom else "",
                    z.area,
                    z.thickness
                ).filter { it.isNotBlank() }.joinToString(" · ")

                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = TextSecondary
                    )
                }

                if (z.price.isNotBlank()) {
                    Text(
                        z.price + " ₽",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        color = TextPrimary
                    )
                }

                if (z.comment.isNotBlank()) {
                    Text(
                        z.comment,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = TextSecondary
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton("Позвонить", onCall, true, Green, Modifier.weight(1f))
                    ActionButton("Карта", onMap, false, Blue, Modifier.weight(1f))
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton("Перенести", onReschedule, false, Orange, Modifier.weight(1f))
                    if (z.status == ZamerStatus.DONE || z.status == ZamerStatus.CANCELLED) {
                        ActionButton("Вернуть", onReturn, true, Green, Modifier.weight(1f))
                    } else {
                        ActionButton("Выполнено", onDone, true, Green, Modifier.weight(1f))
                    }
                }

                Row(modifier = Modifier.padding(top = 4.dp)) {
                    ActionButton("Изменить", onEdit, false, Gray, Modifier.weight(1f))
                }
            }
        }
    }
}
