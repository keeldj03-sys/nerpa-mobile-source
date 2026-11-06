package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.enums.NerpaRoutingMode

/**
 * The three routing presets as one control, so the mode that is in force is readable without
 * opening the ruleset editor.
 *
 * `NerpaRoutingMode.entries` is the display order — least to most intervention — and each segment
 * carries the short label while the line under the row states the mode in full. A short label is
 * deliberate: three segments on a narrow phone cannot fit «Максимальная защита», and an ellipsised
 * mode name is worse than a name explained one line below.
 *
 * The label never wraps: `SingleChoiceSegmentedButtonRow` sizes each segment on its own, so a
 * two-line label makes that one segment taller than the row outline and it draws over its
 * neighbours — seen on a 411dp-wide phone at font scale 1.1 with the default `labelLarge`.
 * `labelMedium` fits «Минимальный» there with room to spare, and the ellipsis is the guard for
 * larger fonts and narrower screens.
 */
@Composable
fun MainRoutingModeRow(
    selected: NerpaRoutingMode,
    onSelect: (NerpaRoutingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = NerpaRoutingMode.entries

    Column(modifier = modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    label = {
                        Text(
                            text = stringResource(mode.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
        Text(
            text = stringResource(selected.descriptionRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 4.dp, end = 4.dp),
        )
    }
}
