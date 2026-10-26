package com.mytime.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytime.launcher.focus.Focus

private val DURATIONS = listOf(25 to "25 min", 60 to "1 hour", 120 to "2 hours", null to "Until I turn it off")

/**
 * Home-screen focus button. One tap starts focus (until you turn it off); long-press picks a
 * length. While focusing it shows the time left, and a tap offers to extend or end.
 */
@Composable
fun FocusPill(vm: LauncherViewModel, state: LauncherState, now: Long) {
    var sheet by remember { mutableStateOf(false) }
    val until by vm.focusUntil.collectAsState()
    val active = state.focusActive
    val left = if (active && until != Focus.UNTIL_OFF) ((until - now + 59_999L) / 60_000L) else null
    val label = when {
        !active -> "◎ Focus"
        left != null -> "◉ Focusing · $left min left"
        else -> "◉ Focusing"
    }
    Text(
        label,
        style = OnWallpaper.copy(fontSize = 14.sp, color = if (active) MaterialTheme.colorScheme.onPrimary else Wall),
        modifier = Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) MaterialTheme.colorScheme.primary else Wall.copy(alpha = 0.15f))
            .combinedClickable(
                onClick = { if (active) sheet = true else vm.startFocus(null) },
                onLongClick = { sheet = true },
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
    if (sheet) FocusSheet(vm, active, state.leisure.size) { sheet = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusSheet(vm: LauncherViewModel, active: Boolean, leisureCount: Int, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(if (active) "You're focusing" else "Focus mode", style = MaterialTheme.typography.titleLarge)
            Text(
                if (leisureCount == 0) "Choose your leisure apps in settings first; focus mode hides them."
                else "Hides your $leisureCount leisure apps from the home screen, app list and suggestions.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (active) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = false, onClick = { vm.extendFocus(15); onDismiss() }, label = { Text("+15 min") })
                    FilterChip(selected = false, onClick = { vm.extendFocus(30); onDismiss() }, label = { Text("+30 min") })
                }
                Button(onClick = { vm.endFocus(); onDismiss() }, modifier = Modifier.padding(top = 12.dp)) { Text("End focus") }
            } else {
                Text("For how long?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    DURATIONS.take(3).forEach { (m, label) ->
                        FilterChip(selected = false, onClick = { vm.startFocus(m); onDismiss() }, label = { Text(label) })
                    }
                }
                FilterChip(selected = false, onClick = { vm.startFocus(null); onDismiss() }, label = { Text(DURATIONS[3].second) })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Shown when a leisure app is tapped in search during focus. */
@Composable
fun FocusBlockDialog(vm: LauncherViewModel) {
    val app by vm.focusBlocked.collectAsState()
    val a = app ?: return
    AlertDialog(
        onDismissRequest = { vm.focusBlocked.value = null },
        title = { Text("You're focusing") },
        text = { Text("${a.label} is one of your leisure apps. Stay focused, or end focus to open it.") },
        confirmButton = { Button(onClick = { vm.focusBlocked.value = null; vm.goHome() }) { Text("Stay focused") } },
        dismissButton = {
            TextButton(onClick = {
                vm.focusBlocked.value = null
                vm.endFocus()
                vm.requestLaunch(a)
            }) { Text("End focus & open") }
        },
    )
}

fun LazyListScope.focusSettings(vm: LauncherViewModel, state: LauncherState) {
    item {
        ActionRow("Weekly report", "Screen time per day, your top apps, and this week vs last") { vm.screen.value = Screen.Report }
    }
    item {
        ActionRow(
            if (state.focusActive) "Focus mode is on" else "Focus mode",
            if (state.focusActive) "Tap to end it" else "Hide leisure apps for a while. Tap ◎ Focus on the home screen, or add the Focus tile to Quick Settings.",
        ) { if (state.focusActive) vm.endFocus() else vm.startFocus(null) }
    }
}
