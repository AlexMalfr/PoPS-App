package com.malfreyt.alexandre.pops_app.data

import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID

enum class NoteChangeType {
    NONE,
    NEW,
    UPDATED,
}

data class OasisAccount(
    val id: String = UUID.randomUUID().toString(),
    val login: String = "",
    val password: String = "",
    val studentId: String = "",
    val displayName: String = "",
    val profilePhotoUrl: String? = null,
    val notificationsEnabled: Boolean = true,
    val notifyNewGrades: Boolean = true,
    val notifyPendingGrades: Boolean = true,
    val notifyUpdatedGrades: Boolean = true,
    val notifyErrors: Boolean = true,
    val pollingMinutes: Int = 30,
    val syncUnmeteredOnly: Boolean = false,
    val syncChargingOnly: Boolean = false,
    val lastSyncAt: Long? = null,
    val lastSyncSummary: String? = null,
    val lastSyncError: String? = null,
    val lastSyncErrorDetails: String? = null,
    val consecutiveFailureCount: Int = 0,
    val lastFailureNotificationDay: Long? = null,
    val failureNotificationActive: Boolean = false,
) {
    fun hasCredentials(): Boolean {
        return login.isNotBlank() && password.isNotBlank()
    }

    fun canSyncInBackground(): Boolean = hasCredentials() && pollingMinutes > 0

    fun resolvedStudentId(): String {
        return studentId.ifBlank { login.trim() }
    }

    fun resolvedDisplayName(): String {
        return displayName.ifBlank { resolvedStudentId() }
    }

    fun isNotificationEnabled(type: AccountNotificationType): Boolean {
        if (!notificationsEnabled) return false
        return when (type) {
            AccountNotificationType.NEW -> notifyNewGrades
            AccountNotificationType.PENDING -> notifyPendingGrades
            AccountNotificationType.UPDATED -> notifyUpdatedGrades
            AccountNotificationType.ERROR -> notifyErrors
        }
    }
}

enum class AccountNotificationType {
    NEW,
    PENDING,
    UPDATED,
    ERROR,
}

data class AppSettings(
    val accounts: List<OasisAccount> = emptyList(),
    val selectedAccountId: String? = null,
    val oasisBaseUrl: String = "https://polytech-saclay.oasis.aouka.org/",
    val notificationsEnabled: Boolean = true,
    val notifyNewGrades: Boolean = true,
    val notifyPendingGrades: Boolean = true,
    val notifyUpdatedGrades: Boolean = true,
    val notifyErrors: Boolean = true,
    val pollingMinutes: Int = 30,
    val ignoreTlsErrors: Boolean = false,
    val onboardingCompleted: Boolean = false,
) {
    fun hasAccounts(): Boolean {
        return accounts.isNotEmpty()
    }

    fun selectedAccountOrNull(): OasisAccount? {
        return accounts.firstOrNull { it.id == selectedAccountId } ?: accounts.firstOrNull()
    }

    fun selectedAccountCanSync(): Boolean {
        return selectedAccountOrNull()?.hasCredentials() == true && oasisBaseUrl.isNotBlank()
    }

    fun selectedAccountNotificationEnabled(type: AccountNotificationType): Boolean {
        val account = selectedAccountOrNull() ?: return false
        return account.isNotificationEnabled(type)
    }

    fun hasAnySyncableAccount(): Boolean {
        return accounts.any(OasisAccount::hasCredentials) && oasisBaseUrl.isNotBlank()
    }

    fun hasAnyBackgroundSyncAccount(): Boolean =
        oasisBaseUrl.isNotBlank() && accounts.any(OasisAccount::canSyncInBackground)

    fun editableEquals(other: AppSettings): Boolean {
        return oasisBaseUrl == other.oasisBaseUrl &&
            notificationsEnabled == other.notificationsEnabled &&
            notifyNewGrades == other.notifyNewGrades &&
            notifyPendingGrades == other.notifyPendingGrades &&
            notifyUpdatedGrades == other.notifyUpdatedGrades &&
            notifyErrors == other.notifyErrors &&
            pollingMinutes == other.pollingMinutes &&
            ignoreTlsErrors == other.ignoreTlsErrors
    }

    fun withRuntimeStateFrom(other: AppSettings): AppSettings {
        return copy(
            accounts = other.accounts,
            selectedAccountId = other.selectedAccountId,
        )
    }

    fun normalized(): AppSettings {
        val validSelectedAccountId = selectedAccountId?.takeIf { selectedId ->
            accounts.any { it.id == selectedId }
        } ?: accounts.firstOrNull()?.id
        return copy(selectedAccountId = validSelectedAccountId)
    }
}

