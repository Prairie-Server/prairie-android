package com.continuum.app.tv.ui.screens.audiobook

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.continuum.app.audiobook.audiobookChapterLabel
import com.continuum.app.model.catalog.VersionChapter
import kotlinx.coroutines.delay

/**
 * Full-screen, focusable chapters overlay. Auto-scrolls to + highlights the
 * current chapter, and grabs focus on it so the D-pad can select immediately;
 * Select jumps. Back is handled by the host screen. Replaces the phone
 * ChaptersSheet (spec §4.9).
 */
@Composable
fun TvAudiobookChaptersPanel(
    chapters: List<VersionChapter>,
    currentChapterIndex: Int,
    onSelectChapter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusIndex = currentChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))

    LaunchedEffect(Unit) {
        if (focusIndex in chapters.indices) {
            runCatching { listState.scrollToItem(focusIndex) }
        }
        // Let the scrolled row compose/lay out before grabbing focus.
        delay(100)
        runCatching { focusRequester.requestFocus() }
    }

    TvAudiobookOverlayScaffold(title = "Chapters", modifier = modifier) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(chapters) { index, chapter ->
                TvAudiobookOverlayRow(
                    label = audiobookChapterLabel(index, chapter.title),
                    trailing = formatAudiobookTime(chapter.startSeconds),
                    isCurrent = index == currentChapterIndex,
                    focusRequester = if (index == focusIndex) focusRequester else null,
                    onSelect = { onSelectChapter(index) },
                )
            }
        }
    }
}
