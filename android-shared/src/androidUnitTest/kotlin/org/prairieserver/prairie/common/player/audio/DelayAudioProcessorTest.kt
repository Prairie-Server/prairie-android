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
        p.queueInput(input)
        val out = p.output
        // Expect 1764 silence bytes + 4000 input bytes
        assertEquals(5_764, out.remaining())
        // First 1764 bytes should be zero
        repeat(1_764) {
            assertEquals(0.toByte(), out.get())
        }
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
