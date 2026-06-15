package com.continuum.app.common.player.video

import com.continuum.app.model.catalog.TimeRange
import com.continuum.app.model.catalog.VersionChapter
import com.continuum.app.model.playback.PlayMethod
import com.continuum.app.model.playback.PlayerSubtitleInfo

sealed interface VideoPlaybackStartResult {
    data class Ready(
        val contentId: String,
        val fileId: Int?,
        val streamUrl: String,
        val playMethod: PlayMethod,
        val container: String? = null,
        val title: String,
        val subtitle: String?,
        val artworkUrl: String?,
        val startPositionSeconds: Double,
        val sessionId: String? = null,
        val serverUrl: String = "",
        val accessToken: String = "",
        val mediaFileId: Int? = null,
        val audioTrackIndex: Int = 0,
        val durationSeconds: Double = 0.0,
        val subtitleUrls: List<PlayerSubtitleInfo> = emptyList(),
        val preferredAudioLanguage: String? = null,
        val preferredTextLanguage: String? = null,
        val intro: TimeRange? = null,
        val credits: TimeRange? = null,
        val chapters: List<VersionChapter> = emptyList(),
    ) : VideoPlaybackStartResult

    data class Error(
        val contentId: String,
        val message: String,
        val cause: Throwable? = null,
    ) : VideoPlaybackStartResult
}
