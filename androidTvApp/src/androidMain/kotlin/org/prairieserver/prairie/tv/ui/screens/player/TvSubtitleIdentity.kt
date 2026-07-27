package org.prairieserver.prairie.tv.ui.screens.player

import org.prairieserver.prairie.common.player.downloadedSubtitleArtifactTrackId
import org.prairieserver.prairie.common.player.isBitmapSubtitleCodecOrMime
import org.prairieserver.prairie.common.player.subtitleLabelIndicatesHearingImpaired
import org.prairieserver.prairie.model.playback.PlayerSubtitleInfo
import org.prairieserver.prairie.model.playback.SubtitleIdentity
import org.prairieserver.prairie.model.playback.SubtitleMediaIdentity
import org.prairieserver.prairie.playback.canonicalSubtitleCodecFamily
import org.prairieserver.prairie.playback.isClientMountableBitmapCodecFamily
import org.prairieserver.prairie.playback.canonicalSubtitleLanguage

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
    if (embedded) {
        // A bitmap track cannot become a Media3 TEXT sidecar, so the staged
        // transaction must not demand one — that is what made the server's
        // correct BURN_IN plan get rejected as "unexpectedly burned in the
        // mounted subtitle" and the pick silently revert to Off.
        //
        // PGS is the exception: the server raw-serves it as a `.sup` sidecar
        // which SubtitleManager mounts, so it materialises like extracted text.
        // VobSub and DVB have no sidecar route and always burn in.
        return if (
            isBitmapSubtitleCodecOrMime(media.codecFamily) &&
            !isClientMountableBitmapCodecFamily(media.codecFamily)
        ) {
            SubtitleIdentity.ServerBurnIn(subtitle.index, media)
        } else {
            SubtitleIdentity.Embedded(subtitle.index, media)
        }
    }

    val external = source == "external" ||
        catalogSource == "external" ||
        source == "server_artifact" ||
        subtitle.url.isNotBlank()
    val mountableBitmapArtifact = subtitle.url.isNotBlank() &&
        isClientMountableBitmapCodecFamily(media.codecFamily)
    return if (
        external &&
        isBitmapSubtitleCodecOrMime(media.codecFamily) &&
        !mountableBitmapArtifact
    ) {
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
