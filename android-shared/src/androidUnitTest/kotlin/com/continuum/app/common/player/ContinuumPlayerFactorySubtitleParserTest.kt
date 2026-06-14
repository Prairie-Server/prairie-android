package com.continuum.app.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ContinuumPlayerFactorySubtitleParserTest {
    private val source = File(
        "src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt",
    ).readText()

    @Test
    fun sidecarSubtitleMediaSourcesUseNormalizingParserFactory() {
        assertTrue(
            source.contains("val subtitleParserFactory = OffsetSubtitleParserFactory(subtitleOffsetHolder)"),
            "ContinuumPlayerFactory should share one OffsetSubtitleParserFactory instance.",
        )
        assertTrue(
            source.contains(".setSubtitleParserFactory(subtitleParserFactory)"),
            "DefaultMediaSourceFactory must use the normalizing parser for sidecar subtitles.",
        )
    }
}
