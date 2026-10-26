package com.mytime.launcher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User settings. Every feature is free: there is no Pro gating anywhere. */
data class Settings(
    val smartSort: Boolean = true,
    val suggestOftenUsed: Boolean = true,
    val usageBreaker: Boolean = false,
    val pauseBeforeOpen: Boolean = true,
    val greyscaleLeisure: Boolean = true,
    val swipeDownNotifications: Boolean = true,
    val doubleTapLock: Boolean = false,
    val showScreenTime: Boolean = true,
    val hideFavoriteLabels: Boolean = false,
    /** Daily budget for all leisure apps together, in minutes. 0 = no limit. */
    val leisureDailyLimitMin: Int = 0,
    /** Show notifications on app rows and let you reply from the home screen. */
    val inlineNotifications: Boolean = true,
    /** Hold non-urgent notifications and deliver them together about every 6 hours. */
    val notificationSummary: Boolean = false,
    val summaryExcludeConversations: Boolean = true,
    /** Also batch low-importance (silent) notifications from any app. */
    val summarizeSilent: Boolean = false,
    /** Name of a [com.mytime.launcher.ui.ClockStyle]. */
    val clockStyle: String = "Basic",
    val weatherEnabled: Boolean = false,
    val fahrenheit: Boolean = false,
    /** Empty = use the phone's location; otherwise a place name looked up once. */
    val weatherPlace: String = "",
    /** "auto" (Material You / wallpaper) or a colour as "#RRGGBB". */
    val accent: String = "#F4D35E",
    /** "system", "dark" or "light": the colour scheme of menus and settings. */
    val themeMode: String = "system",
    val pitchBlack: Boolean = false,
    /** Text drawn on the wallpaper: "auto" (from the wallpaper), "light" or "dark". */
    val wallpaperText: String = "auto",
    /** Name of a [com.mytime.launcher.ui.FontChoice]. */
    val font: String = "Default",
    val fontScale: Float = 1f,
    /** Icon pack package name, or "" for the phone's own icons. */
    val iconPack: String = "",
    /** Name of a [com.mytime.launcher.icons.IconShape]. */
    val iconShape: String = "System",
    val iconSizePct: Int = 100,
    /** Put old non-adaptive icons on a white shape so every icon matches. */
    val reshapeLegacy: Boolean = true,
    /** Play/pause/skip for whatever is playing, on the home screen. Needs notification access. */
    val showMediaPlayer: Boolean = true,
    /** Upcoming events in the header and an agenda when you tap the date. Needs calendar access. */
    val calendarEvents: Boolean = false,
)

/** A saved look: everything on the Look & feel screen, under a name. */
data class SavedTheme(
    val name: String,
    val accent: String,
    val themeMode: String,
    val pitchBlack: Boolean,
    val wallpaperText: String,
    val font: String,
    val fontScale: Float,
    val iconPack: String,
    val iconShape: String,
    val iconSizePct: Int,
    val clockStyle: String,
    /** Gradient colours ("#RRGGBB", top to bottom) for a generated wallpaper; empty = no wallpaper. */
    val wallpaper: List<String> = emptyList(),
) {
    fun applyTo(s: Settings) = s.copy(
        accent = accent, themeMode = themeMode, pitchBlack = pitchBlack, wallpaperText = wallpaperText,
        font = font, fontScale = fontScale, iconPack = iconPack, iconShape = iconShape,
        iconSizePct = iconSizePct, clockStyle = clockStyle,
    )

    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("name", name).put("accent", accent).put("themeMode", themeMode).put("pitchBlack", pitchBlack)
        .put("wallpaperText", wallpaperText).put("font", font).put("fontScale", fontScale.toDouble())
        .put("iconPack", iconPack).put("iconShape", iconShape).put("iconSizePct", iconSizePct)
        .put("clockStyle", clockStyle)
        .put("wallpaper", org.json.JSONArray(wallpaper))

    companion object {
        fun from(name: String, s: Settings) = SavedTheme(
            name, s.accent, s.themeMode, s.pitchBlack, s.wallpaperText, s.font, s.fontScale,
            s.iconPack, s.iconShape, s.iconSizePct, s.clockStyle,
        )

        fun fromJson(o: org.json.JSONObject) = SavedTheme(
            name = o.getString("name"), accent = o.optString("accent", "#F4D35E"),
            themeMode = o.optString("themeMode", "system"), pitchBlack = o.optBoolean("pitchBlack"),
            wallpaperText = o.optString("wallpaperText", "auto"), font = o.optString("font", "Default"),
            fontScale = o.optDouble("fontScale", 1.0).toFloat(), iconPack = o.optString("iconPack"),
            iconShape = o.optString("iconShape", "System"), iconSizePct = o.optInt("iconSizePct", 100),
            clockStyle = o.optString("clockStyle", "Basic"),
            wallpaper = o.optJSONArray("wallpaper")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
        )
    }
}

