package org.prairieserver.prairie.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PrairiePlayerFactorySubtitleParserTest {
    private val source = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/common/player/PrairiePlayerFactory.kt",
    ).readText()

    @Test
    fun sidecarSubtitleMediaSourcesUseNormalizingParserFactory() {
        assertTrue(
            source.contains("sidecarSubtitleParserFactory = OffsetSubtitleParserFactory(") &&
                source.contains("offsetUsProvider = subtitleOffsetHolder::getOffsetUs"),
            "PrairiePlayerFactory should compose the libass parser with Prairie's offset parser.",
        )
        assertTrue(
            source.contains("subtitleParserFactory = sidecarSubtitleParserFactory"),
            "Explicit sidecar media sources must use the source/player timeline correction.",
        )
    }

    @Test
    fun sidecarSubtitleCueTimesUseTheMountedPlaybackTimeline() {
        assertTrue(
            source.contains("timelineOffsetSeconds: Double = 0.0"),
            "buildMediaItem must accept the server-reanchored playback timeline offset.",
        )
        assertTrue(
            source.contains("subtitleOffsetHolder.setTimelineOffsetSeconds(timelineOffsetSeconds)"),
            "Subtitle cue times must be translated from source time to the mounted player timeline.",
        )
    }

    @Test
    fun libassUsesTheSharedParserExtractorRendererAndLifecycle() {
        assertTrue(source.contains("libassBridge.wrapExtractors("))
        assertTrue(source.contains("libassBridge.wrapRenderers("))
        assertTrue(source.contains("subtitleOffsetHolder::getUserOffsetUs"))
        assertTrue(source.contains("builder.build().also(libassBridge::initialize)"))
    }

    @Test
    fun timelineCorrectionIsLimitedToSidecars() {
        assertTrue(
            source.contains("embeddedSubtitleParserFactory = OffsetSubtitleParserFactory(") &&
                source.contains("offsetUsProvider = subtitleOffsetHolder::getUserOffsetUs"),
            "Embedded subtitle samples already share the mounted media timeline and must only use user sync.",
        )
        assertTrue(
            source.contains(".setSubtitleConfigurations(emptyList())") &&
                source.contains("subtitleConfigurations.mapTo(sources, ::createSubtitleMediaSource)"),
            "All sidecars must be split from the content source so their source timeline can be corrected independently.",
        )
    }

    @Test
    fun nowPlayingMetadataCarriesSecondaryTextAndDuration() {
        assertTrue(
            source.contains("durationMs: Long? = null"),
            "buildMediaItem should accept the normalized runtime so MediaSession queue metadata is not duration=0",
        )
        assertTrue(
            !source.contains("durationSeconds\n            .takeIf"),
            "duration conversion should live in VideoPlayerMediaSpec, not be duplicated in the factory",
        )
        assertTrue(
            source.contains("metadataBuilder.setArtist(it)"),
            "Android media controls compare artist, not subtitle, for the secondary now-playing line",
        )
        assertTrue(
            source.contains("metadataBuilder.setDurationMs(it)"),
            "MediaItem metadata must carry duration to keep MediaSession queue and current metadata in sync",
        )
    }

    @Test
    fun directPlaybackMediaItemsUseContainerMimeHints() {
        assertTrue(
            source.contains("container: String? = null"),
            "buildMediaItem should accept the selected file container so extensionless stream URLs do not rely on sniffing.",
        )
        assertTrue(
            source.contains("videoContainerMimeType(container)"),
            "direct/remux MediaItems should set a MIME hint from the selected file container.",
        )
    }

    @Test
    fun hlsMediaSourcesKeepTsPayloadFlagsWithoutDroppingSidecarSubtitleMerging() {
        assertTrue(
            source.contains("DefaultHlsExtractorFactory(") &&
                source.contains("DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS"),
            "HLS must get the same DTS TS payload-reader flag as progressive TS streams.",
        )
        assertTrue(
            source.contains("HlsMediaSource.Factory(dataSourceFactory)") &&
                source.contains(".setExtractorFactory(hlsExtractorFactory)"),
            "HLS playback must use the configured HLS extractor factory.",
        )
        assertTrue(
            source.contains("return MergingMediaSource(*sources.toTypedArray())") &&
                source.contains("SubtitleExtractor(") &&
                source.contains("subtitleParserFactory.create(outputFormat)"),
            "HLS sidecars must be merged without bypassing the configured DTS extractor or subtitle parser.",
        )
    }
}
