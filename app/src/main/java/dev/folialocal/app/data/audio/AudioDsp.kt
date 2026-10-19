package dev.folialocal.app.data.audio

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * 纯 Kotlin 的本地音频 DSP，按 folia-major 的效果链顺序处理 16-bit / float PCM：
 *
 * 十段均衡 -> 高通 -> 低通 -> 饱和驱动 -> 比特破碎 -> 抖晃 ->
 * 立体声宽度 -> 动态压缩 -> 干湿混响 -> 黑胶噪声。
 *
 * 不依赖 Android API，便于单元测试；音频线程直接调用 process 系列方法。
 */
class AudioDsp {

    private var sampleRate = 44_100
    private var channels = 2
    private var supported = false
    private var failed = false
    private var buffersReady = false

    private var appliedRaw: AudioEffects? = null
    private var enabled = false
    private var neutral = true
    private var wasProcessing = false

    // 十段均衡：[声道][频段]。即使输入是单声道也保留两路状态，
    // 这样处理循环可以安全地读写左右两个副本，单声道只回写一路。
    private var eqFilters = Array(2) { Array(AudioEffects.BAND_COUNT) { Biquad() } }
    private val highpass = Array(2) { Biquad() }
    private val lowpass = Array(2) { Biquad() }
    private val noiseFilter = Array(2) { Biquad() }

    private var eqActive = false
    private var filtersActive = false

    // 饱和
    private var driveShape = 1f
    private var driveScale = 1f
    private var driveCompensation = 1f
    private var driveAmount = 0f

    // 比特破碎
    private var crushSteps = 0f
    private var crushAmount = 0f

    // 抖晃（磁带/黑胶漂移）
    private var wowL = FloatArray(0)
    private var wowR = FloatArray(0)
    private var wowWrite = 0
    private var wowPhase = 0.0
    private var flutterPhase = 0.0
    private var wowAmount = 0f

    // 噪声
    private var noiseGain = 0f
    private var crackle = 0f
    private var rngState = 0x1234_5678

    // 立体声宽度
    private var widthDirect = 1f
    private var widthCross = 0f

    // 动态压缩
    private var punchAmount = 0f
    private var compressorThresholdDb = -6f
    private var compressorRatio = 1f
    private var compressorKneeDb = 30f
    private var compressorMakeup = 1f
    private var compressorAttack = 0.005f
    private var compressorRelease = 0.0003f
    private var compressorEnvelope = 0f
    private var outLeft = 0f
    private var outRight = 0f

    // 混响
    private var reverbL = FloatArray(0)
    private var reverbR = FloatArray(0)
    private var reverbWriteL = 0
    private var reverbWriteR = 0
    private var reverbDampL = 0f
    private var reverbDampR = 0f
    private var reverbWet = 0f
    private var reverbDry = 1f

    fun configure(sampleRate: Int, channelCount: Int) {
        supported = sampleRate in 8_000..192_000 && channelCount in 1..2
        if (!supported) {
            failed = false
            buffersReady = false
            return
        }
        if (buffersReady && this.sampleRate == sampleRate && this.channels == channelCount) return
        this.sampleRate = sampleRate
        this.channels = channelCount
        eqFilters = Array(2) { Array(AudioEffects.BAND_COUNT) { Biquad() } }
        val wowSize = (sampleRate * 0.03f).toInt().coerceAtLeast(256) + 4
        wowL = FloatArray(wowSize)
        wowR = FloatArray(wowSize)
        reverbL = FloatArray((sampleRate * 0.031f).toInt().coerceAtLeast(256))
        reverbR = FloatArray((sampleRate * 0.037f).toInt().coerceAtLeast(256))
        wowWrite = 0
        reverbWriteL = 0
        reverbWriteR = 0
        appliedRaw = null
        failed = false
        buffersReady = true
        reset()
    }

