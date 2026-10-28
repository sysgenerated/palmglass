package io.github.sysgenerated.palmglass

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class PalmGlassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                SESSION_CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
    }

    companion object {
        const val SESSION_CHANNEL_ID = "session"
    }
}
