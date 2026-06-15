package com.continuum.app.common.player

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.continuum.app.model.playback.PlayerSubtitleInfo
import com.continuum.app.model.settings.SubtitleAppearance
import com.continuum.app.model.settings.SubtitleBackgroundStylePreset
import com.continuum.app.model.settings.SubtitleFontSizePreset
import com.continuum.app.model.settings.SubtitlePositionPreset

/**
 * Manages subtitle track configuration for the ExoPlayer instance.
 *
 * External subtitles come from the server as URLs that need authentication.
 * This manager builds subtitle configurations and applies track selection.
 */
@UnstableApi
class SubtitleManager {

    /**
     * Builds MediaItem.SubtitleConfiguration entries for external subtitle tracks.
     *
     * @param subtitles The subtitle info list from the playback session
     * @param serverUrl The base server URL for resolving relative subtitle URLs
     * @return List of subtitle configurations to add to the MediaItem
     */
    fun buildSubtitleConfigurations(
        subtitles: List<PlayerSubtitleInfo>,
        serverUrl: String,
    ): List<MediaItem.SubtitleConfiguration> {
        return subtitles.mapNotNull { subtitle ->
            val absoluteUrl = resolveSubtitleUrl(serverUrl, subtitle.url)
            val mimeType = subtitleMimeType(subtitle.codec, absoluteUrl)
            if (!isMedia3TextSidecarMimeType(mimeType)) {
                return@mapNotNull null
            }

            MediaItem.SubtitleConfiguration.Builder(Uri.parse(absoluteUrl))
                .setMimeType(mimeType)
                .setLanguage(subtitle.language)
                .setLabel(subtitle.label ?: subtitle.language ?: "Track ${subtitle.index}")
                .setSelectionFlags(
                    if (subtitle.forced == true) C.SELECTION_FLAG_FORCED else 0
                )
                .build()
        }
    }

    /**
     * Selects or disables subtitles on the player.
     *
     * Widened from `ExoPlayer` to `Player` so callers holding a `MediaController`
     * can invoke it — `currentTracks` and `trackSelectionParameters` are both
     * on `Player` and that's all this method touches.
     *
     * @param player The player instance (ExoPlayer or MediaController)
     * @param subtitleIndex The subtitle track index to select, or -1 to disable subtitles
     */
    fun selectSubtitle(player: Player, subtitleIndex: Int): Boolean {
        if (subtitleIndex < 0) {
            disableSubtitles(player)
            return true
        } else {
            val selection = resolveSubtitleSelection(player.currentTracks, subtitleIndex)
            if (selection == null) {
                Log.w(
                    TAG,
                    "selectSubtitle failed: index=$subtitleIndex not found " +
                        "tracks=${player.currentTracks.describeTextTracks()}",
                )
                return false
            }
            applySubtitleSelection(player, selection)
            return true
        }
    }

    /**
     * Selects a subtitle from the app/server subtitle list. Mobile renders
     * [PlayerSubtitleInfo] rows, while Media3 can expose embedded text tracks
     * before sidecar tracks; selecting by raw ordinal would then choose the
     * wrong language. Prefer metadata matching and fall back to the old flat
     * ordinal for callers whose tracks do not expose labels yet.
     */
    fun selectSubtitle(
        player: Player,
        subtitles: List<PlayerSubtitleInfo>,
        subtitleIndex: Int,
    ): Boolean {
        if (subtitleIndex < 0) {
            disableSubtitles(player)
            return true
        }

        val subtitle = subtitles.getOrNull(subtitleIndex)
        if (subtitle == null) {
            Log.w(TAG, "selectSubtitle failed: app index=$subtitleIndex outside subtitles=${subtitles.size}")
            return false
        }

        val selection = resolveSubtitleSelection(player.currentTracks, subtitle)
            ?: resolveSubtitleSelection(player.currentTracks, subtitleIndex)
        if (selection == null) {
            Log.w(
                TAG,
                "selectSubtitle failed: app index=$subtitleIndex metadata=${subtitle.label ?: subtitle.language} " +
                    "tracks=${player.currentTracks.describeTextTracks()}",
            )
            return false
        }

        applySubtitleSelection(player, selection)
        return true
    }