data class RemoteAccountProfile(
    val studentId: String,
    val displayName: String,
    val profilePhotoUrl: String?,
    val profilePhotoBytes: ByteArray? = null,
)

data class RemoteGrade(
    val subjectId: String,
    val subject: String,
    val name: String,
    val grade: Double?,
    val dateText: String,
    val date: LocalDate?,
    val averageLabel: String,
    val rankLabel: String,
    val commentLabel: String,
    val coefficientLabel: String,
    val semester: Int,
    val academicYear: Int,
)

data class ModuleSummary(
    val groupCode: String,
    val groupTitle: String,
    val code: String,
    val title: String,
    val coefficientLabel: String,
    val blockLabel: String,
    val gradeLabel: String,
    val averageLabel: String,
    val rankLabel: String,
    val creditsLabel: String,
    val semester: Int,
    val academicYear: Int,
)

data class UnitSummary(
    val code: String,
    val title: String,
    val ectsLabel: String,
    val isCommonCore: Boolean,
    val gradeLabel: String,
    val averageLabel: String,
    val rankLabel: String,
    val resultLabel: String,
    val semester: Int,
    val academicYear: Int,
)

data class SemesterSnapshot(
    val accountId: String,
    val academicYear: Int,
    val semester: Int,
    val modules: List<ModuleSummary>,
    val units: List<UnitSummary>,
)

data class RemoteSemesterData(
    val academicYear: Int,
    val semester: Int,
    val exams: List<RemoteGrade>,
    val modules: List<ModuleSummary>,
    val units: List<UnitSummary>,
) {
    fun hasContent(): Boolean {
        return exams.isNotEmpty() || modules.isNotEmpty() || units.isNotEmpty()
    }

    fun toSnapshot(accountId: String): SemesterSnapshot {
        return SemesterSnapshot(
            accountId = accountId,
            academicYear = academicYear,
            semester = semester,
            modules = modules,
            units = units,
        )
    }
}

data class UiGrade(
    val id: String,
    val accountId: String,
    val subjectId: String,
    val subject: String,
    val name: String,
    val gradeLabel: String,
    val dateText: String,
    val averageLabel: String,
    val rankLabel: String,
    val commentLabel: String,
    val coefficientLabel: String,
    val semester: Int,
    val academicYear: Int,
    val changeType: NoteChangeType,
)

data class SyncChange(
    val accountId: String,
    val accountLabel: String,
    val type: NoteChangeType,
    val subject: String,
    val name: String,
    val gradeLabel: String,
    val gradePublished: Boolean,
    val changedFields: String? = null,
)

data class SyncReport(
    val accountId: String,
    val accountLabel: String,
    val changes: List<SyncChange>,
    val syncedCount: Int,
    val summary: String,
)

data class AccountSyncFailure(
    val accountId: String,
    val accountLabel: String,
    val message: String,
    val technicalDetails: String,
    val isNoInternet: Boolean,
)

data class BatchSyncReport(
    val changes: List<SyncChange>,
    val syncedAccountCount: Int,
    val failures: List<AccountSyncFailure>,
)

data class FailureNotificationState(
    val attempts: Int,
    val shouldOnlyAlertOnce: Boolean,
    val shortMessage: String,
    val technicalDetails: String,
)

