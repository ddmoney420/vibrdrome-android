package com.vibrdrome.app.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class BiquadAudioProcessor(
    private val coefficientsStore: EQCoefficientsStore,
) : AudioProcessor {

    private var inputFormat = AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    // Reusable buffer — grows as needed, never shrinks, never re-allocated per frame
    private var reusableBuffer: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    // [band][channel][x1, x2, y1, y2]
    private var state = createState()

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        inputFormat = inputAudioFormat
        coefficientsStore.sampleRate = inputAudioFormat.sampleRate.toFloat()
        return inputAudioFormat
    }

    override fun isActive(): Boolean = coefficientsStore.isEnabled

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return

        val output = ensureBufferCapacity(size)

        if (!coefficientsStore.isEnabled) {
            output.put(inputBuffer)
            output.flip()
            outputBuffer = output
            return
        }

        val coeffs = coefficientsStore.getCoefficients()
        val channelCount = inputFormat.channelCount

        @Suppress("SwitchIntDef")
        when (inputFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val frameCount = size / (channelCount * 2)
                for (frame in 0 until frameCount) {
                    for (ch in 0 until channelCount.coerceAtMost(2)) {
                        val shortVal = inputBuffer.getShort()
                        var sample = shortVal.toFloat() / Short.MAX_VALUE
                        for (band in 0 until EQPresets.BAND_COUNT) {
                            sample = processBiquad(sample, coeffs[band], band, ch)
                        }
                        output.putShort((sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
                    }
                    for (ch in 2 until channelCount) {
                        output.putShort(inputBuffer.getShort())
                    }
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val frameCount = size / (channelCount * 4)
                for (frame in 0 until frameCount) {
                    for (ch in 0 until channelCount.coerceAtMost(2)) {
                        var sample = inputBuffer.getFloat()
                        for (band in 0 until EQPresets.BAND_COUNT) {
                            sample = processBiquad(sample, coeffs[band], band, ch)
                        }
                        output.putFloat(sample.coerceIn(-1f, 1f))
                    }
                    for (ch in 2 until channelCount) {
                        output.putFloat(inputBuffer.getFloat())
                    }
                }
            }
        }

        output.flip()
        outputBuffer = output
    }

    private fun ensureBufferCapacity(size: Int): ByteBuffer {
        if (reusableBuffer.capacity() < size) {
            reusableBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            reusableBuffer.clear()
        }
        return reusableBuffer
    }

    private fun processBiquad(
        input: Float,
        coeffs: BiquadCoefficients,
        band: Int,
        channel: Int,
    ): Float {
        val s = state[band][channel]
        val y0 = coeffs.b0 * input + coeffs.b1 * s[0] + coeffs.b2 * s[1] -
            coeffs.a1 * s[2] - coeffs.a2 * s[3]
        s[1] = s[0]; s[0] = input
        s[3] = s[2]; s[2] = y0
        return y0
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        state = createState()
    }

    override fun reset() {
        flush()
        inputFormat = AudioFormat.NOT_SET
        reusableBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }

    private fun createState() = Array(EQPresets.BAND_COUNT) { Array(2) { FloatArray(4) } }
}
