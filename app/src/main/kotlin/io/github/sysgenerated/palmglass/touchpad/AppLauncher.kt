package io.github.sysgenerated.palmglass.touchpad

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.Toast

data class LaunchableApp(val label: String, val component: ComponentName, val icon: Drawable)

object AppLauncher {
    private const val TAG = "AppLauncher"

    fun list(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                LaunchableApp(
                    label = it.loadLabel(pm).toString(),
                    component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    icon = it.loadIcon(pm),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Opens the app on [displayId]; falls back to the default display if the system refuses. */
    fun launch(context: Context, app: LaunchableApp, displayId: Int) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            context.startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Log.w(TAG, "launch on display $displayId refused, opening on phone", e)
            Toast.makeText(context, "Could not open on glasses; opened on phone", Toast.LENGTH_SHORT).show()
            runCatching { context.startActivity(intent) }
        }
    }
}
