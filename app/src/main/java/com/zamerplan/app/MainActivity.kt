package com.zamerplan.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zamerplan.app.model.Storage
import com.zamerplan.app.model.Zamer
import com.zamerplan.app.model.ZamerStatus
import com.zamerplan.app.ui.MonthCalendar
import com.zamerplan.app.ui.Orange
import com.zamerplan.app.ui.RescheduleDialog
import com.zamerplan.app.ui.ZamerCard
import com.zamerplan.app.ui.ZamerFormDialog
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var storage: Storage
    private val zamers = mutableStateListOf<Zamer>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = Storage(this)
        zamers.addAll(storage.load())

        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark) darkColorScheme(primary = Orange) else lightColorScheme(primary = Orange)
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        zamers = zamers,
                        onSave = { z -> zamers.add(z); storage.save(zamers) },
                        onUpdate = { z ->
                            val i = zamers.indexOfFirst { it.id == z.id }
                            if (i >= 0) zamers[i] = z
                            storage.save(zamers)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    zamers: List<Zamer>,
    onSave: (Zamer) -> Unit,
    onUpdate: (Zamer) -> Unit
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var showForm by remember { mutableStateOf(false) }
    var rescheduleTarget by remember { mutableStateOf<Zamer?>(null) }
    val context = LocalContext.current

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
            Text(
                "План замеров",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                textAlign = TextAlign.Center
            )
            MonthCalendar(
                month = month,
                onMonthChange = { month = it },
                selectedDate = selectedDate,
                onSelectDate = { selectedDate = it },
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
            dayList.chunked(2).forEach { rowItems ->
                Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                    rowItems.forEach { z ->
                        Column(modifier = Modifier.weight(1f)) {
                            ZamerCard(
                                z = z,
                                onCall = {
                                    val tel = z.phone.filter { c -> c.isDigit() || c == '+' }
                                    if (tel.isNotEmpty()) {
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel")))
                                    }
                                },
                                onDone = { onUpdate(z.copy(status = ZamerStatus.DONE)) },
                                onReschedule = { rescheduleTarget = z }
                            )
                        }
                    }
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showForm) {
        ZamerFormDialog(
            initialDate = selectedDate,
            onSave = { z -> onSave(z); showForm = false },
            onDismiss = { showForm = false }
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
