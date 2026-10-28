package io.github.sysgenerated.palmglass.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * A small accessibility-overlay window on the glasses display that draws the pointer.
 * The window is non-touchable and non-focusable, so it never interferes with input.
 */
class CursorOverlay(service: Context, display: Display, private val bounds: Rect) {
    private val windowContext: Context = service.createDisplayContext(display)
        .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
    private val wm = windowContext.getSystemService(WindowManager::class.java)
    private val density = windowContext.resources.displayMetrics.density
    private val sizePx = (CURSOR_DP * density).toInt()
    private val view = CursorView(windowContext)
    private val params = WindowManager.LayoutParams(
        sizePx, sizePx,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "PalmGlass cursor"
    }

    var x: Float = bounds.exactCenterX()
        private set
    var y: Float = bounds.exactCenterY()
        private set
    private var attached = false

    fun show() {
        if (attached) return
        params.x = x.toInt(); params.y = y.toInt()
        wm.addView(view, params)
        attached = true
    }

    fun hide() {
        if (!attached) return
        wm.removeViewImmediate(view)
        attached = false
    }

    fun moveBy(dx: Float, dy: Float) = moveTo(x + dx, y + dy)

    fun moveTo(nx: Float, ny: Float) {
        x = nx.coerceIn(bounds.left.toFloat(), (bounds.right - 1).toFloat())
        y = ny.coerceIn(bounds.top.toFloat(), (bounds.bottom - 1).toFloat())
        if (attached) {
            params.x = x.toInt(); params.y = y.toInt()
            wm.updateViewLayout(view, params)
        }
    }

    fun setLocked(locked: Boolean) { view.locked = locked; view.invalidate() }

    /** Hides the pointer without removing the window, so it can come back instantly. */
    fun setVisible(visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }
    fun setPressed(pressed: Boolean) { view.fingerDown = pressed; view.invalidate() }

    private class CursorView(context: Context) : View(context) {
        var locked = false
        var fingerDown = false
        private val d = context.resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f * d; strokeJoin = Paint.Join.ROUND
        }
        private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 170, 0) }
        private val arrow = Path()

        override fun onDraw(canvas: Canvas) {
            val s = 16f * d
            arrow.reset()
            arrow.moveTo(0f, 0f)
            arrow.lineTo(0f, s * 1.25f)
            arrow.lineTo(s * 0.35f, s * 0.95f)
            arrow.lineTo(s * 0.62f, s * 1.5f)
            arrow.lineTo(s * 0.82f, s * 1.4f)
            arrow.lineTo(s * 0.56f, s * 0.86f)
            arrow.lineTo(s, s * 0.86f)
            arrow.close()
            fill.color = if (fingerDown) Color.rgb(120, 200, 255) else Color.WHITE
            canvas.drawPath(arrow, fill)
            canvas.drawPath(arrow, stroke)
            if (locked) {
                val r = 4f * d
                canvas.drawCircle(s * 1.1f, s * 0.25f, r, badge)
                canvas.drawCircle(s * 1.1f, s * 0.25f, r, stroke)
            }
        }
    }

    companion object {
        private const val CURSOR_DP = 32
    }
}
