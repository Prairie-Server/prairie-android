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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel

/**
 * First screen the user sees. Allows entering the Silo server URL.
 *
 * After validating the server:
 * - If [SetupStatusResponse.needsSetup] is true, navigates to [onNavigateToSetup].
 * - Otherwise navigates to [onNavigateToLogin], indicating whether signup is enabled.
 */
@Composable
fun ServerSetupScreen(
    onNavigateToSetup: () -> Unit,
    onNavigateToLogin: (signupEnabled: Boolean) -> Unit,
    viewModel: ServerSetupViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // React to navigation events produced by the ViewModel.
    LaunchedEffect(state.navigateTo) {
        when (val dest = state.navigateTo) {
            is ServerSetupDestination.Setup -> {
                viewModel.onNavigationConsumed()
                onNavigateToSetup()
            }

            is ServerSetupDestination.Login -> {
                viewModel.onNavigationConsumed()
                onNavigateToLogin(dest.signupEnabled)
            }

            null -> Unit
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
                text = "Connect to Your Server",
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = AuthColors.OnBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter the server address for your Silo library.",
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
                value = state.serverUrl,
                onValueChange = viewModel::onServerUrlChanged,
                label = "Server URL",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
                onImeAction = viewModel::onConnectClick,
            )

            Spacer(modifier = Modifier.height(24.dp))

            ContinuumButton(
                text = "Connect",
                onClick = viewModel::onConnectClick,
                isLoading = state.isLoading,
            )
        }
    }
}
