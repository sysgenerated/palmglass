package io.github.sysgenerated.palmglass.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.sysgenerated.palmglass.PalmGlassApp
import io.github.sysgenerated.palmglass.R
import io.github.sysgenerated.palmglass.SessionReceiver
import io.github.sysgenerated.palmglass.core.Displays
import io.github.sysgenerated.palmglass.core.Prefs
import io.github.sysgenerated.palmglass.core.SessionState
import io.github.sysgenerated.palmglass.touchpad.TouchpadActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Owns the cursor overlay and gesture injection on the glasses display, watches for the glasses
 * being connected or removed, and holds the session notification.
 *
 * Accessibility services are bound by the system, so this process may start the touchpad
 * activity from the background when the glasses appear.
 */
class PalmGlassAccessibilityService : AccessibilityService() {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var displayManager: DisplayManager
    private lateinit var prefs: Prefs

    private var overlay: CursorOverlay? = null
    private var injector: GestureInjector? = null
    private var autoStart = false

    private val displayListener = object : DisplayManager.DisplayListener {
        // External displays are often added in the OFF state and switch on a moment later via
        // onDisplayChanged, so every callback may be the one that first sees a usable display.
        override fun onDisplayAdded(displayId: Int) { rescan(announce = true) }
        override fun onDisplayRemoved(displayId: Int) { rescan(announce = true) }
        override fun onDisplayChanged(displayId: Int) { rescan(announce = true) }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        displayManager = getSystemService(DisplayManager::class.java)
        prefs = Prefs(this)
        SessionState.setServiceRunning(true)
        displayManager.registerDisplayListener(displayListener, mainHandler)
        scope.launch { prefs.settings.collectLatest { autoStart = it.autoStart } }
        scope.launch {
            combine(SessionState.touchpadActive, SessionState.inputLocked) { a, l -> a to l }
                .collectLatest { (active, locked) ->
                    overlay?.let { if (active) it.show() else it.hide(); it.setLocked(locked) }
                    if (active && !locked) pokeCursor() else hideCursorNow()
                    if (active) {
                        getSystemService(NotificationManager::class.java).cancel(CONNECTED_NOTIFICATION_ID)
                        showNotification()
                    } else {
                        clearNotification()
                    }
                }
        }
        rescan(announce = false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardownDisplay()
        displayManager.unregisterDisplayListener(displayListener)
        scope.cancel()
        clearNotification()
        SessionState.setServiceRunning(false)
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        SessionState.setServiceRunning(false)
    }

    // ---- display lifecycle ----

    private fun rescan(announce: Boolean) {
        val display = Displays.findExternal(displayManager)
        val currentId = injector?.displayId ?: Display.INVALID_DISPLAY
        if (display == null) {
            if (currentId != Display.INVALID_DISPLAY) {
                Log.i(TAG, "external display removed")
                teardownDisplay()
                SessionState.requestStop()
            }
            return
        }
        if (display.displayId == currentId) return
        teardownDisplay()
        setupDisplay(display)
        if (announce && !SessionState.touchpadActive.value) {
            if (autoStart) {
                Log.i(TAG, "auto-opening touchpad for display ${display.displayId}")
                launchTouchpad()
            } else {
                showConnectedNotification()
            }
        }
    }

    private fun setupDisplay(display: Display) {
        val bounds = Displays.bounds(display)
        Log.i(TAG, "external display ${display.displayId} '${display.name}' bounds=$bounds")
        try {
            overlay = CursorOverlay(this, display, bounds).also {
                if (SessionState.touchpadActive.value) it.show()
                it.setLocked(SessionState.inputLocked.value)
            }
            pokeCursor()
        } catch (e: Exception) {
            Log.e(TAG, "cursor overlay failed on display ${display.displayId}", e)
            overlay = null
        }
        injector = GestureInjector(this, display.displayId, bounds)
        SessionState.setExternalDisplay(display.displayId)
    }

    private fun teardownDisplay() {
        getSystemService(NotificationManager::class.java).cancel(CONNECTED_NOTIFICATION_ID)
        runCatching { overlay?.hide() }
        overlay = null
        injector = null
        SessionState.setExternalDisplay(Display.INVALID_DISPLAY)
    }

    fun launchTouchpad() {
        try {
            startActivity(
                Intent(this, TouchpadActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            Log.w(TAG, "could not open touchpad from the background", e)
        }
    }

    // ---- cursor visibility ----

    private val hideCursor = Runnable { overlay?.setVisible(false) }

    /** Shows the pointer and restarts the inactivity timer. */
    private fun pokeCursor() {
        if (SessionState.inputLocked.value) return
        overlay?.setVisible(true)
        mainHandler.removeCallbacks(hideCursor)
        mainHandler.postDelayed(hideCursor, CURSOR_HIDE_MS)
    }

    private fun hideCursorNow() {
        mainHandler.removeCallbacks(hideCursor)
        overlay?.setVisible(false)
    }

    // ---- touchpad API (called from TouchpadActivity on the main thread) ----

    fun moveCursor(dx: Float, dy: Float) {
        val o = overlay ?: return
        pokeCursor()
        o.moveBy(dx, dy)
        val inj = injector ?: return
        if (inj.isDragging && dragFollowsCursor) inj.dragTo(o.x, o.y)
    }

    fun tap() { val o = overlay ?: return; pokeCursor(); injector?.tap(o.x, o.y) }
    fun longPress() { val o = overlay ?: return; pokeCursor(); injector?.longPress(o.x, o.y) }

    private var dragFollowsCursor = false

    /** Press-and-hold at the cursor; subsequent cursor moves drag whatever is under it. */
    fun dragStart() {
        val o = overlay ?: return
        pokeCursor()
        dragFollowsCursor = true
        o.setPressed(true)
        injector?.dragStart(o.x, o.y)
    }

    /** Scroll: the injected finger moves while the cursor stays put. */
    fun scrollStart() {
        val o = overlay ?: return
        pokeCursor()
        dragFollowsCursor = false
        scrollX = o.x; scrollY = o.y
        injector?.dragStart(o.x, o.y)
    }

    private var scrollX = 0f
    private var scrollY = 0f

    fun scrollBy(dx: Float, dy: Float) {
        val inj = injector ?: return
        if (!inj.isDragging || dragFollowsCursor) return
        scrollX += dx; scrollY += dy
        pokeCursor()
        inj.dragTo(scrollX, scrollY)
    }

    fun releaseFinger() {
        overlay?.setPressed(false)
        injector?.dragEnd()
    }

    // ---- notification ----

    private fun showNotification() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val stop = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, SessionReceiver::class.java).setAction(SessionReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, TouchpadActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = NotificationCompat.Builder(this, PalmGlassApp.SESSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
    }

    private fun clearNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /** Silent, dismissible "glasses connected" notice that opens the touchpad when tapped. */
    private fun showConnectedNotification() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            this, 2,
            Intent(this, TouchpadActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, PalmGlassApp.SESSION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.connected_title))
            .setContentText(getString(R.string.connected_text))
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        getSystemService(NotificationManager::class.java).notify(CONNECTED_NOTIFICATION_ID, n)
    }

    companion object {
        private const val TAG = "PalmGlassService"
        private const val NOTIFICATION_ID = 1
        private const val CONNECTED_NOTIFICATION_ID = 2
        private const val CURSOR_HIDE_MS = 3000L

        /** The running service, if the user has enabled it. Same process, so a plain reference is fine. */
        @Volatile
        var instance: PalmGlassAccessibilityService? = null
            private set
    }
}
