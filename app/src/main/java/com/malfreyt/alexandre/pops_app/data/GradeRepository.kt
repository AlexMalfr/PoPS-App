package com.malfreyt.alexandre.pops_app.data

import android.content.Context
import android.util.Log
import com.malfreyt.alexandre.pops_app.R
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

    suspend fun testConnection(settings: AppSettings) {
        require(settings.canSync()) { context.getString(R.string.error_missing_credentials_url) }
        oasisRemoteDataSource.testConnection(settings)
    }

    fun saveSyncError(message: String, technicalDetails: String) {
        settingsStore.saveSyncError(message, technicalDetails)
    }

    fun recordBackgroundFailure(error: Throwable): FailureNotificationState {
        val message = error.message ?: context.getString(R.string.error_oasis_sync_generic)
        val details = error.toTechnicalDetails()
        Log.e(TAG, "Background sync failed", error)
        return settingsStore.recordBackgroundFailure(message, details)
    }

    suspend fun sync(settingsOverride: AppSettings? = null): SyncReport {
        val settings = settingsOverride ?: settingsStore.readSettings()
        require(settings.canSync()) { context.getString(R.string.error_missing_credentials_url) }

        Log.i(TAG, "Starting sync against ${settings.oasisBaseUrl}")

        val remoteSemesters = oasisRemoteDataSource.fetchSemesters(settings)
        val remoteGrades = remoteSemesters.flatMap { it.exams }.sortedWith(
            compareByDescending<RemoteGrade> { it.academicYear }
                .thenByDescending { it.semester }
                .thenByDescending { it.date ?: LocalDate.MIN }
                .thenBy { it.subject }
                .thenBy { it.name }
        )

        val now = System.currentTimeMillis()
        val existing = localStore.getActiveGrades()
        val reconcileResult = reconcile(existing, remoteGrades, now)

        localStore.replaceAll(reconcileResult.persisted)
        localStore.replaceSemesterSnapshots(remoteSemesters.map(RemoteSemesterData::toSnapshot))

        val summary = buildSummary(reconcileResult.changes, remoteGrades.size)
        settingsStore.saveSyncSuccess(summary, now)
        Log.i(TAG, "Sync succeeded: $summary")
        return SyncReport(
            changes = reconcileResult.changes,
            syncedCount = remoteGrades.size,
            summary = summary,
        )
    }

    private fun reconcile(
        existing: List<StoredGradeEntity>,
        remoteGrades: List<RemoteGrade>,
        now: Long,
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
                persisted += remote.toStoredEntity(
                    firstSeenAt = now,
                    lastSeenAt = now,
                    changeType = NoteChangeType.NEW,
                )
                changes += SyncChange(
                    type = NoteChangeType.NEW,
                    subject = remote.subject,
                    name = remote.name,
                    gradeLabel = formatGrade(remote.grade),
                    gradePublished = remote.grade != null,
                )
            } else {
                candidates.remove(match)
                val changed = match.contentFingerprint != remote.buildContentFingerprint()
                persisted += remote.toStoredEntity(
                    id = match.id,
                    firstSeenAt = match.firstSeenAt,
                    lastSeenAt = now,
                    changeType = if (changed) NoteChangeType.UPDATED else NoteChangeType.NONE,
                )
                if (changed) {
                    changes += SyncChange(
                        type = NoteChangeType.UPDATED,
                        subject = remote.subject,
                        name = remote.name,
                        gradeLabel = formatGrade(remote.grade),
                        gradePublished = remote.grade != null,
                    )
                }
            }
        }

        remaining.values.flatten().forEach { leftover ->
            persisted += leftover.copy(active = false)
        }

        return ReconcileResult(persisted = persisted, changes = changes)
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

    private companion object {
        private const val TAG = "PoPS-Repository"
    }
}