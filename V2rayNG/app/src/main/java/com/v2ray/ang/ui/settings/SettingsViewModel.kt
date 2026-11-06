package com.v2ray.ang.ui.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.UpdateCheckResult
import com.v2ray.ang.handler.UpdateManifest
import com.v2ray.ang.root.RootManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.ui.main.NerpaSurfacePolicy
import com.v2ray.ang.util.EndpointFetchResult
import com.v2ray.ang.util.HttpUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : BaseViewModel(application) {

    private val _systemVpnSettingsAvailable = MutableStateFlow(false)
    val systemVpnSettingsAvailable = _systemVpnSettingsAvailable.asStateFlow()

    private val _updateCheck = MutableStateFlow<UpdateCheckResult?>(null)
    val updateCheck = _updateCheck.asStateFlow()

    /** True for the whole manifest request, so the row can say the tap did something. */
    private val _updateCheckRunning = MutableStateFlow(false)
    val updateCheckRunning = _updateCheckRunning.asStateFlow()

    suspend fun refreshSystemVpnSettingsAvailability() {
        _systemVpnSettingsAvailable.value = withContext(Dispatchers.IO) {
            // Android exposes the VPN page, not a direct link to the Always-on VPN switch.
            Intent(Settings.ACTION_VPN_SETTINGS).resolveActivity(getApplication<Application>().packageManager) != null
        }
    }

    /**
     * Asks the issuance host for the update manifest and publishes the one outcome the screen has to
     * act on; the caller only ever shows what [UpdateManifest] decided.
     *
     * The manifest URL is derived from the address already baked into the build, so a release adds no
     * second address and a build with no issuance host has nothing to ask - that build also hides the
     * row, so the refusal for an unusable address is a belt, not a branch users walk into.
     */
    suspend fun checkForUpdate(
        endpointUrl: String = BuildConfig.FREE_SUBSCRIPTION_ENDPOINT,
        fetch: (String) -> EndpointFetchResult = ::fetchManifest,
    ) {
        // One answer at a time: a second tap while the request is in flight must not race it.
        if (_updateCheckRunning.value) return
        _updateCheckRunning.value = true
        _isLoading.value = true
        try {
            _updateCheck.value = withContext(Dispatchers.IO) {
                UpdateManifest.check(
                    endpointUrl = endpointUrl,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    fetch = fetch,
                )
            }
        } finally {
            _isLoading.value = false
            _updateCheckRunning.value = false
        }
    }

    /**
     * Drops an offer or a message already shown. The state is the only place the answer lives, so
     * leaving it set would raise the dialog again after a rotation and repeat the toast.
     */
    fun clearUpdateCheck() {
        _updateCheck.value = null
    }

    private fun fetchManifest(url: String): EndpointFetchResult =
        HttpUtil.fetchEndpoint(UrlContentRequest(url = url))

    /**
     * Checks for root access and requests it if necessary.
     * Updates [isLoading] during the process.
     */
    fun checkAndRequestRoot(onSuccess: () -> Unit) {
        launchLoading {
            val hasRoot = withContext(Dispatchers.IO) {
                RootManager.refresh()
            }
            if (hasRoot) {
                onSuccess()
            } else {
                toastError(R.string.toast_root_required)
            }
        }
    }

    /**
     * Puts the settings this build offers no editor for back to the values it was built with.
     *
     * The keys are named by [NerpaSurfacePolicy], the same allowlist that closes the screens, so the
     * row cannot drift away from what is actually hidden. A running tunnel keeps the configuration it
     * already read; the dialog says so rather than pretending the change is live.
     */
    fun resetHiddenTuning() {
        launchLoading {
            withContext(Dispatchers.IO) {
                MmkvManager.removeSettingsKeys(NerpaSurfacePolicy.hiddenTuningKeys)
            }
            toastSuccess(R.string.toast_success)
        }
    }

    /**
     * Validates if the given string is a valid observatory duration.
     * Shows error toast if invalid.
     * @return The trimmed value if valid, null otherwise.
     */
    fun validateObservatoryDuration(value: String): String? {
        val duration = value.trim()
        return if (AppConfig.OBSERVATORY_DURATION_PATTERN.matches(duration)) {
            duration
        } else {
            toastError(R.string.toast_invalid_observatory_duration)
            null
        }
    }

    /**
     * Validates if the given string is a valid observatory sampling value.
     * Shows error toast if invalid.
     * @return The value if valid, null otherwise.
     */
    fun validateObservatorySampling(value: String): String? {
        val sampling = value.trim().toIntOrNull()?.takeIf { it > 0 }
        return if (sampling != null) {
            sampling.toString()
        } else {
            toastError(R.string.toast_invalid_observatory_sampling)
            null
        }
    }
}
