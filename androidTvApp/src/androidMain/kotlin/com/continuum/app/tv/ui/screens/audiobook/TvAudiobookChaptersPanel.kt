package com.continuum.app.tv.ui.screens.audiobook

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.continuum.app.model.catalog.VersionChapter

/**
 * Full-screen, focusable chapters overlay. Auto-scrolls to + highlights
 * [currentChapterIndex]; Select jumps. Back is handled by the host screen
 * (closes the panel). Replaces the phone ChaptersSheet (spec §4.9).
 */
@Composable
fun TvAudiobookChaptersPanel(
    chapters: List<VersionChapter>,
    currentChapterIndex: Int,
    onSelectChapter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex in chapters.indices) {
            runCatching { listState.scrollToItem(currentChapterIndex) }
        }
    }
    TvAudiobookOverlayScaffold(title = "Chapters", modifier = modifier) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(chapters) { index, chapter ->
                TvAudiobookOverlayRow(
                    label = chapterDisplayLabel(index, chapter.title),
                    trailing = formatAudiobookTime(chapter.startSeconds),
                    isCurrent = index == currentChapterIndex,
                    onSelect = { onSelectChapter(index) },
                )
            }
        }
    }
}

/**
 * A usable chapter label. Many audiobooks ship useless chapter "titles" that are
 * just the track number ("1", "007") or blank; for those we show "Chapter N".
 * Genuine titles (e.g. "The Body in the Library") are kept as-is.
 */
private fun chapterDisplayLabel(index: Int, title: String): String {
    val trimmed = title.trim()
    return if (trimmed.isEmpty() || trimmed.all { it.isDigit() }) "Chapter ${index + 1}" else trimmed
}
