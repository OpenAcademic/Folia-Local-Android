package dev.folialocal.app.data.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.folialocal.app.data.audio.AudioReactiveBus
import dev.folialocal.app.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val connected: Boolean = false,
    val currentTrackId: String? = null,
    val queueTrackIds: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null,
)

/**
 * 通过 MediaController 连接 PlaybackService，向 Compose 暴露播放状态。
 */
class PlaybackConnection(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private var controller: MediaController? = null
    private var connectJob: Job? = null
    private var tickerJob: Job? = null
    private var transportJob: Job? = null
    private var wantPlaying = false
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player.playbackState == Player.STATE_ENDED) {
                wantPlaying = false
            }
            if (!player.isPlaying) {
                AudioReactiveBus.reset()
            }
            publishState()
        }

        override fun onPlayerError(error: PlaybackException) {
            wantPlaying = false
            controller?.volume = 1f
            _state.value = _state.value.copy(error = error.localizedMessage ?: context.getString(dev.folialocal.app.R.string.error_playback_failed))
        }
    }

    fun connect() {
        if (controller != null || connectJob?.isActive == true) return
        connectJob = scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                val connected = runCatching { future.get() }.getOrNull()
                if (connected != null) {
                    controller = connected
                    connected.addListener(listener)
                    _state.value = _state.value.copy(connected = true)
                    publishState()
                    startTicker()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    fun play(tracks: List<Track>, startIndex: Int) {
        val controller = controller ?: return
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(tracks.indices)
        val items = tracks.map { it.toMediaItem() }
        transportJob?.cancel()
        wantPlaying = true
        controller.volume = 0f
        controller.setMediaItems(items, index, 0L)
        controller.prepare()
        controller.play()
        fadeVolumeTo(1f)
        _state.value = _state.value.copy(error = null)
    }

    fun playAt(index: Int) {
        val controller = controller ?: return
        if (index !in 0 until controller.mediaItemCount) return
        transportJob?.cancel()
        wantPlaying = true
        controller.volume = 0f
        controller.seekTo(index, 0L)
        controller.prepare()
        controller.play()
        fadeVolumeTo(1f)
    }

    fun togglePlayPause() {
        val controller = controller ?: return
        if (wantPlaying || controller.isPlaying) {
            wantPlaying = false
            fadeVolumeTo(0f) {
                if (!wantPlaying) {
                    controller.pause()
                    // 暂停后再恢复音量，下一次播放会从 0 重新淡入。
                    controller.volume = 1f
                }
            }
        } else {
            wantPlaying = true
            controller.volume = 0f
            controller.play()
            fadeVolumeTo(1f)
        }
        publishState()
    }

    fun next() {
        val controller = controller ?: return
        if (wantPlaying || controller.isPlaying) {
            wantPlaying = true
            controller.volume = 1f
            controller.seekToNextMediaItem()
            controller.play()
        } else {
            controller.seekToNextMediaItem()
        }
        publishState()
    }

    fun previous() {
        val controller = controller ?: return
        if (wantPlaying || controller.isPlaying) {
            wantPlaying = true
            controller.volume = 1f
            controller.seekToPreviousMediaItem()
            controller.play()
        } else {
            controller.seekToPreviousMediaItem()
        }
        publishState()
    }

    fun seekTo(positionMs: Long) {
        val controller = controller ?: return
        controller.seekTo(positionMs.coerceAtLeast(0L))
        _state.value = _state.value.copy(positionMs = positionMs)
        publishState()
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
        publishState()
    }

    fun cycleRepeatMode() {
        val controller = controller ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publishState()
    }

    fun release() {
        tickerJob?.cancel()
        transportJob?.cancel()
        wantPlaying = false
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    /**
     * 200ms 的播放/暂停淡入淡出，对应原项目 playbackFade 的听感。
     * 音量节点在效果链之后，因此只影响输出包络，不改变音效参数。
     */
    private fun fadeVolumeTo(target: Float, onComplete: (() -> Unit)? = null) {
        val controller = controller ?: return
        transportJob?.cancel()
        transportJob = scope.launch {
            val start = controller.volume
            val steps = 12
            for (step in 1..steps) {
                controller.volume = start + (target - start) * (step.toFloat() / steps)
                delay(200L / steps)
            }
            controller.volume = target
            onComplete?.invoke()
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                val controller = controller
                if (controller != null && controller.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = controller.currentPosition.coerceAtLeast(0L),
                        durationMs = controller.duration.coerceAtLeast(0L),
                    )
                }
                delay(200)
            }
        }
    }

    private fun publishState() {
        val controller = controller ?: return
        val queue = buildList {
            for (index in 0 until controller.mediaItemCount) {
                controller.getMediaItemAt(index).mediaId.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        _state.value = _state.value.copy(
            connected = true,
            currentTrackId = controller.currentMediaItem?.mediaId,
            queueTrackIds = queue,
            currentIndex = controller.currentMediaItemIndex.coerceAtLeast(0),
            isPlaying = controller.isPlaying,
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = controller.duration.coerceAtLeast(0L),
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = controller.repeatMode,
        )
    }

    private fun Track.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .apply {
                coverUri?.let { setArtworkUri(Uri.parse(it)) }
                if (trackNumber > 0) setTrackNumber(trackNumber)
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }
}
