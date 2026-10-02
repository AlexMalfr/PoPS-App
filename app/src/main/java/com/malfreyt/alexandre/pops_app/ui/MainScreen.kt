package com.malfreyt.alexandre.pops_app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.malfreyt.alexandre.pops_app.BuildConfig
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.malfreyt.alexandre.pops_app.R
import androidx.compose.ui.platform.LocalAutofillManager
import com.malfreyt.alexandre.pops_app.data.ModuleSummary
import com.malfreyt.alexandre.pops_app.data.NoteChangeType
import com.malfreyt.alexandre.pops_app.data.OasisAccount
import com.malfreyt.alexandre.pops_app.data.AccountNotificationType
import com.malfreyt.alexandre.pops_app.data.currentAcademicYear
import com.malfreyt.alexandre.pops_app.data.formatGrade
import com.malfreyt.alexandre.pops_app.data.SemesterSnapshot
import com.malfreyt.alexandre.pops_app.data.SyncChange
import com.malfreyt.alexandre.pops_app.data.UiGrade
import com.malfreyt.alexandre.pops_app.data.UnitSummary
import com.malfreyt.alexandre.pops_app.notifications.NotificationHelper
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val settingsSaveEnabled = !state.isSaving && !state.draftSettings.editableEquals(state.savedSettings)
    // Per-tab search queries (saved in memory only)
    var searchQueryEpreuves by rememberSaveable { mutableStateOf("") }
    var searchQueryModules by rememberSaveable { mutableStateOf("") }
    var searchQueryUEs by rememberSaveable { mutableStateOf("") }
    val currentSearchQuery = when (state.selectedTab) {
        OasisTab.EPREUVES -> searchQueryEpreuves
        OasisTab.MODULES -> searchQueryModules
        OasisTab.UES -> searchQueryUEs
    }
    val onSearchQueryChange: (String) -> Unit = { value ->
        when (state.selectedTab) {
            OasisTab.EPREUVES -> searchQueryEpreuves = value
            OasisTab.MODULES -> searchQueryModules = value
            OasisTab.UES -> searchQueryUEs = value
        }
    }
    var searchActive by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.snackbarToken) {
        val message = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbar()
    }

    state.errorDialog?.let { dialog ->
        ErrorDetailsDialog(dialog = dialog, onDismiss = viewModel::dismissErrorDialog)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            when (state.destination) {
                MainDestination.OASIS -> OasisTopBar(
                    state = state,
                    onSelectYear = viewModel::selectYear,
                    onRefresh = viewModel::refresh,
                    onHardRefresh = viewModel::hardRefresh,
                    onStopSync = viewModel::stopSync,
                    onShowSyncError = { account ->
                        viewModel.showErrorDialog(
                            ErrorDialogState(
                                title = context.getString(R.string.sync_status_error),
                                message = account.lastSyncError ?: context.getString(R.string.sync_status_error),
                                technicalDetails = account.lastSyncErrorDetails ?: "",
                            )
                        )
                    },
                    searchQuery = currentSearchQuery,
                    searchActive = searchActive,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchActiveChange = { searchActive = it },
                )
                MainDestination.SETTINGS -> TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    actions = {
                        IconButton(onClick = viewModel::saveSettings, enabled = settingsSaveEnabled) {
                            if (state.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save_action))
                            }
                        }
                        OverflowMenu()
                    }
                )
            }
        },
        bottomBar = {
            BottomNavigationBar(state = state, onNavigate = viewModel::navigate)
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.destination) {
                MainDestination.OASIS -> OasisPage(
                    state = state,
                    viewModel = viewModel,
                    searchQuery = currentSearchQuery,
                    onOpenModuleInExams = { module ->
                        searchQueryEpreuves = module.title
                        searchActive = true
                        if (state.gradeSortMode != GradeSortMode.MODULE) {
                            viewModel.setGradeSortMode(GradeSortMode.MODULE)
                        }
                        viewModel.selectTab(OasisTab.EPREUVES)
                    },
                )
                MainDestination.SETTINGS -> SettingsPage(state = state, viewModel = viewModel)
            }
        }
    }
}

// ──────────────────────────────────────────────────
//  General overflow menu (shown on all screens)
// ──────────────────────────────────────────────────

@Composable
private fun OverflowMenu() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_report_issue)) },
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                onClick = {
                    expanded = false
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = buildSupportEmailUri()
                    }
                    runCatching { context.startActivity(intent) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_github)) },
                leadingIcon = { Icon(Icons.Filled.OpenInBrowser, contentDescription = null) },
                onClick = {
                    expanded = false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPOSITORY_URL))
                    runCatching { context.startActivity(intent) }
                },
            )
        }
    }
}

// ──────────────────────────────────────────────────
//  Top bar
// ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OasisTopBar(
    state: MainUiState,
    onSelectYear: (Int) -> Unit,
    onRefresh: () -> Unit,
    onHardRefresh: () -> Unit,
    onStopSync: () -> Unit,
    onShowSyncError: (OasisAccount) -> Unit,
    searchQuery: String,
    searchActive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
) {
    val years = (state.grades.map { it.academicYear } + state.semesterSnapshots.map { it.academicYear })
        .distinct()
        .sortedDescending()
        .ifEmpty { listOf(state.selectedYear ?: 0) }
    var expanded by remember(state.selectedYear, years) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    // Show the search field when active or when there's a query
    val showSearchField = searchActive || searchQuery.isNotBlank()

    TopAppBar(
        title = {
            if (showSearchField) {
                CompactSearchField(
                    query = searchQuery,
                    focusRequester = focusRequester,
                    onQueryChange = onSearchQueryChange,
                    onBlurWithEmptyQuery = { onSearchActiveChange(false) },
                    onClearAndClose = {
                        onSearchQueryChange("")
                        onSearchActiveChange(false)
                    },
                    onFocusStateChanged = { focused ->
                        val wasFocused = hasFocus
                        hasFocus = focused
                        if (wasFocused && !focused && searchQuery.isBlank()) {
                            onSearchActiveChange(false)
                        }
                    },
                )
                LaunchedEffect(showSearchField) {
                    focusRequester.requestFocus()
                }
            } else {
                Column {
                    Text(stringResource(R.string.bottom_nav_oasis))
                    SyncStatusSubtitle(state = state, onShowSyncError = onShowSyncError)
                }
            }
        },
        actions = {
            if (!showSearchField) {
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(formatAcademicYear(state.selectedYear ?: years.firstOrNull()))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.year_picker_content_description))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        years.filter { it > 0 }.forEach { year ->
                            DropdownMenuItem(
                                text = { Text(formatAcademicYear(year)) },
                                onClick = {
                                    expanded = false
                                    onSelectYear(year)
                                }
                            )
                        }
                    }
                }
            }

            IconButton(onClick = {
                if (showSearchField) {
                    onSearchQueryChange("")
                    onSearchActiveChange(false)
                } else {
                    onSearchActiveChange(true)
                }
            }) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.search_action),
                    tint = if (showSearchField || searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OasisKebabMenu(
                state = state,
                onRefresh = onRefresh,
                onHardRefresh = onHardRefresh,
                onStopSync = onStopSync,
            )
        },
    )
}

