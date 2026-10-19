package dev.folialocal.app.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.folialocal.app.FoliaApplication
import dev.folialocal.app.data.model.LibrarySnapshot
import dev.folialocal.app.data.model.Track
import dev.folialocal.app.data.model.toLibrarySnapshot
import dev.folialocal.app.ui.navigation.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as FoliaApplication).container
    private val repository = container.libraryRepository
    private val coverRepository = container.coverRepository

    init {
        if (repository.index.value.rootUri.isNotBlank() && !repository.hasPersistedPermission()) {
            repository.markPermissionLost()
        }
    }

    val index = repository.index
    val progress = repository.progress

    val snapshot: StateFlow<LibrarySnapshot> = repository.index
        .map { index -> index.tracks.toLibrarySnapshot() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibrarySnapshot())

    val rootName: StateFlow<String> = repository.index
        .map { index -> index.rootName.ifBlank { index.rootUri } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val searchResults: StateFlow<List<Track>> = combine(snapshot, _query) { snapshot, raw ->
        val query = raw.trim()
        if (query.isEmpty()) {
            emptyList()
        } else {
            snapshot.tracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true) ||
                    track.album.contains(query, ignoreCase = true) ||
                    track.fileName.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _backStack = MutableStateFlow<List<Destination>>(listOf(Destination.Songs))
    val destination: StateFlow<Destination> = _backStack
        .map { it.last() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Destination.Songs)
    val canGoBack: StateFlow<Boolean> = _backStack
        .map { it.size > 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun navigateRoot(destination: Destination) {
        _backStack.value = listOf(destination)
    }

    fun push(destination: Destination) {
        _backStack.value = _backStack.value + destination
    }

    fun goBack() {
        _backStack.value = _backStack.value.dropLast(1).ifEmpty { listOf(Destination.Songs) }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setRoot(uri: Uri) {
        viewModelScope.launch {
            if (repository.setRoot(uri)) {
                coverRepository.clear()
                navigateRoot(Destination.Songs)
            }
        }
    }

    fun rescan() {
        viewModelScope.launch {
            if (repository.rescan()) {
                // 扫描可能新增/更换了封面，清掉旧的「无封面」缓存。
                coverRepository.clear()
            }
        }
    }

    fun clearLibrary() {
        viewModelScope.launch {
            repository.clear()
            coverRepository.clear()
            navigateRoot(Destination.Songs)
        }
    }

    /** 曲目信息覆盖（标题/艺术家/专辑/封面）变化后刷新索引。 */
    fun refreshOverrides() {
        viewModelScope.launch { repository.refreshOverrides() }
    }
}
