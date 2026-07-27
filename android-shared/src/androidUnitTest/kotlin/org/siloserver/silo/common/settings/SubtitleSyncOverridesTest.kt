package org.siloserver.silo.common.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleSyncOverridesTest {
    @Test
    fun roundTripsEntries() {
        val encoded = encodeSubtitleSyncOverrides(mapOf("movie-1" to -250, "episode-2" to 1_500))
        assertEquals(mapOf("movie-1" to -250, "episode-2" to 1_500), decodeSubtitleSyncOverrides(encoded))
    }

    @Test
    fun malformedLinesAreDroppedNotGuessedAt() {
        val decoded = decodeSubtitleSyncOverrides("good=100\nbroken\nbad=notanumber\n=500\n")
        assertEquals(mapOf("good" to 100), decoded)
    }

    @Test
    fun idsCarryingSeparatorsAreRefused() {
        val encoded = encodeSubtitleSyncOverrides(
            mapOf("ok" to 1, "bad=id" to 2, "bad\nid" to 3, "" to 4),
        )
        assertEquals(mapOf("ok" to 1), decodeSubtitleSyncOverrides(encoded))
    }

    @Test
    fun theMapIsBoundedKeepingTheMostRecent() {
        val decoded = decodeSubtitleSyncOverrides(
            encodeSubtitleSyncOverrides((1..250).associate { "item-$it" to it }),
        )
        assertEquals(200, decoded.size)
        assertEquals(250, decoded["item-250"])
        assertEquals(null, decoded["item-1"])
    }
}
