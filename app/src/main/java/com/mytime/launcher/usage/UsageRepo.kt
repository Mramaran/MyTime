package com.mytime.launcher.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** One continuous stretch of an app in the foreground. */
data class AppSession(val packageName: String, val start: Long, val end: Long) {
    val duration get() = end - start
}

/** One day of usage, from midnight to midnight (or to now, for today). */
class DayUsage(val start: Long, val end: Long) {
    var screenOnMs = 0L
    var unlocks = 0
    val perApp = HashMap<String, Long>()
    val appTotalMs: Long get() = perApp.values.sum()
    fun timeIn(packages: Set<String>) = perApp.filterKeys { it in packages }.values.sum()
}

data class UsageSnapshot(
    val screenOnTodayMs: Long = 0,
    val unlocksToday: Int = 0,
    val perAppTodayMs: Map<String, Long> = emptyMap(),
    /** Launch-frequency score per package over the last 14 days, decayed by age. */
    val scores: Map<String, Double> = emptyMap(),
)

/**
 * Reads screen time from UsageStatsManager, using the same rules as Niagara's reader:
 * a session is ACTIVITY_RESUMED .. ACTIVITY_STOPPED per package, and two stretches less
 * than 10 s apart are merged (activity changes inside one app are not new sessions).
 */
class UsageRepo(private val context: Context) {

    private val usm = context.getSystemService(UsageStatsManager::class.java)

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun snapshot(now: Long = System.currentTimeMillis()): UsageSnapshot {
        if (!hasAccess()) return UsageSnapshot()
        // Exactly the weekly report's "Today", so home and the report always agree.
        val today = dailyUsage(1, now).lastOrNull()
        val screenOn = today?.screenOnMs ?: 0L
        val unlocks = today?.unlocks ?: 0
        val perApp = today?.perApp?.toMap().orEmpty()
        return UsageSnapshot(
            screenOnTodayMs = screenOn,
            unlocksToday = unlocks,
            perAppTodayMs = perApp,
            scores = launchScores(now),
        )
    }

