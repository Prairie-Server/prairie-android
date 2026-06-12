package com.continuum.app.tv.ui.shell

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.common.ui.components.profileAvatarDisplayText
import com.continuum.app.tv.ui.theme.ContinuumOnSurface
import com.continuum.app.tv.ui.theme.navRailLabel

/**
 * Layout constants mirrored from `TVTopMenuLayout` in
 * `iosApp/iosApp/tvOS/Navigation/TVTopMenuBar.swift`. Names match 1:1 so
 * future audits against the iOS source are mechanical.
 */
object TvTopMenuLayout {
    val headerBandHeight: Dp = 46.dp
    val primaryRowHeight: Dp = 27.dp
    val primaryTopInset: Dp = 8.dp
    val searchButtonWidth: Dp = 41.dp
    val homeButtonWidth: Dp = 68.dp
    val librariesButtonWidth: Dp = 98.dp
    val forYouButtonWidth: Dp = 82.dp
    val requestsButtonWidth: Dp = 92.dp
    val rootTextSize = 12
    val itemSpacing: Dp = 17.dp
    val leadingInset: Dp = 22.dp
    val trailingInset: Dp = 22.dp
    val profileMenuTopInset: Dp = 46.dp
    /// Vertical clearance reserved on root pages so content doesn't draw under
    /// the menu band. Mirrors `contentTopInset` on tvOS.
    val contentTopInset: Dp = 94.dp
}

/** Identifies which top-level destination the menu bar selects/highlights. */
enum class TvRootDestination { Search, Home, Libraries, ForYou }

