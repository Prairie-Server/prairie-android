package org.siloserver.silo.common.player

import org.siloserver.silo.model.playback.PlaybackDelivery
import org.siloserver.silo.model.playback.PlaybackExecutionPlan

fun PlaybackExecutionPlan?.validatedColorRangeFallback(): String? {
    val plan = this ?: return null
    return when (plan.delivery) {
        PlaybackDelivery.ORIGINAL_HTTP,
        PlaybackDelivery.SERVER_REMUX_PROGRESSIVE,
        PlaybackDelivery.SERVER_REMUX_HLS,
        -> plan.source.colorRange
            ?.trim()
            ?.lowercase()
            ?.takeIf { it == "tv" || it == "pc" }

        PlaybackDelivery.CLIENT_LOCAL_NORMALIZATION,
        PlaybackDelivery.SERVER_TRANSCODE_HLS,
        -> null
    }
}
