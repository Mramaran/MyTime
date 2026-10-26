package com.mytime.launcher.ui

import android.app.AlarmManager
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytime.launcher.data.AppInfo
import com.mytime.launcher.data.Graph
import com.mytime.launcher.data.HomeWidget
import com.mytime.launcher.icons.IconLoader
import com.mytime.launcher.data.Settings
import com.mytime.launcher.usage.UsageRepo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun LauncherRoot(vm: LauncherViewModel, actions: SystemActions) {
    val screen by vm.screen.collectAsState()
    val state by vm.state.collectAsState()
    val pending by vm.pendingLaunch.collectAsState()
    var optionsFor by remember { mutableStateOf<AppInfo?>(null) }

    BackHandler(enabled = screen != Screen.Home) { vm.goHome() }

    val onLongPress: (AppInfo) -> Unit = { optionsFor = it }

    // Dim the wallpaper more outside the home screen, like Niagara's "dim wallpaper".
    val scrim = if (screen == Screen.Home) 0.25f else 0.8f
    // Nothing here sits on a Surface, so set the default text/icon colour explicitly.
    CompositionLocalProvider(LocalContentColor provides if (screen in WallpaperScreens) Wall else MaterialTheme.colorScheme.onBackground, LocalListStates provides vm.listStates) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim)),
    ) {
        when (screen) {
            Screen.Home -> HomeScreen(vm, state, actions, onLongPress)
            Screen.AllApps -> AllAppsScreen(vm, state, actions, onLongPress)
            Screen.Search -> SearchScreen(vm, state, actions, onLongPress)
            Screen.Settings -> SettingsScreen(vm, state, actions)
            Screen.PickLeisure -> PickLeisureScreen(vm, state)
            Screen.HiddenApps -> HiddenAppsScreen(vm, state)
            Screen.EditFavorites -> EditFavoritesScreen(vm, state)
            Screen.Summary -> SummaryScreen(vm, state)
            Screen.PickSummaryApps -> PickSummaryAppsScreen(vm, state)
            Screen.ClockStyles -> ClockStylesScreen(vm, state, rememberNow(), is24Hour())
            Screen.PickWidget -> PickWidgetScreen(vm, actions)
            Screen.Appearance -> AppearanceScreen(vm, state, actions)
            Screen.PickIconPack -> PickIconPackScreen(vm, state, actions)
            Screen.Report -> ReportScreen(vm, state, actions)
            Screen.ThemeGallery -> ThemeGalleryScreen(vm)
        }
    }
    }

    optionsFor?.let { app ->
        AppOptionsSheet(app, state, vm, actions, onDismiss = { optionsFor = null })
    }
    pending?.let { PauseDialog(it, vm) }
    FocusBlockDialog(vm)
}

/* ---------------------------------------------------------------- Home */

