package com.continuum.app.tv.ui.screens.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.common.ui.components.isImageAvatar
import com.continuum.app.common.ui.components.profileAvatarDisplayText
import com.continuum.app.common.ui.components.rememberProfileServerUrl
import com.continuum.app.common.ui.components.resolveAvatarUrl
import com.continuum.app.model.profile.Profile
import com.continuum.app.tv.ui.components.TvAnimatedMeshBackground
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.components.TvPinEntryDialog
import com.continuum.app.tv.ui.theme.Spacing
import com.continuum.app.tv.ui.theme.continuumCardDefaults
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvProfileSelectionScreen(
    onProfileSelected: () -> Unit,
    viewModel: TvProfileSelectionViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.selectedProfileId) {
        if (state.selectedProfileId != null) {
            viewModel.onSelectionConsumed()
            onProfileSelected()
        }
    }

    when {
        state.isLoading -> TvLoadingScreen()
        state.error != null && state.profiles.isEmpty() -> TvErrorScreen(
            message = state.error!!,
            onRetry = viewModel::loadProfiles,
        )
        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                TvAnimatedMeshBackground()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = Spacing.safeArea, vertical = Spacing.xxxl),
                ) {
                    Text(
                        text = "Profiles",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = "Who's watching?",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "Choose a profile to continue.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(Spacing.xxl))

                    val firstCardFocus = remember { FocusRequester() }
                    LaunchedEffect(state.profiles) {
                        if (state.profiles.isNotEmpty()) {
                            // Grab D-pad focus on the first card so the user can immediately
                            // press OK without having to "find" where focus is.
                            runCatching { firstCardFocus.requestFocus() }
                        }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp),
                    ) {
                        itemsIndexed(state.profiles, key = { _, p -> p.id }) { index, profile ->
                            TvProfileCard(
                                profile = profile,
                                onClick = { viewModel.onProfileSelected(profile) },
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(firstCardFocus)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }

                    if (state.error != null) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    // PIN entry dialog — shown when a PIN-protected profile is selected.
    val pinProfile = state.pinProfile
    if (pinProfile != null) {
        TvPinEntryDialog(
            profileName = pinProfile.name,
            errorMessage = state.pinError,
            isVerifying = state.isVerifyingPin,
            onPinEntered = viewModel::onPinEntered,
            onDismiss = { viewModel.onPinDialogDismissed() },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvProfileCard(
    profile: Profile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val serverUrl = rememberProfileServerUrl()
    val avatarText = remember(profile.avatar, profile.name) {
        profileAvatarDisplayText(profile.avatar, profile.name)
    }
    val avatarUrl = remember(profile.avatar, serverUrl) {
        profile.avatar
            ?.takeIf(::isImageAvatar)
            ?.let { resolveAvatarUrl(serverUrl, it) }
    }

    val shape = RoundedCornerShape(24.dp)
    val cardFocus = continuumCardDefaults(shape = shape)
    val tileTint = profile.tintColor()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(shape = shape),
            scale = cardFocus.scale,
            border = cardFocus.border,
            glow = cardFocus.glow,
            modifier = modifier.size(180.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tileTint)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = shape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl != null) {
                    ThumbhashImage(
                        url = avatarUrl,
                        thumbhash = null,
                        contentDescription = profile.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        transparent = true,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.22f),
                                ),
                            ),
                    )
                } else {
                    Text(
                        text = avatarText,
                        fontSize = if (!profile.avatar.isNullOrBlank() && !isImageAvatar(profile.avatar)) {
                            70.sp
                        } else {
                            58.sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.94f),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.88f),
        )
    }
}

private fun Profile.tintColor(): Color {
    val palette = listOf(
        Color(0xFFB56F5F),
        Color(0xFF6B7C93),
        Color(0xFF8A785E),
        Color(0xFF607C6A),
        Color(0xFF92656E),
        Color(0xFF6F668B),
        Color(0xFF577B83),
        Color(0xFFA28453),
    )
    return palette[kotlin.math.abs(id.hashCode()) % palette.size]
}
