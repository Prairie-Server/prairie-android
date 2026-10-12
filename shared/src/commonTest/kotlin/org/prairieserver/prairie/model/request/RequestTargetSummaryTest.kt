package org.prairieserver.prairie.model.request

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RequestTargetSummaryTest {
    private fun target(quality: String, status: String) = RequestTarget(
        id = quality + status,
        requestId = "r",
        quality = quality,
        status = status,
        createdAt = "2026-10-01T00:00:00Z",
        updatedAt = "2026-10-01T00:00:00Z",
    )

    @Test
    fun textIsNullForSingleTargetRequests() {
        assertNull(RequestTargetSummary.text(emptyList()))
        assertNull(RequestTargetSummary.text(listOf(target("1080p", RequestStatus.Downloading))))
    }

    @Test
    fun textNamesEachQualityState() {
        val targets = listOf(
            target("1080p", RequestStatus.Downloading),
            target("4K", RequestStatus.Queued),
            target("720p", RequestStatus.Approved),
            target("480p", RequestStatus.Pending),
            target("360p", RequestStatus.Completed),
            target("240p", RequestStatus.Failed),
        )
        assertEquals(
            "1080p downloading · 4K queued · 720p queued · 480p pending · 360p done · 240p failed",
            RequestTargetSummary.text(targets),
        )
    }

    @Test
    fun textSkipsBlankQualitiesAndUnknownStatuses() {
        assertEquals(
            "1080p done",
            RequestTargetSummary.text(listOf(target("1080p", RequestStatus.Completed), target("", RequestStatus.Completed), target("4K", "weird"))),
        )
        assertNull(RequestTargetSummary.text(listOf(target("", RequestStatus.Completed), target("4K", "weird"))))
    }

    @Test
    fun qualitiesListsNonBlankQualities() {
        assertEquals("1080p · 4K", RequestTargetSummary.qualities(listOf(target("1080p", "x"), target("", "x"), target("4K", "x"))))
        assertNull(RequestTargetSummary.qualities(listOf(target("", "x"))))
        assertNull(RequestTargetSummary.qualities(emptyList()))
    }
}
