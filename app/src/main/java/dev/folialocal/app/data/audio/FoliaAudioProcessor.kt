package dev.folialocal.app.data.audio

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 把 [AudioDsp] 接入 Media3 的 PCM 管线。
 *
 * 始终留在处理器链中（isActive = true），关闭音效时只做一次内存拷贝，
 * 因此播放中可以随时开关而无需重新 prepare。
 */
class FoliaAudioProcessor(
    private val settingsProvider: () -> AudioEffects,
) : BaseAudioProcessor() {

    private val dsp = AudioDsp()
    private var shortBuffer = ShortArray(0)
    private var floatBuffer = FloatArray(0)
    private var lastProcessing = false
    private var lastPresetId = ""
    private var lastDspFailed = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        Log.i(
            TAG,
            "configured ${inputAudioFormat.sampleRate}Hz ch=${inputAudioFormat.channelCount} enc=${inputAudioFormat.encoding}",
        )
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            // 无法安全处理时直通，不能让整首歌播放失败。
            dsp.configure(0, 0)
            return inputAudioFormat
        }
        dsp.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes <= 0) return
        inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(bytes)
        output.order(ByteOrder.nativeOrder())
        val settings = settingsProvider()
        val processing = settings.enabled && !settings.isNeutral
        if (processing != lastProcessing) {
            lastProcessing = processing
            Log.i(TAG, if (processing) "effects chain active (preset=${settings.presetId})" else "effects chain bypassed")
        }
        if (processing && settings.presetId != lastPresetId) {
            lastPresetId = settings.presetId
            Log.i(TAG, "preset -> ${settings.presetId}")
        }

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> {
                val samples = bytes / 4
                if (floatBuffer.size < samples) floatBuffer = FloatArray(samples)
                inputBuffer.asFloatBuffer().get(floatBuffer, 0, samples)
                inputBuffer.position(inputBuffer.limit())
                dsp.setSettings(settings)
                dsp.processFloat(floatBuffer, samples / inputAudioFormat.channelCount)
                AudioReactiveBus.submit(
                    floatBuffer,
                    samples,
                    inputAudioFormat.channelCount,
                    inputAudioFormat.sampleRate,
                )
                output.asFloatBuffer().put(floatBuffer, 0, samples)
            }
            C.ENCODING_PCM_16BIT -> {
                val samples = bytes / 2
                if (shortBuffer.size < samples) shortBuffer = ShortArray(samples)
                inputBuffer.asShortBuffer().get(shortBuffer, 0, samples)
                inputBuffer.position(inputBuffer.limit())
                dsp.setSettings(settings)
                dsp.processShort(shortBuffer, samples / inputAudioFormat.channelCount)
                AudioReactiveBus.submit(
                    shortBuffer,
                    samples,
                    inputAudioFormat.channelCount,
                    inputAudioFormat.sampleRate,
                )
                output.asShortBuffer().put(shortBuffer, 0, samples)
            }
            else -> {
                output.put(inputBuffer)
            }
        }

        if (dsp.hasFailed && !lastDspFailed) {
            lastDspFailed = true
            Log.e(TAG, "DSP failed; audio is passing through unchanged")
        }

        output.position(bytes)
        output.flip()
    }

    override fun onFlush() {
        dsp.reset()
    }

    override fun onReset() {
        dsp.reset()
    }

    override fun isActive(): Boolean = true

    private companion object {
        const val TAG = "FoliaAudio"
    }
}
