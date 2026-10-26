package com.mytime.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytime.launcher.calendar.CalendarEvent
import com.mytime.launcher.calendar.CalendarInfo
import com.mytime.launcher.calendar.CalendarRepo
import com.mytime.launcher.data.Graph
import com.mytime.launcher.notify.MediaRepo
import com.mytime.launcher.notify.MediaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ---------------------------------------------------------------- Media player */

/** Now playing: art, title, artist and controls. Tap the card to open the player app. */
@Composable
fun MediaCard(m: MediaState) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { MediaRepo.open(context) },
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                val art = remember(m.art) { m.art?.asImageBitmap() }
                if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp))
                else Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title.ifBlank { m.appLabel }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    m.artist.ifBlank { m.appLabel },
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            if (m.canSkipPrevious) IconButton(onClick = { MediaRepo.previous() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.SkipPrevious, "Previous") }
            IconButton(
                onClick = { MediaRepo.playPause() },
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(if (m.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (m.isPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.onPrimary)
            }
            if (m.canSkipNext) IconButton(onClick = { MediaRepo.next() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.SkipNext, "Next") }
        }
    }
}

/* ---------------------------------------------------------------- Calendar */

private fun timeOf(t: Long, is24h: Boolean) = SimpleDateFormat(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(t))

/** "Starts in 20 min", "Now", "10:30", or "All day". */
fun whenText(e: CalendarEvent, now: Long, is24h: Boolean): String = when {
    e.allDay -> "All day"
    e.begin <= now -> "Now"
    e.begin - now < 60 * 60_000L -> "in ${((e.begin - now) / 60_000).coerceAtLeast(1)} min"
    else -> timeOf(e.begin, is24h)
}

/** Next events under the date in the header. */
@Composable
fun EventsLine(events: List<CalendarEvent>, now: Long, is24h: Boolean, onEvent: (CalendarEvent) -> Unit) {
    val upcoming = remember(events, now) { CalendarRepo.upcoming(events, now) }
    upcoming.forEach { e ->
        Row(
            Modifier.padding(top = 2.dp).clickable { onEvent(e) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(e.color)))
            Spacer(Modifier.width(8.dp))
            Text(
                "${e.title} · ${whenText(e, now, is24h)}",
                style = OnWallpaper.copy(fontSize = 14.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Seven-day agenda, opened by tapping the date. */
@Composable
fun AgendaDialog(events: List<CalendarEvent>, now: Long, is24h: Boolean, actions: SystemActions, onDismiss: () -> Unit) {
    val byDay = remember(events) { events.groupBy { dayKey(it.localDayStart()) }.toList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your agenda") },
        text = {
            if (events.isEmpty()) Text("Nothing in the next 7 days.")
            else LazyColumn(Modifier.heightIn(max = 460.dp)) {
                byDay.forEach { (day, list) ->
                    item(key = "d_$day") {
                        Text(dayLabel(list.first().localDayStart()), style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    }
                    items(list, key = { "${it.eventId}_${it.begin}" }) { e ->
                        Row(Modifier.fillMaxWidth().clickable { actions.openEvent(e); onDismiss() }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(Color(e.color)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val time = if (e.allDay) "All day" else "${timeOf(e.begin, is24h)} – ${timeOf(e.end, is24h)}"
                                Text(listOf(time, e.location).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { actions.openCalendar(); onDismiss() }) { Text("Open calendar") } },
        dismissButton = { TextButton(onClick = { actions.newEvent(); onDismiss() }) { Text("New event") } },
    )
}

private fun dayKey(t: Long) = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(t))

private fun dayLabel(dayStart: Long): String {
    val today = CalendarRepo.startOfToday()
    return when (dayStart) {
        today -> "Today"
        today + 24 * 3_600_000L -> "Tomorrow"
        else -> SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(dayStart))
    }
}

/* ---------------------------------------------------------------- Settings */

fun LazyListScope.mediaCalendarSettings(vm: LauncherViewModel, state: LauncherState, actions: SystemActions) {
    val s = state.settings
    item {
        ToggleRow("Media player", "Play, pause and skip on your home screen (needs notification access)", s.showMediaPlayer) { v ->
            vm.updateSettings { it.copy(showMediaPlayer = v) }
        }
    }
    item {
        ToggleRow("Calendar events", "Upcoming events under the date; tap the date for your agenda", s.calendarEvents) { v ->
            vm.updateSettings { it.copy(calendarEvents = v) }
            if (v) actions.requestCalendarPermission()
            vm.refreshCalendar()
        }
    }
    if (s.calendarEvents) item { CalendarFilterRow(vm) }
}

@Composable
private fun CalendarFilterRow(vm: LauncherViewModel) {
    var open by remember { mutableStateOf(false) }
    val hidden by Graph.prefs.hiddenCalendars.collectAsState()
    ActionRow("Calendars to show", if (hidden.isEmpty()) "All calendars" else "${hidden.size} hidden") { open = true }
    if (open) {
        val calendars by produceState(emptyList<CalendarInfo>()) { value = withContext(Dispatchers.IO) { Graph.calendar.calendars() } }
        AlertDialog(
            onDismissRequest = { open = false; vm.refreshCalendar() },
            title = { Text("Calendars to show") },
            text = {
                if (calendars.isEmpty()) Text("No calendars found, or calendar access is off.")
                else Column {
                    calendars.forEach { c ->
                        Row(Modifier.fillMaxWidth().clickable { Graph.prefs.toggleCalendar(c.id) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = c.id.toString() !in hidden, onCheckedChange = { Graph.prefs.toggleCalendar(c.id) })
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c.color)))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(c.name)
                                if (c.account.isNotBlank()) Text(c.account, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false; vm.refreshCalendar() }) { Text("Done") } },
        )
    }
    Spacer(Modifier.height(0.dp))
}