/** A widget on the home screen: its AppWidget id and the height the user picked. */
data class HomeWidget(val id: Int, val heightDp: Int)

/**
 * SharedPreferences-backed store. Lists of apps are stored by [AppInfo.key].
 * Favorites keep their order, so they are stored as one newline-joined string.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences = context.getSharedPreferences("mytime_prefs", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(readList(KEY_FAVORITES))
    val favorites: StateFlow<List<String>> = _favorites

    private val _hidden = MutableStateFlow(sp.getStringSet(KEY_HIDDEN, emptySet())!!.toSet())
    val hidden: StateFlow<Set<String>> = _hidden

    /** Leisure apps are tracked by package name: usage stats report packages, not activities. */
    private val _leisure = MutableStateFlow(sp.getStringSet(KEY_LEISURE, emptySet())!!.toSet())
    val leisure: StateFlow<Set<String>> = _leisure

    private val _notSuggested = MutableStateFlow(sp.getStringSet(KEY_NOT_SUGGESTED, emptySet())!!.toSet())
    val notSuggested: StateFlow<Set<String>> = _notSuggested

    /** Packages whose notifications go into the summary. */
    private val _summaryApps = MutableStateFlow(sp.getStringSet(KEY_SUMMARY_APPS, emptySet())!!.toSet())
    val summaryApps: StateFlow<Set<String>> = _summaryApps

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings

    /** When the current summary period started (epoch ms). The next one is due 6–7 h later. */
    var lastSummaryTime: Long
        get() = sp.getLong(KEY_LAST_SUMMARY, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_SUMMARY, value).apply()

    /** Keys of notifications MyTime snoozed into the summary, so others' snoozes are left alone. */
    var summaryKeys: Set<String>
        get() = sp.getStringSet(KEY_SUMMARY_KEYS, emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet(KEY_SUMMARY_KEYS, value).apply()

    /**
     * Testing aid, honoured only in debuggable builds: a summary period of this many minutes
     * instead of 6 hours. Set it by editing the prefs file with run-as; there is no UI.
     */
    val debugSummaryMinutes: Int =
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
            sp.getInt("debug_summary_minutes", 0) else 0

    /** Keys delivered by the most recent summary, shown on the summary screen until it is closed. */
    var batchKeys: Set<String>
        get() = sp.getStringSet(KEY_BATCH_KEYS, emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet(KEY_BATCH_KEYS, value).apply()

    /** Home widgets in display order, stored as "id:heightDp" lines. */
    private val _widgets = MutableStateFlow(readWidgets())
    val widgets: StateFlow<List<HomeWidget>> = _widgets

    fun setWidgets(list: List<HomeWidget>) {
        sp.edit().putString(KEY_WIDGETS, list.joinToString("\n") { "${it.id}:${it.heightDp}" }).apply()
        _widgets.value = list
    }

    private fun readWidgets(): List<HomeWidget> = readList(KEY_WIDGETS).mapNotNull { line ->
        val (id, h) = line.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        HomeWidget(id.toIntOrNull() ?: return@mapNotNull null, h.toIntOrNull() ?: 180)
    }

    /** Last weather response from Open-Meteo, cached as raw JSON with its fetch time and place. */
    var weatherJson: String
        get() = sp.getString(KEY_WEATHER_JSON, "")!!
        set(value) = sp.edit().putString(KEY_WEATHER_JSON, value).apply()
    var weatherFetchedAt: Long
        get() = sp.getLong(KEY_WEATHER_TIME, 0L)
        set(value) = sp.edit().putLong(KEY_WEATHER_TIME, value).apply()
    /** "lat,lon" the cached weather is for. */
    var weatherCoords: String
        get() = sp.getString(KEY_WEATHER_COORDS, "")!!
        set(value) = sp.edit().putString(KEY_WEATHER_COORDS, value).apply()

    /** Looks the user saved, stored as a JSON array. */
    private val _themes = MutableStateFlow(readThemes())
    val themes: StateFlow<List<SavedTheme>> = _themes

    fun setThemes(list: List<SavedTheme>) {
        val arr = org.json.JSONArray().apply { list.forEach { put(it.toJson()) } }
        sp.edit().putString(KEY_THEMES, arr.toString()).apply()
        _themes.value = list
    }

    private fun readThemes(): List<SavedTheme> = runCatching {
        val arr = org.json.JSONArray(sp.getString(KEY_THEMES, "[]"))
        (0 until arr.length()).map { SavedTheme.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    /** When focus mode ends (epoch ms): 0 = off, Long.MAX_VALUE = until turned off. */
    private val _focusUntil = MutableStateFlow(sp.getLong(KEY_FOCUS_UNTIL, 0L))
    val focusUntil: StateFlow<Long> = _focusUntil

    fun setFocusUntil(until: Long) {
        sp.edit().putLong(KEY_FOCUS_UNTIL, until).apply()
        _focusUntil.value = until
    }

    /** Calendar ids the user turned off; their events are not shown. */
    private val _hiddenCalendars = MutableStateFlow(sp.getStringSet(KEY_HIDDEN_CALENDARS, emptySet())!!.toSet())
    val hiddenCalendars: StateFlow<Set<String>> = _hiddenCalendars
    fun toggleCalendar(id: Long) = toggleIn(_hiddenCalendars, KEY_HIDDEN_CALENDARS, id.toString())

    fun toggleSummaryApp(pkg: String) = toggleIn(_summaryApps, KEY_SUMMARY_APPS, pkg)

    fun setFavorites(keys: List<String>) {
        sp.edit().putString(KEY_FAVORITES, keys.joinToString("\n")).apply()
        _favorites.value = keys
    }

    fun toggleFavorite(key: String) {
        val list = _favorites.value
        setFavorites(if (key in list) list - key else list + key)
    }

    fun moveFavorite(key: String, delta: Int) {
        val list = _favorites.value.toMutableList()
        val i = list.indexOf(key)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        setFavorites(list)
    }

    fun toggleHidden(key: String) = toggleIn(_hidden, KEY_HIDDEN, key)
    fun toggleLeisure(pkg: String) = toggleIn(_leisure, KEY_LEISURE, pkg)
    fun toggleNotSuggested(key: String) = toggleIn(_notSuggested, KEY_NOT_SUGGESTED, key)

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        sp.edit()
            .putBoolean("smart_sort", s.smartSort)
            .putBoolean("suggest_often_used", s.suggestOftenUsed)
            .putBoolean("usage_breaker", s.usageBreaker)
            .putBoolean("pause_before_open", s.pauseBeforeOpen)
            .putBoolean("greyscale_leisure", s.greyscaleLeisure)
            .putBoolean("swipe_down_notifications", s.swipeDownNotifications)
            .putBoolean("double_tap_lock", s.doubleTapLock)
            .putBoolean("show_screen_time", s.showScreenTime)
            .putBoolean("hide_favorite_labels", s.hideFavoriteLabels)
            .putInt("leisure_daily_limit_min", s.leisureDailyLimitMin)
            .putBoolean("inline_notifications", s.inlineNotifications)
            .putBoolean("notification_summary", s.notificationSummary)
            .putBoolean("summary_exclude_conversations", s.summaryExcludeConversations)
            .putBoolean("summarize_silent", s.summarizeSilent)
            .putString("clock_style", s.clockStyle)
            .putBoolean("weather_enabled", s.weatherEnabled)
            .putBoolean("fahrenheit", s.fahrenheit)
            .putString("weather_place", s.weatherPlace)
            .putString("accent", s.accent)
            .putString("theme_mode", s.themeMode)
            .putBoolean("pitch_black", s.pitchBlack)
            .putString("wallpaper_text", s.wallpaperText)
            .putString("font", s.font)
            .putFloat("font_scale", s.fontScale)
            .putString("icon_pack", s.iconPack)
            .putString("icon_shape", s.iconShape)
            .putInt("icon_size_pct", s.iconSizePct)
            .putBoolean("reshape_legacy", s.reshapeLegacy)
            .putBoolean("show_media_player", s.showMediaPlayer)
            .putBoolean("calendar_events", s.calendarEvents)
            .apply()
        _settings.value = s
    }

    private fun readSettings(): Settings {
        val d = Settings()
        return Settings(
            smartSort = sp.getBoolean("smart_sort", d.smartSort),
            suggestOftenUsed = sp.getBoolean("suggest_often_used", d.suggestOftenUsed),
            usageBreaker = sp.getBoolean("usage_breaker", d.usageBreaker),
            pauseBeforeOpen = sp.getBoolean("pause_before_open", d.pauseBeforeOpen),
            greyscaleLeisure = sp.getBoolean("greyscale_leisure", d.greyscaleLeisure),
            swipeDownNotifications = sp.getBoolean("swipe_down_notifications", d.swipeDownNotifications),
            doubleTapLock = sp.getBoolean("double_tap_lock", d.doubleTapLock),
            showScreenTime = sp.getBoolean("show_screen_time", d.showScreenTime),
            hideFavoriteLabels = sp.getBoolean("hide_favorite_labels", d.hideFavoriteLabels),
            leisureDailyLimitMin = sp.getInt("leisure_daily_limit_min", d.leisureDailyLimitMin),
            inlineNotifications = sp.getBoolean("inline_notifications", d.inlineNotifications),
            notificationSummary = sp.getBoolean("notification_summary", d.notificationSummary),
            summaryExcludeConversations = sp.getBoolean("summary_exclude_conversations", d.summaryExcludeConversations),
            summarizeSilent = sp.getBoolean("summarize_silent", d.summarizeSilent),
            clockStyle = sp.getString("clock_style", d.clockStyle)!!,
            weatherEnabled = sp.getBoolean("weather_enabled", d.weatherEnabled),
            fahrenheit = sp.getBoolean("fahrenheit", d.fahrenheit),
            weatherPlace = sp.getString("weather_place", d.weatherPlace)!!,
            accent = sp.getString("accent", d.accent)!!,
            themeMode = sp.getString("theme_mode", d.themeMode)!!,
            pitchBlack = sp.getBoolean("pitch_black", d.pitchBlack),
            wallpaperText = sp.getString("wallpaper_text", d.wallpaperText)!!,
            font = sp.getString("font", d.font)!!,
            fontScale = sp.getFloat("font_scale", d.fontScale),
            iconPack = sp.getString("icon_pack", d.iconPack)!!,
            iconShape = sp.getString("icon_shape", d.iconShape)!!,
            iconSizePct = sp.getInt("icon_size_pct", d.iconSizePct),
            reshapeLegacy = sp.getBoolean("reshape_legacy", d.reshapeLegacy),
            showMediaPlayer = sp.getBoolean("show_media_player", d.showMediaPlayer),
            calendarEvents = sp.getBoolean("calendar_events", d.calendarEvents),
        )
    }

    private fun readList(key: String): List<String> =
        sp.getString(key, "")!!.split("\n").filter { it.isNotBlank() }

    private fun toggleIn(flow: MutableStateFlow<Set<String>>, key: String, value: String) {
        val set = flow.value
        val next = if (value in set) set - value else set + value
        sp.edit().putStringSet(key, next).apply()
        flow.value = next
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_HIDDEN = "hidden"
        const val KEY_LEISURE = "leisure"
        const val KEY_NOT_SUGGESTED = "not_suggested"
        const val KEY_SUMMARY_APPS = "summary_apps"
        const val KEY_LAST_SUMMARY = "last_summary_time"
        const val KEY_SUMMARY_KEYS = "summary_keys"
        const val KEY_BATCH_KEYS = "batch_keys"
        const val KEY_WIDGETS = "widgets"
        const val KEY_THEMES = "themes"
        const val KEY_HIDDEN_CALENDARS = "hidden_calendars"
        const val KEY_FOCUS_UNTIL = "focus_until"
        const val KEY_WEATHER_JSON = "weather_json"
        const val KEY_WEATHER_TIME = "weather_fetched_at"
        const val KEY_WEATHER_COORDS = "weather_coords"
    }
}
