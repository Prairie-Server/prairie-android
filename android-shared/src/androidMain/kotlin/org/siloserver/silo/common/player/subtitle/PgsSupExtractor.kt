package org.siloserver.silo.common.player.subtitle

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.CueEncoder
import androidx.media3.extractor.text.SubtitleParser
import java.io.EOFException

/**
 * Extractor for a raw PGS elementary stream (`.sup`), which Media3 has no
 * extractor for.
 *
 * Media3 parses PGS but only as one *display set* at a time, timestamped by
 * whatever container carried it — inside Matroska the block supplies the time
 * and the payload is bare `[type][len][payload]` segments. A `.sup` file is the
 * same segments with a 10-byte `PG`+PTS+DTS prefix on each and no container at
 * all, so handing the whole file to the parser (which is what SubtitleExtractor
 * does) yields nothing: it sees one enormous sample with no timing. That is why
 * a mounted `.sup` sidecar selected cleanly and drew nothing.
 *
 * So: frame the stream into display sets, strip each segment's prefix back to
 * the container-shaped form the parser expects, and emit one parsed cue sample
 * per set at the PTS the prefix carried.
 *
 * The parser is injected rather than constructed here so the caller's offset
 * wrapper still applies — subtitle sync and the re-anchor delta have to reach
 * these cues like any other.
 */
