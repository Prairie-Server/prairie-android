package com.continuum.app.tv.ui.shell

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.common.ui.components.isImageAvatar
import com.continuum.app.common.ui.components.profileAvatarDisplayText
import com.continuum.app.common.ui.components.resolveAvatarUrl
import com.continuum.app.tv.ui.theme.AccentLavender
import com.continuum.app.tv.ui.theme.AccentLavenderSoft
import com.continuum.app.tv.ui.theme.DrawerIconSurface
import com.continuum.app.tv.ui.theme.DrawerMenuSurface
import com.continuum.app.tv.ui.theme.DrawerOutline
import com.continuum.app.tv.ui.theme.DrawerSelectedBorder
import com.continuum.app.tv.ui.theme.DrawerSelectedSurface
import com.continuum.app.tv.ui.theme.DrawerSurface
import com.continuum.app.tv.ui.theme.FocusedContainer
import com.continuum.app.tv.ui.theme.FocusedContent
import com.continuum.app.tv.ui.theme.RailDimens
import com.continuum.app.tv.ui.theme.navRailLabel

data class DrawerNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val focusRequester: FocusRequester? = null,
)

data class TvDrawerAccountState(
    val displayName: String = "Account",
    val subtitle: String = "",
    val avatar: String? = null,
    val avatarUrl: String? = null,
    val isAdmin: Boolean = false,
)

private data class DrawerAccountAction(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
)

