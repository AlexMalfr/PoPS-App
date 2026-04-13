package com.malfreyt.alexandre.pops_app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.malfreyt.alexandre.pops_app.R
import com.malfreyt.alexandre.pops_app.data.AppContainer
import com.malfreyt.alexandre.pops_app.data.AppSettings
import com.malfreyt.alexandre.pops_app.data.OasisAccount
import com.malfreyt.alexandre.pops_app.data.SemesterSnapshot
import com.malfreyt.alexandre.pops_app.data.UiGrade
import com.malfreyt.alexandre.pops_app.data.currentAcademicYear
import com.malfreyt.alexandre.pops_app.notifications.NotificationHelper
import com.malfreyt.alexandre.pops_app.sync.SyncScheduler
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
}

data class ErrorDialogState(
    val title: String,
    val message: String,
    val technicalDetails: String,
)

data class CredentialSaveRequest(
    val login: String,
    val password: String,
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
    val isSyncing: Boolean = false,
    val isSaving: Boolean = false,
    val isSavingAccount: Boolean = false,
    val serverUrlVisible: Boolean = false,
    val advancedSettingsVisible: Boolean = false,
    val snackbarMessage: String? = null,
    val snackbarToken: Long = 0L,
    val errorDialog: ErrorDialogState? = null,
    val pendingCredentialSave: CredentialSaveRequest? = null,
)

class MainViewModel(
    private val appContainer: AppContainer,
) : ViewModel() {
    private val repository = appContainer.gradeRepository
    private val context = appContainer.appContext
    private var allGrades: List<UiGrade> = emptyList()
    private var allSemesterSnapshots: List<SemesterSnapshot> = emptyList()

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
        _uiState.update { it.copy(gradeSortMode = mode) }
    }

    fun toggleServerUrlSettings() {
        _uiState.update { it.copy(serverUrlVisible = !it.serverUrlVisible) }
    }

    fun toggleAdvancedSettings() {
        _uiState.update { it.copy(advancedSettingsVisible = !it.advancedSettingsVisible) }
    }

    fun updateOasisUrl(value: String) = updateDraftSettings { copy(oasisBaseUrl = value) }
    fun updateIgnoreTlsErrors(value: Boolean) = updateDraftSettings { copy(ignoreTlsErrors = value) }
    fun updatePollingMinutes(value: Int) = updateDraftSettings { copy(pollingMinutes = value) }
    fun updateNotificationsEnabled(value: Boolean) = updateDraftSettings { copy(notificationsEnabled = value) }
    fun updateNotifyNewGrades(value: Boolean) = updateDraftSettings { copy(notifyNewGrades = value) }
    fun updateNotifyPendingGrades(value: Boolean) = updateDraftSettings { copy(notifyPendingGrades = value) }
    fun updateNotifyUpdatedGrades(value: Boolean) = updateDraftSettings { copy(notifyUpdatedGrades = value) }
    fun updateNotifyErrors(value: Boolean) = updateDraftSettings { copy(notifyErrors = value) }

    fun selectAccount(accountId: String) {
        repository.selectAccount(accountId)
    }

    fun saveAccount(existingAccountId: String?, login: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingAccount = true, errorDialog = null) }
            try {
                val account = repository.upsertAccount(existingAccountId, login, password)
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
                        pendingCredentialSave = CredentialSaveRequest(login = login.trim(), password = password),
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
            NotificationHelper.clearSyncFailureNotification(appContainer.appContext, selectedAccount.id)
            SyncScheduler.reschedule(appContainer.appContext, repository.readSettings())
            enqueueSnackbar(context.getString(R.string.account_removed, selectedAccount.resolvedDisplayName()))
        }
    }

    fun consumeCredentialSaveRequest() {
        _uiState.update { it.copy(pendingCredentialSave = null) }
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
                candidate.selectedAccountOrNull()?.takeIf(OasisAccount::hasCredentials)?.let { account ->
                    repository.testConnection(candidate, account.login, account.password)
                }
                repository.saveSettings(candidate)
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
                            title = context.getString(R.string.error_connection_title),
                            message = error.message ?: context.getString(R.string.error_connection_message),
                            technicalDetails = error.toTechnicalDetails(),
                        ),
                    )
                }
            }
        }
    }

    fun refresh() {
        val settings = _uiState.value.savedSettings
        if (!settings.selectedAccountCanSync()) {
            _uiState.update { it.copy(destination = MainDestination.SETTINGS) }
            enqueueSnackbar(context.getString(R.string.settings_configure_before_sync))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorDialog = null) }
            try {
                val report = repository.syncSelectedAccount()
                SyncScheduler.reschedule(appContainer.appContext, repository.readSettings())
                if (repository.readSettings().notificationsEnabled) {
                    NotificationHelper.notifyChanges(appContainer.appContext, repository.readSettings(), report.changes)
                }
                NotificationHelper.clearSyncFailureNotification(appContainer.appContext, report.accountId)
                enqueueSnackbar(report.summary)
                _uiState.update { it.copy(isSyncing = false) }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorDialog = ErrorDialogState(
                            title = context.getString(R.string.error_sync_title),
                            message = error.message ?: context.getString(R.string.error_sync_message),
                            technicalDetails = error.toTechnicalDetails(),
                        ),
                    )
                }
            }
        }
    }

    fun hardRefresh() {
        val settings = _uiState.value.savedSettings
        if (!settings.selectedAccountCanSync()) {
            _uiState.update { it.copy(destination = MainDestination.SETTINGS) }
            enqueueSnackbar(context.getString(R.string.settings_configure_before_sync))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorDialog = null) }
            try {
                repository.clearSelectedAccountCache()
                val report = repository.syncSelectedAccount()
                SyncScheduler.reschedule(appContainer.appContext, repository.readSettings())
                enqueueSnackbar(report.summary)
                _uiState.update { it.copy(isSyncing = false) }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorDialog = ErrorDialogState(
                            title = context.getString(R.string.error_sync_title),
                            message = error.message ?: context.getString(R.string.error_sync_message),
                            technicalDetails = error.toTechnicalDetails(),
                        ),
                    )
                }
            }
        }
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