package com.mytime.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mytime.launcher.data.AppInfo
import com.mytime.launcher.data.Graph
import com.mytime.launcher.usage.DayUsage
import com.mytime.launcher.usage.UsageRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Weekly screen-time report, computed on the phone from Android's usage history:
 * this week vs last week, a bar per day (leisure time in the accent colour), and the
 * top apps for the chosen day or the whole week.
 */
@Composable
fun ReportScreen(vm: LauncherViewModel, state: LauncherState, actions: SystemActions) {
    val access by vm.hasUsageAccess.collectAsState()
    val days by produceState<List<DayUsage>?>(null, access) {
        value = withContext(Dispatchers.IO) { Graph.usage.dailyUsage(14) }
    }
    EditorScaffold("Your week", { vm.goHome() }) {
        if (!access) {
            item {
                Column(Modifier.padding(24.dp)) {
                    Text("MyTime needs usage access to show your screen time. It stays on your phone.")
                    TextButton(onClick = { actions.openUsageAccess() }) { Text("Allow usage access") }
                }
            }
            return@EditorScaffold
        }
        val all = days
        if (all == null) {
            item { Text("Adding up your week…", Modifier.padding(24.dp)) }
            return@EditorScaffold
        }
        val week = all.takeLast(7)
        val previous = all.dropLast(7)
        item { WeekSummary(week, previous, state.leisure) }
        item { DayChartSection(week, state, vm) }
    }
}

@Composable
private fun WeekSummary(week: List<DayUsage>, previous: List<DayUsage>, leisure: Set<String>) {
    val total = week.sumOf { it.screenOnMs }
    val prevTotal = previous.sumOf { it.screenOnMs }
    // Today is partial, so the average is over full days plus today's share.
    val avg = total / week.size.coerceAtLeast(1)
    val leisureMs = week.sumOf { it.timeIn(leisure) }
    val unlocks = week.sumOf { it.unlocks }
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("Screen time this week", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(UsageRepo.formatDuration(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light)
        Text("About ${UsageRepo.formatDuration(avg)} a day · ${unlocks / week.size.coerceAtLeast(1)} unlocks a day",
            style = MaterialTheme.typography.bodyMedium)
        if (prevTotal > 0) {
            val change = ((total - prevTotal) * 100.0 / prevTotal).roundToInt()
            val text = when {
                change < 0 -> "↓ ${abs(change)}% less than last week. Nice."
                change > 0 -> "↑ $change% more than last week"
                else -> "Same as last week"
            }
            Text(text, style = MaterialTheme.typography.bodyMedium,
                color = if (change <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp))
        }
        if (leisure.isNotEmpty() && total > 0) {
            Text("Leisure apps: ${UsageRepo.formatDuration(leisureMs)} (${(leisureMs * 100 / total)}% of screen time)",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun DayChartSection(week: List<DayUsage>, state: LauncherState, vm: LauncherViewModel) {
    var selected by remember { mutableIntStateOf(week.lastIndex) }
    var wholeWeek by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val fg = LocalContentColor.current
    val max = (week.maxOfOrNull { it.screenOnMs } ?: 0L).coerceAtLeast(60_000L)

    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .pointerInput(week) {
                    detectTapGestures { pos ->
                        selected = (pos.x / (size.width / week.size)).toInt().coerceIn(0, week.lastIndex)
                        wholeWeek = false
                    }
                },
        ) {
            val slot = size.width / week.size
            val barW = slot * 0.55f
            week.forEachIndexed { i, d ->
                val x = slot * i + (slot - barW) / 2
                val h = size.height * d.screenOnMs / max
                val lh = (size.height * d.timeIn(state.leisure) / max).coerceAtMost(h)
                val isSel = i == selected && !wholeWeek
                drawRoundRect(fg.copy(alpha = if (isSel) 0.45f else 0.2f), Offset(x, size.height - h), Size(barW, h), CornerRadius(10f))
                if (lh > 0) drawRoundRect(accent.copy(alpha = if (isSel) 1f else 0.7f), Offset(x, size.height - lh), Size(barW, lh), CornerRadius(10f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            week.forEachIndexed { i, d ->
                Text(
                    if (i == week.lastIndex) "Today" else SimpleDateFormat("EEE", Locale.getDefault()).format(Date(d.start)),
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == selected && !wholeWeek) accent else fg.copy(alpha = 0.7f),
                )
            }
        }
        if (state.leisure.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                Text("  Leisure apps", style = MaterialTheme.typography.labelSmall)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            FilterChip(selected = !wholeWeek, onClick = { wholeWeek = false }, label = { Text("Selected day") })
            FilterChip(selected = wholeWeek, onClick = { wholeWeek = true }, label = { Text("Whole week") })
        }

        val day = week[selected]
        val perApp: Map<String, Long> = if (wholeWeek) {
            week.flatMap { it.perApp.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        } else day.perApp
        val screenOn = if (wholeWeek) week.sumOf { it.screenOnMs } else day.screenOnMs
        val unlocks = if (wholeWeek) week.sumOf { it.unlocks } else day.unlocks
        Spacer(Modifier.height(8.dp))
        Text(
            if (wholeWeek) "Whole week" else SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(day.start)),
            style = MaterialTheme.typography.titleMedium,
        )
        Text("${UsageRepo.formatDuration(screenOn)} screen time · $unlocks unlocks", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(8.dp))
        TopApps(perApp, state, vm)
    }
}

@Composable
private fun TopApps(perApp: Map<String, Long>, state: LauncherState, vm: LauncherViewModel) {
    val context = LocalContext.current
    val byPackage = remember(state.allApps) { state.allApps.associateBy { it.packageName } }
    val top = perApp.entries.filter { it.value >= 60_000L && it.key != context.packageName }
        .sortedByDescending { it.value }.take(10)
    val most = top.firstOrNull()?.value ?: 1L
    if (top.isEmpty()) Text("No app was used for a minute or more.", style = MaterialTheme.typography.bodyMedium)
    top.forEach { (pkg, ms) ->
        val app: AppInfo? = byPackage[pkg]
        val label = app?.label ?: runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        val leisure = pkg in state.leisure
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (app != null) AppIcon(app, 32, false) else Spacer(Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), maxLines = 1)
                    if (leisure) Text("leisure  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(UsageRepo.formatDuration(ms), style = MaterialTheme.typography.bodyMedium)
                }
                Box(
                    Modifier.padding(top = 4.dp).fillMaxWidth(ms.toFloat() / most).height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (leisure) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.3f)),
                )
            }
        }
    }
}
