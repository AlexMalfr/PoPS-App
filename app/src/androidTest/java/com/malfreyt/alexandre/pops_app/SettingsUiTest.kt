package com.malfreyt.alexandre.pops_app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.pops_app.data.AppContainer
import com.malfreyt.alexandre.pops_app.data.AppSettings
import com.malfreyt.alexandre.pops_app.data.OasisAccount
import com.malfreyt.alexandre.pops_app.notifications.NotificationHelper
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
    private lateinit var container: AppContainer
    private lateinit var model: MainViewModel

    @Before fun setUp() {
        val isolatedContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = context.getSharedPreferences("settings_ui_test_$name", mode)
            override fun getFilesDir(): File = File(context.cacheDir, "settings-ui-test").apply { mkdirs() }
        }
        container = AppContainer(isolatedContext)
        container.gradeRepository.saveSettings(settings)
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            model = MainViewModel(container)
            store.put("test", model)
        }
        compose.setContent { MaterialTheme { key(generation.value) { SettingsPage(state.value, model) } } }
    }

    @After fun tearDown() {
        compose.runOnUiThread { store.clear() }
        val manager = context.getSystemService(NotificationManager::class.java)
        container.gradeRepository.readSettings().accounts.forEach { account ->
            listOf("new", "pending", "updated", "errors").forEach { manager.deleteNotificationChannel("grades_${it}_${account.id}") }
            manager.deleteNotificationChannelGroup("grades_group_${account.id}")
        }
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

    @Test fun syncOptionsAreCollapsedTogetherWithTls() {
        compose.onNodeWithText(context.getString(R.string.ignore_tls_title)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.sync_unmetered_title)).assertDoesNotExist()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(context.getString(R.string.advanced_title)))
        compose.onNodeWithText(context.getString(R.string.advanced_title)).performClick()
        compose.runOnIdle { state.value = state.value.copy(advancedSettingsVisible = model.uiState.value.advancedSettingsVisible) }
        compose.onNodeWithText(context.getString(R.string.ignore_tls_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.sync_unmetered_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.sync_charging_body)).assertExists()
    }

    @Test fun blockedChannelDisablesOnlyItsToggleOnResumeAndKeepsPreference() {
        val blocked = on.copy(id = "ui-blocked-${System.nanoTime()}")
        container.gradeRepository.saveAccount(blocked)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannelGroup(NotificationChannelGroup("grades_group_${blocked.id}", "Test account"))
        NotificationHelper.createChannel(context, container.gradeRepository.readSettings())
        compose.runOnIdle {
            state.value = state.value.copy(savedSettings = settings.copy(accounts = settings.accounts + blocked, selectedAccountId = blocked.id))
        }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("notifications_new_switch"))
        compose.onNodeWithTag("notifications_new_switch").assertIsEnabled()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        manager.createNotificationChannel(NotificationChannel("grades_new_${blocked.id}", "Test blocked category", NotificationManager.IMPORTANCE_NONE).apply {
            group = "grades_group_${blocked.id}"
        })
        org.junit.Assert.assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel("grades_new_${blocked.id}").importance)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("notifications_new_switch").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithTag("notifications_master_switch").assertIsEnabled()
        compose.onNodeWithTag("notifications_pending_switch").assertIsEnabled()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(context.getString(R.string.notifications_system_categories_blocked)))
        compose.onNodeWithText(context.getString(R.string.notifications_system_categories_blocked)).assertIsDisplayed()
        assertTrue(container.gradeRepository.readSettings().accounts.first { it.id == blocked.id }.notifyNewGrades)
    }

    /** Run separately with POST_NOTIFICATIONS revoked before instrumentation starts. */
    @Test fun globalSystemBlockDisablesControlsWithoutChangingPreferences() {
        org.junit.Assume.assumeFalse(NotificationHelper.systemState(context, on.id).appEnabled)
        compose.runOnIdle { state.value = state.value.copy(savedSettings = settings.copy(selectedAccountId = on.id)) }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("notifications_master_switch"))
        compose.onNodeWithTag("notifications_master_switch").assertIsNotEnabled().assertIsOn()
        compose.onNodeWithTag("notifications_new_switch").assertIsNotEnabled()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(context.getString(R.string.notifications_system_app_blocked)))
        compose.onNodeWithText(context.getString(R.string.notifications_system_app_blocked)).assertIsDisplayed()
        assertTrue(container.gradeRepository.readSettings().accounts.first { it.id == on.id }.notificationsEnabled)
        compose.runOnIdle { state.value = state.value.copy(savedSettings = settings) }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText(context.getString(R.string.notifications_system_settings_action)))
        compose.onNodeWithText(context.getString(R.string.notifications_system_settings_action)).assertIsDisplayed().assertIsEnabled()
    }
}