/**
 * The custom top menu bar that replaces the legacy left-side rail.
 *
 * Layout matches `TVTopMenuBar.swift`:
 * - Center cluster: Search icon · Home · Libraries · For You (focusGroup, horizontal traversal)
 * - Leading cluster: Profile avatar with chevron
 * - Behind everything: a black gradient scrim so buttons read against hero art.
 *
 * Focus model:
 * - `isMenuFocused` is updated as soon as any button gains/loses focus so the
 *   parent shell can hide/show the menu in response.
 * - `focusRequest` is an integer counter — incrementing it causes the bar to
 *   request focus on the currently-selected destination. Using a counter (not
 *   a boolean) means the parent can re-trigger restoration without first
 *   clearing the flag, which matches the tvOS `topMenuFocusRequest` pattern.
 * - When `isFocusSuppressed` is true the bar drops any current focus so D-pad
 *   navigation in the content area below cannot accidentally pull focus back
 *   into the menu via Compose's spatial-focus search.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTopMenuBar(
    selectedRoot: TvRootDestination?,
    destinations: List<TvRootDestination> = TvRootDestination.entries,
    accountState: TvAccountState,
    unreadCount: Int = 0,
    onSelectRoot: (TvRootDestination) -> Unit,
    onProfileClick: () -> Unit,
    onMoveDown: () -> Unit,
    isMenuFocused: Boolean,
    onMenuFocusChange: (Boolean) -> Unit,
    isFocusSuppressed: Boolean,
    focusRequest: Int,
    visibility: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val searchFocusRequester = remember { FocusRequester() }
    val homeFocusRequester = remember { FocusRequester() }
    val librariesFocusRequester = remember { FocusRequester() }
    val forYouFocusRequester = remember { FocusRequester() }
    val profileFocusRequester = remember { FocusRequester() }

    // Track which button currently holds focus so we can shape colors/scale
    // without depending on the surface's focused-state propagation, which is
    // unreliable when the parent suppresses focus mid-animation.
    var focusedButton by remember { mutableStateOf<TvTopMenuFocus?>(null) }

    // `LaunchedEffect(focusRequest, isFocusSuppressed)` means a request that
    // arrives while the bar is still suppressed won't be silently dropped —
    // when suppression lifts, we re-evaluate and request focus.
    LaunchedEffect(focusRequest, isFocusSuppressed) {
        if (isFocusSuppressed) return@LaunchedEffect
        // Non-tab routes (selectedRoot == null) have no menu button to focus —
        // fall back to the search button so an explicit focus request still
        // lands on the bar rather than being dropped.
        val target = when (selectedRoot) {
            TvRootDestination.Search -> searchFocusRequester
            TvRootDestination.Home -> homeFocusRequester
            TvRootDestination.Libraries -> librariesFocusRequester
            TvRootDestination.ForYou -> forYouFocusRequester
            null -> searchFocusRequester
        }
        runCatching { target.requestFocus() }
    }

    LaunchedEffect(focusedButton) {
        onMenuFocusChange(focusedButton != null)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TvTopMenuLayout.headerBandHeight)
            .graphicsLayer {
                // Slide the menu up by its own height as visibility drops to 0
                // and fade alpha in lockstep. Mirrors Apple's TVTopMenuBar
                // hide-on-scroll transition. Driven by the parent shell's
                // nestedScroll-driven `menuVisibility` Animatable.
                translationY = -size.height * (1f - visibility)
                alpha = visibility
            }
            .background(
                Brush.verticalGradient(
                    0.00f to Color.Black.copy(alpha = 0.84f),
                    0.58f to Color.Black.copy(alpha = 0.72f),
                    0.88f to Color.Black.copy(alpha = 0.36f),
                    1.00f to Color.Transparent,
                ),
            )
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onMoveDown()
                    true
                } else {
                    false
                }
            },
    ) {
        // Left cluster (search + 3 root tabs)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = TvTopMenuLayout.primaryTopInset),
            horizontalArrangement = Arrangement.spacedBy(TvTopMenuLayout.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                when (destination) {
                    TvRootDestination.Search -> TvTopMenuIconButton(
                        icon = Icons.Outlined.Search,
                        contentDescription = "Search",
                        width = TvTopMenuLayout.searchButtonWidth,
                        isSelected = selectedRoot == TvRootDestination.Search,
                        isFocused = focusedButton == TvTopMenuFocus.Search,
                        focusRequester = searchFocusRequester,
                        onFocusChanged = { hasFocus ->
                            focusedButton = if (hasFocus) {
                                TvTopMenuFocus.Search
                            } else {
                                focusedButton.takeUnless { it == TvTopMenuFocus.Search }
                            }
                        },
                        onClick = {
                            onSelectRoot(TvRootDestination.Search)
                        },
                        // Search is the leftmost item in the center cluster. Spatial
                        // focus search wouldn't reliably reach the profile button
                        // because they're in separate Rows aligned to different
                        // anchors (TopCenter vs TopStart). Pin LEFT explicitly.
                        extraModifier = Modifier.focusProperties { left = profileFocusRequester },
                    )

                    TvRootDestination.Home -> TvTopMenuTextButton(
                        label = "Home",
                        width = TvTopMenuLayout.homeButtonWidth,
                        isSelected = selectedRoot == TvRootDestination.Home,
                        isFocused = focusedButton == TvTopMenuFocus.Home,
                        focusRequester = homeFocusRequester,
                        onFocusChanged = { hasFocus ->
                            focusedButton = if (hasFocus) {
                                TvTopMenuFocus.Home
                            } else {
                                focusedButton.takeUnless { it == TvTopMenuFocus.Home }
                            }
                        },
                        onClick = { onSelectRoot(TvRootDestination.Home) },
                    )

                    TvRootDestination.Libraries -> TvTopMenuTextButton(
                        label = "Libraries",
                        width = TvTopMenuLayout.librariesButtonWidth,
                        isSelected = selectedRoot == TvRootDestination.Libraries,
                        isFocused = focusedButton == TvTopMenuFocus.Libraries,
                        focusRequester = librariesFocusRequester,
                        onFocusChanged = { hasFocus ->
                            focusedButton = if (hasFocus) {
                                TvTopMenuFocus.Libraries
                            } else {
                                focusedButton.takeUnless { it == TvTopMenuFocus.Libraries }
                            }
                        },
                        onClick = { onSelectRoot(TvRootDestination.Libraries) },
                    )

                    TvRootDestination.ForYou -> TvTopMenuTextButton(
                        label = "For You",
                        width = TvTopMenuLayout.librariesButtonWidth,
                        isSelected = selectedRoot == TvRootDestination.ForYou,
                        isFocused = focusedButton == TvTopMenuFocus.ForYou,
                        focusRequester = forYouFocusRequester,
                        onFocusChanged = { hasFocus ->
                            focusedButton = if (hasFocus) {
                                TvTopMenuFocus.ForYou
                            } else {
                                focusedButton.takeUnless { it == TvTopMenuFocus.ForYou }
                            }
                        },
                        onClick = { onSelectRoot(TvRootDestination.ForYou) },
                    )
                }
            }
        }

        // Leading cluster (profile avatar + chevron)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = TvTopMenuLayout.leadingInset, top = TvTopMenuLayout.primaryTopInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvTopMenuProfileButton(
                accountState = accountState,
                unreadCount = unreadCount,
                isFocused = focusedButton == TvTopMenuFocus.Profile,
                focusRequester = profileFocusRequester,
                onFocusChanged = { hasFocus ->
                    focusedButton = if (hasFocus) TvTopMenuFocus.Profile else focusedButton.takeUnless { it == TvTopMenuFocus.Profile }
                },
                onClick = onProfileClick,
                // Mirror of the bridge on the Search button: pressing RIGHT
                // from the profile avatar lands on the Search icon.
                extraModifier = Modifier.focusProperties { right = searchFocusRequester },
            )
        }
    }
}

private enum class TvTopMenuFocus { Search, Home, Libraries, ForYou, Profile }

/** Minimal account view-data the menu bar renders. */
data class TvAccountState(
    val displayName: String = "Profile",
    val avatar: String? = null,
    val avatarUrl: String? = null,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvTopMenuTextButton(
    label: String,
    width: Dp,
    isSelected: Boolean,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isInteractionFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isInteractionFocused) { onFocusChanged(isInteractionFocused) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.025f else 1.0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
        label = "tvTopMenuTextButtonScale",
    )

    val shape = RoundedCornerShape(12.dp)
    val containerColor = when {
        isFocused -> Color.White.copy(alpha = 0.18f)
        isSelected -> Color.White.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val borderColor = when {
        isFocused -> Color.White.copy(alpha = 0.28f)
        isSelected -> Color.White.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    val textColor = when {
        isFocused || isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.70f)
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = textColor,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.18f),
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.dp, borderColor), shape = shape),
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.28f)),
                shape = shape,
            ),
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .width(width)
            .height(TvTopMenuLayout.primaryRowHeight)
            .graphicsScale(scale),
    ) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = navRailLabel,
                fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvTopMenuIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    width: Dp,
    isSelected: Boolean,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    extraModifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isInteractionFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isInteractionFocused) { onFocusChanged(isInteractionFocused) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.025f else 1.0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
        label = "tvTopMenuIconButtonScale",
    )

    val shape = RoundedCornerShape(12.dp)
    val containerColor = when {
        isFocused -> Color.White.copy(alpha = 0.18f)
        isSelected -> Color.White.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val borderColor = when {
        isFocused -> Color.White.copy(alpha = 0.28f)
        isSelected -> Color.White.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    val iconColor = when {
        isFocused || isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.72f)
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = iconColor,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.18f),
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.dp, borderColor), shape = shape),
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.28f)),
                shape = shape,
            ),
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .then(extraModifier)
            .width(width)
            .height(TvTopMenuLayout.primaryRowHeight)
            .graphicsScale(scale),
    ) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvTopMenuProfileButton(
    accountState: TvAccountState,
    unreadCount: Int,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    extraModifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isInteractionFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isInteractionFocused) { onFocusChanged(isInteractionFocused) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.025f else 1.0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
        label = "tvTopMenuProfileScale",
    )

    val shape = RoundedCornerShape(12.dp)
    val containerColor = if (isFocused) Color.White.copy(alpha = 0.18f) else Color.Transparent
    val borderColor = if (isFocused) Color.White.copy(alpha = 0.28f) else Color.Transparent
    val foreground = if (isFocused) Color.White else Color.White.copy(alpha = 0.78f)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = foreground,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.18f),
            pressedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.dp, borderColor), shape = shape),
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.28f)),
                shape = shape,
            ),
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .then(extraModifier)
            .height(TvTopMenuLayout.primaryRowHeight)
            .defaultMinSize(minWidth = 48.dp)
            .padding(horizontal = 2.dp)
            .graphicsScale(scale),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            TvTopMenuAvatar(accountState = accountState, unreadCount = unreadCount)
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(9.dp),
            )
        }
    }
}

