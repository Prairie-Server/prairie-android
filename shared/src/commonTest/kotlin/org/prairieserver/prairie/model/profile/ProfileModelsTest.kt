package org.prairieserver.prairie.model.profile

import org.prairieserver.prairie.network.PrairieJson
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileModelsTest {
    @Test
    fun hasProfileNamedIsCaseInsensitiveAndRespectsExclude() {
        val profiles = listOf(
            Profile(id = "1", name = "Laura"),
            Profile(id = "2", name = "Sam"),
        )
        assertTrue(profiles.hasProfileNamed(" laura "))
        assertFalse(profiles.hasProfileNamed("Laura", excludeId = "1"))
        assertFalse(profiles.hasProfileNamed("Alex"))
    }

    @Test
    fun createAndUpdateRequestsRoundTrip() {
        val create = CreateProfileRequest(
            name = "Kid",
            pin = "1234",
            isChild = true,
            maxContentRating = "PG",
            autoSkipIntro = true,
            allowedLibraryIds = listOf(1, 2),
        )
        assertEquals(create, PrairieJson.decodeFromString(PrairieJson.encodeToString(create)))
        val update = UpdateProfileRequest(
            name = "Kiddo",
            subtitleMode = "always",
            showForcedSubtitles = true,
            libraryRestrictionsEnabled = true,
            maxPlaybackQuality = "1080p",
        )
        assertEquals(update, PrairieJson.decodeFromString(PrairieJson.encodeToString(update)))
    }
}
