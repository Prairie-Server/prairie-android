package org.siloserver.silo.tv.ui.screens.player

import org.siloserver.silo.model.playback.SubtitleIdentity
import org.siloserver.silo.model.playback.PlayerSubtitleInfo
import org.siloserver.silo.playback.encodeSubtitleIdentityPreference

internal data class TvSubtitleHudOption(
    val stableId: String,
    val identity: SubtitleIdentity,
    val label: String,
)

internal data class TvSubtitleHudRow(
    val stableId: String,
    val identity: SubtitleIdentity,
    val label: String,
    val checked: Boolean,
    val applying: Boolean,
    val focused: Boolean,
) {
    val status: String?
        get() = if (applying) "Applying…" else null
}

internal data class TvSubtitleHudPresentation(
    val rows: List<TvSubtitleHudRow>,
    val hudOpen: Boolean,
    val focusedStableId: String?,
    val focusTrapActive: Boolean,
    val onSelect: (SubtitleIdentity) -> Unit = {},
    val onFocused: (String) -> Unit = {},
)

internal fun tvSubtitleOptionStableId(identity: SubtitleIdentity): String =
    encodeSubtitleIdentityPreference(identity)

internal fun buildTvSubtitleHudPresentation(
    options: List<TvSubtitleHudOption>,
    committedIdentity: SubtitleIdentity,
    pendingIdentity: SubtitleIdentity?,
    hudOpen: Boolean,
    focusedStableId: String?,
    onSelect: (SubtitleIdentity) -> Unit = {},
    onFocused: (String) -> Unit = {},
): TvSubtitleHudPresentation {
    val optionIds = options.mapTo(mutableSetOf(), TvSubtitleHudOption::stableId)
    val resolvedFocus = focusedStableId
        ?.takeIf(optionIds::contains)
        ?: options.firstOrNull { it.identity == committedIdentity }?.stableId
        ?: options.firstOrNull()?.stableId
    return TvSubtitleHudPresentation(
        rows = options.map { option ->
            TvSubtitleHudRow(
                stableId = option.stableId,
                identity = option.identity,
                label = option.label,
                checked = option.identity == committedIdentity,
                applying = option.identity == pendingIdentity &&
                    pendingIdentity != committedIdentity,
                focused = option.stableId == resolvedFocus,
            )
        },
        hudOpen = hudOpen,
        focusedStableId = resolvedFocus,
        focusTrapActive = hudOpen,
        onSelect = onSelect,
        onFocused = onFocused,
    )
}

internal fun buildTvSubtitleHudOptions(
    subtitleUrls: List<PlayerSubtitleInfo>,
    subtitleTracks: List<PlayerTrackEntry>,
): List<TvSubtitleHudOption> {
    val mountedTrackIndexes = resolvedMountedSubtitleTrackIndexes(subtitleTracks, subtitleUrls)
    val playerOnlyTracks = if (subtitleUrls.isEmpty()) {
        subtitleTracks
    } else {
        subtitleTracks.filterNot { it.index in mountedTrackIndexes }
    }
    return buildList {
        add(tvSubtitleHudOption(SubtitleIdentity.Off, "Off"))
        subtitleUrls.forEachIndexed { position, row ->
            add(tvSubtitleHudOption(tvSubtitleIdentity(row), subtitleChoiceLabel(row, position)))
        }
        playerOnlyTracks.forEachIndexed { position, track ->
            add(
                tvSubtitleHudOption(
                    tvSubtitleIdentity(track),
                    track.displayLabel.ifBlank { "Track ${position + 1}" },
                ),
            )
        }
    }.distinctBy(TvSubtitleHudOption::stableId)
}

private fun tvSubtitleHudOption(
    identity: SubtitleIdentity,
    label: String,
): TvSubtitleHudOption = TvSubtitleHudOption(
    stableId = tvSubtitleOptionStableId(identity),
    identity = identity,
    label = label,
)
