package org.siloserver.silo.android.ui.screens.downloads

import org.siloserver.silo.android.ui.util.formatBytes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.siloserver.silo.android.ui.components.ProgressIndicator
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.download.DownloadQuality

/**
 * A single row in the downloads list.
 *
 * Shows poster, title, file size, download progress (if incomplete),
 * and a delete button.
 */
@Composable
fun DownloadItemRow(
    item: DownloadItem,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThumbhashImage(
            url = item.posterUrl,
            thumbhash = item.posterThumbhash,
            contentDescription = item.title,
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.small),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.subtitle != null) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                // "Quality • size" (e.g. "5 Mbps • 420 MB", "Original • 1.2 GB");
                // just the size when the row carries no quality (issue #20 GAP 5).
                text = downloadItemQualityLabel(item)
                    ?.let { "$it • ${formatBytes(item.fileSizeBytes)}" }
                    ?: formatBytes(item.fileSizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!item.isComplete && item.progress > 0f) {
                Spacer(modifier = Modifier.height(6.dp))
                ProgressIndicator(
                    progress = item.progress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Remove download",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Human label for a download's quality, preferring what the server actually
 * delivered (`effectiveQuality`) over what was requested. Null when the row
 * carries no quality (older sidecars) so the row falls back to size-only.
 */
private fun downloadItemQualityLabel(item: DownloadItem): String? {
    val wire = item.effectiveQuality?.takeIf { it.isNotBlank() }
        ?: item.quality?.takeIf { it.isNotBlank() }
        ?: return null
    return DownloadQuality.fromWire(wire).label
}

