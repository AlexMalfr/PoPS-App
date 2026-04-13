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
        val settings = container.gradeRepository.readSettings()
        val syncableAccounts = settings.accounts.filter { it.hasCredentials() }

        return try {
            val report = container.gradeRepository.syncAllAccounts()
            val failedAccountIds = report.failures.map { it.accountId }.toSet()

            syncableAccounts
                .map { it.id }
                .filterNot(failedAccountIds::contains)
                .forEach { accountId ->
                    NotificationHelper.clearSyncFailureNotification(applicationContext, accountId)
                }

            if (settings.notificationsEnabled) {
                NotificationHelper.notifyChanges(applicationContext, settings, report.changes)
            }

            val blockingFailures = report.failures.filterNot { it.isNoInternet }
            if (blockingFailures.isEmpty()) {
                return Result.success()
            }

            blockingFailures.forEach { failure ->
                val failureState = container.settingsStore.recordBackgroundFailure(
                    accountId = failure.accountId,
                    message = failure.message,
                    technicalDetails = failure.technicalDetails,
                )
                val account = container.settingsStore.readSettings().accounts.firstOrNull { it.id == failure.accountId } ?: return@forEach
                if (settings.notificationsEnabled && settings.notifyErrors) {
                    NotificationHelper.notifySyncFailure(
                        context = applicationContext,
                        account = account,
                        attempts = failureState.attempts,
                        errorMessage = failureState.shortMessage,
                        technicalDetails = failureState.technicalDetails,
                        onlyAlertOnce = failureState.shouldOnlyAlertOnce,
                    )
                    container.settingsStore.markFailureNotificationShown(failure.accountId, LocalDate.now().toEpochDay())
                }
            }

            Result.retry()
        } catch (error: Exception) {
            Result.retry()
        }
    }
}