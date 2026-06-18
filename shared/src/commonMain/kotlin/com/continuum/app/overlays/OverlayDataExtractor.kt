package com.continuum.app.overlays

import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.catalog.EpisodeFile
import com.continuum.app.model.catalog.EpisodeListItem
import com.continuum.app.model.catalog.ItemDetail
import com.continuum.app.model.catalog.OverlaySummary
import com.continuum.app.model.section.SectionItem

/**
 * Builds an [OverlayData] bag from the catalog models Android actually
 * has. Fields the Android API doesn't surface are left `null` so the
 * registry's `getValue` returns `null` and those badges hide — exactly
 * like Apple's `getValue == nil` behavior.
 *
 * As more data lands on the server response (ribbon sources, languages),
 * fill the corresponding fields here and the badges light up with no
 * renderer change.
 */
object OverlayDataExtractor {

    /**
     * Map the full server [OverlaySummary] (best-ranked file's tech details)
     * onto [OverlayData]. Mirrors Apple's `applySummary` in
     * `OverlayExtractors.swift` — every summary field is carried, not just
     * resolution/audio/release_type, so badges like HDR, video codec,
     * container, aspect ratio, edition, multi-audio and subtitles render.
     */
    fun fromSummary(summary: OverlaySummary?): OverlayData {
        if (summary == null) return OverlayData()
        return OverlayData(
            resolution = summary.resolution,
            hdr = summary.hdr,
            audio = summary.audio,
            audioChannels = summary.audioChannels,
            videoCodec = summary.videoCodec,
            container = summary.container,
            aspectRatio = summary.aspectRatio,
            releaseType = summary.releaseType,
            edition = summary.edition,
            multiAudio = summary.multiAudio,
            multiSub = summary.multiSub,
        )
    }

    /**
     * From a grid [BrowseItem]: the full tech subset from
     * [BrowseItem.overlaySummary] plus the ratings/metadata the browse payload
     * carries (year, content rating, IMDb rating).
     */
    fun fromBrowseItem(item: BrowseItem): OverlayData {
        return fromSummary(item.overlaySummary).copy(
            ratingImdb = item.ratingImdb,
            contentRating = item.contentRating,
            year = item.year.takeIf { it > 0 },
        )
    }

    /**
     * From a home/section [SectionItem]: the full tech subset from
     * [SectionItem.overlaySummary] plus the ratings/metadata the section
     * payload carries. Routed through [fromSummary] so section rows render the
     * same default-enabled tech badges (resolution / audio / release type) as
     * the grid path instead of a hand-rolled IMDb/year-only [OverlayData].
     */
    fun fromSectionItem(item: SectionItem): OverlayData {
        return fromSummary(item.overlaySummary).copy(
            ratingImdb = item.ratingImdb,
            contentRating = item.contentRating,
            year = item.year.takeIf { it > 0 },
        )
    }

    /**
     * From a full [ItemDetail]: ratings, content rating, year, runtime,
     * and the primary studio/network. Tech fields (resolution, audio,
     * release type) are not derived here — the detail payload exposes
     * per-version files, and selecting the "best" version is the caller's
     * job; pass an [OverlaySummary] via [merge] when one is available.
     */
    fun fromItemDetail(detail: ItemDetail): OverlayData {
        return OverlayData(
            ratingImdb = detail.ratingImdb,
            ratingTmdb = detail.ratingTmdb,
            ratingRtCritic = detail.ratingRtCritic,
            ratingRtAudience = detail.ratingRtAudience,
            contentRating = detail.contentRating,
            year = detail.year.takeIf { it > 0 },
            runtime = detail.runtime.takeIf { it > 0 },
            studio = detail.studios.firstOrNull(),
            network = detail.networks.firstOrNull(),
        )
    }

