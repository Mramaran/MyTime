package com.mytime.launcher.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mytime.launcher.data.AppInfo
import com.mytime.launcher.lock.LockService
import com.mytime.launcher.notify.SummaryScheduler
import com.mytime.launcher.widget.Widgets
import com.mytime.launcher.calendar.CalendarEvent
import android.appwidget.AppWidgetProviderInfo

/** The home screen. Declared as HOME in the manifest, so it is the launcher itself. */
class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.state.collectAsState()
            val darkText by vm.wallpaperPrefersDarkText.collectAsState()
            MyTimeTheme(settings.settings, darkText) {
                LauncherRoot(vm, actions)
            }
        }
        handleSummaryIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        Widgets.init(this)
        Widgets.startListening()
    }

    override fun onStop() {
        super.onStop()
        Widgets.stopListening()
    }

    override fun onResume() {
        super.onResume()
        vm.refreshUsage()
        vm.refreshPermissions()
        vm.refreshWeather()
        vm.refreshCalendar()
    }

    @Deprecated("Widget bind/configure still use the platform result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FONT) {
            if (resultCode == RESULT_OK) data?.data?.let(::importFont)
            return
        }
        if (!Widgets.onActivityResult(this, requestCode, resultCode)) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /** Copies a chosen .ttf/.otf into app storage and switches to it, if Android can read it as a font. */
    private fun importFont(uri: Uri) {
        val target = FontChoice.customFontFile(filesDir)
        val ok = runCatching {
            target.parentFile?.mkdirs()
            contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
            android.graphics.Typeface.createFromFile(target) != null
        }.getOrDefault(false)
        if (ok) {
            vm.updateSettings { it.copy(font = FontChoice.Custom.name) }
        } else {
            target.delete()
            Toast.makeText(this, "That file isn't a font MyTime can use", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) vm.refreshWeather(force = true)
        if (requestCode == REQ_CALENDAR) vm.refreshCalendar()
    }

    /** Pressing Home while MyTime is already in front returns to the favorites screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!handleSummaryIntent(intent) && intent.action == Intent.ACTION_MAIN) vm.goHome()
    }

    private fun handleSummaryIntent(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(SummaryScheduler.EXTRA_OPEN_SUMMARY, false) != true) return false
        intent.removeExtra(SummaryScheduler.EXTRA_OPEN_SUMMARY)
        vm.openSummary()
        return true
    }

    val actions = object : SystemActions {
        override fun expandNotifications() {
            @SuppressLint("WrongConstant")
            val service = getSystemService("statusbar")
            runCatching {
                Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                    .invoke(service)
            }
        }

        override fun lockScreen() {
            if (!vm.state.value.settings.doubleTapLock) return
            if (!LockService.lockNow()) {
                Toast.makeText(this@MainActivity, "Turn on \"MyTime screen lock\" in Accessibility settings", Toast.LENGTH_LONG).show()
                openAccessibilitySettings()
            }
        }

        override fun appInfo(app: AppInfo) {
            getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.component, app.user, null, null)
        }

        override fun uninstall(app: AppInfo) {
            val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, app.component.className))
                .putExtra(Intent.EXTRA_USER, app.user)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
        }

        override fun screenTime(app: AppInfo) {
            val intent = Intent("android.settings.action.APP_USAGE_SETTINGS")
                .putExtra(Intent.EXTRA_PACKAGE_NAME, app.packageName)
            runCatching { startActivity(intent) }.onFailure { appInfo(app) }
        }

        override fun webSearch(query: String) {
            val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }

        override fun playStoreSearch(query: String) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(query)))) }
        }

        override fun openUsageAccess() {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
            runCatching { startActivity(intent) }.onFailure {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }

        override fun openAccessibilitySettings() {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        override fun openDefaultLauncherSettings() {
            runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        }

        override fun requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        override fun isLockServiceEnabled(): Boolean = LockService.isEnabled(this@MainActivity)

        override fun openNotificationAccess() {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }

        override fun openCalendar() {
            val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(intent) }
        }

        override fun showAlarms() {
            runCatching { startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) }
        }

        override fun requestLocationPermission() {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
            }
        }

        override fun addWidget(info: AppWidgetProviderInfo) {
            Widgets.init(this@MainActivity)
            Widgets.startAdd(this@MainActivity, info)
        }

        override fun requestCalendarPermission() {
            if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQ_CALENDAR)
            }
        }

        override fun openEvent(e: CalendarEvent) {
            val uri = android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, e.eventId)
            val intent = Intent(Intent.ACTION_VIEW, uri)
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
                .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, e.end)
            runCatching { startActivity(intent) }.onFailure { openCalendar() }
        }

        override fun newEvent() {
            runCatching { startActivity(Intent(Intent.ACTION_INSERT, android.provider.CalendarContract.Events.CONTENT_URI)) }
        }

        override fun openStoreApp(packageName: String) {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            runCatching { startActivity(market) }.onFailure { openUrl("https://play.google.com/store/apps/details?id=$packageName") }
        }

        override fun openUrl(url: String) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }

        override fun changeWallpaper() {
            runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper")) }
        }

        override fun pickFontFile() {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/octet-stream"))
            @Suppress("DEPRECATION")
            runCatching { startActivityForResult(intent, REQ_FONT) }
        }
    }

    private companion object {
        const val REQ_LOCATION = 4201
        const val REQ_FONT = 4202
        const val REQ_CALENDAR = 4203
    }
}

/** System calls the UI needs, kept out of the composables. */
interface SystemActions {
    fun expandNotifications()
    fun lockScreen()
    fun appInfo(app: AppInfo)
    fun uninstall(app: AppInfo)
    fun screenTime(app: AppInfo)
    fun webSearch(query: String)
    fun playStoreSearch(query: String)
    fun openUsageAccess()
    fun openAccessibilitySettings()
    fun openDefaultLauncherSettings()
    fun requestNotificationPermission()
    fun isLockServiceEnabled(): Boolean
    fun openNotificationAccess()
    fun openCalendar()
    fun showAlarms()
    fun requestLocationPermission()
    fun addWidget(info: AppWidgetProviderInfo)
    fun changeWallpaper()
    fun openStoreApp(packageName: String)
    fun openUrl(url: String)
    fun pickFontFile()
    fun requestCalendarPermission()
    fun openEvent(e: CalendarEvent)
    fun newEvent()
}
