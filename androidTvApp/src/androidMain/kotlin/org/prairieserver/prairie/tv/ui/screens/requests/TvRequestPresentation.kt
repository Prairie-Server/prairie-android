package org.prairieserver.prairie.tv.ui.screens.requests

import org.prairieserver.prairie.model.request.MediaRequest
import org.prairieserver.prairie.model.request.RequestDiscoverySection
import org.prairieserver.prairie.model.request.RequestMediaResult
import org.prairieserver.prairie.model.request.RequestMediaType

internal fun List<RequestMediaResult>.filterTvRequestResults(): List<RequestMediaResult> =
    filter { it.mediaType.isSupportedTvRequestMediaType() }

internal fun List<RequestDiscoverySection>.filterTvRequestSections(): List<RequestDiscoverySection> =
    mapNotNull { section ->
        val visibleResults = section.results.filterTvRequestResults()
        section.copy(results = visibleResults).takeIf { visibleResults.isNotEmpty() }
    }

internal fun List<MediaRequest>.filterTvMediaRequests(): List<MediaRequest> =
    filter { it.mediaType.isSupportedTvRequestMediaType() }

private fun String.isSupportedTvRequestMediaType(): Boolean =
    trim().lowercase() in setOf(
        RequestMediaType.Movie,
        RequestMediaType.Series,
        RequestMediaType.Audiobook,
        "audiobooks",
    )
