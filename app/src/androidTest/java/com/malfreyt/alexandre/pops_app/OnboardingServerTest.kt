package com.malfreyt.alexandre.pops_app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.pops_app.data.DEFAULT_OASIS_BASE_URL
import com.malfreyt.alexandre.pops_app.ui.MainUiState
import com.malfreyt.alexandre.pops_app.ui.OnboardingDataPage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingServerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun serverFieldStartsCollapsedAndEmptyValueUsesDefault() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val server = mutableStateOf("")
        compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.setContent {
            OnboardingDataPage(MainUiState(), "", "", {}, {}, server.value, { server.value = it }, {})
        }
        compose.onNodeWithTag("onboarding_server_url").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.onboarding_server_action)).performScrollTo().performClick()
        compose.onNodeWithTag("onboarding_server_url").performScrollTo().performTextReplacement("http://127.0.0.1:8080/custom")
        compose.onNodeWithTag("onboarding_server_url").assertTextContains("http://127.0.0.1:8080/custom")
        compose.onNodeWithTag("onboarding_server_url").performTextReplacement("ftp://invalid")
        compose.onNodeWithText(context.getString(R.string.error_server_url)).assertExists()
        compose.onNodeWithTag("onboarding_server_url").performTextClearance()
        compose.onNodeWithText(context.getString(R.string.error_server_url)).assertDoesNotExist()
        compose.onNodeWithText(DEFAULT_OASIS_BASE_URL).assertExists()
    }
}