@Composable
private fun TvTopMenuAvatar(accountState: TvAccountState, unreadCount: Int) {
    val avatarText = remember(accountState.avatar, accountState.displayName) {
        profileAvatarDisplayText(accountState.avatar, accountState.displayName)
    }
    // The avatar circle plus a decorative unread badge anchored to its top-end
    // corner. The badge is purely informational — the profile Surface stays the
    // sole focus target, so the focus model is unchanged. Open the menu and pick
    // "Notifications" to reach the inbox.
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (accountState.avatarUrl != null) {
                ThumbhashImage(
                    url = accountState.avatarUrl,
                    thumbhash = null,
                    contentDescription = accountState.displayName,
                    modifier = Modifier.fillMaxHeight(),
                    contentScale = ContentScale.Crop,
                    transparent = true,
                )
            } else {
                Text(
                    text = avatarText,
                    color = ContinuumOnSurface,
                    fontWeight = FontWeight.Bold,
                    style = navRailLabel,
                )
            }
        }
        if (unreadCount > 0) {
            TvUnreadBadge(
                unreadCount = unreadCount,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

/** Decorative unread-count pill overlaid on the profile avatar; caps at "9+". */
@Composable
private fun TvUnreadBadge(unreadCount: Int, modifier: Modifier = Modifier) {
    val label = if (unreadCount > 9) "9+" else unreadCount.toString()
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 13.dp, minHeight = 13.dp)
            .clip(CircleShape)
            .background(Color(0xFFE53935))
            .border(1.dp, Color.Black.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = navRailLabel,
        )
    }
}

/**
 * Tiny `graphicsLayer` helper used so the focus-driven scale animation lives
 * outside the Surface's own scale handling (which we disable at
 * `focusedScale = 1f`). Splitting them keeps the animation continuous when
 * focus moves between adjacent buttons.
 */
private fun Modifier.graphicsScale(scale: Float): Modifier =
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