@Composable
private fun HomeScreen(vm: LauncherViewModel, state: LauncherState, actions: SystemActions, onLongPress: (AppInfo) -> Unit) {
    val allNotifs by vm.notifications.collectAsState()
    val notifs = if (state.settings.inlineNotifications) allNotifs else emptyMap()
    var expandedPkg by remember { mutableStateOf<String?>(null) }
    val widgets by vm.widgets.collectAsState()
    val media by vm.media.collectAsState()
    var widgetOptions by remember { mutableStateOf<HomeWidget?>(null) }
    widgetOptions?.let { w -> WidgetOptionsSheet(widgets.firstOrNull { it.id == w.id } ?: w) { widgetOptions = null } }
    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .pointerInput(state.settings.swipeDownNotifications) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = {
                        val threshold = 60.dp.toPx()
                        when {
                            total < -threshold -> { vm.pendingSection.value = null; vm.screen.value = Screen.AllApps }
                            total > threshold && state.settings.swipeDownNotifications -> actions.expandNotifications()
                        }
                    },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            val now = rememberNow()
            AtAGlanceHeader(vm, state, actions, now, is24Hour())
            if (state.settings.showScreenTime) ScreenTimeLine(vm, state, actions)
            FocusPill(vm, state, now)
            Spacer(Modifier.height(28.dp))

            if (state.favorites.isEmpty()) {
                Text(
                    "No favorites yet. Swipe the alphabet on the right, long-press an app and tap \"Add to favorites\".",
                    style = OnWallpaper.copy(fontSize = 15.sp, color = Wall.copy(alpha = 0.8f)),
                )
            }
            val homeList = rememberLazyListState()
            // The player appears above whatever is first; keep it fully in view instead of half-scrolled.
            val hasMedia = state.settings.showMediaPlayer && media != null
            LaunchedEffect(hasMedia) { if (hasMedia && homeList.firstVisibleItemIndex <= 1) homeList.scrollToItem(0) }
            // Drags the list can't use (up at its end, down at its top) are home-screen swipes:
            // up opens the app list, down opens notifications. The list itself would otherwise
            // swallow them, since it handles vertical drags for scrolling.
            val density = LocalDensity.current
            val swipeDown = state.settings.swipeDownNotifications
            val homeSwipes = remember(swipeDown, density) {
                val threshold = with(density) { 60.dp.toPx() }
                object : NestedScrollConnection {
                    var pulled = 0f
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (source == NestedScrollSource.UserInput) pulled += available.y
                        return Offset.Zero
                    }
                    override suspend fun onPreFling(available: Velocity): Velocity {
                        val p = pulled
                        pulled = 0f
                        when {
                            p < -threshold -> { vm.pendingSection.value = null; vm.screen.value = Screen.AllApps }
                            p > threshold && swipeDown -> actions.expandNotifications()
                        }
                        return Velocity.Zero
                    }
                }
            }
            LazyColumn(
                Modifier.weight(1f).nestedScroll(homeSwipes),
                state = homeList,
                // Room for the settings and search buttons at the bottom.
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                if (state.settings.showMediaPlayer) media?.let { m -> item(key = "media") { MediaCard(m) } }
                items(widgets, key = { "w_" + it.id }) { w ->
                    HomeWidgetItem(w) { widgetOptions = w }
                }
                items(state.favorites, key = { it.key }) { app ->
                    val list = notifs[app.packageName].orEmpty()
                    val expanded = expandedPkg == app.packageName && list.isNotEmpty()
                    AppRow(
                        app, state, big = true, showLabel = !state.settings.hideFavoriteLabels,
                        notifCount = list.size,
                        preview = if (expanded) null else previewOf(list),
                        onPreviewClick = { expandedPkg = app.packageName },
                        onClick = { if (expanded) expandedPkg = null else vm.requestLaunch(app) },
                        onLongClick = { onLongPress(app) },
                    )
                    if (expanded) {
                        Column(Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                            list.take(MAX_EXPANDED_NOTIFS).forEach { NotificationCard(it) }
                            if (list.size > MAX_EXPANDED_NOTIFS) {
                                Text("+${list.size - MAX_EXPANDED_NOTIFS} more in the notification shade",
                                    style = OnWallpaper.copy(fontSize = 12.sp, color = Wall.copy(alpha = 0.6f)))
                            }
                        }
                    }
                }
                if (state.suggestions.isNotEmpty()) {
                    item {
                        Text("Often used", style = OnWallpaper.copy(fontSize = 12.sp, color = Wall.copy(alpha = 0.6f)),
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    }
                    items(state.suggestions, key = { "s_" + it.key }) { app ->
                        AppRow(app, state, big = false, notifCount = notifs[app.packageName]?.size ?: 0,
                            onClick = { vm.requestLaunch(app) }, onLongClick = { onLongPress(app) })
                    }
                }
            }
        }


        FloatingActionButton(
            onClick = { vm.screen.value = Screen.Search },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 52.dp, bottom = 20.dp),
            containerColor = Accent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
        ) { Icon(Icons.Default.Search, contentDescription = "Search") }

    }
}

