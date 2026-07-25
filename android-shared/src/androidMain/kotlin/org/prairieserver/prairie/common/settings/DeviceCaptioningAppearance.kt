package org.prairieserver.prairie.common.settings

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.accessibility.CaptioningManager
import org.prairieserver.prairie.model.settings.SubtitleAppearance
import org.prairieserver.prairie.model.settings.SubtitleBackgroundStylePreset
import org.prairieserver.prairie.model.settings.SubtitleFontSizePreset

/**
 * Maps the Android system captioning preferences ([CaptioningManager]) onto
 * the shared [SubtitleAppearance] model — the Android analogue of tvOS's
 * "Match Device Settings" subtitle toggle, which defers to the OS caption
 * style. Position is not part of the system style and keeps the app value.
 */
fun deviceCaptioningAppearance(context: Context, base: SubtitleAppearance): SubtitleAppearance {
    val manager = context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
        ?: return base
    val style = manager.userStyle

    val fontColor = style.takeIf { it.hasForegroundColor() }?.foregroundColor
    val backgroundColor = style.takeIf { it.hasBackgroundColor() }?.backgroundColor
    val edgeType = style.takeIf { it.hasEdgeType() }?.edgeType
    val edgeColor = style.takeIf { it.hasEdgeColor() }?.edgeColor

    val backgroundAlpha = backgroundColor?.let { AndroidColor.alpha(it) }
    val backgroundStyle = when {
        backgroundAlpha == null -> SubtitleBackgroundStylePreset.Box
        backgroundAlpha == 0 -> SubtitleBackgroundStylePreset.None
        else -> SubtitleBackgroundStylePreset.Box
    }

    return SubtitleAppearance(
        fontSize = fontSizePresetFor(manager.fontScale),
        fontFamily = base.fontFamily,
        fontColor = fontColor?.let(::rgbHex) ?: base.fontColor,
        backgroundColor = backgroundColor?.let(::rgbHex) ?: base.backgroundColor,
        backgroundStyle = backgroundStyle,
        backgroundOpacity = backgroundAlpha?.let { (it * 100) / 255 } ?: base.backgroundOpacity,
        textOutline = edgeType == CaptioningManager.CaptionStyle.EDGE_TYPE_OUTLINE,
        textOutlineColor = edgeColor?.let(::rgbHex) ?: base.textOutlineColor,
        position = base.position,
    ).sanitized()
}

private fun fontSizePresetFor(scale: Float): SubtitleFontSizePreset = when {
    scale < 0.75f -> SubtitleFontSizePreset.Small
    scale < 1.0f -> SubtitleFontSizePreset.Medium
    scale < 1.25f -> SubtitleFontSizePreset.Large
    scale < 1.75f -> SubtitleFontSizePreset.XLarge
    else -> SubtitleFontSizePreset.XXLarge
}

private fun rgbHex(color: Int): String =
    "#%02x%02x%02x".format(
        AndroidColor.red(color),
        AndroidColor.green(color),
        AndroidColor.blue(color),
    )
