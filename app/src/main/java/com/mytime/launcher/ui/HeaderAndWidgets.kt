package com.mytime.launcher.ui

import android.app.AlarmManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.mytime.launcher.data.Graph
import com.mytime.launcher.data.HomeWidget
import com.mytime.launcher.weather.Weather
import com.mytime.launcher.weather.WeatherRepo
import com.mytime.launcher.weather.WeatherState
import com.mytime.launcher.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/* ---------------------------------------------------------------- At a glance */

/** Clock in the chosen style, then the info line: date · next alarm · battery · weather. */
@Composable
fun AtAGlanceHeader(vm: LauncherViewModel, state: LauncherState, actions: SystemActions, now: Long, is24h: Boolean) {
    val context = LocalContext.current
    val weather by vm.weather.collectAsState()
    var showForecast by remember { mutableStateOf(false) }
    var showAgenda by remember { mutableStateOf(false) }
    val events by vm.calendarEvents.collectAsState()

    ClockView(ClockStyle.of(state.settings.clockStyle), now, is24h)

    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }
    val alarm = remember(now) {
        context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime?.let {
            SimpleDateFormat(if (is24h) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault()).format(Date(it))
        }
    }
    val battery = remember(now) {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level >= 0) level * 100 / scale else null
        }
    }
    val info = OnWallpaper.copy(fontSize = 15.sp)
    Row(
        Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(date, style = info, modifier = Modifier.clickable {
            if (state.settings.calendarEvents && Graph.calendar.hasPermission()) showAgenda = true else actions.openCalendar()
        })
        alarm?.let { Text("⏰ $it", style = info, modifier = Modifier.clickable { actions.showAlarms() }) }
        battery?.let { Text("🔋 $it%", style = info) }
        if (state.settings.weatherEnabled) WeatherChip(weather, info) { showForecast = true }
    }
    if (state.settings.calendarEvents) EventsLine(events, now, is24h) { actions.openEvent(it) }
    if (showForecast) weather.weather?.let { ForecastDialog(it, weather.place) { showForecast = false } }
    if (showAgenda) AgendaDialog(events, now, is24h, actions) { showAgenda = false }
}

@Composable
private fun WeatherChip(w: WeatherState, style: androidx.compose.ui.text.TextStyle, onClick: () -> Unit) {
    val current = w.weather
    val text = when {
        current != null -> {
            val (emoji, _) = WeatherRepo.describe(current.code, current.isDay)
            "$emoji ${current.temp.roundToInt()}°"
        }
        w.loading -> "…"
        w.error != null -> "⚠ ${w.error}"
        else -> return
    }
    Text(text, style = style, modifier = Modifier.clickable(enabled = current != null, onClick = onClick))
}

@Composable
private fun ForecastDialog(w: Weather, place: String, onDismiss: () -> Unit) {
    val (emoji, desc) = WeatherRepo.describe(w.code, w.isDay)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(listOf(place, "Weather").first { it.isNotBlank() }) },
        text = {
            Column {
                Text("$emoji ${w.temp.roundToInt()}${w.unit}  $desc", style = MaterialTheme.typography.headlineSmall)
                Text("Feels like ${w.feelsLike.roundToInt()}°", style = MaterialTheme.typography.bodyMedium)
                if (w.rainShareSoon in 0.1..0.9) Text("Rain likely in the next 2 hours", color = Color(0xFF8FC1FF))

                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    w.hourly.take(12).forEach { h ->
                        val hour = runCatching { LocalDateTime.parse(h.time).hour }.getOrDefault(0)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("%02d".format(hour), style = MaterialTheme.typography.labelSmall)
                            Text(WeatherRepo.describe(h.code, h.isDay).first)
                            Text("${h.temp.roundToInt()}°", style = MaterialTheme.typography.labelLarge)
                            if (h.precipChance >= 20) Text("${h.precipChance}%", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8FC1FF))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                w.daily.forEach { d ->
                    val day = runCatching {
                        LocalDate.parse(d.date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    }.getOrDefault(d.date)
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(day, Modifier.width(48.dp))
                        Text(WeatherRepo.describe(d.code).first, Modifier.width(36.dp))
                        Text(if (d.precipChance >= 20) "${d.precipChance}%" else "", Modifier.width(48.dp), color = Color(0xFF8FC1FF))
                        Spacer(Modifier.weight(1f))
                        Text("${d.max.roundToInt()}° / ${d.min.roundToInt()}°")
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Open-Meteo's data is CC BY 4.0, which asks for attribution.
                Text("Weather data by Open-Meteo.com", style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.6f))
            }
        },
    )
}

/* ---------------------------------------------------------------- Home widgets */

