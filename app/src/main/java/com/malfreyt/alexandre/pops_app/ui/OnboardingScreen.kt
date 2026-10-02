package com.malfreyt.alexandre.pops_app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import com.malfreyt.alexandre.pops_app.R
import com.malfreyt.alexandre.pops_app.data.DEFAULT_OASIS_BASE_URL
import com.malfreyt.alexandre.pops_app.data.normalizeOasisBaseUrl
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGE_COUNT = 4
private const val DONTKILLMYAPP_URL = "https://dontkillmyapp.com/"

@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    state: MainUiState,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val autofill = LocalAutofillManager.current
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val scope = rememberCoroutineScope()

    val hasAccount = state.savedSettings.accounts.isNotEmpty()

    // ── Page 2: Login state (lifted so the bottom bar can trigger login) ──
    var onboardingLogin by remember { mutableStateOf("") }
    var onboardingPassword by remember { mutableStateOf("") }
    var onboardingServerUrl by rememberSaveable {
        mutableStateOf(state.savedSettings.oasisBaseUrl.takeUnless { it == DEFAULT_OASIS_BASE_URL }.orEmpty())
    }

    // ── Page 3: Notifications state ──
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationsSkipped by remember { mutableStateOf(false) }
    var notifDeniedPermanently by remember { mutableStateOf(false) }
    var notifRequestCount by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // If rationale is false after denial, the user selected "Don't ask again"
            val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS
            ) ?: false
            if (!shouldShowRationale && notifRequestCount > 0) {
                notifDeniedPermanently = true
            }
        }
        notifRequestCount++
    }

    // ── Page 4: Battery state ──
    var batteryOptimizationIgnored by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    var batterySkipped by remember { mutableStateOf(false) }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        batteryOptimizationIgnored = isBatteryOptimizationIgnored(context)
    }

    // Launcher for opening app notification settings (re-checks permission on return)
    val notifSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Auto-advance when permission is granted
    LaunchedEffect(notificationsGranted) {
        if (notificationsGranted && pagerState.currentPage == 2) {
            pagerState.animateScrollToPage(3)
        }
    }
    // Auto-advance when account is added on page 2 + start first sync
    LaunchedEffect(hasAccount) {
        if (hasAccount && pagerState.currentPage == 1) {
            viewModel.refresh()
            pagerState.animateScrollToPage(2)
        }
    }

    val isLastPage = pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1

    val canProceed = when (pagerState.currentPage) {
        1 -> hasAccount
        2 -> notificationsGranted || notificationsSkipped
        3 -> batteryOptimizationIgnored || batterySkipped
        else -> true
    }

    fun goBack() {
        if (pagerState.currentPage == 1) autofill?.cancel()
        val targetPage = pagerState.currentPage - 1
        // Reset skip state of the page we're navigating back to
        when (targetPage) {
            2 -> { notificationsSkipped = false; notifDeniedPermanently = false }
            3 -> batterySkipped = false
        }
        scope.launch { pagerState.animateScrollToPage(targetPage) }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1).toFloat() / ONBOARDING_PAGE_COUNT },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false,
            ) { page ->
                val selectedAccount = state.savedSettings.selectedAccountOrNull()
                when (page) {
                    0 -> OnboardingDisclaimerPage()
                    1 -> OnboardingDataPage(
                        state = state,
                        login = onboardingLogin,
                        password = onboardingPassword,
                        onLoginChange = { onboardingLogin = it },
                        onPasswordChange = { onboardingPassword = it },
                        serverUrl = onboardingServerUrl,
                        onServerUrlChange = { onboardingServerUrl = it },
                        onLogout = {
                            viewModel.removeSelectedAccount()
                            onboardingLogin = ""
                            onboardingPassword = ""
                        },
                    )
                    2 -> OnboardingNotificationsPage(
                        permissionGranted = notificationsGranted,
                        permanentlyDenied = notifDeniedPermanently,
                        displayName = selectedAccount?.displayName ?: "",
                        profilePhotoUrl = selectedAccount?.profilePhotoUrl,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onOpenSettings = {
                            runCatching {
                                notifSettingsLauncher.launch(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                )
                            }
                        },
                    )
                    3 -> OnboardingBatteryPage(
                        batteryOptimizationIgnored = batteryOptimizationIgnored,
                        onDisableBatteryOptimization = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            batteryLauncher.launch(intent)
                        },
                        onOpenBatterySettings = {
                            runCatching {
                                batteryLauncher.launch(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                )
                            }
                        },
                    )
                }
            }

            // ── Bottom navigation bar ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Skip buttons for pages 3 and 4
                if (pagerState.currentPage == 2 && !notificationsGranted && !notificationsSkipped) {
                    TextButton(onClick = {
                        notificationsSkipped = true
                        scope.launch { pagerState.animateScrollToPage(3) }
                    }) {
                        Text(stringResource(R.string.onboarding_notifications_skip))
                    }
                }
                if (pagerState.currentPage == 3 && !batteryOptimizationIgnored && !batterySkipped) {
                    TextButton(onClick = {
                        batterySkipped = true
                        onComplete()
                    }) {
                        Text(stringResource(R.string.onboarding_battery_skip))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pagerState.currentPage > 0) {
                        TextButton(onClick = ::goBack) {
                            Text(stringResource(R.string.onboarding_back))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (pagerState.currentPage == 1) {
                        // Login button that also advances to next page
                        Button(
                            onClick = {
                                if (hasAccount) {
                                    scope.launch { pagerState.animateScrollToPage(2) }
                                } else {
                                    viewModel.saveAccount(null, onboardingLogin, onboardingPassword, oasisBaseUrl = onboardingServerUrl) { autofill?.commit() }
                                }
                            },
                            enabled = if (hasAccount) true
                                else !state.isSavingAccount && onboardingLogin.isNotBlank() && onboardingPassword.isNotBlank() && normalizeOasisBaseUrl(onboardingServerUrl) != null,
                        ) {
                            if (state.isSavingAccount) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                if (hasAccount) stringResource(R.string.onboarding_next)
                                else stringResource(R.string.onboarding_login_action)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    } else if (isLastPage) {
                        Button(
                            onClick = onComplete,
                            enabled = canProceed,
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.onboarding_get_started))
                        }
                    } else {
                        Button(
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            },
                            enabled = canProceed,
                        ) {
                            Text(stringResource(R.string.onboarding_next))
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Page 1: Unofficial app disclaimer ──

@Composable
private fun OnboardingDisclaimerPage() {
    OnboardingPageScaffold(
        icon = { Text("\uD83D\uDC4B", fontSize = 56.sp) },
        title = stringResource(R.string.onboarding_disclaimer_title),
    ) {
        Text(
            stringResource(R.string.onboarding_disclaimer_body),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ── Page 2: Oasis login ──

@Composable
internal fun OnboardingDataPage(
    state: MainUiState,
    login: String,
    password: String,
    onLoginChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val hasAccount = state.savedSettings.accounts.isNotEmpty()
    val selectedAccount = state.savedSettings.selectedAccountOrNull()
    var showServerUrl by rememberSaveable { mutableStateOf(false) }

    OnboardingPageScaffold(
        icon = {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = stringResource(R.string.onboarding_data_title),
    ) {
        Text(
            stringResource(R.string.onboarding_data_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (hasAccount && selectedAccount != null) {
            Text(
                stringResource(R.string.onboarding_login_success, selectedAccount.resolvedDisplayName()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onLogout) {
                Text(
                    stringResource(R.string.onboarding_logout),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            AccountCredentialsFields(
                login = login,
                password = password,
                onLoginChange = onLoginChange,
                onPasswordChange = onPasswordChange,
                enabled = !state.isSavingAccount,
            )
            state.errorDialog?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    error.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_data_privacy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { showServerUrl = !showServerUrl }, enabled = !state.isSavingAccount) {
                Text(stringResource(R.string.onboarding_server_action))
            }
            if (showServerUrl) {
                val validUrl = normalizeOasisBaseUrl(serverUrl) != null
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text(stringResource(R.string.oasis_url_label)) },
                    placeholder = { Text(DEFAULT_OASIS_BASE_URL) },
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_server_url"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    enabled = !state.isSavingAccount,
                    isError = !validUrl,
                    supportingText = if (validUrl) null else { { Text(stringResource(R.string.error_server_url)) } },
                )
            }
        }
    }
}

// ── Page 3: Notifications permission ──

@Composable
private fun OnboardingNotificationsPage(
    permissionGranted: Boolean,
    permanentlyDenied: Boolean,
    displayName: String,
    profilePhotoUrl: String?,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val firstName = extractFirstName(displayName)

    OnboardingPageScaffold(
        icon = {
            if (!profilePhotoUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = profilePhotoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    error = {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    },
                )
            } else {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        title = if (firstName.isNotBlank()) {
            stringResource(R.string.onboarding_notifications_welcome, firstName)
        } else {
            stringResource(R.string.onboarding_notifications_title)
        },
    ) {
        Text(
            stringResource(R.string.onboarding_notifications_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (permissionGranted) {
            Text(
                stringResource(R.string.onboarding_notifications_granted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.onboarding_open_app_settings))
            }
        } else if (permanentlyDenied) {
            Text(
                stringResource(R.string.onboarding_notifications_denied_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_open_app_settings))
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(onClick = onRequestPermission) {
                Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_notifications_grant_action))
            }
        }
    }
}

// ── Page 4: Battery optimization ──

@Composable
private fun OnboardingBatteryPage(
    batteryOptimizationIgnored: Boolean,
    onDisableBatteryOptimization: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val context = LocalContext.current

    OnboardingPageScaffold(
        icon = {
            Icon(
                Icons.Filled.Sync,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = stringResource(R.string.onboarding_battery_title),
    ) {
        Text(
            stringResource(R.string.onboarding_battery_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (batteryOptimizationIgnored) {
            Text(
                stringResource(R.string.onboarding_battery_already_good),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onOpenBatterySettings) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.onboarding_open_battery_settings))
            }
        } else {
            OutlinedButton(onClick = onDisableBatteryOptimization) {
                Icon(Icons.Filled.BatteryAlert, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_battery_disable_action))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DONTKILLMYAPP_URL))
            runCatching { context.startActivity(intent) }
        }) {
            Text(stringResource(R.string.onboarding_battery_learn_more))
        }
    }
}

// ── Shared page layout ──

private fun extractFirstName(displayName: String): String {
    val parts = displayName.trim().split("\\s+".toRegex())
    return parts.drop(1).joinToString(" ").ifBlank { parts.firstOrNull() ?: displayName }
}

@Composable
private fun OnboardingPageScaffold(
    icon: @Composable () -> Unit,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

// ──────────────────────────────────────────────────
//  Battery optimization dialog (shown on every launch)
// ──────────────────────────────────────────────────

@Composable
fun BatteryOptimizationDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.BatteryAlert, contentDescription = null) },
        title = { Text(stringResource(R.string.battery_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.battery_dialog_body))
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DONTKILLMYAPP_URL))
                    runCatching { context.startActivity(intent) }
                }) {
                    Text(stringResource(R.string.battery_dialog_learn_more))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                runCatching { context.startActivity(intent) }
                onDismiss()
            }) {
                Text(stringResource(R.string.battery_dialog_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.battery_dialog_dismiss))
            }
        },
    )
}

// ──────────────────────────────────────────────────
//  Utility
// ──────────────────────────────────────────────────

fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