@Composable
private fun CompactSearchField(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onBlurWithEmptyQuery: () -> Unit,
    onClearAndClose: () -> Unit,
    onFocusStateChanged: (Boolean) -> Unit,
) {
    var everFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isBlank()) {
                    Text(
                        stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                everFocused = true
                            }
                            onFocusStateChanged(focusState.isFocused)
                            if (everFocused && !focusState.isFocused && query.isBlank()) {
                                onBlurWithEmptyQuery()
                            }
                        },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                )
            }
            if (query.isNotBlank()) {
                IconButton(onClick = onClearAndClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear_action), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SyncStatusSubtitle(
    state: MainUiState,
    onShowSyncError: (OasisAccount) -> Unit,
) {
    val settings = state.savedSettings
    val selectedAccount = settings.selectedAccountOrNull() ?: return
    val noInternetMessage = stringResource(R.string.error_no_internet)
    val hasError = selectedAccount.lastSyncError != null && selectedAccount.lastSyncError != noInternetMessage
    val bgOff = selectedAccount.pollingMinutes == 0
    val lastSuccess = selectedAccount.lastSyncAt?.let { formatRelativeTime(it) }

    // Error row (clickable)
    if (hasError) {
        Row(
            modifier = Modifier.clickable { onShowSyncError(selectedAccount) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Filled.Error, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
            Text(
                text = stringResource(R.string.sync_status_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

    val icon = when {
        bgOff -> Icons.Filled.CloudOff
        else -> null
    }
    val text = when {
        lastSuccess != null -> lastSuccess
        bgOff -> stringResource(R.string.sync_status_bg_off)
        else -> stringResource(R.string.sync_no_recent)
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OasisKebabMenu(
    state: MainUiState,
    onRefresh: () -> Unit,
    onHardRefresh: () -> Unit,
    onStopSync: () -> Unit,
) {
    val context = LocalContext.current
    val oasisBaseUrl = state.savedSettings.oasisBaseUrl
    val canSync = !state.isSyncing && state.savedSettings.selectedAccountCanSync()
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (state.isSyncing) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_stop_sync)) },
                    leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onStopSync()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_refresh)) },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    enabled = canSync,
                    onClick = {
                        expanded = false
                        onRefresh()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_hard_refresh)) },
                    leadingIcon = { Icon(Icons.Filled.Sync, contentDescription = null) },
                    enabled = canSync,
                    onClick = {
                        expanded = false
                        onHardRefresh()
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_open_browser)) },
                leadingIcon = { Icon(Icons.Filled.OpenInBrowser, contentDescription = null) },
                onClick = {
                    expanded = false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(oasisBaseUrl))
                    runCatching { context.startActivity(intent) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_export)) },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                onClick = {
                    expanded = false
                    // TODO: wire export feature (XLSX / PDF / CSV / JSON)
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_report_issue)) },
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                onClick = {
                    expanded = false
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = buildSupportEmailUri()
                    }
                    runCatching { context.startActivity(intent) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_github)) },
                leadingIcon = { Icon(Icons.Filled.OpenInBrowser, contentDescription = null) },
                onClick = {
                    expanded = false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPOSITORY_URL))
                    runCatching { context.startActivity(intent) }
                },
            )
        }
    }
}

// ──────────────────────────────────────────────────
//  Bottom navigation
// ──────────────────────────────────────────────────

@Composable
private fun BottomNavigationBar(
    state: MainUiState,
    onNavigate: (MainDestination) -> Unit,
) {
    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
        NavigationBarItem(
            selected = state.destination == MainDestination.OASIS,
            onClick = { onNavigate(MainDestination.OASIS) },
            icon = { Icon(Icons.Filled.School, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_nav_oasis)) },
        )
        NavigationBarItem(
            selected = state.destination == MainDestination.SETTINGS,
            onClick = { onNavigate(MainDestination.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_nav_settings)) },
        )
    }
}

