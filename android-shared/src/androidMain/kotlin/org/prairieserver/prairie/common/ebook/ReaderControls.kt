package org.prairieserver.prairie.common.ebook

import org.prairieserver.prairie.model.book.BookFormat
import kotlinx.serialization.Serializable

@Serializable
enum class ReaderTheme { System, Light, Dark, Sepia }

@Serializable
enum class ReaderFontFamily { Serif, SansSerif, Slab, Dyslexic }

@Serializable
data class ReaderDisplaySettings(
    val theme: ReaderTheme = ReaderTheme.System,
    val textScale: Float = 1f,
    val marginScale: Float = 1f,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.Serif,
    val lineHeight: Float = 1.5f,
) {
    fun normalized(): ReaderDisplaySettings = copy(
        textScale = textScale.coerceIn(0.6f, 3.0f),
        marginScale = marginScale.coerceIn(0.75f, 1.5f),
        lineHeight = lineHeight.coerceIn(1.1f, 2.2f),
    )
}

data class ReaderSection(
    val index: Int,
    val title: String,
    val location: String,
)

data class ReaderCapabilities(
    val supportsBookmarks: Boolean,
    val supportsPageJump: Boolean,
    val supportsSections: Boolean,
    val supportsTheme: Boolean,
    val supportsTextSize: Boolean,
    val supportsMargins: Boolean,
    val supportsExternalOnly: Boolean = false,
    val engineKind: ReaderEngineKind = ReaderEngineKind.External,
) {
    companion object {
        fun forFormat(format: BookFormat): ReaderCapabilities = when (format) {
            BookFormat.Epub -> ReaderCapabilities(
                supportsBookmarks = true,
                supportsPageJump = true,
                supportsSections = true,
                supportsTheme = true,
                supportsTextSize = true,
                supportsMargins = true,
                engineKind = ReaderEngineKind.Reflowable,
            )
            BookFormat.Pdf -> ReaderCapabilities(
                supportsBookmarks = true,
                supportsPageJump = true,
                supportsSections = false,
                supportsTheme = false,
                supportsTextSize = false,
                supportsMargins = false,
                engineKind = ReaderEngineKind.FixedDocument,
            )
            BookFormat.Cbz -> ReaderCapabilities(
                supportsBookmarks = true,
                supportsPageJump = true,
                supportsSections = false,
                supportsTheme = false,
                supportsTextSize = false,
                supportsMargins = false,
                engineKind = ReaderEngineKind.ComicManga,
            )
            BookFormat.Txt, BookFormat.Markdown, BookFormat.Fb2, BookFormat.Fbz -> ReaderCapabilities(
                supportsBookmarks = true,
                supportsPageJump = true,
                supportsSections = false,
                supportsTheme = true,
                supportsTextSize = true,
                supportsMargins = true,
                engineKind = ReaderEngineKind.Reflowable,
            )
            else -> ReaderCapabilities(
                supportsBookmarks = false,
                supportsPageJump = false,
                supportsSections = false,
                supportsTheme = false,
                supportsTextSize = false,
                supportsMargins = false,
                supportsExternalOnly = true,
            )
        }
    }
}
