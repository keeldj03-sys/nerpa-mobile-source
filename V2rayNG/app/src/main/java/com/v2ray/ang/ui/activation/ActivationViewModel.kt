package com.v2ray.ang.ui.activation

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.ActivationFailure
import com.v2ray.ang.handler.ActivationOutcome
import com.v2ray.ang.handler.FreeLinkResult
import com.v2ray.ang.handler.FreeSubscriptionLink
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionActivation
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActivationViewModel(application: Application) : BaseViewModel(application) {

    private val _state = MutableStateFlow(ActivationUiState())
    val state: StateFlow<ActivationUiState> = _state.asStateFlow()

    fun onAction(action: ActivationAction) {
        when (action) {
            is ActivationAction.Activate -> activate(action.link)
            ActivationAction.FetchFree -> fetchFree()
            ActivationAction.ClearOutcome -> _state.update { it.copy(outcome = null) }
            ActivationAction.Finish -> finishActivity()
        }
    }

    private fun activate(link: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, outcome = null) }
        val remarks = getString(R.string.nerpa_activation_group_name)
        viewModelScope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) {
                    val result = SubscriptionActivation.activate(url = link, remarks = remarks)
                    if (result is ActivationOutcome.Accepted) {
                        SettingsManager.setActivationSubscriptionId(result.subscriptionId)
                    }
                    result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Activation link submit", e)
                ActivationOutcome.Failed(ActivationFailure.Unreachable)
            }
            _state.update { it.copy(busy = false, outcome = outcome) }
        }
    }

    /**
     * Fetches the free link from the operator endpoint and serves it into the single free slot.
     * Neither the endpoint address nor the returned link is ever logged. `SettingsManager`/MMKV
     * access can throw outside the guarded write window, and an uncaught exception in
     * `viewModelScope` would crash the process and leave `busy` on forever, so the whole launch
     * body is wrapped and resolved to the unreachable failure the screen can show.
     */
    private fun fetchFree() {
        if (_state.value.busy) return
        val endpoint = BuildConfig.FREE_SUBSCRIPTION_ENDPOINT
        if (endpoint.isBlank()) return
        _state.update { it.copy(busy = true, outcome = null) }
        viewModelScope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    val fetch = HttpUtil.fetchEndpoint(UrlContentRequest(url = endpoint))
                    when (
                        val free = FreeSubscriptionLink.from(
                            fetch,
                            endpointUrl = endpoint,
                            isValidSubUrl = { Utils.isValidSubUrl(it) },
                            isNodePackage = ::isNodePackage,
                        )
                    ) {
                        is FreeLinkResult.Refused -> ActivationOutcome.Failed(free.failure)
                        is FreeLinkResult.Link -> activateInSlot(free.url)
                    }
                }
                _state.update { it.copy(busy = false, outcome = outcome) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Activation free subscription fetch", e)
                _state.update {
                    it.copy(
                        busy = false,
                        outcome = ActivationOutcome.Failed(ActivationFailure.Unreachable),
                    )
                }
            }
        }
    }

    /**
     * Whether the endpoint answered with the base64 node package that its own address serves, which
     * is how x-ui answers a client's subscription address. `Utils.decode` yields nothing for a body
     * with characters outside the base64 alphabet, so a panel login page is not a package.
     */
    private fun isNodePackage(body: String): Boolean =
        SubscriptionActivation.countNodeLines(Utils.decode(body)) > 0

    /**
     * The free button always serves one slot, and that slot is the group the button created. A link
     * pasted by hand stays the user's own subscription and is never stored as the slot, so the button
     * cannot overwrite it. A later issuance replaces the link inside the same group; when the slot
     * group no longer exists in storage - deleted in the drawer - the issuance starts a fresh group
     * and moves the marker to it, instead of failing on a dangling id.
     */
    private fun activateInSlot(link: String): ActivationOutcome {
        val slot = SettingsManager.freeSubscriptionId()?.takeIf { MmkvManager.decodeSubscription(it) != null }
        val remarks = getString(R.string.nerpa_activation_group_name)
        if (slot == null) {
            val outcome = SubscriptionActivation.activate(url = link, remarks = remarks)
            if (outcome is ActivationOutcome.Accepted) {
                SettingsManager.setFreeSubscriptionId(outcome.subscriptionId)
                // `activationSubscriptionId` answers a different question - "has this app been
                // activated at all" - and both the gate (`ActivationGate.shouldShow`) and the group
                // selected after the screen closes read it. A free issuance is an activation, so it
                // sets that flag too; nothing reads it as the slot any more, which was the bug.
                SettingsManager.setActivationSubscriptionId(outcome.subscriptionId)
            }
            return outcome
        }
        return SubscriptionActivation.reactivate(slot, link)
    }
}
