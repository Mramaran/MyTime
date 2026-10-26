package com.mytime.launcher.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.PowerManager
import com.mytime.launcher.data.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class HourForecast(val time: String, val temp: Double, val code: Int, val precipChance: Int, val isDay: Boolean)
data class DayForecast(val date: String, val code: Int, val max: Double, val min: Double, val precipChance: Int)

data class Weather(
    val temp: Double,
    val feelsLike: Double,
    val code: Int,
    val isDay: Boolean,
    val unit: String,
    val hourly: List<HourForecast>,
    val daily: List<DayForecast>,
    /** Share of the next 2 hours' quarter-hours with rain, 0..1. */
    val rainShareSoon: Double,
)

data class WeatherState(
    val weather: Weather? = null,
    val place: String = "",
    val error: String? = null,
    val loading: Boolean = false,
)

/**
 * Weather from Open-Meteo (free, no API key), called directly from the phone.
 * Refresh rules follow Niagara: hourly, or every 15 minutes while rain is likely
 * (10–90% of the coming quarter-hours wet); refetch after moving 5 km or more;
 * nothing in battery saver.
 */
class WeatherRepo(private val context: Context) {

    private val prefs = Graph.prefs
    private val mutex = Mutex()
    private val _state = MutableStateFlow(WeatherState(weather = parseCached(), place = cachedPlace()))
    val state: StateFlow<WeatherState> = _state

