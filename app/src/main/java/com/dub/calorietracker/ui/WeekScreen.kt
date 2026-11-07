package com.dub.calorietracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dub.calorietracker.calc.Macros
import com.dub.calorietracker.calc.WeekPlanner
import com.dub.calorietracker.data.ChatExport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WeekScreen(vm: AppViewModel, modifier: Modifier, onSetUpPlan: () -> Unit, onOpenDay: (LocalDate) -> Unit) {
    var month by rememberSaveable { mutableStateOf(false) }
    if (month) MonthScreen(vm, modifier, onToggle = { month = it }, onOpenDay = onOpenDay)
    else WeekView(vm, modifier, onSetUpPlan, onToggle = { month = it })
}

@Composable
private fun WeekView(vm: AppViewModel, modifier: Modifier, onSetUpPlan: () -> Unit, onToggle: (Boolean) -> Unit) {
    val copy = rememberCopyForChat()
    val state by vm.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var anchor by remember { mutableStateOf(today) }
    var swapFrom by remember { mutableStateOf<Int?>(null) }

    val dates = WeekPlanner.weekDates(anchor)
    val targets = WeekPlanner.weekTargets(state, anchor)
    val eatenPerDay = dates.map { WeekPlanner.eaten(state.entries, it) }
    val weekEaten = eatenPerDay.fold(Macros.ZERO) { a, b -> a + b }
    val weekTarget = targets.sum()
    val weekMacroGoal = targets.map { WeekPlanner.macroTargets(it, state) }.fold(Macros.ZERO) { a, b -> a + b }
    val zigzag = WeekPlanner.isZigzag(state)
    val swapped = state.weekOverrides.containsKey(WeekPlanner.weekStart(anchor).toString())
    val fmt = DateTimeFormatter.ofPattern("MMM d")

    LazyColumn(modifier.fillMaxSize()) {
        item {
            ScreenHeader(
                if (WeekPlanner.weekStart(anchor) == WeekPlanner.weekStart(today)) "This week" else "Week",
                "${dates.first().format(fmt)} – ${dates.last().format(fmt)}",
                onPrev = { anchor = anchor.minusWeeks(1); swapFrom = null },
                onNext = { anchor = anchor.plusWeeks(1); swapFrom = null },
            )
        }
        item { ProgressToggle(month = false, onChange = onToggle) }

        if (state.plan == null) {
            item {
                SectionCard {
                    Text("No plan yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onSetUpPlan) { Text("Set up plan") }
                }
            }
            return@LazyColumn
        }

        item {
            SectionCard(padding = 24.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Stat(num(weekEaten.calories), "Eaten", Modifier.weight(1f))
                    CalorieGauge(weekEaten.calories, weekTarget.toDouble())
                    Stat(num(weekTarget), "Target", Modifier.weight(1f))
                }
                val daysSoFar = dates.count { !it.isAfter(today) }
                val plannedSoFar = dates.take(daysSoFar).sumOf { targets[WeekPlanner.dayIndex(it)] }
                if (daysSoFar in 1..6) {
                    val diff = plannedSoFar - weekEaten.calories
                    Text(
                        if (diff >= 0) "On pace · ${num(diff)} cal under plan so far"
                        else "${num(-diff)} cal over plan so far — lighter days ahead keep you on target",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (diff >= 0) MaterialTheme.colorScheme.onSurfaceVariant else OverColor,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MacroColumn("Carbs", weekEaten.carbs, weekMacroGoal.carbs, Modifier.weight(1f))
                    MacroColumn("Protein", weekEaten.protein, weekMacroGoal.protein, Modifier.weight(1f))
                    MacroColumn("Fat", weekEaten.fat, weekMacroGoal.fat, Modifier.weight(1f))
                }
            }
        }

        if (zigzag) item {
            Text(
                if (swapFrom == null) "Tap a day, then another day, to swap their calorie targets."
                else "Now tap the day to swap with ${dates[swapFrom!!].format(DateTimeFormatter.ofPattern("EEEE"))} (or tap it again to cancel).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
            )
        }

        item {
            SectionCard(padding = 8.dp) {
                dates.forEachIndexed { i, d ->
                    val e = eatenPerDay[i]
                    val target = targets[WeekPlanner.dayIndex(d)]
                    val selected = swapFrom == i
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable(enabled = zigzag) {
                                val from = swapFrom
                                when {
                                    from == null -> swapFrom = i
                                    from == i -> swapFrom = null
                                    else -> {
                                        vm.swapDays(anchor, WeekPlanner.dayIndex(dates[from]), WeekPlanner.dayIndex(d))
                                        swapFrom = null
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(d.format(DateTimeFormatter.ofPattern("EEEE")),
                                fontWeight = if (d == today) FontWeight.Bold else FontWeight.Medium,
                                color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            if (zigzag && WeekPlanner.isHighDay(targets, WeekPlanner.dayIndex(d))) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Filled.Bolt, "High day", Modifier.size(16.dp), tint = Amber)
                            }
                            Spacer(Modifier.weight(1f))
                            Text("${num(e.calories)} / ${num(target)}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val log = state.dayLogs[d.toString()]
                        val checkIn = listOfNotNull(
                            log?.weightKg?.let { "%.1f".format(ChatExport.weightIn(it, ChatExport.units(state))) + " " + ChatExport.weightLabel(ChatExport.units(state)) },
                            if (log?.gym == true) "Gym" else null,
                            if (log?.cardio == true) "Cardio" else null,
                            log?.waistCm?.let { "waist " + "%.1f".format(ChatExport.waistIn(it, ChatExport.units(state))) + " " + ChatExport.waistLabel(ChatExport.units(state)) },
                        )
                        if (checkIn.isNotEmpty()) Text(checkIn.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(if (target > 0) (e.calories / target).toFloat() else 0f, LocalAppColors.current.calories, 6.dp)
                    }
                }
            }
        }

        if (swapped) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { vm.resetWeek(anchor) }) {
                    Text("Reset this week to the original schedule", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!dates.first().isAfter(today)) item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                Button(onClick = { copy(ChatExport.text(state, dates.first(), dates.first(), today)) }) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("Copy week for ChatGPT")
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
