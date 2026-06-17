package com.continuum.app.tv.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.repository.DeviceLoginRepository
import com.continuum.app.tv.R
import com.continuum.app.tv.ui.components.TvAnimatedMeshBackground
import com.continuum.app.tv.ui.components.TvHeroActionPill
import com.continuum.app.tv.ui.components.TvPillVariant
import com.continuum.app.tv.ui.components.tvOutlinedTextFieldColors
import com.continuum.app.tv.ui.theme.ElevatedSurface
import com.continuum.app.tv.ui.theme.Spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Sign-in form — compact, TOP-anchored so the username/password fields stay
 * above the on-screen IME. See [TvServerSetupScreen] for the rationale: on
 * Android TV the soft keyboard eats the lower half of the viewport, so any
 * centered form hides its inputs.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLoginScreen(
    onLoginSuccess: () -> Unit,
    onCreateAccount: () -> Unit = {},
    signupEnabled: Boolean = false,
    viewModel: TvLoginViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val deviceState by viewModel.deviceLoginState.collectAsState()
    val usernameFocus = remember { FocusRequester() }
    val usernameBringIntoView = remember { BringIntoViewRequester() }
    val passwordBringIntoView = remember { BringIntoViewRequester() }
    val signInBringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.loginSuccess) {
        if (state.loginSuccess) {
            viewModel.onLoginSuccessConsumed()
            onLoginSuccess()
        }
    }
    LaunchedEffect(Unit) { usernameFocus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TvAnimatedMeshBackground()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 64.dp, bottom = Spacing.lg, start = Spacing.lg, end = Spacing.lg),
        ) {
            BrandHeader()

            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CredentialFormCard(
                    state = state,
                    usernameFocus = usernameFocus,
                    usernameBringIntoView = usernameBringIntoView,
                    passwordBringIntoView = passwordBringIntoView,
                    signInBringIntoView = signInBringIntoView,
                    onUsernameChanged = viewModel::onUsernameChanged,
                    onPasswordChanged = viewModel::onPasswordChanged,
                    onLoginClick = viewModel::onLoginClick,
                    signupEnabled = signupEnabled,
                    onCreateAccount = onCreateAccount,
                    scope = scope,
                    modifier = Modifier.width(620.dp),
                )

                QrLoginCard(
                    state = deviceState,
                    onRetry = viewModel::restartDeviceLogin,
                    modifier = Modifier.width(480.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BrandHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Image(
            painter = painterResource(id = R.drawable.silo_wordmark),
            contentDescription = "Silo",
            modifier = Modifier
                .width(132.dp)
                .height(69.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CredentialFormCard(
    state: TvLoginUiState,
    usernameFocus: FocusRequester,
    usernameBringIntoView: BringIntoViewRequester,
    passwordBringIntoView: BringIntoViewRequester,
    signInBringIntoView: BringIntoViewRequester,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
    signupEnabled: Boolean,
    onCreateAccount: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier
            .background(ElevatedSurface, RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        Text(
            text = "Sign in",
            style = TvLoginTextStyles.Title,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Use the account from your Silo server.",
            style = TvLoginTextStyles.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.username,
            onValueChange = onUsernameChanged,
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            enabled = !state.isLoading,
            textStyle = TvLoginTextStyles.Field,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(usernameBringIntoView)
                .onFocusEvent { fs ->
                    if (fs.isFocused) scope.launch { usernameBringIntoView.bringIntoView() }
                }
                .focusRequester(usernameFocus),
            colors = tvOutlinedTextFieldColors(),
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (!state.isLoading &&
                        state.username.isNotBlank() &&
                        state.password.isNotBlank()
                    ) {
                        onLoginClick()
                    }
                },
            ),
            enabled = !state.isLoading,
            textStyle = TvLoginTextStyles.Field,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(passwordBringIntoView)
                .onFocusEvent { fs ->
                    if (fs.isFocused) scope.launch { passwordBringIntoView.bringIntoView() }
                },
            colors = tvOutlinedTextFieldColors(),
        )

        if (state.error != null) {
            Text(
                text = state.error!!,
                style = TvLoginTextStyles.Error,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Box(
            modifier = Modifier
                .bringIntoViewRequester(signInBringIntoView)
                .onFocusEvent { fs ->
                    if (fs.hasFocus) scope.launch { signInBringIntoView.bringIntoView() }
                },
        ) {
            TvHeroActionPill(
                label = if (state.isLoading) "Signing in…" else "Sign In",
                icon = Icons.AutoMirrored.Filled.Login,
                variant = TvPillVariant.Filled,
                heightOverride = 64.dp,
                horizontalPaddingOverride = 38.dp,
                labelStyle = TvLoginTextStyles.Button,
                onClick = onLoginClick,
            )
        }

        // Surfaced only when the server reports public signup is enabled. The
        // ServerSetup probe forwards that flag through the Login route so this
        // affordance never appears on signup-disabled servers.
        if (signupEnabled) {
            Text(
                text = "Don't have an account yet?",
                style = TvLoginTextStyles.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TvHeroActionPill(
                label = "Create Account",
                icon = Icons.Default.AccountCircle,
                variant = TvPillVariant.Hollow,
                heightOverride = 56.dp,
                horizontalPaddingOverride = 32.dp,
                labelStyle = TvLoginTextStyles.Button,
                onClick = onCreateAccount,
            )
        }
    }
}

private object TvLoginTextStyles {
    val Title = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    )

    val Body = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )

    val Field = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = Color.White,
    )

    val Error = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )

    val Button = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )
}

/**
 * Live QR pane bound to the device-login state machine. Renders one of five
 * branches based on [state]:
 *
 *  - Idle / Initiating → spinner copy + empty 320dp box (matches the QR's
 *    final footprint so the layout doesn't reflow when the matrix lands).
 *  - Awaiting → the actual QR (encoded `verification_uri_complete`) plus
 *    the short `user_code` underneath as a typing fallback.
 *  - Approved → "Signed in!" — short-lived, the screen-level
 *    `LaunchedEffect(loginSuccess)` navigates away.
 *  - Failed → message + "Try again" pill that fires [onRetry].
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QrLoginCard(
    state: DeviceLoginRepository.DeviceLoginState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier
            .background(ElevatedSurface, RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        Text(
            text = "Sign in with your phone",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        when (state) {
            DeviceLoginRepository.DeviceLoginState.Idle,
            DeviceLoginRepository.DeviceLoginState.Initiating -> {
                Text(
                    text = "Loading device-login code…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .background(
                            Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(16.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(16.dp),
                        ),
                )
            }
            is DeviceLoginRepository.DeviceLoginState.Awaiting -> {
                Text(
                    text = "Scan the code with your phone's camera",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                QrCodePanel(
                    content = state.session.verificationUriComplete,
                    size = 320.dp,
                )
                Text(
                    text = "Code: ${state.session.userCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is DeviceLoginRepository.DeviceLoginState.Approved -> {
                Text(
                    text = "Signed in!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            is DeviceLoginRepository.DeviceLoginState.Failed -> {
                Text(
                    text = state.message ?: "Sign-in failed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TvHeroActionPill(
                    label = "Try again",
                    icon = Icons.Default.Refresh,
                    variant = TvPillVariant.Hollow,
                    onClick = onRetry,
                )
            }
        }
    }
}
