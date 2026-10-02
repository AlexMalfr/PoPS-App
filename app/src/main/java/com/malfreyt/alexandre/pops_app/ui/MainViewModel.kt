package com.malfreyt.alexandre.pops_app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.malfreyt.alexandre.pops_app.R
import com.malfreyt.alexandre.pops_app.data.AppContainer
import com.malfreyt.alexandre.pops_app.data.AccountNotificationType
import com.malfreyt.alexandre.pops_app.data.AppSettings
import com.malfreyt.alexandre.pops_app.data.OasisAccount
import com.malfreyt.alexandre.pops_app.data.OasisAuthenticationRejectedException
import com.malfreyt.alexandre.pops_app.data.SemesterSnapshot
import com.malfreyt.alexandre.pops_app.data.UiGrade
import com.malfreyt.alexandre.pops_app.data.currentAcademicYear
import com.malfreyt.alexandre.pops_app.notifications.NotificationHelper
import com.malfreyt.alexandre.pops_app.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainDestination {
    OASIS,
    SETTINGS,
}

enum class OasisTab {
    EPREUVES,
    MODULES,
    UES,
}

enum class GradeSortMode {
    DATE,
    MODULE,
    GRADE,
    AVERAGE,
    RANK_POSITION,
    RANK_PERCENTAGE,
}

data class ErrorDialogState(
    val title: String,
    val message: String,
    val technicalDetails: String,
    val warning: String? = null,
)

data class MainUiState(
    val savedSettings: AppSettings = AppSettings(),
    val draftSettings: AppSettings = AppSettings(),
    val grades: List<UiGrade> = emptyList(),
    val semesterSnapshots: List<SemesterSnapshot> = emptyList(),
    val destination: MainDestination = MainDestination.OASIS,
    val selectedYear: Int? = null,
    val selectedTab: OasisTab = OasisTab.EPREUVES,
    val gradeSortMode: GradeSortMode = GradeSortMode.DATE,
    val gradeSortAscending: Boolean = false,
    val isSyncing: Boolean = false,
    val isSaving: Boolean = false,
    val isSavingAccount: Boolean = false,
    val serverUrlVisible: Boolean = false,
    val advancedSettingsVisible: Boolean = false,
    val snackbarMessage: String? = null,
    val snackbarToken: Long = 0L,
    val errorDialog: ErrorDialogState? = null,
)