/** Current time, updated on each minute boundary. */
@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L)
        }
    }
    return now
}

@Composable
fun is24Hour(): Boolean = android.text.format.DateFormat.is24HourFormat(LocalContext.current)

@Composable
private fun ScreenTimeLine(vm: LauncherViewModel, state: LauncherState, actions: SystemActions) {
    val access by vm.hasUsageAccess.collectAsState()
    val u = state.usage
    val text = if (!access) "Tap to allow usage access and see your screen time"
    else "Today ${UsageRepo.formatDuration(u.screenOnTodayMs)} · ${u.unlocksToday} unlocks"
    Text(
        text,
        style = OnWallpaper.copy(fontSize = 14.sp, color = Accent),
        modifier = Modifier
            .padding(top = 6.dp)
            .clickable { if (!access) actions.openUsageAccess() else vm.screen.value = Screen.Report },
    )
}

/* ---------------------------------------------------------------- App rows and icons */

@Composable
fun AppIcon(app: AppInfo, sizeDp: Int, greyscale: Boolean) {
    val settings by Graph.prefs.settings.collectAsState()
    val shown = sizeDp * settings.iconSizePct / 100
    val px = with(LocalDensity.current) { shown.dp.roundToPx() }
    // Reload when the icon pack or shape changes; the cache key includes the same signature.
    val look = IconLoader.signature(settings)
    val icon by produceState<ImageBitmap?>(null, app.key, look, px) { value = Graph.apps.icon(app, px) }
    val filter = remember(greyscale) {
        if (greyscale) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
    }
    Box(Modifier.size(shown.dp)) {
        icon?.let { Image(it, contentDescription = null, colorFilter = filter, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    state: LauncherState,
    big: Boolean,
    showLabel: Boolean = true,
    tag: String? = null,
    notifCount: Int = 0,
    /** Newest notification, shown under the label; tapping it expands the notifications. */
    preview: String? = null,
    onPreviewClick: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val leisure = app.packageName in state.leisure
    val grey = leisure && state.settings.greyscaleLeisure
    val iconSize = if (big) 42 else 34
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = if (big) 8.dp else 6.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                AppIcon(app, iconSize, grey)
                if (notifCount > 0 && !showLabel) NotifDot(Modifier.align(Alignment.TopEnd))
            }
            if (showLabel) {
                Spacer(Modifier.width(16.dp))
                Text(
                    app.label,
                    style = OnWallpaper.copy(
                        fontSize = if (big) 20.sp else 16.sp,
                        color = if (grey) Wall.copy(alpha = 0.6f) else Wall,
                    ),
                    maxLines = 1,
                )
                if (notifCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    NotifDot(count = notifCount)
                }
                tag?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = OnWallpaper.copy(fontSize = 11.sp, color = Wall.copy(alpha = 0.5f)))
                }
            }
        }
        if (preview != null && showLabel) {
            Text(
                preview,
                style = OnWallpaper.copy(fontSize = 13.sp, color = Wall.copy(alpha = 0.75f)),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = (iconSize + 16).dp, top = 2.dp)
                    .clickable(onClick = onPreviewClick),
            )
        }
    }
}

