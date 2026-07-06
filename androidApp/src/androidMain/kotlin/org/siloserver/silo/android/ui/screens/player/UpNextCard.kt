package org.siloserver.silo.android.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.siloserver.silo.common.ui.components.ThumbhashImage

/**
 * Compact bottom-end Up Next card for the phone player — the phone counterpart
 * of the TV's full-screen Up-Next overlay. Shows the next episode's still,
 * label, and runtime with a Play Now button (which carries the auto-play
 * countdown when active) and a dismiss affordance. The card body is not
 * clickable — the surrounding screen keeps receiving gestures.
 */
@Composable
fun UpNextCard(
    info: PlayerViewModel.NextEpisodeInfo,
    videoEnded: Boolean,
    countdownSeconds: Int?,
    onPlayNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.78f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.width(320.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .width(128.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    ThumbhashImage(
                        url = info.stillUrl,
                        thumbhash = info.stillThumbhash,
                        contentDescription = info.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (videoEnded) "PLAYING NEXT" else "UP NEXT",
                        color = Color.White.copy(alpha = 0.52f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = info.label,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (info.runtimeMinutes > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${info.runtimeMinutes} min",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onPlayNow,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = if (countdownSeconds != null) "Play Now · $countdownSeconds" else "Play Now",
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
