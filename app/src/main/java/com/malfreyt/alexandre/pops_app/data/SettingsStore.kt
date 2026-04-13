package com.malfreyt.alexandre.pops_app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val preferences: SharedPreferences
    private val settingsFlow: MutableStateFlow<AppSettings>

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        preferences = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        settingsFlow = MutableStateFlow(readSettingsFromPreferences())
    }

    fun readSettings(): AppSettings {
        return settingsFlow.value
    }

    fun observeSettings(): StateFlow<AppSettings> {
        return settingsFlow.asStateFlow()
    }

    fun saveSettings(settings: AppSettings) {
        persist(settings.normalized())
    }

    fun saveAccount(account: OasisAccount, select: Boolean = false) {
        val normalizedAccount = account.normalized()
        mutateSettings { current ->
            val accounts = current.accounts.toMutableList()
            val existingIndex = accounts.indexOfFirst { it.id == normalizedAccount.id }
            if (existingIndex >= 0) {
                accounts[existingIndex] = normalizedAccount
            } else {
                accounts += normalizedAccount
            }

            current.copy(
                accounts = accounts,
                selectedAccountId = when {
                    select -> normalizedAccount.id
                    current.selectedAccountId == null -> normalizedAccount.id
                    else -> current.selectedAccountId
                },
            )
        }
    }

    fun removeAccount(accountId: String) {
        mutateSettings { current ->
            current.copy(
                accounts = current.accounts.filterNot { it.id == accountId },
                selectedAccountId = current.selectedAccountId?.takeUnless { it == accountId },
            )
        }
    }

    fun selectAccount(accountId: String) {
        mutateSettings { current ->
            current.copy(selectedAccountId = accountId)
        }
    }

    fun saveSyncSuccess(accountId: String, summary: String, timestamp: Long) {
        mutateSettings { current ->
            current.copy(
                accounts = current.accounts.map { account ->
                    if (account.id != accountId) {
                        account
                    } else {
                        account.copy(
                            lastSyncAt = timestamp,
                            lastSyncSummary = summary,
                            lastSyncError = null,
                            lastSyncErrorDetails = null,
                            consecutiveFailureCount = 0,
                            lastFailureNotificationDay = null,
                            failureNotificationActive = false,
                        )
                    }
                }
            )
        }
    }

    fun saveSyncError(accountId: String, message: String, technicalDetails: String) {
        mutateSettings { current ->
            current.copy(
                accounts = current.accounts.map { account ->
                    if (account.id != accountId) account else account.copy(
                        lastSyncError = message,
                        lastSyncErrorDetails = technicalDetails,
                    )
                }
            )
        }
    }

    fun resetSyncState(accountId: String) {
        mutateSettings { current ->
            current.copy(
                accounts = current.accounts.map { account ->
                    if (account.id != accountId) account else account.copy(
                        lastSyncAt = null,
                        lastSyncSummary = null,
                        lastSyncError = null,
                        lastSyncErrorDetails = null,
                        consecutiveFailureCount = 0,
                        lastFailureNotificationDay = null,
                        failureNotificationActive = false,
                    )
                }
            )
        }
    }

    fun updateAccountProfile(accountId: String, profile: RemoteAccountProfile) {
        mutateSettings { current ->
            current.copy(
                accounts = current.accounts.map { account ->
                    if (account.id != accountId) {
                        account
                    } else {
                        account.copy(
                            studentId = profile.studentId.ifBlank { account.resolvedStudentId() },
                            displayName = profile.displayName.ifBlank { account.displayName },
                            profilePhotoUrl = profile.profilePhotoUrl ?: account.profilePhotoUrl,
                        ).normalized()
                    }
                }
            )
        }
    }

    fun recordBackgroundFailure(accountId: String, message: String, technicalDetails: String): FailureNotificationState {
        val current = readSettings()
        val account = current.accounts.firstOrNull { it.id == accountId }
        val attempts = (account?.consecutiveFailureCount ?: 0) + 1
        val today = java.time.LocalDate.now().toEpochDay()
        val shouldOnlyAlertOnce = account?.lastFailureNotificationDay == today && account.failureNotificationActive

        mutateSettings { settings ->
            settings.copy(
                accounts = settings.accounts.map { item ->
                    if (item.id != accountId) {
                        item
                    } else {
                        item.copy(
                            lastSyncError = message,
                            lastSyncErrorDetails = technicalDetails,
                            consecutiveFailureCount = attempts,
                        )
                    }
                }
            )
        }

        return FailureNotificationState(
            attempts = attempts,
            shouldOnlyAlertOnce = shouldOnlyAlertOnce,
            shortMessage = message,
            technicalDetails = technicalDetails,
        )
    }

    fun markFailureNotificationShown(accountId: String, epochDay: Long) {
        mutateSettings { current ->
            current.copy(
                accounts = current.accounts.map { account ->
                    if (account.id != accountId) account else account.copy(
                        lastFailureNotificationDay = epochDay,
                        failureNotificationActive = true,
                    )
                }
            )
        }
    }

    fun markFailureNotificationDismissed(accountId: String) {
        mutateSettings { current ->
            current.copy(
                accounts = current.accounts.map { account ->
                    if (account.id != accountId) account else account.copy(failureNotificationActive = false)
                }
            )
        }
    }

    private fun readSettingsFromPreferences(): AppSettings {
        val jsonText = preferences.getString(KEY_SETTINGS_JSON, null) ?: return AppSettings()
        val json = runCatching { JSONObject(jsonText) }.getOrElse { return AppSettings() }
        return AppSettings(
            accounts = json.optJSONArray("accounts").toAccounts(),
            selectedAccountId = json.optString("selectedAccountId").takeIf { it.isNotBlank() },
            oasisBaseUrl = json.optString("oasisBaseUrl", DEFAULT_OASIS_BASE_URL).ifBlank { DEFAULT_OASIS_BASE_URL },
            notificationsEnabled = json.optBoolean("notificationsEnabled", true),
            notifyNewGrades = json.optBoolean("notifyNewGrades", true),
            notifyPendingGrades = json.optBoolean("notifyPendingGrades", true),
            notifyUpdatedGrades = json.optBoolean("notifyUpdatedGrades", true),
            notifyErrors = json.optBoolean("notifyErrors", true),
            pollingMinutes = json.optInt("pollingMinutes", 30),
            ignoreTlsErrors = json.optBoolean("ignoreTlsErrors", false),
        ).normalized()
    }

    private fun mutateSettings(transform: (AppSettings) -> AppSettings) {
        persist(transform(readSettings()).normalized())
    }

    private fun persist(settings: AppSettings) {
        preferences.edit()
            .putString(KEY_SETTINGS_JSON, settings.toJson().toString())
            .apply()
        publish()
    }

    private fun publish() {
        settingsFlow.value = readSettingsFromPreferences()
    }

    companion object {
        private const val PREFS_NAME = "pops_secure_preferences"
        private const val KEY_SETTINGS_JSON = "settings_json_v2"
        private const val DEFAULT_OASIS_BASE_URL = "https://polytech-saclay.oasis.aouka.org/"
    }
}