/** Notification badge: a small dot, or a pill with the count. */
@Composable
private fun NotifDot(modifier: Modifier = Modifier, count: Int = 0) {
    Box(
        modifier
            .clip(CircleShape)
            .background(Accent)
            .padding(horizontal = if (count > 0) 6.dp else 4.dp, vertical = if (count > 0) 1.dp else 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (count > 0) Text(count.toString(), style = TextStyle(color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
    }
}

/* ---------------------------------------------------------------- All apps */

@Composable
private fun AllAppsScreen(vm: LauncherViewModel, state: LauncherState, actions: SystemActions, onLongPress: (AppInfo) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pendingSection by vm.pendingSection.collectAsState()
    val notifs by vm.notifications.collectAsState()

    // Row index of each letter header, counting headers and app rows.
    val rows = remember(state.visibleApps) {
        buildList<Any> {
            var last: Char? = null
            for (app in state.visibleApps) {
                if (app.section != last) { add(app.section); last = app.section }
                add(app)
            }
        }
    }
    val headerIndex = remember(rows) {
        rows.withIndex().filter { it.value is Char }.associate { (it.value as Char) to it.index }
    }
    val sections = remember(headerIndex) { headerIndex.keys.toList() }

    LaunchedEffect(pendingSection, headerIndex) {
        pendingSection?.let { c -> headerIndex[c]?.let { listState.scrollToItem(it) } }
    }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 56.dp)) {
            items(rows.size, key = { i -> rows[i].let { if (it is AppInfo) it.key else "h_$it" } }) { i ->
                when (val row = rows[i]) {
                    is Char -> Text(
                        row.toString(),
                        style = OnWallpaper.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Accent),
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp),
                    )
                    is AppInfo -> AppRow(
                        row, state, big = false,
                        tag = if (row.isWorkProfile) "work" else null,
                        notifCount = if (state.settings.inlineNotifications) notifs[row.packageName]?.size ?: 0 else 0,
                        onClick = { vm.requestLaunch(row) }, onLongClick = { onLongPress(row) },
                    )
                }
            }
            item(key = "mytime_settings") { SettingsEntry { vm.openSettings() } }
            item { Spacer(Modifier.height(80.dp)) }
        }
        AlphabetBar(
            sections = sections,
            color = Wall,
            modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 48.dp),
            onSection = { c -> headerIndex[c]?.let { scope.launch { listState.scrollToItem(it) } } },
            onDoubleTap = actions::lockScreen,
        )
    }
}

/* ---------------------------------------------------------------- Search */