    fun setSettings(settings: AudioEffects) {
        if (settings == appliedRaw) return
        appliedRaw = settings
        enabled = settings.enabled
        neutral = settings.isNeutral
        val shouldProcess = enabled && !neutral
        if (!shouldProcess) {
            // 从处理态切回直通时清掉滤波器与延迟线，避免残留尾音。
            if (wasProcessing) reset()
            wasProcessing = false
            eqActive = false
            filtersActive = false
            return
        }
        if (!supported) return
        wasProcessing = true

        val gainActive = settings.gains.any { abs(it) > 0.01f }
        eqActive = gainActive
        settings.gains.forEachIndexed { index, gainDb ->
            val frequency = min(AudioEffects.BAND_FREQUENCIES[index].toFloat(), sampleRate * 0.475f)
            eqFilters.forEach { channelFilters ->
                val filter = channelFilters[index]
                when (index) {
                    0 -> filter.setLowShelf(frequency, gainDb, sampleRate)
                    AudioEffects.BAND_COUNT - 1 -> filter.setHighShelf(frequency, gainDb, sampleRate)
                    else -> filter.setPeaking(frequency, 1.4f, gainDb, sampleRate)
                }
            }
        }

        highpass.forEach { it.setHighpass(settings.highpassHz, 0.7f, sampleRate) }
        val lowpassFrequency = min(settings.lowpassHz, sampleRate * 0.475f)
        lowpass.forEach { it.setLowpass(lowpassFrequency, 0.7f, sampleRate) }
        filtersActive = settings.highpassHz > 20.5f || settings.lowpassHz < 19_999f ||
            gainActive

        driveAmount = settings.drive
        if (driveAmount > 0.001f) {
            driveShape = 1f + driveAmount * 4f
            driveScale = tanh(driveShape)
            driveCompensation = measureSaturationCompensation(driveShape, driveScale)
        }

        crushAmount = settings.crush
        if (crushAmount > 0.001f) {
            val bits = 16f - sqrt(crushAmount) * 12f
            crushSteps = max(2f, 2f.pow(bits - 1f))
        }

        wowAmount = settings.wow

        noiseGain = if (settings.noise > 0.001f) {
            settings.noise.pow(1.4f) * 0.55f
        } else {
            0f
        }
        noiseFilter.forEach { it.setBandpass(2_600f, 0.5f, sampleRate) }

        widthDirect = (1f + settings.width) * 0.5f
        widthCross = (1f - settings.width) * 0.5f

        punchAmount = settings.punch
        compressorThresholdDb = -6f - punchAmount * 26f
        compressorRatio = 1f + punchAmount * 7f
        compressorKneeDb = 30f - punchAmount * 18f
        compressorMakeup = 10f.pow(punchAmount * 3f / 20f)
        compressorAttack = 1f - exp(-1f / (sampleRate * 0.008f))
        compressorRelease = 1f - exp(-1f / (sampleRate * 0.18f))

        reverbWet = settings.space * 0.75f
        reverbDry = 1f - settings.space * 0.2f
    }

    /** 供音频处理器上报：DSP 内部异常后已自动直通，避免整首歌静音。 */
    val hasFailed: Boolean get() = failed

    fun processShort(samples: ShortArray, frames: Int) {
        if (!supported || failed || !enabled || neutral) return
        val total = min(samples.size, frames * channels)
        try {
            var index = 0
            while (index + channels <= total) {
                val left = samples[index] / 32_768f
                val right = if (channels == 2) samples[index + 1] / 32_768f else left
                processFrame(left, right)
                val outL = outLeft.coerceIn(-1f, 1f)
                val outR = outRight.coerceIn(-1f, 1f)
                samples[index] = (outL * 32_767f).toInt().toShort()
                if (channels == 2) {
                    samples[index + 1] = (outR * 32_767f).toInt().toShort()
                }
                index += channels
            }
        } catch (_: Exception) {
            failed = true
        }
    }