    /**
     * Applies the user's [SubtitleAppearance] to the [PlayerView]'s subtitle layer.
     *
     * Maps onto Media3 via [CaptionStyleCompat] (colors + edge style + typeface),
     * [androidx.media3.ui.SubtitleView.setFractionalTextSize] (relative-to-view-height
     * font scale), and [androidx.media3.ui.SubtitleView.setBottomPaddingFraction]
     * (vertical position within the surface).
     *
     * Embedded WebVTT/ASS styling is disabled so user preferences win uniformly
     * across track formats. **Caveat:** image-based subtitles (PGS, DVD) are
     * pre-rendered bitmaps and ignore CaptionStyleCompat — they will display
     * with their authored appearance regardless of these settings.
     */
    fun applyAppearance(playerView: PlayerView, appearance: SubtitleAppearance) {
        val subtitleView = playerView.subtitleView ?: return
        val safe = appearance.sanitized()

        val captionStyle = try {
            buildCaptionStyle(safe)
        } catch (_: NumberFormatException) {
            // Defense-in-depth: fall back to default white-on-transparent.
            CaptionStyleCompat(
                Color.WHITE,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_NONE,
                Color.BLACK,
                Typeface.SANS_SERIF,
            )
        }

        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setApplyEmbeddedFontSizes(false)
        subtitleView.setStyle(captionStyle)
        subtitleView.setFractionalTextSize(
            fractionalSizeFor(safe.fontSize),
            /* fractionalRelativeToTextSize = */ false,
        )
        subtitleView.setBottomPaddingFraction(bottomPaddingFor(safe.position))
    }

    private fun buildCaptionStyle(appearance: SubtitleAppearance): CaptionStyleCompat {
        val foreground = parseHexColor(appearance.fontColor)
        val backgroundAlpha = if (appearance.backgroundStyle == SubtitleBackgroundStylePreset.None) {
            0
        } else {
            (appearance.backgroundOpacity.coerceIn(0, 100) * 255 / 100)
        }
        val background = parseHexColor(appearance.backgroundColor, backgroundAlpha)
        val edgeType = when {
            appearance.textOutline -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            appearance.backgroundStyle == SubtitleBackgroundStylePreset.Outline ->
                CaptionStyleCompat.EDGE_TYPE_OUTLINE
            appearance.backgroundStyle == SubtitleBackgroundStylePreset.Shadow ->
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            else -> CaptionStyleCompat.EDGE_TYPE_NONE
        }
        val edgeColor = parseHexColor(appearance.textOutlineColor)
        val typeface = typefaceFor(appearance.fontFamily)
        return CaptionStyleCompat(
            foreground,
            background,
            Color.TRANSPARENT,
            edgeType,
            edgeColor,
            typeface,
        )
    }

    private fun typefaceFor(family: String): Typeface {
        return when (family) {
            SubtitleAppearance.SANS_SERIF -> Typeface.SANS_SERIF
            SubtitleAppearance.SERIF -> Typeface.SERIF
            SubtitleAppearance.MONOSPACE -> Typeface.MONOSPACE
            else -> Typeface.create(family, Typeface.NORMAL)
        }
    }

    private fun fractionalSizeFor(preset: SubtitleFontSizePreset): Float {
        return when (preset) {
            SubtitleFontSizePreset.Small -> 0.032f
            SubtitleFontSizePreset.Medium -> 0.040f
            SubtitleFontSizePreset.Large -> 0.050f
            SubtitleFontSizePreset.XLarge -> 0.060f
            SubtitleFontSizePreset.XXLarge -> 0.072f
        }
    }

    private fun bottomPaddingFor(position: SubtitlePositionPreset): Float {
        return when (position) {
            SubtitlePositionPreset.Bottom -> 0.06f
            SubtitlePositionPreset.LowerThird -> 0.18f
            SubtitlePositionPreset.Top -> 0.74f
        }
    }

    private fun parseHexColor(hex: String, alpha: Int = 255): Int {
        val cleaned = if (hex.startsWith("#")) hex.drop(1) else hex
        val rgb = cleaned.toLong(16).toInt()
        return ((alpha and 0xFF) shl 24) or (rgb and 0x00FFFFFF)
    }