@Composable
private fun SearchScreen(vm: LauncherViewModel, state: LauncherState, actions: SystemActions, onLongPress: (AppInfo) -> Unit) {
    val query by vm.query.collectAsState()
    val results by vm.searchResults.collectAsState()
    val calc by vm.calculatorResult.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).focusRequester(focus),
            placeholder = { Text("Look") },
            singleLine = true,
            // The field sits on the wallpaper, not on a themed surface.
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Wall, unfocusedTextColor = Wall,
                focusedPlaceholderColor = Wall.copy(alpha = 0.6f), unfocusedPlaceholderColor = Wall.copy(alpha = 0.6f),
                focusedLeadingIconColor = Wall, unfocusedLeadingIconColor = Wall,
                focusedTrailingIconColor = Wall, unfocusedTrailingIconColor = Wall,
                unfocusedBorderColor = Wall.copy(alpha = 0.5f),
            ),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                IconButton(onClick = { vm.goHome() }) { Icon(Icons.Default.Close, "Close search") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                val first = results.firstOrNull()
                if (first != null) vm.requestLaunch(first.app) else if (query.isNotBlank()) actions.webSearch(query)
            }),
        )
        LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp)) {
            calc?.let { value ->
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = Wall.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Text("= $value", style = OnWallpaper.copy(fontSize = 28.sp), modifier = Modifier.padding(16.dp))
                    }
                }
            }
            items(results, key = { it.app.key }) { r ->
                AppRow(r.app, state, big = false, tag = if (r.paused) "Focus" else if (r.hidden) "Hidden" else null,
                    onClick = { vm.requestLaunch(r.app) }, onLongClick = { onLongPress(r.app) })
            }
            if (query.isNotBlank()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Wall.copy(alpha = 0.15f))
                    TextButton(onClick = { actions.webSearch(query) }) { Text("Search the web for \"$query\"") }
                    TextButton(onClick = { actions.playStoreSearch(query) }) { Text("Search for more apps") }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- Long-press options */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppOptionsSheet(app: AppInfo, state: LauncherState, vm: LauncherViewModel, actions: SystemActions, onDismiss: () -> Unit) {
    val today = state.usage.perAppTodayMs[app.packageName]
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 44, false)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(app.label, style = MaterialTheme.typography.titleLarge)
                if (today != null) Text("Today: ${UsageRepo.formatDuration(today)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        val isFav = app.key in vm.state.value.favorites.map { it.key }
        val isHidden = app.key in state.hidden
        val isLeisure = app.packageName in state.leisure
        val notSuggested = app.key in state.notSuggested
        SheetItem(if (isFav) "Remove from favorites" else "Add to favorites") { vm.toggleFavorite(app); onDismiss() }
        SheetItem(if (isLeisure) "Remove from leisure apps" else "Mark as leisure app (limit it)") { vm.toggleLeisure(app); onDismiss() }
        SheetItem(if (isHidden) "Show in app list again" else "Hide from app list") { vm.toggleHidden(app); onDismiss() }
        SheetItem(if (notSuggested) "Suggest again" else "Stop suggesting") { vm.toggleSuggest(app); onDismiss() }
        SheetItem("Screen time") { actions.screenTime(app); onDismiss() }
        SheetItem("App info") { actions.appInfo(app); onDismiss() }
        SheetItem("Uninstall") { actions.uninstall(app); onDismiss() }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetItem(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/* ---------------------------------------------------------------- Pause before opening */

@Composable
private fun PauseDialog(p: PendingLaunch, vm: LauncherViewModel) {
    var secondsLeft by remember(p) { mutableIntStateOf(PAUSE_SECONDS) }
    LaunchedEffect(p) {
        while (secondsLeft > 0) { delay(1000); secondsLeft-- }
    }
    AlertDialog(
        onDismissRequest = { vm.pendingLaunch.value = null },
        title = { Text(if (p.limitReached) "Daily limit reached" else "Take a breath") },
        text = {
            Column {
                Text("Do you really want to open ${p.app.label}?")
                Spacer(Modifier.height(8.dp))
                Text("You've used it for ${UsageRepo.formatDuration(p.todayMs)} today.")
                if (p.limitReached) {
                    Spacer(Modifier.height(8.dp))
                    Text("You've used up today's time for your leisure apps.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = secondsLeft == 0, onClick = { vm.launchNow(p.app) }) {
                Text(if (secondsLeft > 0) "Open in $secondsLeft…" else "Open ${p.app.label}")
            }
        },
        dismissButton = {
            Button(onClick = { vm.pendingLaunch.value = null; vm.goHome() }) { Text("Not now") }
        },
    )
}

private const val PAUSE_SECONDS = 5

/** Screens drawn straight on the wallpaper; the rest use the menu colour scheme. */
private val WallpaperScreens = setOf(Screen.Home, Screen.AllApps, Screen.Search)
private const val MAX_EXPANDED_NOTIFS = 3

/* ---------------------------------------------------------------- Settings */

@Composable
private fun SettingsScreen(vm: LauncherViewModel, state: LauncherState, actions: SystemActions) {
    val s = state.settings
    val access by vm.hasUsageAccess.collectAsState()
    val notifAccess by vm.hasNotificationAccess.collectAsState()
    val summaryApps by vm.summaryApps.collectAsState()
    val homeWidgets by vm.widgets.collectAsState()
    fun set(transform: (Settings) -> Settings) = vm.updateSettings(transform)
    val tab by vm.settingsTab.collectAsState()
    val lookItems = appearanceItems(vm, state, actions)

    Column(Modifier.fillMaxSize().systemBarsPadding().background(MaterialTheme.colorScheme.background)) {
        TopBar("MyTime settings") { vm.goHome() }
        // Two tabs, like Niagara's settings: how it looks, and what it does.
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = tab == 0, onClick = { vm.settingsTab.value = 0 }, text = { Text("Themes") },
                icon = { Icon(Icons.Default.Palette, contentDescription = null) })
            Tab(selected = tab == 1, onClick = { vm.settingsTab.value = 1 }, text = { Text("Features") },
                icon = { Icon(Icons.Default.Tune, contentDescription = null) })
        }
        if (tab == 0) LazyColumn(Modifier.fillMaxSize(), state = rememberKeptListState("settings_themes")) {
            item { Header("Ready-made themes") }
            item { ReadyMadeThemes(vm) }
            item {
                ActionRow("Clock style", ClockStyle.of(s.clockStyle).title) { vm.screen.value = Screen.ClockStyles }
            }
            lookItems()
        } else LazyColumn(Modifier.fillMaxSize(), state = rememberKeptListState("settings_features")) {
            item { Header("Setup") }
            item { ActionRow("Set MyTime as default home app", "Needed for the Home button to open MyTime") { actions.openDefaultLauncherSettings() } }
            item { ActionRow("Usage access", if (access) "Allowed" else "Needed for screen time, smart sort and Usage Breaker") { actions.openUsageAccess() } }
            item { ActionRow("Notifications", "Needed for Usage Breaker reminders") { actions.requestNotificationPermission() } }
            item {
                ActionRow("Screen lock (accessibility)", if (actions.isLockServiceEnabled()) "On" else "Needed for double-tap to lock") {
                    actions.openAccessibilitySettings()
                }
            }

            item { Header("Digital wellbeing") }
            focusSettings(vm, state)
            item { ActionRow("Leisure apps", "${state.leisure.size} chosen: apps you use more than you'd like") { vm.screen.value = Screen.PickLeisure } }
            item { ToggleRow("Usage Breaker", "Reminders at 5, 10, 20, 30, 45, 60… minutes in leisure apps", s.usageBreaker) { v -> set { it.copy(usageBreaker = v) } } }
            item { ToggleRow("Pause before opening", "A 5-second pause before a leisure app opens", s.pauseBeforeOpen) { v -> set { it.copy(pauseBeforeOpen = v) } } }
            item { ToggleRow("Greyscale leisure icons", "Grey icons are less tempting", s.greyscaleLeisure) { v -> set { it.copy(greyscaleLeisure = v) } } }
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text("Daily leisure limit", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        listOf(0, 30, 60, 90, 120).forEach { m ->
                            FilterChip(
                                selected = s.leisureDailyLimitMin == m,
                                onClick = { set { it.copy(leisureDailyLimitMin = m) } },
                                label = { Text(if (m == 0) "Off" else "${m}m") },
                            )
                        }
                    }
                }
            }
            item { ToggleRow("Show screen time on home", null, s.showScreenTime) { v -> set { it.copy(showScreenTime = v) } } }

            headerSettings(vm, state, actions, homeWidgets.size)
            mediaCalendarSettings(vm, state, actions)

            notificationSettings(vm, state, actions, notifAccess, summaryApps.size)

            item { Header("Home screen") }
            item { ActionRow("Edit favorites", "${state.favorites.size} favorites") { vm.screen.value = Screen.EditFavorites } }
            item { ToggleRow("Hide favorite names", null, s.hideFavoriteLabels) { v -> set { it.copy(hideFavoriteLabels = v) } } }
            item { ToggleRow("Suggest often-used apps", "Shown below your favorites", s.suggestOftenUsed) { v -> set { it.copy(suggestOftenUsed = v) } } }
            item { ToggleRow("Sort app list by usage", "Most-used apps first inside each letter", s.smartSort) { v -> set { it.copy(smartSort = v) } } }
            item { ActionRow("Hidden apps", "${state.hidden.size} hidden") { vm.screen.value = Screen.HiddenApps } }

            item { Header("Gestures") }
            item { ToggleRow("Swipe down for notifications", null, s.swipeDownNotifications) { v -> set { it.copy(swipeDownNotifications = v) } } }
            item { ToggleRow("Double-tap the alphabet to lock", "Uses the MyTime screen lock service", s.doubleTapLock) { v -> set { it.copy(doubleTapLock = v) } } }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
internal fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 4.dp))
}

