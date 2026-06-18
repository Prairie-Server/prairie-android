package com.continuum.app.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.continuum.app.model.section.SectionItem
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Display payload for the focus marquee — built from section-item models only
 * (no per-item detail fetch). Mirrors tvOS `TVMarqueeContent`: whatever
 * synopsis/badge/runtime fields the section payload already carries render, the
 * rest are omitted.
 *
 * @property id crossfade identity; includes the source row so the same item
 *  focused from a different row still reads as a swap.
 */
data class TvMarqueeContent(
    val id: String,
    val title: String,
    val logoUrl: String?,
    /** Codec/HDR + content-rating chips (`4K`, `DOLBY VISION`, `ATMOS`). */
    val badges: List<String>,
    /** Dot-joined meta tokens after the badges: year · genre · runtime, or
     *  `S2 E7 · episode title · 45 min · 23m left` for episodes. */
    val metaParts: List<String>,
    val synopsis: String?,
    /** A quieter detail line: cast / air-date when carried by the payload. */
    val detailLine: String?,
    val backdropUrl: String?,
    val backdropThumbhash: String?,
    val posterUrl: String?,
    val posterThumbhash: String?,
    val isEpisode: Boolean,
    /** The source item, retained so the ambient tint extracts its palette from
     *  the same (debounced) card the marquee + backdrop show. */
    val source: SectionItem,
) {
    /** Backdrop art for the root hero — episodes prefer a series backdrop over
     *  their low-res still, falling back to whatever they carry. */
    val heroBackdropUrl: String? get() = backdropUrl ?: posterUrl
    val heroBackdropThumbhash: String? get() = backdropThumbhash ?: posterThumbhash

    companion object {
        fun from(item: SectionItem, rowTitle: String): TvMarqueeContent {
            val isEpisode = item.type.equals("episode", ignoreCase = true)

            val meta = mutableListOf<String>()
            if (isEpisode) {
                episodeToken(item.seasonNumber, item.episodeNumber)?.let(meta::add)
                if (item.title.isNotBlank()) meta.add(item.title)
                lengthText(item.durationSeconds)?.let(meta::add)
                timeLeftText(item.positionSeconds, item.durationSeconds)?.let(meta::add)
            } else {
                if (item.year > 0) meta.add(item.year.toString())
                item.genres.firstOrNull { it.isNotBlank() }?.let(meta::add)
                lengthText(item.durationSeconds)?.let(meta::add)
                item.ratingImdb?.let { meta.add(formatRating(it)) }
            }

            val badges = mutableListOf<String>()
            // Section payloads on Android don't carry an overlay-summary codec
            // line; content-rating lives in `status` on some servers. Keep the
            // badge derivation conservative — surface what is present.
            item.status?.takeIf { it.isNotBlank() }?.let { badges.add(it.uppercase()) }

            return TvMarqueeContent(
                id = "$rowTitle#${item.contentId}",
                title = if (isEpisode) (item.seriesTitle ?: item.title) else item.title,
                logoUrl = item.logoUrl?.takeIf { it.isNotBlank() },
                badges = badges,
                metaParts = meta,
                synopsis = item.overview?.takeIf { it.isNotBlank() },
                detailLine = null,
                backdropUrl = item.backdropUrl?.takeIf { it.isNotBlank() },
                backdropThumbhash = item.backdropThumbhash,
                posterUrl = item.posterUrl?.takeIf { it.isNotBlank() },
                posterThumbhash = item.posterThumbhash,
                isEpisode = isEpisode,
                source = item,
            )
        }

        private fun episodeToken(season: Int?, episode: Int?): String? = when {
            season != null && episode != null -> "S$season E$episode"
            season != null -> "Season $season"
            episode != null -> "Episode $episode"
            else -> null
        }

        private fun timeLeftText(position: Double?, duration: Double?): String? {
            if (position == null || duration == null || duration <= 0) return null
            if (position <= 60 || position / duration >= 0.95) return null
            val remaining = (((duration - position) / 60.0)).let { kotlin.math.ceil(it).toInt() }.coerceAtLeast(1)
            return "${remaining}m left"
        }

        private fun lengthText(durationSeconds: Double?): String? {
            if (durationSeconds == null || durationSeconds <= 0) return null
            val minutes = (durationSeconds / 60.0).roundToInt()
            if (minutes <= 0) return null
            return if (minutes >= 60) {
                val hours = minutes / 60
                val rest = minutes % 60
                if (rest == 0) "${hours}h" else "${hours}h ${rest}m"
            } else {
                "$minutes min"
            }
        }

        private fun formatRating(rating: Double): String {
            val rounded = (rating * 10).roundToInt() / 10.0
            return rounded.toString()
        }
    }
}

/**
 * Focused-card → marquee state for the Skyline Home. Row cards report focus
 * immediately via [preview]; the displayed [content] (and therefore the backdrop
 * + tint) only swaps after focus has rested ~150 ms, so scrubbing across a row
 * never flashes intermediate backdrops. Focus is reported only on gain — rows
 * never report loss — so while focus is in chrome the last previewed item is
 * retained.
 */
class TvFocusMarqueeState internal constructor() {
    var content: TvMarqueeContent? by mutableStateOf(null)
        private set

    internal var candidate: TvMarqueeContent? by mutableStateOf(null)

    /** Report card focus. The displayed content swaps after the rest debounce. */
    fun preview(item: SectionItem, rowTitle: String) {
        val next = TvMarqueeContent.from(item, rowTitle)
        // Focus is back on the already-displayed card: cancel any pending swap
        // so a brief A→B→A scrub within the debounce window can't commit a
        // stale B after focus has returned to A.
        if (next == content) {
            candidate = null
            return
        }
        candidate = next
    }

    internal fun commit(value: TvMarqueeContent?) {
        content = value
    }
}

/** Focus-rest debounce before the marquee + backdrop swap (tvOS §4.2). */
const val TvMarqueeRestDebounceMs = 150L

/** Marquee text + backdrop crossfade duration in ms (tvOS §4.2: 240 ms). */
const val TvMarqueeCrossfadeMs = 240

@Composable
fun rememberTvFocusMarqueeState(): TvFocusMarqueeState {
    val state = remember { TvFocusMarqueeState() }
    LaunchedEffect(state.candidate?.id) {
        val candidate = state.candidate ?: return@LaunchedEffect
        delay(TvMarqueeRestDebounceMs)
        state.commit(candidate)
    }
    return state
}
