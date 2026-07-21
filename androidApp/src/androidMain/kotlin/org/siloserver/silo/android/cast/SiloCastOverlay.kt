package org.siloserver.silo.android.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Full-screen overlay shown in place of the local video surface while a Google
 * Cast session is active. Compact "casting to <device>" panel with the live
 * remote position and play/pause + stop controls (driven from [SiloCastState]).
 */
@Composable
fun SiloCastOverlay(
    castState: SiloCastState,
    posterUrl: String?,
    onPlayPause: () -> Unit,
    onStopCasting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(160.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }

            Icon(
                imageVector = Icons.Default.CastConnected,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.White,
            )

            Text(
                text = "Casting to ${castState.deviceName ?: "device"}",
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            if (castState.title.isNotBlank()) {
                Text(
                    text = castState.title,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }

            if (castState.duration > 0.0) {
                LinearProgressIndicator(
                    progress = {
                        (castState.position / castState.duration).toFloat().coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
                Text(
                    text = "${formatTime(castState.position)} / ${formatTime(castState.duration)}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (castState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (castState.isPlaying) "Pause" else "Play",
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (castState.isPlaying) "Pause" else "Play")
                }

                OutlinedButton(onClick = onStopCasting) {
                    Icon(Icons.Default.Close, contentDescription = "Stop casting")
                    Spacer(Modifier.width(8.dp))
                    Text("Stop")
                }
            }
        }
    }
}

private fun formatTime(seconds: Double): String {
    if (!seconds.isFinite() || seconds < 0) return "0:00"
    val total = seconds.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
