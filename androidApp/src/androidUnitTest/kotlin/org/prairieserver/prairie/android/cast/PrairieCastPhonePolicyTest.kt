package org.prairieserver.prairie.android.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prairieserver.prairie.cast.PrairieCastCloseReason
import org.prairieserver.prairie.cast.PrairieCastLaunchRequest
import org.prairieserver.prairie.cast.PrairieCastNowPlayingSummary
import org.prairieserver.prairie.cast.PrairieCastPlaybackRequest
import org.prairieserver.prairie.common.cast.PrairieCastTarget

/** The phone's rules for a TV that keeps several controllers (prairie-apple PrairieControlPhonePolicy). */
class PrairieCastPhonePolicyTest {
    private val target = PrairieCastTarget(deviceId = "tv-1", name = "Living Room", host = "10.0.0.2", port = 4711, version = 2)
    private val request = PrairieCastLaunchRequest(
        serverId = "srv-1",
        playback = PrairieCastPlaybackRequest(contentId = "miasma", startFromBeginning = true),
    )

    @Test
    fun anOlderTvsCloseAlwaysForgetsIt() {
        for (reason in listOf(null, PrairieCastCloseReason.Timeout, PrairieCastCloseReason.Replaced)) {
            assertEquals(
                PrairieCastCloseResponse.LetGo(forgetTarget = true),
                PrairieCastClosePolicy.response(reason, tvKeepsOtherControllers = false),
            )
        }
    }

    @Test
    fun aMultiControllerTvSaysWhetherToReconnectOrForget() {
        fun response(reason: String?) = PrairieCastClosePolicy.response(reason, tvKeepsOtherControllers = true)
        assertEquals(PrairieCastCloseResponse.Reconnect, response(PrairieCastCloseReason.Timeout))
        assertEquals(PrairieCastCloseResponse.LetGo(forgetTarget = true), response(PrairieCastCloseReason.TvDisconnected))
        assertEquals(PrairieCastCloseResponse.LetGo(forgetTarget = true), response(null))
        for (reason in listOf(
            PrairieCastCloseReason.TooManyControllers,
            PrairieCastCloseReason.Unauthorized,
            PrairieCastCloseReason.LaunchTimeout,
            PrairieCastCloseReason.Replaced,
        )) {
            assertEquals(reason, PrairieCastCloseResponse.LetGo(forgetTarget = false), response(reason))
        }
    }

    @Test
    fun theTvsPromptNamesWhoIsWatchingAndTiesConsentToThatTitle() {
        val pending = PrairieCastReplacePrompt.fromTv(
            target,
            request,
            PrairieCastNowPlayingSummary(contentId = "oak", title = "Oak Street", subtitle = "Season 1", profileName = "Alex"),
            fallbackMessage = "Alex is watching Oak Street on this TV.",
        )
        assertEquals("Alex is watching Season 1 · Oak Street on Living Room. Play yours instead?", pending.message)
        assertEquals("Replace", pending.confirmLabel)
        assertEquals(PrairieCastReplaceConsent(contentId = "oak"), pending.consent)
        assertEquals(request, pending.request)
    }

    @Test
    fun aPromptWithoutAProfileNameOrSummaryStillReads() {
        assertEquals(
            "Someone is watching Oak Street on Living Room. Play yours instead?",
            PrairieCastReplacePrompt.tvMessage(PrairieCastNowPlayingSummary(title = "Oak Street"), "Living Room", null),
        )
        val fallback = PrairieCastReplacePrompt.fromTv(target, request, nowPlaying = null, fallbackMessage = "Taken.")
        assertEquals("Taken.", fallback.message)
        assertNull(fallback.consent.contentId)
    }

    @Test
    fun thePhoneAsksBeforeReplacingADifferentTitleOnly() {
        assertTrue(PrairieCastReplacePrompt.replacesPlayingTitle("oak", "miasma"))
        assertFalse(PrairieCastReplacePrompt.replacesPlayingTitle("oak", "oak"))
        assertFalse(PrairieCastReplacePrompt.replacesPlayingTitle(null, "miasma"))
        assertFalse(PrairieCastReplacePrompt.replacesPlayingTitle("", "miasma"))

        val pending = PrairieCastReplacePrompt.beforeSending(target, request, currentContentId = "oak", currentTitle = "Oak Street")
        assertEquals("Living Room is playing Oak Street. Playing this will stop it.", pending.message)
        assertEquals("Play on Living Room", pending.confirmLabel)
        assertEquals(PrairieCastReplaceConsent(contentId = "oak"), pending.consent)
    }

    @Test
    fun resumeTokensBelongToOneTvServerAndProfile() {
        val tokens = PrairieCastResumeTokens { lhs, rhs -> lhs == rhs }
        tokens.store("tok-1", tvId = "tv-1", serverId = "srv-1", profileId = "alex")
        assertEquals("tok-1", tokens.token("tv-1", "srv-1", "alex"))
        assertNull(tokens.token("tv-2", "srv-1", "alex"))
        assertNull(tokens.token("tv-1", "srv-2", "alex"))
        assertNull(tokens.token("tv-1", "srv-1", "sam"))

        // A ready without a token (older TV, or a reused identity) keeps the last one.
        tokens.store(null, tvId = "tv-1", serverId = "srv-1", profileId = "alex")
        tokens.store("", tvId = "tv-1", serverId = "srv-1", profileId = "alex")
        assertEquals("tok-1", tokens.token("tv-1", "srv-1", "alex"))
    }
}
