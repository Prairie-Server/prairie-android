package com.continuum.app.model.personal

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
}
