package com.continuum.app.repository

import com.continuum.app.model.settings.LibraryPlaybackPref
import com.continuum.app.model.settings.LibraryPlaybackPrefRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.LibraryPlaybackPrefsApi
import com.continuum.app.network.map

class LibraryPlaybackPrefsRepository(
    private val api: LibraryPlaybackPrefsApi,
) {
    suspend fun list(): ApiResult<Map<Int, LibraryPlaybackPref>> =
        api.list().map { response ->
            response.preferences.associateBy { it.libraryId }
        }

    suspend fun set(
        libraryId: Int,
        audioLanguage: String?,
        subtitleLanguage: String?,
        subtitleMode: String?,
        showForcedSubtitles: Boolean?,
    ): ApiResult<Unit> = api.set(
        libraryId = libraryId,
        request = LibraryPlaybackPrefRequest(
            audioLanguage = audioLanguage,
            subtitleLanguage = subtitleLanguage,
            subtitleMode = subtitleMode,
            showForcedSubtitles = showForcedSubtitles,
        ),
    )

    suspend fun delete(libraryId: Int): ApiResult<Unit> = api.delete(libraryId)
}
