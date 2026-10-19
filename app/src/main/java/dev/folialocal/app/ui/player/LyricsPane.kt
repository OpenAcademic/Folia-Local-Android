package dev.folialocal.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.folialocal.app.data.lyrics.Lyrics
import dev.folialocal.app.data.settings.VisualStyle
import kotlin.math.abs

/** 当前句唱完多久后淡出（长间奏时避免一句话一直停在屏幕上）。 */
private const val GAP_HIDE_MS = 3_200L

/**
 * 沉浸式歌词视图。
 * 默认样式：屏幕上只保留当前这一句，每个字按时间逐个“蹦出”；长间奏时整句淡出。
 * 封面样式：完整歌词列表，当前句高亮，点击任意句子可跳转。
 */
@Composable
fun LyricsPane(
    lyrics: Lyrics?,
    hasLyricFile: Boolean,
    positionMs: Long,
    isPlaying: Boolean,
    style: VisualStyle,
    fontScale: Float,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lyrics == null || lyrics.isEmpty) {
        LyricsPlaceholder(hasLyricFile = hasLyricFile, modifier = modifier)
        return
    }

    val smoothPosition = remember { Animatable(positionMs.toFloat()) }
    LaunchedEffect(positionMs, isPlaying) {
        val target = positionMs.toFloat()
        val delta = abs(target - smoothPosition.value)
        if (!isPlaying || delta > 1_500f) {
            smoothPosition.snapTo(target)
        } else {
            smoothPosition.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 220, easing = LinearEasing),
            )
        }
    }
    val displayPosition = smoothPosition.value.toLong()

    if (style == VisualStyle.COVER) {
        FullLyricsList(
            lyrics = lyrics,
            positionMs = displayPosition,
            fontScale = fontScale,
            accent = accent,
            onSeek = onSeek,
            modifier = modifier,
        )
    } else {
        PoppingCurrentLine(
            lyrics = lyrics,
            displayPosition = displayPosition,
            style = style,
            fontScale = fontScale,
            accent = accent,
            onSeek = onSeek,
            modifier = modifier,
        )
    }
}

@Composable
private fun PoppingCurrentLine(
    lyrics: Lyrics,
    displayPosition: Long,
    style: VisualStyle,
    fontScale: Float,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIndex = lyrics.lineIndexAt(displayPosition)
    val currentLine = lyrics.lines.getOrNull(currentIndex)
    // 长间奏：当前句唱完一段时间后整句淡出，避免一句话停留到下一段才开始跳。
    val inGap = currentLine != null && displayPosition > currentLine.endMs + GAP_HIDE_MS
    val lineAlpha by animateFloatAsState(
        targetValue = if (inGap) 0f else 1f,
        animationSpec = tween(durationMillis = 680),
        label = "lyricGapAlpha",
    )
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (currentLine != null && lineAlpha > 0.01f) {
            PoppingLyricLine(
                line = currentLine,
                index = currentIndex,
                currentIndex = currentIndex,
                positionMs = displayPosition,
                style = style,
                accent = accent,
                fontScale = fontScale,
                clickable = false,
                onClick = { onSeek(currentLine.startMs) },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = lineAlpha },
            )
        }
    }
}

/**
 * 封面样式的完整歌词列表：所有句子常驻，当前句高亮放大，点击任意句子跳转。
 */
@Composable
private fun FullLyricsList(
    lyrics: Lyrics,
    positionMs: Long,
    fontScale: Float,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIndex = lyrics.lineIndexAt(positionMs)
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            runCatching { listState.animateScrollToItem(currentIndex) }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val verticalPad = maxHeight / 3f
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = verticalPad, bottom = verticalPad),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val isCurrent = index == currentIndex
                val size = (if (isCurrent) 21f else 17f) * fontScale
                Text(
                    text = line.text,
                    style = TextStyle(
                        fontSize = size.sp,
                        lineHeight = size.sp * 1.55f,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = when {
                        isCurrent -> accent
                        index < currentIndex -> Color.White.copy(alpha = 0.30f)
                        else -> Color.White.copy(alpha = 0.55f)
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeek(line.startMs) }
                        .padding(horizontal = 24.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricsPlaceholder(hasLyricFile: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.TextSnippet,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.36f),
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = if (hasLyricFile) {
                    androidx.compose.ui.res.stringResource(dev.folialocal.app.R.string.lyrics_reading)
                } else {
                    androidx.compose.ui.res.stringResource(dev.folialocal.app.R.string.lyrics_missing_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.62f),
            )
            if (!hasLyricFile) {
                Text(
                    text = androidx.compose.ui.res.stringResource(dev.folialocal.app.R.string.lyrics_missing_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.40f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
