package org.siloserver.silo.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SiloPlayerFactorySubtitleParserTest {
    private val source = File(
        "src/androidMain/kotlin/org/siloserver/silo/common/player/SiloPlayerFactory.kt",
    ).readText()

    @Test
    fun sidecarSubtitleMediaSourcesUseNormalizingParserFactory() {
        assertTrue(
            source.contains("val subtitleParserFactory = OffsetSubtitleParserFactory(subtitleOffsetHolder)"),
            "SiloPlayerFactory should share one OffsetSubtitleParserFactory instance.",
        )
        assertTrue(
            source.contains(".setSubtitleParserFactory(subtitleParserFactory)"),
            "DefaultMediaSourceFactory must use the normalizing parser for sidecar subtitles.",
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
}
