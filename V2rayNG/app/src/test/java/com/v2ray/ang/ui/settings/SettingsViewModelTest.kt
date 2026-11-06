package com.v2ray.ang.ui.settings

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.v2ray.ang.handler.UpdateCheckResult
import com.v2ray.ang.util.EndpointFetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch

class SettingsViewModelTest {
    @Test
    fun vpnSettingsVisibilityFollowsActivityAvailability() = runBlocking(Dispatchers.IO) {
        val packageManager = mock<PackageManager>()
        val application = mock<Application>()
        whenever(application.packageManager).thenReturn(packageManager)
        var activity: ComponentName? = null

        mockConstruction(Intent::class.java) { intent, context ->
            assertEquals(listOf(Settings.ACTION_VPN_SETTINGS), context.arguments())
            whenever(intent.resolveActivity(packageManager)).thenAnswer { activity }
        }.use {
            val viewModel = SettingsViewModel(application)
            assertFalse(viewModel.systemVpnSettingsAvailable.value)
            viewModel.refreshSystemVpnSettingsAvailability()
            assertFalse(viewModel.systemVpnSettingsAvailable.value)

            activity = mock<ComponentName>()
            viewModel.refreshSystemVpnSettingsAvailability()
            assertTrue(viewModel.systemVpnSettingsAvailable.value)

            activity = null
            viewModel.refreshSystemVpnSettingsAvailability()
            assertFalse(viewModel.systemVpnSettingsAvailable.value)
        }
    }

    /**
     * The update answer lives only in this state, so the screen can show it exactly once. What the
     * three outcomes are is [com.v2ray.ang.handler.UpdateManifestTest]'s question; asking the real
     * host from a JVM test is not anyone's.
     */
    @Test
    fun updateCheckStateStartsAndEndsEmpty() = runBlocking {
        val viewModel = SettingsViewModel(mock<Application>())
        assertFalse(viewModel.isLoading.value)
        assertEquals(null, viewModel.updateCheck.value)
        viewModel.clearUpdateCheck()
        assertEquals(null, viewModel.updateCheck.value)
    }

    /**
     * The row shows "checking" only while the request is actually in flight: the flag must be up
     * when the fetch has been reached and down again once the outcome is published. A build with no
     * baked endpoint never reaches the fetch, so the endpoint comes in as a parameter - the same
     * reason [com.v2ray.ang.handler.UpdateManifest.check] takes its fetch as one.
     */
    @Test
    fun updateCheckRunningCoversTheWholeRequest() = runBlocking {
        val viewModel = SettingsViewModel(mock<Application>())
        val asked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val job = launch(Dispatchers.IO) {
            viewModel.checkForUpdate(
                endpointUrl = "https://panel.example.com/sub/token",
                fetch = {
                    asked.countDown()
                    release.await()
                    EndpointFetchResult.HttpStatus(500)
                },
            )
        }
        asked.await()
        assertTrue(viewModel.updateCheckRunning.value)
        release.countDown()
        job.join()
        assertFalse(viewModel.updateCheckRunning.value)
        assertTrue(viewModel.updateCheck.value is UpdateCheckResult.Refused)
    }
}