@Composable
internal fun ActionRow(title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) }.padding(horizontal = 8.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/* ---------------------------------------------------------------- List editors */

@Composable
private fun PickLeisureScreen(vm: LauncherViewModel, state: LauncherState) {
    val apps = remember(state.allApps) { state.allApps.distinctBy { it.packageName } }
    EditorScaffold("Leisure apps", { vm.screen.value = Screen.Settings }) {
        item {
            Text("Apps you sometimes use more than you'd like to. MyTime can pause before opening them and remind you how long you've been in them.",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
        items(apps, key = { it.key }) { app ->
            val checked = app.packageName in state.leisure
            Row(Modifier.fillMaxWidth().clickable { vm.toggleLeisure(app) }.padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 32, false)
                Spacer(Modifier.width(16.dp))
                Text(app.label, Modifier.weight(1f))
                Checkbox(checked = checked, onCheckedChange = { vm.toggleLeisure(app) })
            }
        }
    }
}

@Composable
private fun HiddenAppsScreen(vm: LauncherViewModel, state: LauncherState) {
    val hidden = state.allApps.filter { it.key in state.hidden }
    EditorScaffold("Hidden apps", { vm.screen.value = Screen.Settings }) {
        if (hidden.isEmpty()) item {
            Text("No hidden apps. Long-press an app and tap \"Hide from app list\". Hidden apps still show up in search.",
                modifier = Modifier.padding(24.dp))
        }
        items(hidden, key = { it.key }) { app ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 32, false)
                Spacer(Modifier.width(16.dp))
                Text(app.label, Modifier.weight(1f))
                TextButton(onClick = { vm.toggleHidden(app) }) { Text("Show") }
            }
        }
    }
}

