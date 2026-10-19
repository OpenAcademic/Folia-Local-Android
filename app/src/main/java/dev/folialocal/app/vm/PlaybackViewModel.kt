package dev.folialocal.app.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.folialocal.app.FoliaApplication
import dev.folialocal.app.data.lyrics.Lyrics
import dev.folialocal.app.data.model.Track
import dev.folialocal.app.ui.theme.DefaultAccent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as FoliaApplication).container
    private val connection = container.playbackConnection
    private val repository = container.libraryRepository
    private val lyricsRepository = container.lyricsRepository
    private val coverRepository = container.coverRepository
    private val settingsRepository = container.settingsRepository

    val playbackState = connection.state

    private val allTracks: StateFlow<List<Track>> = repository.index
        .map { it.tracks }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentTrack: StateFlow<Track?> = combine(allTracks, playbackState) { tracks, state ->
        tracks.firstOrNull { it.id == state.currentTrackId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val queueTracks: StateFlow<List<Track>> = combine(allTracks, playbackState) { tracks, state ->
        val byId = tracks.associateBy { it.id }
        state.queueTrackIds.mapNotNull { byId[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val lyricsRefresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val lyrics: StateFlow<Lyrics?> = combine(currentTrack, lyricsRefresh) { track, _ -> track }
        .flatMapLatest { track ->
            flow { emit(track?.let { lyricsRepository.load(it) }) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val accentColor: StateFlow<Int> = combine(
        currentTrack,
        settingsRepository.settings,
    ) { track, settings ->
        if (track == null || !settings.coverAccent) DefaultAccent else coverRepository.accentColor(track)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultAccent)

    init {
        connection.connect()
    }

    fun play(tracks: List<Track>, startIndex: Int) {
        connection.play(tracks, startIndex)
    }

    fun playTrack(track: Track, queue: List<Track>) {
        val index = queue.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            connection.play(queue, index)
        } else {
            connection.play(listOf(track), 0)
        }
    }

    fun playAt(index: Int) = connection.playAt(index)

    fun togglePlayPause() = connection.togglePlayPause()

    fun next() = connection.next()

    fun previous() = connection.previous()

    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)

    fun setShuffle(enabled: Boolean) = connection.setShuffle(enabled)

    fun cycleRepeatMode() = connection.cycleRepeatMode()

    /** 歌词文件被编辑后强制重新读取，绕过 LyricsRepository 的缓存。 */
    fun reloadLyrics() {
        currentTrack.value?.let { lyricsRepository.invalidate(it.id) }
        lyricsRefresh.value += 1
    }
}
