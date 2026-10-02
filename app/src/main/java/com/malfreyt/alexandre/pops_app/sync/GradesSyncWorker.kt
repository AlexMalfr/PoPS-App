package com.malfreyt.alexandre.pops_app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.malfreyt.alexandre.pops_app.PoPSApplication
import com.malfreyt.alexandre.pops_app.data.NoInternetConnectionException
import com.malfreyt.alexandre.pops_app.notifications.NotificationHelper
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

class GradesSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PoPSApplication).container
        val accountId = inputData.getString(SyncScheduler.ACCOUNT_ID) ?: return Result.success()
        val initialSettings = container.gradeRepository.readSettings()
        val initialAccount = initialSettings.accounts.firstOrNull { it.id == accountId } ?: return Result.success()
        if (!initialAccount.canSyncInBackground() || initialSettings.oasisBaseUrl.isBlank()) return Result.success()

        return try {
            val report = container.gradeRepository.syncAccount(accountId)
            NotificationHelper.clearSyncFailureNotification(applicationContext, accountId)
            val latestSettings = container.gradeRepository.readSettings()
            val account = latestSettings.accounts.firstOrNull { it.id == accountId }
            if (account?.canSyncInBackground() == true && !isStopped) {
                NotificationHelper.notifyChanges(applicationContext, latestSettings, report.changes)
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: NoInternetConnectionException) {
            Result.success()
        } catch (error: Exception) {
            val settings = container.gradeRepository.readSettings()
            val account = settings.accounts.firstOrNull { it.id == accountId } ?: return Result.success()
            if (!account.canSyncInBackground() || isStopped) return Result.success()
            val failure = container.settingsStore.recordBackgroundFailure(
                accountId, error.message.orEmpty(), account.lastSyncErrorDetails.orEmpty(),
            )
            if (account.notificationsEnabled && account.notifyErrors) {
                NotificationHelper.notifySyncFailure(
                    context = applicationContext,
                    settings = settings,
                    account = account,
                    attempts = failure.attempts,
                    errorMessage = failure.shortMessage,
                    technicalDetails = failure.technicalDetails,
                    onlyAlertOnce = failure.shouldOnlyAlertOnce,
                )
                container.settingsStore.markFailureNotificationShown(accountId, LocalDate.now().toEpochDay())
            }
            Result.retry()
        }
    }
}
