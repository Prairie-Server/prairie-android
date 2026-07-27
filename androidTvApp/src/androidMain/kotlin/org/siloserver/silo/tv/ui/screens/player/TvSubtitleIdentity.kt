package org.siloserver.silo.tv.ui.screens.player

import org.siloserver.silo.common.player.downloadedSubtitleArtifactTrackId
import org.siloserver.silo.common.player.isBitmapSubtitleCodecOrMime
import org.siloserver.silo.common.player.subtitleLabelIndicatesHearingImpaired
import org.siloserver.silo.model.playback.PlayerSubtitleInfo
import org.siloserver.silo.model.playback.SubtitleIdentity
import org.siloserver.silo.model.playback.SubtitleMediaIdentity
import org.siloserver.silo.playback.canonicalSubtitleCodecFamily
import org.siloserver.silo.playback.canonicalSubtitleLanguage

internal fun tvSubtitleIdentity(subtitle: PlayerSubtitleInfo): SubtitleIdentity {
    val source = subtitle.source?.trim()?.lowercase()
    val catalogSource = subtitle.catalogSource?.trim()?.lowercase()
    val downloaded = subtitle.downloadId != null ||
        source == "downloaded" ||
        catalogSource == "downloaded"
    val media = SubtitleMediaIdentity(
        trackId = subtitle.downloadId?.let(::downloadedSubtitleArtifactTrackId)
            ?: subtitle.mediaTrackId,
        label = subtitle.catalogLabel ?: subtitle.label,
        language = canonicalSubtitleLanguage(subtitle.language),
        codecFamily = canonicalSubtitleCodecFamily(
            subtitle.codec ?: subtitle.url
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('.', "")
                .takeIf(String::isNotBlank),
        ),
        forced = subtitle.forced,
        hearingImpaired = subtitleLabelIndicatesHearingImpaired(
            subtitle.catalogLabel ?: subtitle.label,
        ).takeIf { it },
    )
    if (downloaded) {
        val downloadedMedia = media.copy(
            forced = subtitle.forced ?: false,
            hearingImpaired = subtitleLabelIndicatesHearingImpaired(
                subtitle.catalogLabel ?: subtitle.label,
            ),
        )
        return subtitle.downloadId
            ?.let { SubtitleIdentity.Downloaded(it, downloadedMedia) }
            ?: SubtitleIdentity.LocalMedia3(downloadedMedia)
    }

    val embedded = subtitle.url.isBlank() &&
        (source == "embedded" || (source == null && catalogSource == "embedded"))
    if (embedded) return SubtitleIdentity.Embedded(subtitle.index, media)

    val external = source == "external" ||
        catalogSource == "external" ||
        source == "server_artifact" ||
        subtitle.url.isNotBlank()
    return if (external && isBitmapSubtitleCodecOrMime(media.codecFamily)) {
        SubtitleIdentity.ServerBurnIn(subtitle.index, media)
    } else {
        SubtitleIdentity.ServerSidecar(subtitle.index, media)
    }
}

internal fun tvSubtitleIdentity(track: PlayerTrackEntry): SubtitleIdentity =
    SubtitleIdentity.LocalMedia3(
        SubtitleMediaIdentity(
            trackId = track.trackId,
            label = track.displayLabel.ifBlank { track.label },
            language = canonicalSubtitleLanguage(track.language),
            codecFamily = canonicalSubtitleCodecFamily(track.codecOrMime),
            forced = track.isForced,
            hearingImpaired = track.isHearingImpaired,
        ),
    )
