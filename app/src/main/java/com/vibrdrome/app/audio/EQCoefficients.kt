package com.vibrdrome.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class BiquadCoefficients(
    val b0: Float,
    val b1: Float,
    val b2: Float,
    val a1: Float,
    val a2: Float,
) {
    companion object {
        fun parametric(
            frequency: Float,
            gainDb: Float,
            q: Float,
            sampleRate: Float,
        ): BiquadCoefficients {
            if (gainDb == 0f) return bypass()

            val a = 10f.pow(gainDb / 40f)
            val w0 = 2f * PI.toFloat() * frequency / sampleRate
            val sinW0 = sin(w0)
            val cosW0 = cos(w0)
            val alpha = sinW0 / (2f * q)

            val b0 = 1f + alpha * a
            val b1 = -2f * cosW0
            val b2 = 1f - alpha * a
            val a0 = 1f + alpha / a
            val a1 = -2f * cosW0
            val a2 = 1f - alpha / a

            return BiquadCoefficients(
                b0 = b0 / a0,
                b1 = b1 / a0,
                b2 = b2 / a0,
                a1 = a1 / a0,
                a2 = a2 / a0,
            )
        }

        fun bypass() = BiquadCoefficients(1f, 0f, 0f, 0f, 0f)
    }
}

class EQCoefficientsStore {
    @Volatile
    private var coefficients: Array<BiquadCoefficients> = Array(EQPresets.BAND_COUNT) {
        BiquadCoefficients.bypass()
    }

    @Volatile
    var isEnabled: Boolean = false

    @Volatile
    var sampleRate: Float = 44100f

    fun getCoefficients(): Array<BiquadCoefficients> = coefficients

    @Synchronized
    fun update(gains: FloatArray, sampleRate: Float) {
        this.sampleRate = sampleRate
        coefficients = Array(EQPresets.BAND_COUNT) { i ->
            BiquadCoefficients.parametric(
                frequency = EQPresets.frequencies[i],
                gainDb = gains[i],
                q = EQPresets.DEFAULT_Q,
                sampleRate = sampleRate,
            )
        }
    }
}
