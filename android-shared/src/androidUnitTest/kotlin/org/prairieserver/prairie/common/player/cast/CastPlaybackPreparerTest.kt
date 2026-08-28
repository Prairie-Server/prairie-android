package org.prairieserver.prairie.common.player.cast

import org.prairieserver.prairie.common.network.PrairieClientBuildIdentity
import org.prairieserver.prairie.model.playback.PlaybackDelivery
import org.prairieserver.prairie.model.playback.PlaybackPlanV3
import org.prairieserver.prairie.model.playback.PlaybackStreamProtocol
import org.prairieserver.prairie.model.playback.PlaybackStreamV3
import org.prairieserver.prairie.model.playback.PlaybackSubtitleDecisionV3
import org.prairieserver.prairie.model.playback.PlaybackSubtitleInventoryItemV3
import org.prairieserver.prairie.model.playback.PlaybackTimelineV3
import org.prairieserver.prairie.model.playback.playbackClientFeaturesV3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CastPlaybackPreparerTest {
    @Test
    fun castContextDoesNotAdvertiseThePreNeutralSidecarFeature() {
        assertFalse(
            "external_text_sidecar_set_v1" in
                playbackClientFeaturesV3(
                    chromecastPlaybackContext(
                        appVersion = "test",
                        buildIdentity = PrairieClientBuildIdentity(buildNumber = "5", channel = "release"),
                    ),
                ),
        )
    }

    @Test
    fun castUsesPlayerLocalStartInsteadOfSourceTimelinePosition() {
        val plan = plan(
            timeline = PlaybackTimelineV3(
                sourceStartSeconds = 90.0,
                playerStartSeconds = 0.0,
            ),
        )

        assertEquals(0.0, castPlayerStartPosition(plan, requested = 90.0))
    }

    @Test
    fun castUsesAuthoritativeInventoryAndPreservesAnAuthoritativeEmptyList() {
        assertEquals(emptyList(), castSubtitleInventory(plan()))
        val authoritative = PlaybackSubtitleInventoryItemV3(
            trackId = "file:7:subtitle:0",
            combinedIndex = 0,
            source = "embedded",
            codec = "ass",
            delivery = "sidecar",
            url = "/subtitles/0.ass",
        )
        assertEquals(
            listOf(authoritative),
            castSubtitleInventory(
                plan(
                    subtitle = PlaybackSubtitleDecisionV3(
                        inventory = listOf(authoritative),
                    ),
                ),
            ),
        )
    }

    @Test
    fun successfulReceiverLoadResetsTheRecoveryBudget() {
        val budget = CastLoadRecoveryBudget(maxAttempts = 3)

        repeat(5) {
            assertTrue(budget.tryConsume())
            budget.resetAfterSuccess()
        }
    }

    @Test
    fun consecutiveReceiverLoadFailuresExhaustTheRecoveryBudget() {
        val budget = CastLoadRecoveryBudget(maxAttempts = 3)

        repeat(3) { assertTrue(budget.tryConsume()) }
        assertFalse(budget.tryConsume())
    }

    private fun plan(
        timeline: PlaybackTimelineV3 = PlaybackTimelineV3(),
        subtitle: PlaybackSubtitleDecisionV3 = PlaybackSubtitleDecisionV3(),
    ) = PlaybackPlanV3(
        planId = "plan",
        planAttemptKey = "opaque",
        delivery = PlaybackDelivery.SERVER_REMUX_HLS,
        stream = PlaybackStreamV3("/stream.m3u8", PlaybackStreamProtocol.HLS),
        timeline = timeline,
        subtitle = subtitle,
        decisionReason = "test",
    )
}
