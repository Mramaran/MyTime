package com.mytime.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.icu.text.Transliterator
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mytime.launcher.icons.IconLoader
import com.mytime.launcher.icons.IconPacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** One launchable activity. [key] is stable across restarts: "package/class/userSerial". */
data class AppInfo(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val isWorkProfile: Boolean,
    /** Lower-case, accent-free, Latin-transliterated label used for search. */
    val searchLabel: String,
    /** Letter shown in the alphabet bar, or '#'. */
    val section: Char,
)

class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val iconCache = LruCache<String, ImageBitmap>(400)
    private val transliterator: Transliterator? =
        runCatching { Transliterator.getInstance("Any-Latin; Latin-ASCII") }.getOrNull()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) {
            iconCache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach { iconCache.remove(it) }
            refresh()
        }
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback)
        refresh()
    }

    fun refresh() {
        scope.launch { _apps.value = load() }
    }

    private fun load(): List<AppInfo> {
        val myUser = Process.myUserHandle()
        return userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user)
                .filter { it.componentName.packageName != context.packageName }
                .map { info ->
                    val label = info.label.toString().trim()
                    val search = normalize(label)
                    AppInfo(
                        key = "${info.componentName.packageName}/${info.componentName.className}/$serial",
                        label = label,
                        packageName = info.componentName.packageName,
                        component = info.componentName,
                        user = user,
                        isWorkProfile = user != myUser,
                        searchLabel = search,
                        section = sectionOf(search),
                    )
                }
        }.sortedWith(compareBy({ sectionSortKey(it.section) }, { it.searchLabel }))
    }

    fun normalize(text: String): String {
        val latin = transliterator?.transliterate(text) ?: text
        return Normalizer.normalize(latin, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
    }

    private fun sectionOf(normalized: String): Char {
        val c = normalized.firstOrNull { !it.isWhitespace() }?.uppercaseChar() ?: return '#'
        return if (c in 'A'..'Z') c else '#'
    }

    /** Icon for an app (icon pack / shape applied), loaded off the main thread and cached per look. */
    suspend fun icon(app: AppInfo, sizePx: Int): ImageBitmap? {
        val settings = Graph.prefs.settings.value
        val cacheKey = app.key + "#" + IconLoader.signature(settings)
        iconCache.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component)
                val activity = launcherApps.resolveActivity(intent, app.user) ?: return@runCatching null
                IconLoader.load(context, activity, app.user, app.isWorkProfile, sizePx, settings).asImageBitmap()
            }.getOrNull()?.also { iconCache.put(cacheKey, it) }
        }
    }

    /** An icon drawn with other settings (for previews in the icon pack picker); not cached. */
    suspend fun previewIcon(app: AppInfo, sizePx: Int, settings: Settings): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
            val activity = launcherApps.resolveActivity(intent, app.user) ?: return@runCatching null
            IconLoader.load(context, activity, app.user, app.isWorkProfile, sizePx, settings).asImageBitmap()
        }.getOrNull()
    }

    /** Drops cached icons, e.g. after the icon pack or shape changes. */
    fun clearIcons() {
        iconCache.evictAll()
        IconPacks.clear()
    }

    companion object {
        /** '#' sorts after Z, like Niagara. */
        fun sectionSortKey(c: Char): Int = if (c == '#') Int.MAX_VALUE else c.code
    }
}
