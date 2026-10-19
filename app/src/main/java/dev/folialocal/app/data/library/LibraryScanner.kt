package dev.folialocal.app.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.folialocal.app.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "wav", "ogg", "opus")
private val LYRIC_EXTENSIONS = listOf("lrc", "txt")
private val COVER_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")
private val FOLDER_COVER_BASES = listOf("cover", "folder", "front", "album", "albumart")
private const val MAX_DEPTH = 32

data class ScanProgress(
    val active: Boolean = false,
    val scanned: Int = 0,
    val currentFolder: String = "",
    val error: String? = null,
)

/**
 * 通过 SAF 递归扫描用户选择的音乐文件夹。
 * 只读取目录内的音频、同名歌词与封面，不访问网络。
 */
class LibraryScanner(private val context: Context) {

    suspend fun scan(
        rootUri: Uri,
        onProgress: (Int, String) -> Unit,
    ): List<Track> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri)
            ?: error(context.getString(dev.folialocal.app.R.string.error_open_folder_failed))
        val tracks = mutableListOf<Track>()
        var scanned = 0
        walk(root, "", 0, null, tracks) {
            scanned += 1
            onProgress(scanned, it)
        }
        tracks
    }

    private suspend fun walk(
        directory: DocumentFile,
        relativePath: String,
        depth: Int,
        inheritedCover: DocumentFile?,
        output: MutableList<Track>,
        onScanned: (String) -> Unit,
    ) {
        if (depth > MAX_DEPTH) return
        coroutineContext.ensureActive()
        val files = runCatching { directory.listFiles() }.getOrElse { return }
        val byName = files.associateBy { it.name?.lowercase().orEmpty() }
        // 子目录没有自己的封面时继承父目录的专辑封面。
        val folderCover = findFolderCover(files.toList(), byName) ?: inheritedCover
        val displayFolder = relativePath.ifBlank { directory.name.orEmpty() }

        files.sortedBy { it.name?.lowercase().orEmpty() }.forEach { file ->
            coroutineContext.ensureActive()
            val name = file.name ?: return@forEach
            if (name.startsWith('.')) return@forEach
            if (file.isDirectory) {
                val childPath = if (relativePath.isBlank()) name else "$relativePath/$name"
                walk(file, childPath, depth + 1, folderCover, output, onScanned)
                return@forEach
            }
            val extension = name.substringAfterLast('.', "").lowercase()
            if (extension !in AUDIO_EXTENSIONS) return@forEach

            val baseName = name.substringBeforeLast('.')
            val lookupBase = baseName.lowercase()
            val lyric = LYRIC_EXTENSIONS.firstNotNullOfOrNull { ext -> byName["$lookupBase.$ext"] }
            val sameNameCover = COVER_EXTENSIONS.firstNotNullOfOrNull { ext -> byName["$lookupBase.$ext"] }
            val tags = readTags(file.uri, name)
            output += Track(
                id = file.uri.toString(),
                uri = file.uri.toString(),
                title = tags.title,
                artist = tags.artist,
                album = tags.album,
                durationMs = tags.durationMs,
                trackNumber = tags.trackNumber,
                year = tags.year,
                folder = relativePath,
                fileName = name,
                sizeBytes = file.length().coerceAtLeast(0L),
                modifiedAt = file.lastModified(),
                lyricUri = lyric?.uri?.toString(),
                coverUri = (sameNameCover ?: folderCover)?.uri?.toString(),
            )
            onScanned(displayFolder)
        }
    }

    /**
     * 目录封面优先级：cover/folder/front/album/albumart，最后回退到目录里任意一张图片。
     */
    private fun findFolderCover(files: List<DocumentFile>, byName: Map<String, DocumentFile>): DocumentFile? {
        FOLDER_COVER_BASES.forEach { base ->
            COVER_EXTENSIONS.firstNotNullOfOrNull { ext -> byName["$base.$ext"] }?.let { return it }
        }
        return files.firstOrNull { file ->
            file.isFile && run {
                val extension = file.name?.substringAfterLast('.', "")?.lowercase().orEmpty()
                file.type?.startsWith("image/") == true || extension in COVER_EXTENSIONS
            }
        }
    }

    private fun readTags(uri: Uri, fileName: String): Tags {
        val fallback = guessFromFileName(fileName)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf { it.isNotEmpty() }
                ?: fallback.title
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim()?.takeIf { it.isNotEmpty() }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?.trim()?.takeIf { it.isNotEmpty() }
                ?: fallback.artist
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim()?.takeIf { it.isNotEmpty() }
                ?: ""
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.take(4)?.toIntOrNull()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                    ?.take(4)?.toIntOrNull()
                ?: 0
            Tags(title, artist, album, duration, trackNumber, year)
        } catch (_: Exception) {
            Tags(fallback.title, fallback.artist, "", 0L, 0, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun guessFromFileName(fileName: String): FallbackTags {
        val base = fileName.substringBeforeLast('.').trim()
        val withoutTrack = base.replace(Regex("""^\d{1,3}[\s._-]+"""), "").trim()
        val parts = withoutTrack.split(" - ", limit = 2)
        return if (parts.size == 2) {
            FallbackTags(title = parts[1].trim(), artist = parts[0].trim())
        } else {
            FallbackTags(title = withoutTrack, artist = "")
        }
    }

    private data class Tags(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val trackNumber: Int,
        val year: Int,
    )

    private data class FallbackTags(val title: String, val artist: String)
}
