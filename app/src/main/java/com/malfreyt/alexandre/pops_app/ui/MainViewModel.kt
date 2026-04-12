package com.malfreyt.alexandre.pops_app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.malfreyt.alexandre.pops_app.R
import com.malfreyt.alexandre.pops_app.data.AppContainer
import com.malfreyt.alexandre.pops_app.data.AppSettings
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
    val isSyncing: Boolean = false,
    val isSaving: Boolean = false,
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
                    current.copy(
                        savedSettings = settings,
                        draftSettings = nextDraft,
                        selectedYear = chooseSelectedYear(current.selectedYear, current.grades, current.semesterSnapshots),
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.observeGrades().collect { grades ->
                _uiState.update { current ->
                    current.copy(
                        grades = grades,
                        selectedYear = chooseSelectedYear(current.selectedYear, grades, current.semesterSnapshots),
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.observeSemesterSnapshots().collect { snapshots ->
                _uiState.update { current ->
                    current.copy(
                        semesterSnapshots = snapshots,
                        selectedYear = chooseSelectedYear(current.selectedYear, current.grades, snapshots),
                    )
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

    fun toggleServerUrlSettings() {
        _uiState.update { it.copy(serverUrlVisible = !it.serverUrlVisible) }
    }

    fun toggleAdvancedSettings() {
        _uiState.update { it.copy(advancedSettingsVisible = !it.advancedSettingsVisible) }
    }

    fun updateLogin(value: String) = updateDraftSettings { copy(login = value) }
    fun updatePassword(value: String) = updateDraftSettings { copy(password = value) }
    fun updateOasisUrl(value: String) = updateDraftSettings { copy(oasisBaseUrl = value) }
    fun updateIgnoreTlsErrors(value: Boolean) = updateDraftSettings { copy(ignoreTlsErrors = value) }
    fun updatePollingMinutes(value: Int) = updateDraftSettings { copy(pollingMinutes = value) }
    fun updateNotificationsEnabled(value: Boolean) = updateDraftSettings { copy(notificationsEnabled = value) }
    fun updateNotifyNewGrades(value: Boolean) = updateDraftSettings { copy(notifyNewGrades = value) }
    fun updateNotifyPendingGrades(value: Boolean) = updateDraftSettings { copy(notifyPendingGrades = value) }
    fun updateNotifyUpdatedGrades(value: Boolean) = updateDraftSettings { copy(notifyUpdatedGrades = value) }
    fun updateNotifyErrors(value: Boolean) = updateDraftSettings { copy(notifyErrors = value) }

    fun applyCredential(login: String, password: String) {
        _uiState.update { state ->
            state.copy(
                draftSettings = state.draftSettings.copy(login = login, password = password),
            )
        }
        enqueueSnackbar(context.getString(R.string.credentials_loaded))
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
                repository.testConnection(candidate)
                repository.saveSettings(candidate)
                SyncScheduler.reschedule(appContainer.appContext, repository.readSettings())
                enqueueSnackbar(context.getString(R.string.settings_saved))
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        destination = MainDestination.OASIS,
                        pendingCredentialSave = candidate.takeIf(AppSettings::hasCredentials)?.let { settings ->
                            CredentialSaveRequest(login = settings.login, password = settings.password)
                        },
                    )
                }
            } catch (error: Exception) {
                repository.saveSyncError(
                    message = error.message ?: context.getString(R.string.error_connection_impossible),
                    technicalDetails = error.toTechnicalDetails(),
                )
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
        if (!settings.canSync()) {
            _uiState.update { it.copy(destination = MainDestination.SETTINGS) }
            enqueueSnackbar(context.getString(R.string.settings_configure_before_sync))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorDialog = null) }
            try {
                val report = repository.sync(settings)
                SyncScheduler.reschedule(appContainer.appContext, repository.readSettings())
                if (repository.readSettings().notificationsEnabled) {
                    NotificationHelper.notifyChanges(appContainer.appContext, repository.readSettings(), report.changes)
                }
                enqueueSnackbar(report.summary)
                _uiState.update { it.copy(isSyncing = false) }
            } catch (error: Exception) {
                repository.saveSyncError(
                    message = error.message ?: context.getString(R.string.error_sync_generic),
                    technicalDetails = error.toTechnicalDetails(),
                )
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