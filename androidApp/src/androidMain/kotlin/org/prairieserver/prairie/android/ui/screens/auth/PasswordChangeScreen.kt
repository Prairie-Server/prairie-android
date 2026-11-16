package org.prairieserver.prairie.android.ui.screens.auth

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import org.prairieserver.prairie.android.ui.components.marquee.MarqueeButton
import org.prairieserver.prairie.android.ui.components.marquee.MarqueeButtonKind
import org.prairieserver.prairie.android.ui.components.marquee.MarqueeErrorHaptic
import org.prairieserver.prairie.android.ui.components.marquee.MarqueeErrorText
import org.prairieserver.prairie.android.ui.components.marquee.MarqueeFieldGroup
import org.prairieserver.prairie.android.ui.components.marquee.MarqueeHeadline
import org.prairieserver.prairie.android.ui.components.marquee.MarqueeSeparator
import org.prairieserver.prairie.android.ui.components.marquee.MarqueeStage
import org.prairieserver.prairie.android.ui.components.marquee.MarqueeTextField
import org.prairieserver.prairie.common.R as CommonR
import org.prairieserver.prairie.common.i18n.text
import org.prairieserver.prairie.common.ui.marquee.MarqueeColors
import org.prairieserver.prairie.common.ui.marquee.MarqueeScene
import org.prairieserver.prairie.common.ui.marquee.MarqueeScrimStyle
import org.prairieserver.prairie.common.ui.marquee.marqueeShake
import org.prairieserver.prairie.viewmodel.PasswordChangePhase
import org.prairieserver.prairie.viewmodel.PasswordChangeViewModel

/**
 * "Choose a new password", shown after signing in with a temporary password
 * an admin set (prairie-apple `PasswordChangeView`). [PasswordChangeViewModel]
 * owns the rules; this screen lays them out.
 *
 * @param onFinished The session no longer carries the restriction; continue to profile selection.
 * @param onSignOut "Sign out".
 */
@Composable
fun PasswordChangeScreen(
    onFinished: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: PasswordChangeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        MarqueeScene.focus = MarqueeScene.Focus.Account
        MarqueeScene.personalTint = null
    }
    LaunchedEffect(state.finished) {
        if (state.finished) {
            viewModel.onFinishedConsumed()
            onFinished()
        }
    }
    MarqueeErrorHaptic(state.error)

    val save = {
        focusManager.clearFocus()
        viewModel.save()
    }
    val announced = Modifier.semantics { liveRegion = LiveRegionMode.Polite }

    MarqueeStage(scrim = MarqueeScrimStyle.BottomDeep, frostStart = 0.40f) {
        when (val phase = state.phase) {
            is PasswordChangePhase.RefreshFailed -> Outcome(
                title = stringResource(CommonR.string.signIn_passwordChange_changedTitle),
                lead = stringResource(CommonR.string.signIn_passwordChange_changedLead),
                action = stringResource(if (phase.retrying) CommonR.string.signIn_passwordChange_tryingAgain else CommonR.string.common_tryAgain),
                busy = phase.retrying,
                onAction = viewModel::retryRefresh,
                onSignOut = onSignOut,
            )
            is PasswordChangePhase.Unconfirmed -> Outcome(
                title = stringResource(CommonR.string.signIn_passwordChange_unconfirmedTitle),
                lead = stringResource(CommonR.string.signIn_passwordChange_unconfirmedLead),
                action = stringResource(if (phase.checking) CommonR.string.signIn_passwordChange_checking else CommonR.string.common_tryAgain),
                busy = phase.checking,
                onAction = viewModel::confirmChange,
                onSignOut = onSignOut,
            )
            PasswordChangePhase.Editing, PasswordChangePhase.Saving -> {
                MarqueeHeadline(title = stringResource(CommonR.string.signIn_passwordChange_title), lead = state.lead.text())
                Column(Modifier.fillMaxWidth().padding(top = 24.dp).animateContentSize()) {
                    MarqueeFieldGroup(isError = state.error != null, modifier = Modifier.marqueeShake(state.errorCount)) {
                        MarqueeTextField(
                            value = state.currentPassword,
                            onValueChange = viewModel::onCurrentPasswordChanged,
                            icon = Icons.Outlined.Key,
                            placeholder = stringResource(CommonR.string.signIn_passwordChange_temporary),
                            keyboardType = KeyboardType.Password,
                            isSecure = true,
                            enabled = !state.isBusy,
                        )
                        MarqueeSeparator()
                        MarqueeTextField(
                            value = state.newPassword,
                            onValueChange = viewModel::onNewPasswordChanged,
                            icon = Icons.Outlined.Lock,
                            placeholder = stringResource(CommonR.string.signIn_passwordChange_new),
                            keyboardType = KeyboardType.Password,
                            isSecure = true,
                            enabled = !state.isBusy,
                        )
                        MarqueeSeparator()
                        MarqueeTextField(
                            value = state.confirmation,
                            onValueChange = viewModel::onConfirmationChanged,
                            icon = Icons.Outlined.Lock,
                            placeholder = stringResource(CommonR.string.signIn_passwordChange_confirm),
                            keyboardType = KeyboardType.Password,
                            isSecure = true,
                            enabled = !state.isBusy,
                            imeAction = ImeAction.Go,
                            onImeAction = save,
                        )
                    }
                    Text(
                        state.requirementsHint.text(),
                        color = MarqueeColors.InkTertiary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                    )
                    state.error?.let { MarqueeErrorText(it.text.text(), Modifier.padding(top = 10.dp, start = 4.dp).then(announced)) }
                    MarqueeButton(
                        text = stringResource(if (state.isBusy) CommonR.string.signIn_passwordChange_saving else CommonR.string.signIn_passwordChange_save),
                        onClick = save,
                        isLoading = state.isBusy,
                        enabled = !state.isBusy,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    SignOutButton(enabled = !state.isBusy, onSignOut = onSignOut)
                }
            }
        }
    }
}

/** The password may have changed, so the form stays closed: one action, or sign out. */
@Composable
private fun Outcome(
    title: String,
    lead: String,
    action: String,
    busy: Boolean,
    onAction: () -> Unit,
    onSignOut: () -> Unit,
) {
    MarqueeHeadline(title = title, lead = lead)
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        MarqueeButton(text = action, onClick = onAction, isLoading = busy, enabled = !busy)
        SignOutButton(enabled = !busy, onSignOut = onSignOut)
    }
}

@Composable
private fun SignOutButton(enabled: Boolean, onSignOut: () -> Unit) {
    MarqueeButton(
        text = stringResource(CommonR.string.common_signOut),
        onClick = onSignOut,
        kind = MarqueeButtonKind.Plain,
        enabled = enabled,
        modifier = Modifier.padding(top = 6.dp),
    )
}