@Composable
private fun EditFavoritesScreen(vm: LauncherViewModel, state: LauncherState) {
    EditorScaffold("Your favorites", { vm.screen.value = Screen.Settings }) {
        if (state.favorites.isEmpty()) item {
            Text("No favorites yet. Long-press any app and tap \"Add to favorites\". Four to eight works best.",
                modifier = Modifier.padding(24.dp))
        }
        items(state.favorites, key = { it.key }) { app ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 32, false)
                Spacer(Modifier.width(16.dp))
                Text(app.label, Modifier.weight(1f))
                IconButton(onClick = { vm.moveFavorite(app, -1) }) { Icon(Icons.Default.ArrowUpward, "Move up") }
                IconButton(onClick = { vm.moveFavorite(app, 1) }) { Icon(Icons.Default.ArrowDownward, "Move down") }
                IconButton(onClick = { vm.toggleFavorite(app) }) { Icon(Icons.Default.Close, "Remove") }
            }
        }
    }
}

/** Scroll positions per screen, owned by the view model (see [LauncherViewModel.listStates]). */
val LocalListStates = androidx.compose.runtime.staticCompositionLocalOf { HashMap<String, androidx.compose.foundation.lazy.LazyListState>() }

/** A list state that survives leaving the screen and coming back. */
@Composable
fun rememberKeptListState(key: String): androidx.compose.foundation.lazy.LazyListState {
    val map = LocalListStates.current
    return remember(key, map) { map.getOrPut(key) { androidx.compose.foundation.lazy.LazyListState() } }
}

@Composable
internal fun EditorScaffold(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().systemBarsPadding().background(MaterialTheme.colorScheme.background)) {
        TopBar(title, onBack)
        LazyColumn(Modifier.fillMaxSize(), state = rememberKeptListState(title), content = content)
    }
}

/** Last row of the app list: opens MyTime's settings (it used to be a button in the corner). */
@Composable
private fun SettingsEntry(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text("MyTime settings", style = OnWallpaper.copy(fontSize = 16.sp))
    }
}
