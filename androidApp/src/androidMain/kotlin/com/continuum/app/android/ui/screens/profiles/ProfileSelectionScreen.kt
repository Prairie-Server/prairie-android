package com.continuum.app.android.ui.screens.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.continuum.app.android.ui.screens.auth.AuthStage
import com.continuum.app.android.ui.screens.auth.AuthColors
import com.continuum.app.android.ui.screens.auth.AuthErrorBanner
import com.continuum.app.model.profile.Profile
import org.koin.compose.viewmodel.koinViewModel

/**
 * Grid of profile avatars shown after login.
 *
 * @param onNavigateToHome Called after a profile is selected.
 * @param onNavigateToCreateProfile Called when the "Add Profile" card is tapped.
 * @param onNavigateToEditProfile Called when the edit icon is tapped in manage mode.
 */
@Composable
fun ProfileSelectionScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToCreateProfile: () -> Unit,
    onNavigateToEditProfile: (profileId: String) -> Unit,
    viewModel: ProfileSelectionViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Navigate after a profile is selected.
    LaunchedEffect(state.selectedProfileId) {
        if (state.selectedProfileId != null) {
            viewModel.onProfileSelectedConsumed()
            onNavigateToHome()
        }
    }

    // PIN entry dialog
    state.pinDialogProfile?.let { profile ->
        PINEntryDialog(
            profileName = profile.name,
            isLoading = state.pinIsVerifying,
            error = state.pinError,
            onPinComplete = viewModel::onPinEntered,
            onDismiss = viewModel::dismissPinDialog,
        )
    }

    AuthStage {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Who's watching?",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AuthColors.OnBackground,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Choose a profile to continue.",
                    fontSize = 15.sp,
                    color = AuthColors.OnSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                state.error?.let { error ->
                    AuthErrorBanner(message = error)
                }

                if (state.isLoading) {
                    Spacer(modifier = Modifier.height(48.dp))
                    CircularProgressIndicator(color = AuthColors.Primary)
                } else {
                    Spacer(modifier = Modifier.height(32.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        modifier = Modifier.height(220.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(26.dp),
                    ) {
                        items(state.profiles, key = { it.id }) { profile ->
                            ProfileCard(
                                profile = profile,
                                isManageMode = state.isManageMode,
                                onTap = { viewModel.onProfileTapped(profile) },
                                onEdit = { onNavigateToEditProfile(profile.id) },
                                onDelete = { viewModel.deleteProfile(profile.id) },
                            )
                        }

                        item {
                            AddProfileCard(onClick = onNavigateToCreateProfile)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    TextButton(onClick = viewModel::toggleManageMode) {
                        Text(
                            text = if (state.isManageMode) "Done" else "Manage Profiles",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = AuthColors.OnBackground,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    isManageMode: Boolean,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(contentAlignment = Alignment.TopEnd) {
        Column(
            modifier = Modifier
                .clickable(onClick = if (isManageMode) onEdit else onTap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileAvatar(
                avatar = profile.avatar,
                name = profile.name,
                size = 80.dp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = profile.name,
                fontSize = 14.sp,
                color = AuthColors.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            if (profile.isChild) {
                Text(
                    text = "Child",
                    fontSize = 11.sp,
                    color = AuthColors.OnSurfaceVariant,
                )
            }
        }

        // Manage mode overlays
        if (isManageMode) {
            // Edit button
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit profile",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Delete button (offset to the left)
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AuthColors.Error),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Delete profile",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add profile",
                tint = AuthColors.OnSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add Profile",
            fontSize = 14.sp,
            color = AuthColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