// ──────────────────────────────────────────────────
//  Oasis page (tabs + pull-to-refresh)
// ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OasisPage(
    state: MainUiState,
    viewModel: MainViewModel,
    searchQuery: String,
    onOpenModuleInExams: (ModuleSummary) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val selectedYear = state.selectedYear
    val gradesForYear = state.grades.filter { it.academicYear == selectedYear }
    val semesterSnapshotsForYear = state.semesterSnapshots.filter { it.academicYear == selectedYear }.sortedBy { it.semester }
    val rawModuleCount = semesterSnapshotsForYear.sumOf { it.modules.size }
    val rawUnitCount = semesterSnapshotsForYear.sumOf { it.units.size }
    val trimmedQuery = searchQuery.trim()
    val filteredGradesForYear = gradesForYear.filter { matchesGradeSearch(it, trimmedQuery) }
    val filteredSemesterSnapshotsForYear = semesterSnapshotsForYear.map { snapshot ->
        snapshot.copy(
            modules = snapshot.modules.filter { matchesModuleSearch(it, trimmedQuery) },
            units = snapshot.units.filter { matchesUnitSearch(it, trimmedQuery) },
        )
    }.filter { it.modules.isNotEmpty() || it.units.isNotEmpty() || trimmedQuery.isBlank() }
    val moduleCount = filteredSemesterSnapshotsForYear.sumOf { it.modules.size }
    val unitCount = filteredSemesterSnapshotsForYear.sumOf { it.units.size }
    val tabLabels = listOf(
        stringResource(R.string.tab_exams, filteredGradesForYear.size),
        stringResource(R.string.tab_modules, moduleCount),
        stringResource(R.string.tab_units, unitCount),
    )

    Column(modifier = Modifier.fillMaxSize().clearFocusOnTap(focusManager)) {
        TabRow(selectedTabIndex = state.selectedTab.ordinal) {
            tabLabels.forEachIndexed { index, label ->
                val tab = OasisTab.entries[index]
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(label) },
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isSyncing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                !state.savedSettings.hasAccounts() -> MissingCredentialsState(onOpenSettings = { viewModel.navigate(MainDestination.SETTINGS) })
                gradesForYear.isEmpty() && rawModuleCount == 0 && rawUnitCount == 0 && state.isSyncing -> FirstSyncState()
                gradesForYear.isEmpty() && rawModuleCount == 0 && rawUnitCount == 0 && !state.isSyncing -> EmptyOasisState(onRefresh = viewModel::refresh)
                state.selectedTab == OasisTab.EPREUVES && filteredGradesForYear.isEmpty() && trimmedQuery.isNotBlank() -> SearchEmptyState()
                state.selectedTab == OasisTab.MODULES && moduleCount == 0 && trimmedQuery.isNotBlank() -> SearchEmptyState()
                state.selectedTab == OasisTab.UES && unitCount == 0 && trimmedQuery.isNotBlank() -> SearchEmptyState()
                state.selectedTab == OasisTab.EPREUVES && gradesForYear.isEmpty() -> TabEmptyState(
                    title = stringResource(R.string.tab_exams_plain),
                    body = stringResource(R.string.empty_exams_body),
                )
                state.selectedTab == OasisTab.MODULES && moduleCount == 0 -> TabEmptyState(
                    title = stringResource(R.string.tab_modules_plain),
                    body = stringResource(R.string.empty_modules_body),
                )
                state.selectedTab == OasisTab.UES && unitCount == 0 -> TabEmptyState(
                    title = stringResource(R.string.tab_units_plain),
                    body = stringResource(R.string.empty_units_body),
                )
                state.selectedTab == OasisTab.EPREUVES -> GradesList(
                    grades = filteredGradesForYear,
                    sortMode = state.gradeSortMode,
                    sortAscending = state.gradeSortAscending,
                    onSortModeChange = viewModel::setGradeSortMode,
                )
                state.selectedTab == OasisTab.MODULES -> ModulesList(
                    snapshots = filteredSemesterSnapshotsForYear,
                    onOpenModuleInExams = onOpenModuleInExams,
                )
                else -> UnitsList(snapshots = filteredSemesterSnapshotsForYear)
            }
        }
    }
}

@Composable
private fun SearchEmptyState() {
    TabEmptyState(
        title = stringResource(R.string.search_no_results_title),
        body = stringResource(R.string.search_no_results_body),
    )
}

@Composable
private fun MissingCredentialsState(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(10.dp)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.settings_missing_title), style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.settings_missing_body))
                OutlinedButton(onClick = onOpenSettings) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_open))
                }
            }
        }
    }
}

@Composable
private fun EmptyOasisState(onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.empty_year_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.empty_year_body))
                Button(onClick = onRefresh) {
                    Text(stringResource(R.string.sync_action))
                }
            }
        }
    }
}

@Composable
private fun FirstSyncState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.sync_first_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.sync_first_body))
            }
        }
    }
}

@Composable
private fun TabEmptyState(title: String, body: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(20.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body)
            }
        }
    }
}

// ──────────────────────────────────────────────────
//  Épreuves (Exams) tab
// ──────────────────────────────────────────────────

@Composable
private fun GradesList(
    grades: List<UiGrade>,
    sortMode: GradeSortMode,
    sortAscending: Boolean,
    onSortModeChange: (GradeSortMode) -> Unit,
) {
    // Separate new/updated grades from the rest
    val newGrades = grades.filter { it.changeType != NoteChangeType.NONE }
    val regularGrades = grades.filter { it.changeType == NoteChangeType.NONE }

    // Sort regular grades
    val sortedRegular = when (sortMode) {
        GradeSortMode.DATE -> if (sortAscending) regularGrades.reversed() else regularGrades
        GradeSortMode.MODULE -> regularGrades.sortedWith(compareBy<UiGrade> { it.subjectId }.let { if (sortAscending) it else it.reversed() })
        GradeSortMode.GRADE -> regularGrades.sortedByNumeric({ parseDisplayNumber(it.gradeLabel) }, sortAscending)
        GradeSortMode.AVERAGE -> regularGrades.sortedByNumeric({ parseDisplayNumber(it.averageLabel) }, sortAscending)
        GradeSortMode.RANK_POSITION -> regularGrades.sortedByNumeric({ parseRankPositionNumber(it.rankLabel) }, sortAscending)
        GradeSortMode.RANK_PERCENTAGE -> regularGrades.sortedByNumeric({ parseRankPercentageNumber(it.rankLabel) }, sortAscending)
    }

    val groupedGrades = sortedRegular.groupBy { it.semester }.toList().sortedBy { it.first }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Sort controls
        item {
            GradeSortBar(current = sortMode, ascending = sortAscending, onChange = onSortModeChange)
        }

        // NEW grades section – prominent, separated
        if (newGrades.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.new_grades_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(newGrades, key = { "new-${it.id}" }) { grade ->
                GradeCard(grade = grade, isHighlighted = true)
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        if (sortMode == GradeSortMode.MODULE) {
            val moduleGroups = sortedRegular.groupBy { it.subjectId to it.subject }
                .toList()
                .sortedWith(compareBy<Pair<Pair<String, String>, List<UiGrade>>> { it.first.first }.let { if (sortAscending) it else it.reversed() })

            moduleGroups.forEach { (moduleKey, moduleGrades) ->
                item {
                    UeGroupHeader(code = moduleKey.first, title = moduleKey.second)
                }
                items(moduleGrades, key = { it.id }) { grade ->
                    GradeCard(grade = grade, isHighlighted = false)
                }
            }
        } else {
            // Regular grades grouped by semester
            groupedGrades.forEach { (semester, itemsForSemester) ->
                item {
                    SemesterHeader(semester = semester, subtitle = stringResource(R.string.exam_count, itemsForSemester.size))
                }
                items(itemsForSemester, key = { it.id }) { grade ->
                    GradeCard(grade = grade, isHighlighted = false)
                }
            }
        }
    }
}

@Composable
private fun GradeSortBar(
    current: GradeSortMode,
    ascending: Boolean,
    onChange: (GradeSortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.sort_label),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            FilterChip(
                selected = true,
                onClick = { expanded = true },
                label = { Text(sortModeLabel(current)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                GradeSortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(sortModeLabel(mode)) },
                        onClick = {
                            expanded = false
                            if (mode != current) onChange(mode)
                        },
                        leadingIcon = if (mode == current) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                    )
                }
            }
        }
        FilterChip(
            selected = false,
            onClick = { onChange(current) },
            label = { Text(if (ascending) "↑" else "↓") },
        )
    }
}

