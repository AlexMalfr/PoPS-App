package com.malfreyt.alexandre.pops_app.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.malfreyt.alexandre.pops_app.R
import com.malfreyt.alexandre.pops_app.credentials.OasisCredentialManager
import com.malfreyt.alexandre.pops_app.credentials.findComponentActivity
import com.malfreyt.alexandre.pops_app.data.ModuleSummary
import com.malfreyt.alexandre.pops_app.data.NoteChangeType
import com.malfreyt.alexandre.pops_app.data.UiGrade
import com.malfreyt.alexandre.pops_app.data.UnitSummary
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val coroutineScope = rememberCoroutineScope()
    val settingsSaveEnabled = !state.isSaving && !state.draftSettings.editableEquals(state.savedSettings)
    var credentialRequestInFlight by remember { mutableStateOf(false) }

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
                MainDestination.OASIS -> OasisTopBar(state = state, onSelectYear = viewModel::selectYear, onRefresh = viewModel::refresh)
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
                MainDestination.OASIS -> OasisPage(state = state, viewModel = viewModel)
                MainDestination.SETTINGS -> SettingsPage(
                    state = state,
                    viewModel = viewModel,
                    onCredentialFieldFocus = {
                        if (credentialRequestInFlight) {
                            return@SettingsPage
                        }
                        val currentActivity = activity
                        if (currentActivity == null) {
                            return@SettingsPage
                        }
                        credentialRequestInFlight = true
                        coroutineScope.launch {
                            runCatching {
                                OasisCredentialManager.getPasswordCredential(
                                    activity = currentActivity,
                                    loginHint = state.draftSettings.login.trim().ifBlank { null },
                                )
                            }.onSuccess { credential ->
                                viewModel.applyCredential(credential.login, credential.password)
                            }.onFailure { error ->
                                if (!error.isCredentialFlowCancellation()) {
                                    viewModel.showSnackbarMessage(context.getString(R.string.credentials_load_failed))
                                }
                            }
                            credentialRequestInFlight = false
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OasisTopBar(
    state: MainUiState,
    onSelectYear: (Int) -> Unit,
    onRefresh: () -> Unit,
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
                Text(
                    text = state.savedSettings.lastSyncSummary ?: stringResource(R.string.sync_no_recent),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
            IconButton(onClick = onRefresh, enabled = !state.isSyncing && state.savedSettings.canSync()) {
                if (state.isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh_content_description))
                }
            }
        },
    )
}

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

@Composable
private fun OasisPage(
    state: MainUiState,
    viewModel: MainViewModel,
) {
    val selectedYear = state.selectedYear
    val gradesForYear = state.grades.filter { it.academicYear == selectedYear }
    val semesterSnapshotsForYear = state.semesterSnapshots.filter { it.academicYear == selectedYear }.sortedBy { it.semester }
    val moduleCount = semesterSnapshotsForYear.sumOf { it.modules.size }
    val unitCount = semesterSnapshotsForYear.sumOf { it.units.size }
    val tabLabels = listOf(
        stringResource(R.string.tab_exams, gradesForYear.size),
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

        when {
            !state.savedSettings.hasCredentials() -> MissingCredentialsState(onOpenSettings = { viewModel.navigate(MainDestination.SETTINGS) })
            gradesForYear.isEmpty() && moduleCount == 0 && unitCount == 0 && !state.isSyncing -> EmptyOasisState(onRefresh = viewModel::refresh)
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
            state.selectedTab == OasisTab.EPREUVES -> GradesList(grades = gradesForYear, isSyncing = state.isSyncing)
            state.selectedTab == OasisTab.MODULES -> ModulesList(snapshots = semesterSnapshotsForYear)
            else -> UnitsList(snapshots = semesterSnapshotsForYear)
        }
    }
}

@Composable
private fun MissingCredentialsState(
    onOpenSettings: () -> Unit,
) {
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
private fun EmptyOasisState(
    onRefresh: () -> Unit,
) {
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
private fun TabEmptyState(
    title: String,
    body: String,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(20.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body)
            }
        }
    }
}

@Composable
private fun GradesList(
    grades: List<UiGrade>,
    isSyncing: Boolean,
) {
    val groupedGrades = grades.groupBy { it.semester }.toList().sortedBy { it.first }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (isSyncing) {
            item {
                SyncingBanner()
            }
        }

        groupedGrades.forEach { (semester, itemsForSemester) ->
            item {
                SemesterHeader(semester = semester, subtitle = stringResource(R.string.exam_count, itemsForSemester.size))
            }
            items(itemsForSemester, key = { it.id }) { grade ->
                GradeCard(grade = grade)
            }
        }
    }
}

@Composable
private fun SyncingBanner() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.sync_in_progress))
        }
    }
}

