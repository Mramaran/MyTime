package com.mytime.launcher.notify

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the home-screen player shows. */
data class MediaState(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val isPlaying: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
)

/**
 * Follows the phone's media sessions, like Niagara's player. Reading other apps' sessions is
 * allowed because MyTime is an enabled notification listener, so this starts and stops with it.
 * The session shown is the one playing now, or else the one that played most recently.
 */
object MediaRepo {

    private val _state = MutableStateFlow<MediaState?>(null)
    val state: StateFlow<MediaState?> = _state

    private var appContext: Context? = null
    private var manager: MediaSessionManager? = null
    private var current: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> choose(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = recheck()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = recheck()
    }

    fun start(context: Context, listener: ComponentName) {
        val ctx = context.applicationContext
        appContext = ctx
        val m = ctx.getSystemService(MediaSessionManager::class.java)
        manager = m
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, listener, handler)
            choose(m.getActiveSessions(listener))
        }
    }

    fun stop() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        current?.unregisterCallback(callback)
        current = null
        _state.value = null
    }

    /** Re-picks the session when one starts or stops playing (another app may have taken over). */
    private fun recheck() {
        val m = manager ?: return
        val listener = appContext?.let { ComponentName(it, NotifListener::class.java) } ?: return
        runCatching { choose(m.getActiveSessions(listener)) }.onFailure { publish() }
    }

    private fun choose(sessions: List<MediaController>) {
        val ownPackage = appContext?.packageName
        val usable = sessions.filter { it.packageName != ownPackage && it.metadata != null }
        val pick = usable.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: usable.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
        if (pick?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            current = pick
            pick?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = current
        val meta = c?.metadata
        if (c == null || meta == null) {
            _state.value = null
            return
        }
        val ps = c.playbackState
        val actions = ps?.actions ?: 0L
        val ctx = appContext
        val label = ctx?.let {
            runCatching { it.packageManager.getApplicationLabel(it.packageManager.getApplicationInfo(c.packageName, 0)).toString() }.getOrNull()
        } ?: c.packageName
        _state.value = MediaState(
            packageName = c.packageName,
            appLabel = label,
            title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
            artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            isPlaying = ps?.state == PlaybackState.STATE_PLAYING || ps?.state == PlaybackState.STATE_BUFFERING,
            // Apps that don't declare their actions usually still support skipping.
            canSkipNext = actions == 0L || actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSkipPrevious = actions == 0L || actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
        )
    }

    fun playPause() {
        val c = current ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = current?.transportControls?.skipToNext()
    fun previous() = current?.transportControls?.skipToPrevious()

    /** Opens the player's own screen, or the app if it doesn't offer one. */
    fun open(context: Context) {
        val c = current ?: return
        val pi = c.sessionActivity
        if (pi != null) {
            val opts = if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle() else null
            runCatching { pi.send(context, 0, null, null, null, null, opts) }
        } else {
            context.packageManager.getLaunchIntentForPackage(c.packageName)?.let { runCatching { context.startActivity(it) } }
        }
    }
}