    suspend fun refreshIfStale(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        try {
            val s = prefs.settings.value
            if (!s.weatherEnabled) return@withContext
            val power = context.getSystemService(PowerManager::class.java)
            if (!force && power.isPowerSaveMode) return@withContext

            val coords = resolveCoords(s.weatherPlace) ?: run {
                _state.value = _state.value.copy(
                    error = if (s.weatherPlace.isBlank()) "Allow location or set a place in settings" else "Place not found",
                    loading = false,
                )
                return@withContext
            }
            val cached = parseCached()
            val age = System.currentTimeMillis() - prefs.weatherFetchedAt
            val moved = movedKm(prefs.weatherCoords, coords.first, coords.second) >= 5.0
            val unitChanged = cached != null && cached.unit != unitLabel(s.fahrenheit)
            val maxAge = if (cached != null && cached.rainShareSoon in 0.1..0.9) 15 * 60_000L else 60 * 60_000L
            if (!force && cached != null && age < maxAge && !moved && !unitChanged) return@withContext

            _state.value = _state.value.copy(loading = true)
            val json = fetch(coords.first, coords.second, s.fahrenheit)
            prefs.weatherJson = json
            prefs.weatherFetchedAt = System.currentTimeMillis()
            prefs.weatherCoords = "${coords.first},${coords.second}"
            _state.value = WeatherState(weather = parse(json), place = coords.third)
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = "Weather unavailable", loading = false)
        } finally {
            mutex.unlock()
        }
    }

    /** (lat, lon, place name). A typed place is looked up with Open-Meteo's geocoder. */
    private fun resolveCoords(place: String): Triple<Double, Double, String>? {
        if (place.isNotBlank()) {
            val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&name=" + Uri.encode(place)
            val results = JSONObject(get(url)).optJSONArray("results") ?: return null
            val r = results.optJSONObject(0) ?: return null
            return Triple(r.getDouble("latitude"), r.getDouble("longitude"), r.optString("name", place))
        }
        val loc = lastKnownLocation() ?: return null
        return Triple(round2(loc.latitude), round2(loc.longitude), "")
    }

    private fun lastKnownLocation(): Location? {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = context.getSystemService(LocationManager::class.java)
        return lm.getProviders(true).mapNotNull { p ->
            try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null }
        }.maxByOrNull { it.time }
    }

    private fun fetch(lat: Double, lon: Double, fahrenheit: Boolean): String {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon")
            append("&current=temperature_2m,apparent_temperature,weather_code,is_day")
            append("&hourly=temperature_2m,weather_code,precipitation_probability,is_day")
            append("&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
            append("&minutely_15=precipitation&forecast_minutely_15=8")
            append("&forecast_days=7&forecast_hours=24&timezone=auto")
            if (fahrenheit) append("&temperature_unit=fahrenheit")
        }
        return get(url)
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseCached(): Weather? = prefs.weatherJson.takeIf { it.isNotBlank() }?.let { runCatching { parse(it) }.getOrNull() }

    private fun cachedPlace(): String = prefs.settings.value.weatherPlace

    private fun parse(json: String): Weather {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        val unit = o.optJSONObject("current_units")?.optString("temperature_2m") ?: "°C"
        val h = o.getJSONObject("hourly")
        val hTime = h.getJSONArray("time")
        val hourly = (0 until hTime.length()).map { i ->
            HourForecast(
                time = hTime.getString(i),
                temp = h.getJSONArray("temperature_2m").optDouble(i),
                code = h.getJSONArray("weather_code").optInt(i),
                precipChance = h.optJSONArray("precipitation_probability")?.optInt(i) ?: 0,
                isDay = h.optJSONArray("is_day")?.optInt(i, 1) != 0,
            )
        }
        val d = o.getJSONObject("daily")
        val dTime = d.getJSONArray("time")
        val daily = (0 until dTime.length()).map { i ->
            DayForecast(
                date = dTime.getString(i),
                code = d.getJSONArray("weather_code").optInt(i),
                max = d.getJSONArray("temperature_2m_max").optDouble(i),
                min = d.getJSONArray("temperature_2m_min").optDouble(i),
                precipChance = d.optJSONArray("precipitation_probability_max")?.optInt(i) ?: 0,
            )
        }
        val quarter = o.optJSONObject("minutely_15")?.optJSONArray("precipitation")
        val wet = quarter?.let { a -> (0 until a.length()).count { a.optDouble(it) >= 0.1 }.toDouble() / a.length().coerceAtLeast(1) } ?: 0.0
        return Weather(
            temp = cur.getDouble("temperature_2m"),
            feelsLike = cur.optDouble("apparent_temperature"),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            unit = unit,
            hourly = hourly,
            daily = daily,
            rainShareSoon = wet,
        )
    }

    private fun unitLabel(fahrenheit: Boolean) = if (fahrenheit) "°F" else "°C"

    private fun round2(v: Double) = Math.round(v * 100) / 100.0

    private fun movedKm(cached: String, lat: Double, lon: Double): Double {
        val parts = cached.split(',').mapNotNull { it.toDoubleOrNull() }
        if (parts.size != 2) return Double.MAX_VALUE
        val out = FloatArray(1)
        Location.distanceBetween(parts[0], parts[1], lat, lon, out)
        return out[0] / 1000.0
    }

    companion object {
        /** WMO weather code → (emoji, description), as used by Open-Meteo. */
        fun describe(code: Int, isDay: Boolean = true): Pair<String, String> = when (code) {
            0 -> (if (isDay) "☀️" else "🌙") to "Clear"
            1 -> (if (isDay) "🌤️" else "🌙") to "Mainly clear"
            2 -> "⛅" to "Partly cloudy"
            3 -> "☁️" to "Overcast"
            45, 48 -> "🌫️" to "Fog"
            51, 53, 55 -> "🌦️" to "Drizzle"
            56, 57 -> "🌧️" to "Freezing drizzle"
            61, 63, 65 -> "🌧️" to "Rain"
            66, 67 -> "🌧️" to "Freezing rain"
            71, 73, 75, 77 -> "🌨️" to "Snow"
            80, 81, 82 -> "🌦️" to "Rain showers"
            85, 86 -> "🌨️" to "Snow showers"
            95 -> "⛈️" to "Thunderstorm"
            96, 99 -> "⛈️" to "Thunderstorm with hail"
            else -> "🌡️" to "Weather"
        }
    }
}
