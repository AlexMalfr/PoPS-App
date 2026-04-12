package com.malfreyt.alexandre.pops_app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.malfreyt.alexandre.pops_app.data.SettingsStore

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SYNC_FAILURE_DISMISSED) {
            SettingsStore(context.applicationContext).markFailureNotificationDismissed()
        }
    }

    companion object {
        const val ACTION_SYNC_FAILURE_DISMISSED = "com.malfreyt.alexandre.pops_app.notifications.SYNC_FAILURE_DISMISSED"
    }
}