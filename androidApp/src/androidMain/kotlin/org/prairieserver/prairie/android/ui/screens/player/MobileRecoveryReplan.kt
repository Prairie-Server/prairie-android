package org.prairieserver.prairie.android.ui.screens.player

import org.prairieserver.prairie.common.player.PlaybackSessionManager
import org.prairieserver.prairie.common.player.VideoSessionStartV3
import org.prairieserver.prairie.network.ApiResult

/** A deferred replan keeps the selected subtitle ordinal until its recovery can run. */
internal data class MobileRecoveryReplan(
    val classification: String,
    val notice: String,
    val subtitleTrackIndexOverride: Int? = null,
) {
    val shouldQueue: Boolean
        get() = classification in PlaybackSessionManager.USER_INVALIDATION_CLASSIFICATIONS ||
            classification == "subtitle_embedded_failed"

    fun isNonfatalFailure(result: ApiResult<VideoSessionStartV3>): Boolean =
        classification == "subtitle_embedded_failed" &&
            result !is ApiResult.Success
}
