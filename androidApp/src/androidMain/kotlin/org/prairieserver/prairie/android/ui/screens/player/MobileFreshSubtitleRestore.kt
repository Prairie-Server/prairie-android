package org.prairieserver.prairie.android.ui.screens.player

import kotlinx.coroutines.CancellationException
import org.prairieserver.prairie.model.playback.PlayerSubtitleInfo
import org.prairieserver.prairie.model.playback.SubtitleIdentity
import org.prairieserver.prairie.model.playback.mergeDownloadedSubtitles
import org.prairieserver.prairie.model.subtitles.DownloadedSubtitlesResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.playback.decodeSubtitleIdentityPreference
import org.prairieserver.prairie.playback.resolveMountedSubtitleOrdinal

internal data class MobileFreshSubtitleRestore(
    val subtitleTracks: List<PlayerSubtitleInfo>,
    val persistedPreferencePresent: Boolean,
    val persistedSelectionOrdinal: Int?,
    val persistedSelectionIdentity: SubtitleIdentity?,
)

internal suspend fun prepareMobileFreshSubtitleRestore(
    mediaFileId: Int?,
    mountedSubtitles: List<PlayerSubtitleInfo>,
    sessionId: String,
    serverUrl: String,
    persistedPreference: String?,
    authoritativeInventory: Boolean = false,
    loadDownloadedSubtitles: suspend (Int) -> ApiResult<DownloadedSubtitlesResponse>,
): MobileFreshSubtitleRestore {
    val downloaded = if (authoritativeInventory || mediaFileId == null) {
        emptyList()
    } else {
        try {
            when (val result = loadDownloadedSubtitles(mediaFileId)) {
                is ApiResult.Success -> result.data.subtitles
                else -> emptyList()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }
    val subtitleTracks = if (authoritativeInventory) {
        mountedSubtitles
    } else {
        mergeDownloadedSubtitles(
            existing = mountedSubtitles,
            downloaded = downloaded,
            sessionId = sessionId,
            serverUrl = serverUrl,
        )
    }
    val preference = persistedPreference?.trim()?.takeIf(String::isNotEmpty)
    val persistedIdentity = decodeSubtitleIdentityPreference(preference)
    val persistedOrdinal = persistedIdentity
        ?.let { identity -> resolveMobileSubtitleOrdinal(identity, subtitleTracks) }
        ?: resolveMountedSubtitleOrdinal(subtitleTracks, preference)
    val resolvedIdentity = when (persistedOrdinal) {
        null -> null
        -1 -> SubtitleIdentity.Off
        else -> subtitleTracks
            .getOrNull(persistedOrdinal)
            ?.let(::mobileSubtitleIdentity)
    }

    return MobileFreshSubtitleRestore(
        subtitleTracks = subtitleTracks,
        persistedPreferencePresent = preference != null,
        persistedSelectionOrdinal = persistedOrdinal,
        persistedSelectionIdentity = resolvedIdentity,
    )
}
