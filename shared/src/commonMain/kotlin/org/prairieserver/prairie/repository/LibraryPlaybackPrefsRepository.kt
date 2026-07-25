package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.settings.LibraryPlaybackPref
import org.prairieserver.prairie.model.settings.LibraryPlaybackPrefRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.LibraryPlaybackPrefsApi
import org.prairieserver.prairie.network.map

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
