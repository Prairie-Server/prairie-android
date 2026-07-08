package org.siloserver.silo.common.player.mpv

import org.siloserver.silo.model.settings.SubtitleAppearance
import org.siloserver.silo.model.settings.SubtitleBackgroundStylePreset
import org.siloserver.silo.model.settings.legacyPosition
import org.siloserver.silo.model.settings.pointSize

/** Applied by [MpvPlayer]; kept as an interface so player screens can style
 *  whichever engine is active without depending on MPV directly (mirrors
 *  [MpvVideoScaleController]). */
interface MpvSubtitleStyleController {
    fun applySubtitleAppearance(appearance: SubtitleAppearance)
}

/**
 * Pure translation of the shared [SubtitleAppearance] (same 9 fields and
 * palettes as silo-apple) into MPV `sub-*` properties, closing the gap where
 * MPV-routed playback ignored user styling entirely.
 *
 * Semantics mirror Apple's SubtitleStylingOverride:
 * - Box = ASS BorderStyle 4 with the background color+opacity as BackColour.
 * - Shadow = half-alpha BackColour behind a small shadow offset.
 * - Outline ring = clamp(pointSize * 0.08, 2, 4), independent of background.
 * - Position reuses the legacy 0/70/100 mapping — MPV `sub-pos` is the same
 *   0–100 baseline percentage.
 * - **Native ASS is sacred**: when the active subtitle track is authored
 *   ASS/SSA, only `sub-ass-override=no` is emitted so authored styling wins
 *   (Apple's `isNativeASS` branch); everything else applies to converted or
 *   plain-text formats via `force`.
 */
fun subtitleAppearanceToMpvProperties(
    appearance: SubtitleAppearance,
    nativeAssTrack: Boolean,
): Map<String, String> {
    if (nativeAssTrack) {
        return mapOf("sub-ass-override" to "no")
    }
    val a = appearance.sanitized()
    val props = mutableMapOf<String, String>()
    props["sub-ass-override"] = "force"
    props["sub-font"] = a.fontFamily
    props["sub-font-size"] = a.fontSize.pointSize.toInt().toString()
    props["sub-color"] = a.fontColor

    val outlineSize = if (a.textOutline) {
        (a.fontSize.pointSize * 0.08).coerceIn(2.0, 4.0).toInt().toString()
    } else {
        "0"
    }
    props["sub-border-size"] = outlineSize
    if (a.textOutline) {
        props["sub-border-color"] = a.textOutlineColor
    }

    when (a.backgroundStyle) {
        SubtitleBackgroundStylePreset.Box -> {
            props["sub-border-style"] = "4"
            props["sub-back-color"] = argbHex(a.backgroundColor, alphaPercent = a.backgroundOpacity)
            props["sub-shadow-offset"] = "0"
            // In border-style 4 the border size inflates the BOX (it is not
            // drawn as a ring), so it doubles as box padding. Keep at least a
            // few pixels so the box doesn't hug the glyphs (QA 2026-07-08:
            // "SRT subtitles with box should have more padding").
            props["sub-border-size"] = maxOf(outlineSize.toIntOrNull() ?: 0, 3).toString()
        }
        SubtitleBackgroundStylePreset.Shadow -> {
            props["sub-border-style"] = "1"
            props["sub-back-color"] = argbHex(a.backgroundColor, alphaPercent = 50)
            props["sub-shadow-offset"] = "2"
        }
        // Legacy `outline` decodes migrate to none+textOutline in sanitized();
        // treat any remaining value like none.
        SubtitleBackgroundStylePreset.Outline,
        SubtitleBackgroundStylePreset.None,
        -> {
            props["sub-border-style"] = "1"
            props["sub-back-color"] = "#00000000"
            props["sub-shadow-offset"] = "0"
        }
    }

    props["sub-pos"] = a.position.legacyPosition.toString()
    // Subtitles anchor to the VISIBLE VIDEO frame, never the letterbox bars —
    // deliberate deviation from Apple (whose Bottom preset drops into the
    // tvOS letterbox via ass_set_use_margins): position presets should mean
    // top/bottom of the movie, not of the screen. Also matches MpvPlayer's
    // init default (sub-use-margins=no).
    props["sub-use-margins"] = "no"
    return props
}

/** `#RRGGBB` + percent opacity → MPV `#AARRGGBB`. */
private fun argbHex(rgbHex: String, alphaPercent: Int): String {
    val rgb = rgbHex.removePrefix("#").uppercase()
    val alpha = (alphaPercent.coerceIn(0, 100) * 255 + 50) / 100
    return "#%02X%s".format(alpha, rgb)
}
