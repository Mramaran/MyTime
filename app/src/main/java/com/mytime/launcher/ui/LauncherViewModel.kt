package com.mytime.launcher.ui

import android.app.Application
import android.content.pm.LauncherApps
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ezylang.evalex.Expression
import com.mytime.launcher.data.AppInfo
import com.mytime.launcher.data.AppRepository
import com.mytime.launcher.data.Graph
import com.mytime.launcher.calendar.CalendarEvent
import com.mytime.launcher.data.HomeWidget
import com.mytime.launcher.notify.MediaRepo
import com.mytime.launcher.notify.MediaState
import com.mytime.launcher.data.SavedTheme
import com.mytime.launcher.icons.IconLoader
import com.mytime.launcher.data.Settings
import com.mytime.launcher.weather.WeatherState
import com.mytime.launcher.notify.NotifItem
import com.mytime.launcher.notify.NotifListener
import com.mytime.launcher.notify.SummaryScheduler
import com.mytime.launcher.usage.UsageBreakerService
import com.mytime.launcher.usage.UsageRepo
import com.mytime.launcher.usage.UsageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import com.mytime.launcher.focus.Focus
import com.mytime.launcher.theme.ThemeWallpaper
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.DecimalFormat

enum class Screen { Home, AllApps, Search, Settings, PickLeisure, HiddenApps, EditFavorites, Summary, PickSummaryApps, ClockStyles, PickWidget, Appearance, PickIconPack, Report, ThemeGallery }

/** [paused]: a leisure app while focus mode is on. */
data class SearchResult(val app: AppInfo, val hidden: Boolean, val paused: Boolean = false)

/** A launch waiting behind the "pause before opening" screen. */
data class PendingLaunch(val app: AppInfo, val todayMs: Long, val limitReached: Boolean)

