package org.prairieserver.prairie.common.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DelayAudioProcessorTest {

    private fun makeStereo16Pcm44k() = AudioFormat(
        /* sampleRate = */ 44_100,
        /* channelCount = */ 2,
        /* encoding = */ C.ENCODING_PCM_16BIT,
    )

    private fun inputBytes(n: Int): ByteBuffer {
        val b = ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder())
        repeat(n) { b.put(((it % 127) + 1).toByte()) }  // non-zero pattern
        b.flip()
        return b
    }

    @Test
    fun `clamps delay to MAX_DELAY_MS on overflow`() {
        val p = DelayAudioProcessor()
        p.setDelayMs(99_999)
        p.configure(makeStereo16Pcm44k())
        p.flush(StreamMetadata.DEFAULT)
        assertEquals(DelayAudioProcessor.MAX_DELAY_MS, p.getActiveDelayMs())
    }

    @Test
    fun `clamps delay to MIN_DELAY_MS on underflow`() {
        val p = DelayAudioProcessor()
        p.setDelayMs(-99_999)
        p.configure(makeStereo16Pcm44k())
        p.flush(StreamMetadata.DEFAULT)
        assertEquals(DelayAudioProcessor.MIN_DELAY_MS, p.getActiveDelayMs())
    }

    @Test
    fun `zero delay is identity pass-through`() {
        val p = DelayAudioProcessor()
        p.configure(makeStereo16Pcm44k())
        p.flush(StreamMetadata.DEFAULT)
        val input = inputBytes(200)
        p.queueInput(input)
        val out = p.output
        assertEquals(200, out.remaining())
    }

    @Test
    fun `positive delay prepends silence frames`() {
        val p = DelayAudioProcessor()
        p.setDelayMs(10)  // 10ms @ 44.1kHz stereo 16-bit = 1764 bytes
        p.configure(makeStereo16Pcm44k())
        p.flush(StreamMetadata.DEFAULT)
        val input = inputBytes(4_000)

        // The head is paid off first, on its own. The input is deliberately
        // NOT consumed on this pass, so it is offered again.
        p.queueInput(input)
        val silence = p.output
        assertEquals(1_764, silence.remaining())
        repeat(1_764) { assertEquals(0.toByte(), silence.get()) }
        assertEquals(4_000, input.remaining())

        // Then the audio, unbroken.
        p.queueInput(input)
        assertEquals(4_000, p.output.remaining())
    }

    /**
     * The real streaming shape, which the single-large-buffer test above cannot
     * express: a delay spanning MANY decoder buffers.
     *
     * The processor used to emit silence and audio on every pass until the head
     * was consumed, so a delay longer than one buffer came out as
     * silence, audio, silence, audio... — audible as chopped, half-rate sound
     * for the length of the offset. Silence must come out in one unbroken run.
     */
    @Test
    fun `a delay longer than one buffer does not interleave audio`() {
        val p = DelayAudioProcessor()
        p.setDelayMs(100) // 17_640 bytes — far more than one buffer
        p.configure(makeStereo16Pcm44k())
        p.flush(StreamMetadata.DEFAULT)

        var silenceEmitted = 0
        var passes = 0
        while (silenceEmitted < 17_640 && passes < 200) {
            passes++
            val chunk = inputBytes(512)
            p.queueInput(chunk)
            val out = p.output
            val len = out.remaining()
            if (len == 0) continue
            // Every byte before the head is paid off must be silence. A single
            // non-zero byte here is the interleaving bug.
            repeat(len) { assertEquals(0.toByte(), out.get()) }
            silenceEmitted += len
            // Audio is held back while the head is outstanding.
            assertEquals(512, chunk.remaining())
        }
        assertEquals(17_640, silenceEmitted)

        // Head paid: audio now flows.
        val audio = inputBytes(512)
        p.queueInput(audio)
        assertEquals(512, p.output.remaining())
    }

    @Test
    fun `negative delay drops initial frames`() {
        val p = DelayAudioProcessor()
        p.setDelayMs(-10)  // drop ~1764 bytes
        p.configure(makeStereo16Pcm44k())
        p.flush(StreamMetadata.DEFAULT)
        val input = inputBytes(4_000)
        p.queueInput(input)
        val out = p.output
        assertEquals(4_000 - 1_764, out.remaining())
    }

    @Test
    fun `pass-through survives self-aliased buffer (regression — IAE on Pixel)`() {
        // Real-world crash: Media3's AudioProcessingPipeline can hand our
        // previously-handed-out output buffer back as the next input
        // buffer, at which point `outBuffer.put(inputBuffer)` is a self-
        // copy and Android's DirectByteBuffer.put throws
        // `IllegalArgumentException: The source buffer is this buffer`.
        // Simulate by feeding the processor's output back as input.
        val p = DelayAudioProcessor()
        p.configure(makeStereo16Pcm44k())
        p.flush(StreamMetadata.DEFAULT)

        p.queueInput(inputBytes(512))
        val firstOut = p.output
        assertEquals(512, firstOut.remaining())

        // Now feed firstOut back in. If the processor's internal buffer is
        // the same reference (which BaseAudioProcessor reuses), this is
        // the self-aliased path that crashed the Pixel.
        p.queueInput(firstOut)
        val secondOut = p.output
        assertEquals(512, secondOut.remaining())
    }

    @Test
    fun `re-flush re-applies new pending delay`() {
        val p = DelayAudioProcessor()
        p.setDelayMs(0)
        p.configure(makeStereo16Pcm44k())
        p.flush(StreamMetadata.DEFAULT)
        assertEquals(0, p.getActiveDelayMs())

        p.setDelayMs(50)
        p.flush(StreamMetadata.DEFAULT)
        assertEquals(50, p.getActiveDelayMs())
    }
}
