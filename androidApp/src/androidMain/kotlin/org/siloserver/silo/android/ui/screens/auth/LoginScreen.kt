package org.siloserver.silo.android.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.siloserver.silo.android.ui.components.aurora.AuroraEyebrow
import org.siloserver.silo.android.ui.components.aurora.AuroraErrorLabel
import org.siloserver.silo.android.ui.components.aurora.AuroraGhostButton
import org.siloserver.silo.android.ui.components.aurora.AuroraInkTertiary
import org.siloserver.silo.android.ui.components.aurora.AuroraPrimaryButton
import org.siloserver.silo.android.ui.components.aurora.AuroraScreen
import org.siloserver.silo.android.ui.components.aurora.AuroraScrim
import org.siloserver.silo.android.ui.components.aurora.AuroraTextField
import org.siloserver.silo.android.ui.components.aurora.AuroraVariant
import org.siloserver.silo.android.ui.components.aurora.auroraGlass
import org.koin.compose.viewmodel.koinViewModel

/**
 * Password-first sign-in. Mirrors silo-apple iOS phone `LoginView` (Aurora):
 * wordmark, "Step 02 — Sign in" eyebrow + "Welcome back", then a glass card with
 * Username + Password fields, the cream "Sign in" button, and the
 * Create-account / Change-server ghost buttons.
 *
 * @param signupEnabled Whether the "Create account" ghost is shown.
 * @param onNavigateToSignup Tapped "Create account".
 * @param onNavigateToProfiles Called after a successful login.
 * @param onChangeServer Tapped "Change server" (back to server setup).
 */
@Composable
fun LoginScreen(
    signupEnabled: Boolean = false,
    onNavigateToSignup: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onChangeServer: () -> Unit = {},
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(signupEnabled) { viewModel.setSignupEnabled(signupEnabled) }
    LaunchedEffect(state.loginSuccess) {
        if (state.loginSuccess) {
            viewModel.onLoginSuccessConsumed()
            onNavigateToProfiles()
        }
    }

    AuroraScreen(variant = AuroraVariant.SignIn, scrim = AuroraScrim.Soft) {
        SiloLogo()

        Spacer(Modifier.height(30.dp))
        AuroraEyebrow(text = "Step 02 — Sign in", centered = true)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Welcome back",
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF3EFE9),
        )
        state.serverHostLabel?.let { host ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = host,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = AuroraInkTertiary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(24.dp))

        Column2Glass {
            AuroraTextField(
                label = "Username",
                value = state.username,
                onValueChange = viewModel::onUsernameChanged,
                placeholder = "yourname",
                imeAction = ImeAction.Next,
            )
            AuroraTextField(
                label = "Password",
                value = state.password,
                onValueChange = viewModel::onPasswordChanged,
                placeholder = "••••••",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
                onImeAction = viewModel::onLoginClick,
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailing = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = Color.White.copy(alpha = 0.62f),
                        )
                    }
                },
            )

            state.error?.let { AuroraErrorLabel(it) }

            AuroraPrimaryButton(
                label = if (state.isLoading) "Signing in…" else "Sign in",
                onClick = viewModel::onLoginClick,
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, androidx.compose.ui.Alignment.CenterHorizontally),
            ) {
                if (state.signupEnabled) {
                    AuroraGhostButton(label = "Create account", onClick = onNavigateToSignup)
                }
                AuroraGhostButton(label = "Change server", onClick = onChangeServer)
            }
        }
    }
}

@Composable
private fun Column2Glass(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .auroraGlass(cornerRadius = 24.dp, emphasized = true)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}
