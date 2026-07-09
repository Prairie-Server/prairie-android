package org.siloserver.silo.android.ui.screens.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared behavior for the player's modal bottom sheets.
 *
 * [playerSheetMaxHeight] caps sheet content so the sheet (and its drag
 * handle) never reaches the top edge of the screen. The player runs
 * immersive (system bars hidden), so Material 3 would otherwise let a tall
 * sheet grow flush with the display edge — where dragging the handle down
 * opens the system notification shade instead of moving the sheet.
 *
 * [PlayerSheetFlingGuard] sits between a sheet's scrollable content and the
 * sheet's own nested-scroll connection. Material 3 hands the fling velocity
 * left over after content scrolling to the sheet, so a downward fling that
 * runs into the top of the content hurls the sheet to Hidden — which is why
 * scrolling player menus back up kept dismissing them. Consuming the
 * post-fling velocity keeps flings inside the content; the sheet still
 * dismisses from a real drag (on the handle, or on content already at the
 * top) because drag deltas pass through untouched.
 */
@Composable
internal fun playerSheetMaxHeight(): Dp = (LocalConfiguration.current.screenHeightDp * 0.8f).dp

internal val PlayerSheetFlingGuard = object : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/**
 * Header row for the glass-style sheets. When [onBack] is provided (gear
 * submenus), a leading chevron returns to the parent settings sheet — QA:
 * submenus previously had no way back.
 */
@Composable
internal fun PlayerSheetHeader(
    title: String,
    onBack: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(
            start = if (onBack != null) 8.dp else 20.dp,
            end = 20.dp,
            top = if (onBack != null) 12.dp else 20.dp,
            bottom = 8.dp,
        ),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back to playback settings",
                    tint = Color.White,
                )
            }
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
