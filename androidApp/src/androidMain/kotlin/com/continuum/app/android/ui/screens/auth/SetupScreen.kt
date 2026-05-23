package com.continuum.app.android.ui.screens.auth

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel

/**
 * First-time server setup screen. Creates the initial admin account.
 *
 * @param onNavigateToProfiles Called after the admin account is successfully created.
 */
@Composable
fun SetupScreen(
    onNavigateToProfiles: () -> Unit,
    viewModel: SetupViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.setupSuccess) {
        if (state.setupSuccess) {
            viewModel.onSetupSuccessConsumed()
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
                text = "Welcome to Silo",
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = AuthColors.OnBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Create your first admin profile.",
                fontSize = 15.sp,
                color = AuthColors.OnSurfaceVariant,
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

            ContinuumTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChanged,
                label = "Email",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )

            Spacer(modifier = Modifier.height(16.dp))

            ContinuumPasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChanged,
                label = "Password",
                imeAction = ImeAction.Done,
                onImeAction = viewModel::onCreateAccountClick,
            )

            Spacer(modifier = Modifier.height(24.dp))

            ContinuumButton(
                text = "Create Account",
                onClick = viewModel::onCreateAccountClick,
                isLoading = state.isLoading,
            )
        }
    }
}
