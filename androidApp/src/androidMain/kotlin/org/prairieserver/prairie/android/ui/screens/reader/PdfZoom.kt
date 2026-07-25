package org.prairieserver.prairie.android.ui.screens.reader

const val PDF_MIN_ZOOM = 1f
const val PDF_MAX_ZOOM = 5f
private const val PDF_DOUBLE_TAP_ZOOM = 2.5f

fun clampPdfZoom(scale: Float): Float = scale.coerceIn(PDF_MIN_ZOOM, PDF_MAX_ZOOM)

fun nextDoubleTapZoom(current: Float): Float =
    if (current > PDF_MIN_ZOOM + 0.01f) PDF_MIN_ZOOM else PDF_DOUBLE_TAP_ZOOM
