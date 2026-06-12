package com.continuum.app.android.ui.screens.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.book.BookFormat
import com.continuum.app.model.catalog.ItemDetail
import com.continuum.app.model.ebook.bookFormatFromEbookVersion
import com.continuum.app.model.ebook.ebookFormatDisplayName
import com.continuum.app.model.ebook.ebookFormatSupport
import com.continuum.app.model.ebook.isSupportedEbookVersion

/**
 * Phone book detail. Tall cover on the left (2:3 like a movie poster
 * since print covers share that ratio), author + format + page count
 * on the right, then a Read button + overview.
 */
@Composable
fun BookDetailContent(
    detail: ItemDetail,
    isFavorite: Boolean,
    isInWatchlist: Boolean,
    selectedVersionIndex: Int,
    onVersionSelected: (Int) -> Unit,
    canReadSelectedVersion: Boolean,
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    onReadClick: (fileId: Int?) -> Unit,
    onFavoriteClick: () -> Unit,
    onWatchlistClick: () -> Unit,
    onDownloadClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val meta = detail.book
    val ebook = detail.ebook
    val selectedVersion = detail.versions.getOrNull(selectedVersionIndex)
    val canDownloadSelectedVersion = selectedVersion?.isSupportedEbookVersion() == true && onDownloadClick != null
    val selectedFormatSupport = selectedVersion?.ebookFormatSupport()
    val format = selectedVersion?.bookFormatFromEbookVersion()
        ?: meta?.formatEnum()
        ?: BookFormat.Unknown
    val author = ebook?.authorNames ?: meta?.author
    val publisher = ebook?.publisher ?: meta?.publisher
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                ThumbhashImage(
                    url = detail.posterUrl,
                    thumbhash = detail.posterThumbhash,
                    contentDescription = detail.title,
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    author?.takeIf { it.isNotBlank() }?.let { authorName ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("by $authorName", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FormatBadge(format)
                        meta?.pageCount?.let { Text("· $it pages", style = MaterialTheme.typography.labelMedium) }
                    }
                    publisher?.takeIf { it.isNotBlank() }?.let { publisherName ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = publisherName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ebook?.series?.name?.takeIf { it.isNotBlank() }?.let { seriesName ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Series: $seriesName",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { selectedVersion?.let { onReadClick(it.fileId) } },
                    enabled = selectedVersion != null && canReadSelectedVersion,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        when {
                            selectedVersion == null -> "Unavailable"
                            canReadSelectedVersion -> "Read"
                            else -> "Open from Downloads"
                        },
                    )
                }
                OutlinedButton(
                    onClick = { onDownloadClick?.invoke() },
                    enabled = canDownloadSelectedVersion && !isDownloaded,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Filled.Check else Icons.Filled.Download,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        when {
                            isDownloaded -> "Downloaded"
                            downloadProgress != null -> "Cancel Download"
                            canDownloadSelectedVersion -> "Download"
                            else -> "Download Unavailable"
                        },
                    )
                }
                downloadProgress?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (selectedVersion != null && !canReadSelectedVersion) {
                    Text(
                        text = selectedFormatSupport?.reason
                            ?: "This format can be downloaded in its original file format and opened from Downloads or another reader.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (detail.versions.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Versions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    detail.versions.forEachIndexed { index, version ->
                        val label = version.ebookFormatDisplayName()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (index == selectedVersionIndex) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                )
                                .clickable { onVersionSelected(index) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "file ${version.fileId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        detail.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            item {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ebook?.related?.alsoByAuthor
            ?.filter { it.contentId.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { related ->
            item {
                Text(
                    text = "Also by this author",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(related, key = { it.contentId }) { item ->
                Text(
                    text = listOfNotNull(item.title, item.year?.toString()).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun FormatBadge(format: BookFormat) {
    Text(
        text = format.displayName,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
