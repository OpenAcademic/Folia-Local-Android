package dev.folialocal.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val DefaultAccent = 0xFFC9B8FF.toInt()

val FoliaBackground = Color(0xFF09090B)
val FoliaSurface = Color(0xFF121216)
val FoliaSurfaceHigh = Color(0xFF1A1A20)
val FoliaText = Color(0xFFF5F2EA)
val FoliaMuted = Color(0xFF9A98A6)
val FoliaDim = Color(0xFF5E5C68)

private val FoliaDarkColors = darkColorScheme(
    primary = Color(DefaultAccent),
    onPrimary = Color(0xFF171021),
    primaryContainer = Color(0xFF2A2140),
    onPrimaryContainer = Color(0xFFE8DFFF),
    secondary = Color(0xFFB8C4D9),
    onSecondary = Color(0xFF15171C),
    background = FoliaBackground,
    onBackground = FoliaText,
    surface = FoliaSurface,
    onSurface = FoliaText,
    surfaceVariant = FoliaSurfaceHigh,
    onSurfaceVariant = FoliaMuted,
    outline = Color(0xFF303038),
    outlineVariant = Color(0xFF232329),
    error = Color(0xFFFF8A8A),
)

private val FoliaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = 1.2.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.4.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
    ),
)

@Composable
fun FoliaTheme(
    accent: Color = Color(DefaultAccent),
    content: @Composable () -> Unit,
) {
    val colors = FoliaDarkColors.copy(
        primary = accent,
        primaryContainer = accent.copy(alpha = 0.18f).compositeOver(FoliaSurface),
        onPrimary = contentColorForAccent(accent),
    )
    MaterialTheme(
        colorScheme = colors,
        typography = FoliaTypography,
    ) {
        // Material3 不会在 MaterialTheme 层自动提供 LocalContentColor；
        // 没有 Surface 包裹的页面（大屏双栏、播放页、曲库页头）里的默认图标会因此变成黑色。
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            content()
        }
    }
}

fun contentColorForAccent(accent: Color): Color {
    val luminance = 0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue
    return if (luminance > 0.62f) Color(0xFF141018) else Color(0xFFF7F4FF)
}

private fun Color.compositeOver(background: Color): Color {
    val alpha = this.alpha
    return Color(
        red = red * alpha + background.red * (1 - alpha),
        green = green * alpha + background.green * (1 - alpha),
        blue = blue * alpha + background.blue * (1 - alpha),
        alpha = 1f,
    )
}

@Composable
fun isDarkTheme(): Boolean = isSystemInDarkTheme()
