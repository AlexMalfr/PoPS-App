package com.malfreyt.alexandre.pops_app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.malfreyt.alexandre.pops_app.R
import com.malfreyt.alexandre.pops_app.credentials.OasisCredentialManager
import com.malfreyt.alexandre.pops_app.credentials.findComponentActivity
import com.malfreyt.alexandre.pops_app.data.ModuleSummary
import com.malfreyt.alexandre.pops_app.data.NoteChangeType
import com.malfreyt.alexandre.pops_app.data.OasisAccount
import com.malfreyt.alexandre.pops_app.data.SemesterSnapshot
import com.malfreyt.alexandre.pops_app.data.UiGrade
import com.malfreyt.alexandre.pops_app.data.UnitSummary
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

enum class SearchMode {
    ALL,
    NAME,
    CODE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val settingsSaveEnabled = !state.isSaving && !state.draftSettings.editableEquals(state.savedSettings)
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchMode by rememberSaveable { mutableStateOf(SearchMode.ALL) }

    LaunchedEffect(state.snackbarToken) {
        val message = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbar()
    }

    LaunchedEffect(state.pendingCredentialSave) {
        val request = state.pendingCredentialSave ?: return@LaunchedEffect
        val currentActivity = activity
        if (currentActivity != null) {
            runCatching {
                OasisCredentialManager.savePasswordCredential(
                    activity = currentActivity,
                    login = request.login,
                    password = request.password,
                )
            }
        }
        viewModel.consumeCredentialSaveRequest()
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
                    searchVisible = searchVisible,
                    hasActiveSearch = searchQuery.isNotBlank(),
                    onToggleSearch = { searchVisible = !searchVisible },
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
                    searchVisible = searchVisible,
                    searchQuery = searchQuery,
                    searchMode = searchMode,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchModeChange = { searchMode = it },
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
    searchVisible: Boolean,
    hasActiveSearch: Boolean,
    onToggleSearch: () -> Unit,
) {
    val years = (state.grades.map { it.academicYear } + state.semesterSnapshots.map { it.academicYear })
        .distinct()
        .sortedDescending()
        .ifEmpty { listOf(state.selectedYear ?: 0) }
    var expanded by remember(state.selectedYear, years) { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.bottom_nav_oasis))
                SyncStatusSubtitle(state = state)
            }
        },
        actions = {
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

            IconButton(onClick = onToggleSearch) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.search_action),
                    tint = if (searchVisible || hasActiveSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OasisKebabMenu(
                state = state,
                onRefresh = onRefresh,
                onHardRefresh = onHardRefresh,
            )
        },
    )
}

