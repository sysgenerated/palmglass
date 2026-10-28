package io.github.sysgenerated.palmglass.ime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hands keystrokes from the touchpad activity to the input method, when it is the active keyboard. */
object KeyboardBridge {
    @Volatile
    private var sink: PalmGlassInputMethodService? = null

    private val _active = MutableStateFlow(false)
    /** True while PalmGlass is the system's current input method. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun attach(service: PalmGlassInputMethodService) { sink = service; _active.value = true }
    fun detach(service: PalmGlassInputMethodService) {
        if (sink === service) { sink = null; _active.value = false }
    }

    fun type(text: CharSequence) { sink?.commit(text) }
    fun backspace() { sink?.backspace() }
    fun enter() { sink?.enter() }
    fun key(keyCode: Int) { sink?.key(keyCode) }
}
