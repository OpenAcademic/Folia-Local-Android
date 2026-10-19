package dev.folialocal.app.data.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.folialocal.app.R
import dev.folialocal.app.data.model.LibraryIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LibraryRepository(
    private val context: Context,
    private val overrideRepository: TrackOverrideRepository,
) {

    private val store = LibraryIndexStore(context)
    private val scanner = LibraryScanner(context)
    private val _index = MutableStateFlow(
        store.load().let { index -> index.copy(tracks = overrideRepository.applyTo(index.tracks)) },
    )
    val index: StateFlow<LibraryIndex> = _index.asStateFlow()
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    val hasLibrary: Boolean get() = _index.value.tracks.isNotEmpty()

    fun rootName(): String = _index.value.rootName.ifBlank { DocumentFile.fromTreeUri(context, Uri.parse(_index.value.rootUri))?.name.orEmpty() }

    fun hasPersistedPermission(): Boolean {
        val rootUri = _index.value.rootUri
        if (rootUri.isBlank()) return false
        return runCatching {
            context.contentResolver.persistedUriPermissions.any {
                it.uri.toString() == rootUri && it.isReadPermission
            }
        }.getOrDefault(false)
    }

    fun persistPermission(uri: Uri): Boolean {
        // 优先申请读写权限：歌词元数据编辑需要写回同目录的 .lrc 文件。
        val readWrite = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)
        if (readWrite) return true
        return runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)
    }

    /**
     * 旧版本只申请过读权限；编辑歌词前尝试升级为读写权限。
     */
    fun ensureWritePermission(): Boolean {
        val rootUri = _index.value.rootUri
        if (rootUri.isBlank()) return false
        return runCatching {
            context.contentResolver.takePersistableUriPermission(
                Uri.parse(rootUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        }.getOrDefault(
            context.contentResolver.persistedUriPermissions.any {
                it.uri.toString() == rootUri && it.isReadPermission && it.isWritePermission
            },
        )
    }

    /**
     * 设定音乐根目录并立即重新扫描。
     */
    suspend fun setRoot(uri: Uri): Boolean {
        if (!persistPermission(uri)) {
            _progress.value = ScanProgress(error = context.getString(R.string.error_save_permission_failed))
            return false
        }
        val rootName = DocumentFile.fromTreeUri(context, uri)?.name.orEmpty()
        _index.value = LibraryIndex(rootUri = uri.toString(), rootName = rootName)
        return rescan()
    }

    suspend fun rescan(): Boolean {
        if (_progress.value.active) return false
        val rootUri = _index.value.rootUri
        if (rootUri.isBlank()) {
            _progress.value = ScanProgress(error = context.getString(R.string.error_no_folder_selected))
            return false
        }
        if (!hasPersistedPermission()) {
            _progress.value = ScanProgress(error = context.getString(R.string.error_folder_permission_lost))
            return false
        }
        _progress.value = ScanProgress(active = true, currentFolder = rootName())
        return try {
            val scannedTracks = scanner.scan(Uri.parse(rootUri)) { scanned, folder ->
                _progress.value = ScanProgress(
                    active = true,
                    scanned = scanned,
                    currentFolder = folder,
                )
            }
            val tracks = overrideRepository.applyTo(scannedTracks)
            val newIndex = _index.value.copy(
                tracks = tracks,
                scannedAt = System.currentTimeMillis(),
            )
            _index.value = newIndex
            store.save(newIndex)
            _progress.value = ScanProgress(active = false, scanned = tracks.size)
            true
        } catch (error: Exception) {
            _progress.value = ScanProgress(active = false, error = error.message ?: context.getString(R.string.error_scan_failed))
            false
        }
    }

    suspend fun clear() {
        store.clear()
        _index.value = LibraryIndex()
        _progress.value = ScanProgress()
    }

    /** 曲目信息覆盖变化后，重新合并到当前索引并持久化。 */
    suspend fun refreshOverrides() {
        val current = _index.value
        if (current.tracks.isEmpty()) return
        val updated = current.copy(tracks = overrideRepository.applyTo(current.tracks))
        _index.value = updated
        store.save(updated)
    }

    fun markPermissionLost() {
        if (_index.value.rootUri.isNotBlank()) {
            _progress.value = ScanProgress(error = context.getString(R.string.error_folder_permission_lost_settings))
        }
    }
}
