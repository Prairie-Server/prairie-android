package com.continuum.app.common.player.subtitle

import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.SubtitleParser.OutputOptions

/**
 * Wraps [DefaultSubtitleParserFactory] so every emitted [CuesWithTiming]
 * gets [SubtitleOffsetHolder.getOffsetUs] added to its `startTimeUs`.
 * Reads the live offset on each emission — no need to recreate the parser
 * when the offset changes.
 */
@UnstableApi
class OffsetSubtitleParserFactory(
    private val holder: SubtitleOffsetHolder,
) : SubtitleParser.Factory {

    private val delegate = DefaultSubtitleParserFactory()

    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int =
        delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser =
        OffsetSubtitleParser(delegate.create(format), holder)
}

@UnstableApi
private class OffsetSubtitleParser(
    private val delegate: SubtitleParser,
    private val holder: SubtitleOffsetHolder,
) : SubtitleParser {

    override fun getCueReplacementBehavior(): Int = delegate.cueReplacementBehavior

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: OutputOptions,
        output: Consumer<CuesWithTiming>,
    ) {
        val offsetUs = holder.getOffsetUs()
        val shifted = Consumer<CuesWithTiming> { cues ->
            output.accept(
                CuesWithTiming(
                    cues.cues,
                    (cues.startTimeUs + offsetUs).coerceAtLeast(0L),
                    cues.durationUs,
                )
            )
        }
        delegate.parse(data, offset, length, outputOptions, shifted)
    }

    override fun reset() {
        delegate.reset()
    }
}
