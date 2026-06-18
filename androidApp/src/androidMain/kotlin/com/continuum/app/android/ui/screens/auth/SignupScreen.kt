package com.continuum.app.android.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.continuum.app.android.ui.components.aurora.AuroraEyebrow
import com.continuum.app.android.ui.components.aurora.AuroraErrorLabel
import com.continuum.app.android.ui.components.aurora.AuroraGhostButton
import com.continuum.app.android.ui.components.aurora.AuroraPrimaryButton
import com.continuum.app.android.ui.components.aurora.AuroraScreen
import com.continuum.app.android.ui.components.aurora.AuroraScrim
import com.continuum.app.android.ui.components.aurora.AuroraTextField
import com.continuum.app.android.ui.components.aurora.AuroraVariant
import com.continuum.app.android.ui.components.aurora.auroraGlass
import org.koin.compose.viewmodel.koinViewModel

/**
 * Account registration. Mirrors silo-apple iOS phone `SignupView` (Aurora):
 * wordmark, "Create account" eyebrow + "Create your account", glass card with
 * Username / Email / Password / Invite code, cream "Create account" button, and
 * a "Back to sign in" ghost. (Android VM has no confirm-password field, so it
 * is omitted rather than adding VM state.)
 */
@Composable
fun SignupScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    viewModel: SignupViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(state.signupSuccess) {
        if (state.signupSuccess) {
            viewModel.onSignupSuccessConsumed()
            onNavigateToProfiles()
        }
    }

    AuroraScreen(variant = AuroraVariant.SignIn, scrim = AuroraScrim.Soft) {
        ContinuumLogo()

        Spacer(Modifier.height(30.dp))
        AuroraEyebrow(text = "Create account", centered = true)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Create your account",
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF3EFE9),
        )
        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .auroraGlass(cornerRadius = 24.dp, emphasized = true)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AuroraTextField(
                label = "Username",
                value = state.username,
                onValueChange = viewModel::onUsernameChanged,
                placeholder = "yourname",
                imeAction = ImeAction.Next,
            )
            AuroraTextField(
                label = "Email",
                value = state.email,
                onValueChange = viewModel::onEmailChanged,
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )
            AuroraTextField(
                label = "Password",
                value = state.password,
                onValueChange = viewModel::onPasswordChanged,
                placeholder = "••••••",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
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
            AuroraTextField(
                label = "Invite code",
                value = state.inviteCode,
                onValueChange = viewModel::onInviteCodeChanged,
                placeholder = "ABCD-1234",
                imeAction = ImeAction.Go,
                onImeAction = viewModel::onSignupClick,
            )

            state.error?.let { AuroraErrorLabel(it) }

            AuroraPrimaryButton(
                label = if (state.isLoading) "Creating…" else "Create account",
                onClick = viewModel::onSignupClick,
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            AuroraGhostButton(
                label = "Back to sign in",
                onClick = onNavigateToLogin,
                fillMaxWidth = true,
            )
        }
    }
}
