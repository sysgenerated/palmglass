package io.github.sysgenerated.palmglass.touchpad

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Recognises trackpad gestures on the black phone panel and reports them as intents.
 *
 *  one finger move ........ cursor
 *  one finger tap ......... click
 *  tap, then press+move ... drag (tap-and-drag)
 *  two finger tap ......... long press (context menu)
 *  two finger move ........ scroll
 *  three finger tap ....... toggle input lock
 *  three finger swipe down  exit
 *  three finger swipe up .. launcher panel
 *  three finger swipe left or right ... toggle keyboard panel
 *
 * Edge swipes are left alone so the system's own back and home gestures keep working.
 *
 * Contacts wider than a fingertip are treated as a palm and ignored for the whole gesture.
 */
class TouchpadView(context: Context) : View(context) {

    interface Listener {
        fun onMove(dx: Float, dy: Float, speed: Float)
        fun onTap()
        fun onLongPress()
        fun onDragStart()
        fun onDragEnd()
        fun onScrollStart()
        fun onScroll(dx: Float, dy: Float)
        fun onScrollEnd()
        fun onToggleLock()
        fun onExit()
        fun onToggleKeyboard()
        fun onOpenLauncher()
    }

    var listener: Listener? = null
    var locked: Boolean = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val tapTimeout = ViewConfiguration.getTapTimeout().toLong() + 120
    private val doubleTapWindow = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val dm = context.resources.displayMetrics
    private val palmMajorPx = PALM_MM / 25.4f * dm.xdpi
    private val swipeMinPx = SWIPE_MIN_DP * dm.density

    private var startTime = 0L
    private var maxPointers = 0
    private var ignoreGesture = false
    private var dragging = false
    private var scrolling = false
    private var totalDistance = 0f
    private var startX = 0f
    private var startY = 0f
    private var focalX = 0f
    private var focalY = 0f
    private var focalCount = 0
    private var lastMoveTime = 0L
    private var lastTapUpTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    // A trackpad surface has no click target of its own; taps become clicks on the glasses.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val now = SystemClock.uptimeMillis()
                startTime = now
                maxPointers = 1
                ignoreGesture = isPalm(e, 0)
                totalDistance = 0f
                startX = e.x; startY = e.y
                dragging = false; scrolling = false
                resetFocal(e)
                lastMoveTime = now
                if (!locked && !ignoreGesture &&
                    now - lastTapUpTime < doubleTapWindow &&
                    hypot(e.x - lastTapX, e.y - lastTapY) < touchSlop * 4
                ) {
                    dragging = true
                    listener?.onDragStart()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isPalm(e, e.actionIndex)) ignoreGesture = true
                maxPointers = maxOf(maxPointers, e.pointerCount)
                if (dragging) { dragging = false; listener?.onDragEnd() }
                resetFocal(e)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (ignoreGesture) return true
                if (e.pointerCount != focalCount) { resetFocal(e); return true }
                val (fx, fy) = focal(e)
                val dx = fx - focalX
                val dy = fy - focalY
                focalX = fx; focalY = fy
                totalDistance += hypot(dx, dy)
                val now = SystemClock.uptimeMillis()
                val dt = (now - lastMoveTime).coerceAtLeast(1L)
                lastMoveTime = now
                if (locked) return true
                when {
                    maxPointers == 1 -> listener?.onMove(dx, dy, hypot(dx, dy) / dt)
                    maxPointers == 2 -> {
                        if (!scrolling && totalDistance > touchSlop) { scrolling = true; listener?.onScrollStart() }
                        if (scrolling) listener?.onScroll(dx, dy)
                    }
                    else -> Unit // three fingers: decided on release
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                resetFocal(e, excludeIndex = e.actionIndex)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val cancelled = e.actionMasked == MotionEvent.ACTION_CANCEL
                val duration = SystemClock.uptimeMillis() - startTime
                val moved = totalDistance > touchSlop
                val dyTotal = e.y - startY
                val dxTotal = e.x - startX
                val pointers = maxPointers
                val wasDragging = dragging
                val wasScrolling = scrolling
                val ignored = ignoreGesture
                dragging = false; scrolling = false; ignoreGesture = false

                if (wasDragging) listener?.onDragEnd()
                if (wasScrolling) listener?.onScrollEnd()
                if (ignored || cancelled) return true

                if (pointers >= 3) {
                    if (!moved && duration < tapTimeout) {
                        listener?.onToggleLock()
                    } else if (!locked && maxOf(abs(dxTotal), abs(dyTotal)) > swipeMinPx) {
                        when {
                            abs(dyTotal) >= abs(dxTotal) && dyTotal > 0 -> listener?.onExit()
                            abs(dyTotal) >= abs(dxTotal) -> listener?.onOpenLauncher()
                            else -> listener?.onToggleKeyboard()
                        }
                    }
                    return true
                }
                if (locked) return true
                if (pointers == 1 && !wasDragging && !moved && duration < tapTimeout) registerTap(e)
                if (pointers == 2 && !moved && duration < tapTimeout + 150) listener?.onLongPress()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    /** Taps are reported through [Listener.onTap]; this satisfies the accessibility click contract. */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun registerTap(e: MotionEvent) {
        lastTapUpTime = SystemClock.uptimeMillis()
        lastTapX = e.x; lastTapY = e.y
        performClick()
        listener?.onTap()
    }

    private fun isPalm(e: MotionEvent, index: Int): Boolean {
        val major = e.getTouchMajor(index)
        return major > 0f && major > palmMajorPx
    }

    private fun focal(e: MotionEvent, excludeIndex: Int = -1): Pair<Float, Float> {
        var sx = 0f; var sy = 0f; var n = 0
        for (i in 0 until e.pointerCount) {
            if (i == excludeIndex) continue
            sx += e.getX(i); sy += e.getY(i); n++
        }
        return if (n == 0) e.x to e.y else (sx / n) to (sy / n)
    }

    private fun resetFocal(e: MotionEvent, excludeIndex: Int = -1) {
        val (fx, fy) = focal(e, excludeIndex)
        focalX = fx; focalY = fy
        focalCount = if (excludeIndex >= 0) e.pointerCount - 1 else e.pointerCount
        focalCount = min(focalCount, e.pointerCount)
    }

    companion object {
        private const val PALM_MM = 18f
        private const val SWIPE_MIN_DP = 64f
    }
}