@UnstableApi
class PgsSupExtractor(
    private val parserFactory: SubtitleParser.Factory,
    /**
     * Subtitle sync + the server re-anchor delta, in microseconds. Applied to
     * the sample timestamp here because PGS carries no cue-relative time for
     * the parser's own offset wrapper to shift — it reports TIME_UNSET, and
     * adding that produced timestamps ~9.2e15 ms into the future.
     */
    private val offsetUsProvider: () -> Long,
    /**
     * The sidecar's own format. Emitting a freshly built one instead drops the
     * stable track id, language and label the mount resolver matches on — the
     * track then appears as `mounted=[1:]` and the pick dies on its deadline.
     */
    private val sourceFormat: Format,
) : Extractor {

    private val cueEncoder = CueEncoder()
    private val headerScratch = ByteArray(SEGMENT_HEADER_SIZE)

    private var trackOutput: TrackOutput? = null
    private var parser: SubtitleParser? = null

    /** Segments of the display set being accumulated, already prefix-stripped. */
    private var displaySet = ByteArrayBuilder()
    private var displaySetTimeUs = C.TIME_UNSET
    private var displaySetSegmentCount = 0
    private var failedClosed = false
    private var emittedSets = 0
    private var emittedCues = 0

    override fun sniff(input: ExtractorInput): Boolean {
        val probe = ByteArray(2)
        return try {
            input.peekFully(probe, 0, probe.size)
            probe[0] == MAGIC_P && probe[1] == MAGIC_G
        } catch (_: EOFException) {
            false
        }
    }

    override fun init(output: ExtractorOutput) {
        val track = output.track(0, C.TRACK_TYPE_TEXT)
        parser = parserFactory.create(sourceFormat)
        // Everything except the sample mime carries over: id, language, label,
        // selection and role flags are what identify this track downstream.
        track.format(
            sourceFormat.buildUpon()
                .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
                .setCodecs(sourceFormat.sampleMimeType)
                .setCueReplacementBehavior(
                    parserFactory.getCueReplacementBehavior(sourceFormat),
                )
                .build(),
        )
        trackOutput = track
        output.endTracks()
        // The cues are held in the sample queue once read, so backward seeks are
        // served from memory; a seek before the read completes just restarts it.
        output.seekMap(SeekMap.Unseekable(C.TIME_UNSET))
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (failedClosed) return Extractor.RESULT_END_OF_INPUT
        val output = trackOutput ?: return Extractor.RESULT_END_OF_INPUT
        try {
            input.readFully(headerScratch, 0, SEGMENT_HEADER_SIZE)
        } catch (_: EOFException) {
            discardPendingDisplaySet()
            return Extractor.RESULT_END_OF_INPUT
        }
        val header = ParsableByteArray(headerScratch)
        if (header.readUnsignedByte() != MAGIC_P_INT || header.readUnsignedByte() != MAGIC_G_INT) {
            // Resynchronising mid-stream would mean guessing where the next
            // segment starts; a truncated or mislabelled artifact is better
            // reported as end-of-input than turned into fabricated cues.
            discardPendingDisplaySet()
            return Extractor.RESULT_END_OF_INPUT
        }
        val pts90kHz = header.readUnsignedInt()
        header.skipBytes(4) // DTS: unused, presentation time is what cues need.
        val segmentType = header.readUnsignedByte()
        val segmentLength = header.readUnsignedShort()

        if (displaySetSegmentCount >= MAX_DISPLAY_SET_SEGMENTS ||
            displaySet.size + CONTAINER_SEGMENT_HEADER_SIZE + segmentLength > MAX_DISPLAY_SET_BYTES
        ) {
            failClosed()
            return Extractor.RESULT_END_OF_INPUT
        }

        val payload = ByteArray(segmentLength)
        if (segmentLength > 0) {
            try {
                input.readFully(payload, 0, segmentLength)
            } catch (_: EOFException) {
                discardPendingDisplaySet()
                return Extractor.RESULT_END_OF_INPUT
            }
        }

        if (segmentType == SEGMENT_TYPE_END) {
            // The END section stays in the buffer: Media3's PgsParser builds the
            // cue when it reads one. Stripping it as pure framing produced a set
            // the parser accepted and returned zero cues for — a mounted,
            // selected, correctly timed track that drew nothing.
            appendSegment(segmentType, segmentLength, payload)
            flushDisplaySet(output)
            return Extractor.RESULT_CONTINUE
        }

        // First segment of a set carries the time the whole set is shown at.
        if (displaySet.isEmpty()) {
            displaySetTimeUs = pts90kHz * C.MICROS_PER_SECOND / PTS_CLOCK_HZ
        }
        appendSegment(segmentType, segmentLength, payload)
        return Extractor.RESULT_CONTINUE
    }

    /** Back to the container-shaped form the parser reads: [type][length][payload]. */
    private fun appendSegment(segmentType: Int, segmentLength: Int, payload: ByteArray) {
        displaySet.append(segmentType.toByte())
        displaySet.append((segmentLength shr 8 and 0xFF).toByte())
        displaySet.append((segmentLength and 0xFF).toByte())
        displaySet.append(payload)
        displaySetSegmentCount += 1
    }

    /**
     * A set is only complete once its END segment arrives, so a stream that
     * stops mid-set has nothing renderable — parsing what arrived would risk a
     * half-built caption from a composition with no bitmap yet.
     */
    private fun discardPendingDisplaySet() {
        displaySet = ByteArrayBuilder()
        displaySetTimeUs = C.TIME_UNSET
        displaySetSegmentCount = 0
    }

    private fun failClosed() {
        discardPendingDisplaySet()
        failedClosed = true
    }

    private fun flushDisplaySet(output: TrackOutput) {
        if (displaySet.isEmpty()) return
        val bytes = displaySet.toByteArray()
        val timeUs = displaySetTimeUs
        displaySet = ByteArrayBuilder()
        displaySetTimeUs = C.TIME_UNSET
        displaySetSegmentCount = 0
        val activeParser = parser ?: return
        if (timeUs == C.TIME_UNSET) return

        emittedSets++
        if (emittedSets <= 3 || emittedSets % 200 == 0) {
            org.siloserver.silo.common.player.SubDiag.log(
                "SUP set=$emittedSets t=${timeUs / 1000}ms bytes=${bytes.size}",
            )
        }
        activeParser.parse(
            bytes,
            0,
            bytes.size,
            SubtitleParser.OutputOptions.allCues(),
        ) { cues ->
            emittedCues += cues.cues.size
            if (emittedCues <= 3) {
                org.siloserver.silo.common.player.SubDiag.log(
                    "SUP cue n=${cues.cues.size} at=${(timeUs + offsetUsProvider()) / 1000}ms",
                )
            }
            // Duration stays unset: PGS ends a caption with the next display
            // set, and the parser's REPLACE behaviour already means a new
            // sample supersedes the last one.
            val encoded = cueEncoder.encode(cues.cues, C.TIME_UNSET)
            val data = ParsableByteArray(encoded)
            output.sampleData(data, encoded.size)
            output.sampleMetadata(
                (timeUs + offsetUsProvider()).coerceAtLeast(0L),
                C.BUFFER_FLAG_KEY_FRAME,
                encoded.size,
                0,
                null,
            )
        }
    }

    override fun seek(position: Long, timeUs: Long) {
        displaySet = ByteArrayBuilder()
        displaySetTimeUs = C.TIME_UNSET
        displaySetSegmentCount = 0
        failedClosed = false
        parser?.reset()
    }

    override fun release() {
        parser = null
    }

    /** Grow-on-append byte buffer; a display set is a handful of small segments. */
    private class ByteArrayBuilder {
        private var buffer = ByteArray(INITIAL_CAPACITY)
        var size = 0
            private set

        fun isEmpty(): Boolean = size == 0

        fun append(value: Byte) {
            ensure(1)
            buffer[size++] = value
        }

        fun append(values: ByteArray) {
            if (values.isEmpty()) return
            ensure(values.size)
            values.copyInto(buffer, size)
            size += values.size
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)

        private fun ensure(extra: Int) {
            if (size + extra <= buffer.size) return
            var capacity = buffer.size
            while (capacity < size + extra) capacity *= 2
            buffer = buffer.copyOf(capacity)
        }

        private companion object {
            const val INITIAL_CAPACITY = 4096
        }
    }

    companion object {
        /** `PG` magic, 4-byte PTS, 4-byte DTS, type, 2-byte length. */
        const val SEGMENT_HEADER_SIZE = 13
        const val SEGMENT_TYPE_END = 0x80
        private const val CONTAINER_SEGMENT_HEADER_SIZE = 3
        private const val MAX_DISPLAY_SET_BYTES = 16 * 1024 * 1024
        private const val MAX_DISPLAY_SET_SEGMENTS = 512
        private const val PTS_CLOCK_HZ = 90_000L
        private const val MAGIC_P_INT = 0x50
        private const val MAGIC_G_INT = 0x47
        private const val MAGIC_P = MAGIC_P_INT.toByte()
        private const val MAGIC_G = MAGIC_G_INT.toByte()
    }
}
