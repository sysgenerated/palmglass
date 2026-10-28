package io.github.sysgenerated.palmglass.touchpad

import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import io.github.sysgenerated.palmglass.core.Prefs
import io.github.sysgenerated.palmglass.core.SessionState
import io.github.sysgenerated.palmglass.core.Settings
import io.github.sysgenerated.palmglass.ime.KeyboardBridge
import io.github.sysgenerated.palmglass.service.PalmGlassAccessibilityService
import io.github.sysgenerated.palmglass.ui.PalmGlassTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * The black full-screen touchpad. The window is kept awake, driven at minimum brightness, and
 * made non-focusable so that gamepad and keyboard focus stays with the window on the glasses.
 */
class TouchpadActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private var settings = Settings()

    private var showLauncher by mutableStateOf(false)
    private var keyboardPinned by mutableStateOf(false)
    private var locked by mutableStateOf(false)
    private var fieldFocused by mutableStateOf(false)
    private var keyboardActive by mutableStateOf(false)
    private var panelVisible = false
    private lateinit var touchpadView: TouchpadView

    private val service: PalmGlassAccessibilityService? get() = PalmGlassAccessibilityService.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        touchpadView = TouchpadView(this).apply { listener = gestureListener }

        setContent {
            val showKeyboard = keyboardPinned || fieldFocused
            LaunchedEffect(showLauncher, showKeyboard) {
                panelVisible = showLauncher || showKeyboard
                applyBrightness()
            }
            PalmGlassTheme {
                TouchpadScreen(
                    touchpadView = touchpadView,
                    locked = locked,
                    showKeyboard = showKeyboard,
                    keyboardActive = keyboardActive,
                    showLauncher = showLauncher,
                    onDismissLauncher = { showLauncher = false },
                    onLauncherToKeyboard = { showLauncher = false; keyboardPinned = true },
                    onDismissKeyboard = { keyboardPinned = false },
                    onChooseKeyboard = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
                    onLaunchApp = { app ->
                        showLauncher = false
                        AppLauncher.launch(this, app, SessionState.externalDisplayId.value)
                    },
                    onExit = { finish() },
                )
            }
        }

        lifecycleScope.launch { prefs.settings.collectLatest { settings = it; applyBrightness() } }
        lifecycleScope.launch { SessionState.stopRequests.collectLatest { finish() } }
        lifecycleScope.launch {
            SessionState.inputLocked.collectLatest { locked = it; touchpadView.locked = it }
        }
        lifecycleScope.launch { SessionState.textFieldFocused.collectLatest { fieldFocused = it } }
        lifecycleScope.launch { KeyboardBridge.active.collectLatest { keyboardActive = it } }
    }

    override fun onResume() {
        super.onResume()
        SessionState.setTouchpadActive(true)
        applyBrightness()
    }

    override fun onPause() {
        service?.releaseFinger()
        SessionState.setTouchpadActive(false)
        super.onPause()
    }

    private fun applyBrightness() {
        window.attributes = window.attributes.apply {
            // Panels need to be readable; the bare touchpad does not.
            screenBrightness = if (settings.dimScreen && !panelVisible) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF // lowest brightness, not off
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    private val gestureListener = object : TouchpadView.Listener {
        override fun onMove(dx: Float, dy: Float, speed: Float) {
            // speed is px/ms on the phone; mild acceleration from 1x at rest to 2.5x when flicking.
            val accel = 1f + min(speed, 3f) / 3f * 1.5f
            val gain = BASE_GAIN * settings.sensitivity * accel
            service?.moveCursor(dx * gain, dy * gain)
        }
        override fun onTap() { service?.tap() }
        override fun onLongPress() { service?.longPress() }
        override fun onDragStart() { service?.dragStart() }
        override fun onDragEnd() { service?.releaseFinger() }
        override fun onScrollStart() { service?.scrollStart() }
        override fun onScroll(dx: Float, dy: Float) {
            val sign = if (settings.naturalScroll) 1f else -1f
            service?.scrollBy(dx * SCROLL_GAIN * sign, dy * SCROLL_GAIN * sign)
        }
        override fun onScrollEnd() { service?.releaseFinger() }
        override fun onToggleLock() {
            val locked = SessionState.toggleInputLocked()
            // Two pulses when locking, one when unlocking: felt in the hand, nothing on screen.
            buzz(if (locked) VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_CLICK)
        }
        override fun onExit() { finish() }
        override fun onToggleKeyboard() { keyboardPinned = !keyboardPinned }
        override fun onOpenLauncher() { showLauncher = true }
    }

    private fun buzz(effect: Int) {
        runCatching {
            getSystemService(VibratorManager::class.java).defaultVibrator
                .vibrate(VibrationEffect.createPredefined(effect))
        }
    }

    companion object {
        private const val BASE_GAIN = 1.6f
        private const val SCROLL_GAIN = 1.2f
    }
}
