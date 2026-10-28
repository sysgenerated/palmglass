package io.github.sysgenerated.palmglass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.sysgenerated.palmglass.core.SessionState

/** Handles the Stop action on the session notification. */
class SessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) SessionState.requestStop()
    }

    companion object {
        const val ACTION_STOP = "io.github.sysgenerated.palmglass.action.STOP"
    }
}
