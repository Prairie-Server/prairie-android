package com.continuum.app.common.ebook

import com.continuum.app.model.book.BookFormat
import kotlinx.serialization.Serializable

@Serializable
enum class ReaderTheme { System, Light, Dark, Sepia }

@Serializable
data class ReaderDisplaySettings(
    val theme: ReaderTheme = ReaderTheme.System,
    val textScale: Float = 1f,
    val marginScale: Float = 1f,
) {
    fun normalized(): ReaderDisplaySettings = copy(
        textScale = textScale.coerceIn(0.8f, 1.6f),
        marginScale = marginScale.coerceIn(0.75f, 1.5f),
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
            )
            BookFormat.Pdf, BookFormat.Cbz -> ReaderCapabilities(
                supportsBookmarks = true,
                supportsPageJump = true,
                supportsSections = false,
                supportsTheme = false,
                supportsTextSize = false,
                supportsMargins = false,
            )
            BookFormat.Txt, BookFormat.Markdown, BookFormat.Fb2, BookFormat.Fbz -> ReaderCapabilities(
                supportsBookmarks = true,
                supportsPageJump = true,
                supportsSections = false,
                supportsTheme = true,
                supportsTextSize = true,
                supportsMargins = true,
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
