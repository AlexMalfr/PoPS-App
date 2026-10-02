package com.malfreyt.alexandre.pops_app

import android.Manifest
import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.malfreyt.alexandre.pops_app.ui.BatteryOptimizationDialog
import com.malfreyt.alexandre.pops_app.ui.MainDestination
import com.malfreyt.alexandre.pops_app.ui.MainScreen
import com.malfreyt.alexandre.pops_app.ui.MainViewModel
import com.malfreyt.alexandre.pops_app.ui.MainViewModelFactory
import com.malfreyt.alexandre.pops_app.ui.OnboardingScreen
import com.malfreyt.alexandre.pops_app.ui.isBatteryOptimizationIgnored
import com.malfreyt.alexandre.pops_app.ui.theme.PoPSTheme

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as PoPSApplication
            val factory = remember(app.container) { MainViewModelFactory(app.container) }
            val viewModel: MainViewModel = viewModel(factory = factory)
            val state by viewModel.uiState.collectAsState()
            val openNotificationPreferences = remember(intent) {
                intent?.categories?.contains(Notification.INTENT_CATEGORY_NOTIFICATION_PREFERENCES) == true
            }

            PoPSTheme {
                if (!state.savedSettings.onboardingCompleted) {
                    OnboardingScreen(
                        viewModel = viewModel,
                        state = state,
                        onComplete = { viewModel.completeOnboarding() },
                    )
                } else {
                    LaunchedEffect(openNotificationPreferences) {
                        if (openNotificationPreferences) {
                            viewModel.navigate(MainDestination.SETTINGS)
                        }
                    }

                    BatteryOptimizationEffect(
                        backgroundSyncEnabled = state.savedSettings.hasAnyBackgroundSyncAccount(),
                    )

                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun BatteryOptimizationEffect(backgroundSyncEnabled: Boolean) {
    val context = LocalContext.current
    var showDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(backgroundSyncEnabled) {
        if (backgroundSyncEnabled && !isBatteryOptimizationIgnored(context)) {
            showDialog = true
        }
    }

    if (showDialog) {
        BatteryOptimizationDialog(onDismiss = { showDialog = false })
    }
}
