package com.mytime.launcher.notify

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mytime.launcher.data.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/**
 * Feeds notifications to the home screen and runs the Notification Summary.
 *
 * Summary: an eligible notification is snoozed until the next delivery time, so Android
 * brings the whole batch back together then. A listener cannot end a snooze early, which is
 * why the delivery time is fixed when the notification is held. Calls, alarms, transport,
 * system and stopwatch notifications, ongoing ones, group summaries and system packages are
 * never held, and a notification is held at most once.
 */
class NotifListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Graph.init(this)
        instance = WeakReference(this)
        _connected.value = true
        holdEligible()
        refresh()
        SummaryScheduler.schedule(this)
        MediaRepo.start(this, ComponentName(this, NotifListener::class.java))
    }

    override fun onListenerDisconnected() {
        instance = null
        _connected.value = false
        MediaRepo.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (shouldHold(sbn)) hold(sbn) else refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?) = refresh()

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        _byPackage.value = active
            .filter { it.packageName != packageName && NotifParser.isShowable(it) }
            .map(NotifParser::parse)
            // Conversations first (Niagara's top tier), then newest.
            .sortedWith(compareByDescending<NotifItem> { it.isConversation }.thenByDescending { it.postTime })
            .groupBy { it.packageName }
    }

    /** Applies the summary rules to notifications that were already showing. */
    fun holdEligible() {
        runCatching { activeNotifications }.getOrNull().orEmpty().filter(::shouldHold).forEach(::hold)
    }

    private fun shouldHold(sbn: StatusBarNotification): Boolean {
        val s = Graph.prefs.settings.value
        if (!s.notificationSummary) return false
        // Already held once: this is the batch coming back at delivery time.
        if (sbn.key in Graph.prefs.summaryKeys || sbn.key in Graph.prefs.batchKeys) return false
        val n = sbn.notification
        val pkg = sbn.packageName
        if (pkg == packageName || pkg == "android" || pkg.startsWith("com.android")) return false
        if (!sbn.isClearable || n.flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        // Snoozing a group summary snoozes its whole group, including conversations
        // that should come through, so each child is judged on its own instead.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (n.category in NEVER_HOLD_CATEGORIES) return false

        val ranking = Ranking()
        val ranked = runCatching { currentRanking.getRanking(sbn.key, ranking) }.getOrDefault(false)
        val conversation = NotifParser.isConversation(n) || (Build.VERSION.SDK_INT >= 30 && ranked && ranking.isConversation)
        if (conversation && s.summaryExcludeConversations) return false

        if (pkg in Graph.prefs.summaryApps.value) return true
        val silent = ranked && ranking.importance <= NotificationManager.IMPORTANCE_LOW
        return s.summarizeSilent && silent && !conversation
    }

    /** Snoozes until the next delivery, when Android posts it again with the rest of the batch. */
    private fun hold(sbn: StatusBarNotification) {
        val until = SummaryScheduler.nextDelivery(Graph.prefs.lastSummaryTime)
        val duration = (until - System.currentTimeMillis()).coerceAtLeast(MIN_HOLD_MS)
        runCatching { snoozeNotification(sbn.key, duration) }.onSuccess {
            Graph.prefs.summaryKeys = Graph.prefs.summaryKeys + sbn.key
        }
    }

    private fun waiting(): List<NotifItem> {
        val keys = Graph.prefs.summaryKeys
        return runCatching { snoozedNotifications }.getOrNull().orEmpty()
            .filter { it.key in keys && NotifParser.isShowable(it) }
            .map(NotifParser::parse)
            .sortedByDescending { it.postTime }
    }

    private fun delivered(): List<NotifItem> {
        val keys = Graph.prefs.batchKeys
        return runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.key in keys && NotifParser.isShowable(it) }
            .map(NotifParser::parse)
            .sortedByDescending { it.postTime }
    }

    private fun countWaiting(): Int {
        val keys = Graph.prefs.summaryKeys
        return runCatching { snoozedNotifications }.getOrNull().orEmpty().count { it.key in keys }
    }

    companion object {
        private const val MIN_HOLD_MS = 60_000L
        private val NEVER_HOLD_CATEGORIES = setOf(
            Notification.CATEGORY_ALARM, Notification.CATEGORY_CALL, Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_SYSTEM, Notification.CATEGORY_STOPWATCH,
            Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_REMINDER,
        )

        private var instance: WeakReference<NotifListener>? = null

        private val _byPackage = MutableStateFlow<Map<String, List<NotifItem>>>(emptyMap())
        /** Current notifications per package, newest conversation first. */
        val byPackage: StateFlow<Map<String, List<NotifItem>>> = _byPackage

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, NotifListener::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }

        fun dismiss(key: String) {
            instance?.get()?.let { runCatching { it.cancelNotification(key) } }
        }

        fun applySummaryRules() = instance?.get()?.holdEligible()

        /** The batch that arrived with the last summary (still in the shade). */
        fun deliveredBatch(): List<NotifItem> = instance?.get()?.delivered().orEmpty()

        /** Notifications held for the next summary. */
        fun waitingForNext(): List<NotifItem> = instance?.get()?.waiting().orEmpty()

        fun waitingCount(): Int = instance?.get()?.countWaiting() ?: 0

        fun requestRebind(context: Context) {
            runCatching { requestRebind(ComponentName(context, NotifListener::class.java)) }
        }
    }
}
