package org.prairieserver.prairie.tv.ui.screens.home

import org.prairieserver.prairie.model.section.ResolvedSection
import org.prairieserver.prairie.tv.ui.components.TvRowStyle
import org.prairieserver.prairie.tv.ui.components.isTvProgressRow
import org.prairieserver.prairie.tv.ui.util.isAudiobookMediaType
import org.prairieserver.prairie.tv.ui.util.visibleOnTv

internal fun List<ResolvedSection>.normalizeTvHomeSections(): List<ResolvedSection> =
    visibleOnTv().flatMap { section ->
        if (section.isTvProgressRow()) section.splitAudioProgress() else listOf(section)
    }

internal fun ResolvedSection.isTvAudioProgressSection(): Boolean =
    isTvProgressRow() && items.isNotEmpty() && items.all { isAudiobookMediaType(it.type) }

internal fun ResolvedSection.tvHomeRowStyle(): TvRowStyle = when {
    isTvAudioProgressSection() -> TvRowStyle.Poster
    isTvProgressRow() -> TvRowStyle.Backdrop
    else -> TvRowStyle.Poster
}

private fun ResolvedSection.splitAudioProgress(): List<ResolvedSection> {
    val audioItems = items.filter { isAudiobookMediaType(it.type) }
    if (audioItems.isEmpty()) return listOf(this)

    val nonAudioItems = items.filterNot { isAudiobookMediaType(it.type) }
    val listening = copy(
        id = "$id-continue-listening",
        sectionType = "continue_listening",
        title = "Continue Listening",
        totalCount = audioItems.size,
        items = audioItems,
    )

    return buildList {
        if (nonAudioItems.isNotEmpty()) {
            add(
                copy(
                    totalCount = nonAudioItems.size,
                    items = nonAudioItems,
                )
            )
        }
        add(listening)
    }
}
