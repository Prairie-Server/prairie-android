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
        """
        #reflow-root{
          color:${theme.color};
          background:${theme.background};
          font-family:Georgia,"Times New Roman",serif;
          font-size: $fontScalePercent%;
          padding:${marginEm}em;
          box-sizing:border-box;
          line-height:$lineHeight;
          overflow-wrap:break-word;
          word-break:normal;
          text-rendering:optimizeLegibility;
          -webkit-font-smoothing:antialiased;
        }
        #reflow-root p{
          margin:0 0 1em 0;
          widows:2;
          orphans:2;
        }
        #reflow-root h1,#reflow-root h2,#reflow-root h3{
          line-height:1.18;
          margin:1.2em 0 0.65em 0;
          font-weight:700;
          break-after:avoid;
        }
        #reflow-root img,#reflow-root svg,#reflow-root table{
          max-width:100%;
          height:auto;
          break-inside:avoid;
        }
        #reflow-root blockquote{
          margin:1em 1.5em;
        }
        #reflow-root a{
          color:inherit;
        }
        """.trimIndent()
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
