package org.prairieserver.prairie.android.ui.screens.player

import org.prairieserver.prairie.common.player.PlaybackSessionManager
import org.prairieserver.prairie.common.player.VideoSessionStartV3
import org.prairieserver.prairie.common.player.isPlaybackSessionEnded
import org.prairieserver.prairie.model.playback.SUBTITLE_SELECTION_FAILURE_CLASSIFICATIONS
import org.prairieserver.prairie.network.ApiResult

/** A deferred replan keeps the selected subtitle ordinal until its recovery can run. */
internal data class MobileRecoveryReplan(
    val classification: String,
    val notice: String,
    val subtitleTrackIndexOverride: Int? = null,
) {
    val shouldQueue: Boolean
        get() = classification in PlaybackSessionManager.USER_INVALIDATION_CLASSIFICATIONS ||
            classification in SUBTITLE_SELECTION_FAILURE_CLASSIFICATIONS

    /**
     * A failed subtitle replan keeps the current video playing, except when
     * the server ended the session (410 `playback_session_ended`): the player
     * then leaves instead.
     */
    fun isNonfatalFailure(result: ApiResult<VideoSessionStartV3>): Boolean =
        classification in SUBTITLE_SELECTION_FAILURE_CLASSIFICATIONS &&
            result !is ApiResult.Success &&
            !result.isPlaybackSessionEnded()
}
