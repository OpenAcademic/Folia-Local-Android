package dev.folialocal.app.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEffectsTest {

    @Test
    fun `every preset keeps the shared ten band layout`() {
        AudioPreset.entries.forEach { preset ->
            val effects = preset.create()
            assertEquals(AudioEffects.BAND_COUNT, effects.gains.size)
            assertEquals(preset.id, effects.presetId)
        }
    }

    @Test
    fun `lofi preset matches the original curve`() {
        val effects = AudioPreset.LOFI.create()
        assertEquals(
            listOf(-4f, -2f, 0f, 2f, 3f, 2f, 0f, -3f, -7f, -10f),
            effects.gains,
        )
        assertEquals(90f, effects.highpassHz)
        assertEquals(6_000f, effects.lowpassHz)
        assertEquals(0.45f, effects.crush)
        assertEquals(0.22f, effects.noise)
    }

    @Test
    fun `flat preset is neutral and disabled`() {
        val effects = AudioPreset.FLAT.create()
        assertFalse(effects.enabled)
        assertTrue(effects.isNeutral)
    }

    @Test
    fun `normalization clamps out of range values`() {
        val normalized = AudioEffects(
            gains = listOf(99f, -99f),
            highpassHz = 1f,
            lowpassHz = 99_000f,
            drive = 3f,
            width = -1f,
        ).normalized()

        assertEquals(AudioEffects.BAND_COUNT, normalized.gains.size)
        assertEquals(12f, normalized.gains[0])
        assertEquals(-12f, normalized.gains[1])
        assertEquals(0f, normalized.gains[9])
        assertEquals(20f, normalized.highpassHz)
        assertEquals(20_000f, normalized.lowpassHz)
        assertEquals(1f, normalized.drive)
        assertEquals(0f, normalized.width)
    }

    @Test
    fun `logarithmic parameter mapping round trips`() {
        AudioEffectParameter.entries.filter { it.logarithmic }.forEach { parameter ->
            listOf(parameter.min, parameter.max, 440f, 2_600f)
                .filter { it in parameter.min..parameter.max }
                .forEach { value ->
                    val roundTrip = parameter.fromSlider(parameter.toSlider(value))
                    assertEquals(value, roundTrip, value * 0.02f + 1f)
                }
        }
    }

    @Test
    fun `editing a slider switches to custom and enables effects`() {
        val effects = AudioPreset.FLAT.create()
            .withGain(3, 5f)
            .withParameter(AudioEffectParameter.SPACE, 0.4f)

        assertTrue(effects.enabled)
        assertEquals(AudioEffects.CUSTOM_PRESET_ID, effects.presetId)
        assertEquals(5f, effects.gains[3])
        assertEquals(0.4f, effects.space)
    }
}
