package com.dub.calorietracker.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dub.calorietracker.calc.WeekPlanner
import com.dub.calorietracker.data.AppState
import com.dub.calorietracker.data.ChatExport
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Week / Month switch at the top of the Progress tab. */
@Composable
fun ProgressToggle(month: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !month, onClick = { onChange(false) }, label = { Text("Week") })
        FilterChip(selected = month, onClick = { onChange(true) }, label = { Text("Month") })
    }
}

/** Copies [text] for ChatGPT and says so. */
@Composable
fun rememberCopyForChat(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    return { text ->
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(ctx, "Copied — paste it into ChatGPT and ask how you did", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun MonthScreen(vm: AppViewModel, modifier: Modifier, onToggle: (Boolean) -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val stats = ChatExport.stats(state, first, last, today)
    val copy = rememberCopyForChat()
    val wl = ChatExport.weightLabel(ChatExport.units(state))
    val started = !first.isAfter(today)

    LazyColumn(modifier.fillMaxSize()) {
        item {
            ScreenHeader(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                if (month == YearMonth.from(today)) "This month" else null,
                onPrev = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
            )
        }
        item { ProgressToggle(month = true, onChange = onToggle) }

        // ----- calendar -----
        item {
            SectionCard(padding = 10.dp) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                        Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(4.dp))
                val lead = WeekPlanner.dayIndex(first)
                val cells: List<LocalDate?> = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { d -> DayCell(state, d, today, Modifier.weight(1f), onOpenDay) }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Calories · weight · ", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Filled.FitnessCenter, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(" gym · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(" cardio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Tap a day to see or edit it.", Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ----- summary -----
        if (started) item {
            SectionCard {
                Text("Month so far", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                SummaryLine("Days with food logged", "${stats.loggedDays} of ${stats.days}")
                if (stats.loggedDays > 0) {
                    SummaryLine("Average calories", num(stats.avgCalories) + if (state.plan != null) " / ${num(stats.avgTarget)}" else "")
                    SummaryLine("Average protein", num(stats.avgProtein) + " g" +
                        (state.plan?.proteinG?.takeIf { it > 0 }?.let { " / $it g" } ?: ""))
                }
                SummaryLine("Gym sessions", stats.gym.toString())
                SummaryLine("Cardio sessions", stats.cardio.toString())
                val w = stats.weights
                if (w.size >= 2) {
                    val change = w.last().second - w.first().second
                    SummaryLine("Weight", "${"%.1f".format(w.first().second)} → ${"%.1f".format(w.last().second)} $wl " +
                        "(${if (change < 0) "−" else "+"}${"%.1f".format(abs(change))})")
                    Spacer(Modifier.height(12.dp))
                    WeightChart(w.map { it.second }, w.map { it.first.dayOfMonth }, month.lengthOfMonth())
                } else if (w.size == 1) SummaryLine("Weight", "${"%.1f".format(w.first().second)} $wl")
            }
        }

        if (started) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = { copy(ChatExport.text(state, first, last, today)) }) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("Copy month for ChatGPT")
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DayCell(state: AppState, d: LocalDate?, today: LocalDate, modifier: Modifier, onOpenDay: (LocalDate) -> Unit) {
    if (d == null) { Spacer(modifier.height(66.dp)); return }
    val eaten = WeekPlanner.eaten(state.entries, d)
    val logged = state.entries.any { it.date == d.toString() }
    val target = WeekPlanner.dayTarget(state, d)
    val log = state.dayLogs[d.toString()]
    val units = ChatExport.units(state)
    val isToday = d == today
    Column(
        modifier.height(66.dp).padding(1.dp).clip(RoundedCornerShape(10.dp))
            .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onOpenDay(d) }
            .alpha(if (d.isAfter(today)) 0.4f else 1f)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium)
        }
        // Gym and cardio icons on their own line so both fit in a narrow cell.
        if (log?.gym == true || log?.cardio == true) Row {
            if (log.gym) Icon(Icons.Filled.FitnessCenter, "Gym", Modifier.size(11.dp), tint = MaterialTheme.colorScheme.primary)
            if (log.cardio) Icon(Icons.AutoMirrored.Filled.DirectionsRun, "Cardio", Modifier.size(11.dp), tint = LocalAppColors.current.calories)
        }
        if (logged) Text(num(eaten.calories), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (target > 0 && eaten.calories > target * 1.05) OverColor else MaterialTheme.colorScheme.primary)
        log?.weightKg?.let {
            Text(String.format(Locale.getDefault(), "%.1f", ChatExport.weightIn(it, units)), fontSize = 10.sp, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Simple line of the month's morning weights (x = day of month). */
@Composable
private fun WeightChart(values: List<Double>, days: List<Int>, monthLength: Int) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val lo = values.min()
    val hi = values.max()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.height(96.dp).width(44.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("%.1f".format(hi), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("%.1f".format(lo), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.weight(1f).height(96.dp)) {
            Canvas(Modifier.fillMaxSize().padding(vertical = 6.dp)) {
                val span = (hi - lo).takeIf { it > 0.01 } ?: 1.0
                fun pt(i: Int) = Offset(
                    x = size.width * (days[i] - 1) / (monthLength - 1).coerceAtLeast(1),
                    y = (size.height * (1 - (values[i] - lo) / span)).toFloat(),
                )
                drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                val path = Path().apply { values.indices.forEach { i -> pt(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } } }
                drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                values.indices.forEach { drawCircle(line, 3.5.dp.toPx(), pt(it)) }
            }
        }
    }
}