@Composable
private fun SyncStatusSubtitle(state: MainUiState) {
    val settings = state.savedSettings
    val selectedAccount = settings.selectedAccountOrNull()
    val hasError = selectedAccount?.lastSyncError != null
    val bgOff = settings.pollingMinutes == 0
    val lastSuccess = selectedAccount?.lastSyncAt?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
    }

    val icon = when {
        hasError -> Icons.Filled.Error
        bgOff -> Icons.Filled.CloudOff
        else -> null
    }
    val text = when {
        lastSuccess != null -> stringResource(R.string.sync_status_last_success, lastSuccess)
        hasError -> selectedAccount?.lastSyncError ?: stringResource(R.string.sync_status_error)
        bgOff -> stringResource(R.string.sync_status_bg_off)
        else -> stringResource(R.string.sync_no_recent)
    }
    val color = when {
        hasError && lastSuccess == null -> MaterialTheme.colorScheme.error
        bgOff -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
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
) {
    val context = LocalContext.current
    val oasisBaseUrl = state.savedSettings.oasisBaseUrl
    val canSync = !state.isSyncing && state.savedSettings.selectedAccountCanSync()
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            if (state.isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.MoreVert, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
    searchVisible: Boolean,
    searchQuery: String,
    searchMode: SearchMode,
    onSearchQueryChange: (String) -> Unit,
    onSearchModeChange: (SearchMode) -> Unit,
) {
    val selectedYear = state.selectedYear
    val gradesForYear = state.grades.filter { it.academicYear == selectedYear }
    val semesterSnapshotsForYear = state.semesterSnapshots.filter { it.academicYear == selectedYear }.sortedBy { it.semester }
    val rawModuleCount = semesterSnapshotsForYear.sumOf { it.modules.size }
    val rawUnitCount = semesterSnapshotsForYear.sumOf { it.units.size }
    val trimmedQuery = searchQuery.trim()
    val filteredGradesForYear = gradesForYear.filter { matchesGradeSearch(it, trimmedQuery, searchMode) }
    val filteredSemesterSnapshotsForYear = semesterSnapshotsForYear.map { snapshot ->
        snapshot.copy(
            modules = snapshot.modules.filter { matchesModuleSearch(it, trimmedQuery, searchMode) },
            units = snapshot.units.filter { matchesUnitSearch(it, trimmedQuery, searchMode) },
        )
    }.filter { it.modules.isNotEmpty() || it.units.isNotEmpty() || trimmedQuery.isBlank() }
    val moduleCount = filteredSemesterSnapshotsForYear.sumOf { it.modules.size }
    val unitCount = filteredSemesterSnapshotsForYear.sumOf { it.units.size }
    val tabLabels = listOf(
        stringResource(R.string.tab_exams, filteredGradesForYear.size),
        stringResource(R.string.tab_modules, moduleCount),
        stringResource(R.string.tab_units, unitCount),
    )

    Column(modifier = Modifier.fillMaxSize()) {
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

        AnimatedVisibility(visible = searchVisible) {
            SearchPanel(
                query = searchQuery,
                searchMode = searchMode,
                onQueryChange = onSearchQueryChange,
                onSearchModeChange = onSearchModeChange,
            )
        }

        PullToRefreshBox(
            isRefreshing = state.isSyncing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                !state.savedSettings.hasAccounts() -> MissingCredentialsState(onOpenSettings = { viewModel.navigate(MainDestination.SETTINGS) })
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
                    onSortModeChange = viewModel::setGradeSortMode,
                )
                state.selectedTab == OasisTab.MODULES -> ModulesList(snapshots = filteredSemesterSnapshotsForYear)
                else -> UnitsList(snapshots = filteredSemesterSnapshotsForYear)
            }
        }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    searchMode: SearchMode,
    onQueryChange: (String) -> Unit,
    onSearchModeChange: (SearchMode) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = searchMode == SearchMode.ALL,
                onClick = { onSearchModeChange(SearchMode.ALL) },
                label = { Text(stringResource(R.string.search_filter_all)) },
            )
            FilterChip(
                selected = searchMode == SearchMode.NAME,
                onClick = { onSearchModeChange(SearchMode.NAME) },
                label = { Text(stringResource(R.string.search_filter_name)) },
            )
            FilterChip(
                selected = searchMode == SearchMode.CODE,
                onClick = { onSearchModeChange(SearchMode.CODE) },
                label = { Text(stringResource(R.string.search_filter_code)) },
            )
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
    onSortModeChange: (GradeSortMode) -> Unit,
) {
    // Separate new/updated grades from the rest
    val newGrades = grades.filter { it.changeType != NoteChangeType.NONE }
    val regularGrades = grades.filter { it.changeType == NoteChangeType.NONE }

    // Sort regular grades
    val sortedRegular = when (sortMode) {
        GradeSortMode.DATE -> regularGrades // already sorted by date from repo
        GradeSortMode.MODULE -> regularGrades.sortedBy { it.subjectId }
        GradeSortMode.GRADE -> regularGrades.sortedByDescending {
            it.gradeLabel.toDoubleOrNull() ?: Double.MIN_VALUE
        }
    }

    val groupedGrades = sortedRegular.groupBy { it.semester }.toList().sortedBy { it.first }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Sort controls
        item {
            GradeSortBar(current = sortMode, onChange = onSortModeChange)
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

@Composable
private fun GradeSortBar(
    current: GradeSortMode,
    onChange: (GradeSortMode) -> Unit,
) {
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
        FilterChip(
            selected = current == GradeSortMode.DATE,
            onClick = { onChange(GradeSortMode.DATE) },
            label = { Text(stringResource(R.string.sort_by_date)) },
        )
        FilterChip(
            selected = current == GradeSortMode.MODULE,
            onClick = { onChange(GradeSortMode.MODULE) },
            label = { Text(stringResource(R.string.sort_by_module)) },
        )
        FilterChip(
            selected = current == GradeSortMode.GRADE,
            onClick = { onChange(GradeSortMode.GRADE) },
            label = { Text(stringResource(R.string.sort_by_grade)) },
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

            // Expand / collapse
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

            AnimatedVisibility(visible = expanded) {
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
private fun ModulesList(snapshots: List<SemesterSnapshot>) {
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
                    ModuleCard(module = module)
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
private fun ModuleCard(module: ModuleSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
private fun SettingsPage(
    state: MainUiState,
    viewModel: MainViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { AccountSettingsCard(state = state, viewModel = viewModel) }
        item { SyncSettingsCard(state = state, viewModel = viewModel) }
        item { NotificationSettingsCard(state = state, viewModel = viewModel) }
        item { AdvancedSettingsCard(state = state, viewModel = viewModel) }
        item { AboutCard() }
    }
}

// ── Account section ──

private data class AccountEditorState(
    val accountId: String? = null,
    val login: String = "",
    val password: String = "",
)

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

    LaunchedEffect(state.isSavingAccount, state.errorDialog, state.pendingCredentialSave, state.savedSettings.accounts.size) {
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
            onConfirm = { login, password ->
                shouldCloseEditorAfterSave = true
                viewModel.saveAccount(editor.accountId, login, password)
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    onConfirm: (String, String) -> Unit,
) {
    var login by remember(initialState) { mutableStateOf(initialState.login) }
    var password by remember(initialState) { mutableStateOf(initialState.password) }
    val isEditing = initialState.accountId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onConfirm(login, password) },
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
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.dialog_close))
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
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text(stringResource(R.string.login_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
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
    val bgSyncEnabled = state.draftSettings.pollingMinutes > 0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.settings_section_sync), style = MaterialTheme.typography.titleLarge)

            SettingToggleRow(
                icon = { Icon(Icons.Filled.Sync, contentDescription = null) },
                title = stringResource(R.string.background_sync_title),
                subtitle = stringResource(R.string.background_sync_body),
                checked = bgSyncEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        viewModel.updatePollingMinutes(30) // default to 30 min when toggling on
                    } else {
                        viewModel.updatePollingMinutes(0) // manual
                    }
                },
            )

            if (bgSyncEnabled) {
                PollingSlider(
                    current = state.draftSettings.pollingMinutes,
                    onChange = viewModel::updatePollingMinutes,
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.settings_section_notifications), style = MaterialTheme.typography.titleLarge)

            SettingToggleRow(
                icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                title = stringResource(R.string.notifications_master_title),
                subtitle = null,
                checked = state.draftSettings.notificationsEnabled,
                onCheckedChange = viewModel::updateNotificationsEnabled,
            )
            if (state.draftSettings.notificationsEnabled) {
                SettingToggleRow(
                    icon = { Box(modifier = Modifier.size(24.dp)) },
                    title = stringResource(R.string.notifications_new_title),
                    subtitle = stringResource(R.string.notifications_new_body),
                    checked = state.draftSettings.notifyNewGrades,
                    onCheckedChange = viewModel::updateNotifyNewGrades,
                )
                SettingToggleRow(
                    icon = { Box(modifier = Modifier.size(24.dp)) },
                    title = stringResource(R.string.notifications_pending_title),
                    subtitle = stringResource(R.string.notifications_pending_body),
                    checked = state.draftSettings.notifyPendingGrades,
                    onCheckedChange = viewModel::updateNotifyPendingGrades,
                )
                SettingToggleRow(
                    icon = { Box(modifier = Modifier.size(24.dp)) },
                    title = stringResource(R.string.notifications_updated_title),
                    subtitle = stringResource(R.string.notifications_updated_body),
                    checked = state.draftSettings.notifyUpdatedGrades,
                    onCheckedChange = viewModel::updateNotifyUpdatedGrades,
                )
                SettingToggleRow(
                    icon = { Box(modifier = Modifier.size(24.dp)) },
                    title = stringResource(R.string.notifications_errors_title),
                    subtitle = stringResource(R.string.notifications_errors_body),
                    checked = state.draftSettings.notifyErrors,
                    onCheckedChange = viewModel::updateNotifyErrors,
                )
            }
        }
    }
}

