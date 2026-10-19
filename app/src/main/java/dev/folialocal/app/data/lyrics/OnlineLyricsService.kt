package dev.folialocal.app.data.lyrics

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.folialocal.app.R
import dev.folialocal.app.data.library.LibraryRepository
import dev.folialocal.app.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 在线歌词：搜索 lrclib.net 公共接口，把结果保存为与音频同名的 .lrc 文件。
 */
class OnlineLyricsService(
    private val context: Context,
    private val libraryRepository: LibraryRepository,
) {

    private val client = LrcLibClient()

    suspend fun search(trackName: String, artistName: String): List<OnlineLyrics> =
        withContext(Dispatchers.IO) {
            client.search(trackName, artistName)
        }

    /** 把歌词写进音频所在文件夹，命名与音频文件同名（仅替换扩展名）。 */
    suspend fun save(track: Track, lyricsText: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(lyricsText.isNotBlank()) { context.getString(R.string.error_lyrics_empty) }
            if (!libraryRepository.ensureWritePermission()) {
                error(context.getString(R.string.error_no_write_permission))
            }
            val rootUri = libraryRepository.index.value.rootUri
            if (rootUri.isBlank()) error(context.getString(R.string.error_no_folder_selected))
            var dir = DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
                ?: error(context.getString(R.string.error_access_folder_failed))
            track.folder.split('/').filter { it.isNotBlank() }.forEach { segment ->
                dir = dir.findFile(segment)?.takeIf { it.isDirectory }
                    ?: error(context.getString(R.string.error_find_folder_failed, segment))
            }
            val baseName = track.fileName.substringBeforeLast('.')
            val existing = dir.listFiles().firstOrNull { file ->
                file.name?.equals("$baseName.lrc", ignoreCase = true) == true
            }
            val target = existing
                ?: dir.createFile("application/octet-stream", "$baseName.lrc")
                ?: error(context.getString(R.string.error_create_lyrics_failed))
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { stream ->
                stream.write(lyricsText.toByteArray(Charsets.UTF_8))
            } ?: error(context.getString(R.string.error_write_lyrics_failed))
        }
    }
}
