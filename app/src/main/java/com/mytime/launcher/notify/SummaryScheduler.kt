package com.mytime.launcher.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mytime.launcher.R
import com.mytime.launcher.data.Graph
import com.mytime.launcher.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Times the Notification Summary. Delivery is the last summary + 6 h (7 h when the
 * minute is 30 or later), rounded down to the hour, as in Niagara.
 */
object SummaryScheduler {

    private const val CHANNEL_ID = "batch_reminder"
    private const val NOTIFICATION_ID = 2
    const val EXTRA_OPEN_SUMMARY = "open_summary"

    fun nextDelivery(last: Long): Long {
        val debugMin = Graph.prefs.debugSummaryMinutes
        if (debugMin > 0) return last + debugMin * 60_000L
        return regularDelivery(last)
    }

    private fun regularDelivery(last: Long): Long = Calendar.getInstance().apply {
        timeInMillis = last
        add(Calendar.HOUR_OF_DAY, if (get(Calendar.MINUTE) >= 30) 7 else 6)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun nextDeliveryText(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nextDelivery(Graph.prefs.lastSummaryTime)))

    fun schedule(context: Context) {
        Graph.init(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(context)
        if (!Graph.prefs.settings.value.notificationSummary) {
            alarms.cancel(pi)
            return
        }
        if (Graph.prefs.lastSummaryTime == 0L) Graph.prefs.lastSummaryTime = System.currentTimeMillis()
        // A 5-minute window needs no exact-alarm permission. The held notifications come back
        // on their own at the delivery time; this alarm only posts "Here's your summary".
        alarms.setWindow(AlarmManager.RTC_WAKEUP, nextDelivery(Graph.prefs.lastSummaryTime), 5 * 60_000L, pi)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, SummaryAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Delivery time: the held notifications' snoozes end now, so they are back in the shade.
     * They become the batch shown on the summary screen, and the next period starts.
     */
    fun deliver(context: Context) {
        Graph.init(context)
        val prefs = Graph.prefs
        val batch = prefs.summaryKeys
        prefs.batchKeys = batch
        prefs.summaryKeys = emptySet()
        prefs.lastSummaryTime = System.currentTimeMillis()
        if (batch.isNotEmpty()) postReminder(context, batch.size)
        schedule(context)
    }

    private fun postReminder(context: Context, count: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Notification summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when your notification summary is ready"
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .putExtra(EXTRA_OPEN_SUMMARY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_summary)
            .setContentTitle("Here's your summary")
            .setContentText(if (count == 1) "1 notification arrived together" else "$count notifications arrived together")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    fun cancelReminder(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}

class SummaryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = SummaryScheduler.deliver(context)
}
