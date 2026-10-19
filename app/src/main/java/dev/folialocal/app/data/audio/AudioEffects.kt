package dev.folialocal.app.data.audio

import androidx.annotation.StringRes
import dev.folialocal.app.R
import kotlinx.serialization.Serializable
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * 十段均衡器与九级效果链的完整设置。
 * 数值与 folia-major 的 audioEqualizer.ts / audioEffects.ts 保持一致，
 * 默认全部中性：关闭时音频原样通过。
 */
@Serializable
data class AudioEffects(
    val enabled: Boolean = false,
    val presetId: String = AudioPreset.FLAT.id,
    /** 10 段增益，单位 dB，范围 -12..12。 */
    val gains: List<Float> = List(BAND_COUNT) { 0f },
    val highpassHz: Float = 20f,
    val lowpassHz: Float = 20_000f,
    val drive: Float = 0f,
    val crush: Float = 0f,
    val wow: Float = 0f,
    val noise: Float = 0f,
    val width: Float = 1f,
    val space: Float = 0f,
    val punch: Float = 0f,
) {
    val isNeutral: Boolean
        get() = gains.all { kotlin.math.abs(it) < 0.01f } &&
            highpassHz <= 20.5f &&
            lowpassHz >= 19_999f &&
            drive <= 0.001f &&
            crush <= 0.001f &&
            wow <= 0.001f &&
            noise <= 0.001f &&
            kotlin.math.abs(width - 1f) <= 0.001f &&
            space <= 0.001f &&
            punch <= 0.001f

    fun normalized(): AudioEffects = copy(
        gains = List(BAND_COUNT) { index ->
            (gains.getOrNull(index) ?: 0f).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        },
        highpassHz = highpassHz.coerceIn(20f, 2_000f),
        lowpassHz = lowpassHz.coerceIn(500f, 20_000f),
        drive = drive.coerceIn(0f, 1f),
        crush = crush.coerceIn(0f, 1f),
        wow = wow.coerceIn(0f, 1f),
        noise = noise.coerceIn(0f, 1f),
        width = width.coerceIn(0f, 2f),
        space = space.coerceIn(0f, 1f),
        punch = punch.coerceIn(0f, 1f),
        presetId = presetId.takeIf { it.isNotBlank() } ?: AudioPreset.FLAT.id,
    )

    fun withGain(index: Int, gainDb: Float): AudioEffects {
        if (index !in 0 until BAND_COUNT) return this
        val next = gains.toMutableList()
        next[index] = gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        return copy(enabled = true, presetId = CUSTOM_PRESET_ID, gains = next)
    }

    fun withParameter(parameter: AudioEffectParameter, value: Float): AudioEffects {
        val clamped = value.coerceIn(parameter.min, parameter.max)
        val next = when (parameter) {
            AudioEffectParameter.HIGHPASS -> copy(highpassHz = clamped)
            AudioEffectParameter.LOWPASS -> copy(lowpassHz = clamped)
            AudioEffectParameter.DRIVE -> copy(drive = clamped)
            AudioEffectParameter.CRUSH -> copy(crush = clamped)
            AudioEffectParameter.WOW -> copy(wow = clamped)
            AudioEffectParameter.NOISE -> copy(noise = clamped)
            AudioEffectParameter.WIDTH -> copy(width = clamped)
            AudioEffectParameter.SPACE -> copy(space = clamped)
            AudioEffectParameter.PUNCH -> copy(punch = clamped)
        }
        return next.copy(enabled = true, presetId = CUSTOM_PRESET_ID)
    }

    fun parameterValue(parameter: AudioEffectParameter): Float = when (parameter) {
        AudioEffectParameter.HIGHPASS -> highpassHz
        AudioEffectParameter.LOWPASS -> lowpassHz
        AudioEffectParameter.DRIVE -> drive
        AudioEffectParameter.CRUSH -> crush
        AudioEffectParameter.WOW -> wow
        AudioEffectParameter.NOISE -> noise
        AudioEffectParameter.WIDTH -> width
        AudioEffectParameter.SPACE -> space
        AudioEffectParameter.PUNCH -> punch
    }

    companion object {
        const val BAND_COUNT = 10
        const val MIN_GAIN_DB = -12f
        const val MAX_GAIN_DB = 12f
        const val CUSTOM_PRESET_ID = "custom"

        val BAND_FREQUENCIES = intArrayOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)
        val BAND_LABELS = arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    }
}

