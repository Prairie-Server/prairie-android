package com.continuum.app.tv.ui.screens.requests

import com.continuum.app.model.request.MediaRequest
import com.continuum.app.model.request.RequestDiscoverySection
import com.continuum.app.model.request.RequestMediaResult
import com.continuum.app.model.request.RequestMediaType

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
