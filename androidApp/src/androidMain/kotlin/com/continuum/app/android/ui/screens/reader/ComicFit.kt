package com.continuum.app.android.ui.screens.reader
import kotlin.math.min
fun comicFitScale(pageW: Int, pageH: Int, viewW: Int, viewH: Int, mode: ComicFitMode): Float {
    if (pageW <= 0 || pageH <= 0 || viewW <= 0 || viewH <= 0) return 1f
    val sw = viewW.toFloat() / pageW
    val sh = viewH.toFloat() / pageH
    return when (mode) {
        ComicFitMode.Width -> sw
        ComicFitMode.Height -> sh
        ComicFitMode.Screen -> min(sw, sh)
        ComicFitMode.Original -> 1f
    }
}
