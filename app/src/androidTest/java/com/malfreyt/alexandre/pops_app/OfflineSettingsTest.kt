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
        listOf("new", "pending", "updated", "errors").forEach {
            manager.deleteNotificationChannel("grades_${it}_${account.id}")
        }
        manager.deleteNotificationChannelGroup("grades_group_${account.id}")
        appContext.deleteSharedPreferences("offline_settings_test_pops_secure_preferences")
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
        assertEquals(0, container.gradeRepository.readSettings().pollingMinutes)
        assertNull(viewModel.uiState.value.errorDialog)
        assertTrue(viewModel.uiState.value.draftSettings.editableEquals(viewModel.uiState.value.savedSettings))
        assertEquals("Saving preferences must not contact Oasis", 0, requests.get())
        awaitOperation { !hasFailureNotification() }
        assertFalse(container.gradeRepository.readSettings().selectedAccountOrNull()!!.failureNotificationActive)
        // Verify persistence by reopening the encrypted store.
        assertEquals(0, AppContainer(container.appContext).gradeRepository.readSettings().pollingMinutes)
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
        assertEquals(60, saved.pollingMinutes)
        assertFalse(saved.selectedAccountOrNull()!!.notifyErrors)
        assertFalse(saved.selectedAccountOrNull()!!.notifyNewGrades)
        assertEquals(0, requests.get())
    }

    private fun seedFailureNotification() {
        com.malfreyt.alexandre.pops_app.notifications.NotificationHelper.createChannel(container.appContext, container.gradeRepository.readSettings())
        val manager = appContext.getSystemService(NotificationManager::class.java)
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
