package dev.folialocal.app.ui.adaptive

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

/**
 * 以 Material 窗口尺寸分级为基准，同时覆盖展开态折叠屏。
 * 直接读取 Configuration，保证折叠/展开时布局立即重组。
 */
@Composable
fun rememberWindowWidthClass(): WindowWidthClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 600 -> WindowWidthClass.Compact
        widthDp < 840 -> WindowWidthClass.Medium
        else -> WindowWidthClass.Expanded
    }
}

data class FoldInfo(
    val isSeparating: Boolean = false,
    val isVertical: Boolean = false,
    val hingeWidthDp: Float = 0f,
    val hingeHeightDp: Float = 0f,
    val isHalfOpened: Boolean = false,
)

/**
 * 读取折叠屏姿态：分离式竖直铰链用于双栏避让，水平铰链用于桌面模式上下分区。
 */
@Composable
fun rememberFoldInfo(activity: Activity): FoldInfo {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutInfo by WindowInfoTracker.getOrCreate(context)
        .windowLayoutInfo(activity)
        .collectAsStateWithLifecycle(initialValue = null)

    val feature = layoutInfo?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull()
        ?: return FoldInfo()

    val vertical = feature.orientation == FoldingFeature.Orientation.VERTICAL
    val widthDp = with(density) { feature.bounds.width().toDp().value }
    val heightDp = with(density) { feature.bounds.height().toDp().value }
    return FoldInfo(
        isSeparating = feature.isSeparating,
        isVertical = vertical,
        hingeWidthDp = widthDp,
        hingeHeightDp = heightDp,
        isHalfOpened = feature.state == FoldingFeature.State.HALF_OPENED,
    )
}
