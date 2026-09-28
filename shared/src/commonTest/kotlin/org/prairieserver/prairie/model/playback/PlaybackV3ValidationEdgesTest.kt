package org.prairieserver.prairie.model.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaybackV3ValidationEdgesTest {
    private val features = listOf(PLAYBACK_PLAN_V3_FEATURE, NEUTRAL_PLAYBACK_V3_CONTRACT_FEATURE)
    private val plan = PlaybackPlanV3(
        planId = "plan-1",
        planAttemptKey = "v3:0000000000000001",
        sessionId = "session-1",
        delivery = PlaybackDelivery.ORIGINAL_HTTP,
        stream = PlaybackStreamV3(url = "/stream", protocol = PlaybackStreamProtocol.HTTP_PROGRESSIVE),
        decisionReason = "original_compatible",
    )

    private fun response(
        candidate: PlaybackPlanV3? = plan,
        outcome: PlaybackDecisionOutcome? = PlaybackDecisionOutcome.PLAYABLE,
        sessionId: String? = null,
        terminal: PlaybackTerminalV3? = null,
    ) = PlaybackDecisionResponseV3(
        protocolVersion = 3,
        serverFeatures = features,
        outcome = outcome,
        sessionId = sessionId,
        playbackPlan = candidate,
        terminal = terminal,
    ).validateForMedia3()

    private fun terminalReason(result: PlaybackV3Validation): String = assertIs<PlaybackV3Validation.Terminal>(result).reason

    @Test
    fun adaptationUnavailableWithoutTerminalBodyIsInvalid() {
        val result = assertIs<PlaybackV3Validation.Terminal>(
            response(candidate = null, outcome = PlaybackDecisionOutcome.ADAPTATION_UNAVAILABLE),
        )
        assertEquals("invalid_terminal_response", result.reason)
        assertEquals(false, result.retryable)
    }

    @Test
    fun missingOutcomeOrPlanFailsClosed() {
        assertEquals("invalid_playback_outcome", terminalReason(response(outcome = null)))
        assertEquals("invalid_playback_plan", terminalReason(response(candidate = null)))
    }

    @Test
    fun sessionIdentityFallsBackToTheResponseThenFails() {
        val fromResponse = assertIs<PlaybackV3Validation.Playable>(
            response(candidate = plan.copy(sessionId = null), sessionId = "outer"),
        )
        assertEquals("outer", fromResponse.sessionId)
        val missing = assertIs<PlaybackV3Validation.Terminal>(response(candidate = plan.copy(sessionId = null)))
        assertTrue(missing.message.contains("session identity"))
    }

    @Test
    fun unsupportedPlanVersionAndEmptyStreamUrlAreTerminal() {
        val version = assertIs<PlaybackV3Validation.Terminal>(response(candidate = plan.copy(protocolVersion = 2)))
        assertTrue(version.message.contains("plan version"))
        val emptyUrl = assertIs<PlaybackV3Validation.Terminal>(
            response(candidate = plan.copy(stream = plan.stream.copy(url = " "))),
        )
        assertTrue(emptyUrl.message.contains("empty stream URL"))
    }

    @Test
    fun serverTransformationOnOriginalDeliveryRequestsReplan() {
        val candidate = plan.copy(transformations = listOf(PlaybackTransformationV3(name = "tone_map")))
        val result = assertIs<PlaybackV3Validation.ReplanRequired>(response(candidate = candidate))
        assertEquals("invalid_original_server_transformation", result.reason)
        assertEquals("session-1", result.sessionId)
    }

    @Test
    fun onlyKnownClientRecipesAreExecutable() {
        val candidate = plan.copy(
            transformations = listOf(
                PlaybackTransformationV3(CLIENT_DV7_TO_HDR10, PlaybackTransformationExecutor.CLIENT),
                PlaybackTransformationV3(CLIENT_DV7_TO_DV81, PlaybackTransformationExecutor.CLIENT, recipeVersion = "2"),
                PlaybackTransformationV3(CLIENT_DV7_TO_DV81, PlaybackTransformationExecutor.SERVER),
            ),
        )
        assertEquals(listOf(CLIENT_DV7_TO_HDR10), candidate.executableMedia3ClientTransformations())
    }

    @Test
    fun dv8BaseLayerClaimIsActiveOnlyForOriginalDelivery() {
        val dv8 = plan.copy(decisionReason = DECISION_REASON_CLIENT_DV8_BASE_LAYER)
        assertEquals(listOf(CLIENT_DV8_BASE_LAYER_FALLBACK_V1_CLAIM), dv8.activeOriginalHttpClaims())
        assertEquals(emptyList(), dv8.copy(delivery = PlaybackDelivery.SERVER_TRANSCODE_HLS).activeOriginalHttpClaims())
        assertEquals(emptyList(), plan.activeOriginalHttpClaims())
    }
}
