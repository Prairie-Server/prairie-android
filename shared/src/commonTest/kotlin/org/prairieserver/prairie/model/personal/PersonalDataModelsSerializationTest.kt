package org.prairieserver.prairie.model.personal

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonalDataModelsSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun encodesSetRatingRequestAsJsonInteger() {
        // Go's int unmarshal rejects "4.0" — the wire value must be a bare
        // JSON integer with no decimal point.
        val encoded = json.encodeToString(
            SetRatingRequest.serializer(),
            SetRatingRequest(rating = 4),
        )
        assertEquals("""{"rating":4}""", encoded)
    }

    @Test
    fun decodesRatingEntryFromSingleRatingResponse() {
        // Server single-rating response is {rating, rated_at} — no media_item_id.
        val wire = """{"rating":4.5,"rated_at":"2026-06-01T00:00:00Z"}"""
        val entry = json.decodeFromString<RatingEntry>(wire)
        assertEquals(4.5, entry.rating)
        assertEquals(null as String?, entry.mediaItemId)
        assertEquals("2026-06-01T00:00:00Z", entry.updatedAt)
    }
}
