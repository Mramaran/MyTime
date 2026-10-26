package com.mytime.launcher.notify

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification

/** A notification action shown as a chip; [remoteInput] is set for a reply action. */
data class NotifAction(
    val label: String,
    val intent: PendingIntent,
    val remoteInput: RemoteInput?,
    val choices: List<String>,
)

/** What the launcher needs from a notification. Content is held in memory only. */
data class NotifItem(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val isConversation: Boolean,
    val autoCancel: Boolean,
    val clearable: Boolean,
    val contentIntent: PendingIntent?,
    val actions: List<NotifAction>,
) {
    val reply: NotifAction? get() = actions.firstOrNull { it.remoteInput != null }
    val plainActions: List<NotifAction> get() = actions.filter { it.remoteInput == null }.take(3)
}

object NotifParser {

    fun parse(sbn: StatusBarNotification): NotifItem {
        val n = sbn.notification
        val extras = n.extras
        val messages = messagingLines(extras)
        val title = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        val text = messages.lastOrNull()
            ?: (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val actions = n.actions.orEmpty().mapNotNull { a ->
            val intent = a.actionIntent ?: return@mapNotNull null
            val input = a.remoteInputs?.firstOrNull { it.allowFreeFormInput }
            NotifAction(
                label = a.title?.toString().orEmpty(),
                intent = intent,
                remoteInput = input,
                choices = input?.choices?.map { it.toString() }?.take(3).orEmpty(),
            )
        }
        return NotifItem(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title,
            text = text,
            postTime = sbn.postTime,
            isConversation = isConversation(n),
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            clearable = sbn.isClearable,
            contentIntent = n.contentIntent ?: n.fullScreenIntent,
            actions = actions,
        )
    }

    fun isConversation(n: Notification): Boolean =
        n.category == Notification.CATEGORY_MESSAGE ||
            n.extras.getString(Notification.EXTRA_TEMPLATE) == Notification.MessagingStyle::class.java.name

    /** Notifications that should not appear on app rows (media, ongoing, foreground services, group summaries). */
    fun isShowable(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY) != 0) return false
        if (n.extras.getString(Notification.EXTRA_TEMPLATE) == Notification.MediaStyle::class.java.name) return false
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)
        return !title.isNullOrBlank() || !text.isNullOrBlank()
    }

    private fun messagingLines(extras: Bundle): List<String> {
        @Suppress("DEPRECATION")
        val raw = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        return raw.mapNotNull { (it as? Bundle) }.mapNotNull { b ->
            val sender = b.getCharSequence("sender")?.toString()
            val text = b.getCharSequence("text")?.toString() ?: return@mapNotNull null
            if (sender.isNullOrBlank()) text else "$sender: $text"
        }
    }
}

/** Sending intents from the home screen. The launcher is in the foreground, so it may start activities. */
object NotifActions {

    private fun options(): Bundle? = if (Build.VERSION.SDK_INT >= 34) {
        ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
    } else null

    private fun send(context: Context, pi: PendingIntent, fill: Intent? = null) {
        runCatching { pi.send(context, 0, fill, null, null, null, options()) }
    }

    fun open(context: Context, item: NotifItem) {
        item.contentIntent?.let { send(context, it) }
        if (item.autoCancel) NotifListener.dismiss(item.key)
    }

    fun runAction(context: Context, action: NotifAction) = send(context, action.intent)

    fun reply(context: Context, action: NotifAction, text: String) {
        val input = action.remoteInput ?: return
        val fill = Intent()
        RemoteInput.addResultsToIntent(arrayOf(input), fill, Bundle().apply { putCharSequence(input.resultKey, text) })
        send(context, action.intent, fill)
    }
}
