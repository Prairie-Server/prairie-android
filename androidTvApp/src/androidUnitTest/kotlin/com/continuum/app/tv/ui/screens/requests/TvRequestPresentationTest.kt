package com.continuum.app.tv.ui.screens.requests

import com.continuum.app.model.request.RequestDiscoverySection
import com.continuum.app.model.request.MediaRequest
import com.continuum.app.model.request.RequestMediaResult
import com.continuum.app.model.request.RequestMediaType
import com.continuum.app.model.request.RequestOutcome
import com.continuum.app.model.request.RequestStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class TvRequestPresentationTest {

    @Test
    fun tvRequestResultsIncludeVideoAndAudiobooksButNotEbooks() {
        val results = listOf(
            requestResult(title = "The Signal", mediaType = RequestMediaType.Movie),
            requestResult(title = "The Expanse", mediaType = RequestMediaType.Series),
            requestResult(title = "Project Hail Mary", mediaType = "ebook"),
            requestResult(title = "Dungeon Crawler Carl", mediaType = "audiobook"),
            requestResult(title = "Mystery", mediaType = "unknown"),
        )

        val visible = results.filterTvRequestResults()

        assertEquals(listOf("The Signal", "The Expanse", "Dungeon Crawler Carl"), visible.map { it.title })
    }

    @Test
    fun tvRequestSectionsDropUnsupportedItemsAndEmptySections() {
        val sections = listOf(
            RequestDiscoverySection(
                key = "popular",
                title = "Popular",
                results = listOf(
                    requestResult(title = "Popular Book", mediaType = "ebook"),
                    requestResult(title = "Popular Movie", mediaType = RequestMediaType.Movie),
                ),
            ),
            RequestDiscoverySection(
                key = "books",
                title = "Books",
                results = listOf(requestResult(title = "Only Book", mediaType = "ebook")),
            ),
        )

        val visible = sections.filterTvRequestSections()

        assertEquals(listOf("popular"), visible.map { it.key })
        assertEquals(listOf("Popular Movie"), visible.single().results.map { it.title })
    }

    @Test
    fun tvMyRequestsIncludeVideoAndAudiobooksButNotEbooks() {
        val requests = listOf(
            mediaRequest(title = "Movie Request", mediaType = RequestMediaType.Movie),
            mediaRequest(title = "Series Request", mediaType = RequestMediaType.Series),
            mediaRequest(title = "Ebook Request", mediaType = "ebook"),
            mediaRequest(title = "Audio Request", mediaType = "audiobook"),
        )

        val visible = requests.filterTvMediaRequests()

        assertEquals(listOf("Movie Request", "Series Request", "Audio Request"), visible.map { it.title })
    }

    private fun requestResult(
        title: String,
        mediaType: String,
    ): RequestMediaResult = RequestMediaResult(
        mediaType = mediaType,
        tmdbId = title.hashCode(),
        title = title,
    )

    private fun mediaRequest(
        title: String,
        mediaType: String,
    ): MediaRequest = MediaRequest(
        id = title,
        mediaType = mediaType,
        tmdbId = title.hashCode(),
        title = title,
        status = RequestStatus.Pending,
        outcome = RequestOutcome.Active,
        createdAt = "2026-06-09T00:00:00Z",
        updatedAt = "2026-06-09T00:00:00Z",
    )
}
