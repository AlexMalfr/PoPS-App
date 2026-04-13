package com.malfreyt.alexandre.pops_app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.malfreyt.alexandre.pops_app.data.AppSettings
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val WORK_NAME = "grades_sync_work"

    fun reschedule(context: Context, settings: AppSettings) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.hasAnySyncableAccount() || !settings.notificationsEnabled || settings.pollingMinutes == 0) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val intervalMinutes = maxOf(15L, settings.pollingMinutes.toLong())
        val request = PeriodicWorkRequestBuilder<GradesSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}