@Composable
private fun SemesterHeader(semester: Int, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.semester_title, semester),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GradeCard(grade: UiGrade, isHighlighted: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val hasExtraDetails = grade.averageLabel != "—" || grade.rankLabel != "—" || grade.commentLabel != "—"

    val containerColor = when {
        isHighlighted && grade.changeType == NoteChangeType.NEW ->
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isHighlighted && grade.changeType == NoteChangeType.UPDATED ->
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
        else -> CardDefaults.cardColors().containerColor
    }
    val borderColor = when {
        isHighlighted && grade.changeType == NoteChangeType.NEW -> MaterialTheme.colorScheme.primary
        isHighlighted && grade.changeType == NoteChangeType.UPDATED -> MaterialTheme.colorScheme.tertiary
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderColor?.let { BorderStroke(1.dp, it) },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            grade.subject,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            grade.subjectId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            grade.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            grade.dateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                AssistChip(onClick = { }, label = { Text(grade.gradeLabel) })
            }

            if (hasExtraDetails) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (expanded) stringResource(R.string.collapse_details) else stringResource(R.string.expand_details),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Comment preview (truncated first line when collapsed)
            if (grade.commentLabel != "—" && !expanded) {
                Text(
                    grade.commentLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            AnimatedVisibility(visible = expanded && hasExtraDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider()
                    DetailLine(buildDetailItems(
                        grade.averageLabel.takeIf { it != "—" }?.let { stringResource(R.string.grade_promo_average, it) },
                        grade.rankLabel.takeIf { it != "—" }?.let { stringResource(R.string.grade_rank, it) },
                    ))
                    if (grade.commentLabel != "—") {
                        Text(
                            stringResource(R.string.grade_comment, grade.commentLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────
//  Modules tab – grouped by UE
// ──────────────────────────────────────────────────

@Composable
private fun ModulesList(
    snapshots: List<SemesterSnapshot>,
    onOpenModuleInExams: (ModuleSummary) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        snapshots.forEach { snapshot ->
            item {
                SemesterHeader(
                    semester = snapshot.semester,
                    subtitle = stringResource(R.string.module_count, snapshot.modules.size),
                )
            }

            // Group modules by their UE (groupCode + groupTitle)
            val modulesByGroup = snapshot.modules.groupBy { it.groupCode to it.groupTitle }
            modulesByGroup.forEach { (group, modules) ->
                item {
                    UeGroupHeader(code = group.first, title = group.second)
                }
                items(
                    items = modules,
                    key = { module -> "${module.academicYear}-${module.semester}-${module.groupCode}-${module.code}-${module.title}" },
                ) { module ->
                    ModuleCard(module = module, onOpenInExams = onOpenModuleInExams)
                }
            }
        }
    }
}

@Composable
private fun UeGroupHeader(code: String, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                code,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModuleCard(module: ModuleSummary, onOpenInExams: (ModuleSummary) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenInExams(module) }) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        module.title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        module.code,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                AssistChip(onClick = { }, label = { Text(module.gradeLabel) })
            }
            DetailLine(buildDetailItems(
                module.coefficientLabel.takeIf { it != "—" }?.let { stringResource(R.string.module_coefficient, it) },
                module.blockLabel.takeIf { it != "—" }?.let { stringResource(R.string.module_block, it) },
                module.creditsLabel.takeIf { it != "—" }?.let { stringResource(R.string.module_credits, it) },
                module.averageLabel.takeIf { it != "—" }?.let { stringResource(R.string.metric_average, it) },
                module.rankLabel.takeIf { it != "—" }?.let { stringResource(R.string.metric_rank, it) },
            ))
        }
    }
}

// ──────────────────────────────────────────────────
//  UEs tab
// ──────────────────────────────────────────────────

@Composable
private fun UnitsList(snapshots: List<SemesterSnapshot>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        snapshots.forEach { snapshot ->
            item {
                SemesterHeader(
                    semester = snapshot.semester,
                    subtitle = stringResource(R.string.unit_count, snapshot.units.size),
                )
            }
            items(
                items = snapshot.units,
                key = { unit -> "${unit.academicYear}-${unit.semester}-${unit.code}-${unit.title}" },
            ) { unit ->
                UnitCard(unit = unit)
            }
        }
    }
}

@Composable
private fun UnitCard(unit: UnitSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        unit.title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        unit.code,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                AssistChip(onClick = { }, label = { Text(unit.gradeLabel) })
            }
            DetailLine(buildDetailItems(
                stringResource(R.string.unit_ects, unit.ectsLabel),
                unit.averageLabel.takeIf { it != "—" }?.let { stringResource(R.string.metric_average, it) },
                unit.rankLabel.takeIf { it != "—" }?.let { stringResource(R.string.metric_rank, it) },
                unit.resultLabel.takeIf { it != "—" }?.let { stringResource(R.string.unit_result, it) },
            ))
        }
    }
}

