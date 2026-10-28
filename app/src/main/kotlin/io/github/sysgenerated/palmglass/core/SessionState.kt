package io.github.sysgenerated.palmglass.core

import android.view.Display
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide session state shared by the accessibility service, the input method,
 * the touchpad activity and the setup screen. All three components run in this process.
 */
object SessionState {
    private val _externalDisplayId = MutableStateFlow(Display.INVALID_DISPLAY)
    /** Display id of the glasses, or [Display.INVALID_DISPLAY] when none is connected. */
    val externalDisplayId: StateFlow<Int> = _externalDisplayId.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _touchpadActive = MutableStateFlow(false)
    /** True while the touchpad activity is in the foreground. */
    val touchpadActive: StateFlow<Boolean> = _touchpadActive.asStateFlow()

    private val _inputLocked = MutableStateFlow(false)
    /** True when touches are ignored (pocket mode). Toggled by a three-finger tap. */
    val inputLocked: StateFlow<Boolean> = _inputLocked.asStateFlow()

    private val _textFieldFocused = MutableStateFlow(false)
    /** Reported by the input method when a text field on any display wants input. */
    val textFieldFocused: StateFlow<Boolean> = _textFieldFocused.asStateFlow()

    private val _stopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopRequests: SharedFlow<Unit> = _stopRequests.asSharedFlow()

    fun setExternalDisplay(id: Int) { _externalDisplayId.value = id }
    fun setServiceRunning(running: Boolean) { _serviceRunning.value = running }
    fun setTouchpadActive(active: Boolean) {
        _touchpadActive.value = active
        if (!active) _inputLocked.value = false
    }
    fun setInputLocked(locked: Boolean) { _inputLocked.value = locked }
    fun toggleInputLocked(): Boolean {
        _inputLocked.value = !_inputLocked.value
        return _inputLocked.value
    }
    fun setTextFieldFocused(focused: Boolean) { _textFieldFocused.value = focused }
    fun requestStop() { _stopRequests.tryEmit(Unit) }

    val hasExternalDisplay: Boolean get() = _externalDisplayId.value != Display.INVALID_DISPLAY
}
