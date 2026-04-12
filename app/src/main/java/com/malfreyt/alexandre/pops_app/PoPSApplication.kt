package com.malfreyt.alexandre.pops_app

import android.app.Application
import com.malfreyt.alexandre.pops_app.data.AppContainer
import com.malfreyt.alexandre.pops_app.notifications.NotificationHelper
import com.malfreyt.alexandre.pops_app.sync.SyncScheduler

class PoPSApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)
        SyncScheduler.reschedule(this, container.settingsStore.readSettings())
    }
}