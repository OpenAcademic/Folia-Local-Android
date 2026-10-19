package dev.folialocal.app.data.audio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * 音效设置持久化。音频线程通过 StateFlow 读取最新快照，不经过 UI 线程。
 */
class AudioEffectsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("folia_audio_effects", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AudioEffects> = _settings.asStateFlow()

    fun update(effects: AudioEffects) {
        val normalized = effects.normalized()
        prefs.edit().putString(KEY, json.encodeToString(normalized)).apply()
        _settings.value = normalized
    }

    fun reset() = update(AudioEffects())

    private fun load(): AudioEffects {
        val raw = prefs.getString(KEY, null) ?: return AudioEffects()
        return runCatching {
            json.decodeFromString<AudioEffects>(raw).normalized()
        }.getOrElse { AudioEffects() }
    }

    private companion object {
        const val KEY = "settings_v1"
    }
}
