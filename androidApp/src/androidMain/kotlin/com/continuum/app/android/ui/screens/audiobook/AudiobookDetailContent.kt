package com.continuum.app.android.ui.screens.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.audiobook.AudiobookNarration
import com.continuum.app.model.catalog.ItemDetail
import com.continuum.app.model.catalog.VersionChapter
import com.continuum.app.model.ebook.MediaRelatedContent
import com.continuum.app.model.ebook.MediaRelatedItem
import com.continuum.app.model.ebook.MediaSeriesGroup

/**
 * Phone audiobook detail. Cover + author + narrator above, then the
 * chapter list. Tapping the play button or any chapter opens the
 * dedicated audiobook player.
 *
 * Differs from MovieDetailContent in shape: square cover (not 2:3
 * poster), chapter list as the primary affordance (not "play" pill).
 */
@Composable
fun AudiobookDetailContent(
    detail: ItemDetail,
    isFavorite: Boolean,
    isInWatchlist: Boolean,
    selectedFileId: Int? = null,
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    onPlayClick: (fileId: Int?) -> Unit,
    onPlayFromStartClick: (fileId: Int?) -> Unit = {},
    onChapterClick: (VersionChapter) -> Unit,
    onFavoriteClick: () -> Unit,
    onWatchlistClick: () -> Unit,
    onDownloadClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val meta = detail.audiobook
    val authorText = meta?.authorNames
    val narratorText = meta?.narratorNames
    val playableVersion = selectedFileId
        ?.let { fileId -> detail.versions.firstOrNull { it.fileId == fileId } }
        ?: detail.versions.firstOrNull()
    val durationSeconds = meta?.totalDurationSeconds?.toDouble()
        ?: playableVersion?.duration
    // Server-recorded resume position (Continue Listening); drives the
    // "Resume · h:mm:ss" hero label when the listener is partway through.
    val resumeSeconds = detail.userData?.positionSeconds?.takeIf { it > 0 }
    val chapters = playableVersion?.chapters.orEmpty()
    val displayableNarrations = meta?.otherNarrations.orEmpty()
        .filter { it.title.isNotBlank() }
    val relatedLines = meta?.related?.displayLines().orEmpty()
    // Chapters start collapsed; the header toggles them open. Long audiobooks
    // (100+ chapters) shouldn't bury the rest of the detail under the list.
    var chaptersExpanded by remember { mutableStateOf(false) }
    // Clear the status bar / camera cutout so the cover isn't tucked under it.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 16.dp,
            top = 16.dp + topInset,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThumbhashImage(
                    url = detail.posterUrl,
                    thumbhash = detail.posterThumbhash,
                    contentDescription = detail.title,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    authorText?.takeIf { it.isNotBlank() }?.let { author ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "by $author",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    narratorText?.takeIf { it.isNotBlank() }?.let { narrator ->
                        Text(
                            text = "narrated by $narrator",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    durationSeconds?.let { dur ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formatDuration(dur),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Big play pill — mirrors the movie/series hero action.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (playableVersion != null && resumeSeconds != null) {
                    Button(
                        onClick = { onPlayClick(playableVersion.fileId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Resume · ${formatClock(resumeSeconds)}")
                    }
                    TextButton(
                        onClick = { onPlayFromStartClick(playableVersion.fileId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Play from beginning")
                    }
                } else {
                    Button(
                        onClick = { onPlayClick(playableVersion?.fileId) },
                        enabled = playableVersion != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (playableVersion == null) "Unavailable" else "Play")
                    }
                }
                OutlinedButton(
                    onClick = { onDownloadClick?.invoke() },
                    enabled = onDownloadClick != null && !isDownloaded,
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
                            onDownloadClick != null -> "Download"
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

        meta?.publisher?.takeIf { it.isNotBlank() }?.let { publisher ->
            item {
                AudiobookInfoLine(label = "Publisher", value = publisher)
            }
        }

        meta?.series?.takeIf { it.hasDisplayableContent() }?.let { series ->
            item {
                AudiobookSeriesSection(series = series)
            }
        }

        if (displayableNarrations.isNotEmpty()) {
            item {
                Text(
                    text = "Other Narrations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(displayableNarrations) { narration ->
                OtherNarrationRow(narration = narration)
            }
        }

        if (relatedLines.isNotEmpty()) {
            item {
                AudiobookRelatedSection(lines = relatedLines)
            }
        }

        if (chapters.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { chaptersExpanded = !chaptersExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Chapters (${chapters.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (chaptersExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (chaptersExpanded) "Collapse chapters" else "Expand chapters",
                    )
                }
            }
            if (chaptersExpanded) {
                items(chapters) { chapter ->
                    ChapterRow(chapter = chapter, onClick = { onChapterClick(chapter) })
                }
            }
        }
    }
}

@Composable
private fun AudiobookInfoLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AudiobookSeriesSection(series: MediaSeriesGroup) {
    val title = series.name.takeIf { it.isNotBlank() }
    val entryLines = series.entries
        .mapNotNull { entry ->
            listOfNotNull(
                entry.seriesIndex?.let { "Book ${formatSeriesIndex(it)}" },
                entry.title.takeIf { it.isNotBlank() },
            ).joinToString(" · ").takeIf { it.isNotBlank() }
        }
        .take(3)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Series",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        entryLines.forEach { entryLine ->
            Text(
                text = entryLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OtherNarrationRow(narration: AudiobookNarration) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = narration.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val subtitle = listOfNotNull(
            narration.year?.toString(),
            narration.narrators
                .filter { it.isNotBlank() }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", "),
        ).joinToString(" · ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AudiobookRelatedSection(lines: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Related",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun MediaRelatedContent.displayLines(): List<String> = listOfNotNull(
    alsoByAuthor.joinToRelatedTitles()
        .takeIf { it.isNotBlank() }
        ?.let { "Also by author: $it" },
    similar.joinToRelatedTitles()
        .takeIf { it.isNotBlank() }
        ?.let { "Similar: $it" },
)

private fun List<MediaRelatedItem>.joinToRelatedTitles(): String =
    mapNotNull { it.title.takeIf { title -> title.isNotBlank() } }
        .take(3)
        .joinToString(", ")

private fun MediaSeriesGroup.hasDisplayableContent(): Boolean =
    name.isNotBlank() || entries.any { it.title.isNotBlank() || it.seriesIndex != null }

private fun formatSeriesIndex(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

@Composable
private fun ChapterRow(chapter: VersionChapter, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${chapter.index + 1}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatDuration(chapter.endSeconds - chapter.startSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

/** Clock-style position label (h:mm:ss / m:ss) for the Resume button. */
private fun formatClock(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
