package com.malfreyt.alexandre.pops_app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

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
        preferences.edit()
            .putString(KEY_LOGIN, settings.login)
            .putString(KEY_PASSWORD, settings.password)
            .putString(KEY_OASIS_BASE_URL, settings.oasisBaseUrl)
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, settings.notificationsEnabled)
            .putBoolean(KEY_NOTIFY_NEW_GRADES, settings.notifyNewGrades)
            .putBoolean(KEY_NOTIFY_PENDING_GRADES, settings.notifyPendingGrades)
            .putBoolean(KEY_NOTIFY_UPDATED_GRADES, settings.notifyUpdatedGrades)
            .putBoolean(KEY_NOTIFY_ERRORS, settings.notifyErrors)
            .putInt(KEY_POLLING_MINUTES, settings.pollingMinutes)
            .putBoolean(KEY_IGNORE_TLS_ERRORS, settings.ignoreTlsErrors)
            .apply()
        publish()
    }

    fun saveSyncSuccess(summary: String, timestamp: Long) {
        preferences.edit()
            .putLong(KEY_LAST_SYNC_AT, timestamp)
            .putString(KEY_LAST_SYNC_SUMMARY, summary)
            .remove(KEY_LAST_SYNC_ERROR)
            .remove(KEY_LAST_SYNC_ERROR_DETAILS)
            .putInt(KEY_CONSECUTIVE_FAILURE_COUNT, 0)
            .remove(KEY_LAST_FAILURE_NOTIFICATION_DAY)
            .putBoolean(KEY_FAILURE_NOTIFICATION_ACTIVE, false)
            .apply()
        publish()
    }

    fun saveSyncError(message: String, technicalDetails: String) {
        preferences.edit()
            .putString(KEY_LAST_SYNC_ERROR, message)
            .putString(KEY_LAST_SYNC_ERROR_DETAILS, technicalDetails)
            .apply()
        publish()
    }

    fun recordBackgroundFailure(message: String, technicalDetails: String): FailureNotificationState {
        val current = readSettings()
        val attempts = current.consecutiveFailureCount + 1
        val today = LocalDate.now().toEpochDay()
        val shouldOnlyAlertOnce = current.lastFailureNotificationDay == today && current.failureNotificationActive

        preferences.edit()
            .putString(KEY_LAST_SYNC_ERROR, message)
            .putString(KEY_LAST_SYNC_ERROR_DETAILS, technicalDetails)
            .putInt(KEY_CONSECUTIVE_FAILURE_COUNT, attempts)
            .apply()
        publish()

        return FailureNotificationState(
            attempts = attempts,
            shouldOnlyAlertOnce = shouldOnlyAlertOnce,
            shortMessage = message,
            technicalDetails = technicalDetails,
        )
    }

    fun markFailureNotificationShown(epochDay: Long) {
        preferences.edit()
            .putLong(KEY_LAST_FAILURE_NOTIFICATION_DAY, epochDay)
            .putBoolean(KEY_FAILURE_NOTIFICATION_ACTIVE, true)
            .apply()
        publish()
    }

    fun markFailureNotificationDismissed() {
        preferences.edit()
            .putBoolean(KEY_FAILURE_NOTIFICATION_ACTIVE, false)
            .apply()
        publish()
    }

    private fun readSettingsFromPreferences(): AppSettings {
        return AppSettings(
            login = preferences.getString(KEY_LOGIN, "") ?: "",
            password = preferences.getString(KEY_PASSWORD, "") ?: "",
            oasisBaseUrl = preferences.getString(KEY_OASIS_BASE_URL, "https://polytech-saclay.oasis.aouka.org/") ?: "https://polytech-saclay.oasis.aouka.org/",
            notificationsEnabled = preferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
            notifyNewGrades = preferences.getBoolean(KEY_NOTIFY_NEW_GRADES, true),
            notifyPendingGrades = preferences.getBoolean(KEY_NOTIFY_PENDING_GRADES, true),
            notifyUpdatedGrades = preferences.getBoolean(KEY_NOTIFY_UPDATED_GRADES, true),
            notifyErrors = preferences.getBoolean(KEY_NOTIFY_ERRORS, true),
            pollingMinutes = preferences.getInt(KEY_POLLING_MINUTES, 30),
            ignoreTlsErrors = preferences.getBoolean(KEY_IGNORE_TLS_ERRORS, false),
            lastSyncAt = preferences.getLong(KEY_LAST_SYNC_AT, 0L).takeIf { it > 0L },
            lastSyncSummary = preferences.getString(KEY_LAST_SYNC_SUMMARY, null),
            lastSyncError = preferences.getString(KEY_LAST_SYNC_ERROR, null),
            lastSyncErrorDetails = preferences.getString(KEY_LAST_SYNC_ERROR_DETAILS, null),
            consecutiveFailureCount = preferences.getInt(KEY_CONSECUTIVE_FAILURE_COUNT, 0),
            lastFailureNotificationDay = preferences.getLong(KEY_LAST_FAILURE_NOTIFICATION_DAY, 0L).takeIf { it > 0L },
            failureNotificationActive = preferences.getBoolean(KEY_FAILURE_NOTIFICATION_ACTIVE, false),
        )
    }

    private fun publish() {
        settingsFlow.value = readSettingsFromPreferences()
    }

    companion object {
        private const val PREFS_NAME = "pops_secure_preferences"
        private const val KEY_LOGIN = "login"
        private const val KEY_PASSWORD = "password"
        private const val KEY_OASIS_BASE_URL = "oasis_base_url"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_NOTIFY_NEW_GRADES = "notify_new_grades"
        private const val KEY_NOTIFY_PENDING_GRADES = "notify_pending_grades"
        private const val KEY_NOTIFY_UPDATED_GRADES = "notify_updated_grades"
        private const val KEY_NOTIFY_ERRORS = "notify_errors"
        private const val KEY_POLLING_MINUTES = "polling_minutes"
        private const val KEY_IGNORE_TLS_ERRORS = "ignore_tls_errors"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SYNC_SUMMARY = "last_sync_summary"
        private const val KEY_LAST_SYNC_ERROR = "last_sync_error"
        private const val KEY_LAST_SYNC_ERROR_DETAILS = "last_sync_error_details"
        private const val KEY_CONSECUTIVE_FAILURE_COUNT = "consecutive_failure_count"
        private const val KEY_LAST_FAILURE_NOTIFICATION_DAY = "last_failure_notification_day"
        private const val KEY_FAILURE_NOTIFICATION_ACTIVE = "failure_notification_active"
    }
}