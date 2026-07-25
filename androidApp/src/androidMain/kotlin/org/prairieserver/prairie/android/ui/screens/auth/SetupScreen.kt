package org.prairieserver.prairie.android.ui.screens.auth

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
import org.prairieserver.prairie.android.ui.components.aurora.AuroraEyebrow
import org.prairieserver.prairie.android.ui.components.aurora.AuroraErrorLabel
import org.prairieserver.prairie.android.ui.components.aurora.AuroraPrimaryButton
import org.prairieserver.prairie.android.ui.components.aurora.AuroraScreen
import org.prairieserver.prairie.android.ui.components.aurora.AuroraScrim
import org.prairieserver.prairie.android.ui.components.aurora.AuroraTextField
import org.prairieserver.prairie.android.ui.components.aurora.AuroraVariant
import org.prairieserver.prairie.android.ui.components.aurora.auroraGlass
import org.koin.compose.viewmodel.koinViewModel

/**
 * First-run admin account creation — an Android capability (iOS points to the
 * browser for this); kept and reskinned in the Aurora grammar to match the rest
 * of onboarding. Wordmark, "Setup needed" eyebrow + "Welcome to Prairie", glass
 * card with Username / Email / Password + cream "Create account".
 */
@Composable
fun SetupScreen(
    onNavigateToProfiles: () -> Unit,
    viewModel: SetupViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(state.setupSuccess) {
        if (state.setupSuccess) {
            viewModel.onSetupSuccessConsumed()
            onNavigateToProfiles()
        }
    }

    AuroraScreen(variant = AuroraVariant.Server, scrim = AuroraScrim.Soft) {
        PrairieLogo()

        Spacer(Modifier.height(30.dp))
        AuroraEyebrow(text = "Setup needed", centered = true)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Welcome to Prairie",
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF3EFE9),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Create your first admin profile.",
            fontSize = 15.sp,
            color = Color(0xFFF3EFE9).copy(alpha = 0.7f),
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
                imeAction = ImeAction.Go,
                onImeAction = viewModel::onCreateAccountClick,
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
                label = if (state.isLoading) "Creating…" else "Create account",
                onClick = viewModel::onCreateAccountClick,
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
