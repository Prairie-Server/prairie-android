package org.siloserver.silo.playback

import org.siloserver.silo.model.catalog.AudioTrack
import org.siloserver.silo.model.catalog.SubtitleTrack
import org.siloserver.silo.model.playback.PlayerSubtitleInfo

const val SUBTITLE_OFF_FINGERPRINT = "off"

fun audioTrackFingerprint(track: AudioTrack): String =
    trackSelectionFingerprint(
        index = track.index,
        language = track.language,
        codec = track.codec,
        title = track.title,
        forced = track.isDefault,
    )

fun subtitleTrackFingerprint(track: SubtitleTrack): String =
    trackSelectionFingerprint(
        index = track.index,
        language = track.language,
        codec = track.codec,
        title = track.title,
        forced = track.forced,
    )

fun subtitleTrackFingerprint(track: PlayerSubtitleInfo): String =
    trackSelectionFingerprint(
        index = track.index,
        language = track.language,
        codec = track.codec,
        title = track.label,
        forced = track.forced == true,
    )

fun resolveAudioTrackOrdinal(
    tracks: List<AudioTrack>,
    fingerprint: String?,
): Int? {
    val saved = fingerprint.normalizedFingerprintOrNull() ?: return null
    return tracks.indexOfFirst { audioTrackFingerprint(it) == saved }
        .takeIf { it >= 0 }
}

fun resolveSubtitleTrackOrdinal(
    tracks: List<SubtitleTrack>,
    fingerprint: String?,
): Int? {
    val saved = fingerprint.normalizedFingerprintOrNull() ?: return null
    if (saved == SUBTITLE_OFF_FINGERPRINT) return -1
    return tracks.indexOfFirst { subtitleTrackFingerprint(it) == saved }
        .takeIf { it >= 0 }
}

fun trackSelectionFingerprint(
    index: Int,
    language: String?,
    codec: String?,
    title: String?,
    forced: Boolean,
): String = listOf(
    index.toString(),
    language.normalizedTrackField(lowercase = true),
    codec.normalizedTrackField(lowercase = true),
    title.normalizedTrackField(lowercase = false),
    forced.toString(),
).joinToString("|")

private fun String?.normalizedTrackField(lowercase: Boolean): String {
    val value = this
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        .orEmpty()
    return if (lowercase) value.lowercase() else value
}

private fun String?.normalizedFingerprintOrNull(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }
