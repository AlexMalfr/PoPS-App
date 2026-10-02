package com.malfreyt.alexandre.pops_app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.pops_app.data.AppContainer
import com.malfreyt.alexandre.pops_app.data.AppSettings
import com.malfreyt.alexandre.pops_app.data.OasisAccount
import com.malfreyt.alexandre.pops_app.ui.MainUiState
import com.malfreyt.alexandre.pops_app.ui.MainViewModel
import com.malfreyt.alexandre.pops_app.ui.SettingsPage
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ViewModelStore()
    private val off = OasisAccount(id = "ui-off", login = "off", password = "test", notificationsEnabled = false, pollingMinutes = 0)
    private val on = off.copy(id = "ui-on", login = "on", notificationsEnabled = true)
    private val settings = AppSettings(accounts = listOf(off, on), selectedAccountId = off.id)
    private val state = mutableStateOf(MainUiState(savedSettings = settings))
    private val generation = mutableStateOf(0)

    @Before fun setUp() {
        val isolatedContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = context.getSharedPreferences("settings_ui_test_$name", mode)
            override fun getFilesDir(): File = File(context.cacheDir, "settings-ui-test").apply { mkdirs() }
        }
        val container = AppContainer(isolatedContext)
        container.gradeRepository.saveSettings(settings)
        lateinit var model: MainViewModel
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            model = MainViewModel(container)
            store.put("test", model)
        }
        compose.setContent { MaterialTheme { key(generation.value) { SettingsPage(state.value, model) } } }
    }

    @After fun tearDown() {
        compose.runOnUiThread { store.clear() }
        context.deleteSharedPreferences("settings_ui_test_pops_secure_preferences")
        File(context.cacheDir, "settings-ui-test").deleteRecursively()
    }

    @Test fun switchingAccountsUpdatesBothSwitchValueAndThumbPosition() {
        fun master() = compose.onNodeWithTag("notifications_master_switch")
        fun reveal() = compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("notifications_master_switch"))
        reveal()
        master().assertIsOff()
        compose.runOnIdle { state.value = state.value.copy(savedSettings = settings.copy(selectedAccountId = on.id)) }
        reveal()
        master().assertIsOn()
        val switched = master().captureToImage().toPixelMap()
        // A fresh enabled switch is the visual reference, including thumb position.
        compose.runOnIdle { generation.value++ }
        reveal()
        val fresh = master().captureToImage().toPixelMap()
        assertTrue(switched.width == fresh.width && switched.height == fresh.height)
        var differing = 0
        for (x in 0 until fresh.width) for (y in 0 until fresh.height) {
            if (switched[x, y] != fresh[x, y]) differing++
        }
        assertTrue("Changing accounts must render an enabled thumb like a fresh enabled switch", differing < fresh.width * fresh.height / 100)
        compose.runOnIdle { state.value = state.value.copy(savedSettings = settings) }
        reveal()
        master().assertIsOff()
    }

    @Test fun accountDialogOffersCancelWithoutSaving() {
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(context.getString(R.string.account_add)))
        compose.onNodeWithText(context.getString(R.string.account_add)).performClick()
        compose.onNodeWithText(context.getString(R.string.dialog_cancel)).assertIsDisplayed().performClick()
        compose.onNodeWithTag("account_login").assertDoesNotExist()
    }
}
