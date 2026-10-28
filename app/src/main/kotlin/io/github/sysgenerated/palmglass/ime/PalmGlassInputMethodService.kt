package io.github.sysgenerated.palmglass.ime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import io.github.sysgenerated.palmglass.core.Prefs
import io.github.sysgenerated.palmglass.core.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A keyboard with no on-screen UI. Text typed on the phone-side keyboard panel is committed
 * into whichever field has focus, on whichever display. Input methods run in the app process,
 * so the touchpad activity reaches this service through [KeyboardBridge].
 */
class PalmGlassInputMethodService : InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var handBackKeyboard = true

    override fun onCreate() {
        super.onCreate()
        scope.launch { Prefs(this@PalmGlassInputMethodService).settings.collectLatest { handBackKeyboard = it.handBackKeyboard } }
        KeyboardBridge.attach(this)
    }

    override fun onDestroy() {
        KeyboardBridge.detach(this)
        SessionState.setTextFieldFocused(false)
        scope.cancel()
        super.onDestroy()
    }

    /** Never show a keyboard on the glasses; the phone panel is the keyboard. */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return false
    }
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val isText = attribute != null &&
            (attribute.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_NULL
        SessionState.setTextFieldFocused(isText)
        if (isText && handBackKeyboard && !SessionState.touchpadActive.value) {
            // Touchpad is closed: hand the field back to the user's normal keyboard.
            switchToPreviousInputMethod()
        }
    }

    override fun onFinishInput() {
        SessionState.setTextFieldFocused(false)
        super.onFinishInput()
    }

    fun commit(text: CharSequence) {
        currentInputConnection?.commitText(text, 1)
    }

    fun backspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    fun enter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val multiline = (info?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (!multiline && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    fun key(keyCode: Int) = sendDownUpKeyEvents(keyCode)
}