@Composable
private fun SemesterHeader(
    semester: Int,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.semester_title, semester), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModulesList(snapshots: List<com.malfreyt.alexandre.pops_app.data.SemesterSnapshot>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        snapshots.forEach { snapshot ->
            item {
                SemesterHeader(
                    semester = snapshot.semester,
                    subtitle = stringResource(R.string.module_count, snapshot.modules.size),
                )
            }
            items(
                items = snapshot.modules,
                key = { module -> "${module.academicYear}-${module.semester}-${module.groupCode}-${module.code}-${module.title}" },
            ) { module ->
                ModuleCard(module = module)
            }
        }
    }
}

@Composable
private fun UnitsList(snapshots: List<com.malfreyt.alexandre.pops_app.data.SemesterSnapshot>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
private fun GradeCard(grade: UiGrade) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            if (grade.changeType != NoteChangeType.NONE) {
                HorizontalDivider()
                Text(
                    if (grade.changeType == NoteChangeType.NEW) stringResource(R.string.grade_new) else stringResource(R.string.grade_updated),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ModuleCard(module: ModuleSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(module.title, style = MaterialTheme.typography.titleMedium)
                    Text(module.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = { }, label = { Text(module.gradeLabel) })
            }
            Text(
                stringResource(R.string.module_group_format, module.groupCode, module.groupTitle),
                style = MaterialTheme.typography.bodyMedium,
            )
            DetailLine(buildDetailItems(
                stringResource(R.string.module_coefficient, module.coefficientLabel),
                module.blockLabel.takeIf { it != "—" }?.let { stringResource(R.string.module_block, it) },
                module.creditsLabel.takeIf { it != "—" }?.let { stringResource(R.string.module_credits, it) },
                module.averageLabel.takeIf { it != "—" }?.let { stringResource(R.string.metric_average, it) },
                module.rankLabel.takeIf { it != "—" }?.let { stringResource(R.string.metric_rank, it) },
            ))
        }
    }
}

@Composable
private fun UnitCard(unit: UnitSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(unit.title, style = MaterialTheme.typography.titleMedium)
                    Text(unit.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(onClick = { }, label = { Text(unit.gradeLabel) })
            }
            DetailLine(buildDetailItems(
                stringResource(R.string.unit_ects, unit.ectsLabel),
                unit.averageLabel.takeIf { it != "—" }?.let { stringResource(R.string.metric_average, it) },
                unit.rankLabel.takeIf { it != "—" }?.let { stringResource(R.string.metric_rank, it) },
                unit.resultLabel.takeIf { it != "—" }?.let { stringResource(R.string.unit_result, it) },
                unit.isCommonCore.takeIf { it }?.let { stringResource(R.string.unit_common_core) },
            ))
        }
    }
}

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

@Composable
private fun SettingsPage(
    state: MainUiState,
    viewModel: MainViewModel,
    onCredentialFieldFocus: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OasisSettingsCard(
                state = state,
                viewModel = viewModel,
                onCredentialFieldFocus = onCredentialFieldFocus,
            )
        }
    }
}

@Composable
private fun OasisSettingsCard(
    state: MainUiState,
    viewModel: MainViewModel,
    onCredentialFieldFocus: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.settings_category_oasis), style = MaterialTheme.typography.titleLarge)

            SectionTitle(stringResource(R.string.settings_section_status))
            Text(state.savedSettings.lastSyncSummary ?: stringResource(R.string.sync_never_recorded))
            state.savedSettings.lastSyncAt?.let {
                Text(
                    stringResource(
                        R.string.last_attempt,
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.savedSettings.lastSyncError?.let {
                Text(stringResource(R.string.last_error, it), color = MaterialTheme.colorScheme.error)
            }

            HorizontalDivider()

            OutlinedTextField(
                value = state.draftSettings.login,
                onValueChange = viewModel::updateLogin,
                label = { Text(stringResource(R.string.login_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (it.isFocused) {
                            onCredentialFieldFocus()
                        }
                    },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.draftSettings.password,
                onValueChange = viewModel::updatePassword,
                label = { Text(stringResource(R.string.password_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (it.isFocused) {
                            onCredentialFieldFocus()
                        }
                    },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )

            ServerUrlSection(state = state, viewModel = viewModel)

            HorizontalDivider()

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
                PollingSlider(
                    current = state.draftSettings.pollingMinutes,
                    onChange = viewModel::updatePollingMinutes,
                )
            }

            HorizontalDivider()

            AdvancedSection(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PollingSlider(
    current: Int,
    onChange: (Int) -> Unit,
) {
    val options = remember { listOf(0, 15, 30, 60, 360, 1440, 10080) }
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
private fun AdvancedSection(state: MainUiState, viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = viewModel::toggleAdvancedSettings),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.advanced_title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                if (state.advancedSettingsVisible) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }
        if (state.advancedSettingsVisible) {
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

private fun Throwable.isCredentialFlowCancellation(): Boolean {
    return javaClass.simpleName.contains("Cancellation", ignoreCase = true)
}