data class StoredGradeEntity(
    val id: String,
    val accountId: String,
    val matchGroupKey: String,
    val contentFingerprint: String,
    val normalizedName: String,
    val subjectId: String,
    val subject: String,
    val name: String,
    val grade: Double?,
    val dateText: String,
    val dateEpochDay: Long?,
    val averageLabel: String,
    val rankLabel: String,
    val commentLabel: String,
    val coefficientLabel: String,
    val semester: Int,
    val academicYear: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val changeType: String,
    val active: Boolean,
)

fun StoredGradeEntity.toUiModel(): UiGrade {
    return UiGrade(
        id = id,
        accountId = accountId,
        subjectId = subjectId,
        subject = subject,
        name = name,
        gradeLabel = formatGrade(grade),
        dateText = dateText,
        averageLabel = averageLabel,
        rankLabel = rankLabel,
        commentLabel = commentLabel,
        coefficientLabel = coefficientLabel,
        semester = semester,
        academicYear = academicYear,
        changeType = NoteChangeType.valueOf(changeType),
    )
}

fun RemoteGrade.toStoredEntity(
    accountId: String,
    id: String = UUID.randomUUID().toString(),
    firstSeenAt: Long,
    lastSeenAt: Long,
    changeType: NoteChangeType,
): StoredGradeEntity {
    return StoredGradeEntity(
        id = id,
        accountId = accountId,
        matchGroupKey = buildMatchGroupKey(subjectId, academicYear, semester, dateText, date),
        contentFingerprint = buildContentFingerprint(),
        normalizedName = normalizeText(name),
        subjectId = subjectId,
        subject = subject,
        name = name,
        grade = grade,
        dateText = dateText,
        dateEpochDay = date?.toEpochDay(),
        averageLabel = averageLabel,
        rankLabel = rankLabel,
        commentLabel = commentLabel,
        coefficientLabel = coefficientLabel,
        semester = semester,
        academicYear = academicYear,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        changeType = changeType.name,
        active = true,
    )
}

fun RemoteGrade.buildContentFingerprint(): String {
    return sha256(
        listOf(
            subjectId,
            subject,
            name,
            grade?.toString() ?: "null",
            dateText,
            date?.toString() ?: "null",
            averageLabel,
            rankLabel,
            commentLabel,
            coefficientLabel,
            semester.toString(),
            academicYear.toString(),
        ).joinToString("|")
    )
}

fun buildMatchGroupKey(
    subjectId: String,
    academicYear: Int,
    semester: Int,
    dateText: String,
    date: LocalDate?,
): String {
    return listOf(
        subjectId.trim(),
        academicYear.toString(),
        semester.toString(),
        date?.toEpochDay()?.toString() ?: normalizeText(dateText),
    ).joinToString("|")
}

fun normalizeText(value: String): String {
    val normalized = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return normalized.replace("\\p{M}+".toRegex(), "").replace("\\s+".toRegex(), " ").trim()
}

fun formatGrade(grade: Double?): String {
    if (grade == null) {
        return "-"
    }
    return if (grade % 1.0 == 0.0) {
        grade.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", grade).trimEnd('0').trimEnd('.')
    }
}

fun parseFrenchDate(dateText: String): LocalDate? {
    val parts = dateText.trim().split(" ")
    if (parts.size < 3) {
        return null
    }
    val day = parts[0].toIntOrNull() ?: return null
    val month = monthIndex(parts[1]) ?: return null
    val year = parts[2].toIntOrNull() ?: return null
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

fun currentAcademicYear(): Int {
    val now = YearMonth.now()
    return if (now.monthValue < 9) now.year - 1 else now.year
}

private fun monthIndex(month: String): Int? {
    return mapOf(
        "janvier" to 1,
        "fevrier" to 2,
        "février" to 2,
        "mars" to 3,
        "avril" to 4,
        "mai" to 5,
        "juin" to 6,
        "juillet" to 7,
        "aout" to 8,
        "août" to 8,
        "septembre" to 9,
        "octobre" to 10,
        "novembre" to 11,
        "decembre" to 12,
        "décembre" to 12,
    )[month.lowercase(Locale.ROOT)]
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
