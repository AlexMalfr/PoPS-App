package com.malfreyt.alexandre.pops_app

import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.pops_app.data.AppContainer
import com.malfreyt.alexandre.pops_app.data.AppSettings
import com.malfreyt.alexandre.pops_app.data.OasisAccount
import com.malfreyt.alexandre.pops_app.sync.SyncScheduler
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.malfreyt.alexandre.pops_app.ui.MainViewModel
import java.io.File
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineSettingsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val appContext = instrumentation.targetContext
    private val account = OasisAccount(id = "offline-settings-test", login = "test", password = "test", studentId = "test")
    private val viewModelStore = ViewModelStore()
    private lateinit var container: AppContainer
    private lateinit var viewModel: MainViewModel
    private lateinit var server: ServerSocket
    private lateinit var serverThread: Thread
    private val requests = AtomicInteger()
    private val channelId = "grades_errors_${account.id}"
    private val notificationId = 10_000 + ((account.id.hashCode() and 0x7fffffff) % 100_000)

    @Before
    fun setUp() {
        // Separate encrypted preferences and caches from the user's installed account.
        val isolatedContext = object : ContextWrapper(appContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                appContext.getSharedPreferences("offline_settings_test_$name", mode)
            override fun getFilesDir(): File = File(appContext.cacheDir, "offline-settings-test").apply { mkdirs() }
        }
        container = AppContainer(isolatedContext)
        server = ServerSocket(0)
        serverThread = thread(name = "UnavailableOasis") {
            try {
                while (!server.isClosed) {
                    server.accept().use { socket ->
                        requests.incrementAndGet()
                        socket.getOutputStream().write(
                            "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
                        )
                    }
                }
            } catch (_: SocketException) {
                // Closing the server stops the test fixture.
            }
        }
        container.gradeRepository.saveSettings(
            AppSettings(
                accounts = listOf(account),
                selectedAccountId = account.id,
                oasisBaseUrl = "http://127.0.0.1:${server.localPort}/",
            )
        )
        instrumentation.runOnMainSync {
            viewModel = MainViewModel(container)
            viewModelStore.put("test", viewModel)
        }
        instrumentation.waitForIdleSync()
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { viewModelStore.clear() }
        server.close()
        serverThread.join(1000)
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.cancel(notificationId)
        listOf(account.id, "offline-settings-test-second").forEach { id ->
            listOf("new", "pending", "updated", "errors").forEach {
                manager.deleteNotificationChannel("grades_${it}_$id")
            }
            manager.deleteNotificationChannelGroup("grades_group_$id")
        }
        SyncScheduler.reschedule(container.appContext, AppSettings())
        appContext.deleteSharedPreferences("offline_settings_test_pops_secure_preferences")
        appContext.deleteSharedPreferences("offline_settings_test_pops_sync_scheduler")
        File(appContext.cacheDir, "offline-settings-test").deleteRecursively()
        SyncScheduler.reschedule(appContext, (appContext.applicationContext as PoPSApplication).container.gradeRepository.readSettings())
    }

    @Test
    fun backgroundSyncCanBeDisabledWhileOasisIsDown() {
        seedFailureNotification()
        instrumentation.runOnMainSync {
            viewModel.updatePollingMinutes(0)
            viewModel.saveSettings()
        }
        awaitOperation { !viewModel.uiState.value.isSaving }
        assertEquals(0, container.gradeRepository.readSettings().selectedAccountOrNull()!!.pollingMinutes)
        assertNull(viewModel.uiState.value.errorDialog)
        assertTrue(viewModel.uiState.value.draftSettings.editableEquals(viewModel.uiState.value.savedSettings))
        assertEquals("Saving preferences must not contact Oasis", 0, requests.get())
        awaitOperation { !hasFailureNotification() }
        assertFalse(container.gradeRepository.readSettings().selectedAccountOrNull()!!.failureNotificationActive)
        // Verify persistence by reopening the encrypted store.
        assertEquals(0, AppContainer(container.appContext).gradeRepository.readSettings().selectedAccountOrNull()!!.pollingMinutes)
    }

    @Test
    fun serverSettingsCanBeSavedWithoutChangingCredentials() {
        instrumentation.runOnMainSync {
            viewModel.updateOasisUrl("http://127.0.0.1:${server.localPort}/unavailable/")
            viewModel.updateIgnoreTlsErrors(true)
            viewModel.saveSettings()
        }
        awaitOperation { !viewModel.uiState.value.isSaving }
        val saved = container.gradeRepository.readSettings()
        assertTrue(saved.oasisBaseUrl.endsWith("/unavailable/"))
        assertTrue(saved.ignoreTlsErrors)
        assertEquals(account, saved.selectedAccountOrNull())
        assertNull(viewModel.uiState.value.errorDialog)
        assertEquals(0, requests.get())
    }

    @Test
    fun credentialChangesStillRequireSuccessfulConnection() {
        instrumentation.runOnMainSync { viewModel.saveAccount(account.id, "changed-login", "changed-password") }
        awaitOperation { !viewModel.uiState.value.isSavingAccount }
        assertNotNull(viewModel.uiState.value.errorDialog)
        assertEquals(account, container.gradeRepository.readSettings().selectedAccountOrNull())
        assertNull(viewModel.uiState.value.pendingCredentialSave)
        assertTrue("Changing credentials must contact Oasis", requests.get() > 0)
    }

    @Test
    fun disablingErrorNotificationsClearsExistingAlertAndPersistsOffline() {
        seedFailureNotification()
        instrumentation.runOnMainSync { viewModel.updateNotifyErrors(false) }
        instrumentation.waitForIdleSync()
        assertFalse(container.gradeRepository.readSettings().selectedAccountOrNull()!!.notifyErrors)
        awaitOperation { !hasFailureNotification() }
        assertFalse(container.gradeRepository.readSettings().selectedAccountOrNull()!!.failureNotificationActive)
        assertEquals(0, requests.get())
    }

    @Test
    fun disablingAllAccountNotificationsClearsExistingError() {
        seedFailureNotification()
        instrumentation.runOnMainSync { viewModel.updateNotificationsEnabled(false) }
        instrumentation.waitForIdleSync()
        assertFalse(container.gradeRepository.readSettings().selectedAccountOrNull()!!.notificationsEnabled)
        awaitOperation { !hasFailureNotification() }
        assertFalse(container.gradeRepository.readSettings().selectedAccountOrNull()!!.failureNotificationActive)
        assertEquals(0, requests.get())
    }

    @Test
    fun savingGeneralSettingsPreservesImmediateNotificationChanges() {
        instrumentation.runOnMainSync {
            viewModel.updateNotifyErrors(false)
            viewModel.updateNotifyNewGrades(false)
            viewModel.updatePollingMinutes(60)
            viewModel.saveSettings()
        }
        awaitOperation { !viewModel.uiState.value.isSaving }
        val saved = container.gradeRepository.readSettings()
        assertEquals(60, saved.selectedAccountOrNull()!!.pollingMinutes)
        assertFalse(saved.selectedAccountOrNull()!!.notifyErrors)
        assertFalse(saved.selectedAccountOrNull()!!.notifyNewGrades)
        assertEquals(0, requests.get())
    }

    @Test
    fun syncPreferencesAndSchedulesAreIndependentPerAccount() {
        val second = account.copy(id = "offline-settings-test-second", login = "second", studentId = "second", pollingMinutes = 360)
        container.gradeRepository.saveAccount(second)
        instrumentation.runOnMainSync {
            viewModel.updateSyncUnmeteredOnly(true)
            viewModel.updateSyncChargingOnly(true)
            viewModel.updatePollingMinutes(60)
        }
        val manager = WorkManager.getInstance(appContext)
        val firstWork = manager.getWorkInfosForUniqueWork(SyncScheduler.workName(account.id)).get().single { !it.state.isFinished }
        val secondWork = manager.getWorkInfosForUniqueWork(SyncScheduler.workName(second.id)).get().single { !it.state.isFinished }
        assertEquals(NetworkType.UNMETERED, firstWork.constraints.requiredNetworkType)
        assertTrue(firstWork.constraints.requiresCharging())
        assertEquals(60L * 60_000, firstWork.periodicityInfo!!.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, secondWork.constraints.requiredNetworkType)
        assertFalse(secondWork.constraints.requiresCharging())
        assertEquals(360L * 60_000, secondWork.periodicityInfo!!.repeatIntervalMillis)
        val reopened = AppContainer(container.appContext).gradeRepository.readSettings()
        assertTrue(reopened.accounts.first { it.id == account.id }.syncChargingOnly)
        assertFalse(reopened.accounts.first { it.id == second.id }.syncChargingOnly)
        instrumentation.runOnMainSync { viewModel.updatePollingMinutes(0) }
        assertTrue(manager.getWorkInfosForUniqueWork(SyncScheduler.workName(account.id)).get().all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(manager.getWorkInfosForUniqueWork(SyncScheduler.workName(second.id)).get().any { !it.state.isFinished })
        assertEquals(0, requests.get())
    }

    @Test
    fun legacyGlobalSyncPreferencesAreMigrated() {
        val context = container.appContext
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val preferences = EncryptedSharedPreferences.create(context, "pops_secure_preferences", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        preferences.edit().putString("settings_json_v2", """{"pollingMinutes":360,"accounts":[{"id":"legacy","login":"test","password":"test"}]}""").commit()
        val migrated = AppContainer(context).gradeRepository.readSettings().selectedAccountOrNull()!!
        assertEquals(360, migrated.pollingMinutes)
        assertFalse(migrated.syncChargingOnly)
        assertFalse(migrated.syncUnmeteredOnly)
        preferences.edit().putString("settings_json_v2", """{"notificationsEnabled":false,"pollingMinutes":360,"accounts":[{"id":"legacy","login":"test","password":"test"}]}""").commit()
        assertEquals(0, AppContainer(context).gradeRepository.readSettings().selectedAccountOrNull()!!.pollingMinutes)
    }

    private fun seedFailureNotification() {
        com.malfreyt.alexandre.pops_app.notifications.NotificationHelper.createChannel(container.appContext, container.gradeRepository.readSettings())
        val manager = appContext.getSystemService(NotificationManager::class.java)
        org.junit.Assume.assumeTrue("Notification permission is needed to exercise alert cancellation", manager.areNotificationsEnabled())
        manager.notify(notificationId, NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("PoPS regression test")
            .setSilent(true)
            .build())
        container.settingsStore.markFailureNotificationShown(account.id, java.time.LocalDate.now().toEpochDay())
        instrumentation.waitForIdleSync()
        awaitOperation { hasFailureNotification() }
        assertTrue("The test requires notification permission", hasFailureNotification())
    }

    private fun hasFailureNotification(): Boolean = appContext.getSystemService(NotificationManager::class.java)
        .activeNotifications.any { it.id == notificationId }

    private fun awaitOperation(completed: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        do {
            instrumentation.waitForIdleSync()
            if (completed()) return
            Thread.sleep(20)
        } while (android.os.SystemClock.uptimeMillis() < deadline)
        fail("Settings operation did not finish")
    }
}
