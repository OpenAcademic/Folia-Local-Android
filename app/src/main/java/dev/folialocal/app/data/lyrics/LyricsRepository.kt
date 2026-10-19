package dev.folialocal.app.data.lyrics

import android.content.Context
import android.net.Uri
import dev.folialocal.app.R
import dev.folialocal.app.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class LyricsRepository(private val context: Context) {

    private val cache = ConcurrentHashMap<String, Lyrics?>()

    suspend fun load(track: Track): Lyrics? {
        val lyricUri = track.lyricUri ?: return null
        cache[track.id]?.let { return it }
        return withContext(Dispatchers.IO) {
            val parsed = runCatching {
                val text = readRawText(lyricUri) ?: return@runCatching null
                LrcParser.parse(text, track.durationMs).takeIf { !it.isEmpty }
            }.getOrNull()
            cache[track.id] = parsed
            parsed
        }
    }

    suspend fun readRaw(track: Track): String? = withContext(Dispatchers.IO) {
        val lyricUri = track.lyricUri ?: return@withContext null
        runCatching { readRawText(lyricUri) }.getOrNull()
    }

    /**
     * 把编辑后的 LRC 头部标签写回原文件。歌词正文和未知标签保持不变。
     */
    suspend fun writeMetadata(track: Track, metadata: LyricMetadata): Result<Unit> =
        withContext(Dispatchers.IO) {
            val lyricUri = track.lyricUri
                ?: return@withContext Result.failure(IllegalStateException(context.getString(R.string.error_no_lyrics_file)))
            runCatching {
                val raw = readRawText(lyricUri) ?: error(context.getString(R.string.error_read_lyrics_failed))
                val next = LrcMetadata.apply(raw, metadata)
                context.contentResolver.openOutputStream(Uri.parse(lyricUri), "wt")?.use { stream ->
                    stream.write(next.toByteArray(Charsets.UTF_8))
                } ?: error(context.getString(R.string.error_write_lyrics_failed))
                invalidate(track.id)
            }
        }

    fun invalidate(trackId: String) {
        cache.remove(trackId)
    }

    private fun readRawText(lyricUri: String): String? {
        val bytes = context.contentResolver.openInputStream(Uri.parse(lyricUri))
            ?.use { it.readBytes() }
            ?: return null
        return LyricTextDecoder.decode(bytes)
    }
}
