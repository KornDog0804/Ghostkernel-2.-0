package com.github.soundpod.service

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Applies real gain to 16-bit PCM samples, bypassing ExoPlayer's Player.volume
 * (which is clamped to 0.0-1.0 and silently ignores anything above unity on most devices).
 *
 * Includes a soft limiter so pushing gain above 1.0x doesn't hard-clip into crackle/distortion -
 * this is the "clean boost" LoudnessEnhancer couldn't give us.
 */
@UnstableApi
class GainAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var gain: Float = 1.0f

    private val limiterThreshold = 0.9f

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val currentGain = gain
        if (currentGain == 1.0f) {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val outputBuffer = replaceOutputBuffer(remaining)
        val inShorts = inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val outShorts = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        val maxVal = Short.MAX_VALUE.toFloat()
        val limitStart = maxVal * limiterThreshold

        while (inShorts.hasRemaining()) {
            val sample = inShorts.get().toFloat() * currentGain
            val abs = kotlin.math.abs(sample)

            val limited = if (abs <= limitStart) {
                sample
            } else {
                val sign = if (sample < 0) -1f else 1f
                val over = abs - limitStart
                val headroom = maxVal - limitStart
                val compressed = limitStart + headroom * (1f - 1f / (1f + over / headroom))
                sign * compressed
            }

            val clamped = max(-maxVal, min(maxVal, limited))
            outShorts.put(clamped.toInt().toShort())
        }

        inputBuffer.position(inputBuffer.limit())
        outputBuffer.position(outShorts.position() * 2)
        outputBuffer.flip()
    }

    override fun onFlush() {
    }

    override fun onReset() {
        // Preserve GhostKernel's selected clean-boost level across
        // ExoPlayer resets and audio-format changes.
    }
}
