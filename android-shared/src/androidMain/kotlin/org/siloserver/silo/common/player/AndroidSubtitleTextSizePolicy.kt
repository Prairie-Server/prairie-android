package org.siloserver.silo.common.player

import org.siloserver.silo.model.settings.SubtitleFontSizePreset

internal sealed interface AndroidSubtitleTextSize {
    data class Fractional(val fraction: Float) : AndroidSubtitleTextSize
    data class FixedSp(val sp: Float) : AndroidSubtitleTextSize
}

internal fun androidSubtitleTextSize(
    presentation: AndroidSubtitlePresentation,
    preset: SubtitleFontSizePreset,
): AndroidSubtitleTextSize = when (presentation) {
    AndroidSubtitlePresentation.Phone -> AndroidSubtitleTextSize.Fractional(
        when (preset) {
            SubtitleFontSizePreset.Small -> 22.5f
            SubtitleFontSizePreset.Medium -> 29.25f
            SubtitleFontSizePreset.Large -> 36f
            SubtitleFontSizePreset.XLarge -> 45f
            SubtitleFontSizePreset.XXLarge -> 54f
        } / 720f,
    )
    AndroidSubtitlePresentation.Television -> AndroidSubtitleTextSize.FixedSp(
        when (preset) {
            SubtitleFontSizePreset.Small -> 18f
            SubtitleFontSizePreset.Medium -> 22f
            SubtitleFontSizePreset.Large -> 26f
            SubtitleFontSizePreset.XLarge -> 32f
            SubtitleFontSizePreset.XXLarge -> 40f
        },
    )
}
