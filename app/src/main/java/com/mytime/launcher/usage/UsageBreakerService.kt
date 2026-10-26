package com.mytime.launcher.usage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.mytime.launcher.data.Graph
import com.mytime.launcher.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Usage Breaker: while you are in a leisure app, an ongoing silent notification shows how
 * long you have spent in leisure apps this phone session, and pops up again at
 * 5, 10, 20, 30, 45, 60, 90, 120, 180 and 240 minutes (Niagara's thresholds).
 * It also warns when the daily leisure budget is used up.
 *
 * The service runs only while there is leisure use to track and stops itself 10 minutes
 * after you leave your leisure apps.
 */
class UsageBreakerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopStarted = false
    private var lastAlertedThreshold = 0
    private var trackedSessionStart = 0L
    private var dailyLimitAlerted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Graph.init(this)
        createChannel(this)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, build(0, null, alert = false, limitReached = false),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (!loopStarted) {
            loopStarted = true
            scope.launch { loop() }
        }
        return START_NOT_STICKY
    }

    private suspend fun loop() {
        val usage = Graph.usage
        val prefs = Graph.prefs
        var lastLeisureSeen = System.currentTimeMillis()
        while (scope.isActive) {
            val settings = prefs.settings.value
            val leisure = prefs.leisure.value
            if (!settings.usageBreaker || leisure.isEmpty() || !usage.hasAccess()) break

            val now = System.currentTimeMillis()
            val foreground = usage.foregroundPackage(now)
            if (foreground != null && foreground in leisure) lastLeisureSeen = now
            if (now - lastLeisureSeen > UsageRepo.SESSION_GAP_MS) break

            val sessionStart = usage.phoneSessionStart(now)
            if (sessionStart != null) {
                if (sessionStart != trackedSessionStart) {
                    trackedSessionStart = sessionStart
                    lastAlertedThreshold = 0
                }
                val minutes = (usage.timeIn(leisure, sessionStart, now) / 60_000).toInt()
                val reached = THRESHOLDS_MIN.lastOrNull { minutes >= it } ?: 0
                val alert = reached > lastAlertedThreshold
                if (alert) lastAlertedThreshold = reached

                val limit = settings.leisureDailyLimitMin
                val todayLeisure = usage.timeIn(leisure, UsageRepo.startOfDay(now), now) / 60_000
                val limitReached = limit > 0 && todayLeisure >= limit
                val limitAlert = limitReached && !dailyLimitAlerted
                if (limitAlert) dailyLimitAlerted = true

                val appLabel = foreground?.takeIf { it in leisure }
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, build(minutes, appLabel, alert || limitAlert, limitReached))
            }
            delay(POLL_MS)
        }
        stopSelf()
    }

    private fun build(minutes: Int, foregroundPkg: String?, alert: Boolean, limitReached: Boolean): Notification {
        val appName = foregroundPkg?.let { pkg ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        }
        val title = when {
            limitReached -> "Daily leisure limit reached"
            minutes == 0 -> "Usage Breaker is on"
            else -> "$minutes min have passed"
        }
        val text = when {
            limitReached -> "You've used up today's time for your leisure apps."
            appName != null -> "This is how long you have been using $appName"
            else -> "This is how long you have been using your leisure apps"
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(minuteIcon(minutes))
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        foregroundPkg?.let { pkg ->
            runCatching { packageManager.getApplicationIcon(pkg).toBitmap(128, 128) }.getOrNull()
                ?.let { builder.setLargeIcon(it) }
        }
        return builder.build()
    }

    /** Status-bar icon showing the minute count, like Niagara's. */
    private fun minuteIcon(minutes: Int): Icon {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val label = if (minutes <= 0) "•" else minutes.toString()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = if (label.length >= 3) 44f else 64f
        }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2
        Canvas(bmp).drawText(label, size / 2f, y, paint)
        return Icon.createWithBitmap(bmp)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "usage-breaker"
        const val NOTIFICATION_ID = 1
        const val POLL_MS = 10_000L
        val THRESHOLDS_MIN = intArrayOf(5, 10, 20, 30, 45, 60, 90, 120, 180, 240)

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Usage Breaker", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders about time spent in your leisure apps"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Starts tracking. Call from the foreground (for example when launching a leisure app). */
        fun start(context: Context) {
            val s = Graph.prefs.settings.value
            if (!s.usageBreaker || Graph.prefs.leisure.value.isEmpty() || !Graph.usage.hasAccess()) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, UsageBreakerService::class.java)) }
        }
    }
}