    private fun subtitleMimeType(codec: String?, url: String): String =
        mimeTypeFromUrl(url) ?: codecToMimeType(codec)

    private fun mimeTypeFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".vtt") || path.endsWith(".webvtt") -> MimeTypes.TEXT_VTT
            path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            path.endsWith(".sup") -> MimeTypes.APPLICATION_PGS
            path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            path.endsWith(".ttml") -> MimeTypes.APPLICATION_TTML
            else -> null
        }
    }

    private fun codecToMimeType(codec: String?): String {
        return when (codec?.lowercase()) {
            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ttml" -> MimeTypes.APPLICATION_TTML
            "pgs", "hdmv_pgs_subtitle" -> MimeTypes.APPLICATION_PGS
            "dvd_subtitle", "dvdsub" -> MimeTypes.APPLICATION_DVBSUBS
            else -> MimeTypes.APPLICATION_SUBRIP // default to SRT
        }
    }

    private fun isMedia3TextSidecarMimeType(mimeType: String): Boolean =
        when (mimeType) {
            MimeTypes.TEXT_VTT,
            MimeTypes.TEXT_SSA,
            MimeTypes.APPLICATION_SUBRIP,
            MimeTypes.APPLICATION_TTML,
            -> true
            else -> false
        }

    private fun disableSubtitles(player: Player) {
        // Disable all text tracks
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private fun applySubtitleSelection(
        player: Player,
        selection: SubtitleSelection,
    ) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(
                TrackSelectionOverride(
                    selection.mediaTrackGroup,
                    selection.trackIndex,
                )
            )
            .build()
    }

}

internal fun resolveSubtitleUrl(serverUrl: String, url: String): String =
    resolvePlaybackStreamUrl(serverUrl, url)

internal data class SubtitleSelection(
    val mediaTrackGroup: TrackGroup,
    val trackIndex: Int,
)

internal fun resolveSubtitleSelection(
    tracks: Tracks,
    subtitleIndex: Int,
): SubtitleSelection? {
    var flatIndex = 0
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (trackIndex in 0 until group.length) {
            if (flatIndex == subtitleIndex) {
                return SubtitleSelection(group.mediaTrackGroup, trackIndex)
            }
            flatIndex++
        }
    }
    return null
}

internal fun resolveSubtitleSelection(
    tracks: Tracks,
    subtitle: PlayerSubtitleInfo,
): SubtitleSelection? {
    val label = subtitle.label?.trim()?.takeIf { it.isNotBlank() }
    val language = subtitle.language?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
    if (label == null && language == null) return null

    val candidates = textTrackCandidates(tracks)
    if (label != null) {
        candidates.firstOrNull { it.label?.trim() == label }?.let { return it.selection }
    }
    if (label != null) {
        val normalizedLabel = label.lowercase()
        candidates.firstOrNull { candidate ->
            candidate.label?.trim()?.lowercase() == normalizedLabel
        }?.let { return it.selection }
    }
    if (language != null) {
        candidates.firstOrNull { it.language?.trim()?.lowercase() == language }?.let { return it.selection }
    }
    return null
}

private data class TextTrackCandidate(
    val selection: SubtitleSelection,
    val label: String?,
    val language: String?,
)

private fun textTrackCandidates(tracks: Tracks): List<TextTrackCandidate> {
    val candidates = mutableListOf<TextTrackCandidate>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (trackIndex in 0 until group.length) {
            val format = group.getTrackFormat(trackIndex)
            candidates += TextTrackCandidate(
                selection = SubtitleSelection(group.mediaTrackGroup, trackIndex),
                label = format.label,
                language = format.language,
            )
        }
    }
    return candidates
}

private fun Tracks.describeTextTracks(): String {
    val parts = mutableListOf<String>()
    var flatIndex = 0
    for (group in groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (trackIndex in 0 until group.length) {
            val format = group.getTrackFormat(trackIndex)
            parts += "$flatIndex:${format.label ?: format.language ?: "?"}" +
                "[selected=${group.isTrackSelected(trackIndex)} supported=${group.isTrackSupported(trackIndex)}]"
            flatIndex++
        }
    }
    return parts.joinToString(prefix = "[", postfix = "]")
}

private const val TAG = "SiloSubtitles"