    /** Foreground sessions between [start] and [end]. */
    fun sessions(start: Long, end: Long, packages: Set<String>? = null): List<AppSession> {
        val events = usm.queryEvents(start, end) ?: return emptyList()
        val resumed = HashMap<String, MutableSet<String>>()
        val openStart = HashMap<String, Long>()
        val pendingEnd = HashMap<String, Long>()
        val pendingStart = HashMap<String, Long>()
        val out = ArrayList<AppSession>()
        val e = UsageEvents.Event()
        val stopType = if (Build.VERSION.SDK_INT >= 29) STOPPED else UsageEvents.Event.MOVE_TO_BACKGROUND

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            if (packages != null && pkg !in packages) continue
            val t = e.timeStamp
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    // Only one app is in front at a time: if another app's session never got a
                    // STOPPED event (it was killed, or the log has a gap), it ends here.
                    if (packages == null) {
                        for (other in openStart.keys.filter { it != pkg }) {
                            resumed[other]?.clear()
                            pendingStart[other] = openStart.remove(other)!!
                            pendingEnd[other] = t
                        }
                    }
                    val set = resumed.getOrPut(pkg) { HashSet() }
                    if (set.isEmpty() && pkg !in openStart) {
                        val ended = pendingEnd.remove(pkg)
                        val began = pendingStart.remove(pkg)
                        if (ended != null && began != null && t - ended <= MERGE_GAP_MS) {
                            openStart[pkg] = began
                        } else {
                            if (ended != null && began != null) out += AppSession(pkg, began, ended)
                            openStart[pkg] = t
                        }
                    }
                    set += e.className ?: ""
                }
                stopType -> {
                    val set = resumed[pkg] ?: continue
                    set -= e.className ?: ""
                    if (set.isEmpty()) {
                        openStart.remove(pkg)?.let { began ->
                            pendingStart[pkg] = began
                            pendingEnd[pkg] = t
                        }
                    }
                }
            }
        }
        for ((pkg, ended) in pendingEnd) out += AppSession(pkg, pendingStart.getValue(pkg), ended)
        for ((pkg, began) in openStart) out += AppSession(pkg, began, end)
        return out.sortedBy { it.start }
    }

    /** Package currently in the foreground, if usage events can tell. */
    fun foregroundPackage(now: Long = System.currentTimeMillis()): String? =
        sessions(now - 60 * 60_000L, now).lastOrNull { it.end >= now - 1_000 }?.packageName

    /** Stretches of time the screen was on between [start] and [end]. */
    fun screenOnIntervals(start: Long, end: Long): List<Pair<Long, Long>> {
        val events = usm.queryEvents(start, end) ?: return emptyList()
        val e = UsageEvents.Event()
        val out = ArrayList<Pair<Long, Long>>()
        var onSince: Long? = null
        var sawAny = false
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                SCREEN_INTERACTIVE -> { if (onSince == null) onSince = e.timeStamp; sawAny = true }
                SCREEN_NON_INTERACTIVE -> {
                    // A screen-off with no screen-on before it means the screen was on at [start].
                    val from = onSince ?: if (!sawAny) start else null
                    if (from != null) out += from to e.timeStamp
                    onSince = null
                    sawAny = true
                }
            }
        }
        if (onSince != null) out += onSince to end
        else if (!sawAny) out += start to end // no screen events at all: assume it stayed on
        return out
    }

    /** Total screen-on time and number of unlocks between [start] and [end]. */
    fun screenStats(start: Long, end: Long): Pair<Long, Int> {
        val events = usm.queryEvents(start, end) ?: return 0L to 0
        val e = UsageEvents.Event()
        var onSince: Long? = null
        var total = 0L
        var unlocks = 0
        var sawAny = false
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                SCREEN_INTERACTIVE -> { onSince = e.timeStamp; sawAny = true }
                SCREEN_NON_INTERACTIVE -> {
                    // A screen-off with no screen-on before it means the screen was on at [start].
                    total += e.timeStamp - (onSince ?: if (!sawAny) start else e.timeStamp)
                    onSince = null
                    sawAny = true
                }
                KEYGUARD_HIDDEN -> unlocks++
            }
        }
        onSince?.let { total += end - it }
        return total to unlocks
    }

    /**
     * Start of the current "phone session": screen-on time in which no screen-off gap is
     * longer than 10 minutes. Looks back at most 3 hours. Null if the screen is off now.
     */
    fun phoneSessionStart(now: Long = System.currentTimeMillis()): Long? {
        val events = usm.queryEvents(now - LOOKBACK_MS, now) ?: return null
        val e = UsageEvents.Event()
        var sessionStart = now - LOOKBACK_MS
        var lastOff: Long? = null
        var screenOn = true
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                SCREEN_NON_INTERACTIVE -> { lastOff = e.timeStamp; screenOn = false }
                SCREEN_INTERACTIVE -> {
                    val off = lastOff
                    if (off != null && e.timeStamp - off > SESSION_GAP_MS) sessionStart = e.timeStamp
                    screenOn = true
                }
            }
        }
        return if (screenOn) sessionStart else null
    }

    /** Time spent in [packages] since [since], with overlapping sessions counted once. */
    fun timeIn(packages: Set<String>, since: Long, now: Long = System.currentTimeMillis()): Long {
        if (packages.isEmpty()) return 0
        val ranges = sessions(since, now, packages).map { max(it.start, since) to min(it.end, now) }
            .filter { it.second > it.first }
            .sortedBy { it.first }
        var total = 0L
        var curStart = -1L
        var curEnd = -1L
        for ((s, en) in ranges) {
            if (s > curEnd) {
                if (curEnd > curStart) total += curEnd - curStart
                curStart = s; curEnd = en
            } else curEnd = max(curEnd, en)
        }
        if (curEnd > curStart) total += curEnd - curStart
        return total
    }

    /**
     * Launch score per package: each launch in the last 14 days counts 0.5^(ageDays / 7),
     * and launches less than 10 minutes apart count once (same idea as Niagara's scorer).
     */
    private fun launchScores(now: Long): Map<String, Double> {
        val scores = HashMap<String, Double>()
        val lastLaunch = HashMap<String, Long>()
        for (s in sessions(now - 14 * DAY_MS, now)) {
            val prev = lastLaunch[s.packageName]
            lastLaunch[s.packageName] = s.end
            if (prev != null && s.start - prev < 10 * 60_000L) continue
            val ageDays = (now - s.start).toDouble() / DAY_MS
            scores[s.packageName] = (scores[s.packageName] ?: 0.0) + 0.5.pow(ageDays / 7)
        }
        return scores
    }

    /**
     * Per-day usage for the last [days] days (oldest first, today last): screen-on time,
     * unlocks, and time per app. Sessions that cross midnight are split between the two days.
     */
    fun dailyUsage(days: Int = 14, now: Long = System.currentTimeMillis()): List<DayUsage> {
        if (!hasAccess()) return emptyList()
        val todayStart = startOfDay(now)
        val starts = (days - 1 downTo 0).map { back -> shiftDays(todayStart, -back) }
        val rangeStart = starts.first()
        val out = starts.mapIndexed { i, s -> DayUsage(s, if (i == starts.lastIndex) now else starts[i + 1]) }

        // App time only counts while the screen is on, so no app can exceed the screen time.
        // Reading from a day earlier catches a screen (or app) that was already on at midnight.
        val lookback = shiftDays(rangeStart, -1)
        val screenOn = screenOnIntervals(lookback, now)
        for (session in sessions(lookback, now)) {
            for ((onStart, onEnd) in screenOn) {
                val s = max(session.start, onStart)
                val e = min(session.end, onEnd)
                if (e <= s) continue
                for (d in out) {
                    val a = max(s, d.start)
                    val b = min(e, d.end)
                    if (b > a) d.perApp[session.packageName] = (d.perApp[session.packageName] ?: 0L) + (b - a)
                }
            }
        }
        for (d in out) {
            // Screen time comes from the same intervals the app times were clipped to.
            d.screenOnMs = screenOn.sumOf { (s, e) -> (min(e, d.end) - max(s, d.start)).coerceAtLeast(0L) }
            d.unlocks = screenStats(d.start, d.end).second
        }
        return out
    }

    companion object {
        const val STOPPED = 23
        const val SCREEN_INTERACTIVE = 15
        const val SCREEN_NON_INTERACTIVE = 16
        const val KEYGUARD_HIDDEN = 18
        const val MERGE_GAP_MS = 10_000L
        const val SESSION_GAP_MS = 10 * 60_000L
        const val LOOKBACK_MS = 3 * 60 * 60_000L
        const val DAY_MS = 24 * 60 * 60_000L

        fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        /** Midnight [n] days from [dayStart], respecting daylight-saving changes. */
        fun shiftDays(dayStart: Long, n: Int): Long = Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, n)
        }.timeInMillis

        fun formatDuration(ms: Long): String {
            val totalMin = ms / 60_000
            val h = totalMin / 60
            val m = totalMin % 60
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }
    }
}
