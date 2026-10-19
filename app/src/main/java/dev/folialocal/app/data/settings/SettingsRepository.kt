package dev.folialocal.app.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val visualStyle: VisualStyle = VisualStyle.LUMI,
    val lyricFontScale: Float = 1f,
    val coverAccent: Boolean = true,
)

/**
 * 轻量设置存储。所有设置都留在本机 SharedPreferences。
 * 旧的 lyric_style 键会被读取并迁移到 visual_style。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("folia_local_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setVisualStyle(style: VisualStyle) {
        prefs.edit().putString(KEY_VISUAL_STYLE, style.id).apply()
        _settings.value = _settings.value.copy(visualStyle = style)
    }

    fun setLyricFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.75f, 1.5f)
        prefs.edit().putFloat(KEY_FONT_SCALE, clamped).apply()
        _settings.value = _settings.value.copy(lyricFontScale = clamped)
    }

    fun setCoverAccent(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COVER_ACCENT, enabled).apply()
        _settings.value = _settings.value.copy(coverAccent = enabled)
    }

    private fun read(): AppSettings {
        val storedStyle = prefs.getString(KEY_VISUAL_STYLE, null)
            ?: prefs.getString(LEGACY_STYLE, null)
        return AppSettings(
            visualStyle = VisualStyle.fromId(storedStyle),
            lyricFontScale = prefs.getFloat(KEY_FONT_SCALE, 1f).coerceIn(0.75f, 1.5f),
            coverAccent = prefs.getBoolean(KEY_COVER_ACCENT, true),
        )
    }

    private companion object {
        const val KEY_VISUAL_STYLE = "visual_style"
        const val LEGACY_STYLE = "lyric_style"
        const val KEY_FONT_SCALE = "lyric_font_scale"
        const val KEY_COVER_ACCENT = "cover_accent"
    }
}
