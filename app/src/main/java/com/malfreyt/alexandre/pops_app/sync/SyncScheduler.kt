package com.malfreyt.alexandre.pops_app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.malfreyt.alexandre.pops_app.data.AppSettings
import com.malfreyt.alexandre.pops_app.data.OasisAccount
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val ACCOUNT_ID = "account_id"
    private const val LEGACY_WORK_NAME = "grades_sync_work"
    private const val SCHEDULED_IDS = "scheduled_account_ids"

    fun workName(accountId: String): String = "grades_sync_account_$accountId"

    fun constraintsFor(account: OasisAccount): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (account.syncUnmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(account.syncChargingOnly)
        .build()

    fun reschedule(context: Context, settings: AppSettings) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)
        val preferences = context.getSharedPreferences("pops_sync_scheduler", Context.MODE_PRIVATE)
        val previousIds = preferences.getStringSet(SCHEDULED_IDS, emptySet()).orEmpty()
        val accounts = settings.accounts.filter { settings.oasisBaseUrl.isNotBlank() && it.canSyncInBackground() }
        val nextIds = accounts.map { it.id }.toSet()
        (previousIds - nextIds).forEach { workManager.cancelUniqueWork(workName(it)) }
        accounts.forEach { account ->
            val request = PeriodicWorkRequestBuilder<GradesSyncWorker>(maxOf(15L, account.pollingMinutes.toLong()), TimeUnit.MINUTES)
                .setInputData(workDataOf(ACCOUNT_ID to account.id))
                .setConstraints(constraintsFor(account))
                .addTag("grades_sync_accounts")
                .build()
            workManager.enqueueUniquePeriodicWork(workName(account.id), ExistingPeriodicWorkPolicy.UPDATE, request)
        }
        preferences.edit().putStringSet(SCHEDULED_IDS, nextIds).apply()
    }
}