// ── Advanced section ──

@Composable
private fun AdvancedSettingsCard(
    state: MainUiState,
    viewModel: MainViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.advanced_title), style = MaterialTheme.typography.titleLarge)

            SettingToggleRow(
                icon = { Box(modifier = Modifier.size(24.dp)) },
                title = stringResource(R.string.ignore_tls_title),
                subtitle = stringResource(R.string.ignore_tls_body),
                checked = state.draftSettings.ignoreTlsErrors,
                onCheckedChange = viewModel::updateIgnoreTlsErrors,
            )
        }
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

            // TODO: replace with BuildConfig.VERSION_NAME once build is configured
            Text(
                stringResource(R.string.about_version, "0.1.0-dev"),
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
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
    var detailsExpanded by remember(dialog) { mutableStateOf(false) }

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
                OutlinedButton(onClick = { detailsExpanded = !detailsExpanded }) {
                    Icon(
                        if (detailsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (detailsExpanded) stringResource(R.string.dialog_hide_details) else stringResource(R.string.dialog_show_details)
                    )
                }
                if (detailsExpanded) {
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

private fun matchesGradeSearch(
    grade: UiGrade,
    query: String,
    searchMode: SearchMode,
): Boolean {
    if (query.isBlank()) {
        return true
    }
    val haystack = when (searchMode) {
        SearchMode.ALL -> listOf(
            grade.subjectId,
            grade.subject,
            grade.name,
            grade.gradeLabel,
            grade.dateText,
            grade.averageLabel,
            grade.rankLabel,
            grade.commentLabel,
            grade.coefficientLabel,
        )
        SearchMode.NAME -> listOf(grade.subject, grade.name, grade.commentLabel)
        SearchMode.CODE -> listOf(grade.subjectId)
    }
    return haystack.any { it.contains(query, ignoreCase = true) }
}

private fun matchesModuleSearch(
    module: ModuleSummary,
    query: String,
    searchMode: SearchMode,
): Boolean {
    if (query.isBlank()) {
        return true
    }
    val haystack = when (searchMode) {
        SearchMode.ALL -> listOf(
            module.groupCode,
            module.groupTitle,
            module.code,
            module.title,
            module.gradeLabel,
            module.averageLabel,
            module.rankLabel,
            module.coefficientLabel,
        )
        SearchMode.NAME -> listOf(module.groupTitle, module.title)
        SearchMode.CODE -> listOf(module.groupCode, module.code)
    }
    return haystack.any { it.contains(query, ignoreCase = true) }
}

private fun matchesUnitSearch(
    unit: UnitSummary,
    query: String,
    searchMode: SearchMode,
): Boolean {
    if (query.isBlank()) {
        return true
    }
    val haystack = when (searchMode) {
        SearchMode.ALL -> listOf(unit.code, unit.title, unit.gradeLabel, unit.averageLabel, unit.rankLabel, unit.resultLabel)
        SearchMode.NAME -> listOf(unit.title)
        SearchMode.CODE -> listOf(unit.code)
    }
    return haystack.any { it.contains(query, ignoreCase = true) }
}

private fun buildSupportEmailUri(): Uri {
    return Uri.parse(
        "mailto:alexandre.malfreyt+popsapp@universite-paris-saclay.fr?cc=" +
            Uri.encode("alexandre.malfreyt+popsapp@gmail.com")
    )
}

private const val GITHUB_REPOSITORY_URL = "https://github.com/AlexMalfr/Pops-app"
