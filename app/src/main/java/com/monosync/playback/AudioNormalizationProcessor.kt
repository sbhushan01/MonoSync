package com.monosync.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Custom AudioProcessor for basic audio normalization (gain adjustment).
 * Supports both PCM_16BIT and PCM_FLOAT encodings to handle various
 * decoded formats including FLAC.
 */
class AudioNormalizationProcessor : BaseAudioProcessor() {

    private var targetGain: Float = 1.0f
    private var encoding: Int = C.ENCODING_PCM_16BIT

    fun setGain(gain: Float) {
        if (this.targetGain != gain) {
            this.targetGain = gain
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        return when (encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> {
                // Pass through unsupported encodings without processing
                // rather than throwing and silencing audio
                inputAudioFormat
            }
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position

        if (targetGain == 1.0f) {
            // No gain change needed — pass through efficiently
            val buffer = replaceOutputBuffer(size)
            val slice = inputBuffer.duplicate()
            slice.position(position)
            slice.limit(limit)
            buffer.put(slice)
            inputBuffer.position(limit)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(size)

        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                for (i in position until limit step 2) {
                    val sample = inputBuffer.getShort(i).toFloat()
                    val modified = (sample * targetGain).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    buffer.putShort(modified.toShort())
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                inputBuffer.order(ByteOrder.nativeOrder())
                for (i in position until limit step 4) {
                    val sample = inputBuffer.getFloat(i)
                    val modified = (sample * targetGain).coerceIn(-1.0f, 1.0f)
                    buffer.putFloat(modified)
                }
            }
            else -> {
                // Unknown encoding — pass through unchanged
                val slice = inputBuffer.duplicate()
                slice.position(position)
                slice.limit(limit)
                buffer.put(slice)
            }
        }

        inputBuffer.position(limit)
        buffer.flip()
    }
}
