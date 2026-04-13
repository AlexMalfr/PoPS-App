package com.malfreyt.alexandre.pops_app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.malfreyt.alexandre.pops_app.data.SettingsStore

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SYNC_FAILURE_DISMISSED) {
            val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID).orEmpty()
            if (accountId.isNotBlank()) {
                SettingsStore(context.applicationContext).markFailureNotificationDismissed(accountId)
            }
        }
    }

    companion object {
        const val ACTION_SYNC_FAILURE_DISMISSED = "com.malfreyt.alexandre.pops_app.notifications.SYNC_FAILURE_DISMISSED"
        const val EXTRA_ACCOUNT_ID = "account_id"
    }
}