    fun processFloat(samples: FloatArray, frames: Int) {
        if (!supported || failed || !enabled || neutral) return
        val total = min(samples.size, frames * channels)
        try {
            var index = 0
            while (index + channels <= total) {
                val left = samples[index]
                val right = if (channels == 2) samples[index + 1] else left
                processFrame(left, right)
                samples[index] = outLeft.coerceIn(-1f, 1f)
                if (channels == 2) samples[index + 1] = outRight.coerceIn(-1f, 1f)
                index += channels
            }
        } catch (_: Exception) {
            failed = true
        }
    }

    fun reset() {
        eqFilters.forEach { channel -> channel.forEach { it.reset() } }
        highpass.forEach { it.reset() }
        lowpass.forEach { it.reset() }
        noiseFilter.forEach { it.reset() }
        wowL.fill(0f)
        wowR.fill(0f)
        wowWrite = 0
        wowPhase = 0.0
        flutterPhase = 0.0
        reverbL.fill(0f)
        reverbR.fill(0f)
        reverbWriteL = 0
        reverbWriteR = 0
        reverbDampL = 0f
        reverbDampR = 0f
        compressorEnvelope = 0f
        crackle = 0f
        rngState = 0x1234_5678
    }

    private fun processFrame(inputL: Float, inputR: Float) {
        var left = inputL
        var right = inputR

        if (eqActive) {
            for (band in 0 until AudioEffects.BAND_COUNT) {
                left = eqFilters[0][band].process(left)
                right = eqFilters[1][band].process(right)
            }
        }

        left = highpass[0].process(left)
        right = highpass[1].process(right)
        left = lowpass[0].process(left)
        right = lowpass[1].process(right)

        if (driveAmount > 0.001f) {
            left = saturation(left)
            right = saturation(right)
        }

        if (crushAmount > 0.001f) {
            left = round(left * crushSteps) / crushSteps
            right = round(right * crushSteps) / crushSteps
        }

        if (wowAmount > 0.001f) {
            wowPhase += 2.0 * Math.PI * 0.7 / sampleRate
            flutterPhase += 2.0 * Math.PI * 6.8 / sampleRate
            if (wowPhase > 2.0 * Math.PI) wowPhase -= 2.0 * Math.PI
            if (flutterPhase > 2.0 * Math.PI) flutterPhase -= 2.0 * Math.PI
            val delaySeconds = 0.012 +
                wowAmount * 0.0045 * sin(wowPhase) +
                wowAmount * 0.0006 * sin(flutterPhase)
            val delaySamples = (delaySeconds * sampleRate).toFloat()
            left = readWow(wowL, delaySamples)
            right = readWow(wowR, delaySamples)
            wowL[wowWrite] = inputL
            wowR[wowWrite] = inputR
            wowWrite = (wowWrite + 1) % wowL.size
        }

        if (channels == 2 && abs(widthDirect - 1f) > 0.001f) {
            val nextLeft = left * widthDirect + right * widthCross
            val nextRight = right * widthDirect + left * widthCross
            left = nextLeft
            right = nextRight
        }

        if (punchAmount > 0.001f) {
            val peak = max(abs(left), abs(right))
            val coefficient = if (peak > compressorEnvelope) compressorAttack else compressorRelease
            compressorEnvelope += (peak - compressorEnvelope) * coefficient
            if (compressorEnvelope > 1e-6f) {
                val levelDb = 20f * log10(compressorEnvelope)
                val overDb = levelDb - compressorThresholdDb
                val gainDb = when {
                    overDb <= 0f -> 0f
                    overDb < compressorKneeDb -> {
                        val slope = 1f / compressorRatio - 1f
                        slope * overDb * overDb / (2f * compressorKneeDb)
                    }
                    else -> (1f / compressorRatio - 1f) * overDb
                }
                val gain = 10f.pow(gainDb / 20f) * compressorMakeup
                left *= gain
                right *= gain
            }
        }

        if (channels == 2 && reverbWet > 0.001f) {
            val outL = reverbL[reverbWriteL]
            val outR = reverbR[reverbWriteR]
            reverbDampL += (outL - reverbDampL) * 0.22f
            reverbDampR += (outR - reverbDampR) * 0.22f
            reverbL[reverbWriteL] = left + reverbDampL * 0.82f + reverbDampR * 0.12f
            reverbR[reverbWriteR] = right + reverbDampR * 0.82f + reverbDampL * 0.12f
            reverbWriteL = (reverbWriteL + 1) % reverbL.size
            reverbWriteR = (reverbWriteR + 1) % reverbR.size
            left = left * reverbDry + outL * reverbWet
            right = right * reverbDry + outR * reverbWet
        }

        if (noiseGain > 0.001f) {
            val rawLeft = nextNoise()
            val rawRight = nextNoise()
            left += noiseFilter[0].process(rawLeft) * noiseGain
            right += noiseFilter[1].process(rawRight) * noiseGain
        }

        outLeft = left
        outRight = right
    }