class MainViewModel(
    private val appContainer: AppContainer,
) : ViewModel() {
    private val repository = appContainer.gradeRepository
    private val context = appContainer.appContext
    private var allGrades: List<UiGrade> = emptyList()
    private var allSemesterSnapshots: List<SemesterSnapshot> = emptyList()
    private var activeSyncJob: Job? = null

    private val _uiState = MutableStateFlow(
        MainUiState(
            savedSettings = repository.readSettings(),
            draftSettings = repository.readSettings(),
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSettings().collect { settings ->
                _uiState.update { current ->
                    val shouldReplaceDraft = current.draftSettings.editableEquals(current.savedSettings)
                    val nextDraft = if (shouldReplaceDraft) settings else current.draftSettings.withRuntimeStateFrom(settings)
                    applyVisibleAccountData(
                        current.copy(
                            savedSettings = settings,
                            draftSettings = nextDraft,
                        ),
                        settings,
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.observeGrades().collect { grades ->
                allGrades = grades
                _uiState.update { current ->
                    applyVisibleAccountData(current, current.savedSettings)
                }
            }
        }

        viewModelScope.launch {
            repository.observeSemesterSnapshots().collect { snapshots ->
                allSemesterSnapshots = snapshots
                _uiState.update { current ->
                    applyVisibleAccountData(current, current.savedSettings)
                }
            }
        }
    }

    fun navigate(destination: MainDestination) {
        _uiState.update { it.copy(destination = destination) }
    }

    fun selectYear(year: Int) {
        _uiState.update { it.copy(selectedYear = year) }
    }

    fun selectTab(tab: OasisTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setGradeSortMode(mode: GradeSortMode) {
        _uiState.update {
            if (it.gradeSortMode == mode) {
                it.copy(gradeSortAscending = !it.gradeSortAscending)
            } else {
                it.copy(
                    gradeSortMode = mode,
                    gradeSortAscending = defaultGradeSortAscending(mode),
                )
            }
        }
    }

    fun toggleServerUrlSettings() {
        _uiState.update { it.copy(serverUrlVisible = !it.serverUrlVisible) }
    }

    fun toggleAdvancedSettings() {
        _uiState.update { it.copy(advancedSettingsVisible = !it.advancedSettingsVisible) }
    }

    fun updateOasisUrl(value: String) = updateDraftSettings { copy(oasisBaseUrl = value) }
    fun updateIgnoreTlsErrors(value: Boolean) = updateDraftSettings { copy(ignoreTlsErrors = value) }
    fun updatePollingMinutes(value: Int) = updateSelectedAccountSync { copy(pollingMinutes = value) }
    fun updateSyncUnmeteredOnly(value: Boolean) = updateSelectedAccountSync { copy(syncUnmeteredOnly = value) }
    fun updateSyncChargingOnly(value: Boolean) = updateSelectedAccountSync { copy(syncChargingOnly = value) }

    private fun updateSelectedAccountSync(transform: OasisAccount.() -> OasisAccount) {
        val account = repository.readSettings().selectedAccountOrNull() ?: return
        repository.saveAccount(account.transform())
        applyNotificationSettings()
        SyncScheduler.reschedule(appContainer.appContext, repository.readSettings())
    }
    fun updateNotificationsEnabled(value: Boolean) {
        val account = repository.readSettings().selectedAccountOrNull() ?: return
        val updated = account.copy(notificationsEnabled = value)
        repository.saveAccount(updated)
        applyNotificationSettings()
    }
    fun updateNotifyNewGrades(value: Boolean) = updateSelectedAccountNotification(AccountNotificationType.NEW, value)
    fun updateNotifyPendingGrades(value: Boolean) = updateSelectedAccountNotification(AccountNotificationType.PENDING, value)
    fun updateNotifyUpdatedGrades(value: Boolean) = updateSelectedAccountNotification(AccountNotificationType.UPDATED, value)
    fun updateNotifyErrors(value: Boolean) = updateSelectedAccountNotification(AccountNotificationType.ERROR, value)

    fun selectAccount(accountId: String) {
        repository.selectAccount(accountId)
    }

    fun completeOnboarding() {
        val current = repository.readSettings()
        repository.saveSettings(current.copy(onboardingCompleted = true))
        _uiState.update { it.copy(errorDialog = null) }
    }

    fun saveAccount(existingAccountId: String?, login: String, password: String, oasisBaseUrl: String? = null, onAuthenticated: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingAccount = true, errorDialog = null) }
            try {
                val account = repository.upsertAccount(existingAccountId, login, password, oasisBaseUrl)
                // Commit Autofill only after Oasis has accepted these credentials.
                runCatching { onAuthenticated() }
                NotificationHelper.createChannel(appContainer.appContext, repository.readSettings())
                SyncScheduler.reschedule(appContainer.appContext, repository.readSettings())
                enqueueSnackbar(
                    if (existingAccountId == null) {
                        context.getString(R.string.account_added, account.resolvedDisplayName())
                    } else {
                        context.getString(R.string.account_updated, account.resolvedDisplayName())
                    }
                )
                _uiState.update {
                    it.copy(
                        isSavingAccount = false,
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingAccount = false,
                        errorDialog = ErrorDialogState(
                            title = context.getString(R.string.error_connection_title),
                            message = error.message ?: context.getString(R.string.error_connection_message),
                            technicalDetails = error.toTechnicalDetails(),
                            warning = if (error is OasisAuthenticationRejectedException) context.getString(R.string.error_oasis_auth_warning) else null,
                        ),
                    )
                }
            }
        }
    }

    fun removeSelectedAccount() {
        val selectedAccount = _uiState.value.savedSettings.selectedAccountOrNull() ?: return
        viewModelScope.launch {
            repository.removeAccount(selectedAccount.id)
            NotificationHelper.createChannel(appContainer.appContext, repository.readSettings())
            NotificationHelper.clearSyncFailureNotification(appContainer.appContext, selectedAccount.id)
            SyncScheduler.reschedule(appContainer.appContext, repository.readSettings())
            enqueueSnackbar(context.getString(R.string.account_removed, selectedAccount.resolvedDisplayName()))
        }
    }

    fun showSnackbarMessage(message: String) {
        enqueueSnackbar(message)
    }

    fun saveSettings() {
        val current = _uiState.value
        val candidate = current.draftSettings.withRuntimeStateFrom(current.savedSettings)
        if (candidate.editableEquals(current.savedSettings)) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorDialog = null) }
            try {
                // Credentials are validated by saveAccount; preferences must remain editable offline.
                repository.saveSettings(candidate.withRuntimeStateFrom(repository.readSettings()))
                applyNotificationSettings()
                SyncScheduler.reschedule(appContainer.appContext, repository.readSettings())
                enqueueSnackbar(context.getString(R.string.settings_saved))
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        destination = MainDestination.OASIS,
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorDialog = ErrorDialogState(
                            title = context.getString(R.string.error_settings_title),
                            message = error.message ?: context.getString(R.string.error_settings_message),
                            technicalDetails = error.toTechnicalDetails(),
                            warning = if (error is OasisAuthenticationRejectedException) context.getString(R.string.error_oasis_auth_warning) else null,
                        ),
                    )
                }
            }
        }
    }

    fun refresh() {
        startSync(clearCacheFirst = false)
    }

    fun hardRefresh() {
        startSync(clearCacheFirst = true)
    }

    fun stopSync() {
        repository.cancelActiveSync()
        activeSyncJob?.cancel(CancellationException("Sync cancelled by user"))
        activeSyncJob = null
        _uiState.update { it.copy(isSyncing = false, errorDialog = null) }
        enqueueSnackbar(context.getString(R.string.sync_cancelled))
    }

    private fun startSync(clearCacheFirst: Boolean) {
        val settings = _uiState.value.savedSettings
        if (!settings.selectedAccountCanSync()) {
            _uiState.update { it.copy(destination = MainDestination.SETTINGS) }
            enqueueSnackbar(context.getString(R.string.settings_configure_before_sync))
            return
        }

        activeSyncJob?.cancel()
        activeSyncJob = viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorDialog = null) }
            try {
                if (clearCacheFirst) {
                    repository.clearSelectedAccountCache()
                }
                val report = repository.syncSelectedAccount()
                SyncScheduler.reschedule(appContainer.appContext, repository.readSettings())
                NotificationHelper.notifyChanges(appContainer.appContext, repository.readSettings(), report.changes)
                NotificationHelper.clearSyncFailureNotification(appContainer.appContext, report.accountId)
                enqueueSnackbar(report.summary)
            } catch (error: CancellationException) {
                return@launch
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        errorDialog = ErrorDialogState(
                            title = context.getString(R.string.error_sync_title),
                            message = error.message ?: context.getString(R.string.error_sync_message),
                            technicalDetails = error.toTechnicalDetails(),
                            warning = if (error is OasisAuthenticationRejectedException) context.getString(R.string.error_oasis_auth_warning) else null,
                        ),
                    )
                }
            } finally {
                activeSyncJob = null
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun showErrorDialog(dialog: ErrorDialogState) {
        _uiState.update { it.copy(errorDialog = dialog) }
    }

    fun dismissErrorDialog() {
        _uiState.update { it.copy(errorDialog = null) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun updateDraftSettings(transform: AppSettings.() -> AppSettings) {
        _uiState.update { state ->
            state.copy(draftSettings = state.draftSettings.transform())
        }
    }

    private fun enqueueSnackbar(message: String) {
        _uiState.update { current ->
            current.copy(
                snackbarMessage = message,
                snackbarToken = current.snackbarToken + 1L,
            )
        }
    }

    private fun applyVisibleAccountData(current: MainUiState, settings: AppSettings): MainUiState {
        val selectedAccountId = settings.selectedAccountOrNull()?.id
        val visibleGrades = if (selectedAccountId == null) {
            emptyList()
        } else {
            allGrades.filter { it.accountId == selectedAccountId }
        }
        val visibleSnapshots = if (selectedAccountId == null) {
            emptyList()
        } else {
            allSemesterSnapshots.filter { it.accountId == selectedAccountId }
        }
        return current.copy(
            grades = visibleGrades,
            semesterSnapshots = visibleSnapshots,
            selectedYear = chooseSelectedYear(current.selectedYear, visibleGrades, visibleSnapshots),
        )
    }

    private fun chooseSelectedYear(selectedYear: Int?, grades: List<UiGrade>, snapshots: List<SemesterSnapshot>): Int {
        val availableYears = (grades.map { it.academicYear } + snapshots.map { it.academicYear }).distinct().sortedDescending()
        return when {
            selectedYear != null && availableYears.contains(selectedYear) -> selectedYear
            availableYears.isNotEmpty() -> availableYears.first()
            else -> currentAcademicYear()
        }
    }

    private fun Exception.toTechnicalDetails(): String {
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

    private fun updateSelectedAccountNotification(type: AccountNotificationType, enabled: Boolean) {
        val account = repository.readSettings().selectedAccountOrNull() ?: return
        val updated = when (type) {
            AccountNotificationType.NEW -> account.copy(notifyNewGrades = enabled)
            AccountNotificationType.PENDING -> account.copy(notifyPendingGrades = enabled)
            AccountNotificationType.UPDATED -> account.copy(notifyUpdatedGrades = enabled)
            AccountNotificationType.ERROR -> account.copy(notifyErrors = enabled)
        }
        repository.saveAccount(updated)
        applyNotificationSettings()
    }

    private fun applyNotificationSettings() {
        val settings = repository.readSettings()
        NotificationHelper.createChannel(appContainer.appContext, settings)
        settings.accounts.filter { account ->
            !account.canSyncInBackground() ||
                !account.isNotificationEnabled(AccountNotificationType.ERROR)
        }.forEach { account ->
            NotificationHelper.clearSyncFailureNotification(appContainer.appContext, account.id)
            if (account.failureNotificationActive) {
                appContainer.settingsStore.markFailureNotificationDismissed(account.id)
            }
        }
    }

    private fun defaultGradeSortAscending(mode: GradeSortMode): Boolean {
        return when (mode) {
            GradeSortMode.DATE -> false
            GradeSortMode.MODULE -> true
            GradeSortMode.GRADE -> false
            GradeSortMode.AVERAGE -> false
            GradeSortMode.RANK_POSITION -> true
            GradeSortMode.RANK_PERCENTAGE -> true
        }
    }
}

class MainViewModelFactory(
    private val appContainer: AppContainer,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(appContainer) as T
        }
        throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
    }
}
