package com.mytime.launcher.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.mytime.launcher.data.Graph
import com.mytime.launcher.data.HomeWidget
import kotlin.math.abs

/** Widget host view that reports a long press, like a launcher icon, so widgets can be edited. */
class LongPressWidgetView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val trigger = Runnable { fired = true; performHapticFeedback(0); onLongPress?.invoke() }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false; downX = ev.x; downY = ev.y
                handler.postDelayed(trigger, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) handler.removeCallbacks(trigger)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(trigger)
        }
        // After a long press, take the gesture away from the widget so it doesn't also click.
        return fired
    }
}

private class Host(context: Context) : AppWidgetHost(context, HOST_ID) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LongPressWidgetView(context)
}

private const val HOST_ID = 4354

/**
 * Hosts home-screen widgets. Adding one follows the platform flow: allocate an id, bind it
 * (asking the user if needed), run the widget's configure screen if it has one, then save.
 */
object Widgets {
    const val REQ_BIND = 4101
    const val REQ_CONFIGURE = 4102

    private lateinit var host: AppWidgetHost
    private lateinit var manager: AppWidgetManager
    /** Widget waiting for the bind or configure result. */
    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    fun init(context: Context) {
        if (::host.isInitialized) return
        host = Host(context.applicationContext)
        manager = AppWidgetManager.getInstance(context.applicationContext)
    }

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    /** All widget providers, for every profile, sorted by app then widget name. */
    fun providers(context: Context): List<AppWidgetProviderInfo> {
        val users = context.getSystemService(UserManager::class.java).userProfiles
        val pm = context.packageManager
        return users.flatMap { manager.getInstalledProvidersForProfile(it) }
            .sortedWith(compareBy({ appLabel(context, it) }, { it.loadLabel(pm) }))
    }

    fun appLabel(context: Context, info: AppWidgetProviderInfo): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
    }.getOrDefault(info.provider.packageName)

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun createView(context: Context, id: Int): LongPressWidgetView? {
        val info = info(id) ?: return null
        return host.createView(context, id, info) as? LongPressWidgetView
    }

    @Suppress("DEPRECATION")
    fun updateSize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        runCatching { view.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp) }
    }

    fun defaultHeightDp(context: Context, info: AppWidgetProviderInfo): Int {
        val density = context.resources.displayMetrics.density
        return (info.minHeight / density).toInt().coerceIn(110, 400)
    }

    /** Step 1: allocate and bind. Asks the user when the launcher isn't allowed to bind silently. */
    fun startAdd(activity: Activity, info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        pendingInfo = info
        if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            configureOrFinish(activity)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQ_BIND)
        }
    }

    /** Called from the activity's onActivityResult. Returns true if it handled the result. */
    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIGURE) return false
        if (pendingId == -1) return true
        if (resultCode != Activity.RESULT_OK) {
            cancelPending()
            return true
        }
        if (requestCode == REQ_BIND) configureOrFinish(activity) else finishAdd(activity)
        return true
    }

    @SuppressLint("WrongConstant")
    private fun configureOrFinish(activity: Activity) {
        val info = pendingInfo ?: return
        val optional = Build.VERSION.SDK_INT >= 31 &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (info.configure != null && !optional) {
            runCatching {
                host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQ_CONFIGURE, null)
            }.onFailure { finishAdd(activity) }
        } else {
            finishAdd(activity)
        }
    }

    private fun finishAdd(context: Context) {
        val info = pendingInfo ?: return
        val list = Graph.prefs.widgets.value
        Graph.prefs.setWidgets(list + HomeWidget(pendingId, defaultHeightDp(context, info)))
        pendingId = -1
        pendingInfo = null
    }

    private fun cancelPending() {
        runCatching { host.deleteAppWidgetId(pendingId) }
        pendingId = -1
        pendingInfo = null
    }

    fun remove(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
        Graph.prefs.setWidgets(Graph.prefs.widgets.value.filter { it.id != id })
    }

    fun setHeight(id: Int, heightDp: Int) {
        Graph.prefs.setWidgets(Graph.prefs.widgets.value.map { if (it.id == id) it.copy(heightDp = heightDp) else it })
    }

    fun move(id: Int, delta: Int) {
        val list = Graph.prefs.widgets.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        Graph.prefs.setWidgets(list)
    }
}
