package org.prairieserver.prairie.model.onboarding

import org.prairieserver.prairie.network.PrairieJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingModelsTest {

    @Test
    fun flowRoundTripsWithSettingStepAndUnknownKind() {
        val flow = OnboardingFlow(
            version = 2,
            tourId = "phone-welcome",
            steps = listOf(
                OnboardingStep(
                    id = "intro",
                    kind = "copy",
                    title = "Welcome",
                    body = "A short tour.",
                    illustration = "welcome",
                ),
                OnboardingStep(
                    id = "audio",
                    kind = "setting",
                    setting = OnboardingSettingSpec(
                        target = "setting",
                        key = "playback.audio_language",
                        control = "picker",
                        options = listOf(
                            OnboardingSettingOption(value = "en", label = "English"),
                            OnboardingSettingOption(value = "ja", label = "Japanese"),
                        ),
                        default = "en",
                        label = "Audio language",
                    ),
                ),
                OnboardingStep(
                    id = "future",
                    kind = "unknown_future_kind",
                ),
            ),
        )

        val decoded = PrairieJson.decodeFromString(OnboardingFlow.serializer(), PrairieJson.encodeToString(flow))

        assertEquals(2, decoded.version)
        assertEquals("phone-welcome", decoded.tourId)
        assertEquals(3, decoded.steps.size)
        assertEquals("copy", decoded.steps[0].kind)
        assertEquals("welcome", decoded.steps[0].illustration)
        assertEquals("playback.audio_language", decoded.steps[1].setting?.key)
        assertEquals(2, decoded.steps[1].setting?.options?.size)
        assertEquals("en", decoded.steps[1].setting?.default)
        assertEquals("unknown_future_kind", decoded.steps[2].kind)
        assertNull(decoded.steps[2].setting)
    }

    @Test
    fun stateAndProgressRequestRoundTrip() {
        val state = OnboardingState(
            tourId = "tv-welcome",
            lastStep = "audio",
            completedAt = null,
            skippedAt = "2026-07-01T00:00:00Z",
            done = true,
        )
        val progress = OnboardingProgressRequest(
            tourId = "tv-welcome",
            lastStep = "audio",
            completed = false,
            skipped = true,
        )

        val decodedState =
            PrairieJson.decodeFromString(OnboardingState.serializer(), PrairieJson.encodeToString(state))
        val decodedProgress =
            PrairieJson.decodeFromString(
                OnboardingProgressRequest.serializer(),
                PrairieJson.encodeToString(progress),
            )

        assertEquals("tv-welcome", decodedState.tourId)
        assertEquals("audio", decodedState.lastStep)
        assertNull(decodedState.completedAt)
        assertEquals("2026-07-01T00:00:00Z", decodedState.skippedAt)
        assertTrue(decodedState.done)

        assertEquals("tv-welcome", decodedProgress.tourId)
        assertEquals("audio", decodedProgress.lastStep)
        assertFalse(decodedProgress.completed)
        assertTrue(decodedProgress.skipped)
    }
}
