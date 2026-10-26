package com.mytime.launcher.data

import android.content.Context
import com.mytime.launcher.calendar.CalendarRepo
import com.mytime.launcher.usage.UsageRepo
import com.mytime.launcher.weather.WeatherRepo

/** Process-wide singletons, created on first use from the application context. */
object Graph {
    @Volatile private var initialized = false
    lateinit var apps: AppRepository; private set
    lateinit var prefs: Prefs; private set
    lateinit var usage: UsageRepo; private set
    lateinit var weather: WeatherRepo; private set
    lateinit var calendar: CalendarRepo; private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            prefs = Prefs(app)
            usage = UsageRepo(app)
            apps = AppRepository(app)
            weather = WeatherRepo(app)
            calendar = CalendarRepo(app)
            initialized = true
        }
    }
}
