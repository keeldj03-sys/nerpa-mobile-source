package com.v2ray.ang.ui.activation

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.handler.ActivationFailure
import com.v2ray.ang.handler.ActivationOutcome
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.base.BaseViewModelEvent
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import kotlinx.coroutines.launch

/**
 * The access screen. `MainActivity` puts it on top while nothing is connectable — no live
 * subscription and no manually added nodes — per
 * [com.v2ray.ang.ui.main.ActivationGate.shouldShow], and the settings menu opens it directly so the
 * free-issuance button stays reachable after that gate has let the main screen through. There are
 * two ways out, the button and a pasted personal subscription link, and the screen returns only when
 * every subscription is dead or gone and no manually added node is left.
 */
class ActivationActivity : BaseComponentActivity() {

    private val viewModel: ActivationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewModelEvent.collect { event ->
                    if (event is BaseViewModelEvent.FinishActivity) finish()
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        ActivationScreen(state = state, onAction = viewModel::onAction)
    }
}

@Composable
fun ActivationScreen(
    state: ActivationUiState,
    onAction: (ActivationAction) -> Unit,
) {
    var link by rememberSaveable { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        // The block is centred rather than top-anchored, but stays scrollable: with the IME open and
        // an outcome card shown, the form is taller than the remaining viewport.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.nerpa_activation_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.nerpa_activation_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                if (state.freeEnabled) {
                    Button(
                        onClick = { onAction(ActivationAction.FetchFree) },
                        enabled = !state.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                    ) {
                        Text(stringResource(R.string.nerpa_activation_free_action))
                    }
                    Text(
                        text = stringResource(R.string.nerpa_activation_own_link),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                }
                FormTextField(
                    label = stringResource(R.string.nerpa_activation_link_label),
                    value = link,
                    onValueChange = {
                        link = it
                        if (state.outcome != null) onAction(ActivationAction.ClearOutcome)
                    },
                    enabled = !state.busy,
                    keyboardType = KeyboardType.Uri,
                    maxLines = 3,
                    isError = state.outcome is ActivationOutcome.Failed,
                    modifier = Modifier.padding(top = if (state.freeEnabled) 8.dp else 24.dp),
                )
                if (state.busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.nerpa_activation_busy),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                } else {
                    Button(
                        onClick = { onAction(ActivationAction.Activate(link)) },
                        enabled = link.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.nerpa_activation_action))
                    }
                }

                when (val outcome = state.outcome) {
                    is ActivationOutcome.Failed -> OutcomeCard(
                        text = stringResource(outcome.failure.messageRes()),
                        accent = MaterialTheme.colorScheme.error,
                    )

                    is ActivationOutcome.Accepted -> {
                        OutcomeCard(
                            text = acceptedText(outcome),
                            accent = MaterialTheme.colorScheme.primary,
                        )
                        Button(
                            onClick = { onAction(ActivationAction.Finish) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        ) {
                            Text(stringResource(R.string.nerpa_activation_done))
                        }
                    }

                    null -> Unit
                }

                NavigationBarsSpacer()
            }
        }
    }
}

@Composable
private fun OutcomeCard(text: String, accent: Color) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@Composable
private fun acceptedText(outcome: ActivationOutcome.Accepted): String {
    return if (outcome.accepted < outcome.total) {
        pluralStringResource(
            R.plurals.nerpa_activation_nodes_partial,
            outcome.accepted,
            outcome.accepted,
            outcome.total,
        )
    } else {
        pluralStringResource(R.plurals.nerpa_activation_nodes, outcome.accepted, outcome.accepted)
    }
}

private fun ActivationFailure.messageRes(): Int = when (this) {
    ActivationFailure.InvalidUrl -> R.string.nerpa_activation_error_invalid_url
    ActivationFailure.Unreachable -> R.string.nerpa_activation_error_unreachable
    ActivationFailure.EmptyResponse -> R.string.nerpa_activation_error_empty_response
    ActivationFailure.NoParsableNodes -> R.string.nerpa_activation_error_no_nodes
    ActivationFailure.NotALink -> R.string.nerpa_activation_error_not_a_link
    ActivationFailure.QuotaClosed -> R.string.nerpa_activation_error_quota
}