/** One hosted widget. Long-press opens its options. */
@Composable
fun HomeWidgetItem(widget: HomeWidget, onLongPress: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(widget.heightDp.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        val widthDp = maxWidth.value.toInt()
        AndroidView(
            factory = { ctx ->
                Widgets.createView(ctx, widget.id)?.apply { this.onLongPress = onLongPress }
                    ?: android.widget.TextView(ctx).apply {
                        text = "This widget is no longer available. Long-press to remove it."
                        setTextColor(android.graphics.Color.WHITE)
                        setOnLongClickListener { onLongPress(); true }
                    }
            },
            update = { view ->
                (view as? com.mytime.launcher.widget.LongPressWidgetView)?.let {
                    it.onLongPress = onLongPress
                    Widgets.updateSize(it, widthDp, widget.heightDp)
                }
            },
            modifier = Modifier.fillMaxWidth().height(widget.heightDp.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetOptionsSheet(widget: HomeWidget, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val label = remember(widget.id) {
        Widgets.info(widget.id)?.loadLabel(context.packageManager) ?: "Widget"
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text("Height", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
        Row(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("S" to 120, "M" to 180, "L" to 260, "XL" to 360).forEach { (name, h) ->
                FilterChip(selected = widget.heightDp == h, onClick = { Widgets.setHeight(widget.id, h) }, label = { Text(name) })
            }
        }
        ActionRow("Move up", null) { Widgets.move(widget.id, -1); onDismiss() }
        ActionRow("Move down", null) { Widgets.move(widget.id, 1); onDismiss() }
        ActionRow("Remove widget", null) { Widgets.remove(widget.id); onDismiss() }
        Spacer(Modifier.height(24.dp))
    }
}

/** All widgets on the phone, grouped by app, with their preview images. */
@Composable
fun PickWidgetScreen(vm: LauncherViewModel, actions: SystemActions) {
    val context = LocalContext.current
    val providers by produceState(emptyList<AppWidgetProviderInfo>()) {
        value = withContext(Dispatchers.IO) { Widgets.providers(context) }
    }
    val grouped = remember(providers) { providers.groupBy { Widgets.appLabel(context, it) }.toList() }
    EditorScaffold("Add a widget", { vm.screen.value = Screen.Settings }) {
        if (providers.isEmpty()) item { Text("Loading widgets…", modifier = Modifier.padding(24.dp)) }
        grouped.forEach { (app, list) ->
            item(key = "h_$app") {
                Text(app, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 4.dp))
            }
            items(list, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
                WidgetChoice(info) {
                    vm.screen.value = Screen.Home
                    actions.addWidget(info)
                }
            }
        }
    }
}

@Composable
private fun WidgetChoice(info: AppWidgetProviderInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val d = info.loadPreviewImage(context, context.resources.displayMetrics.densityDpi) ?: info.loadIcon(context, 0)
                val w = d.intrinsicWidth.coerceIn(1, 600)
                val h = d.intrinsicHeight.coerceIn(1, 400)
                d.toBitmap(w, h).asImageBitmap()
            }.getOrNull()
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 96.dp, height = 64.dp), contentAlignment = Alignment.Center) {
            preview?.let { Image(it, contentDescription = null, modifier = Modifier.heightIn(max = 64.dp)) }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(info.loadLabel(context.packageManager), fontWeight = FontWeight.Medium)
            Text("${(info.minWidth / density).toInt()} × ${(info.minHeight / density).toInt()} dp",
                style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.6f))
        }
    }
}

/* ---------------------------------------------------------------- Settings */

fun LazyListScope.headerSettings(vm: LauncherViewModel, state: LauncherState, actions: SystemActions, widgetCount: Int) {
    val s = state.settings
    item { Header("Clock, weather & widgets") }
    item { ActionRow("Clock style", ClockStyle.of(s.clockStyle).title) { vm.screen.value = Screen.ClockStyles } }
    item {
        ToggleRow("Weather", "Current weather in the header; tap it for the forecast", s.weatherEnabled) { v ->
            vm.updateSettings { it.copy(weatherEnabled = v) }
            if (v && s.weatherPlace.isBlank()) actions.requestLocationPermission()
            if (v) vm.refreshWeather(force = true)
        }
    }
    if (s.weatherEnabled) {
        item {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !s.fahrenheit, onClick = { vm.updateSettings { it.copy(fahrenheit = false) }; vm.refreshWeather(true) }, label = { Text("°C") })
                FilterChip(selected = s.fahrenheit, onClick = { vm.updateSettings { it.copy(fahrenheit = true) }; vm.refreshWeather(true) }, label = { Text("°F") })
            }
        }
        item { PlaceRow(vm, s.weatherPlace, actions) }
    }
    item { ActionRow("Add a widget", "$widgetCount on your home screen. Long-press a widget to resize or remove it.") { vm.screen.value = Screen.PickWidget } }
}

@Composable
private fun PlaceRow(vm: LauncherViewModel, place: String, actions: SystemActions) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(place) { mutableStateOf(place) }
    ActionRow("Weather location", if (place.isBlank()) "Current location" else place) { editing = true }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Weather location") },
            text = {
                Column {
                    Text("Type a city, or leave empty to use your phone's location.")
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
                        placeholder = { Text("e.g. Chennai") }, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = text.trim()
                    vm.updateSettings { it.copy(weatherPlace = p) }
                    if (p.isBlank()) actions.requestLocationPermission()
                    vm.refreshWeather(force = true)
                    editing = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}