// ──────────────────────────────────────────────────
//  Shared detail helpers
// ──────────────────────────────────────────────────

@Composable
private fun DetailLine(items: List<String>) {
    if (items.isEmpty()) {
        return
    }
    Text(
        text = items.joinToString(" • "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun buildDetailItems(vararg items: String?): List<String> {
    return items.filterNotNull().filter { it.isNotBlank() }
}

// ──────────────────────────────────────────────────
//  Settings page – split into section cards
// ──────────────────────────────────────────────────

@Composable
internal fun SettingsPage(
    state: MainUiState,
    viewModel: MainViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { AccountSettingsCard(state = state, viewModel = viewModel) }
        // Each account owns its controls' remembered animation and gesture state.
        item(key = "sync-${state.savedSettings.selectedAccountOrNull()?.id}") { SyncSettingsCard(state = state, viewModel = viewModel) }
        item(key = "notifications-${state.savedSettings.selectedAccountOrNull()?.id}") { NotificationSettingsCard(state = state, viewModel = viewModel) }
        item { AboutCard() }
    }
}

// ── Account section ──

private data class AccountEditorState(
    val accountId: String? = null,
    val login: String = "",
    val password: String = "",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountSettingsCard(
    state: MainUiState,
    viewModel: MainViewModel,
) {
    val selectedAccount = state.savedSettings.selectedAccountOrNull()
    val noInternetMessage = stringResource(R.string.error_no_internet)
    var editorState by remember(selectedAccount?.id, state.savedSettings.accounts.size) { mutableStateOf<AccountEditorState?>(null) }
    var shouldCloseEditorAfterSave by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSavingAccount, state.errorDialog, state.savedSettings.accounts.size) {
        if (shouldCloseEditorAfterSave && !state.isSavingAccount) {
            if (state.errorDialog == null) {
                editorState = null
            }
            shouldCloseEditorAfterSave = false
        }
    }

    editorState?.let { editor ->
        AccountEditorDialog(
            initialState = editor,
            isSaving = state.isSavingAccount,
            onDismiss = {
                if (!state.isSavingAccount) {
                    editorState = null
                    shouldCloseEditorAfterSave = false
                }
            },
            onConfirm = { login, password, onAuthenticated ->
                shouldCloseEditorAfterSave = true
                viewModel.saveAccount(editor.accountId, login, password, onAuthenticated = onAuthenticated)
            },
        )
    }

    if (showRemoveDialog && selectedAccount != null) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveDialog = false
                        viewModel.removeSelectedAccount()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.account_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(stringResource(R.string.dialog_close))
                }
            },
            title = { Text(stringResource(R.string.account_remove_confirm_title)) },
            text = { Text(stringResource(R.string.account_remove_confirm_body, selectedAccount.resolvedDisplayName())) },
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.settings_section_account), style = MaterialTheme.typography.titleLarge)

            if (selectedAccount == null) {
                Text(
                    stringResource(R.string.account_none_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { editorState = AccountEditorState() }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.account_add))
                }
            } else {
                AccountPickerRow(
                    accounts = state.savedSettings.accounts,
                    selectedAccount = selectedAccount,
                    onSelectAccount = viewModel::selectAccount,
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editorState = AccountEditorState() }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.account_add))
                    }
                    OutlinedButton(onClick = {
                        editorState = AccountEditorState(
                            accountId = selectedAccount.id,
                            login = selectedAccount.login,
                            password = selectedAccount.password,
                        )
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.account_edit))
                    }
                    OutlinedButton(
                        onClick = { showRemoveDialog = true },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.account_remove))
                    }
                }

                if (!selectedAccount.lastSyncError.isNullOrBlank() && selectedAccount.lastSyncError != noInternetMessage) {
                    OutlinedButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.account_retry))
                    }
                }
            }

            Text(
                stringResource(R.string.account_storage_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            ServerUrlSection(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun AccountPickerRow(
    accounts: List<OasisAccount>,
    selectedAccount: OasisAccount,
    onSelectAccount: (String) -> Unit,
) {
    var expanded by remember(selectedAccount.id, accounts.size) { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { expanded = true }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountAvatar(account = selectedAccount, size = 44.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    selectedAccount.resolvedDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    selectedAccount.resolvedStudentId(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(account.resolvedDisplayName(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                account.resolvedStudentId(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = {
                        AccountAvatar(account = account, size = 32.dp)
                    },
                    onClick = {
                        expanded = false
                        onSelectAccount(account.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountAvatar(
    account: OasisAccount,
    size: androidx.compose.ui.unit.Dp,
) {
    val placeholderText = account.resolvedDisplayName().take(1).uppercase().ifBlank { "?" }
    val modifier = Modifier
        .size(size)
        .clip(CircleShape)

    if (!account.profilePhotoUrl.isNullOrBlank()) {
        SubcomposeAsyncImage(
            model = account.profilePhotoUrl,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            loading = {
                PlaceholderAccountAvatar(text = placeholderText, size = size)
            },
            error = {
                PlaceholderAccountAvatar(text = placeholderText, size = size)
            },
        )
    } else {
        PlaceholderAccountAvatar(text = placeholderText, size = size)
    }
}

@Composable
private fun PlaceholderAccountAvatar(
    text: String,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun AccountEditorDialog(
    initialState: AccountEditorState,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, () -> Unit) -> Unit,
) {
    var login by remember(initialState) { mutableStateOf(initialState.login) }
    var password by remember(initialState) { mutableStateOf(initialState.password) }
    val isEditing = initialState.accountId != null
    val autofill = LocalAutofillManager.current
    val dismiss = { autofill?.cancel(); onDismiss() }

    AlertDialog(
        onDismissRequest = dismiss,
        confirmButton = {
            Button(
                onClick = { onConfirm(login, password) { autofill?.commit() } },
                enabled = !isSaving && login.isNotBlank() && password.isNotBlank(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.account_dialog_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss, enabled = !isSaving) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
        title = {
            Text(
                if (isEditing) {
                    stringResource(R.string.account_dialog_edit_title)
                } else {
                    stringResource(R.string.account_dialog_add_title)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountCredentialsFields(
                    login = login,
                    password = password,
                    onLoginChange = { login = it },
                    onPasswordChange = { password = it },
                    enabled = !isSaving,
                )
                Text(
                    stringResource(R.string.account_storage_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

// ── Sync section ──

@Composable
private fun SyncSettingsCard(
    state: MainUiState,
    viewModel: MainViewModel,
) {
    val account = state.savedSettings.selectedAccountOrNull()
    val bgSyncEnabled = account?.canSyncInBackground() == true

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.settings_section_sync), style = MaterialTheme.typography.titleLarge)

            if (account == null) {
                Text(stringResource(R.string.sync_settings_no_account))
            } else {
                Text(stringResource(R.string.account_settings_scope, account.resolvedDisplayName()), style = MaterialTheme.typography.bodySmall)
                SettingToggleRow(
                    icon = { Icon(Icons.Filled.Sync, contentDescription = null) },
                    title = stringResource(R.string.background_sync_title),
                    subtitle = stringResource(R.string.background_sync_body),
                    checked = bgSyncEnabled,
                    onCheckedChange = { viewModel.updatePollingMinutes(if (it) 30 else 0) },
                )
                if (bgSyncEnabled) {
                    PollingSlider(current = account.pollingMinutes, onChange = viewModel::updatePollingMinutes)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::toggleAdvancedSettings),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.advanced_title), style = MaterialTheme.typography.bodyLarge)
                Icon(if (state.advancedSettingsVisible) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            if (state.advancedSettingsVisible) {
                if (account != null) {
                    SettingToggleRow(
                        icon = {},
                        title = stringResource(R.string.sync_unmetered_title),
                        subtitle = stringResource(R.string.sync_unmetered_body),
                        checked = account.syncUnmeteredOnly,
                        onCheckedChange = viewModel::updateSyncUnmeteredOnly,
                    )
                    SettingToggleRow(
                        icon = {},
                        title = stringResource(R.string.sync_charging_title),
                        subtitle = stringResource(R.string.sync_charging_body),
                        checked = account.syncChargingOnly,
                        onCheckedChange = viewModel::updateSyncChargingOnly,
                    )
                }
                SettingToggleRow(
                    icon = {},
                    title = stringResource(R.string.ignore_tls_title),
                    subtitle = stringResource(R.string.ignore_tls_body),
                    checked = state.draftSettings.ignoreTlsErrors,
                    onCheckedChange = viewModel::updateIgnoreTlsErrors,
                )
            }
        }
    }
}

// ── Notification section ──

@Composable
private fun NotificationSettingsCard(
    state: MainUiState,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val selectedAccount = state.savedSettings.selectedAccountOrNull()
    val multipleAccounts = state.savedSettings.accounts.count(OasisAccount::hasCredentials) > 1
    val grades = state.grades
    val currentYear = state.selectedYear ?: currentAcademicYear()
    val modules = state.semesterSnapshots
        .filter { it.academicYear == currentYear }
        .flatMap { it.modules }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.settings_section_notifications), style = MaterialTheme.typography.titleLarge)

            if (multipleAccounts && selectedAccount != null) {
                Text(
                    stringResource(R.string.account_settings_scope, selectedAccount.resolvedDisplayName()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (selectedAccount == null) {
                Text(
                    stringResource(R.string.settings_notifications_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            selectedAccount?.let { account ->
                SettingToggleRow(
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    title = stringResource(R.string.notifications_master_title),
                    subtitle = null,
                    checked = account.notificationsEnabled,
                    onCheckedChange = viewModel::updateNotificationsEnabled,
                    switchModifier = Modifier.testTag("notifications_master_switch"),
                )
                if (account.notificationsEnabled) {
                    NotificationToggleWithTest(
                        title = stringResource(R.string.notifications_new_title),
                        subtitle = stringResource(R.string.notifications_new_body),
                        checked = account.notifyNewGrades,
                        onCheckedChange = viewModel::updateNotifyNewGrades,
                        onTest = {
                            NotificationHelper.notifyPreview(
                                context = context, settings = state.savedSettings, account = account,
                                type = NotificationHelper.NotificationPreviewType.NEW,
                                sampleChanges = buildPreviewChanges(
                                    context = context,
                                    account = account,
                                    grades = grades,
                                    modules = modules,
                                    type = NotificationHelper.NotificationPreviewType.NEW,
                                ),
                            )
                        },
                    )
                    NotificationToggleWithTest(
                        title = stringResource(R.string.notifications_pending_title),
                        subtitle = stringResource(R.string.notifications_pending_body),
                        checked = account.notifyPendingGrades,
                        onCheckedChange = viewModel::updateNotifyPendingGrades,
                        onTest = {
                            NotificationHelper.notifyPreview(
                                context = context, settings = state.savedSettings, account = account,
                                type = NotificationHelper.NotificationPreviewType.PENDING,
                                sampleChanges = buildPreviewChanges(
                                    context = context,
                                    account = account,
                                    grades = grades,
                                    modules = modules,
                                    type = NotificationHelper.NotificationPreviewType.PENDING,
                                ),
                            )
                        },
                    )
                    NotificationToggleWithTest(
                        title = stringResource(R.string.notifications_updated_title),
                        subtitle = stringResource(R.string.notifications_updated_body),
                        checked = account.notifyUpdatedGrades,
                        onCheckedChange = viewModel::updateNotifyUpdatedGrades,
                        onTest = {
                            NotificationHelper.notifyPreview(
                                context = context, settings = state.savedSettings, account = account,
                                type = NotificationHelper.NotificationPreviewType.UPDATED,
                                sampleChanges = buildPreviewChanges(
                                    context = context,
                                    account = account,
                                    grades = grades,
                                    modules = modules,
                                    type = NotificationHelper.NotificationPreviewType.UPDATED,
                                ),
                            )
                        },
                    )
                    NotificationToggleWithTest(
                        title = stringResource(R.string.notifications_errors_title),
                        subtitle = stringResource(R.string.notifications_errors_body),
                        checked = account.notifyErrors,
                        onCheckedChange = viewModel::updateNotifyErrors,
                        onTest = {
                            NotificationHelper.notifyPreview(
                                context = context, settings = state.savedSettings, account = account,
                                type = NotificationHelper.NotificationPreviewType.ERROR,
                                sampleChanges = null,
                            )
                        },
                    )

                    HorizontalDivider()

                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            )
                        }
                    }) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.notifications_system_settings_action))
                    }
                    Text(
                        stringResource(R.string.notifications_system_settings_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleWithTest(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onTest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onTest, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text(stringResource(R.string.notifications_test_action), style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── About section ──

@Composable
private fun AboutCard() {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_section_about), style = MaterialTheme.typography.titleLarge)

            Text(
                stringResource(R.string.about_unofficial),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.about_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.about_contribute),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = buildSupportEmailUri()
                    }
                    runCatching { context.startActivity(intent) }
                }) {
                    Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.about_support_email))
                }
                OutlinedButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPOSITORY_URL))
                    runCatching { context.startActivity(intent) }
                }) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.about_github))
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────
//  Settings shared components
// ──────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PollingSlider(
    current: Int,
    onChange: (Int) -> Unit,
) {
    val options = remember { listOf(15, 30, 60, 360, 1440, 10080) }
    val currentIndex = options.indexOf(current).takeIf { it >= 0 } ?: options.indexOf(30)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.polling_frequency_title, pollingLabel(options[currentIndex])),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = currentIndex.toFloat(),
            onValueChange = { value ->
                val nextIndex = value.roundToInt().coerceIn(0, options.lastIndex)
                onChange(options[nextIndex])
            },
            valueRange = 0f..options.lastIndex.toFloat(),
            steps = options.size - 2,
        )
    }
}

@Composable
private fun ServerUrlSection(state: MainUiState, viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = viewModel::toggleServerUrlSettings),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.oasis_url_label), style = MaterialTheme.typography.bodyLarge)
            Icon(
                if (state.serverUrlVisible) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }
        if (state.serverUrlVisible) {
            OutlinedTextField(
                value = state.draftSettings.oasisBaseUrl,
                onValueChange = viewModel::updateOasisUrl,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = switchModifier)
    }
}

// ──────────────────────────────────────────────────
//  Dialogs
// ──────────────────────────────────────────────────

@Composable
private fun ErrorDetailsDialog(
    dialog: ErrorDialogState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close))
            }
        },
        title = { Text(dialog.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(dialog.message)
                if (dialog.technicalDetails.isNotBlank()) {
                    Card {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.dialog_technical_details), style = MaterialTheme.typography.labelLarge)
                            Text(dialog.technicalDetails, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
    )
}

// ──────────────────────────────────────────────────
//  Utilities
// ──────────────────────────────────────────────────

@Composable
private fun pollingLabel(value: Int): String {
    return when (value) {
        0 -> stringResource(R.string.polling_manual)
        15 -> stringResource(R.string.polling_15m)
        30 -> stringResource(R.string.polling_30m)
        60 -> stringResource(R.string.polling_1h)
        360 -> stringResource(R.string.polling_6h)
        1440 -> stringResource(R.string.polling_1d)
        10080 -> stringResource(R.string.polling_1w)
        else -> value.toString()
    }
}

private fun formatAcademicYear(year: Int?): String {
    if (year == null || year <= 0) {
        return "-"
    }
    return "$year-${year + 1}"
}

@Composable
private fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestampMs
    val diffMin = diffMs / 60_000
    val diffHour = diffMs / 3_600_000
    val diffDay = diffMs / 86_400_000
    return when {
        diffMin < 1 -> stringResource(R.string.sync_time_just_now)
        diffMin < 60 -> stringResource(R.string.sync_time_minutes_ago, diffMin)
        diffHour < 24 -> stringResource(R.string.sync_time_hours_ago, diffHour)
        diffDay < 7 -> stringResource(R.string.sync_time_days_ago, diffDay)
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(timestampMs))
    }
}