// Horizontal inset inside the drawer. Chosen so the icon slot (IconSlot = 44dp)
// is perfectly centered in the collapsed rail (72dp): 14dp + 44dp + 14dp = 72dp.
// This single constant keeps every icon's horizontal center fixed at drawer-x=36dp
// across the expand/collapse animation — they don't slide, only the text to the
// right fades in/out.
private val DrawerHorizontalPadding = 14.dp
private val IconSlot = 44.dp
private val DrawerVerticalPadding = 14.dp
private val DrawerSectionGap = 6.dp
private val DrawerItemHeight = 46.dp
private val DrawerAccountHeight = 50.dp
private val DrawerBrandHeight = 44.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvNavigationDrawer(
    currentRoute: String,
    searchSelected: Boolean,
    expanded: Boolean,
    accountState: TvDrawerAccountState,
    accountMenuOpen: Boolean,
    searchItem: DrawerNavItem,
    primaryItems: List<DrawerNavItem>,
    accountFocusRequester: FocusRequester,
    firstAccountActionFocusRequester: FocusRequester,
    onSearchSelected: () -> Unit,
    onAccountClick: () -> Unit,
    onRouteSelected: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    onSwitchServer: () -> Unit,
    onOpenAdmin: () -> Unit,
    onMoveFocusIntoContent: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accountActions = buildList {
        add(
            DrawerAccountAction(
                label = "Switch Profile",
                icon = Icons.Filled.Person,
                onClick = onSwitchProfile,
            ),
        )
        add(
            DrawerAccountAction(
                label = "Change Server",
                icon = Icons.Filled.Dns,
                onClick = onSwitchServer,
            ),
        )
        if (accountState.isAdmin) {
            add(
                DrawerAccountAction(
                    label = "Admin Dashboard",
                    icon = Icons.Filled.Security,
                    onClick = onOpenAdmin,
                ),
            )
        }
    }

    val drawerWidth by animateDpAsState(
        targetValue = if (expanded) RailDimens.ExpandedWidth else RailDimens.CollapsedWidth,
        label = "tvDrawerWidth",
    )
    // Derive contentWidth directly from the animating drawerWidth so expanding
    // widgets track the rail smoothly instead of snapping on the `expanded` flag.
    val contentWidth = (drawerWidth - DrawerHorizontalPadding * 2).coerceAtLeast(IconSlot)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(drawerWidth)
            .background(DrawerSurface)
            .border(width = 1.dp, color = DrawerOutline)
            .padding(horizontal = DrawerHorizontalPadding, vertical = DrawerVerticalPadding)
            .focusRestorer()
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onMoveFocusIntoContent()
                    true
                } else {
                    false
                }
            },
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(DrawerSectionGap),
    ) {
        DrawerBrand(expanded = expanded, width = contentWidth)

        DrawerSearchShortcut(
            item = searchItem,
            selected = searchSelected,
            expanded = expanded,
            width = contentWidth,
            onExpandRequested = { onExpandedChange(true) },
            onClick = onSearchSelected,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(DrawerSectionGap),
            modifier = Modifier.width(contentWidth),
        ) {
            primaryItems.forEach { item ->
                DrawerItem(
                    label = item.label,
                    icon = if (item.route == currentRoute) item.selectedIcon else item.icon,
                    selected = item.route == currentRoute,
                    expanded = expanded,
                    width = contentWidth,
                    focusRequester = item.focusRequester,
                    onExpandRequested = { onExpandedChange(true) },
                    onClick = { onRouteSelected(item.route) },
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .width(contentWidth),
        ) {
            if (expanded && accountMenuOpen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = DrawerAccountHeight + DrawerSectionGap),
                ) {
                    AccountMenu(
                        actions = accountActions,
                        width = contentWidth,
                        firstActionFocusRequester = firstAccountActionFocusRequester,
                    )
                }
            }

            AccountButton(
                accountState = accountState,
                expanded = expanded,
                width = contentWidth,
                focusRequester = accountFocusRequester,
                onExpandRequested = { onExpandedChange(true) },
                onClick = onAccountClick,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
fun rememberTvDrawerAccountState(
    userName: String?,
    role: String?,
    profileName: String?,
    avatar: String?,
    serverUrl: String,
): TvDrawerAccountState {
    val displayName = profileName?.takeIf(String::isNotBlank)
        ?: userName?.takeIf(String::isNotBlank)
        ?: "Account"
    val avatarUrl = remember(avatar, serverUrl) {
        avatar
            ?.takeIf(::isImageAvatar)
            ?.let { resolveAvatarUrl(serverUrl, it) }
    }
    val subtitle = remember(serverUrl, userName) {
        serverHost(serverUrl).ifBlank { userName.orEmpty() }
    }

    return TvDrawerAccountState(
        displayName = displayName,
        subtitle = subtitle,
        avatar = avatar,
        avatarUrl = avatarUrl,
        isAdmin = role.equals("admin", ignoreCase = true),
    )
}

@Composable
private fun DrawerBrand(
    expanded: Boolean,
    width: Dp,
) {
    Row(
        modifier = Modifier
            .width(width)
            .height(DrawerBrandHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(IconSlot),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(AccentLavender.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "A",
                    color = AccentLavender,
                    style = navRailLabel,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = "SILO",
                color = Color.White.copy(alpha = 0.92f),
                style = navRailLabel,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DrawerSearchShortcut(
    item: DrawerNavItem,
    selected: Boolean,
    expanded: Boolean,
    width: Dp,
    onExpandRequested: () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var hadFocus by remember { mutableStateOf(false) }
    val shape = CircleShape
    // The focusable circular surface stays a fixed size. A width=IconSlot Row
    // hosts it so its horizontal center lines up with every other drawer icon
    // (drawer-x = DrawerHorizontalPadding + IconSlot/2). Wrapping in a
    // width=`width` Row ensures focus-restoration lands on a consistent hit
    // target across rail widths.
    Row(
        modifier = Modifier.width(width),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(IconSlot),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = onClick,
                interactionSource = interactionSource,
                shape = ClickableSurfaceDefaults.shape(shape = shape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = when {
                        focused -> FocusedContainer
                        selected -> DrawerSelectedSurface
                        else -> DrawerIconSurface
                    },
                    contentColor = when {
                        focused -> FocusedContent
                        selected -> Color.White
                        else -> Color.White.copy(alpha = 0.82f)
                    },
                    focusedContainerColor = FocusedContainer,
                    focusedContentColor = FocusedContent,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border = ClickableSurfaceDefaults.border(
                    border = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) DrawerSelectedBorder else Color.Transparent,
                        ),
                        shape = shape,
                    ),
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(2.dp, AccentLavender),
                        shape = shape,
                    ),
                ),
                modifier = Modifier
                    .then(if (item.focusRequester != null) Modifier.focusRequester(item.focusRequester) else Modifier)
                    .onFocusChanged { state ->
                        val gainedFocus = state.hasFocus && !hadFocus
                        hadFocus = state.hasFocus
                        if (gainedFocus && !expanded) {
                            onExpandRequested()
                        }
                    }
                    .size(40.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AccountButton(
    accountState: TvDrawerAccountState,
    expanded: Boolean,
    width: Dp,
    focusRequester: FocusRequester,
    onExpandRequested: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var hadFocus by remember { mutableStateOf(false) }
    val contentColor = if (focused) FocusedContent else Color.White
    val avatarText = remember(accountState.avatar, accountState.displayName) {
        profileAvatarDisplayText(accountState.avatar, accountState.displayName)
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (focused) FocusedContainer else Color.Transparent,
            contentColor = contentColor,
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(18.dp),
            ),
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, AccentLavender),
                shape = RoundedCornerShape(18.dp),
            ),
        ),
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                val gainedFocus = state.hasFocus && !hadFocus
                hadFocus = state.hasFocus
                if (gainedFocus && !expanded) {
                    onExpandRequested()
                }
            }
            .width(width),
    ) {
        Row(
            modifier = Modifier.height(DrawerAccountHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier.size(IconSlot),
                contentAlignment = Alignment.Center,
            ) {
                AvatarBadge(
                    avatarUrl = accountState.avatarUrl,
                    displayName = accountState.displayName,
                    avatarText = avatarText,
                    contentColor = contentColor,
                )
            }

            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = accountState.displayName,
                        style = navRailLabel,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (accountState.subtitle.isNotBlank()) {
                        Text(
                            text = accountState.subtitle,
                            color = contentColor.copy(alpha = if (focused) 0.7f else 0.56f),
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarBadge(
    avatarUrl: String?,
    displayName: String,
    avatarText: String,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accountTint(displayName))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            ThumbhashImage(
                url = avatarUrl,
                thumbhash = null,
                contentDescription = displayName,
                modifier = Modifier.fillMaxHeight(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                transparent = true,
            )
        } else {
            Text(
                text = avatarText,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AccountMenu(
    actions: List<DrawerAccountAction>,
    width: Dp,
    firstActionFocusRequester: FocusRequester,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(width)
            .background(DrawerMenuSurface, shape)
            .border(1.dp, DrawerOutline, shape)
            .padding(10.dp)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEachIndexed { index, action ->
            DrawerActionItem(
                label = action.label,
                icon = action.icon,
                onClick = action.onClick,
                modifier = if (index == 0) {
                    Modifier.focusRequester(firstActionFocusRequester)
                } else {
                    Modifier
                },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DrawerActionItem(
    label: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White.copy(alpha = 0.86f),
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, AccentLavender),
                shape = RoundedCornerShape(14.dp),
            ),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .height(52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (icon != null) 12.dp else 0.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = label,
                style = navRailLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean,
    width: Dp,
    focusRequester: FocusRequester? = null,
    onExpandRequested: () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var hadFocus by remember { mutableStateOf(false) }
    val contentColor = when {
        focused -> FocusedContent
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.72f)
    }
    val shape = RoundedCornerShape(16.dp)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                focused -> FocusedContainer
                selected -> DrawerSelectedSurface
                else -> Color.Transparent
            },
            contentColor = contentColor,
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = if (selected) 1.dp else 0.dp,
                    color = if (selected) DrawerSelectedBorder else Color.Transparent,
                ),
                shape = shape,
            ),
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, AccentLavender),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = AccentLavenderSoft,
                elevation = 12.dp,
            ),
        ),
        modifier = Modifier
            .width(width)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { state ->
                val gainedFocus = state.hasFocus && !hadFocus
                hadFocus = state.hasFocus
                if (gainedFocus && !expanded) {
                    onExpandRequested()
                }
            },
    ) {
        Row(
            modifier = Modifier.height(DrawerItemHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier.size(IconSlot),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = label,
                    style = navRailLabel,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun serverHost(serverUrl: String): String {
    if (serverUrl.isBlank()) return ""
    return Uri.parse(serverUrl).host ?: serverUrl
}

private fun accountTint(seed: String): Color {
    val palette = listOf(
        Color(0xFF6B7C93),
        Color(0xFF607C6A),
        Color(0xFF6F668B),
        Color(0xFF577B83),
        Color(0xFF8A785E),
    )
    return palette[kotlin.math.abs(seed.hashCode()) % palette.size]
}
