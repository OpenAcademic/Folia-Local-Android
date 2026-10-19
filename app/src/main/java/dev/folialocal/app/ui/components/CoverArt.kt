package dev.folialocal.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.folialocal.app.data.model.Track
import dev.folialocal.app.ui.LocalAppContainer
import kotlin.math.abs

/**
 * 本地封面：同目录封面 / 内嵌封面；加载失败时用曲目 id 生成稳定渐变占位。
 * 尺寸完全由调用方通过 modifier 决定。
 */
@Composable
fun CoverArt(
    track: Track?,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    targetPx: Int = 600,
) {
    val repository = LocalAppContainer.current.coverRepository
    val bitmap by produceState<BitmapHolder?>(
        initialValue = null,
        key1 = track?.id,
        key2 = targetPx,
    ) {
        value = track?.let { cover ->
            repository.load(cover, targetPx = targetPx)?.let(::BitmapHolder)
        }
    }

    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(placeholderBrush(track)),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap?.bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = track?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val iconSize = minOf(maxWidth, maxHeight) * 0.36f
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@JvmInline
private value class BitmapHolder(val bitmap: android.graphics.Bitmap)

private fun placeholderBrush(track: Track?): Brush {
    val seed = abs(track?.id?.hashCode() ?: 17)
    val hue = (seed % 360).toFloat()
    val top = Color.hsv(hue, 0.42f, 0.46f)
    val bottom = Color.hsv((hue + 46f) % 360f, 0.55f, 0.24f)
    return Brush.linearGradient(listOf(top, bottom))
}
