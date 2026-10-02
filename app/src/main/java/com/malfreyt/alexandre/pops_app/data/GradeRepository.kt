package com.malfreyt.alexandre.pops_app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.malfreyt.alexandre.pops_app.R
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GradeRepository(
    private val context: Context,
    private val localStore: GradeLocalStore,
    private val settingsStore: SettingsStore,
    private val oasisRemoteDataSource: OasisRemoteDataSource,
) {
    fun observeGrades(): Flow<List<UiGrade>> {
        return localStore.observeActiveGrades().map { grades ->
            grades.map { it.toUiModel() }
        }
    }

    fun observeSemesterSnapshots(): Flow<List<SemesterSnapshot>> {
        return localStore.observeSemesterSnapshots()
    }

    fun readSettings(): AppSettings = settingsStore.readSettings()

    fun observeSettings() = settingsStore.observeSettings()

    fun saveSettings(settings: AppSettings) {
        settingsStore.saveSettings(settings)
    }

    suspend fun testConnection(settings: AppSettings, login: String, password: String) {
        require(login.isNotBlank() && password.isNotBlank() && settings.oasisBaseUrl.isNotBlank()) {
            context.getString(R.string.error_missing_credentials_url)
        }
        oasisRemoteDataSource.testConnection(settings, login, password)
    }

    fun saveAccount(account: OasisAccount, select: Boolean = false) {
        settingsStore.saveAccount(account, select)
    }

    fun selectAccount(accountId: String) {
        settingsStore.selectAccount(accountId)
    }

    suspend fun removeAccount(accountId: String) {
        deleteAccountPhoto(accountId)
        settingsStore.removeAccount(accountId)
        localStore.removeAccount(accountId)
    }

    suspend fun upsertAccount(existingAccountId: String?, login: String, password: String): OasisAccount {
        val settings = settingsStore.readSettings()
        val normalizedLogin = login.trim()
        require(normalizedLogin.isNotBlank() && password.isNotBlank() && settings.oasisBaseUrl.isNotBlank()) {
            context.getString(R.string.error_missing_credentials_url)
        }

        if (settings.accounts.any { it.id != existingAccountId && it.login.equals(normalizedLogin, ignoreCase = true) }) {
            throw IllegalStateException(context.getString(R.string.account_already_exists, normalizedLogin))
        }

        val profile = oasisRemoteDataSource.fetchAccountProfile(settings, normalizedLogin, password)
        val existing = existingAccountId?.let { accountId ->
            settings.accounts.firstOrNull { it.id == accountId }
        }
        val accountId = existing?.id ?: existingAccountId ?: java.util.UUID.randomUUID().toString()
        val profilePhotoUri = profile.profilePhotoBytes?.let { bytes ->
            saveAccountPhoto(accountId, bytes)
        } ?: existing?.profilePhotoUrl

        val account = (existing ?: OasisAccount(id = accountId)).copy(
            login = normalizedLogin,
            password = password,
            studentId = profile.studentId,
            displayName = profile.displayName,
            profilePhotoUrl = profilePhotoUri,
        )

        settingsStore.saveAccount(account, select = true)
        return account
    }

    suspend fun syncSelectedAccount(): SyncReport {
        val settings = settingsStore.readSettings()
        val selectedAccount = settings.selectedAccountOrNull()
            ?: throw IllegalStateException(context.getString(R.string.error_missing_credentials_url))
        return syncAccount(selectedAccount.id, settings)
    }

    suspend fun syncAccount(accountId: String): SyncReport = syncAccount(accountId, settingsStore.readSettings())

    suspend fun clearSelectedAccountCache() {
        val settings = settingsStore.readSettings()
        val selectedAccount = settings.selectedAccountOrNull() ?: return
        localStore.removeAccount(selectedAccount.id)
        settingsStore.resetSyncState(selectedAccount.id)
    }

    fun cancelActiveSync() {
        oasisRemoteDataSource.cancelOngoingRequests()
    }

    suspend fun syncAllAccounts(): BatchSyncReport {
        val settings = settingsStore.readSettings()
        if (!settings.hasAnySyncableAccount()) {
            return BatchSyncReport(changes = emptyList(), syncedAccountCount = 0, failures = emptyList())
        }

        val changes = mutableListOf<SyncChange>()
        val failures = mutableListOf<AccountSyncFailure>()
        var syncedAccountCount = 0

        settings.accounts.filter(OasisAccount::hasCredentials).forEach { account ->
            try {
                val report = syncAccount(account.id, settings)
                syncedAccountCount += 1
                changes += report.changes
            } catch (error: Exception) {
                Log.e(TAG, "Background sync failed for ${account.resolvedDisplayName()}", error)
                failures += AccountSyncFailure(
                    accountId = account.id,
                    accountLabel = account.resolvedDisplayName(),
                    message = error.message ?: context.getString(R.string.error_oasis_sync_generic),
                    technicalDetails = error.toTechnicalDetails(),
                    isNoInternet = error is NoInternetConnectionException,
                )
            }
        }

        return BatchSyncReport(
            changes = changes,
            syncedAccountCount = syncedAccountCount,
            failures = failures,
        )
    }

    private suspend fun syncAccount(accountId: String, settingsSnapshot: AppSettings): SyncReport {
        val account = settingsSnapshot.accounts.firstOrNull { it.id == accountId }
            ?: throw IllegalStateException(context.getString(R.string.error_missing_credentials_url))
        require(account.hasCredentials() && settingsSnapshot.oasisBaseUrl.isNotBlank()) {
            context.getString(R.string.error_missing_credentials_url)
        }

        return try {
            Log.i(TAG, "Starting sync for ${account.resolvedDisplayName()} against ${settingsSnapshot.oasisBaseUrl}")

            val remoteSemesters = oasisRemoteDataSource.fetchSemesters(settingsSnapshot, account)
            val remoteGrades = remoteSemesters.flatMap { semester ->
                val coefficientsByModuleCode = semester.modules.associateBy { it.code }
                semester.exams.map { exam ->
                    exam.copy(
                        coefficientLabel = coefficientsByModuleCode[exam.subjectId]?.coefficientLabel ?: exam.coefficientLabel,
                    )
                }
            }.sortedWith(
                compareByDescending<RemoteGrade> { it.academicYear }
                    .thenByDescending { it.semester }
                    .thenByDescending { it.date ?: LocalDate.MIN }
                    .thenBy { it.subject }
                    .thenBy { it.name }
            )

            val now = System.currentTimeMillis()
            val existing = localStore.getActiveGrades(account.id)
            val reconcileResult = reconcile(account, existing, remoteGrades, now, isInitialSync = account.lastSyncAt == null)

            localStore.replaceAllForAccount(account.id, reconcileResult.persisted)
            localStore.replaceSemesterSnapshots(account.id, remoteSemesters.map { it.toSnapshot(account.id) })

            val summary = buildSummary(reconcileResult.changes, remoteGrades.size)
            settingsStore.saveSyncSuccess(account.id, summary, now)
            Log.i(TAG, "Sync succeeded for ${account.resolvedDisplayName()}: $summary")
            SyncReport(
                accountId = account.id,
                accountLabel = account.resolvedDisplayName(),
                changes = reconcileResult.changes,
                syncedCount = remoteGrades.size,
                summary = summary,
            )
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) {
                throw error
            }
            settingsStore.saveSyncError(
                accountId = account.id,
                message = error.message ?: context.getString(R.string.error_oasis_sync_generic),
                technicalDetails = error.toTechnicalDetails(),
            )
            throw error
        }
    }

    private fun reconcile(
        account: OasisAccount,
        existing: List<StoredGradeEntity>,
        remoteGrades: List<RemoteGrade>,
        now: Long,
        isInitialSync: Boolean,
    ): ReconcileResult {
        val remaining = existing.groupBy { it.matchGroupKey }
            .mapValues { (_, values) -> values.toMutableList() }
            .toMutableMap()

        val persisted = mutableListOf<StoredGradeEntity>()
        val changes = mutableListOf<SyncChange>()

        for (remote in remoteGrades) {
            val groupKey = buildMatchGroupKey(remote.subjectId, remote.academicYear, remote.semester, remote.dateText, remote.date)
            val candidates = remaining.getOrPut(groupKey) { mutableListOf() }
            val match = findBestMatch(remote, candidates)

            if (match == null) {
                val changeType = if (isInitialSync) NoteChangeType.NONE else NoteChangeType.NEW
                persisted += remote.toStoredEntity(
                    accountId = account.id,
                    firstSeenAt = now,
                    lastSeenAt = now,
                    changeType = changeType,
                )
                if (!isInitialSync) {
                    changes += SyncChange(
                        accountId = account.id,
                        accountLabel = account.resolvedDisplayName(),
                        type = NoteChangeType.NEW,
                        subject = remote.subject,
                        name = remote.name,
                        gradeLabel = formatGrade(remote.grade),
                        gradePublished = remote.grade != null,
                    )
                }
            } else {
                candidates.remove(match)
                val changed = match.contentFingerprint != remote.buildContentFingerprint()
                persisted += remote.toStoredEntity(
                    accountId = account.id,
                    id = match.id,
                    firstSeenAt = match.firstSeenAt,
                    lastSeenAt = now,
                    changeType = if (changed) NoteChangeType.UPDATED else NoteChangeType.NONE,
                )
                if (changed) {
                    changes += SyncChange(
                        accountId = account.id,
                        accountLabel = account.resolvedDisplayName(),
                        type = NoteChangeType.UPDATED,
                        subject = remote.subject,
                        name = remote.name,
                        gradeLabel = formatGrade(remote.grade),
                        gradePublished = remote.grade != null,
                        changedFields = describeChanges(match, remote),
                    )
                }
            }
        }

        remaining.values.flatten().forEach { leftover ->
            persisted += leftover.copy(active = false)
        }

        return ReconcileResult(persisted = persisted, changes = changes)
    }

    private fun describeChanges(old: StoredGradeEntity, new: RemoteGrade): String {
        val parts = mutableListOf<String>()
        val oldGrade = formatGrade(old.grade)
        val newGrade = formatGrade(new.grade)
        if (oldGrade != newGrade) parts += context.getString(R.string.notification_change_grade, oldGrade, newGrade)
        if (old.averageLabel != new.averageLabel) parts += context.getString(R.string.notification_change_average, old.averageLabel, new.averageLabel)
        if (old.rankLabel != new.rankLabel) parts += context.getString(R.string.notification_change_rank, old.rankLabel, new.rankLabel)
        if (old.coefficientLabel != new.coefficientLabel) parts += context.getString(R.string.notification_change_coeff, old.coefficientLabel, new.coefficientLabel)
        if (old.commentLabel != new.commentLabel) parts += context.getString(R.string.notification_change_comment)
        return parts.joinToString(", ").ifEmpty { context.getString(R.string.notification_change_details) }
    }

    private fun findBestMatch(remote: RemoteGrade, candidates: List<StoredGradeEntity>): StoredGradeEntity? {
        if (candidates.isEmpty()) {
            return null
        }
        val fingerprint = remote.buildContentFingerprint()
        val exactFingerprint = candidates.firstOrNull { it.contentFingerprint == fingerprint }
        if (exactFingerprint != null) {
            return exactFingerprint
        }
        val normalizedName = normalizeText(remote.name)
        val exactName = candidates.firstOrNull { it.normalizedName == normalizedName }
        if (exactName != null) {
            return exactName
        }
        val sameGrade = candidates.firstOrNull { it.grade == remote.grade }
        if (sameGrade != null) {
            return sameGrade
        }
        return candidates.singleOrNull()
    }

    private fun buildSummary(changes: List<SyncChange>, totalCount: Int): String {
        if (changes.isEmpty()) {
            return context.getString(R.string.sync_summary_no_change, totalCount)
        }
        val newCount = changes.count { it.type == NoteChangeType.NEW }
        val updatedCount = changes.count { it.type == NoteChangeType.UPDATED }
        return context.getString(R.string.sync_summary_changes, totalCount, newCount, updatedCount)
    }

    private data class ReconcileResult(
        val persisted: List<StoredGradeEntity>,
        val changes: List<SyncChange>,
    )

    private fun Throwable.toTechnicalDetails(): String {
        return buildString {
            append(this@toTechnicalDetails::class.java.simpleName)
            this@toTechnicalDetails.message?.let {
                append(": ")
                append(it)
            }
            this@toTechnicalDetails.cause?.let { cause ->
                append("\nCause: ")
                append(cause::class.java.simpleName)
                cause.message?.let {
                    append(": ")
                    append(it)
                }
            }
        }
    }

    private fun saveAccountPhoto(accountId: String, bytes: ByteArray): String {
        val file = accountPhotoFile(accountId)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return Uri.fromFile(file).toString()
    }

    private fun deleteAccountPhoto(accountId: String) {
        accountPhotoFile(accountId).delete()
    }

    private fun accountPhotoFile(accountId: String): File {
        return File(context.filesDir, "account_photos/$accountId.png")
    }

    private companion object {
        private const val TAG = "PoPS-Repository"
    }
}
