package org.prairieserver.prairie.android.cast

import org.prairieserver.prairie.cast.PrairieCastCloseReason
import org.prairieserver.prairie.cast.PrairieCastPlaybackRequest
import org.prairieserver.prairie.model.catalog.FileVersion
import org.prairieserver.prairie.model.playback.combinedSubtitleSelectionIndexes
import org.prairieserver.prairie.cast.PrairieCastLaunchRequest
import org.prairieserver.prairie.cast.PrairieCastNowPlayingSummary
import org.prairieserver.prairie.common.cast.PrairieCastTarget

/**
 * A Roku takes `launch` track indexes as the values `/api/v2/playback/start`
 * takes (prairie-roku `docs/prairiecontrol.md` section 7.2): the audio ordinal,
 * which is the detail picker's position already, and the COMBINED subtitle
 * ordinal (externals first), which the picker's catalog position is not.
 * Other TVs keep receiving the catalog position they have always read.
 */
object PrairieCastRokuTracks {
    /**
     * [playback] with its subtitle index as the combined ordinal in the
     * launched version, found by `fileId` (or the only version). Off (negative)
     * stays off. A choice that can't be mapped is dropped so the Roku picks by
     * its own rules, rather than showing a different track.
     */
    fun forRoku(playback: PrairieCastPlaybackRequest, versions: List<FileVersion>): PrairieCastPlaybackRequest {
        val position = playback.subtitleTrackIndex ?: return playback
        if (position < 0) return playback
        val version = versions.firstOrNull { it.fileId == playback.fileId } ?: versions.singleOrNull()
        val combined = version?.subtitleTracks?.let(::combinedSubtitleSelectionIndexes)?.getOrNull(position)
        return playback.copy(subtitleTrackIndex = combined)
    }
}

/** What the phone does when the TV closes its session (prairie-apple `PrairieControlCloseResponse`). */
sealed interface PrairieCastCloseResponse {
    /** Connect again quietly, as after a dropped link. */
    data object Reconnect : PrairieCastCloseResponse

    /** End the session. With [forgetTarget], no later foreground probe silently reattaches to that TV. */
    data class LetGo(val forgetTarget: Boolean) : PrairieCastCloseResponse
}

object PrairieCastClosePolicy {
    /**
     * A TV without `multi_controller` closes only to hand the slot to another
     * phone or because someone pressed Disconnect, so the phone lets go and
     * forgets it, as it always has. A TV that keeps several controllers says
     * why: a deliberate disconnect (or no reason) still forgets the TV, a
     * timeout reconnects, and the rest end the session but let a later
     * foreground find the TV again.
     */
    fun response(reason: String?, tvKeepsOtherControllers: Boolean): PrairieCastCloseResponse {
        if (!tvKeepsOtherControllers) return PrairieCastCloseResponse.LetGo(forgetTarget = true)
        return when (reason) {
            PrairieCastCloseReason.Timeout -> PrairieCastCloseResponse.Reconnect
            null, PrairieCastCloseReason.TvDisconnected -> PrairieCastCloseResponse.LetGo(forgetTarget = true)
            else -> PrairieCastCloseResponse.LetGo(forgetTarget = false)
        }
    }
}

/**
 * The user's agreement to replace one particular title on the TV, carried from
 * the prompt to the offer's `replacingContentId`.
 */
data class PrairieCastReplaceConsent(
    /** The title the user saw when agreeing; null when the TV didn't say. */
    val contentId: String?,
)

/**
 * A launch waiting for the user to agree to replace what the TV plays. Asked
 * either by the phone before sending a different title to an engaged TV, or by
 * the TV when another profile's title is on screen.
 */
data class PrairieCastPendingReplace(
    val target: PrairieCastTarget,
    val request: PrairieCastLaunchRequest,
    val message: String,
    val confirmLabel: String,
    /** Consent sent with the launch once the user agrees. */
    val consent: PrairieCastReplaceConsent,
)

object PrairieCastReplacePrompt {
    /** The TV refused the handoff because another profile's title is on screen. */
    fun fromTv(
        target: PrairieCastTarget,
        request: PrairieCastLaunchRequest,
        nowPlaying: PrairieCastNowPlayingSummary?,
        fallbackMessage: String?,
    ): PrairieCastPendingReplace = PrairieCastPendingReplace(
        target = target,
        request = request,
        message = tvMessage(nowPlaying, target.name, fallbackMessage),
        confirmLabel = "Replace",
        consent = PrairieCastReplaceConsent(contentId = nowPlaying?.contentId),
    )

    /** The phone is about to send a different title to a TV that is playing one. */
    fun beforeSending(
        target: PrairieCastTarget,
        request: PrairieCastLaunchRequest,
        currentContentId: String,
        currentTitle: String,
    ): PrairieCastPendingReplace = PrairieCastPendingReplace(
        target = target,
        request = request,
        message = "${target.name} is playing $currentTitle. Playing this will stop it.",
        confirmLabel = "Play on ${target.name}",
        consent = PrairieCastReplaceConsent(contentId = currentContentId),
    )

    /** Whether sending [contentId] would replace a different title the TV is playing. */
    fun replacesPlayingTitle(playingContentId: String?, contentId: String): Boolean =
        !playingContentId.isNullOrEmpty() && playingContentId != contentId

    /** The same sentence prairie-apple builds from the TV's summary. */
    fun tvMessage(nowPlaying: PrairieCastNowPlayingSummary?, tvName: String, fallbackMessage: String?): String {
        if (nowPlaying == null) return fallbackMessage ?: "Someone is watching something on $tvName. Play yours instead?"
        val who = nowPlaying.profileName ?: "Someone"
        val what = listOfNotNull(nowPlaying.subtitle, nowPlaying.title)
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
        return "$who is watching $what on $tvName. Play yours instead?"
    }
}

/**
 * Resume tokens the TVs issued this phone, held in memory only. A token lets a
 * later connection to the same TV reuse the profile it already holds for this
 * phone without a new approval, as after a Wi-Fi change.
 */
class PrairieCastResumeTokens(
    private val serverIdsMatch: (String?, String?) -> Boolean,
) {
    private data class Entry(val serverId: String, val profileId: String, val token: String)

    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun token(tvId: String, serverId: String, profileId: String): String? {
        val entry = entries[tvId] ?: return null
        if (!serverIdsMatch(entry.serverId, serverId) || entry.profileId != profileId) return null
        return entry.token
    }

    @Synchronized
    fun store(token: String?, tvId: String, serverId: String, profileId: String) {
        if (token.isNullOrEmpty()) return
        entries[tvId] = Entry(serverId, profileId, token)
    }
}