private fun matchesGradeSearch(
    grade: UiGrade,
    query: String,
): Boolean {
    if (query.isBlank()) return true
    return listOf(
        grade.subjectId, grade.subject, grade.name,
        grade.gradeLabel, grade.dateText,
        grade.averageLabel, grade.rankLabel,
        grade.commentLabel, grade.coefficientLabel,
    ).any { it.contains(query, ignoreCase = true) }
}

private fun matchesModuleSearch(
    module: ModuleSummary,
    query: String,
): Boolean {
    if (query.isBlank()) return true
    return listOf(
        module.groupCode, module.groupTitle,
        module.code, module.title,
        module.gradeLabel, module.averageLabel,
        module.rankLabel, module.coefficientLabel,
    ).any { it.contains(query, ignoreCase = true) }
}

private fun matchesUnitSearch(
    unit: UnitSummary,
    query: String,
): Boolean {
    if (query.isBlank()) return true
    return listOf(
        unit.code, unit.title, unit.gradeLabel,
        unit.averageLabel, unit.rankLabel, unit.resultLabel,
    ).any { it.contains(query, ignoreCase = true) }
}

private fun List<UiGrade>.sortedByNumeric(selector: (UiGrade) -> Double?, ascending: Boolean): List<UiGrade> {
    return sortedWith(compareBy<UiGrade> {
        val value = selector(it)
        when {
            value == null && ascending -> Double.POSITIVE_INFINITY
            value == null -> Double.NEGATIVE_INFINITY
            else -> value
        }
    }.let { if (ascending) it else it.reversed() })
}

