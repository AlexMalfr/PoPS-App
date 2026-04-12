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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.malfreyt.alexandre.pops_app.ui.MainDestination
import com.malfreyt.alexandre.pops_app.ui.MainScreen
import com.malfreyt.alexandre.pops_app.ui.MainViewModel
import com.malfreyt.alexandre.pops_app.ui.MainViewModelFactory
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
            val openNotificationPreferences = remember(intent) {
                intent?.categories?.contains(Notification.INTENT_CATEGORY_NOTIFICATION_PREFERENCES) == true
            }

            PoPSTheme {
                NotificationPermissionEffect()
                LaunchedEffect(openNotificationPreferences) {
                    if (openNotificationPreferences) {
                        viewModel.navigate(MainDestination.SETTINGS)
                    }
                }
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun NotificationPermissionEffect() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return
    }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}