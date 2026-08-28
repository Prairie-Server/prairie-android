package org.prairieserver.prairie.tv.ui.screens.player

data class TvTransportControlMetrics(
    val buttonSizeDp: Float,
    val symbolSizeDp: Float,
)

fun tvTransportControlMetrics(isPrimary: Boolean): TvTransportControlMetrics =
    TvTransportControlMetrics(
        buttonSizeDp = 44f,
        symbolSizeDp = if (isPrimary) 22f else 20f,
    )