data class LauncherState(
    val allApps: List<AppInfo> = emptyList(),
    /** All apps minus hidden, in display order (alphabetical, or by usage inside each letter). */
    val visibleApps: List<AppInfo> = emptyList(),
    val favorites: List<AppInfo> = emptyList(),
    val suggestions: List<AppInfo> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val leisure: Set<String> = emptySet(),
    val notSuggested: Set<String> = emptySet(),
    val settings: Settings = Settings(),
    val usage: UsageSnapshot = UsageSnapshot(),
    /** Focus mode is on: leisure apps are left out of favorites, suggestions and the app list. */
    val focusActive: Boolean = false,
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    init { Graph.init(app) }

    private val prefs = Graph.prefs
    private val repo = Graph.apps
    private val usageRepo = Graph.usage
    private val launcherApps = app.getSystemService(LauncherApps::class.java)

    val screen = MutableStateFlow(Screen.Home)
    val query = MutableStateFlow("")
    val pendingLaunch = MutableStateFlow<PendingLaunch?>(null)
    /** Letter picked on the home alphabet; the all-apps list scrolls to it. */
    val pendingSection = MutableStateFlow<Char?>(null)
    val hasUsageAccess = MutableStateFlow(usageRepo.hasAccess())
    private val usage = MutableStateFlow(UsageSnapshot())

    /** Live notifications per package, from the notification listener. */
    val notifications: StateFlow<Map<String, List<NotifItem>>> = NotifListener.byPackage
    val hasNotificationAccess = MutableStateFlow(NotifListener.isEnabled(app))
    val summaryApps: StateFlow<Set<String>> = prefs.summaryApps
    /** Notifications held for the summary, loaded when the summary screen opens. */
    val summaryItems = MutableStateFlow<List<NotifItem>>(emptyList())
    val weather: StateFlow<WeatherState> = Graph.weather.state
    val widgets: StateFlow<List<HomeWidget>> = prefs.widgets

    val calendarEvents: StateFlow<List<CalendarEvent>> = Graph.calendar.events
    val media: StateFlow<MediaState?> = MediaRepo.state

    fun refreshCalendar() = Graph.calendar.refresh()

    fun refreshWeather(force: Boolean = false) {
        viewModelScope.launch { Graph.weather.refreshIfStale(force) }
    }

    /** Notifications held for the next delivery. */
    val summaryWaiting = MutableStateFlow<List<NotifItem>>(emptyList())

    private val lists = combine(prefs.favorites, prefs.hidden, prefs.leisure, prefs.notSuggested) { f, h, l, n ->
        Lists(f, h, l, n)
    }

    /** Ticks every 30 s so a timed focus session ends on its own. */
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }
    val focusUntil: StateFlow<Long> = prefs.focusUntil
    private val focusActive = combine(prefs.focusUntil, clock) { until, now -> now < until }.distinctUntilChanged()

    val state: StateFlow<LauncherState> = combine(repo.apps, lists, prefs.settings, usage, focusActive) { apps, l, settings, u, focus ->
        build(apps, l, settings, u, focus)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherState())

    /** A leisure app tapped (from search) while focusing; the UI asks before opening it. */
    val focusBlocked = MutableStateFlow<AppInfo?>(null)

    fun startFocus(minutes: Int?) = Focus.start(getApplication(), minutes)
    fun extendFocus(minutes: Int) = Focus.extend(getApplication(), minutes)
    fun endFocus() = Focus.end(getApplication())

    val searchResults: StateFlow<List<SearchResult>> = combine(state, query) { s, q -> search(s, q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val calculatorResult: StateFlow<String?> = query.map { calculate(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private data class Lists(val favorites: List<String>, val hidden: Set<String>, val leisure: Set<String>, val notSuggested: Set<String>)

    private fun build(apps: List<AppInfo>, l: Lists, settings: Settings, u: UsageSnapshot, focus: Boolean): LauncherState {
        val byKey = apps.associateBy { it.key }
        val visible = apps.filter { it.key !in l.hidden && !(focus && it.packageName in l.leisure) }.let { list ->
            if (!settings.smartSort || u.scores.isEmpty()) list
            else list.sortedWith(
                compareBy<AppInfo>({ AppRepository.sectionSortKey(it.section) })
                    .thenByDescending { u.scores[it.packageName] ?: 0.0 }
                    .thenBy { it.searchLabel },
            )
        }
        val favorites = l.favorites.mapNotNull { byKey[it] }.filter { !(focus && it.packageName in l.leisure) }
        val suggestions = if (!settings.suggestOftenUsed || u.scores.isEmpty()) emptyList() else {
            val max = u.scores.values.maxOrNull() ?: 0.0
            visible.asSequence()
                .filter { it.key !in l.favorites && it.key !in l.notSuggested && it.packageName !in l.leisure }
                .filter { (u.scores[it.packageName] ?: 0.0) > max * 0.07 }
                .sortedByDescending { u.scores[it.packageName] ?: 0.0 }
                .take(MAX_SUGGESTIONS)
                .toList()
        }
        return LauncherState(apps, visible, favorites, suggestions, l.hidden, l.leisure, l.notSuggested, settings, u, focus)
    }

    /** Ranks: label prefix, then word start, then substring, then letters in order (fuzzy). */
    private fun search(s: LauncherState, raw: String): List<SearchResult> {
        val q = repo.normalize(raw.trim())
        if (q.isEmpty()) return emptyList()
        return s.allApps.mapNotNull { app ->
            val label = app.searchLabel
            val rank = when {
                label.startsWith(q) -> 0
                label.split(' ', '-', '.').any { it.startsWith(q) } -> 1
                label.contains(q) -> 2
                isSubsequence(q, label) -> 3
                else -> return@mapNotNull null
            }
            Triple(app, rank, s.usage.scores[app.packageName] ?: 0.0)
        }
            .sortedWith(compareBy<Triple<AppInfo, Int, Double>>({ it.second }).thenByDescending { it.third })
            .take(12)
            .map { SearchResult(it.first, it.first.key in s.hidden, s.focusActive && it.first.packageName in s.leisure) }
    }

    private fun isSubsequence(q: String, text: String): Boolean {
        var i = 0
        for (c in text) if (i < q.length && c == q[i]) i++
        return i == q.length
    }

    private fun calculate(raw: String): String? {
        val q = raw.trim().replace('×', '*').replace('÷', '/')
        if (q.length < 3 || q.none { it.isDigit() } || q.none { it in "+-*/^%(" }) return null
        return runCatching {
            val value = Expression(q).evaluate()
            if (value.isBooleanValue) value.booleanValue.toString() else format(value.numberValue)
        }.getOrNull()
    }

    private fun format(n: BigDecimal): String {
        val abs = n.abs()
        return if (abs.signum() == 0 || (abs >= BigDecimal("1e-7") && abs <= BigDecimal("1e10"))) {
            DecimalFormat("#,##0.##########").format(n)
        } else {
            DecimalFormat("0.######E0").format(n)
        }
    }

    fun refreshUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            val access = usageRepo.hasAccess()
            hasUsageAccess.value = access
            usage.value = if (access) usageRepo.snapshot() else UsageSnapshot()
        }
    }

    /** Entry point for every tap on an app. Leisure apps may go through the pause screen. */
    fun requestLaunch(app: AppInfo) {
        val s = state.value
        if (s.focusActive && app.packageName in s.leisure) {
            focusBlocked.value = app
            return
        }
        if (app.packageName in s.leisure && s.settings.pauseBeforeOpen) {
            viewModelScope.launch(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val today = if (usageRepo.hasAccess())
                    usageRepo.timeIn(setOf(app.packageName), UsageRepo.startOfDay(now), now) else 0L
                val limit = s.settings.leisureDailyLimitMin
                val leisureToday = if (limit > 0 && usageRepo.hasAccess())
                    usageRepo.timeIn(s.leisure, UsageRepo.startOfDay(now), now) else 0L
                pendingLaunch.value = PendingLaunch(app, today, limit > 0 && leisureToday >= limit * 60_000L)
            }
        } else {
            launchNow(app)
        }
    }

    fun launchNow(app: AppInfo) {
        pendingLaunch.value = null
        val ctx = getApplication<Application>()
        runCatching { launcherApps.startMainActivity(app.component, app.user, null, null) }
        if (app.packageName in state.value.leisure) UsageBreakerService.start(ctx)
        query.value = ""
        screen.value = Screen.Home
    }

    fun toggleFavorite(app: AppInfo) = prefs.toggleFavorite(app.key)
    fun moveFavorite(app: AppInfo, delta: Int) = prefs.moveFavorite(app.key, delta)
    fun toggleHidden(app: AppInfo) = prefs.toggleHidden(app.key)
    fun toggleLeisure(app: AppInfo) = prefs.toggleLeisure(app.packageName)
    fun toggleSuggest(app: AppInfo) = prefs.toggleNotSuggested(app.key)
    fun toggleSummaryApp(app: AppInfo) {
        prefs.toggleSummaryApp(app.packageName)
        NotifListener.applySummaryRules()
    }

    val savedThemes: StateFlow<List<SavedTheme>> = prefs.themes
    /** Whether the wallpaper asks for dark text (light wallpapers), refreshed on resume. */
    val wallpaperPrefersDarkText = MutableStateFlow(wallpaperDarkText())

    private fun wallpaperDarkText(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 27) return false
        val colors = runCatching {
            android.app.WallpaperManager.getInstance(getApplication())
                .getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
        }.getOrNull() ?: return false
        return colors.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
    }

    fun saveCurrentTheme() {
        val list = prefs.themes.value
        var n = list.size + 1
        while (list.any { it.name == "My theme $n" }) n++
        prefs.setThemes(list + SavedTheme.from("My theme $n", prefs.settings.value))
    }

    /** Applies a look; with [withWallpaper], also draws and sets its gradient wallpaper. */
    fun applyTheme(t: SavedTheme, withWallpaper: Boolean) {
        val setWallpaper = withWallpaper && t.wallpaper.isNotEmpty()
        // Our own wallpaper's colours are known, so pick the text colour directly instead of
        // waiting for Android's wallpaper hint (late, and confused by the dark hills).
        val text = if (!setWallpaper) t.wallpaperText else {
            val top = runCatching { android.graphics.Color.parseColor(t.wallpaper.first()) }.getOrDefault(android.graphics.Color.BLACK)
            if (android.graphics.Color.luminance(top) > 0.5f) "dark" else "light"
        }
        updateSettings { t.applyTo(it).copy(wallpaperText = text) }
        if (setWallpaper) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { ThemeWallpaper.apply(getApplication(), t.wallpaper) }
                wallpaperPrefersDarkText.value = wallpaperDarkText()
            }
        }
    }

    fun deleteTheme(t: SavedTheme) = prefs.setThemes(prefs.themes.value.filter { it.name != t.name })

    fun updateSettings(transform: (Settings) -> Settings) {
        val before = prefs.settings.value
        prefs.update(transform)
        val after = prefs.settings.value
        if (IconLoader.signature(before) != IconLoader.signature(after)) repo.clearIcons()
        if (before.notificationSummary != after.notificationSummary) {
            if (after.notificationSummary) prefs.lastSummaryTime = System.currentTimeMillis()
            SummaryScheduler.schedule(getApplication())
        }
        if (after.notificationSummary) NotifListener.applySummaryRules()
    }

    fun refreshPermissions() {
        hasNotificationAccess.value = NotifListener.isEnabled(getApplication())
        wallpaperPrefersDarkText.value = wallpaperDarkText()
    }

    fun openSummary() {
        reloadSummary()
        SummaryScheduler.cancelReminder(getApplication())
        screen.value = Screen.Summary
    }

    fun reloadSummary() {
        summaryItems.value = NotifListener.deliveredBatch()
        summaryWaiting.value = NotifListener.waitingForNext()
    }

    /** "Done": the batch stays in the notification shade; the summary screen forgets it. */
    fun closeSummary() {
        Graph.prefs.batchKeys = emptySet()
        summaryItems.value = emptyList()
        goHome()
    }

    /** Settings tab: 0 = Themes, 1 = Features. */
    val settingsTab = MutableStateFlow(1)

    /**
     * Scroll positions of the settings screens, kept here so going into a sub-screen and back
     * returns to the same place instead of the top. Cleared when settings is opened afresh.
     */
    val listStates = HashMap<String, androidx.compose.foundation.lazy.LazyListState>()

    fun openSettings() {
        listStates.clear()
        screen.value = Screen.Settings
    }

    fun goHome() {
        query.value = ""
        screen.value = Screen.Home
    }

    private companion object {
        const val MAX_SUGGESTIONS = 4
    }
}
