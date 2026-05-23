package com.continuum.app.common.player

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
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
        return subtitles.map { subtitle ->
            val absoluteUrl = resolveUrl(serverUrl, subtitle.url)
            val mimeType = codecToMimeType(subtitle.codec)

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
    fun selectSubtitle(player: Player, subtitleIndex: Int) {
        if (subtitleIndex < 0) {
            // Disable all text tracks
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            // Enable text tracks and select the specific one
            val trackGroups = player.currentTracks.groups
            var textGroupIndex = 0

            for (group in trackGroups) {
                if (group.type == C.TRACK_TYPE_TEXT) {
                    if (textGroupIndex == subtitleIndex) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                androidx.media3.common.TrackSelectionOverride(
                                    group.mediaTrackGroup,
                                    /* trackIndex = */ 0,
                                )
                            )
                            .build()
                        return
                    }
                    textGroupIndex++
                }
            }
        }
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
            SubtitleFontSizePreset.Small -> 0.040f
            SubtitleFontSizePreset.Medium -> 0.050f
            SubtitleFontSizePreset.Large -> 0.060f
            SubtitleFontSizePreset.XLarge -> 0.072f
            SubtitleFontSizePreset.XXLarge -> 0.085f
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

    private fun resolveUrl(serverUrl: String, url: String): String {
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "${serverUrl.trimEnd('/')}$url"
        }
    }
}