    private fun readWow(buffer: FloatArray, delaySamples: Float): Float {
        var read = wowWrite - delaySamples
        while (read < 0f) read += buffer.size
        val index = read.toInt() % buffer.size
        val next = (index + 1) % buffer.size
        val fraction = read - index
        return buffer[index] * (1f - fraction) + buffer[next] * fraction
    }

    /** 生成与 folia-major createVinylNoiseBuffer 相同统计特征的黑胶底噪。 */
    private fun nextNoise(): Float {
        if (randomUnit() < 0.0007f) {
            crackle = randomSigned()
        }
        crackle *= 0.88f
        return randomSigned() * 0.12f + crackle * 0.9f
    }

    private fun randomSigned(): Float = (nextRandom() ushr 1) / 1_073_741_824f - 1f

    private fun randomUnit(): Float = (nextRandom() ushr 1) / 2_147_483_648f

    private fun nextRandom(): Int {
        var x = rngState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        rngState = x
        return x
    }

    private fun saturation(input: Float): Float = tanh(driveShape * input) / driveScale * driveCompensation

    private fun measureSaturationCompensation(shape: Float, scale: Float): Float {
        var energy = 0f
        val samples = 512
        for (index in 0 until samples) {
            val input = 0.5f * sin(2.0 * Math.PI * index / samples).toFloat()
            val shaped = tanh(shape * input) / scale
            energy += shaped * shaped
        }
        val shapedRms = sqrt(energy / samples)
        val referenceRms = 0.5f / sqrt(2f)
        return if (shapedRms > 0f) referenceRms / shapedRms else 1f
    }

    /** RBJ 双二阶滤波器，系数与 Web Audio BiquadFilterNode 对齐。 */
    private class Biquad {
        private var b0 = 1f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun process(input: Float): Float {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }

        private fun set(b0: Float, b1: Float, b2: Float, a0: Float, a1: Float, a2: Float) {
            val inverse = 1f / a0
            this.b0 = b0 * inverse
            this.b1 = b1 * inverse
            this.b2 = b2 * inverse
            this.a1 = a1 * inverse
            this.a2 = a2 * inverse
        }

        fun setPeaking(frequency: Float, q: Float, gainDb: Float, sampleRate: Int) {
            val a = 10f.pow(gainDb / 40f)
            val omega = 2.0 * Math.PI * (frequency / sampleRate)
            val alpha = sin(omega) / (2.0 * q)
            val cosOmega = cos(omega)
            set(
                b0 = (1f + alpha * a).toFloat(),
                b1 = (-2f * cosOmega).toFloat(),
                b2 = (1f - alpha * a).toFloat(),
                a0 = (1f + alpha / a).toFloat(),
                a1 = (-2f * cosOmega).toFloat(),
                a2 = (1f - alpha / a).toFloat(),
            )
        }

