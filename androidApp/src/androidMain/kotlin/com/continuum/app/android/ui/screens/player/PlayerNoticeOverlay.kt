package com.continuum.app.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.continuum.app.common.player.NoticeTone
import com.continuum.app.common.player.PlayerNotice

/**
 * Top-left transient toast surface for the phone player.
 *
 * Driven by [com.continuum.app.common.player.PlaybackSessionLifecycle.notice]. Visibility
 * tracks `notice != null`; when the underlying lifecycle clears the notice (recovery
 * succeeds or fails) the surface fades+slides out. The notice itself is not user-dismissable
 * — its lifetime is owned by the lifecycle.
 *
 * Anchoring is the parent's responsibility (see [PlayerOverlay]).
 */
@Composable
fun PlayerNoticeOverlay(
    notice: PlayerNotice?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn() + slideInHorizontally { -it / 2 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // Crossfade when transitioning between two non-null notices (e.g. Reconnecting → some
        // future Info banner) instead of replaying the slide-in. AnimatedVisibility above only
        // triggers on null↔non-null transitions, so this handles the in-place case.
        AnimatedContent(
            targetState = notice,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "PlayerNoticeContent",
        ) { current ->
            if (current != null) {
                NoticePill(notice = current)
            }
        }
    }
}

@Composable
private fun NoticePill(notice: PlayerNotice) {
    val backgroundColor = when (notice.tone) {
        NoticeTone.Info -> Color(0xFF1E40AF).copy(alpha = 0.92f)
        NoticeTone.Warning -> Color(0xFFB45309).copy(alpha = 0.92f)
    }
    val icon: ImageVector = when (notice.tone) {
        NoticeTone.Info -> Icons.Outlined.Info
        NoticeTone.Warning -> Icons.Outlined.Warning
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.widthIn(max = 360.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = notice.message,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
        }
    }
}
