package com.mytime.launcher.focus

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mytime.launcher.R
import com.mytime.launcher.data.Graph

/**
 * Focus mode: while it is on, leisure apps disappear from favorites, suggestions and the
 * app list, and opening one from search asks first. It ends by itself after the chosen time.
 */
object Focus {
    const val UNTIL_OFF = Long.MAX_VALUE

    fun isActive(now: Long = System.currentTimeMillis()): Boolean = now < Graph.prefs.focusUntil.value

    /** Starts focus for [minutes], or until turned off when null. */
    fun start(context: Context, minutes: Int?) {
        Graph.prefs.setFocusUntil(if (minutes == null) UNTIL_OFF else System.currentTimeMillis() + minutes * 60_000L)
        refreshTile(context)
    }

    fun extend(context: Context, minutes: Int) {
        val until = Graph.prefs.focusUntil.value
        if (until == UNTIL_OFF) return
        Graph.prefs.setFocusUntil(maxOf(until, System.currentTimeMillis()) + minutes * 60_000L)
        refreshTile(context)
    }

    fun end(context: Context) {
        Graph.prefs.setFocusUntil(0L)
        refreshTile(context)
    }

    fun minutesLeft(now: Long = System.currentTimeMillis()): Long? {
        val until = Graph.prefs.focusUntil.value
        if (until == UNTIL_OFF || until <= now) return null
        return ((until - now + 59_999L) / 60_000L)
    }

    private fun refreshTile(context: Context) {
        runCatching { TileService.requestListeningState(context, ComponentName(context, FocusTileService::class.java)) }
    }
}

/** Quick Settings tile: one tap in the notification shade turns focus on or off. */
class FocusTileService : TileService() {

    override fun onStartListening() {
        Graph.init(this)
        update()
    }

    override fun onClick() {
        Graph.init(this)
        if (Focus.isActive()) Focus.end(this) else Focus.start(this, null)
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val active = Focus.isActive()
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Focus"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_focus)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when {
                !active -> "Off"
                Focus.minutesLeft() != null -> "${Focus.minutesLeft()} min left"
                else -> "On"
            }
        }
        tile.updateTile()
    }
}
