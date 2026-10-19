package dev.folialocal.app.data.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class AudioLevels(
    val rms: Float = 0f,
    val bands: FloatArray = FloatArray(AudioReactiveBus.BAND_COUNT),
    val pulse: Float = 0f,
)

/**
 * 从 Media3 的 PCM 缓冲区里提取轻量音频特征，供视觉效果使用。
 * 不申请录音权限，也不额外开启音频采集会话。
 */
object AudioReactiveBus {

    const val BAND_COUNT = 12
    private val FREQUENCIES = floatArrayOf(
        50f, 80f, 120f, 200f, 320f, 500f, 800f, 1_200f, 2_000f, 3_200f, 5_000f, 8_000f,
    )
    private const val ANALYSIS_SAMPLES = 512

    private val _levels = MutableStateFlow(AudioLevels())
    val levels: StateFlow<AudioLevels> = _levels.asStateFlow()
    private val smoothedBands = FloatArray(BAND_COUNT)
    private var frameCounter = 0
    private var smoothedRms = 0f

    fun submit(samples: ShortArray, sampleCount: Int, channels: Int, sampleRate: Int) {
        if (++frameCounter % 2 != 0) return
        analyze(sampleCount, channels, sampleRate) { index -> samples[index] / 32_768f }
    }

    fun submit(samples: FloatArray, sampleCount: Int, channels: Int, sampleRate: Int) {
        if (++frameCounter % 2 != 0) return
        analyze(sampleCount, channels, sampleRate) { index -> samples[index] }
    }

    fun reset() {
        smoothedBands.fill(0f)
        smoothedRms = 0f
        _levels.value = AudioLevels()
    }

    private inline fun analyze(sampleCount: Int, channels: Int, sampleRate: Int, sampleAt: (Int) -> Float) {
        if (sampleCount <= 0 || sampleRate <= 0 || channels <= 0) return
        val frames = sampleCount / channels
        if (frames <= 0) return
        val stride = max(1, frames / ANALYSIS_SAMPLES)
        val mono = FloatArray(ANALYSIS_SAMPLES)
        val used = min(ANALYSIS_SAMPLES, frames / stride)
        var energy = 0.0
        for (index in 0 until used) {
            val value = sampleAt(index * stride * channels)
            mono[index] = value
            energy += value * value
        }
        val rms = sqrt(energy / used.coerceAtLeast(1)).toFloat()
        smoothedRms = max(rms, smoothedRms * 0.86f)

        for (band in 0 until BAND_COUNT) {
            val magnitude = goertzel(mono, used, sampleRate, FREQUENCIES[band])
            val normalized = (ln(1f + magnitude * 60f) / ln(61f)).coerceIn(0f, 1f)
            smoothedBands[band] = max(normalized, smoothedBands[band] * 0.82f)
        }
        val pulse = (smoothedBands[0] * 0.45f + smoothedBands[1] * 0.3f + smoothedBands[2] * 0.25f)
            .coerceIn(0f, 1f)
        _levels.value = AudioLevels(
            rms = smoothedRms,
            bands = smoothedBands.copyOf(),
            pulse = pulse,
        )
    }

    private fun goertzel(samples: FloatArray, count: Int, sampleRate: Int, frequency: Float): Float {
        val omega = 2.0 * PI * frequency / sampleRate
        val coefficient = 2.0 * cos(omega)
        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0
        for (index in 0 until count) {
            s0 = samples[index] + coefficient * s1 - s2
            s2 = s1
            s1 = s0
        }
        val power = s1 * s1 + s2 * s2 - coefficient * s1 * s2
        return (sqrt(max(0.0, power)) / count.coerceAtLeast(1)).toFloat()
    }
}
