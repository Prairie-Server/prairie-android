package com.continuum.app.tv.ui.screens.auth

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.tv.R
import com.continuum.app.common.pairing.PairingReceiver
import com.continuum.app.common.pairing.PairingReceiverStatus
import com.continuum.app.common.pairing.TvPairingAdvertiser
import com.continuum.app.tv.ui.components.AuroraEyebrow
import com.continuum.app.tv.ui.components.auroraGlass
import com.continuum.app.tv.ui.components.AuroraPrimaryButton
import com.continuum.app.tv.ui.components.TvAuroraBackdrop
import com.continuum.app.tv.ui.components.TvAuroraVariant
import com.continuum.app.tv.ui.components.tvOutlinedTextFieldColors
import com.continuum.app.tv.ui.theme.Spacing
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Server setup — connects the app to a Silo server.
 *
 * Layout is deliberately COMPACT and TOP-ANCHORED (not vertically centered)
 * so the URL field stays visible when the on-screen IME is open. On Android
 * TV the soft keyboard covers roughly the lower half of the screen, so any
 * centered form gets its input hidden. The input sits in the upper third;
 * a small brand row sits above it, a single helper line below, and the
 * Connect pill at the bottom of the form.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvServerSetupScreen(
    onContinueToLogin: (signupEnabled: Boolean) -> Unit,
    onNeedsSetup: () -> Unit,
    onPairedSignIn: () -> Unit = {},
    viewModel: TvServerSetupViewModel = koinViewModel(),
    pairingReceiver: PairingReceiver = koinInject(),
    pairingAdvertiser: TvPairingAdvertiser = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()
    val pairingStatus by pairingReceiver.status.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val urlBringIntoView = remember { BringIntoViewRequester() }
    val connectBringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    // Companion LAN pairing: advertise `_silopair._tcp` while this screen is on
    // so a phone running Silo can push the server URL + drive device-login,
    // sparing the viewer from typing a URL on the remote. Advertising stops
    // when the screen leaves the composition.
    DisposableEffect(Unit) {
        pairingAdvertiser.start()
        onDispose { pairingAdvertiser.stop() }
    }
    LaunchedEffect(pairingStatus) {
        if (pairingStatus is PairingReceiverStatus.SignedIn) {
            pairingAdvertiser.stop()
            onPairedSignIn()
        }
    }

    LaunchedEffect(state.navigateTo) {
        when (val dest = state.navigateTo) {
            is TvServerSetupDestination.Setup -> {
                viewModel.onNavigationConsumed()
                onNeedsSetup()
            }
            is TvServerSetupDestination.Login -> {
                viewModel.onNavigationConsumed()
                onContinueToLogin(dest.signupEnabled)
            }
            null -> Unit
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // imePadding + verticalScroll + bringIntoView ensures the focused field
    // (URL input or Connect pill) is always visible above the soft IME.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TvAuroraBackdrop(variant = TvAuroraVariant.Server)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(840.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 48.dp, bottom = Spacing.lg, start = Spacing.xl, end = Spacing.xl),
        ) {
            BrandHeader()

            Spacer(modifier = Modifier.height(Spacing.xs))

            AuroraEyebrow(text = "Step 01 — Connect")

            Text(
                text = "Connect to your server",
                style = TvServerSetupTextStyles.Title,
                color = Color.White,
            )

            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = viewModel::onServerUrlChanged,
                label = {
                    Text(
                        text = "Server URL",
                        style = TvServerSetupTextStyles.FieldLabel,
                        color = Color.White,
                    )
                },
                placeholder = {
                    Text(
                        text = "https://streamapp.example.com",
                        style = TvServerSetupTextStyles.FieldText,
                        color = Color.White.copy(alpha = 0.68f),
                    )
                },
                singleLine = true,
                textStyle = TvServerSetupTextStyles.FieldText,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        if (!state.isLoading && state.serverUrl.isNotBlank()) {
                            viewModel.onConnectClick()
                        }
                    },
                ),
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(urlBringIntoView)
                    .onFocusEvent { fs ->
                        if (fs.isFocused) scope.launch { urlBringIntoView.bringIntoView() }
                    }
                    .focusRequester(focusRequester),
                colors = tvOutlinedTextFieldColors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.White.copy(alpha = 0.96f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.58f),
                ),
            )

            if (state.error != null) {
                Text(
                    text = state.error!!,
                    style = TvServerSetupTextStyles.Error,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Box(
                modifier = Modifier
                    .bringIntoViewRequester(connectBringIntoView)
                    .onFocusEvent { fs ->
                        if (fs.hasFocus) scope.launch { connectBringIntoView.bringIntoView() }
                    },
            ) {
                AuroraPrimaryButton(
                    label = if (state.isLoading) "Connecting…" else "Connect",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = viewModel::onConnectClick,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
            PairingPanel(status = pairingStatus)
        }
    }
}

/**
 * Companion-pairing status panel — passive (no focus target): tells the viewer
 * they can configure this TV from a phone on the same network, and reflects the
 * live [PairingReceiver] state once a phone connects. Mirrors tvOS
 * `TVPairingReceiverView` shown in-place inside server setup.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PairingPanel(status: PairingReceiverStatus) {
    val (headline, detail) = when (status) {
        is PairingReceiverStatus.AwaitingApproval ->
            "Confirm on your phone" to "Match code ${status.matchCode} — approve the sign-in on your phone."
        is PairingReceiverStatus.Pairing ->
            "Pairing…" to "Setting up ${status.serverURL} from your phone."
        PairingReceiverStatus.Connected ->
            "Phone connected" to "Choose a server on your phone to continue."
        PairingReceiverStatus.SignedIn ->
            "Signed in" to "Finishing up…"
        is PairingReceiverStatus.Failed ->
            "Pairing failed" to status.message
        else ->
            "Or pair from your phone" to "Open Silo on a phone on this network to set up this TV — no typing required."
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .auroraGlass(20.dp)
            .padding(horizontal = 28.dp, vertical = 22.dp),
    ) {
        AuroraEyebrow(text = "Companion pairing")
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = headline,
            style = TvServerSetupTextStyles.FieldLabel,
            color = Color.White,
        )
        Text(
            text = detail,
            style = TvServerSetupTextStyles.PairingDetail,
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}

private object TvServerSetupTextStyles {
    val Title = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    )

    val FieldLabel = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    )

    val FieldText = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
        color = Color.White,
    )

    val Button = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    )

    val Error = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )

    val PairingDetail = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )
}

/** Compact horizontal brand row — replaces the oversized centered logo/title pair. */
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
