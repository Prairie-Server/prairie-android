package com.continuum.app.android.ui.screens.reader

enum class ReadingDirection { LeftToRight, RightToLeft, Vertical }
enum class ComicFitMode { Width, Height, Screen, Original }
enum class ComicTapAction { Previous, Next, ToggleChrome }

data class ComicReaderConfig(
    val direction: ReadingDirection = ReadingDirection.LeftToRight,
    val fitMode: ComicFitMode = ComicFitMode.Screen,
) {
    fun tapAction(xFraction: Float): ComicTapAction {
        val x = xFraction.coerceIn(0f, 1f)
        val zone = when {
            x < 1f / 3f -> ComicTapAction.Previous
            x > 2f / 3f -> ComicTapAction.Next
            else -> ComicTapAction.ToggleChrome
        }
        return if (direction == ReadingDirection.RightToLeft) zone.invertHorizontal() else zone
    }
}

private fun ComicTapAction.invertHorizontal(): ComicTapAction = when (this) {
    ComicTapAction.Previous -> ComicTapAction.Next
    ComicTapAction.Next -> ComicTapAction.Previous
    ComicTapAction.ToggleChrome -> ComicTapAction.ToggleChrome
}
