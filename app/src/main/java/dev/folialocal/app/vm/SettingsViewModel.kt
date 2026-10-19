package dev.folialocal.app.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.folialocal.app.FoliaApplication
import dev.folialocal.app.data.audio.AudioEffects
import dev.folialocal.app.data.settings.AppSettings
import dev.folialocal.app.data.settings.VisualStyle
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as FoliaApplication).container.settingsRepository
    private val audioRepository = (application as FoliaApplication).container.audioEffectsRepository

    val settings: StateFlow<AppSettings> = repository.settings
    val audioEffects: StateFlow<AudioEffects> = audioRepository.settings

    fun setVisualStyle(style: VisualStyle) = repository.setVisualStyle(style)

    fun setLyricFontScale(scale: Float) = repository.setLyricFontScale(scale)

    fun setCoverAccent(enabled: Boolean) = repository.setCoverAccent(enabled)

    fun setAudioEffects(effects: AudioEffects) = audioRepository.update(effects)

    fun resetAudioEffects() = audioRepository.reset()
}
