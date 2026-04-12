package com.malfreyt.alexandre.pops_app.sync

import android.content.Context
import java.time.LocalDate
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.malfreyt.alexandre.pops_app.PoPSApplication
import com.malfreyt.alexandre.pops_app.notifications.NotificationHelper

class GradesSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PoPSApplication).container
        return try {
            val report = container.gradeRepository.sync()
            val settings = container.gradeRepository.readSettings()
            if (settings.notificationsEnabled) {
                NotificationHelper.notifyChanges(applicationContext, settings, report.changes)
            }
            NotificationHelper.clearSyncFailureNotification(applicationContext)
            Result.success()
        } catch (error: Exception) {
            val failureState = container.gradeRepository.recordBackgroundFailure(error)
            val settings = container.gradeRepository.readSettings()
            if (settings.notificationsEnabled && settings.notifyErrors) {
                NotificationHelper.notifySyncFailure(
                    context = applicationContext,
                    attempts = failureState.attempts,
                    errorMessage = failureState.shortMessage,
                    technicalDetails = failureState.technicalDetails,
                    onlyAlertOnce = failureState.shouldOnlyAlertOnce,
                )
                container.settingsStore.markFailureNotificationShown(LocalDate.now().toEpochDay())
            }
            Result.retry()
        }
    }
}