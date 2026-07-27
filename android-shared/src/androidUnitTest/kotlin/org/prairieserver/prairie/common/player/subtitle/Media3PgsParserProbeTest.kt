package org.prairieserver.prairie.common.player.subtitle

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Probe against a REAL display set pulled from a server `.sup` extract
 * (PCS+WDS+PDS+ODS, 34 KB, the first caption of SNW S01E03's English track).
 * Everything upstream of the parser is verified, so this answers the only
 * remaining question: does Media3 turn this into a cue?
 */
@RunWith(RobolectricTestRunner::class)
class Media3PgsParserProbeTest {
    @Test
    fun reportWhatMedia3MakesOfARealDisplaySet() {
        val bytes = javaClass.classLoader!!
            .getResourceAsStream("pgs/display-set-0.bin")!!
            .readBytes()
        println("PROBE input bytes=${bytes.size}")

        val format = Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_PGS).build()
        val parser = DefaultSubtitleParserFactory().create(format)
        println("PROBE parser=${parser.javaClass.name}")

        // Hypothesis: the parser builds the cue when it sees the END section,
        // which the extractor currently strips. Try both shapes.
        val withEnd = bytes + byteArrayOf(0x80.toByte(), 0, 0)
        var endGroups = 0
        var endCues = 0
        DefaultSubtitleParserFactory().create(format)
            .parse(withEnd, 0, withEnd.size, SubtitleParser.OutputOptions.allCues()) { out ->
                endGroups++
                endCues += out.cues.size
                out.cues.forEach { cue ->
                    println("PROBE withEnd cue bitmap=${cue.bitmap?.width}x${cue.bitmap?.height}")
                }
            }
        println("PROBE withEnd groups=$endGroups cues=$endCues")

        var groups = 0
        var cues = 0
        parser.parse(bytes, 0, bytes.size, SubtitleParser.OutputOptions.allCues()) { out ->
            groups++
            cues += out.cues.size
            out.cues.forEach { cue ->
                println(
                    "PROBE cue bitmap=${cue.bitmap?.width}x${cue.bitmap?.height} " +
                        "pos=${cue.position} line=${cue.line} size=${cue.size}",
                )
            }
        }
        println("PROBE groups=$groups cues=$cues")
    }
}