private fun parseDisplayNumber(label: String): Double? {
    return label.replace(',', '.').trim().takeIf { it.isNotBlank() && it != "—" }?.toDoubleOrNull()
}

private fun parseRankParts(label: String): Pair<Double, Double>? {
    val numerator = label.substringBefore('/').trim().takeIf { it.isNotBlank() && it != "—" }?.toDoubleOrNull() ?: return null
    val denominator = label.substringAfter('/', "").trim().takeIf { it.isNotBlank() && it != "—" }?.toDoubleOrNull()
        ?: return null
    if (denominator <= 0.0) {
        return null
    }
    return numerator to denominator
}

private fun parseRankPositionNumber(label: String): Double? {
    return parseRankParts(label)?.first
}

private fun parseRankPercentageNumber(label: String): Double? {
    val (position, total) = parseRankParts(label) ?: return null
    return position / total * 100.0
}

@Composable
private fun sortModeLabel(mode: GradeSortMode): String {
    return when (mode) {
        GradeSortMode.DATE -> stringResource(R.string.sort_by_date)
        GradeSortMode.MODULE -> stringResource(R.string.sort_by_module)
        GradeSortMode.GRADE -> stringResource(R.string.sort_by_grade)
        GradeSortMode.AVERAGE -> stringResource(R.string.sort_by_average)
        GradeSortMode.RANK_POSITION -> stringResource(R.string.sort_by_rank_position)
        GradeSortMode.RANK_PERCENTAGE -> stringResource(R.string.sort_by_rank_percentage)
    }
}

