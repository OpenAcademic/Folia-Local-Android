package dev.folialocal.app.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.folialocal.app.data.lyrics.LyricLine
import dev.folialocal.app.data.settings.VisualStyle
import kotlin.math.abs
import kotlin.math.sin

private const val APPEAR_MS = 380f
private const val RECENT_GLOW_MS = 900f

/**
 * 一行歌词。当前行按字符时间逐个“蹦出”：缩放从 0.45 过冲到 1.12 再回稳，
 * 同时从下方浮起并点亮；拉丁词作为一个整体换行，但词内每个字母仍然逐个弹出。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PoppingLyricLine(
    line: LyricLine,
    index: Int,
    currentIndex: Int,
    positionMs: Long,
    style: VisualStyle,
    accent: Color,
    fontScale: Float,
    clickable: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isActive = index == currentIndex
    val distance = abs(index - currentIndex)
    val units = remember(line, style) { buildLyricUnits(line) }
    val inactiveAlpha = when {
        isActive -> 1f
        distance == 1 -> 0.55f
        distance == 2 -> 0.32f
        else -> 0.16f
    }
    val textStyle = lineTextStyle(style, fontScale, isActive, index)
    val lineTransform = when (style) {
        VisualStyle.LUMI -> Modifier.graphicsLayer {
            rotationZ = 0.8f
            translationY = (-2).dp.toPx()
        }
        VisualStyle.DIORAMA -> Modifier.graphicsLayer {
            rotationY = -8f
            rotationX = 3f
            cameraDistance = 18f * density
        }
        else -> Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = verticalPadding(style, isActive))
            .then(lineTransform),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isActive) {
            Text(
                text = line.text,
                style = textStyle.copy(
                    fontStyle = if (style == VisualStyle.TILT && index % 2 == 1) {
                        FontStyle.Italic
                    } else {
                        FontStyle.Normal
                    },
                ),
                color = Color.White.copy(alpha = inactiveAlpha * 0.9f),
                textAlign = if (style == VisualStyle.FUME) TextAlign.Start else textStyle.textAlign,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            return@Row
        }

        if (units.isEmpty()) {
            Text(
                text = line.text,
                style = textStyle,
                color = Color.White.copy(alpha = 0.8f),
            )
            return@Row
        }

        val totalChars = units.sumOf { it.chars.size }.coerceAtLeast(1)
        var characterIndex = 0
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.Center,
            maxItemsInEachRow = Int.MAX_VALUE,
        ) {
            units.forEach { unit ->
                if (unit.isSpace) {
                    Spacer(Modifier.width((textStyle.fontSize.value * 0.32f).dp))
                    return@forEach
                }
                if (style == VisualStyle.CAPPELLA) {
                    CappellaBubble(
                        unit = unit,
                        positionMs = positionMs,
                        accent = accent,
                        style = textStyle,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        unit.chars.forEach { char ->
                            val currentIndexInLine = characterIndex
                            characterIndex += 1
                            PoppingChar(
                                char = char,
                                charIndex = currentIndexInLine,
                                totalChars = totalChars,
                                positionMs = positionMs,
                                style = style,
                                accent = accent,
                                baseStyle = textStyle,
                                lineIndex = index,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoppingChar(
    char: TimedChar,
    charIndex: Int,
    totalChars: Int,
    positionMs: Long,
    style: VisualStyle,
    accent: Color,
    baseStyle: TextStyle,
    lineIndex: Int,
) {
    val raw = ((positionMs - char.startMs) / APPEAR_MS).coerceIn(0f, 1f)
    val eased = easeOutBack(raw)
    val sinceStart = positionMs - char.startMs
    val isCurrent = positionMs >= char.startMs && positionMs < char.endMs
    val isRecent = sinceStart in 0..RECENT_GLOW_MS.toLong()
    val isSung = positionMs >= char.endMs
    val progress = if (char.endMs <= char.startMs) {
        if (isSung) 1f else 0f
    } else {
        ((positionMs - char.startMs).toFloat() / (char.endMs - char.startMs).toFloat()).coerceIn(0f, 1f)
    }
    val color = when {
        sinceStart < 0 -> Color.Transparent
        isSung -> accent
        isCurrent -> lerp(accent.copy(alpha = 0.72f), accent, progress)
        else -> Color.White.copy(alpha = 0.38f)
    }
    val glow = if (isRecent) {
        Shadow(color = accent.copy(alpha = 0.65f), blurRadius = if (isCurrent) 22f else 12f)
    } else {
        null
    }

    val tiltExtra = if (style == VisualStyle.TILT) {
        (if (charIndex % 2 == 0) -1f else 1f) * 5f
    } else {
        0f
    }
    val patStep = if (style == VisualStyle.PAT) {
        (charIndex % 4) * 1.5f
    } else {
        0f
    }
    val arcT = if (style == VisualStyle.PENDOLO) {
        charIndex.toFloat() / totalChars.toFloat() - 0.5f
    } else {
        0f
    }
    val arcRotation = if (style == VisualStyle.PENDOLO) arcT * 22f else 0f
    val arcOffset = if (style == VisualStyle.PENDOLO) arcT * arcT * 54f else 0f
    val lumiStagger = if (style == VisualStyle.LUMI) {
        sin((charIndex + lineIndex) * 0.9f) * 3f
    } else {
        0f
    }
    val pulse = if (isCurrent) {
        1f + 0.20f * sin(progress * Math.PI.toFloat()).coerceAtLeast(0f)
    } else {
        1f
    }
    val scale = (0.30f + 0.70f * eased) * pulse * (if (isRecent) 1.05f else 1f)
    val alpha = if (sinceStart < 0) 0f else 0.18f + 0.82f * raw
    val offsetY = (1f - eased) * 20f + tiltExtra + patStep + lumiStagger + arcOffset
    val halo = if (isRecent) {
        (1f - (sinceStart.coerceAtLeast(0L) / RECENT_GLOW_MS).coerceIn(0f, 1f)) *
            (if (isCurrent) 0.42f else 0.22f)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offsetY.dp.toPx()
                rotationZ = arcRotation
                this.alpha = alpha
            }
            .drawBehind {
                if (halo > 0.01f) {
                    val radius = size.minDimension * (0.75f + (1f - halo) * 0.65f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = halo),
                                accent.copy(alpha = halo * 0.25f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = radius,
                        ),
                        radius = radius,
                        center = Offset(size.width / 2f, size.height / 2f),
                    )
                    if (isCurrent) {
                        drawCircle(
                            color = accent.copy(alpha = 0.55f * (1f - raw)),
                            radius = size.minDimension * (0.55f + raw * 0.9f),
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(width = 1.6f),
                        )
                    }
                }
            },
    ) {
        Text(
            text = char.text,
            style = baseStyle.copy(
                fontStyle = if (style == VisualStyle.TILT && lineIndex % 2 == 1) {
                    FontStyle.Italic
                } else {
                    baseStyle.fontStyle
                },
                shadow = glow,
            ),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun CappellaBubble(
    unit: LyricUnit,
    positionMs: Long,
    accent: Color,
    style: TextStyle,
) {
    val start = unit.startMs
    val raw = ((positionMs - start) / APPEAR_MS).coerceIn(0f, 1f)
    val eased = easeOutBack(raw)
    val isSung = positionMs >= unit.endMs
    val bubbleColor = if (isSung) {
        accent.copy(alpha = 0.26f)
    } else {
        accent.copy(alpha = 0.13f)
    }
    Box(
        modifier = Modifier
            .padding(3.dp)
            .graphicsLayer {
                scaleX = 0.55f + 0.45f * eased
                scaleY = 0.55f + 0.45f * eased
                alpha = 0.2f + 0.8f * raw
                translationY = (1f - eased) * 10.dp.toPx()
            }
            .background(bubbleColor, RoundedCornerShape(18.dp))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = unit.text,
            style = style.copy(fontSize = style.fontSize * 0.78f, letterSpacing = 0.4.sp),
            color = if (isSung) accent else Color.White.copy(alpha = 0.92f),
            maxLines = 1,
        )
    }
}

private fun lineTextStyle(
    style: VisualStyle,
    fontScale: Float,
    isActive: Boolean,
    lineIndex: Int,
): TextStyle {
    val size = when (style) {
        VisualStyle.LUMI -> 38f
        VisualStyle.FUME -> 32f
        VisualStyle.CAD -> 34f
        VisualStyle.PAT -> 34f
        VisualStyle.CAPPELLA -> 26f
        VisualStyle.TILT -> 40f
        VisualStyle.DIORAMA -> 36f
        VisualStyle.PENDOLO -> 24f
        VisualStyle.SPECTRUM -> 34f
        VisualStyle.COVER -> 34f
    } * fontScale * (if (isActive) 1f else 0.68f)
    val lineHeight = when (style) {
        VisualStyle.FUME -> size * 1.65f
        VisualStyle.PENDOLO -> size * 1.8f
        else -> size * 1.38f
    }
    return TextStyle(
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        fontStyle = if (style == VisualStyle.TILT && lineIndex % 2 == 1) FontStyle.Italic else FontStyle.Normal,
        letterSpacing = when (style) {
            VisualStyle.FUME -> 1.4.sp
            VisualStyle.TILT -> 0.14.sp
            VisualStyle.CAD -> 0.8.sp
            VisualStyle.PENDOLO -> 0.05.sp
            else -> 0.25.sp
        },
        textAlign = when (style) {
            VisualStyle.FUME -> TextAlign.Start
            VisualStyle.TILT -> TextAlign.Center
            else -> TextAlign.Center
        },
        color = Color.Unspecified,
    )
}

private fun verticalPadding(style: VisualStyle, isActive: Boolean): androidx.compose.ui.unit.Dp {
    val base = when (style) {
        VisualStyle.FUME -> 10f
        VisualStyle.TILT -> 10f
        VisualStyle.PENDOLO -> 8f
        else -> 8f
    }
    return (if (isActive) base * 1.15f else base).dp
}

private fun easeOutBack(t: Float): Float {
    val c1 = 2.4f
    val c3 = c1 + 1f
    val x = (t.coerceIn(0f, 1f) - 1f)
    return 1f + c3 * x * x * x + c1 * x * x
}
