package org.prairieserver.prairie.common.player.subtitle

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import org.junit.Test

/**
 * Probe, not a behavioural assertion: does the bundled Media3 parse a raw PGS
 * sidecar? Mounting `.sup` client-side only works if it does.
 */
class Media3PgsSupportProbeTest {
    @Test
    fun reportBitmapSubtitleSupport() {
        val factory = DefaultSubtitleParserFactory()
        for (mime in listOf(
            MimeTypes.APPLICATION_PGS,
            MimeTypes.APPLICATION_VOBSUB,
            MimeTypes.APPLICATION_DVBSUBS,
            MimeTypes.APPLICATION_SUBRIP,
        )) {
            val format = Format.Builder().setSampleMimeType(mime).build()
            println("PROBE $mime supported=${factory.supportsFormat(format)}")
        }
    }
}