private data class PreviewSeed(
    val subject: String,
    val name: String,
    val numericValue: Double,
)

private fun buildPreviewChanges(
    context: Context,
    account: OasisAccount,
    grades: List<UiGrade>,
    modules: List<ModuleSummary>,
    type: NotificationHelper.NotificationPreviewType,
): List<SyncChange> {
    val random = Random(System.currentTimeMillis())
    val desiredCount = random.nextInt(1, 4)
    val seeds = pickPreviewSeeds(context, grades, modules, desiredCount, random)

    return seeds.map { seed ->
        when (type) {
            NotificationHelper.NotificationPreviewType.NEW -> SyncChange(
                accountId = account.id,
                accountLabel = account.resolvedDisplayName(),
                type = NoteChangeType.NEW,
                subject = seed.subject,
                name = seed.name,
                gradeLabel = formatGrade(seed.numericValue),
                gradePublished = true,
            )
            NotificationHelper.NotificationPreviewType.PENDING -> SyncChange(
                accountId = account.id,
                accountLabel = account.resolvedDisplayName(),
                type = NoteChangeType.NEW,
                subject = seed.subject,
                name = seed.name,
                gradeLabel = formatGrade(seed.numericValue),
                gradePublished = false,
            )
            NotificationHelper.NotificationPreviewType.UPDATED -> {
                val oldValue = previousPreviewValue(seed.numericValue, random)
                val newLabel = formatGrade(seed.numericValue)
                SyncChange(
                    accountId = account.id,
                    accountLabel = account.resolvedDisplayName(),
                    type = NoteChangeType.UPDATED,
                    subject = seed.subject,
                    name = seed.name,
                    gradeLabel = newLabel,
                    gradePublished = true,
                    changedFields = context.getString(
                        R.string.notification_change_grade,
                        formatGrade(oldValue),
                        newLabel,
                    ),
                )
            }
            NotificationHelper.NotificationPreviewType.ERROR -> error("Error previews do not use grade samples")
        }
    }
}

private fun pickPreviewSeeds(
    context: Context,
    grades: List<UiGrade>,
    modules: List<ModuleSummary>,
    count: Int,
    random: Random,
): List<PreviewSeed> {
    val gradeSeeds = grades
        .filter { parseDisplayNumber(it.gradeLabel) != null }
        .map {
            PreviewSeed(
                subject = it.subject,
                name = it.name,
                numericValue = parseDisplayNumber(it.gradeLabel) ?: 14.0,
            )
        }
        .shuffled(random)

    val moduleSeeds = modules
        .map {
            PreviewSeed(
                subject = it.title,
                name = context.getString(R.string.notification_preview_grade_name, it.code),
                numericValue = randomPreviewValue(random),
            )
        }
        .shuffled(random)

    val pool = (gradeSeeds + moduleSeeds).ifEmpty {
        listOf(
            PreviewSeed(
                subject = context.getString(R.string.notification_preview_subject),
                name = context.getString(R.string.notification_preview_fallback_name, 1),
                numericValue = randomPreviewValue(random),
            )
        )
    }

    if (pool.size >= count) {
        return pool.take(count)
    }

    return buildList {
        addAll(pool)
        while (size < count) {
            add(
                PreviewSeed(
                    subject = context.getString(R.string.notification_preview_subject),
                    name = context.getString(R.string.notification_preview_fallback_name, size + 1),
                    numericValue = randomPreviewValue(random),
                )
            )
        }
    }
}

private fun randomPreviewValue(random: Random): Double {
    return (random.nextInt(90, 191) / 10.0)
}

private fun previousPreviewValue(newValue: Double, random: Random): Double {
    val delta = random.nextInt(5, 21) / 10.0
    val candidate = if (random.nextBoolean()) newValue - delta else newValue + delta
    return candidate.coerceIn(0.0, 20.0)
}

private fun buildSupportEmailUri(): Uri {
    return Uri.parse(
        "mailto:alexandre.malfreyt+popsapp@universite-paris-saclay.fr?cc=" +
            Uri.encode("alexandre.malfreyt+popsapp@gmail.com")
    )
}

private fun Modifier.clearFocusOnTap(focusManager: androidx.compose.ui.focus.FocusManager): Modifier {
    return pointerInput(focusManager) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Final)
            val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
            if (up != null) {
                focusManager.clearFocus()
            }
        }
    }
}

private const val GITHUB_REPOSITORY_URL = "https://github.com/AlexMalfr/Pops-app"
