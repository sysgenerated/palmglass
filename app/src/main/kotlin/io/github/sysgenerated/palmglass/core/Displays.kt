package io.github.sysgenerated.palmglass.core

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display

object Displays {
    private const val TAG = "Displays"

    /**
     * Finds the glasses. Prefers displays Android flags as presentation-capable (wired external
     * displays always are), then falls back to any powered-on non-default display.
     */
    fun findExternal(dm: DisplayManager): Display? {
        val presentation = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF }
        if (presentation != null) return presentation
        return dm.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY && it.state != Display.STATE_OFF
        }
    }

    /**
     * Full bounds of [display] in its own logical pixels.
     *
     * Reads the display's own metrics, which are tied to that display and already account for
     * rotation. WindowManager metrics obtained through a context proved unreliable from a
     * service (they could report the phone's size), and the physical mode ignores rotation and
     * any logical scaling, so neither is used as a source any more.
     */
    @Suppress("DEPRECATION")
    fun bounds(display: Display): Rect {
        val metrics = android.util.DisplayMetrics()
        display.getRealMetrics(metrics)
        val logical = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        val mode = display.mode
        Log.i(
            TAG,
            "display ${display.displayId} '${display.name}' logical=$logical " +
                "physical=${mode.physicalWidth}x${mode.physicalHeight} rotation=${display.rotation}",
        )
        return logical
    }
}