    /**
     * From an [EpisodeListItem]: runtime plus the per-file tech subset. Prefers
     * the user's last-played file fields (resolution / codec / HDR) when
     * present, otherwise falls back to the episode's *best-ranked* file —
     * highest resolution — matching the server's `bestFile` selection in
     * `internal/overlays/summary.go` (and Apple, which reads the summary the
     * server builds from that same file). Picking the best file rather than
     * blindly `files.first()` keeps the badge consistent with what the server
     * would have published for the movie/grid path.
     *
     * Channel counts are formatted to the same canonical labels the server
     * emits in `OverlaySummary.audioChannels` (2→"Stereo", 6→"5.1", 8→"7.1",
     * …) so the per-file Android path and the summary-driven path agree.
     */
    fun fromEpisode(episode: EpisodeListItem): OverlayData {
        val file = bestFile(episode.files)
        val userData = episode.userData
        val resolution = userData?.lastResolution ?: file?.resolution
        val videoCodec = userData?.lastCodecVideo ?: file?.codecVideo
        val hdrFlag = userData?.lastHdr ?: file?.hdr
        return OverlayData(
            resolution = resolution,
            videoCodec = videoCodec,
            hdr = if (hdrFlag == true) "HDR" else null,
            audioChannels = audioChannelsLabel(file?.audioChannels ?: 0),
            container = file?.container,
            runtime = episode.runtime.takeIf { it > 0 },
        )
    }

    /**
     * Overlay the full tech subset from [summary] onto an existing [base]
     * (e.g. one built from [fromItemDetail]). Non-null summary fields win;
     * everything else on [base] is preserved.
     */
    fun merge(base: OverlayData, summary: OverlaySummary?): OverlayData {
        if (summary == null) return base
        return base.copy(
            resolution = summary.resolution ?: base.resolution,
            hdr = summary.hdr ?: base.hdr,
            audio = summary.audio ?: base.audio,
            audioChannels = summary.audioChannels ?: base.audioChannels,
            videoCodec = summary.videoCodec ?: base.videoCodec,
            container = summary.container ?: base.container,
            aspectRatio = summary.aspectRatio ?: base.aspectRatio,
            releaseType = summary.releaseType ?: base.releaseType,
            edition = summary.edition ?: base.edition,
            multiAudio = summary.multiAudio ?: base.multiAudio,
            multiSub = summary.multiSub ?: base.multiSub,
        )
    }

    /**
     * Pick the highest-resolution file, mirroring the server's `bestFile`
     * (`resolutionRank` on the normalized resolution). Ties keep the earlier
     * file, matching the server's strict `>` comparison.
     */
    private fun bestFile(files: List<EpisodeFile>): EpisodeFile? {
        var best: EpisodeFile? = null
        var bestRank = -1
        for (file in files) {
            val rank = resolutionRank(file.resolution)
            if (best == null || rank > bestRank) {
                best = file
                bestRank = rank
            }
        }
        return best
    }

    /**
     * Numeric rank of a resolution string for best-file selection. Mirrors the
     * server's `resolutionRank`: "4k"/"uhd" normalize to 2160p, "1080p" → 1080,
     * unknown → 0.
     */
    private fun resolutionRank(value: String?): Int {
        val cleaned = value?.trim()?.lowercase().orEmpty()
        val normalized = when (cleaned) {
            "" -> return 0
            "4k", "uhd" -> "2160p"
            else -> cleaned
        }
        val numeric = normalized.removeSuffix("p")
        return if (numeric != normalized) numeric.toIntOrNull() ?: 0 else 0
    }

    /**
     * Canonical channel-count label, byte-for-byte matching the server's
     * `audioChannelsLabel` in `internal/overlays/summary.go` so the per-file
     * Android episode path produces the same string the server publishes in
     * the summary for other surfaces.
     */
    private fun audioChannelsLabel(channels: Int): String? = when {
        channels <= 0 -> null
        channels == 1 -> "Mono"
        channels == 2 -> "Stereo"
        channels == 6 -> "5.1"
        channels == 7 -> "6.1"
        channels == 8 -> "7.1"
        else -> "${channels}ch"
    }
}
