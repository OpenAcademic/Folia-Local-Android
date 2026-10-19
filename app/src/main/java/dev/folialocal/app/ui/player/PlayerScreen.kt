package dev.folialocal.app.ui.player

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import dev.folialocal.app.R
import dev.folialocal.app.data.audio.AudioEffects
import dev.folialocal.app.data.audio.AudioReactiveBus
import dev.folialocal.app.data.model.Track
import dev.folialocal.app.data.playback.PlaybackUiState
import dev.folialocal.app.data.settings.AppSettings
import dev.folialocal.app.data.settings.VisualStyle
import dev.folialocal.app.ui.LocalAppContainer
import dev.folialocal.app.ui.components.CoverArt
import dev.folialocal.app.ui.components.artistLabel
import dev.folialocal.app.ui.components.formatDuration
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    track: Track?,
    playbackState: PlaybackUiState,
    lyrics: dev.folialocal.app.data.lyrics.Lyrics?,
    queue: List<Track>,
    accentInt: Int,
    settings: AppSettings,
    audioEffects: AudioEffects,
    isFullScreen: Boolean,
    tabletopGap: androidx.compose.ui.unit.Dp = 0.dp,
    onClose: (() -> Unit)?,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: (Boolean) -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onAudioEffectsChange: (AudioEffects) -> Unit,
    onStyleChange: (VisualStyle) -> Unit,
    onTrackMetadataSaved: () -> Unit,
    onLyricsImported: () -> Unit,
) {
    val accent = Color(accentInt)
    val audioLevels by AudioReactiveBus.levels.collectAsStateWithLifecycle()
    val lyricIndex = lyrics?.lineIndexAt(playbackState.positionMs) ?: -1
    val currentLine = lyrics?.lines?.getOrNull(lyricIndex)
    val ghostText = currentLine?.text ?: track?.title.orEmpty()
    val lineProgress = if (currentLine != null && currentLine.endMs > currentLine.startMs) {
        ((playbackState.positionMs - currentLine.startMs).toFloat() /
            (currentLine.endMs - currentLine.startMs).toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val lyricAccent = when (settings.visualStyle) {
        VisualStyle.COVER, VisualStyle.SPECTRUM -> accent
        else -> settings.visualStyle.accent
    }
    HideSystemBars(enabled = isFullScreen)
    var showQueue by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }
    var showStyles by remember { mutableStateOf(false) }
    var editingTrack by remember { mutableStateOf(false) }
    var downloadingLyrics by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val collapseLabel = stringResource(R.string.player_collapse)
    val nowPlayingLabel = stringResource(R.string.now_playing)
    val openStylesLabel = stringResource(R.string.player_open_styles)
    val openEffectsLabel = stringResource(R.string.player_open_effects)
    val openQueueLabel = stringResource(R.string.player_open_queue)
    val queueTitle = stringResource(R.string.player_queue_title)
    val emptyHint = stringResource(R.string.player_empty_hint)

    Box(Modifier.fillMaxSize()) {
        if (settings.visualStyle == VisualStyle.COVER) {
            PlayerBackdrop(track = track, accent = accent)
        } else {
            VisualBackdrop(
                style = settings.visualStyle,
                track = track,
                accent = lyricAccent,
                levels = audioLevels,
                ghostText = ghostText,
                progress = lineProgress,
            )
        }

        if (track != null) {
            // 歌词铺满整个屏幕，底部播放条悬浮在歌词之上。
            LyricsPane(
                lyrics = lyrics,
                hasLyricFile = track.lyricUri != null,
                positionMs = playbackState.positionMs,
                isPlaying = playbackState.isPlaying,
                style = settings.visualStyle,
                fontScale = settings.lyricFontScale,
                accent = lyricAccent,
                onSeek = onSeek,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = 196.dp, start = 8.dp, end = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isFullScreen) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFullScreen && onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = collapseLabel)
                    }
                }
                Text(
                    text = nowPlayingLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.62f),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showStyles = true }) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = openStylesLabel,
                        tint = lyricAccent,
                    )
                }
                IconButton(onClick = { showEffects = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Equalizer,
                        contentDescription = openEffectsLabel,
                        tint = if (audioEffects.enabled) lyricAccent else Color.White.copy(alpha = 0.72f),
                    )
                }
                IconButton(onClick = { showQueue = true }, enabled = queue.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = openQueueLabel)
                }
            }

            if (track == null) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = emptyHint,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
                FloatingPlayerBar(
                    track = track,
                    playbackState = playbackState,
                    accent = lyricAccent,
                    tabletopGap = tabletopGap,
                    onEditTrack = { editingTrack = true },
                    onDownloadLyrics = { downloadingLyrics = true },
                    onSeek = onSeek,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                )
            }
        }
    }

    if (editingTrack && track != null) {
        TrackEditDialog(
            track = track,
            onDismiss = { editingTrack = false },
            onSaved = onTrackMetadataSaved,
        )
    }

    if (downloadingLyrics && track != null) {
        OnlineLyricsDialog(
            track = track,
            onDismiss = { downloadingLyrics = false },
            onImported = onLyricsImported,
        )
    }

    if (showStyles) {
        VisualStyleSheet(
            selected = settings.visualStyle,
            onSelect = {
                onStyleChange(it)
                showStyles = false
            },
            onDismiss = { showStyles = false },
        )
    }

    if (showEffects) {
        AudioEffectsSheet(
            effects = audioEffects,
            onEffectsChange = onAudioEffectsChange,
            onDismiss = { showEffects = false },
        )
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = queueTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                    val isCurrent = item.id == track?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPlayAt(index)
                                scope.launch { sheetState.hide() }.invokeOnCompletion { showQueue = false }
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverArt(
                            track = item,
                            modifier = Modifier.size(42.dp),
                            corner = 8.dp,
                            targetPx = 240,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.artistLabel(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = formatDuration(item.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerBackdrop(track: Track?, accent: Color) {
    val repository = LocalAppContainer.current.coverRepository
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = track?.id) {
        value = track?.let { repository.load(it, targetPx = 700) }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF08080A))) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.25f
                        scaleY = 1.25f
                        alpha = 0.38f
                    }
                    .blur(56.dp),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                accent.copy(alpha = 0.30f),
                                accent.copy(alpha = 0.08f),
                                Color(0xFF08080A),
                            ),
                            radius = 1100f,
                        ),
                    ),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.52f),
                        0.42f to Color(0xFF09090B).copy(alpha = 0.68f),
                        1f to Color(0xFF09090B).copy(alpha = 0.97f),
                    ),
                ),
        )
    }
}