        fun setLowShelf(frequency: Float, gainDb: Float, sampleRate: Int) {
            val a = 10f.pow(gainDb / 40f)
            val omega = 2.0 * Math.PI * (frequency / sampleRate)
            val cosOmega = cos(omega)
            val alpha = sin(omega) / 2.0 * sqrt(2.0)
            val sqrtA = sqrt(a.toDouble())
            set(
                b0 = (a * ((a + 1) - (a - 1) * cosOmega + 2 * sqrtA * alpha)).toFloat(),
                b1 = (2 * a * ((a - 1) - (a + 1) * cosOmega)).toFloat(),
                b2 = (a * ((a + 1) - (a - 1) * cosOmega - 2 * sqrtA * alpha)).toFloat(),
                a0 = ((a + 1) + (a - 1) * cosOmega + 2 * sqrtA * alpha).toFloat(),
                a1 = (-2 * ((a - 1) + (a + 1) * cosOmega)).toFloat(),
                a2 = ((a + 1) + (a - 1) * cosOmega - 2 * sqrtA * alpha).toFloat(),
            )
        }

        fun setHighShelf(frequency: Float, gainDb: Float, sampleRate: Int) {
            val a = 10f.pow(gainDb / 40f)
            val omega = 2.0 * Math.PI * (frequency / sampleRate)
            val cosOmega = cos(omega)
            val alpha = sin(omega) / 2.0 * sqrt(2.0)
            val sqrtA = sqrt(a.toDouble())
            set(
                b0 = (a * ((a + 1) + (a - 1) * cosOmega + 2 * sqrtA * alpha)).toFloat(),
                b1 = (-2 * a * ((a - 1) + (a + 1) * cosOmega)).toFloat(),
                b2 = (a * ((a + 1) + (a - 1) * cosOmega - 2 * sqrtA * alpha)).toFloat(),
                a0 = ((a + 1) - (a - 1) * cosOmega + 2 * sqrtA * alpha).toFloat(),
                a1 = (2 * ((a - 1) - (a + 1) * cosOmega)).toFloat(),
                a2 = ((a + 1) - (a - 1) * cosOmega - 2 * sqrtA * alpha).toFloat(),
            )
        }

        fun setHighpass(frequency: Float, q: Float, sampleRate: Int) {
            val omega = 2.0 * Math.PI * (frequency / sampleRate)
            val alpha = sin(omega) / (2.0 * q)
            val cosOmega = cos(omega)
            set(
                b0 = ((1 + cosOmega) / 2).toFloat(),
                b1 = (-(1 + cosOmega)).toFloat(),
                b2 = ((1 + cosOmega) / 2).toFloat(),
                a0 = (1 + alpha).toFloat(),
                a1 = (-2 * cosOmega).toFloat(),
                a2 = (1 - alpha).toFloat(),
            )
        }

        fun setLowpass(frequency: Float, q: Float, sampleRate: Int) {
            val omega = 2.0 * Math.PI * (frequency / sampleRate)
            val alpha = sin(omega) / (2.0 * q)
            val cosOmega = cos(omega)
            set(
                b0 = ((1 - cosOmega) / 2).toFloat(),
                b1 = (1 - cosOmega).toFloat(),
                b2 = ((1 - cosOmega) / 2).toFloat(),
                a0 = (1 + alpha).toFloat(),
                a1 = (-2 * cosOmega).toFloat(),
                a2 = (1 - alpha).toFloat(),
            )
        }

        fun setBandpass(frequency: Float, q: Float, sampleRate: Int) {
            val omega = 2.0 * Math.PI * (frequency / sampleRate)
            val alpha = sin(omega) / (2.0 * q)
            val cosOmega = cos(omega)
            set(
                b0 = alpha.toFloat(),
                b1 = 0f,
                b2 = (-alpha).toFloat(),
                a0 = (1 + alpha).toFloat(),
                a1 = (-2 * cosOmega).toFloat(),
                a2 = (1 - alpha).toFloat(),
            )
        }
    }
}
