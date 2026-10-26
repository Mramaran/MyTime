package com.mytime.launcher.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.mytime.launcher.data.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String,
    val calendarId: Long,
) {
    /** All-day events are stored as UTC midnights; this is the local day they belong to. */
    fun localDayStart(): Long = if (!allDay) begin else Calendar.getInstance().apply {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = begin }
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

data class CalendarInfo(val id: Long, val name: String, val color: Int, val account: String)

/**
 * Upcoming events for the next 7 days, from the phone's calendars (Niagara's query: event
 * instances, declined events left out, chosen calendars filtered). Refreshes when any calendar
 * changes.
 */
class CalendarRepo(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events
    private var observing = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun refresh() {
        scope.launch {
            if (!Graph.prefs.settings.value.calendarEvents || !hasPermission()) {
                _events.value = emptyList()
                return@launch
            }
            if (!observing) {
                runCatching { context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer) }
                observing = true
            }
            _events.value = runCatching { query() }.getOrDefault(emptyList())
        }
    }

    private fun query(): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        val start = now - 24 * 3_600_000L // includes all-day events and events already under way
        val end = now + 7 * 24 * 3_600_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.CALENDAR_ID,
        )
        val selection = "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}" +
            " AND ${CalendarContract.Instances.VISIBLE} = 1"
        val hidden = Graph.prefs.hiddenCalendars.value
        val out = ArrayList<CalendarEvent>()
        context.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val e = CalendarEvent(
                    eventId = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                    begin = c.getLong(2),
                    end = c.getLong(3),
                    allDay = c.getInt(4) == 1,
                    color = c.getInt(5),
                    location = c.getString(6).orEmpty(),
                    calendarId = c.getLong(7),
                )
                if (e.calendarId.toString() in hidden) continue
                // Drop what has already finished (timed) or was before today (all-day).
                val today = startOfToday()
                if (!e.allDay && e.end < now) continue
                if (e.allDay && e.localDayStart() < today) continue
                out += e
            }
        }
        return out
    }

    fun calendars(): List<CalendarInfo> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.ACCOUNT_NAME,
        )
        val out = ArrayList<CalendarInfo>()
        runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) out += CalendarInfo(c.getLong(0), c.getString(1).orEmpty(), c.getInt(2), c.getString(3).orEmpty())
            }
        }
        return out
    }

    companion object {
        fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        /**
         * What the header shows: events under way or starting in the next 24 hours, then
         * today's all-day events; at most [max].
         */
        fun upcoming(events: List<CalendarEvent>, now: Long, max: Int = 2): List<CalendarEvent> {
            val timed = events.filter { !it.allDay && it.begin < now + 24 * 3_600_000L && it.end > now }
            val tomorrow = startOfToday() + 24 * 3_600_000L
            val allDayToday = events.filter { it.allDay && it.localDayStart() < tomorrow }
            return (timed + allDayToday).take(max)
        }
    }
}