@Composable
private fun FloatingPlayerBar(
    track: Track,
    playbackState: PlaybackUiState,
    accent: Color,
    tabletopGap: androidx.compose.ui.unit.Dp,
    onEditTrack: () -> Unit,
    onDownloadLyrics: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: (Boolean) -> Unit,
    onCycleRepeat: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .shadow(22.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xEA1A1A22), Color(0xF40A0A0F)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.13f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column {
            TrackMeta(
                track = track,
                accent = accent,
                onEditTrack = onEditTrack,
                onDownloadLyrics = onDownloadLyrics,
            )
            if (tabletopGap > 0.dp) {
                Spacer(Modifier.height(tabletopGap))
            }
            SeekBar(
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs,
                accent = accent,
                onSeek = onSeek,
            )
            Controls(
                isPlaying = playbackState.isPlaying,
                shuffleEnabled = playbackState.shuffleEnabled,
                repeatMode = playbackState.repeatMode,
                accent = accent,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )
        }
    }
}

@Composable
private fun TrackMeta(
    track: Track,
    accent: Color,
    onEditTrack: () -> Unit,
    onDownloadLyrics: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
    ) {
        CoverArt(
            track = track,
            modifier = Modifier.size(54.dp),
            corner = 12.dp,
            targetPx = 320,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDownloadLyrics) {
            Icon(
                imageVector = Icons.Rounded.CloudDownload,
                contentDescription = stringResource(dev.folialocal.app.R.string.player_download_lyrics),
                tint = if (track.lyricUri != null) {
                    Color.White.copy(alpha = 0.55f)
                } else {
                    accent.copy(alpha = 0.9f)
                },
            )
        }
        IconButton(onClick = onEditTrack) {
            Icon(
                imageVector = Icons.Rounded.EditNote,
                contentDescription = stringResource(dev.folialocal.app.R.string.player_edit_track),
                tint = Color.White.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
) {
    var dragValue by remember(durationMs) { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(0L)
    val fraction = if (duration > 0) {
        (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shownFraction = dragValue ?: fraction
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shownFraction,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { onSeek((it * duration).toLong()) }
                dragValue = null
            },
            enabled = duration > 0,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration((shownFraction * duration).toLong()),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.62f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun Controls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    accent: Color,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: (Boolean) -> Unit,
    onCycleRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shuffleLabel = stringResource(R.string.player_shuffle)
        val previousLabel = stringResource(R.string.player_previous)
        val pauseLabel = stringResource(R.string.player_pause)
        val playLabel = stringResource(R.string.player_play)
        val nextLabel = stringResource(R.string.player_next)
        val repeatLabel = stringResource(R.string.player_repeat_mode)
        IconButton(onClick = { onToggleShuffle(!shuffleEnabled) }) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = shuffleLabel,
                tint = if (shuffleEnabled) accent else Color.White.copy(alpha = 0.62f),
            )
        }
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = previousLabel,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }
        Surface(
            color = accent,
            shape = CircleShape,
            modifier = Modifier
                .size(68.dp)
                .clickable(onClick = onTogglePlayPause),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) pauseLabel else playLabel,
                    tint = dev.folialocal.app.ui.theme.contentColorForAccent(accent),
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = nextLabel,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }
        IconButton(onClick = onCycleRepeat) {
            Icon(
                imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                },
                contentDescription = repeatLabel,
                tint = if (repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun HideSystemBars(enabled: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val activity = context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (enabled && controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
