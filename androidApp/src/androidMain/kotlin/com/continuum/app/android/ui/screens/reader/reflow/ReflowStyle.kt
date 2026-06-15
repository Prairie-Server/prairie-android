package com.continuum.app.android.ui.screens.reader.reflow

import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.common.ebook.ReaderTheme

enum class ReflowTheme(val color: String, val background: String) {
    Light("#1c1b1f", "#fffbfe"),
    Sepia("#2b2118", "#f4ecd8"),
    Dark("#e6e1e5", "#1c1b1f"),
}

data class ReflowStyle(
    val theme: ReflowTheme,
    val fontScalePercent: Int,
    val marginEm: Double,
    val lineHeight: Double = 1.55,
) {
    fun toCss(): String =
        "color:${theme.color};background:${theme.background};" +
            "font-size: $fontScalePercent%;padding:${marginEm}em;" +
            "box-sizing:border-box;line-height:$lineHeight;"
}

fun ReaderDisplaySettings.toReflowStyle(systemDark: Boolean): ReflowStyle {
    val n = normalized()
    val theme = when (n.theme) {
        ReaderTheme.System -> if (systemDark) ReflowTheme.Dark else ReflowTheme.Light
        ReaderTheme.Light -> ReflowTheme.Light
        ReaderTheme.Dark -> ReflowTheme.Dark
        ReaderTheme.Sepia -> ReflowTheme.Sepia
    }
    return ReflowStyle(
        theme = theme,
        fontScalePercent = (n.textScale * 100).toInt(),
        marginEm = (n.marginScale * 1.2),
    )
}
