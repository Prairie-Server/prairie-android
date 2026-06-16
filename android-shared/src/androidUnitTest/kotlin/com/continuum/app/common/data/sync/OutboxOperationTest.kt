package com.continuum.app.common.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class OutboxOperationTest {
    @Test
    fun positionCoalesceKeyIncludesFileAndKind() {
        val op = OutboxOperation.setPosition(
            profileId = "p1", contentId = "c1", fileId = 7,
            positionSeconds = 30.0, durationSeconds = 3600.0, atMs = 1L,
        )
        assertEquals("SET_POSITION", op.kind)
        assertEquals("p1|c1|7|SET_POSITION", op.coalesceKey)
    }

    @Test
    fun favoriteAndRatingCoalesceSeparatelyPerContent() {
        val fav = OutboxOperation.setFavorite("p1", "c1", favorite = true, atMs = 1L)
        val rate = OutboxOperation.setRating("p1", "c1", rating = 5, atMs = 1L)
        assertEquals("p1|c1|SET_FAVORITE", fav.coalesceKey)
        assertEquals("p1|c1|SET_RATING", rate.coalesceKey)
    }

    @Test
    fun watchedOpCarriesValueInPayload() {
        val op = OutboxOperation.setWatched("p1", "c1", watched = true, atMs = 1L)
        assertEquals("SET_WATCHED", op.kind)
        assertEquals(true, OutboxOperation.decodeBooleanPayload(op.payloadJson))
    }

    @Test
    fun ratingPayloadSupportsNullForClear() {
        val op = OutboxOperation.setRating("p1", "c1", rating = null, atMs = 1L)
        assertEquals(null, OutboxOperation.decodeRatingPayload(op.payloadJson))
    }
}
