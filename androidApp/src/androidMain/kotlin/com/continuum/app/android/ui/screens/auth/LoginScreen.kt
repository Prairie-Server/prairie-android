package com.continuum.app.android.ui.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel

/**
 * Login screen with username / password fields.
 *
 * @param signupEnabled Whether the "Create Account" link should be shown.
 * @param onNavigateToSignup Called when the user taps "Create Account".
 * @param onNavigateToProfiles Called after a successful login.
 */
@Composable
fun LoginScreen(
    signupEnabled: Boolean = false,
    onNavigateToSignup: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Propagate the parameter into the ViewModel once.
    LaunchedEffect(signupEnabled) {
        viewModel.setSignupEnabled(signupEnabled)
    }

    // React to successful login.
    LaunchedEffect(state.loginSuccess) {
        if (state.loginSuccess) {
            viewModel.onLoginSuccessConsumed()
            onNavigateToProfiles()
        }
    }

    AuthStage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ContinuumLogo()

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Sign In",
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = AuthColors.OnBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pick up where you left off.",
                fontSize = 15.sp,
                color = AuthColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            state.error?.let { error ->
                AuthErrorBanner(message = error)
                Spacer(modifier = Modifier.height(12.dp))
            }

            ContinuumTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChanged,
                label = "Username",
                imeAction = ImeAction.Next,
            )

            Spacer(modifier = Modifier.height(16.dp))

            ContinuumPasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChanged,
                label = "Password",
                imeAction = ImeAction.Done,
                onImeAction = viewModel::onLoginClick,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Forgot Password?",
                fontSize = 13.sp,
                color = AuthColors.OnSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 4.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            ContinuumButton(
                text = "Sign In",
                onClick = viewModel::onLoginClick,
                isLoading = state.isLoading,
            )

            if (state.signupEnabled) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Create Account",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = AuthColors.OnBackground,
                    modifier = Modifier.clickable(onClick = onNavigateToSignup),
                )
            }
        }
    }
}
