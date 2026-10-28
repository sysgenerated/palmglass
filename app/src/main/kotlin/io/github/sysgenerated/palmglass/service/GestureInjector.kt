package io.github.sysgenerated.palmglass.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Turns touchpad intents into injected touch strokes on the glasses display.
 *
 * Taps and long-presses are single strokes. Drags are a chain of "continued" strokes: the system
 * holds the injected finger down between chunks, and each chunk is dispatched only after the
 * previous one completes, because a new non-continued gesture would cancel the one in flight.
 */
class GestureInjector(
    private val service: AccessibilityService,
    val displayId: Int,
    private val bounds: Rect,
) {
    private val handler = Handler(Looper.getMainLooper())

    private var activeStroke: StrokeDescription? = null
    private var inFlight = false
    private var fingerX = 0f
    private var fingerY = 0f
    private var pendingX: Float? = null
    private var pendingY: Float? = null
    private var pendingEnd = false

    val isDragging: Boolean get() = activeStroke != null || pendingEnd

    fun tap(x: Float, y: Float) {
        if (isDragging) return
        dispatch(StrokeDescription(pointPath(x, y), 0, TAP_MS, false))
    }

    fun longPress(x: Float, y: Float) {
        if (isDragging) return
        dispatch(StrokeDescription(pointPath(x, y), 0, LONG_PRESS_MS, false))
    }

    fun dragStart(x: Float, y: Float) {
        if (isDragging) return
        fingerX = clampX(x); fingerY = clampY(y)
        pendingX = null; pendingY = null; pendingEnd = false
        val stroke = StrokeDescription(pointPath(fingerX, fingerY), 0, DOWN_MS, true)
        activeStroke = stroke
        dispatch(stroke)
    }

    fun dragTo(x: Float, y: Float) {
        if (!isDragging) return
        pendingX = clampX(x); pendingY = clampY(y)
        pump()
    }

    fun dragEnd() {
        if (!isDragging) return
        pendingEnd = true
        pump()
    }

    /** Current position of the injected finger during a drag. */
    val fingerPosition: Pair<Float, Float> get() = fingerX to fingerY

    private fun pump() {
        if (inFlight) return
        val stroke = activeStroke ?: return
        val tx = pendingX
        val ty = pendingY
        if (tx != null && ty != null && (tx != fingerX || ty != fingerY)) {
            pendingX = null; pendingY = null
            val path = Path().apply { moveTo(fingerX, fingerY); lineTo(tx, ty) }
            fingerX = tx; fingerY = ty
            val next = stroke.continueStroke(path, 0, MOVE_MS, true)
            activeStroke = next
            dispatch(next)
        } else if (pendingEnd) {
            pendingEnd = false
            pendingX = null; pendingY = null
            val next = stroke.continueStroke(pointPath(fingerX, fingerY), 0, UP_MS, false)
            activeStroke = null
            dispatch(next)
        }
    }

    private fun dispatch(stroke: StrokeDescription) {
        val gesture = GestureDescription.Builder()
            .setDisplayId(displayId)
            .addStroke(stroke)
            .build()
        inFlight = true
        val ok = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { inFlight = false; pump() }
            override fun onCancelled(g: GestureDescription?) {
                inFlight = false
                // A cancelled continued stroke means the system dropped the finger. Reset.
                activeStroke = null; pendingEnd = false; pendingX = null; pendingY = null
            }
        }, handler)
        if (!ok) {
            Log.w(TAG, "dispatchGesture refused on display $displayId")
            inFlight = false
            activeStroke = null; pendingEnd = false
        }
    }

    private fun pointPath(x: Float, y: Float) = Path().apply { moveTo(clampX(x), clampY(y)) }
    private fun clampX(x: Float) = x.coerceIn(bounds.left.toFloat(), (bounds.right - 1).toFloat())
    private fun clampY(y: Float) = y.coerceIn(bounds.top.toFloat(), (bounds.bottom - 1).toFloat())

    companion object {
        private const val TAG = "GestureInjector"
        private const val TAP_MS = 40L
        private const val LONG_PRESS_MS = 750L
        private const val DOWN_MS = 20L
        private const val MOVE_MS = 24L
        private const val UP_MS = 16L
    }
}
