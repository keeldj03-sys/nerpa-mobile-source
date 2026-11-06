package com.v2ray.ang.ui.activation

import com.v2ray.ang.BuildConfig
import com.v2ray.ang.handler.ActivationOutcome

/** State of the first-run activation screen. The pasted link itself stays in the composable: it is
 * uncommitted input, not durable state. */
data class ActivationUiState(
    val busy: Boolean = false,
    val outcome: ActivationOutcome? = null,
    /** False when the build carries no endpoint address; then the button is not rendered. */
    val freeEnabled: Boolean = BuildConfig.FREE_SUBSCRIPTION_ENDPOINT.isNotBlank(),
)

sealed class ActivationAction {
    data class Activate(val link: String) : ActivationAction()
    data object FetchFree : ActivationAction()
    data object ClearOutcome : ActivationAction()
    data object Finish : ActivationAction()
}
