package dev.folialocal.app.data.settings

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import dev.folialocal.app.R

/**
 * 全屏播放效果。每一项同时决定背景视觉、歌词排版和逐字动画气质，
 * 对应原项目 img/preview-* 中的同名样式。
 */
enum class VisualStyle(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val accent: Color,
) {
    LUMI(
        id = "lumi",
        titleRes = R.string.style_lumi,
        descriptionRes = R.string.style_lumi_desc,
        accent = Color(0xFFE7BD50),
    ),
    FUME(
        id = "fume",
        titleRes = R.string.style_fume,
        descriptionRes = R.string.style_fume_desc,
        accent = Color(0xFFEFC863),
    ),
    CAD(
        id = "cad",
        titleRes = R.string.style_cad,
        descriptionRes = R.string.style_cad_desc,
        accent = Color(0xFF8FD3FF),
    ),
    PAT(
        id = "pat",
        titleRes = R.string.style_pat,
        descriptionRes = R.string.style_pat_desc,
        accent = Color(0xFFD9E6F2),
    ),
    CAPPELLA(
        id = "cappella",
        titleRes = R.string.style_cappella,
        descriptionRes = R.string.style_cappella_desc,
        accent = Color(0xFFFF9FC7),
    ),
    TILT(
        id = "tilt",
        titleRes = R.string.style_tilt,
        descriptionRes = R.string.style_tilt_desc,
        accent = Color(0xFF6DBBF4),
    ),
    DIORAMA(
        id = "diorama",
        titleRes = R.string.style_diorama,
        descriptionRes = R.string.style_diorama_desc,
        accent = Color(0xFFB6CDD3),
    ),
    PENDOLO(
        id = "pendolo",
        titleRes = R.string.style_pendolo,
        descriptionRes = R.string.style_pendolo_desc,
        accent = Color(0xFF6D898B),
    ),
    SPECTRUM(
        id = "spectrum",
        titleRes = R.string.style_spectrum,
        descriptionRes = R.string.style_spectrum_desc,
        accent = Color(0xFFC9B8FF),
    ),
    COVER(
        id = "cover",
        titleRes = R.string.style_cover,
        descriptionRes = R.string.style_cover_desc,
        accent = Color(0xFFC9B8FF),
    );

    companion object {
        fun fromId(value: String?): VisualStyle {
            if (value.isNullOrBlank()) return LUMI
            return entries.firstOrNull { it.id.equals(value, ignoreCase = true) }
                ?: entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: LUMI
        }
    }
}
