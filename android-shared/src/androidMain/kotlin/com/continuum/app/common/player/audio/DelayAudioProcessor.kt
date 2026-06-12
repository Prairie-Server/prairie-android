package com.continuum.app.common.player.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 [AudioProcessor] that adds a configurable head delay (positive →
 * prepend silence; negative → drop initial frames). Hot-reconfigurable
 * via [setDelayMs]; pending change applied on next [flush].
 *
 * Reused by the HUD Audio pane's delay slider (E). The currently active
 * value is mirrored from the per-profile `AudioSyncMs` preference.
 *
 * Range is clamped to ±5000ms to match the existing `setAudioSyncMs(value)`
 * coercion in `AndroidPlayerSettingsStore` (mirrors iOS's `audioSyncMs`
 * range — `iosApp/Screens/Player/Sheets/PlayerSettingsSheet.swift:269`).
 */
@UnstableApi
class DelayAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var pendingDelayMs: Int = 0

    /** Active delay applied to the current stream, captured at flush. */
    @Volatile
    private var activeDelayMs: Int = 0

    /** Cached bytes-per-second of the active format. */
    @Volatile
    private var bytesPerSecond: Int = 0

    /** Remaining bytes of silence to prepend (positive delay) or drop (negative). */
    @Volatile
    private var remainingHeadBytes: Int = 0

    /**
     * Reusable scratch array used to copy bytes from the input buffer into the
     * output buffer. We *must* round-trip through a byte[] because Media3's
     * AudioProcessingPipeline can pass our own previously-handed-out output
     * buffer back as the next input buffer; `ByteBuffer.put(ByteBuffer)`
     * rejects self-copies (`The source buffer is this buffer` IAE on Android).
     * Grown on demand, never shrunk.
     */
    private var scratch: ByteArray = ByteArray(0)

    private fun copyThroughScratch(input: ByteBuffer, output: ByteBuffer, count: Int) {
        if (count == 0) return
        if (scratch.size < count) scratch = ByteArray(count)
        // Capture output's write cursor before the read — when input === output
        // (the Media3 self-aliased case), `input.get` advances the shared
        // position and the subsequent `output.put` would otherwise write at
        // that advanced offset (doubling the buffer length). Restoring puts
        // the write back at the correct cursor.
        val outputStartPos = output.position()
        input.get(scratch, 0, count)
        output.position(outputStartPos)
        output.put(scratch, 0, count)
    }

    /**
     * Range: [MIN_DELAY_MS, MAX_DELAY_MS]. Out-of-range values clamp.
     * Takes effect on the next [flush] (caller typically does a
     * `player.seekTo(player.currentPosition)` to apply mid-playback).
     */
    fun setDelayMs(delayMs: Int) {
        pendingDelayMs = delayMs.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    fun getActiveDelayMs(): Int = activeDelayMs

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Output format == input; we don't resample or rechannel.
        // bytesPerFrame already accounts for channelCount * sampleSize, so
        // bytesPerSecond is simply sampleRate * bytesPerFrame.
        bytesPerSecond = inputAudioFormat.sampleRate * inputAudioFormat.bytesPerFrame
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: StreamMetadata) {
        activeDelayMs = pendingDelayMs
        remainingHeadBytes = if (bytesPerSecond > 0) {
            (activeDelayMs.toLong() * bytesPerSecond / 1_000L).toInt()
        } else 0
        // remainingHeadBytes is positive (silence-to-prepend) or negative
        // (bytes-to-drop). Computed once at flush; consumed during queueInput.
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        when {
            remainingHeadBytes > 0 -> {
                // Positive delay: emit silence equal to remaining head bytes,
                // then pass input through.
                val inputLen = inputBuffer.remaining()
                val silenceLen = minOf(remainingHeadBytes, inputLen)
                val outBuffer = replaceOutputBuffer(silenceLen + inputLen)
                outBuffer.order(ByteOrder.nativeOrder())
                // Emit silence.
                val silenceBytes = ByteArray(silenceLen)  // zero-initialized
                outBuffer.put(silenceBytes)
                // Then emit input — via scratch to handle the self-aliased
                // buffer case (see [scratch]).
                copyThroughScratch(inputBuffer, outBuffer, inputLen)
                outBuffer.flip()
                remainingHeadBytes -= silenceLen
            }
            remainingHeadBytes < 0 -> {
                // Negative delay: drop |remainingHeadBytes| input bytes.
                val dropLen = minOf(-remainingHeadBytes, inputBuffer.remaining())
                inputBuffer.position(inputBuffer.position() + dropLen)
                remainingHeadBytes += dropLen
                val remaining = inputBuffer.remaining()
                if (remaining > 0) {
                    val outBuffer = replaceOutputBuffer(remaining)
                    outBuffer.order(ByteOrder.nativeOrder())
                    copyThroughScratch(inputBuffer, outBuffer, remaining)
                    outBuffer.flip()
                }
            }
            else -> {
                // No delay (or fully consumed): pass through.
                val remaining = inputBuffer.remaining()
                val outBuffer = replaceOutputBuffer(remaining)
                outBuffer.order(ByteOrder.nativeOrder())
                copyThroughScratch(inputBuffer, outBuffer, remaining)
                outBuffer.flip()
            }
        }
    }

    companion object {
        const val MIN_DELAY_MS = -5000
        const val MAX_DELAY_MS = 5000
    }
}
