package com.malfreyt.alexandre.pops_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.autofill.AutofillManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsFocused
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.pops_app.ui.AccountCredentialsFields
import java.io.FileInputStream
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutofillIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun nativeAutofillReceivesUsernameAndPasswordFromSharedForm() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
            FileInputStream(it.fileDescriptor).bufferedReader().readText().trim()
        }
        val previousService = shell("settings get secure autofill_service")
        require(previousService == "null" || previousService.matches(Regex("[a-zA-Z0-9_./:]+")))
        val result = java.util.concurrent.atomic.AtomicReference<Pair<Boolean, Boolean>>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                result.set(intent.getBooleanExtra("username", false) to intent.getBooleanExtra("password", false))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(AutofillProbeService.ACTION), ContextCompat.RECEIVER_EXPORTED)
        try {
            shell("settings put secure autofill_service com.malfreyt.alexandre.pops_app.test/com.malfreyt.alexandre.pops_app.AutofillProbeService")
            assertEquals("com.malfreyt.alexandre.pops_app.test/com.malfreyt.alexandre.pops_app.AutofillProbeService", shell("settings get secure autofill_service"))
            shell("input keyevent KEYCODE_WAKEUP")
            Thread.sleep(500)
            val login = mutableStateOf("")
            val password = mutableStateOf("")
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    assertTrue("The native autofill service must be enabled", activity.getSystemService(AutofillManager::class.java).isEnabled)
                    activity.setContent { AccountCredentialsFields(login.value, password.value, { login.value = it }, { password.value = it }, true) }
                }
                compose.onNodeWithTag("account_login").performClick().assertIsFocused()
                compose.waitUntil(10_000) { result.get() != null }
                assertTrue("Android must receive a username hint", result.get().first)
                assertTrue("Android must receive a password hint", result.get().second)
                scenario.onActivity { it.getSystemService(AutofillManager::class.java).cancel() }
            }
        } finally {
            if (previousService == "null") shell("settings delete secure autofill_service")
            else shell("settings put secure autofill_service $previousService")
            context.unregisterReceiver(receiver)
        }
    }
}