/** 九项效果参数：标签、范围、单位与对数滑块映射。 */
enum class AudioEffectParameter(
    @StringRes val labelRes: Int,
    val min: Float,
    val max: Float,
    val neutral: Float,
    val unit: EffectUnit,
    val logarithmic: Boolean = false,
) {
    HIGHPASS(R.string.effect_highpass, 20f, 2_000f, 20f, EffectUnit.HERTZ, logarithmic = true),
    LOWPASS(R.string.effect_lowpass, 500f, 20_000f, 20_000f, EffectUnit.HERTZ, logarithmic = true),
    DRIVE(R.string.effect_drive, 0f, 1f, 0f, EffectUnit.PERCENT),
    CRUSH(R.string.effect_crush, 0f, 1f, 0f, EffectUnit.PERCENT),
    WOW(R.string.effect_wow, 0f, 1f, 0f, EffectUnit.PERCENT),
    NOISE(R.string.effect_noise, 0f, 1f, 0f, EffectUnit.PERCENT),
    WIDTH(R.string.effect_width, 0f, 2f, 1f, EffectUnit.PERCENT),
    SPACE(R.string.effect_space, 0f, 1f, 0f, EffectUnit.PERCENT),
    PUNCH(R.string.effect_punch, 0f, 1f, 0f, EffectUnit.PERCENT);

    /** 把实际值映射到 0..1 的滑块位置。 */
    fun toSlider(value: Float): Float {
        val clamped = value.coerceIn(min, max)
        return if (logarithmic) {
            (ln(clamped / min) / ln(max / min)).coerceIn(0f, 1f)
        } else {
            ((clamped - min) / (max - min)).coerceIn(0f, 1f)
        }
    }

    /** 把 0..1 的滑块位置还原成实际值。 */
    fun fromSlider(position: Float): Float {
        val ratio = position.coerceIn(0f, 1f)
        return if (logarithmic) {
            (min * kotlin.math.exp(ratio * ln(max / min))).coerceIn(min, max)
        } else {
            min + ratio * (max - min)
        }
    }

    fun format(value: Float): String = when (unit) {
        EffectUnit.HERTZ -> if (value >= 1_000f) {
            String.format(java.util.Locale.US, "%.1f kHz", value / 1_000f)
        } else {
            "${value.roundToInt()} Hz"
        }
        EffectUnit.PERCENT -> "${(value * 100).roundToInt()}%"
    }
}

enum class EffectUnit { HERTZ, PERCENT }

/** 六套内置音效，曲线与效果值与 folia-major 的 audioPresets.ts 一致。 */
enum class AudioPreset(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    FLAT("flat", R.string.preset_flat, R.string.preset_flat_desc),
    LOFI("lofi", R.string.preset_lofi, R.string.preset_lofi_desc),
    RADIO("radio", R.string.preset_radio, R.string.preset_radio_desc),
    HALL("hall", R.string.preset_hall, R.string.preset_hall_desc),
    VOCAL("vocal", R.string.preset_vocal, R.string.preset_vocal_desc),
    BASS("bass", R.string.preset_bass, R.string.preset_bass_desc);

    fun create(): AudioEffects = when (this) {
        FLAT -> AudioEffects(presetId = id)
        LOFI -> AudioEffects(
            enabled = true,
            presetId = id,
            gains = listOf(-4f, -2f, 0f, 2f, 3f, 2f, 0f, -3f, -7f, -10f),
            highpassHz = 90f,
            lowpassHz = 6_000f,
            drive = 0.28f,
            crush = 0.45f,
            wow = 0.32f,
            noise = 0.22f,
            width = 0.7f,
            space = 0.16f,
            punch = 0.4f,
        )
        RADIO -> AudioEffects(
            enabled = true,
            presetId = id,
            gains = listOf(-12f, -8f, -3f, 2f, 4f, 4f, 2f, -3f, -8f, -12f),
            highpassHz = 420f,
            lowpassHz = 3_800f,
            drive = 0.42f,
            crush = 0.3f,
            wow = 0.1f,
            noise = 0.2f,
            width = 0.12f,
            punch = 0.6f,
        )
        HALL -> AudioEffects(
            enabled = true,
            presetId = id,
            gains = listOf(1f, 1f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 0f),
            lowpassHz = 16_000f,
            width = 1.3f,
            space = 0.55f,
            punch = 0.15f,
        )
        VOCAL -> AudioEffects(
            enabled = true,
            presetId = id,
            gains = listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f),
            highpassHz = 110f,
            width = 0.9f,
            space = 0.08f,
            punch = 0.35f,
        )
        BASS -> AudioEffects(
            enabled = true,
            presetId = id,
            gains = listOf(6f, 5f, 3f, 1f, 0f, -1f, -1f, 0f, 1f, 2f),
            drive = 0.1f,
            width = 1.15f,
            punch = 0.45f,
        )
    }

    companion object {
        fun fromId(id: String): AudioPreset? = entries.firstOrNull { it.id == id }
    }
}
