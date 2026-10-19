package dev.folialocal.app.data.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class AudioDspTest {

    private val sampleRate = 44_100

    @Test
    fun `disabled effects pass audio through untouched`() {
        val input = stereo(440f, 0.5f)
        val dsp = configured(AudioEffects())
        val output = input.copyOf()
        dsp.processShort(output, output.size / 2)
        assertArrayEquals(input, output)
    }

    @Test
    fun `neutral enabled chain is also a passthrough`() {
        val input = stereo(440f, 0.5f)
        val dsp = configured(AudioEffects().copy(enabled = true))
        val output = input.copyOf()
        dsp.processShort(output, output.size / 2)
        assertArrayEquals(input, output)
    }

    @Test
    fun `lowpass attenuates a high frequency tone`() {
        val input = stereo(8_000f, 0.5f, frames = 8_192)
        val dsp = configured(AudioEffects(enabled = true, lowpassHz = 1_000f))
        dsp.processShort(input, input.size / 2)

        val before = rms(stereo(8_000f, 0.5f, frames = 8_192), tailOnly = true)
        val after = rms(input, tailOnly = true)
        assertTrue("expected strong attenuation, before=$before after=$after", after < before * 0.45)
    }

    @Test
    fun `highpass attenuates a low frequency tone`() {
        val input = stereo(60f, 0.5f, frames = 8_192)
        val dsp = configured(AudioEffects(enabled = true, highpassHz = 1_000f))
        dsp.processShort(input, input.size / 2)

        val after = rms(input, tailOnly = true)
        val reference = 0.5 / sqrt(2.0)
        assertTrue("expected strong attenuation, after=$after", after < reference * 0.45)
    }

    @Test
    fun `width zero collapses stereo to mono`() {
        val frames = 4_096
        val input = ShortArray(frames * 2) { index ->
            val frame = index / 2
            val phase = 2.0 * PI * (if (index % 2 == 0) 440.0 else 880.0) * frame / sampleRate
            (sin(phase) * 0.5 * 32_767).toInt().toShort()
        }
        val dsp = configured(AudioEffects(enabled = true, width = 0f))
        dsp.processShort(input, frames)

        var maxDifference = 0
        for (frame in frames / 2 until frames) {
            maxDifference = maxOf(maxDifference, abs(input[frame * 2].toInt() - input[frame * 2 + 1].toInt()))
        }
        assertTrue("left and right should be nearly identical, maxDifference=$maxDifference", maxDifference <= 2)
    }

    @Test
    fun `crush quantizes the waveform`() {
        val input = ShortArray(2_048 * 2) { index ->
            val frame = index / 2
            val phase = 2.0 * PI * 220.0 * frame / sampleRate
            (sin(phase) * 0.8 * 32_767).toInt().toShort()
        }
        val dsp = configured(AudioEffects(enabled = true, crush = 1f))
        dsp.processShort(input, input.size / 2)

        val distinct = input.asSequence().map { it.toInt() }.distinct().count()
        assertTrue("expected a staircase output, distinct=$distinct", distinct <= 20)
    }

    @Test
    fun `noise effect adds a noise floor to silence`() {
        val input = ShortArray(4_096 * 2)
        val dsp = configured(AudioEffects(enabled = true, noise = 1f))
        dsp.processShort(input, input.size / 2)

        val tailRms = rms(input, tailOnly = true)
        assertTrue("noise should be audible on silence, rms=$tailRms", tailRms > 0.001)
    }

    @Test
    fun `punch reduces peaks`() {
        val frames = 8_192
        val input = stereo(200f, 0.95f, frames = frames)
        val peakBefore = peak(input)
        val dsp = configured(AudioEffects(enabled = true, punch = 1f))
        dsp.processShort(input, frames)
        val peakAfter = peak(input, tailOnly = true)

        assertTrue("compressor should lower the peak, before=$peakBefore after=$peakAfter", peakAfter < peakBefore)
    }

    @Test
    fun `mono input processes effects without falling back`() {
        val frames = 8_192
        val input = ShortArray(frames) { index ->
            val phase = 2.0 * PI * 8_000.0 * index / sampleRate
            (sin(phase) * 0.5 * 32_767).toInt().toShort()
        }
        val dsp = AudioDsp().apply {
            configure(sampleRate, 1)
            setSettings(AudioEffects(enabled = true, lowpassHz = 1_000f))
        }
        dsp.processShort(input, frames)

        assertFalse("mono DSP must not fall back", dsp.hasFailed)
        assertTrue("lowpass should still attenuate mono audio", rms(input, tailOnly = true) < 0.5 / sqrt(2.0) * 0.45)
    }

    @Test
    fun `space adds a reverb tail after the source stops`() {
        val frames = 4_096
        val input = stereo(330f, 0.6f, frames = frames)
        val dsp = configured(AudioEffects(enabled = true, space = 0.8f))
        dsp.processShort(input, frames)

        val silence = ShortArray(8_192 * 2)
        dsp.processShort(silence, silence.size / 2)
        val tailRms = rms(silence, tailOnly = false)
        assertTrue("reverb tail should remain audible, rms=$tailRms", tailRms > 0.0005)
    }

    private fun configured(effects: AudioEffects): AudioDsp = AudioDsp().apply {
        configure(sampleRate, 2)
        setSettings(effects)
    }

    private fun stereo(
        frequency: Float,
        amplitude: Float,
        frames: Int = 4_096,
    ): ShortArray = ShortArray(frames * 2) { index ->
        val frame = index / 2
        val phase = 2.0 * PI * frequency * frame / sampleRate
        (sin(phase) * amplitude * 32_767).toInt().toShort()
    }

    private fun rms(samples: ShortArray, tailOnly: Boolean): Double {
        val start = if (tailOnly) samples.size / 2 else 0
        var sum = 0.0
        var count = 0
        for (index in start until samples.size) {
            val value = samples[index] / 32_768.0
            sum += value * value
            count += 1
        }
        return if (count == 0) 0.0 else sqrt(sum / count)
    }

    private fun peak(samples: ShortArray, tailOnly: Boolean = false): Int {
        val start = if (tailOnly) samples.size / 2 else 0
        var result = 0
        for (index in start until samples.size) {
            result = maxOf(result, abs(samples[index].toInt()))
        }
        return result
    }
}
