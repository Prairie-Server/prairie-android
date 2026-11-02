package org.prairieserver.prairie.android.ui.screens.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.prairieserver.prairie.common.R
import org.prairieserver.prairie.common.settings.ConnectionSpeedUiState
import org.prairieserver.prairie.common.settings.connectionRateLabel
import org.prairieserver.prairie.common.settings.connectionSpeedLabel
import org.prairieserver.prairie.common.settings.connectionSpeedMessageLabel
import org.prairieserver.prairie.common.settings.connectionSpeedSkipLabel
import org.prairieserver.prairie.common.settings.rememberMinuteClock

/**
 * Playback → Connection (the buffering-quality spec's *Reporting and
 * display*, phase 2): the measured speed with **Test speed**, and Auto's
 * budget in the footer. Shown only while the server measures speed. On
 * cellular and metered networks the row says speed tests are skipped and
 * offers no test. The copy is the spec's.
 */
@Composable
fun PlaybackConnectionSection(
    state: ConnectionSpeedUiState,
    onTestSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by rememberMinuteClock()
    SettingsSection(
        title = stringResource(R.string.settings_connection_title),
        footer = state.autoBudgetKbps?.let { stringResource(R.string.settings_connection_budget, connectionRateLabel(it)) },
        modifier = modifier,
    ) {
        val skipped = state.skipped
        SettingsRow(
            label = stringResource(R.string.settings_connection_speed),
            description = if (skipped != null) connectionSpeedSkipLabel(skipped) else connectionSpeedLabel(state.reading, now),
        ) {
            if (skipped == null) {
                TextButton(onClick = onTestSpeed, enabled = !state.testing) {
                    Text(
                        stringResource(
                            if (state.testing) R.string.settings_connection_testing else R.string.settings_connection_test,
                        ),
                    )
                }
            }
        }
        state.message?.takeIf { skipped == null }?.let { SettingsProse(body = connectionSpeedMessageLabel(it)) }
    }
}
