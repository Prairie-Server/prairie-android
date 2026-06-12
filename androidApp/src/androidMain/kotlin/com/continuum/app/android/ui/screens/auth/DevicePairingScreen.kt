package com.continuum.app.android.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.continuum.app.viewmodel.DevicePairingViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DevicePairingScreen(
    token: String?,
    code: String?,
    onDone: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: DevicePairingViewModel = koinViewModel(
        parameters = { parametersOf(token to code) },
    ),
) {
    val state by viewModel.uiState.collectAsState()

    AuthStage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ContinuumLogo()

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Pair Device",
                style = MaterialTheme.typography.headlineMedium,
                color = AuthColors.OnBackground,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = state.lookup?.deviceName?.let { deviceName ->
                    "Approve sign-in for $deviceName"
                } ?: "Approve sign-in on another screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = AuthColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = state.lookup?.userCode ?: state.code,
                onValueChange = viewModel::onCodeChanged,
                enabled = state.token.isNullOrBlank() && state.completedStatus == null,
                label = { Text("Code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            state.lookup?.let { lookup ->
                Spacer(modifier = Modifier.height(18.dp))
                PairingDetailRow(label = "Match", value = lookup.matchCode.orEmpty())
                PairingDetailRow(label = "Device", value = lookup.deviceName.orEmpty())
                PairingDetailRow(label = "Platform", value = lookup.devicePlatform.orEmpty())
                lookup.ipAddressHint?.takeIf { it.isNotBlank() }?.let {
                    PairingDetailRow(label = "IP", value = it)
                }
            }

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(18.dp))
                AuthErrorBanner(message = error)
                if (error.startsWith("Sign in")) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign In")
                    }
                }
            }

            state.completedStatus?.let { status ->
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = if (status == "approved") "Device approved." else "Device denied.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AuthColors.OnBackground,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (state.completedStatus == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::lookup,
                        enabled = !state.isLoading && !state.isSubmitting,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                        Text("Check")
                    }
                    OutlinedButton(
                        onClick = viewModel::deny,
                        enabled = state.canSubmit,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Text("Deny")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = viewModel::approve,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AuthColors.Primary),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Text("Approve")
                }
            } else {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AuthColors.Primary),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun PairingDetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AuthColors.OnSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = AuthColors.OnBackground,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}
