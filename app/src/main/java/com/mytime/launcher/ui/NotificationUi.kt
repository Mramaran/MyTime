package com.mytime.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mytime.launcher.data.AppInfo
import com.mytime.launcher.notify.NotifActions
import com.mytime.launcher.notify.NotifItem
import com.mytime.launcher.notify.NotifListener
import com.mytime.launcher.notify.SummaryScheduler

/** Short preview for an app row: the newest notification, "Title: text". */
fun previewOf(items: List<NotifItem>): String? = items.firstOrNull()?.let { n ->
    listOf(n.title, n.text).filter { it.isNotBlank() }.joinToString(": ")
}

/**
 * One notification, expanded: content, up to 3 action chips, quick-reply chips and an inline
 * reply box when the app offers a reply action, plus Open and Dismiss.
 */
@Composable
fun NotificationCard(item: NotifItem, allowDismiss: Boolean = true, onDone: () -> Unit = {}) {
    val context = LocalContext.current
    var reply by remember(item.key) { mutableStateOf("") }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            if (item.title.isNotBlank()) Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))

            val actions = item.plainActions
            if (actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    actions.forEach { a ->
                        AssistChip(onClick = { NotifActions.runAction(context, a); onDone() }, label = { Text(a.label, maxLines = 1) })
                    }
                }
            }

            item.reply?.let { action ->
                if (action.choices.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        action.choices.forEach { c ->
                            AssistChip(onClick = { NotifActions.reply(context, action, c); onDone() }, label = { Text(c, maxLines = 1) })
                        }
                    }
                }
                val send = {
                    if (reply.isNotBlank()) {
                        NotifActions.reply(context, action, reply.trim())
                        reply = ""
                        onDone()
                    }
                }
                OutlinedTextField(
                    value = reply,
                    onValueChange = { reply = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text(action.label.ifBlank { "Reply" }) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    trailingIcon = { IconButton(onClick = send) { Icon(Icons.AutoMirrored.Filled.Send, "Send reply") } },
                )
            }

            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                if (allowDismiss && item.clearable) {
                    TextButton(onClick = { NotifListener.dismiss(item.key); onDone() }) { Text("Dismiss") }
                }
                if (item.contentIntent != null) {
                    TextButton(onClick = { NotifActions.open(context, item); onDone() }) { Text("Open") }
                }
            }
        }
    }
}

/** The summary: the batch that just arrived, grouped by app, and what is waiting for the next one. */
@Composable
fun SummaryScreen(vm: LauncherViewModel, state: LauncherState) {
    val items by vm.summaryItems.collectAsState()
    val waiting by vm.summaryWaiting.collectAsState()
    val labels = remember(state.allApps) { state.allApps.associate { it.packageName to it.label } }
    val grouped = remember(items) { items.groupBy { it.packageName }.toList() }
    EditorScaffold("Your summary", { vm.goHome() }) {
        item {
            Text(
                if (items.isEmpty()) "No summary has arrived yet. Non-urgent notifications from your chosen apps are held and arrive together around ${SummaryScheduler.nextDeliveryText()}."
                else if (items.size == 1) "1 notification arrived in this summary."
                else "${items.size} notifications arrived together in this summary.",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        grouped.forEach { (pkg, list) ->
            item(key = "h_$pkg") { AppHeader(labels[pkg] ?: pkg) }
            items(list, key = { it.key }) { n ->
                Column(Modifier.padding(horizontal = 16.dp)) { NotificationCard(n, onDone = { vm.reloadSummary() }) }
            }
        }
        if (items.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = { vm.closeSummary() }) { Text("Done") }
                Spacer(Modifier.height(8.dp))
                Text("Anything you didn't open stays in your notification shade.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (waiting.isNotEmpty()) {
            item(key = "waiting") {
                Text("Waiting for ${SummaryScheduler.nextDeliveryText()} (${waiting.size})", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 4.dp))
            }
            items(waiting, key = { "w_" + it.key }) { n ->
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(labels[n.packageName] ?: n.packageName, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                    NotificationCard(n, allowDismiss = false)
                }
            }
        }
    }
}

@Composable
private fun AppHeader(label: String) {
    Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp))
}

@Composable
fun PickSummaryAppsScreen(vm: LauncherViewModel, state: LauncherState) {
    val chosen by vm.summaryApps.collectAsState()
    val apps = remember(state.allApps) { state.allApps.distinctBy { it.packageName } }
    EditorScaffold("Apps in your summary", { vm.screen.value = Screen.Settings }) {
        item {
            Text("Notifications from these apps wait for your summary instead of interrupting you. Calls, alarms and other urgent notifications always come through.",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
        items(apps, key = { it.key }) { app: AppInfo ->
            Row(Modifier.fillMaxWidth().clickable { vm.toggleSummaryApp(app) }.padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 32, false)
                Spacer(Modifier.width(16.dp))
                Text(app.label, Modifier.weight(1f))
                Checkbox(checked = app.packageName in chosen, onCheckedChange = { vm.toggleSummaryApp(app) })
            }
        }
    }
}

/** Settings rows for notifications, added to the settings list. */
fun LazyListScope.notificationSettings(vm: LauncherViewModel, state: LauncherState, actions: SystemActions, access: Boolean, summaryAppCount: Int) {
    val s = state.settings
    item { Header("Notifications") }
    item {
        ActionRow("Notification access", if (access) "Allowed" else "Needed for notifications on the home screen and the summary") {
            actions.openNotificationAccess()
        }
    }
    item {
        ToggleRow("Notifications on the home screen", "See and reply to notifications on your app rows", s.inlineNotifications) { v ->
            vm.updateSettings { it.copy(inlineNotifications = v) }
        }
    }
    item {
        ToggleRow("Notification Summary", "Bundle non-urgent notifications and get them about every 6 hours", s.notificationSummary) { v ->
            vm.updateSettings { it.copy(notificationSummary = v) }
        }
    }
    if (s.notificationSummary) {
        item { ActionRow("Choose apps for your summary", "$summaryAppCount chosen") { vm.screen.value = Screen.PickSummaryApps } }
        item {
            ToggleRow("Exclude conversations", "Messages from people always come through", s.summaryExcludeConversations) { v ->
                vm.updateSettings { it.copy(summaryExcludeConversations = v) }
            }
        }
        item {
            ToggleRow("Summarize silent notifications", "Also collect low-priority notifications from any app", s.summarizeSilent) { v ->
                vm.updateSettings { it.copy(summarizeSilent = v) }
            }
        }
        item { ActionRow("View summary", "Next summary around ${SummaryScheduler.nextDeliveryText()}") { vm.openSummary() } }
    }
}