private fun AppSettings.toJson(): JSONObject {
    return JSONObject()
        .put("accounts", JSONArray().apply {
            accounts.forEach { account ->
                put(account.toJson())
            }
        })
        .put("selectedAccountId", selectedAccountId)
        .put("oasisBaseUrl", oasisBaseUrl)
        .put("notificationsEnabled", notificationsEnabled)
        .put("notifyNewGrades", notifyNewGrades)
        .put("notifyPendingGrades", notifyPendingGrades)
        .put("notifyUpdatedGrades", notifyUpdatedGrades)
        .put("notifyErrors", notifyErrors)
        .put("pollingMinutes", pollingMinutes)
        .put("ignoreTlsErrors", ignoreTlsErrors)
}

private fun OasisAccount.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("login", login)
        .put("password", password)
        .put("studentId", resolvedStudentId())
        .put("displayName", displayName)
        .put("profilePhotoUrl", profilePhotoUrl)
        .put("lastSyncAt", lastSyncAt)
        .put("lastSyncSummary", lastSyncSummary)
        .put("lastSyncError", lastSyncError)
        .put("lastSyncErrorDetails", lastSyncErrorDetails)
        .put("consecutiveFailureCount", consecutiveFailureCount)
        .put("lastFailureNotificationDay", lastFailureNotificationDay)
        .put("failureNotificationActive", failureNotificationActive)
}

private fun JSONArray?.toAccounts(): List<OasisAccount> {
    if (this == null) {
        return emptyList()
    }
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                OasisAccount(
                    id = item.optString("id"),
                    login = item.optString("login"),
                    password = item.optString("password"),
                    studentId = item.optString("studentId"),
                    displayName = item.optString("displayName"),
                    profilePhotoUrl = item.optString("profilePhotoUrl").takeIf { it.isNotBlank() },
                    lastSyncAt = item.optLong("lastSyncAt", 0L).takeIf { it > 0L },
                    lastSyncSummary = item.optString("lastSyncSummary").takeIf { it.isNotBlank() },
                    lastSyncError = item.optString("lastSyncError").takeIf { it.isNotBlank() },
                    lastSyncErrorDetails = item.optString("lastSyncErrorDetails").takeIf { it.isNotBlank() },
                    consecutiveFailureCount = item.optInt("consecutiveFailureCount", 0),
                    lastFailureNotificationDay = item.optLong("lastFailureNotificationDay", 0L).takeIf { it > 0L },
                    failureNotificationActive = item.optBoolean("failureNotificationActive", false),
                ).normalized()
            )
        }
    }
}

private fun OasisAccount.normalized(): OasisAccount {
    return copy(
        login = login.trim(),
        password = password,
        studentId = resolvedStudentId(),
        displayName = displayName.trim(),
        profilePhotoUrl = profilePhotoUrl?.takeIf { it.isNotBlank() },
    )
}