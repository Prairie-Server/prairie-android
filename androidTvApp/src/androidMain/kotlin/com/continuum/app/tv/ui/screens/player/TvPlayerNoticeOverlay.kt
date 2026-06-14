package com.continuum.app.tv.ui.screens.player

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
 * Top-start transient toast surface for the TV player. Larger paddings and
 * font than the phone variant so the message reads at TV viewing distance.
 *
 * Driven by [com.continuum.app.common.player.PlaybackSessionLifecycle.notice].
 * Visibility tracks `notice != null`; when the lifecycle clears the notice
 * (recovery succeeds or fails) the surface fades + slides out. The notice is
 * not user-dismissable — its lifetime is owned by the lifecycle.
 *
 * Anchoring is the parent's responsibility (typically a Box with top-start
 * alignment and 32dp inset).
 */
@Composable
fun TvPlayerNoticeOverlay(
    notice: PlayerNotice?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn() + slideInHorizontally { -it / 2 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // Crossfade when transitioning between two non-null notices (e.g.
        // Reconnecting → some future Info banner) instead of replaying the
        // slide-in. AnimatedVisibility above only triggers on null↔non-null
        // transitions, so this handles the in-place case.
        AnimatedContent(
            targetState = notice,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "TvPlayerNoticeContent",
        ) { current ->
            if (current != null) {
                TvNoticePill(notice = current)
            }
        }
    }
}

@Composable
private fun TvNoticePill(notice: PlayerNotice) {
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
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.widthIn(max = 480.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = notice.message,
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 24.sp,
            )
        }
    }
}
